package com.tandemmoto.playlist

import com.tandemmoto.library.Song
import java.io.File
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** What's saved: every entry (removed ones included, as markers), the clock and the partner. */
@Serializable
data class RidePlaylistData(
    val entries: List<RideEntry> = emptyList(),
    val clock: Long = 0,
    /** The install ID this list was last shared with; a different one means a new partner. */
    val partner: String? = null,
    /** Acceptor only: move this phone's songs after the initiator's once its list arrives. */
    val combinePending: Boolean = false
)

data class RidePlaylistState(
    val songs: List<RideEntry> = emptyList(),
    val me: String = "",
    val loaded: Boolean = false
)

interface RidePlaylistStore {
    suspend fun load(): RidePlaylistData

    suspend fun save(data: RidePlaylistData)
}

class JsonFileRidePlaylistStore(
    private val file: File,
    private val io: CoroutineDispatcher = Dispatchers.IO
) : RidePlaylistStore {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    override suspend fun load(): RidePlaylistData = withContext(io) {
        if (!file.exists()) return@withContext RidePlaylistData()
        runCatching { json.decodeFromString(RidePlaylistData.serializer(), file.readText()) }
            .getOrDefault(RidePlaylistData())
    }

    override suspend fun save(data: RidePlaylistData) = withContext(io) {
        file.parentFile?.mkdirs()
        val temp = File(file.parentFile, file.name + ".tmp")
        temp.writeText(json.encodeToString(RidePlaylistData.serializer(), data))
        if (!temp.renameTo(file)) {
            file.delete()
            temp.renameTo(file)
        }
        Unit
    }
}

/**
 * The ride playlist (#49): one ordered list of both phones' songs, edited on either phone and
 * merged with [RideList]'s rules. Local edits go out on [outgoing]; songs that disappear (removed
 * here or on the partner's phone) come out on [gone] so the owner can release the file.
 */
