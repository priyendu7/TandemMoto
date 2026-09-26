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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** One row of the Playlist screen. */
sealed interface PlaylistRow {
    val key: String

    /** A picked file whose fingerprint and tags are still being read. */
    data class Reading(val uri: String, val name: String) : PlaylistRow {
        override val key get() = "reading:$uri"
    }

    data class Entry(val song: Song, val missing: Boolean) : PlaylistRow {
        override val key get() = song.id
    }
}

data class PlaylistUiState(
    val rows: List<PlaylistRow> = emptyList(),
    val loaded: Boolean = false,
    val hasFolders: Boolean = false,
    /** Single files that can still be added before Android's permission limit. */
    val fileSlotsLeft: Int = Int.MAX_VALUE
) {
    /** Songs only (reading rows excluded), in playlist order: what move indexes refer to. */
    val songCount: Int get() = rows.count { it is PlaylistRow.Entry }
}

internal fun LibraryState.toUi(fileSlotsLeft: Int) = PlaylistUiState(
    rows = songs.map { PlaylistRow.Entry(it, it.id in missing) } +
        reading.map { PlaylistRow.Reading(it.uri, it.name) },
    loaded = loaded,
    hasFolders = folders.isNotEmpty(),
    fileSlotsLeft = fileSlotsLeft
)

class PlaylistViewModel(private val library: Library) : ViewModel() {
    val uiState: StateFlow<PlaylistUiState> = library.state
        .map { it.toUi(library.fileSlotsLeft) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, PlaylistUiState())

    val summaries: Flow<ImportSummary> = library.summaries

    /** The Playlist opened: see whether any song's file has gone. */
    fun onVisible() = library.checkFiles()

    fun addFiles(uris: List<String>) = library.addFiles(uris)

    fun addFolder(folder: String) = library.addFolder(folder)

    fun checkFolders() = library.checkFolders()

    fun remove(id: String) = library.remove(id)

    fun move(from: Int, to: Int) = library.move(from, to)

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                    as TandemMotoApp
                PlaylistViewModel(app.library)
            }
        }
    }
}
