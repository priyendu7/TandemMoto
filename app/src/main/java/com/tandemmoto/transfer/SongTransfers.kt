package com.tandemmoto.transfer

import com.tandemmoto.library.LibraryState
import com.tandemmoto.link.ChannelState
import com.tandemmoto.link.Endpoint
import com.tandemmoto.playlist.RidePlaylistState
import com.tandemmoto.state.Message
import java.io.IOException
import java.io.InputStream
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** How far a song has got: bytes [done] of [total]. */
data class Progress(val id: String, val done: Long, val total: Long) {
    val percent: Int get() = if (total <= 0) 0 else (done * 100 / total).toInt().coerceIn(0, 100)
}

data class TransferState(
    val receiving: Progress? = null,
    val sending: Progress? = null,
    /** Songs the partner's phone can play (its own, copies, downloads), from `SongsOnPhone`. */
    val partnerHas: Set<String> = emptySet(),
    /** The partner's songs downloaded on this phone. */
    val stored: Set<String> = emptySet(),
    val storedBytes: Long = 0,
    /** The partner's songs the window wants on this phone. */
    val wanted: Set<String> = emptySet(),
    val linked: Boolean = false,
    val storageFull: Boolean = false,
    val aheadLimitedTo: Int? = null
)

/**
 * Song transfer and the song window (#50). This phone asks for the partner's songs the window
 * wants, one at a time (current, then ahead nearest first, then behind), and serves the partner's
 * requests for its own songs:
 * - request: `SongRequest(id, offset)` on the command channel, resuming from the bytes already in
 *   the part file after a drop;
 * - data: [SongChunk]s on the [TransferConnection], appended to the part file, verified against
 *   the song's fingerprint at the end (a mismatch is thrown away and fetched again);
 * - which songs each phone can play goes both ways as `SongsOnPhone`, for "On both phones".
 */
