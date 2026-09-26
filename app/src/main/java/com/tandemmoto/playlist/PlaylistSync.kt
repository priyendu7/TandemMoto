package com.tandemmoto.playlist

import com.tandemmoto.library.Library
import com.tandemmoto.link.ChannelState
import com.tandemmoto.link.Partner
import com.tandemmoto.state.Message
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Keeps the ride playlist in step with the partner's phone and this phone's files (#49):
 * - songs added in [library] join the playlist at the end;
 * - on every connection, both phones send their whole list and merge the other's; after that,
 *   each edit sends just the changed songs;
 * - a song that leaves the playlist (removed on either phone, or a previous partner's) lets go of
 *   this phone's file for it: its own song, or its copy of the partner's.
 */
class PlaylistSync(
    private val playlist: RidePlaylist,
    private val library: Library,
    private val channelState: StateFlow<ChannelState>,
    private val incoming: Flow<Message>,
    private val send: suspend (Message) -> Boolean,
    private val partner: StateFlow<Partner?>,
    private val scope: CoroutineScope,
    private val log: (String) -> Unit = {}
) {
    /** Songs in the partner's full list so far this connection (the combine needs all of them). */
    private val received = mutableSetOf<String>()

    fun start() {
        scope.launch { library.added.collect { playlist.addMine(it) } }
        scope.launch { joinExistingSongs() }
        scope.launch {
            playlist.gone.collect { entries ->
                val me = playlist.state.value.me
                entries.forEach { entry ->
                    if (entry.owner == me) library.remove(entry.id)
                    library.removeCopyOf(entry.id)
                }
            }
        }
        scope.launch { playlist.outgoing.collect { sendInBatches(it, complete = false) } }
        scope.launch {
            incoming.filterIsInstance<Message.PlaylistEntries>().collect { onEntries(it) }
        }
        scope.launch {
            channelState.collect { state ->
                if (state is ChannelState.Open) onConnected(state.partner)
            }
        }
    }

    /**
     * Songs this phone has that aren't in the ride playlist at all (added before it existed: the
     * #48 builds kept them in My songs only) join it at the end, in their saved order.
     */
    private suspend fun joinExistingSongs() {
        playlist.state.first { it.loaded }
        val songs = library.state.first { it.loaded }
        val known = playlist.snapshot().map { it.id }.toSet()
        val copies = songs.copyOf.values.toSet()
        val missing = songs.songs.filter { it.id !in known && it.id !in copies }
        if (missing.isNotEmpty()) {
            log("Adding ${missing.size} of this phone's songs to the ride playlist")
            playlist.addMine(missing)
        }
    }

    private suspend fun onConnected(hello: Message.Hello) {
        received.clear()
        val iAmInitiator = partner.value?.role == Partner.Role.Initiator
        val dropped = playlist.meetPartner(hello.installId, iAmInitiator)
        // A previous partner's song this phone had its own copy of: that copy is a song again.
        dropped.mapNotNull { library.releaseCopy(it.id) }
            .takeIf { it.isNotEmpty() }
            ?.let { playlist.addMine(it) }
        val all = playlist.snapshot()
        log("Sending the playlist (${all.size} entries)")
        sendInBatches(all, complete = true)
    }

    private suspend fun onEntries(message: Message.PlaylistEntries) {
        received += message.entries.map { it.id }
        playlist.merge(message.entries)
        if (message.complete && playlist.combinePending) {
            log("Combining lists: the initiator's songs first")
            playlist.combineAfter(received.toSet())
        }
    }

    /** Frames are at most 64 KB; a batch of [BATCH] songs stays well under it. */
    private suspend fun sendInBatches(entries: List<RideEntry>, complete: Boolean) {
        if (entries.isEmpty()) {
            if (complete) send(Message.PlaylistEntries(emptyList(), complete = true))
            return
        }
        val batches = entries.chunked(BATCH)
        batches.forEachIndexed { i, batch ->
            send(Message.PlaylistEntries(batch, complete = complete && i == batches.lastIndex))
        }
    }

    companion object {
        const val BATCH = 100
    }
}
