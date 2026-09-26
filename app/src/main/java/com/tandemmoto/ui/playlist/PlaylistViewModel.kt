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
        val onThisPhone: Boolean = true
    ) : PlaylistRow {
        override val key get() = song.id
    }
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
    fileSlotsLeft: Int
) = PlaylistUiState(
    rows = ride.songs.map { entry ->
        val mine = entry.owner == ride.me
        PlaylistRow.Entry(
            song = entry.asSong(),
            missing = mine && entry.id in library.missing,
            mine = mine,
            onThisPhone = library.hasFile(entry.id)
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

class PlaylistViewModel(
    private val library: Library,
    private val playlist: RidePlaylist,
    partner: StateFlow<Partner?>,
    linkStatus: StateFlow<LinkStatus>
) : ViewModel() {
    val uiState: StateFlow<PlaylistUiState> =
        combine(playlist.state, library.state, partner, linkStatus) { ride, songs, who, status ->
            playlistUi(ride, songs, who, status is LinkStatus.Connected, library.fileSlotsLeft)
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
                PlaylistViewModel(app.library, app.ridePlaylist, app.link.partner, app.link.status)
            }
        }
    }
}
