package com.tandemmoto.player

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import com.tandemmoto.library.LibraryState
import com.tandemmoto.playlist.RideEntry
import java.io.File
import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
    val gettingSong: Boolean = false,
    /** Songs whose audio format this phone can't decode (skipped; phone test on #51). */
    val cantPlay: Set<String> = emptySet(),
    /** Where the current song is, for Home's seek bar (updated every 0.5 s while playing). */
    val positionMs: Long = 0,
    /** The current song's length; 0 while unknown. */
    val durationMs: Long = 0
)

/**
 * The local player (#51): ExoPlayer playing the ride playlist in its order, and a MediaSession
 * so the lock screen, the notification and headset buttons control it. Every song is queued as
 * `tandem://song/<id>` and resolved when it's opened ([SongDataSource]), so a partner's song
 * that isn't here yet just waits for the song window (#50) to bring it. Main thread only.
 *
 * Every control (Home, Playlist, the notification, and through [session] the lock screen and
 * earbuds) goes through the public methods here, which tell [listener] so the partner's phone
 * follows (#60, [PlaybackMirror]); [apply] is the partner's state, which isn't told back.
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
) : SongAvailability,
    LocalPlayer {
    /** Told about controls, so the partner's phone follows ([PlaybackMirror]). */
    var listener: PlaybackListener? = null

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

    private var ticker: Job? = null

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
        // The session's commands (lock screen, earbuds, Bluetooth) come in as controls too.
        session = MediaSession.Builder(context, Controls(player))
            .setId("tandemmoto-${System.identityHashCode(this)}")
            .build()
        player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                onCurrentChanged()
                // The song ended and the next began: both phones move on together.
                if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) control("SongEnd")
            }

            override fun onEvents(player: Player, events: Player.Events) = publish()

            override fun onPlayerError(error: PlaybackException) = skipUnplayable(error)

            override fun onTracksChanged(tracks: Tracks) = checkDecodable(tracks)

            override fun onIsPlayingChanged(isPlaying: Boolean) = tick(isPlaying)

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                // Controls tell the listener themselves; these pause on their own. A short
                // interruption (a navigation prompt, a call) only suppresses playback and
                // resumes by itself, so it stays on this phone.
                when (reason) {
                    Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_BECOMING_NOISY ->
                        control("EarphonesOut")
                    Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS ->
                        control("AudioFocusLost")
                    else -> Unit
                }
            }
        })
    }

    fun start() {
        scope.launch { songs.collect(::syncQueue) }
    }

    fun togglePlay() {
        if (player.isPlaying || player.playWhenReady) pause() else play()
    }

    fun play() {
        if (!startPlaying()) return
        control("Play")
    }

    fun pause() {
        player.pause()
        control("Pause")
    }

    fun next() {
        if (!player.hasNextMediaItem()) return
        player.seekToNextMediaItem()
        control("Next")
    }

    /** Over 3 s into a song it starts again; otherwise the previous song (Media3's rule). */
    fun previous() {
        player.seekToPrevious()
        control("Previous")
    }

    /** Plays from the song at [index] of the ride playlist (a tap in Playlist). */
    fun playAt(index: Int) {
        if (index !in 0 until player.mediaItemCount) return
        player.seekTo(index, 0)
        startPlaying()
        control("PlaylistTap")
    }

    /** Home's seek bar, when it's let go. */
    fun seekTo(positionMs: Long) {
        if (player.mediaItemCount == 0) return
        player.seekTo(positionMs.coerceAtLeast(0))
        publish()
        control("Seek")
    }

    fun release() {
        ticker?.cancel()
        session.release()
        player.release()
    }

    // ---- LocalPlayer (the partner's state, from PlaybackMirror) ----

    override fun position() = PlayerPosition(
        songId = player.currentMediaItem?.mediaId,
        playing = player.playWhenReady,
        positionMs = player.currentPosition
    )

    override fun hasSong(songId: String) = indexOf(songId) >= 0

    override fun apply(songId: String, positionMs: Long, playing: Boolean) {
        val index = indexOf(songId)
        if (index < 0) return
        val offMs = abs(player.currentPosition - positionMs)
        if (index != player.currentMediaItemIndex || offMs > APPLY_TOLERANCE_MS || !playing) {
            player.seekTo(index, positionMs)
        }
        if (playing) startPlaying() else player.pause()
        publish()
    }

    private fun indexOf(songId: String) = (0 until player.mediaItemCount).firstOrNull {
        player.getMediaItemAt(it).mediaId == songId
    } ?: -1

    private fun control(name: String) {
        listener?.onControl(name)
    }

    /** Prepares if needed and plays; false with nothing to play. */
    private fun startPlaying(): Boolean {
        if (player.mediaItemCount == 0) return false
        if (player.playbackState == Player.STATE_IDLE ||
            player.playbackState == Player.STATE_ENDED
        ) {
            if (player.playbackState == Player.STATE_ENDED) player.seekTo(0, 0)
            player.prepare()
        }
        player.play()
        return true
    }

    /** Home's seek bar moves every [TICK_MS] while playing. */
    private fun tick(isPlaying: Boolean) {
        ticker?.cancel()
        if (!isPlaying) return
        ticker = scope.launch {
            while (true) {
                delay(TICK_MS)
                publish()
            }
        }
    }

    // ---- SongAvailability (player's loading thread) ----

    override fun find(id: String): SongFileState = SongFiles.find(id, library.value, downloaded)

    override fun decide(id: String): WaitDecision =
        WaitRules.decide(id == currentId, now() - currentSince, linked(), storageFull())

    override fun waiting(id: String, waiting: Boolean) {
        log("song-${id.take(8)} ${if (waiting) "isn't here yet: waiting" else "stopped waiting"}")
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
        listener?.onQueueChanged()
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
        val cause = error.cause?.let { " (${it.javaClass.simpleName}: ${it.message})" }.orEmpty()
        log("Skipping song-${currentId?.take(8)}: ${error.errorCodeName}$cause")
        val wasPlaying = player.playWhenReady
        if (player.hasNextMediaItem()) {
            player.seekToNextMediaItem()
            player.prepare()
            if (wasPlaying) player.play()
        } else {
            player.stop()
        }
        control("SkipUnplayable")
    }

    /**
     * A song whose audio this phone can't decode doesn't raise an error: ExoPlayer just has no
     * audio track to play (the Redmi Y2 on Android 9 with two large files, #51 phone test). Mark
     * it, log its format, and move on.
     */
    private fun checkDecodable(tracks: Tracks) {
        val id = player.currentMediaItem?.mediaId ?: return
        if (!tracks.containsType(C.TRACK_TYPE_AUDIO) ||
            tracks.isTypeSupported(C.TRACK_TYPE_AUDIO)
        ) {
            return
        }
        val format = tracks.groups.firstOrNull { it.type == C.TRACK_TYPE_AUDIO }?.getTrackFormat(0)
        log(
            "Can't play song-${id.take(8)} on this phone: ${format?.sampleMimeType}, " +
                "${format?.sampleRate} Hz, ${format?.channelCount} ch, " +
                "encoding ${format?.pcmEncoding}, ${format?.bitrate} bit/s"
        )
        _state.update { it.copy(cantPlay = it.cantPlay + id) }
        val wasPlaying = player.playWhenReady
        if (player.hasNextMediaItem()) {
            player.seekToNextMediaItem()
            if (wasPlaying) player.play()
        } else {
            player.pause()
        }
        control("SkipCantPlay")
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
                positionMs = player.currentPosition,
                durationMs = player.duration.takeIf { d -> d != C.TIME_UNSET } ?: 0,
                playWhenReady = player.playWhenReady,
                gettingSong = waitingFor != null && waitingFor == player.currentMediaItem?.mediaId
            )
        }
    }

    /**
     * The player the media session sees: its commands (lock screen, earbuds, Bluetooth buttons)
     * become this phone's controls, so they're mirrored like Home's.
     */
    private inner class Controls(player: Player) : ForwardingPlayer(player) {
        override fun play() = this@Playback.play()

        override fun pause() = this@Playback.pause()

        override fun setPlayWhenReady(playWhenReady: Boolean) =
            if (playWhenReady) play() else pause()

        override fun seekToNext() = next()

        override fun seekToNextMediaItem() = next()

        override fun seekToPrevious() = previous()

        override fun seekToPreviousMediaItem() = previous()

        override fun seekTo(positionMs: Long) = this@Playback.seekTo(positionMs)

        override fun seekTo(mediaItemIndex: Int, positionMs: Long) {
            if (mediaItemIndex == wrappedPlayer.currentMediaItemIndex) {
                this@Playback.seekTo(positionMs)
            } else {
                playAt(mediaItemIndex)
            }
        }
    }

    private companion object {
        const val TICK_MS = 500L

        /** Closer than this to the partner's position: no seek (it would be heard). */
        const val APPLY_TOLERANCE_MS = 150L
    }
}