class SongTransfers(
    private val playlist: StateFlow<RidePlaylistState>,
    private val library: StateFlow<LibraryState>,
    private val currentIndex: StateFlow<Int>,
    private val settings: StateFlow<WindowSettings>,
    private val channelState: StateFlow<ChannelState>,
    private val incoming: Flow<Message>,
    private val send: suspend (Message) -> Boolean,
    private val endpoint: StateFlow<Endpoint?>,
    private val connection: TransferConnection,
    private val store: SongStore,
    private val freeBytes: () -> Long,
    /** Opens this phone's own song file (a content Uri) for sending. */
    private val openSong: (String) -> InputStream?,
    private val scope: CoroutineScope,
    private val log: (String) -> Unit = {},
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val now: () -> Long = System::currentTimeMillis
) {
    private val lock = Mutex()
    private val _state = MutableStateFlow(TransferState())
    val state: StateFlow<TransferState> = _state.asStateFlow()

    private var plan = WindowPlan(emptyList(), emptySet())
    private var active: String? = null
    private var activeStartedAt = 0L
    private var activeStartOffset = 0L
    private var lastChunkAt = 0L
    private var watchdog: Job? = null
    private var sending: Job? = null
    private var lastInventory: Set<String>? = null
    private val failures = mutableMapOf<String, Int>()
    private val unavailable = mutableSetOf<String>()

    private val linked get() = channelState.value is ChannelState.Open

    fun start() {
        scope.launch {
            combine(playlist, library, currentIndex, settings) { _, _, _, _ -> }.collect {
                recompute()
            }
        }
        scope.launch {
            endpoint.collect { where ->
                if (where != null) connection.open(where) else connection.close()
            }
        }
        scope.launch {
            connection.ready.collect { ready ->
                // A new connection after a drop: ask again now (resuming) rather than wait for
                // the stall check.
                if (ready) lock.withLock { if (active != null) stopActive() }
                recompute()
            }
        }
        scope.launch {
            channelState.collect { state ->
                if (state !is ChannelState.Open) {
                    lock.withLock {
                        stopActive()
                        lastInventory = null
                        _state.update {
                            it.copy(partnerHas = emptySet(), linked = false, sending = null)
                        }
                    }
                    sending?.cancel()
                } else {
                    _state.update { it.copy(linked = true) }
                    unavailable.clear()
                }
                recompute()
            }
        }
        scope.launch {
            incoming.collect { message ->
                when (message) {
                    is Message.SongRequest -> serve(message)
                    is Message.SongUnavailable -> onUnavailable(message.id)
                    is Message.SongsOnPhone ->
                        _state.update { it.copy(partnerHas = message.ids.toSet()) }
                    else -> Unit
                }
            }
        }
        scope.launch { connection.chunks.collect { onChunk(it) } }
    }

    /** Settings → Remove songs from partner. They come back as the window needs them. */
    fun removeAll() {
        scope.launch {
            lock.withLock {
                stopActive()
                withContext(io) { store.deleteAll() }
                log("Removed all of the partner's songs")
            }
            recompute()
        }
    }

    /** Works out the window, deletes what left it, and starts the next download. */
    suspend fun recompute() {
        val next = lock.withLock {
            val ride = playlist.value
            val songs = library.value
            val downloaded = withContext(io) { store.stored() }
            val free = withContext(io) { freeBytes() }
            plan = SongWindow.plan(
                ride.songs,
                currentIndex.value,
                ride.me,
                settings.value,
                downloaded,
                songs::hasFile,
                free
            )
            val wantedIds = plan.wanted.map { it.id }.toSet()
            if (active != null && active !in wantedIds) stopActive()
            val deleted = plan.delete.filter { it != active }
            withContext(io) {
                deleted.forEach(store::delete)
                store.deletePartsExcept(wantedIds)
            }
            if (deleted.isNotEmpty()) log("Deleted ${deleted.size} songs outside the window")
            val stored = downloaded - deleted.toSet()
            _state.update {
                it.copy(
                    stored = stored.keys,
                    storedBytes = stored.values.sum(),
                    wanted = wantedIds,
                    storageFull = plan.storageFull,
                    aheadLimitedTo = plan.aheadLimitedTo
                )
            }
            if (active != null || !linked || !connection.ready.value) {
                null
            } else {
                plan.wanted.firstOrNull {
                    it.id !in stored &&
                        it.id !in unavailable &&
                        (failures[it.id] ?: 0) < MAX_FAILURES
                }
            }
        }
        sendInventory()
        next?.let { request(it.id, it.sizeBytes) }
    }

    private suspend fun request(id: String, size: Long) {
        val offset = lock.withLock {
            if (active != null) return
            active = id
            val have = withContext(io) { store.received(id) }
            activeStartedAt = now()
            activeStartOffset = have
            lastChunkAt = now()
            _state.update { it.copy(receiving = Progress(id, have, size)) }
            have
        }
        log("Requesting ${logId(id)} from ${offset / 1024} KB")
        if (!send(Message.SongRequest(id, offset))) {
            lock.withLock { stopActive() }
            return
        }
        watchdog?.cancel()
        watchdog = scope.launch {
            while (isActive) {
                delay(STALL_CHECK_MS)
                val stalled = lock.withLock {
                    (active == id && now() - lastChunkAt > STALL_MS).also { if (it) stopActive() }
                }
                if (stalled) {
                    log("${logId(id)} stalled; asking again")
                    // Not recompute() here: stopActive() cancelled this watchdog, so a suspending
                    // call in it would never run (the resume never happened).
                    scope.launch { recompute() }
                    return@launch
                }
            }
        }
    }

    private suspend fun onChunk(chunk: SongChunk) {
        val finished = lock.withLock {
            if (chunk.id != active) return
            val have = withContext(io) { store.received(chunk.id) }
            if (chunk.offset != have) return // left over from an earlier request
            withContext(io) { store.append(chunk.id, chunk.offset, chunk.data) }
            lastChunkAt = now()
            val done = have + chunk.data.size
            _state.update { it.copy(receiving = Progress(chunk.id, done, chunk.total)) }
            if (!chunk.last) return
            val ok = withContext(io) { store.finish(chunk.id) }
            val seconds = (now() - activeStartedAt) / 1000.0
            val megabytes = (done - activeStartOffset) / 1_048_576.0
            if (ok) {
                failures.remove(chunk.id)
                log(
                    "Received ${logId(chunk.id)}: %.1f MB in %.1f s (%.0f Mbit/s)%s".format(
                        megabytes,
                        seconds,
                        if (seconds > 0) megabytes * 8 / seconds else 0.0,
                        if (activeStartOffset >
                            0
                        ) {
                            ", resumed at ${activeStartOffset / 1024} KB"
                        } else {
                            ""
                        }
                    )
                )
            } else {
                failures[chunk.id] = (failures[chunk.id] ?: 0) + 1
                log("${logId(chunk.id)} failed verification; fetching again")
            }
            stopActive()
            true
        }
        if (finished) recompute()
    }

    private suspend fun onUnavailable(id: String) {
        lock.withLock {
            unavailable += id
            if (active == id) stopActive()
        }
        log("${logId(id)} isn't available on the partner's phone")
        recompute()
    }

    /** The partner asked for one of this phone's songs: stream it (one at a time). */
    private fun serve(request: Message.SongRequest) {
        sending?.cancel()
        sending = scope.launch {
            val song = library.value.songs.firstOrNull { it.id == request.id }
            val input = song?.let {
                withContext(io) { runCatching { openSong(it.uri) }.getOrNull() }
            }
            if (song == null || input == null) {
                send(Message.SongUnavailable(request.id))
                return@launch
            }
            log("Sending ${logId(song.id)} from ${request.offset / 1024} KB")
            try {
                input.use { stream ->
                    withContext(io) { stream.skipFully(request.offset) }
                    var offset = request.offset
                    val buffer = ByteArray(SongChunk.DATA_BYTES)
                    while (isActive) {
                        val read = withContext(io) { stream.readUpTo(buffer) }
                        if (read <= 0) break
                        val chunk = SongChunk(song.id, offset, song.sizeBytes, buffer.copyOf(read))
                        if (!connection.send(chunk)) break
                        offset += read
                        _state.update {
                            it.copy(sending = Progress(song.id, offset, song.sizeBytes))
                        }
                    }
                }
            } catch (e: IOException) {
                log("Sending ${logId(song.id)} failed: ${e.javaClass.simpleName}")
            } finally {
                _state.update { it.copy(sending = null) }
            }
        }
    }

    private suspend fun sendInventory() {
        if (!linked) return
        val songs = library.value
        val ids = songs.songs.map { it.id }.toSet() + songs.copyOf.keys + _state.value.stored
        if (ids == lastInventory) return
        if (send(Message.SongsOnPhone(ids.toList()))) lastInventory = ids
    }

    /** Call with [lock] held. */
    private fun stopActive() {
        active = null
        watchdog?.cancel()
        watchdog = null
        _state.update { it.copy(receiving = null) }
    }

    private fun logId(id: String) = "song-" + id.take(8)

    private fun InputStream.skipFully(bytes: Long) {
        var left = bytes
        while (left > 0) {
            val skipped = skip(left)
            if (skipped <= 0) {
                if (read() < 0) throw IOException("File shorter than the offset")
                left--
            } else {
                left -= skipped
            }
        }
    }

    /** Fills [buffer] as far as the stream allows, so chunks are full-size. */
    private fun InputStream.readUpTo(buffer: ByteArray): Int {
        var total = 0
        while (total < buffer.size) {
            val read = read(buffer, total, buffer.size - total)
            if (read < 0) break
            total += read
        }
        return total
    }

    companion object {
        const val STALL_MS = 20_000L
        const val STALL_CHECK_MS = 5_000L
        const val MAX_FAILURES = 3
    }
}
