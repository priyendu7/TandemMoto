package com.tandemmoto.playlist

import com.tandemmoto.library.FakeSongSource
import com.tandemmoto.library.InMemoryLibraryStore
import com.tandemmoto.library.Library
import com.tandemmoto.link.ChannelState
import com.tandemmoto.link.Partner
import com.tandemmoto.state.Envelope
import com.tandemmoto.state.Framing
import com.tandemmoto.state.Message
import com.tandemmoto.state.MessageCodec
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InMemoryRidePlaylistStore(var data: RidePlaylistData = RidePlaylistData()) :
    RidePlaylistStore {
    override suspend fun load() = data

    override suspend fun save(data: RidePlaylistData) {
        this.data = data
    }
}

/** One phone: its songs, ride playlist and sync, linked to another [Phone] in memory. */
class Phone(
    val id: String,
    role: Partner.Role,
    scope: CoroutineScope,
    dispatcher: kotlinx.coroutines.CoroutineDispatcher,
    val rideStore: InMemoryRidePlaylistStore = InMemoryRidePlaylistStore(),
    val source: FakeSongSource = FakeSongSource()
) {
    val ride = RidePlaylist(rideStore, { id }, scope)
    val library =
        Library(source, InMemoryLibraryStore(), scope, io = dispatcher, partnerSongs = {
            ride.partnerSongs
        })
    val channel = MutableStateFlow<ChannelState>(ChannelState.Idle)
    val incoming = MutableSharedFlow<Message>(extraBufferCapacity = 10_000)
    val partner = MutableStateFlow<Partner?>(Partner("partner", "addr", role, 0L))
    var other: Phone? = null
    var largestFrame = 0

    private val sync = PlaylistSync(
        ride,
        library,
        channel,
        incoming,
        send = { message ->
            val to = other
            if (to == null || channel.value !is ChannelState.Open) {
                false
            } else {
                // Through the real encoding, as on the socket.
                val bytes = MessageCodec.encode(message, 0)
                largestFrame = maxOf(largestFrame, bytes.size)
                to.incoming.emit((MessageCodec.decode(bytes) as Envelope.Known).message)
                true
            }
        },
        partner = partner,
        scope = scope
    )

    init {
        ride.start()
        library.start()
        sync.start()
    }

    fun hello() = Message.Hello("0.1.0", id, null, "x")

    /** Adds songs by title; each is its own file on this phone. */
    fun add(vararg titles: String, durationMs: Long = 200_000) {
        titles.forEach { source.file("$id/$it", it, durationMs = durationMs) }
        library.addFiles(titles.map { "$id/$it" })
    }

    val titles get() = ride.state.value.songs.map { it.title }
}

@OptIn(ExperimentalCoroutinesApi::class)
class PlaylistSyncTest {
    private fun TestScope.phones(
        initiator: String = "S25",
        acceptor: String = "Redmi"
    ): Pair<Phone, Phone> {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val a = Phone(initiator, Partner.Role.Initiator, backgroundScope, dispatcher)
        val b = Phone(acceptor, Partner.Role.Acceptor, backgroundScope, dispatcher)
        runCurrent()
        return a to b
    }

    private fun TestScope.link(a: Phone, b: Phone) {
        a.other = b
        b.other = a
        a.channel.value = ChannelState.Open(b.hello())
        b.channel.value = ChannelState.Open(a.hello())
        runCurrent()
    }

    private fun TestScope.unlink(a: Phone, b: Phone) {
        a.channel.value = ChannelState.Opening
        b.channel.value = ChannelState.Opening
        runCurrent()
    }

    @Test
    fun songsAddedOnEitherPhoneShowOnBothWhileLinked() = runTest {
        val (s25, redmi) = phones()
        link(s25, redmi)
        s25.add("One", "Two")
        runCurrent()
        redmi.add("Three")
        runCurrent()
        assertEquals(listOf("One", "Two", "Three"), s25.titles)
        assertEquals(s25.titles, redmi.titles)
        assertEquals(s25.id, redmi.ride.state.value.songs.first().owner)
    }

    @Test
    fun removingAndMovingOnEitherPhoneReachesTheOther() = runTest {
        val (s25, redmi) = phones()
        link(s25, redmi)
        s25.add("One", "Two", "Three")
        runCurrent()
        redmi.ride.move(2, 0)
        runCurrent()
        assertEquals(listOf("Three", "One", "Two"), s25.titles)
        s25.ride.remove(redmi.ride.state.value.songs[1].id)
        runCurrent()
        assertEquals(listOf("Three", "Two"), redmi.titles)
        assertEquals(redmi.titles, s25.titles)
    }

