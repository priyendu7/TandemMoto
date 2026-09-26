package com.tandemmoto.library

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** A picked file still being read (fingerprint and tags), shown with a spinner. */
data class PendingSong(val uri: String, val name: String)

data class LibraryState(
    val songs: List<Song> = emptyList(),
    /** Ids of songs whose file is gone (moved, deleted, or access revoked). */
    val missing: Set<String> = emptySet(),
    val reading: List<PendingSong> = emptyList(),
    val folders: List<String> = emptyList(),
    val loaded: Boolean = false
)

/** The outcome of one Add songs / Add a folder, for the summary message. */
data class ImportSummary(
    val added: Int,
    val alreadyThere: Int,
    val unreadable: Int,
    /** Files not added because Android's permission limit was reached (add a folder instead). */
    val overLimit: Int = 0,
    val fromFolder: Boolean = false,
    /** Android refused to keep access to the picked folder. */
    val folderRefused: Boolean = false
)

/**
 * This phone's songs, "My songs" (#48): added with the file picker, a file or a whole folder at a
 * time, remembered in place (never copied), in the user's order, saved across restarts.
 *
 * Each single file holds one of Android's persistable permissions, capped at 512 (Android 11+) or
 * 128 (10 and older), past which the oldest silently drop; a folder holds one for everything in
 * it. So files beyond the cap aren't added, and the summary suggests adding a folder.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class Library(
    private val source: SongSource,
    private val store: LibraryStore,
    private val scope: CoroutineScope,
    private val log: (String) -> Unit = {},
    io: CoroutineDispatcher = Dispatchers.IO
) {
    /** Two files read at a time: fast enough, and gentle on an older phone. */
    private val reading = io.limitedParallelism(READ_PARALLELISM)
    private val changes = Mutex()
    private var data = LibraryData()

    private val _state = MutableStateFlow(LibraryState())
    val state: StateFlow<LibraryState> = _state.asStateFlow()

    private val _summaries = MutableSharedFlow<ImportSummary>(extraBufferCapacity = 8)
    val summaries: SharedFlow<ImportSummary> = _summaries.asSharedFlow()

    /** Single files that can still be added before Android's permission limit. */
    val fileSlotsLeft: Int get() = (source.maxAccessCount - source.heldAccessCount()).coerceAtLeast(
        0
    )

    fun start() {
        scope.launch {
            changes.withLock {
                data = store.load()
                publish()
                _state.update { it.copy(loaded = true) }
            }
            log("My songs: ${data.songs.size} songs, ${data.folders.size} folders")
            checkFiles()
        }
    }

    /** Files from "Add songs". */
    fun addFiles(uris: List<String>) {
        if (uris.isEmpty()) return
        scope.launch {
            val picked = uris.distinct()
            val fresh = changes.withLock {
                picked.filter { uri -> data.songs.none { it.uri == uri } }
            }
            val accepted = fresh.take(fileSlotsLeft)
            val kept = accepted.filter(source::keepAccess)
            import(
                kept,
                folder = null,
                overLimit = fresh.size - accepted.size,
                refused = accepted.size - kept.size,
                // Picking the very same file again: it's already there (phone test on #48).
                alreadyThere = picked.size - fresh.size
            )
        }
    }

    /** A folder from "Add a folder": every audio file in it and its subfolders. */
    fun addFolder(folder: String) {
        scope.launch {
            val known = changes.withLock { folder in data.folders }
            if (!known) {
                if (!source.keepAccess(folder)) {
                    _summaries.emit(ImportSummary(0, 0, 0, fromFolder = true, folderRefused = true))
                    return@launch
                }
                changes.withLock {
                    data = data.copy(folders = data.folders + folder)
                    save()
                }
                log("Folder added (${data.folders.size} folders)")
            }
            scanFolder(folder)
        }
    }

    /** "Check folders for new songs": adds files put into added folders since. */
    fun checkFolders() {
        scope.launch { data.folders.forEach { scanFolder(it) } }
    }

    fun remove(id: String) {
        scope.launch {
            changes.withLock {
                val song = data.songs.firstOrNull { it.id == id } ?: return@withLock
                var next = data.copy(songs = data.songs - song)
                if (song.folder == null) {
                    source.releaseAccess(song.uri)
                } else {
                    next = next.copy(removedFromFolders = next.removedFromFolders + song.uri)
                    // The folder's last song: let go of the folder too.
                    if (next.songs.none { it.folder == song.folder }) {
                        source.releaseAccess(song.folder)
                        next = next.copy(
                            folders = next.folders - song.folder,
                            removedFromFolders = next.removedFromFolders
                                .filterNot { it.startsWith(song.folder) }.toSet()
                        )
                    }
                }
                data = next
                save()
                log("Removed ${song.logId}")
            }
        }
    }

    /** Reorders: the song at [from] goes to [to] (list indexes). */
    fun move(from: Int, to: Int) {
        scope.launch {
            changes.withLock {
                val songs = data.songs.toMutableList()
                if (from !in songs.indices || to !in songs.indices || from == to) return@withLock
                songs.add(to, songs.removeAt(from))
                data = data.copy(songs = songs)
                save()
            }
        }
    }

    /** Marks songs whose file is gone; on start and whenever the Playlist opens. */
    fun checkFiles() {
        scope.launch {
            val songs = changes.withLock { data.songs }
            val missing = coroutineScope {
                songs.map { song ->
                    async(reading) { song.id.takeUnless { source.exists(song.uri) } }
                }
                    .mapNotNull { it.await() }
                    .toSet()
            }
            if (missing.isNotEmpty()) log("${missing.size} songs' files are missing")
            _state.update { it.copy(missing = missing) }
        }
    }

    private suspend fun scanFolder(folder: String) {
        val files = withContext(reading) { source.listFolder(folder) }
        val fresh = changes.withLock {
            files.filter { uri ->
                uri !in data.removedFromFolders &&
                    data.songs.none { it.uri == uri }
            }
        }
        import(fresh, folder = folder)
    }

    private suspend fun import(
        uris: List<String>,
        folder: String?,
        overLimit: Int = 0,
        refused: Int = 0,
        alreadyThere: Int = 0
    ) {
        val pending =
            withContext(reading) { uris.map { PendingSong(it, source.displayName(it) ?: "") } }
        _state.update { it.copy(reading = it.reading + pending) }
        var added = 0
        var duplicates = alreadyThere
        var unreadable = refused
        coroutineScope {
            val reads = uris.map { uri ->
                uri to
                    async(reading) { runCatching { source.read(uri) }.getOrNull() }
            }
            for ((uri, read) in reads) {
                val file = read.await()
                changes.withLock {
                    val song = file?.let {
                        Song(it.id, uri, it.title, it.artist, it.durationMs, it.sizeBytes, folder)
                    }
                    when {
                        song == null -> {
                            unreadable++
                            if (folder == null) source.releaseAccess(uri)
                        }
                        Duplicates.findIn(data.songs, song) != null -> {
                            duplicates++
                            if (folder == null) source.releaseAccess(uri)
                        }
                        else -> {
                            added++
                            data = data.copy(songs = data.songs + song)
                            save()
                        }
                    }
                    _state.update { state ->
                        state.copy(
                            reading = state.reading.filterNot {
                                it.uri ==
                                    uri
                            }
                        )
                    }
                    publish()
                }
            }
        }
        log(
            "Added $added songs ($duplicates already there, $unreadable unreadable, $overLimit over the limit)"
        )
        _summaries.emit(
            ImportSummary(added, duplicates, unreadable, overLimit, fromFolder = folder != null)
        )
    }

    /** Call with [changes] held. */
    private suspend fun save() {
        store.save(data)
        publish()
    }

    private fun publish() {
        _state.update { it.copy(songs = data.songs, folders = data.folders) }
    }

    private companion object {
        const val READ_PARALLELISM = 2
    }
}
