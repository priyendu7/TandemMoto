package com.tandemmoto.player

import androidx.annotation.OptIn
import androidx.media3.common.Format
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DecoderReuseEvaluation
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.audio.AudioSink

/**
 * Logs what the player does with each song's audio, so a phone test can tell why a song doesn't
 * play (#62: the Redmi Y2 stalled on two FLAC files its Files app plays): the decoder picked, the
 * format in and out, state changes, underruns and errors, and how long sound takes to start after
 * a start or a jump. Song IDs are hashed; no titles.
 */
@OptIn(UnstableApi::class)
class PlayerDiagnostics(
    private val currentSong: () -> String?,
    private val log: (String) -> Unit,
    private val now: () -> Long = System::currentTimeMillis
) : AnalyticsListener {
    private var startRequestedAt: Long? = null

    /** The player was asked to start or jump while playing: time until sound comes out. */
    fun startRequested() {
        startRequestedAt = now()
    }

    private fun song() = "song-${currentSong()?.take(8)}"

    override fun onAudioDecoderInitialized(
        eventTime: AnalyticsListener.EventTime,
        decoderName: String,
        initializedTimestampMs: Long,
        initializationDurationMs: Long
    ) = log("${song()}: audio decoder $decoderName ($initializationDurationMs ms to start)")

    override fun onAudioInputFormatChanged(
        eventTime: AnalyticsListener.EventTime,
        format: Format,
        decoderReuseEvaluation: DecoderReuseEvaluation?
    ) = log("${song()}: audio in ${describe(format)}")

    override fun onAudioTrackInitialized(
        eventTime: AnalyticsListener.EventTime,
        audioTrackConfig: AudioSink.AudioTrackConfig
    ) = log(
        "${song()}: audio out encoding ${audioTrackConfig.encoding}, " +
            "${audioTrackConfig.sampleRate} Hz, buffer ${audioTrackConfig.bufferSize} bytes"
    )

    override fun onAudioPositionAdvancing(
        eventTime: AnalyticsListener.EventTime,
        playoutStartSystemTimeMs: Long
    ) {
        val requested = startRequestedAt ?: return
        startRequestedAt = null
        log("${song()}: sound started ${playoutStartSystemTimeMs - requested} ms after the start")
    }

    override fun onPlaybackStateChanged(eventTime: AnalyticsListener.EventTime, state: Int) {
        val name = when (state) {
            Player.STATE_IDLE -> "idle"
            Player.STATE_BUFFERING -> "buffering"
            Player.STATE_READY -> "ready"
            Player.STATE_ENDED -> "ended"
            else -> "state $state"
        }
        log("${song()}: player $name")
    }

    override fun onAudioUnderrun(
        eventTime: AnalyticsListener.EventTime,
        bufferSize: Int,
        bufferSizeMs: Long,
        elapsedSinceLastFeedMs: Long
    ) = log("${song()}: audio underrun ($elapsedSinceLastFeedMs ms since the last feed)")

    override fun onAudioSinkError(
        eventTime: AnalyticsListener.EventTime,
        audioSinkError: Exception
    ) = log(
        "${song()}: audio output error ${audioSinkError.javaClass.simpleName}: ${audioSinkError.message}"
    )

    override fun onAudioCodecError(
        eventTime: AnalyticsListener.EventTime,
        audioCodecError: Exception
    ) = log(
        "${song()}: audio decoder error ${audioCodecError.javaClass.simpleName}: ${audioCodecError.message}"
    )

    companion object {
        fun describe(format: Format?): String = format?.let {
            "${it.sampleMimeType}, ${it.sampleRate} Hz, ${it.channelCount} ch, " +
                "encoding ${it.pcmEncoding}, ${it.bitrate} bit/s"
        } ?: "unknown format"
    }
}

/**
 * A song the player says is playing but whose position doesn't move (#62): the Redmi Y2 sat on
 * two FLAC files like that, silent and seconds behind the partner. Fed once a second.
 */
class StallCheck(private val stallMs: Long = STALL_MS) {
    private var lastPositionMs = -1L
    private var stuckSince: Long? = null

    /**
     * [playing]: it should be moving (playing, not held, not waiting for the file or for another
     * app's sound). True once it has been stuck for [stallMs].
     */
    fun update(nowMs: Long, positionMs: Long, playing: Boolean): Boolean {
        if (!playing) {
            reset()
            return false
        }
        val since = stuckSince
        val moved = positionMs - lastPositionMs >= MOVED_MS || positionMs < lastPositionMs
        if (since == null || moved) {
            lastPositionMs = positionMs
            stuckSince = nowMs
            return false
        }
        if (nowMs - since < stallMs) return false
        reset()
        return true
    }

    fun reset() {
        lastPositionMs = -1L
        stuckSince = null
    }

    companion object {
        const val STALL_MS = 3_000L
        private const val MOVED_MS = 200L
    }
}
