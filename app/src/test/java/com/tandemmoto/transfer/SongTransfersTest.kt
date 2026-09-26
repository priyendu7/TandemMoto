package com.tandemmoto.transfer

import com.tandemmoto.library.Fingerprint
import com.tandemmoto.library.LibraryState
import com.tandemmoto.library.Song
import com.tandemmoto.link.ChannelState
import com.tandemmoto.link.Endpoint
import com.tandemmoto.link.FrameConnection
import com.tandemmoto.link.FrameTransport
import com.tandemmoto.link.PipeEnd
import com.tandemmoto.link.connectionPair
import com.tandemmoto.playlist.RideEntry
import com.tandemmoto.playlist.RidePlaylistState
import com.tandemmoto.playlist.Stamp
import com.tandemmoto.state.Message
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import kotlin.random.Random
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** A transport joining a group owner's accept() to a client's connect(), in memory. */
class PairedTransport {
    private val waiting = Channel<PipeEnd>(Channel.UNLIMITED)
    val ends = mutableListOf<PipeEnd>()

    val owner = object : FrameTransport {
        override suspend fun accept(port: Int): FrameConnection = waiting.receive()

        override suspend fun connect(host: String, port: Int) = throw IOException("owner")
    }

    val client = object : FrameTransport {
        override suspend fun accept(port: Int) = throw IOException("client")

        override suspend fun connect(host: String, port: Int): FrameConnection {
            val (a, b) = connectionPair()
            ends += a
            ends += b
            waiting.send(a)
            return b
        }
    }

    /** The Wi-Fi link blinks: every open transfer connection breaks. */
    fun drop() = ends.forEach { it.close() }
}