    @Test
    fun editsMadeApartAllSurviveTheReconnect() = runTest {
        val (s25, redmi) = phones()
        link(s25, redmi)
        s25.add("One", "Two", "Three")
        runCurrent()
        unlink(s25, redmi)

        s25.add("Four") // added on each phone while apart
        redmi.add("Five")
        runCurrent()
        s25.ride.move(2, 0) // Three to the top on the S25
        val twoId = redmi.ride.state.value.songs.first { it.title == "Two" }.id
        redmi.ride.remove(twoId) // and Two removed on the Redmi
        runCurrent()

        link(s25, redmi)
        assertEquals(s25.titles, redmi.titles)
        assertEquals(setOf("One", "Three", "Four", "Five"), s25.titles.toSet())
        assertEquals("Three", s25.titles.first())
    }

    @Test
    fun theFirstTimeTwoListsMeetTheInitiatorsSongsComeFirst() = runTest {
        val (s25, redmi) = phones()
        s25.add("S1", "S2") // both built lists before ever syncing
        redmi.add("R1", "R2")
        runCurrent()
        link(s25, redmi)
        assertEquals(listOf("S1", "S2", "R1", "R2"), s25.titles)
        assertEquals(s25.titles, redmi.titles)
    }

    @Test
    fun aBigListIsSentInFramesUnder64Kb() = runTest {
        val (s25, redmi) = phones()
        s25.add(*Array(450) { "A fairly long song title number $it for the frame size test" })
        runCurrent()
        link(s25, redmi)
        assertEquals(450, redmi.titles.size)
        assertTrue(s25.largestFrame < Framing.MAX_FRAME_BYTES)
    }

    @Test
    fun theSameFileOnBothPhonesIsOneSong() = runTest {
        val (s25, redmi) = phones()
        link(s25, redmi)
        s25.add("Shared")
        runCurrent()
        // The Redmi has the very same file (same fingerprint).
        redmi.source.files["redmi/copy"] = s25.source.files["S25/Shared"]
        redmi.library.addFiles(listOf("redmi/copy"))
        runCurrent()
        assertEquals(listOf("Shared"), redmi.titles)
        assertEquals(listOf("Shared"), s25.titles)
    }

    @Test
    fun theSameTrackInAnotherFileBecomesThisPhonesCopy() = runTest {
        val (s25, redmi) = phones()
        link(s25, redmi)
        s25.add("Highway Star")
        runCurrent()
        redmi.add("highway star", durationMs = 201_000) // its own FLAC of the same track
        runCurrent()
        assertEquals(listOf("Highway Star"), redmi.titles)
        val songId = redmi.ride.state.value.songs.single().id
        assertTrue(redmi.library.state.value.hasFile(songId)) // plays its own copy (#50, #51)
    }

    @Test
    fun removingThePartnersSongLetsTheOwnerGoOfTheFile() = runTest {
        val (s25, redmi) = phones()
        link(s25, redmi)
        s25.add("One")
        runCurrent()
        assertTrue("S25/One" in s25.source.held)
        redmi.ride.remove(redmi.ride.state.value.songs.single().id)
        runCurrent()
        assertTrue(s25.titles.isEmpty())
        assertFalse("S25/One" in s25.source.held)
    }

    @Test
    fun aNewPartnerKeepsYourSongsAndDropsThePreviousPartners() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val (s25, redmi) = phones()
        link(s25, redmi)
        s25.add("Mine")
        redmi.add("Old partner's")
        runCurrent()
        unlink(s25, redmi)

        // The S25 pairs with a third phone.
        val moto = Phone("Moto", Partner.Role.Acceptor, backgroundScope, dispatcher)
        runCurrent()
        moto.add("New partner's")
        runCurrent()
        link(s25, moto)
        assertEquals(listOf("Mine", "New partner's"), s25.titles)
        assertEquals(s25.titles, moto.titles)
    }

    @Test
    fun theListSurvivesARestart() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val (s25, redmi) = phones()
        link(s25, redmi)
        s25.add("One", "Two")
        runCurrent()
        s25.ride.move(1, 0)
        runCurrent()
        val restarted =
            Phone("S25", Partner.Role.Initiator, backgroundScope, dispatcher, s25.rideStore)
        runCurrent()
        assertEquals(listOf("Two", "One"), restarted.titles)
    }
}