class RidePlaylist(
    private val store: RidePlaylistStore,
    private val installId: suspend () -> String,
    private val scope: CoroutineScope,
    private val log: (String) -> Unit = {}
) {
    private val lock = Mutex()
    private var data = RidePlaylistData()
    private var me = ""

    private val _state = MutableStateFlow(RidePlaylistState())
    val state: StateFlow<RidePlaylistState> = _state.asStateFlow()

    private val _outgoing = MutableSharedFlow<List<RideEntry>>(extraBufferCapacity = 64)

    /** Entries changed on this phone, to send to the partner. */
    val outgoing: SharedFlow<List<RideEntry>> = _outgoing.asSharedFlow()

    private val _gone = MutableSharedFlow<List<RideEntry>>(extraBufferCapacity = 64)

    /** Entries that just stopped being in the playlist, wherever they were removed. */
    val gone: SharedFlow<List<RideEntry>> = _gone.asSharedFlow()

    /** The partner's songs, for duplicate checks when adding (#48). */
    val partnerSongs: List<Song>
        get() = RideList.ordered(data.entries).filter { it.owner != me }.map { it.asSong() }

    fun start() {
        scope.launch {
            lock.withLock {
                me = installId()
                data = store.load()
                publish(loaded = true)
            }
            log("Ride playlist: ${RideList.ordered(data.entries).size} songs")
        }
    }

    /** This phone's newly added songs go at the end. */
    suspend fun addMine(songs: List<Song>) {
        if (songs.isEmpty()) return
        val sent = lock.withLock {
            val entries = data.entries.associateBy { it.id }.toMutableMap()
            val changed = mutableListOf<RideEntry>()
            for (song in songs) {
                val stamp = tick()
                val position = RideList.endPosition(entries.values)
                val entry =
                    entries[song.id]?.copy(added = stamp, position = position, moved = stamp)
                        ?: RideEntry(
                            song.id, song.title, song.artist, song.durationMs, song.sizeBytes,
                            owner = me, added = stamp, position = position, moved = stamp
                        )
                entries[song.id] = entry
                changed += entry
            }
            update(entries.values.toList())
            changed
        }
        _outgoing.emit(sent)
    }

    fun remove(id: String) {
        scope.launch {
            val removed = lock.withLock {
                val entry =
                    data.entries.firstOrNull { it.id == id && it.visible } ?: return@withLock null
                val gone = entry.copy(removed = tick())
                update(data.entries.map { if (it.id == id) gone else it })
                gone
            } ?: return@launch
            log("Removed ${logId(id)}")
            _gone.emit(listOf(removed))
            _outgoing.emit(listOf(removed))
        }
    }

    /** Moves the song at [from] to [to] (indexes in the shown order). */
    fun move(from: Int, to: Int) {
        scope.launch {
            val moved = lock.withLock {
                val position = RideList.positionFor(data.entries, from, to) ?: return@withLock null
                val entry = RideList.ordered(data.entries)[from]
                    .copy(position = position, moved = tick())
                update(data.entries.map { if (it.id == entry.id) entry else it })
                entry
            } ?: return@launch
            _outgoing.emit(listOf(moved))
        }
    }

    /** Entries from the partner's phone. */
    suspend fun merge(incoming: List<RideEntry>) {
        val gone = lock.withLock {
            incoming.forEach { observe(it) }
            val before = data.entries.associateBy { it.id }
            val merged = RideList.merge(before, incoming)
            update(merged.entries.values.toList())
            merged.changed.filter { !it.visible && before[it.id]?.visible == true }
        }
        if (gone.isNotEmpty()) _gone.emit(gone)
    }

    /**
     * A partner's app said Hello. A different partner than last time: the previous partner's
     * songs leave (the caller releases copies), and the lists combine. The initiator's songs come
     * first, so the acceptor moves its own after them once the initiator's list is in
     * ([combineAfter]). Returns the dropped entries.
     */
    suspend fun meetPartner(partner: String, iAmInitiator: Boolean): List<RideEntry> {
        val dropped = lock.withLock {
            if (data.partner == partner) return@withLock emptyList()
            val (keep, drop) = data.entries.partition { it.owner == me || it.owner == partner }
            val hadSongs = keep.any { it.owner == me && it.visible }
            data = data.copy(partner = partner, combinePending = !iAmInitiator && hadSongs)
            update(keep)
            log("New partner: ${drop.count { it.visible }} songs of the previous one dropped")
            drop.filter { it.visible }
        }
        if (dropped.isNotEmpty()) _gone.emit(dropped)
        return dropped
    }

    /** Acceptor, after the initiator's whole list arrived: put this phone's songs after it. */
    suspend fun combineAfter(initiatorIds: Set<String>) {
        val sent = lock.withLock {
            if (!data.combinePending) return@withLock emptyList()
            val ordered = RideList.ordered(data.entries)
            var last = ordered.filter { it.id in initiatorIds }.maxOfOrNull { it.position }
            val mine = ordered.filter { it.owner == me && it.id !in initiatorIds }
            val moved = mine.map { entry ->
                val position = FractionalIndex.between(last, null)
                last = position
                entry.copy(position = position, moved = tick())
            }.associateBy { it.id }
            data = data.copy(combinePending = false)
            update(data.entries.map { moved[it.id] ?: it })
            moved.values.toList()
        }
        if (sent.isNotEmpty()) _outgoing.emit(sent)
    }

    val combinePending: Boolean get() = data.combinePending

    /** Every entry, removal markers included, for a full sync. */
    suspend fun snapshot(): List<RideEntry> = lock.withLock { data.entries }

    /** Keeps the clock ahead of anything seen, so this phone's next change is newer. */
    private fun observe(entry: RideEntry) {
        val newest = listOfNotNull(entry.added, entry.moved, entry.removed).maxOf { it.clock }
        if (newest > data.clock) data = data.copy(clock = newest)
    }

    private fun tick(): Stamp {
        data = data.copy(clock = data.clock + 1)
        return Stamp(data.clock, me)
    }

    /** Call with [lock] held. */
    private suspend fun update(entries: List<RideEntry>) {
        data = data.copy(entries = entries)
        store.save(data)
        publish()
    }

    private fun publish(loaded: Boolean = _state.value.loaded) {
        _state.value = RidePlaylistState(RideList.ordered(data.entries), me, loaded)
    }

    private fun logId(id: String) = "song-" + id.take(8)
}