/** One phone's transfer side. */
class TransferPhone(
    val id: String,
    owner: Boolean,
    transport: FrameTransport,
    scope: CoroutineScope,
    testScope: TestScope,
    songs: List<RideEntry>,
    mySongs: List<Song>,
    val files: Map<String, ByteArray>
) {
    val playlist = MutableStateFlow(RidePlaylistState(songs, id, loaded = true))
    val library = MutableStateFlow(LibraryState(songs = mySongs, loaded = true))
    val settings = MutableStateFlow(WindowSettings())
    val current = MutableStateFlow(0)
    val channel = MutableStateFlow<ChannelState>(ChannelState.Opening)
    val incoming = MutableSharedFlow<Message>(extraBufferCapacity = 1_000)
    val store = SongStore(Files.createTempDirectory(id).toFile())
    var free = 50_000L * 1024 * 1024
    var other: TransferPhone? = null
    val logs = mutableListOf<String>()

    /** False drops a message on its way to the other phone. */
    var deliver: (Message) -> Boolean = { true }

    /** Called with a stream to send, so a test can break or corrupt it. */
    var wrap: (String, InputStream) -> InputStream = { _, s -> s }

    val transfers = SongTransfers(
        playlist = playlist,
        library = library,
        currentIndex = current,
        settings = settings,
        channelState = channel,
        incoming = incoming,
        send = { message ->
            val to = other
            if (to != null && deliver(message)) to.incoming.emit(message)
            to != null
        },
        endpoint = MutableStateFlow(Endpoint(owner, "192.168.49.1")),
        connection = TransferConnection(transport, scope) { logs += "conn: $it" },
        store = store,
        // Like a real phone: stored songs use up free space.
        freeBytes = { free - store.stored().values.sum() },
        openSong = { uri -> files[uri]?.let { wrap(uri, it.inputStream()) } },
        scope = scope,
        log = { logs += it },
        io = StandardTestDispatcher(testScope.testScheduler),
        now = { testScope.testScheduler.currentTime }
    )

    init {
        transfers.start()
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class SongTransfersTest {
    private val random = Random(50)

    /** [count] songs of [size] bytes, all the S25's. */
    private fun songs(count: Int, size: Int = 200_000): List<Pair<RideEntry, ByteArray>> =
        (0 until count).map { i ->
            val bytes = random.nextBytes(size)
            val id = Fingerprint.of(bytes.inputStream())
            RideEntry(
                id, "Song $i", null, 1, size.toLong(), "S25",
                Stamp(
                    1,
                    "S25"
                ),
                "V$i", Stamp(1, "S25")
            ) to
                bytes
        }

    private fun TestScope.phones(
        songs: List<Pair<RideEntry, ByteArray>>
    ): Pair<TransferPhone, TransferPhone> {
        val transport = PairedTransport()
        val entries = songs.map { it.first }
        val mine = songs.map { (e, _) -> Song(e.id, "uri-${e.id}", e.title, null, 1, e.sizeBytes) }
        val files = songs.associate { (e, b) -> "uri-${e.id}" to b }
        val s25 =
            TransferPhone("S25", true, transport.owner, backgroundScope, this, entries, mine, files)
        val redmi =
            TransferPhone(
                "Redmi",
                false,
                transport.client,
                backgroundScope,
                this,
                entries,
                emptyList(),
                emptyMap()
            )
        s25.other = redmi
        redmi.other = s25
        this@SongTransfersTest.transport = transport
        return s25 to redmi
    }

    private lateinit var transport: PairedTransport

    private fun TestScope.link(a: TransferPhone, b: TransferPhone) {
        a.channel.value = ChannelState.Open(Message.Hello("0.1.0", b.id, null, "x"))
        b.channel.value = ChannelState.Open(Message.Hello("0.1.0", a.id, null, "x"))
        advanceTimeBy(2_000)
    }

    @Test
    fun thePartnersSongsArriveVerifiedAndBothPhonesKnow() = runTest {
        val songs = songs(3)
        val (s25, redmi) = phones(songs)
        link(s25, redmi)
        assertEquals(songs.map { it.first.id }.toSet(), redmi.store.stored().keys)
        songs.forEach { (e, bytes) ->
            assertTrue(bytes.contentEquals(redmi.store.file(e.id).readBytes()))
        }
        assertEquals(songs.map { it.first.id }.toSet(), s25.transfers.state.value.partnerHas)
        assertTrue(redmi.logs.any { it.startsWith("Received song-") })
    }

    @Test
    fun onlyTheWindowIsDownloaded() = runTest {
        val songs = songs(12)
        val (s25, redmi) = phones(songs)
        link(s25, redmi)
        // Current (the first) + 7 ahead; nothing behind the first song.
        assertEquals(songs.take(8).map { it.first.id }.toSet(), redmi.store.stored().keys)
    }

    @Test
    fun aDropMidSongResumesFromWhatArrived() = runTest {
        val songs = songs(1, size = 1_000_000)
        val (s25, redmi) = phones(songs)
        var dropped = false
        s25.wrap = { _, stream ->
            object : FilterInputStream(stream) {
                var sent = 0
                override fun read(b: ByteArray, off: Int, len: Int): Int {
                    if (!dropped && sent > 400_000) {
                        dropped = true
                        transport.drop()
                    }
                    return super.read(b, off, len).also { if (it > 0) sent += it }
                }
            }
        }
        link(s25, redmi)
        advanceTimeBy(3_000) // resumes once the connection is back, not after the stall check
        assertTrue(dropped)
        assertEquals(
            redmi.logs.toString() + s25.logs,
            setOf(songs.single().first.id),
            redmi.store.stored().keys
        )
        assertTrue(redmi.logs.toString(), redmi.logs.any { it.contains("resumed at") })
    }

    @Test
    fun aRequestThatGetsNoAnswerIsAskedForAgainAfterTheStallTime() = runTest {
        val songs = songs(1)
        val (s25, redmi) = phones(songs)
        // The partner's phone ignores requests for the first 10 s (e.g. its app was busy).
        redmi.deliver =
            { message -> !(message is Message.SongRequest && testScheduler.currentTime < 10_000) }
        link(s25, redmi)
        assertTrue(redmi.store.stored().isEmpty())
        advanceTimeBy(SongTransfers.STALL_MS + SongTransfers.STALL_CHECK_MS * 2)
        assertTrue(redmi.logs.any { it.contains("stalled") })
        assertEquals(setOf(songs.single().first.id), redmi.store.stored().keys)
    }

    @Test
    fun switchingSongsMidTransferKeepsTheConnectionOpen() = runTest {
        // Phone test on #51: stopping a send by cancelling it interrupted the socket and closed
        // the whole transfer connection.
        val songs = songs(12, size = 2_000_000)
        val (s25, redmi) = phones(songs)
        link(s25, redmi)
        // Mid-way, the Redmi's current song jumps far ahead: the first songs leave the window.
        redmi.current.value = 11
        advanceTimeBy(10_000)
        assertFalse(s25.logs.toString(), s25.logs.any { it == "conn: Transfer connection closed" })
        assertTrue(songs[11].first.id in redmi.store.stored().keys)
    }

    @Test
    fun theSongThatsPlayingComesBeforeOtherDownloads() = runTest {
        val songs = songs(12)
        val (s25, redmi) = phones(songs)
        // The request for the second song gets no answer, so it stays the active download.
        redmi.deliver = { message ->
            !(message is Message.SongRequest && message.id == songs[1].first.id)
        }
        link(s25, redmi)
        assertEquals(setOf(songs[0].first.id), redmi.store.stored().keys)
        // Playback jumps to the third song: it doesn't wait for the second (20 s stall).
        redmi.current.value = 2
        advanceTimeBy(2_000)
        assertTrue(redmi.logs.toString(), songs[2].first.id in redmi.store.stored().keys)
        assertTrue(redmi.logs.any { it.contains("is playing: it comes before") })
    }

    @Test
    fun aCorruptedSongIsFetchedAgain() = runTest {
        val songs = songs(1)
        val (s25, redmi) = phones(songs)
        var corrupted = false
        s25.wrap = { _, stream ->
            if (corrupted) {
                stream
            } else {
                corrupted = true
                val bytes = stream.readBytes().also { it[100] = (it[100] + 1).toByte() }
                bytes.inputStream()
            }
        }
        link(s25, redmi)
        advanceTimeBy(1_000)
        assertTrue(redmi.logs.any { it.contains("failed verification") })
        assertEquals(setOf(songs.single().first.id), redmi.store.stored().keys)
        assertTrue(
            songs.single().second.contentEquals(
                redmi.store.file(songs.single().first.id).readBytes()
            )
        )
    }

    @Test
    fun aSongThePartnerCantSendIsSkipped() = runTest {
        val songs = songs(2)
        val (s25, redmi) = phones(songs)
        // The S25's first file is gone.
        s25.library.value = s25.library.value.copy(songs = s25.library.value.songs.drop(1))
        link(s25, redmi)
        assertEquals(setOf(songs[1].first.id), redmi.store.stored().keys)
        assertTrue(redmi.logs.any { it.contains("isn't available") })
    }

    @Test
    fun songsThatLeaveTheWindowAreDeleted() = runTest {
        val songs = songs(12)
        val (s25, redmi) = phones(songs)
        link(s25, redmi)
        // The first song moves to the end: it leaves the window, the 9th joins.
        val moved = songs.map { it.first }.let { it.drop(1) + it.first() }
        redmi.playlist.value = redmi.playlist.value.copy(songs = moved)
        advanceTimeBy(2_000)
        assertEquals(moved.take(8).map { it.id }.toSet(), redmi.store.stored().keys)
    }

    @Test
    fun aSmallerWindowDeletesTheExtraSongs() = runTest {
        val songs = songs(12)
        val (s25, redmi) = phones(songs)
        link(s25, redmi)
        redmi.settings.value = WindowSettings(behind = 0, ahead = 3)
        advanceTimeBy(1_000)
        assertEquals(songs.take(4).map { it.first.id }.toSet(), redmi.store.stored().keys)
    }

    @Test
    fun removingAllFreesTheSpaceAndTheWindowRefillsWhileLinked() = runTest {
        val songs = songs(3)
        val (s25, redmi) = phones(songs)
        link(s25, redmi)
        redmi.channel.value = ChannelState.Opening // not linked: nothing comes back yet
        runCurrent()
        redmi.transfers.removeAll()
        advanceTimeBy(1_000)
        assertTrue(redmi.store.stored().isEmpty())
        assertEquals(songs.map { it.first.id }.toSet(), redmi.transfers.state.value.wanted)
        assertFalse(redmi.transfers.state.value.linked)

        link(s25, redmi)
        assertEquals(3, redmi.store.stored().size)
    }

    @Test
    fun lowSpaceShrinksTheWindow() = runTest {
        val songs = songs(12, size = 10 * 1024 * 1024)
        val (s25, redmi) = phones(songs)
        redmi.free = SongWindow.FLOOR_FREE_BYTES + 35L * 1024 * 1024 // room for 3 songs
        link(s25, redmi)
        advanceTimeBy(10_000)
        assertEquals(songs.take(3).map { it.first.id }.toSet(), redmi.store.stored().keys)
        assertEquals(2, redmi.transfers.state.value.aheadLimitedTo)
    }
}
