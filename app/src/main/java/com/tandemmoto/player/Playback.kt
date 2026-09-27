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
import androidx.media3.exoplayer.DefaultRenderersFactory
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
    /**
     * Holding at the start of the song until both phones have it (#61): which phone it's
     * missing on ("Getting song…" here, "Getting song on …" for the partner's). Null otherwise.
     */
    val waitingOn: MissingOn? = null,
    /** The current song's embedded picture (album art), when the file has one. */
    val artwork: ByteArray? = null,
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
    /** What the partner's phone has, for the start gate (#61). */
    private val partnerSongs: () -> PartnerSongs = { PartnerSongs() },
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

    private val diagnostics = PlayerDiagnostics({ currentId }, log, now)
    private val stall = StallCheck()

    /** Holding at the start of [Held.songId] until both phones have it (#61). */
    private data class Held(val songId: String, val missingOn: MissingOn)

    private var held: Held? = null
    private var holdTimeout: Job? = null

    /** A start both phones make at the same moment, scheduled (#61). */
    private var startJob: Job? = null

    /** The song the partner last said it's playing (so it has it). */
    private var partnerPlayingSong: String? = null

    init {
        val files = DataSource.Factory {
            SongDataSource(DefaultDataSource.Factory(context).createDataSource(), this)
        }
        // Decoder fallback: if the phone's first audio decoder can't start, try its next one.
        // Not float output: on the S25 it crackled and ran fast (#62 phone test).
        val renderers = DefaultRenderersFactory(context).setEnableDecoderFallback(true)
        player = ExoPlayer.Builder(context, renderers)
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
        player.addAnalyticsListener(diagnostics)
        player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                onCurrentChanged()
                // The song ended and the next began: both phones move on together, through the
                // start gate (#61).
                if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
                    startPlaying()
                    control("SongEnd")
                }
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
        scope.launch {
            while (true) {
                delay(STALL_CHECK_MS)
                checkStall()
            }
        }
    }

    fun togglePlay() {
        if (player.isPlaying || wantsToPlay()) pause() else play()
    }

    fun play() {
        if (!startPlaying()) return
        control("Play")
    }

    fun pause() {
        stopWaiting()
        player.pause()
        publish() // a held song was already paused underneath: no player event
        control("Pause")
    }

    fun next() {
        if (!player.hasNextMediaItem()) return
        moveThen { player.seekToNextMediaItem() }
        control("Next")
    }

    /** Over 3 s into a song it starts again; otherwise the previous song (Media3's rule). */
    fun previous() {
        moveThen { player.seekToPrevious() }
        control("Previous")
    }

    /** Plays from the song at [index] of the ride playlist (a tap in Playlist). */
    fun playAt(index: Int) {
        if (index !in 0 until player.mediaItemCount) return
        stopWaiting()
        player.seekTo(index, 0)
        startPlaying()
        control("PlaylistTap")
    }

    /**
     * What the start gate depends on changed (a download finished, the partner's song list
     * arrived, the link came or went): a held song may start now (#61).
     */
    fun recheck() {
        val waiting = held ?: return
        val id = player.currentMediaItem?.mediaId
        if (id != waiting.songId) {
            stopWaiting()
            publish()
            return
        }
        when (val decision = decideStart(id)) {
            StartDecision.Play -> {
                log("song-${id.take(8)} is on both phones: starting")
                startTogether(id)
            }
            is StartDecision.Hold -> if (decision.missingOn != waiting.missingOn) {
                hold(id, decision.missingOn)
            }
            StartDecision.Skip -> {
                skipForward()
                control("SkipCantPlay")
            }
        }
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
        stopWaiting()
        session.release()
        player.release()
    }

    // ---- LocalPlayer (the partner's state, from PlaybackMirror) ----

    override fun position() = PlayerPosition(
        songId = player.currentMediaItem?.mediaId,
        playing = wantsToPlay(),
        positionMs = player.currentPosition,
        waiting = held != null
    )

    override fun hasSong(songId: String) = indexOf(songId) >= 0

    override fun apply(
        songId: String,
        positionMs: Long,
        playing: Boolean,
        startInMs: Long,
        partnerPlaying: Boolean
    ) {
        val index = indexOf(songId)
        if (index < 0) return
        partnerPlayingSong = songId.takeIf { partnerPlaying }
        val sameSong = index == player.currentMediaItemIndex
        val inStep = sameSong &&
            player.isPlaying &&
            startInMs == 0L &&
            abs(player.currentPosition - positionMs) <= APPLY_TOLERANCE_MS
        stopWaiting()
        if (!inStep) player.seekTo(index, positionMs)
        when {
            !playing -> player.pause()
            inStep -> Unit
            else -> when (val decision = decideStart(songId)) {
                StartDecision.Play -> scheduleStart(songId, startInMs)
                is StartDecision.Hold -> hold(songId, decision.missingOn)
                StartDecision.Skip -> {
                    skipForward()
                    control("SkipCantPlay")
                }
            }
        }
        publish()
    }

    private fun indexOf(songId: String) = (0 until player.mediaItemCount).firstOrNull {
        player.getMediaItemAt(it).mediaId == songId
    } ?: -1

    private fun control(name: String) {
        listener?.onControl(name)
    }

    /**
     * Prepares if needed and plays the current song, if both phones have it (#61): otherwise it
     * holds at its start, and a song one of the phones can't play is skipped. False with nothing
     * to play.
     */
    private fun startPlaying(): Boolean {
        if (player.mediaItemCount == 0) return false
        if (player.playbackState == Player.STATE_IDLE ||
            player.playbackState == Player.STATE_ENDED
        ) {
            if (player.playbackState == Player.STATE_ENDED) player.seekTo(0, 0)
            player.prepare()
        }
        repeat(player.mediaItemCount) {
            val id = player.currentMediaItem?.mediaId ?: return false
            when (val decision = decideStart(id)) {
                StartDecision.Play -> {
                    stopWaiting()
                    diagnostics.startRequested()
                    player.play()
                    return true
                }
                is StartDecision.Hold -> {
                    hold(id, decision.missingOn)
                    return true
                }
                StartDecision.Skip -> {
                    log("Skipping song-${id.take(8)}: one of the phones can't play it")
                    if (!player.hasNextMediaItem()) {
                        stopWaiting()
                        player.pause()
                        return false
                    }
                    player.seekToNextMediaItem()
                }
            }
        }
        return false
    }

    private fun decideStart(id: String) = StartGate.decide(
        songId = id,
        linked = linked(),
        hereReady = find(id) is SongFileState.Ready,
        cantPlayHere = _state.value.cantPlay,
        partner = partnerSongs(),
        partnerPlaying = partnerPlayingSong == id
    )

    /** Playing, or wanting to: held until both phones have the song, or starting shortly. */
    private fun wantsToPlay() = player.playWhenReady || held != null || startJob?.isActive == true

    /** Waits at the start of [id] until both phones have it (#61). */
    private fun hold(id: String, missingOn: MissingOn) {
        startJob?.cancel()
        player.pause()
        val first = held?.songId != id
        held = Held(id, missingOn)
        log(
            "Holding song-${id.take(8)}: not on " +
                if (missingOn == MissingOn.ThisPhone) "this phone yet" else "the partner's yet"
        )
        if (first) {
            holdTimeout?.cancel()
            // The phone without the song gives up after its wait rules (#51) and the skip is
            // mirrored; this is the backstop if that never comes (e.g. the link dropped).
            holdTimeout = scope.launch {
                delay(HOLD_GIVE_UP_MS)
                if (held?.songId == id) {
                    log("song-${id.take(8)} never reached both phones: skipping")
                    skipForward()
                    control("SkipNotOnBoth")
                }
            }
        }
        publish()
    }

    /** Both phones have it: start at the same moment on both (a start planned ahead). */
    private fun startTogether(id: String) {
        stopWaiting()
        scheduleStart(id, if (linked()) listener?.onStartTogether() ?: 0 else 0)
    }

    private fun scheduleStart(id: String, inMs: Long) {
        startJob?.cancel()
        if (inMs <= 0) {
            diagnostics.startRequested()
            player.play()
            publish()
            return
        }
        player.pause()
        startJob = scope.launch {
            delay(inMs)
            if (player.currentMediaItem?.mediaId == id) {
                diagnostics.startRequested()
                player.play()
            }
            publish()
        }
    }

    private fun stopWaiting() {
        held = null
        holdTimeout?.cancel()
        holdTimeout = null
        startJob?.cancel()
        startJob = null
    }

    /** Moves (next/previous), then plays the new song if the old one was playing or held. */
    private inline fun moveThen(move: () -> Unit) {
        val wanted = wantsToPlay()
        stopWaiting()
        move()
        if (wanted) startPlaying()
    }

    /** On to the next song (playing it if this one was), or stop at the end. */
    private fun skipForward() {
        val wanted = wantsToPlay()
        stopWaiting()
        if (player.hasNextMediaItem()) {
            player.seekToNextMediaItem()
            player.prepare()
            if (wanted) startPlaying()
        } else {
            player.pause()
        }
        publish()
    }

    /**
     * A song that should be playing but doesn't move for 3 s (#62): the phone can't really play
     * it, even though Android didn't say so. Marked "Can't play on this phone" and skipped on
     * both phones, like a song Android reports it can't decode.
     */
    private fun checkStall() {
        val id = player.currentMediaItem?.mediaId
        val shouldMove = id != null &&
            player.playWhenReady &&
            held == null &&
            startJob?.isActive != true &&
            waitingFor != id &&
            player.playbackSuppressionReason == Player.PLAYBACK_SUPPRESSION_REASON_NONE &&
            (
                player.playbackState == Player.STATE_READY ||
                    player.playbackState == Player.STATE_BUFFERING
                )
        if (!stall.update(now(), player.currentPosition, shouldMove) || id == null) return
        val state = if (player.playbackState == Player.STATE_READY) "ready" else "buffering"
        val format = PlayerDiagnostics.describe(player.audioFormat)
        log(
            "song-${id.take(8)} doesn't move ($state at ${player.currentPosition / 1_000} s, " +
                "$format): can't play it on this phone"
        )
        _state.update { it.copy(cantPlay = it.cantPlay + id) }
        skipForward()
        control("SkipStalled")
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
        if (player.hasNextMediaItem()) {
            skipForward()
        } else {
            stopWaiting()
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
        skipForward()
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
                waitingOn = held?.takeIf { h -> h.songId == player.currentMediaItem?.mediaId }
                    ?.missingOn,
                artwork = player.mediaMetadata.artworkData,
                positionMs = player.currentPosition,
                durationMs = player.duration.takeIf { d -> d != C.TIME_UNSET } ?: 0,
                playWhenReady = wantsToPlay(),
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
        const val STALL_CHECK_MS = 1_000L

        /** Closer than this to the partner's position: no seek (it would be heard). */
        const val APPLY_TOLERANCE_MS = 150L

        /** A hold's backstop: past the linked wait (60 s) of the phone without the song. */
        const val HOLD_GIVE_UP_MS = 75_000L
    }
}
