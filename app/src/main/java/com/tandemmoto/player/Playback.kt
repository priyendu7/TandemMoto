package com.tandemmoto.player

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import com.tandemmoto.library.LibraryState
import com.tandemmoto.playlist.RideEntry
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What Home, the Playlist and the notification show about playback. */
data class PlaybackState(
    val hasSongs: Boolean = false,
    val currentId: String? = null,
    val title: String? = null,
    val artist: String? = null,
    val isPlaying: Boolean = false,
    /** The user wants it playing (it may be waiting for the song: [gettingSong]). */
    val playWhenReady: Boolean = false,
    /** The current song isn't on this phone yet: "Getting song…". */
    val gettingSong: Boolean = false
)

/**
 * The local player (#51): ExoPlayer playing the ride playlist in its order, and a MediaSession
 * so the lock screen, the notification and headset buttons control it. Every song is queued as
 * `tandem://song/<id>` and resolved when it's opened ([SongDataSource]), so a partner's song
 * that isn't here yet just waits for the song window (#50) to bring it. Main thread only.
 */
@OptIn(UnstableApi::class) // Media3's data source and media source APIs; contained here
class Playback(
    context: Context,
    private val scope: CoroutineScope,
    private val songs: StateFlow<List<RideEntry>>,
    private val library: StateFlow<LibraryState>,
    private val downloaded: (String) -> File?,
    /** Linked with the partner, and whether its songs can't fit: for the wait rules. */
    private val linked: () -> Boolean,
    private val storageFull: () -> Boolean,
    /** The current song's place, for the song window (#50). */
    private val currentIndex: MutableStateFlow<Int>,
    private val log: (String) -> Unit = {},
    private val now: () -> Long = System::currentTimeMillis
) : SongAvailability {
    private val _state = MutableStateFlow(PlaybackState())
    val state: StateFlow<PlaybackState> = _state.asStateFlow()

    // Read from the player's loading thread.
    @Volatile
    private var currentId: String? = null

    @Volatile
    private var currentSince = now()

    @Volatile
    private var waitingFor: String? = null

    val player: ExoPlayer
    val session: MediaSession

    init {
        val files = DataSource.Factory {
            SongDataSource(DefaultDataSource.Factory(context).createDataSource(), this)
        }
        player = ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(files))
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                // Pause for calls and other apps' audio.
                true
            )
            .setHandleAudioBecomingNoisy(true) // pause when earphones are unplugged
            .build()
        // Media3 keeps session IDs process-wide; one app instance on a phone, but a unique ID
        // lets tests create the app again in the same process.
        session = MediaSession.Builder(context, player)
            .setId("tandemmoto-${System.identityHashCode(this)}")
            .build()
        player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) =
                onCurrentChanged()

            override fun onEvents(player: Player, events: Player.Events) = publish()

            override fun onPlayerError(error: PlaybackException) = skipUnplayable(error)
        })
    }

    fun start() {
        scope.launch { songs.collect(::syncQueue) }
    }

    fun togglePlay() {
        if (player.isPlaying || player.playWhenReady) player.pause() else play()
    }

    fun play() {
        if (player.mediaItemCount == 0) return
        if (player.playbackState == Player.STATE_IDLE ||
            player.playbackState == Player.STATE_ENDED
        ) {
            if (player.playbackState == Player.STATE_ENDED) player.seekTo(0, 0)
            player.prepare()
        }
        player.play()
    }

    fun pause() = player.pause()

    fun next() {
        if (player.hasNextMediaItem()) player.seekToNextMediaItem()
    }

    fun previous() = player.seekToPrevious()

    /** Plays from the song at [index] of the ride playlist (a tap in Playlist). */
    fun playAt(index: Int) {
        if (index !in 0 until player.mediaItemCount) return
        player.seekTo(index, 0)
        play()
    }

    fun release() {
        session.release()
        player.release()
    }

    // ---- SongAvailability (player's loading thread) ----

    override fun find(id: String): SongFileState = SongFiles.find(id, library.value, downloaded)

    override fun decide(id: String): WaitDecision =
        WaitRules.decide(id == currentId, now() - currentSince, linked(), storageFull())

    override fun waiting(id: String, waiting: Boolean) {
        waitingFor = if (waiting) id else waitingFor.takeUnless { it == id }
        _state.update { it.copy(gettingSong = waitingFor != null && waitingFor == currentId) }
    }

    // ---- Player ----

    /** Brings the queue to the ride playlist's order in place (the current song plays on). */
    private fun syncQueue(entries: List<RideEntry>) {
        val queue = (0 until player.mediaItemCount).map { player.getMediaItemAt(it).mediaId }
        val byId = entries.associateBy { it.id }
        for (step in QueueSync.steps(queue, entries.map { it.id })) {
            when (step) {
                is QueueStep.Remove -> player.removeMediaItem(step.index)
                is QueueStep.Add -> player.addMediaItem(step.index, item(byId.getValue(step.id)))
                is QueueStep.Move -> player.moveMediaItem(step.from, step.to)
            }
        }
        if (queue.isEmpty() && entries.isNotEmpty()) player.prepare()
        onCurrentChanged()
    }

    private fun item(entry: RideEntry) = MediaItem.Builder()
        .setMediaId(entry.id)
        .setUri(SongDataSource.uriFor(entry.id))
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(entry.title)
                .setArtist(entry.artist)
                .setIsPlayable(true)
                .build()
        )
        .build()

    private fun onCurrentChanged() {
        val id = player.currentMediaItem?.mediaId
        if (id != currentId) {
            currentId = id
            currentSince = now()
        }
        if (player.mediaItemCount > 0) currentIndex.value = player.currentMediaItemIndex
        publish()
    }

    /**
     * A song that can't play (never arrived, or its file broke): go on with the next one rather
     * than stop, as long as there is one.
     */
    private fun skipUnplayable(error: PlaybackException) {
        log("Skipping song-${currentId?.take(8)}: ${error.errorCodeName}")
        val wasPlaying = player.playWhenReady
        if (player.hasNextMediaItem()) {
            player.seekToNextMediaItem()
            player.prepare()
            if (wasPlaying) player.play()
        } else {
            player.stop()
        }
    }

    private fun publish() {
        val metadata = player.currentMediaItem?.mediaMetadata
        _state.update {
            it.copy(
                hasSongs = player.mediaItemCount > 0,
                currentId = player.currentMediaItem?.mediaId,
                title = metadata?.title?.toString(),
                artist = metadata?.artist?.toString(),
                isPlaying = player.isPlaying,
                playWhenReady = player.playWhenReady,
                gettingSong = waitingFor != null && waitingFor == player.currentMediaItem?.mediaId
            )
        }
    }
}
