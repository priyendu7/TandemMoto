package com.tandemmoto.ui.playlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.tandemmoto.TandemMotoApp
import com.tandemmoto.library.ImportSummary
import com.tandemmoto.library.Library
import com.tandemmoto.library.LibraryState
import com.tandemmoto.library.Song
import com.tandemmoto.link.LinkStatus
import com.tandemmoto.link.Partner
import com.tandemmoto.playlist.RidePlaylist
import com.tandemmoto.playlist.RidePlaylistState
import com.tandemmoto.transfer.TransferState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** One row of the Playlist screen. */
sealed interface PlaylistRow {
    val key: String

    /** A picked file whose fingerprint and tags are still being read. */
    data class Reading(val uri: String, val name: String) : PlaylistRow {
        override val key get() = "reading:$uri"
    }

    /**
     * A song in the ride playlist. [mine]: added on this phone; otherwise the partner's.
     * [onThisPhone]: this phone has a file for it (its own, or its copy of the partner's).
     */
    data class Entry(
        val song: Song,
        val missing: Boolean,
        val mine: Boolean = true,
        val onThisPhone: Boolean = true,
        /** Where the song is between the phones (#50); null when not paired. */
        val badge: SongBadge? = null
    ) : PlaylistRow {
        override val key get() = song.id
    }
}

/** A song's place between the two phones (#50). */
sealed interface SongBadge {
    /** Yours, and the partner's phone has it too. */
    data object OnBothPhones : SongBadge

    /** Yours, not on the partner's phone (yet: it arrives when it's near their current song). */
    data object NotOnPartner : SongBadge

    data class Sending(val percent: Int) : SongBadge

    /** The partner's, and this phone has it (downloaded, or its own copy). */
    data object OnThisPhone : SongBadge

    data class Receiving(val percent: Int) : SongBadge

    /** The partner's, in the window, next in line to download. */
    data object Queued : SongBadge

    /** The partner's, in the window, but the phones aren't linked. */
    data object WaitingForConnection : SongBadge

    /** The partner's, outside the window: downloads when it gets close. */
    data object OnPartnerOnly : SongBadge

    /** The partner's current song doesn't fit in this phone's storage. */
    data object StorageFull : SongBadge
}

/** Whether edits reach the partner now. */
enum class Sharing { NotPaired, Shared, WaitingToSync }

data class PlaylistUiState(
    val rows: List<PlaylistRow> = emptyList(),
    val loaded: Boolean = false,
    val hasFolders: Boolean = false,
    /** Single files that can still be added before Android's permission limit. */
    val fileSlotsLeft: Int = Int.MAX_VALUE,
    val sharing: Sharing = Sharing.NotPaired,
    val partnerName: String? = null
) {
    /** Songs only (reading rows excluded), in playlist order: what move indexes refer to. */
    val songCount: Int get() = rows.count { it is PlaylistRow.Entry }
}

internal fun playlistUi(
    ride: RidePlaylistState,
    library: LibraryState,
    partner: Partner?,
    connected: Boolean,
    fileSlotsLeft: Int,
    transfers: TransferState = TransferState()
) = PlaylistUiState(
    rows = ride.songs.mapIndexed { index, entry ->
        val mine = entry.owner == ride.me
        val onThisPhone = library.hasFile(entry.id) || entry.id in transfers.stored
        PlaylistRow.Entry(
            song = entry.asSong(),
            missing = mine && entry.id in library.missing,
            mine = mine,
            onThisPhone = onThisPhone,
            badge = if (partner ==
                null
            ) {
                null
            } else {
                badge(entry.id, index, mine, onThisPhone, connected, transfers)
            }
        )
    } + library.reading.map { PlaylistRow.Reading(it.uri, it.name) },
    loaded = ride.loaded && library.loaded,
    hasFolders = library.folders.isNotEmpty(),
    fileSlotsLeft = fileSlotsLeft,
    sharing = when {
        partner == null -> Sharing.NotPaired
        connected -> Sharing.Shared
        else -> Sharing.WaitingToSync
    },
    partnerName = partner?.name
)

private fun badge(
    id: String,
    index: Int,
    mine: Boolean,
    onThisPhone: Boolean,
    connected: Boolean,
    transfers: TransferState
): SongBadge = when {
    mine && transfers.sending?.id == id -> SongBadge.Sending(transfers.sending.percent)
    mine && id in transfers.partnerHas -> SongBadge.OnBothPhones
    mine -> SongBadge.NotOnPartner
    onThisPhone -> SongBadge.OnThisPhone
    transfers.receiving?.id == id -> SongBadge.Receiving(transfers.receiving.percent)
    // The current song (the first until the player, #51) is the one storage can refuse.
    transfers.storageFull && index == 0 -> SongBadge.StorageFull
    id in transfers.wanted && connected -> SongBadge.Queued
    id in transfers.wanted -> SongBadge.WaitingForConnection
    else -> SongBadge.OnPartnerOnly
}

class PlaylistViewModel(
    private val library: Library,
    private val playlist: RidePlaylist,
    partner: StateFlow<Partner?>,
    linkStatus: StateFlow<LinkStatus>,
    transfers: StateFlow<TransferState>
) : ViewModel() {
    val uiState: StateFlow<PlaylistUiState> =
        combine(playlist.state, library.state, partner, linkStatus, transfers) {
                ride,
                songs,
                who,
                status,
                moving
            ->
            playlistUi(
                ride,
                songs,
                who,
                status is LinkStatus.Connected,
                library.fileSlotsLeft,
                moving
            )
        }.stateIn(viewModelScope, SharingStarted.Eagerly, PlaylistUiState())

    val summaries: Flow<ImportSummary> = library.summaries

    /** The Playlist opened: see whether any song's file has gone. */
    fun onVisible() = library.checkFiles()

    fun addFiles(uris: List<String>) = library.addFiles(uris)

    fun addFolder(folder: String) = library.addFolder(folder)

    fun checkFolders() = library.checkFolders()

    /** Removes the song from the playlist on both phones (the owner lets go of the file). */
    fun remove(id: String) = playlist.remove(id)

    fun move(from: Int, to: Int) = playlist.move(from, to)

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                    as TandemMotoApp
                PlaylistViewModel(
                    app.library,
                    app.ridePlaylist,
                    app.link.partner,
                    app.link.status,
                    app.transfers.state
                )
            }
        }
    }
}
