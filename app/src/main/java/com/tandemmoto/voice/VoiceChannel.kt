package com.tandemmoto.voice

import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import kotlin.concurrent.thread
import kotlin.math.roundToLong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Carries each person's voice to the other phone (#71): the mic in 20 ms frames → a
 * [VoicePacket] → UDP on [PORT], on Wi-Fi's voice queue → the partner's [JitterBuffer] → its
 * speaker. It runs while the command channel has the partner's address ([partnerHost]); it
 * **sends** only while [setSending] is on (mic mode, #72, or Settings → Talk test), and plays
 * whatever voice arrives, closing the speaker after [SPEAKER_IDLE_MS] of silence.
 *
 * Audio runs on its own threads (capture, receive, playback), not coroutines: the speaker's
 * blocking write sets the pace. Every [statsEveryMs] it logs what arrived and the estimated
 * mouth-to-ear delay.
 */
class VoiceChannel(
    private val mic: MicSource,
    private val speaker: SpeakerSource,
    private val partnerHost: StateFlow<String?>,
    /** The partner's clock minus this phone's (#60), for the network delay. */
    private val clockOffsetNanos: StateFlow<Long?>,
    private val scope: CoroutineScope,
    private val log: (String) -> Unit = {},
    private val localPort: Int = PORT,
    private val partnerPort: Int = PORT,
    private val nanoTime: () -> Long = System::nanoTime,
    /** Raises an audio thread's priority (Android's urgent-audio class on phones). */
    private val audioPriority: () -> Unit = {},
    private val statsEveryMs: Long = STATS_EVERY_MS
) {
    private val _sending = MutableStateFlow(false)
    val sending: StateFlow<Boolean> = _sending.asStateFlow()

    private val jitter = JitterBuffer()
    private val lock = Any()

    @Volatile
    private var socket: DatagramSocket? = null

    @Volatile
    private var partner: InetSocketAddress? = null
    private var capturing: Thread? = null
    private var playing: Thread? = null
    private var seq = 0

    // Stats since the last report (guarded by lock).
    private var sent = 0
    private val networkMs = mutableListOf<Long>()

    @Volatile
    private var micLatencyMs = 0

    @Volatile
    private var speakerLatencyMs = 0

    fun start() {
        scope.launch { partnerHost.collect { host -> if (host == null) close() else open(host) } }
        scope.launch {
            while (true) {
                delay(statsEveryMs)
                report()
            }
        }
    }

    /** Sends this phone's mic to the partner while on. */
    fun setSending(on: Boolean) {
        synchronized(lock) {
            if (_sending.value == on) return
            _sending.value = on
            log(if (on) "Sending voice" else "Stopped sending voice")
            if (on) startCapture()
        }
    }

    private fun open(host: String) = synchronized(lock) {
        close()
        val address = InetSocketAddress(InetAddress.getByName(host), partnerPort)
        // Not inside apply: there, localPort is the unbound socket's own (0), and every phone
        // bound a random port (the loopback test caught it).
        val bindTo = InetSocketAddress(localPort)
        val opened = try {
            DatagramSocket(null).apply {
                reuseAddress = true
                // Wi-Fi's voice queue, like the command channel (#62).
                runCatching { trafficClass = VOICE_TOS }
                bind(bindTo)
            }
        } catch (e: IOException) {
            log("Voice socket failed: ${e.javaClass.simpleName}")
            return
        }
        socket = opened
        partner = address
        log("Voice channel open with the partner")
        thread(name = "voice-receive", isDaemon = true) { receiveLoop(opened) }
        if (_sending.value) startCapture()
    }

    private fun close() = synchronized(lock) {
        val open = socket ?: return
        socket = null
        partner = null
        open.close() // ends the receive loop; capture and playback see socket == null
        log("Voice channel closed")
    }

    /** Stops everything; the channel reopens when the partner's address comes back. */
    fun stop() {
        setSending(false)
        close()
    }

    private fun startCapture() {
        if (socket == null || capturing?.isAlive == true) return
        capturing = thread(name = "voice-capture", isDaemon = true) { captureLoop() }
    }

    private fun captureLoop() {
        audioPriority()
        val capture = mic.open()
        if (capture == null) {
            log("Couldn't open the mic for voice")
            _sending.value = false
            return
        }
        micLatencyMs = capture.latencyMs
        val frame = ShortArray(JitterBuffer.FRAME_SAMPLES)
        try {
            while (_sending.value) {
                val out = socket ?: break
                val to = partner ?: break
                if (!readFully(capture, frame)) break
                val bytes = VoicePackets.encode(VoicePacket(seq++, nanoTime(), frame.copyOf()))
                try {
                    out.send(DatagramPacket(bytes, bytes.size, to))
                    synchronized(lock) { sent++ }
                } catch (e: IOException) {
                    if (socket == null) break // closed underneath us
                }
            }
        } finally {
            capture.close() // releases the mic: Android's mic indicator goes off
        }
    }

    private fun readFully(capture: MicCapture, frame: ShortArray): Boolean {
        var total = 0
        val chunk = ShortArray(frame.size)
        while (total < frame.size) {
            val read = capture.read(chunk)
            if (read <= 0) return false
            val take = minOf(read, frame.size - total)
            chunk.copyInto(frame, total, 0, take)
            total += take
        }
        return true
    }

    private fun receiveLoop(from: DatagramSocket) {
        audioPriority()
        val buffer = ByteArray(MAX_PACKET_BYTES)
        val datagram = DatagramPacket(buffer, buffer.size)
        while (true) {
            try {
                datagram.setLength(buffer.size)
                from.receive(datagram)
            } catch (e: IOException) {
                return // closed
            }
            val expected = partner ?: return
            if (datagram.address != expected.address) continue
            val packet = VoicePackets.decode(buffer, datagram.length) ?: continue
            val arrival = nanoTime()
            jitter.put(packet, arrival)
            clockOffsetNanos.value?.let { offset ->
                val oneWay = (arrival - (packet.sentAtNanos - offset)) / 1_000_000
                synchronized(lock) { networkMs += oneWay }
            }
            startPlayback()
        }
    }

    private fun startPlayback() = synchronized(lock) {
        if (playing?.isAlive == true) return
        playing = thread(name = "voice-play", isDaemon = true) { playLoop() }
    }

    private fun playLoop() {
        audioPriority()
        val output = speaker.open()
        if (output == null) {
            log("Couldn't open the speaker for voice")
            return
        }
        speakerLatencyMs = output.latencyMs
        var lastVoice = nanoTime()
        try {
            while (socket != null) {
                val frame = jitter.next()
                if (output.write(frame.samples) < 0) break
                if (frame.kind != FrameKind.Silence) {
                    lastVoice = nanoTime()
                } else if ((nanoTime() - lastVoice) / 1_000_000 > SPEAKER_IDLE_MS) {
                    break
                }
            }
        } finally {
            output.close()
        }
    }

    private fun report() {
        val stats = jitter.takeStats()
        val (sentNow, network) = synchronized(lock) {
            (sent to networkMs.sorted()).also {
                sent = 0
                networkMs.clear()
            }
        }
        if (sentNow == 0 && stats.received == 0) return
        val parts = mutableListOf<String>()
        if (sentNow > 0) parts += "sent $sentNow"
        if (stats.received > 0) {
            parts += "received ${stats.received}, filled in ${stats.filledIn}, " +
                "late ${stats.late}, trimmed ${stats.trimmed}, " +
                "buffer ${stats.depthFrames}/${stats.targetFrames} frames"
        }
        if (network.isNotEmpty()) {
            val median = network[network.size / 2]
            val p95 = network[(network.size * 95 / 100).coerceAtMost(network.size - 1)]
            val buffered = (stats.targetFrames * JitterBuffer.FRAME_MS).roundToLong()
            val frameMs = JitterBuffer.FRAME_MS.roundToLong()
            val mouthToEar = frameMs + micLatencyMs + p95 + buffered + speakerLatencyMs
            parts += "network median $median ms, p95 $p95 ms; mouth-to-ear about " +
                "$mouthToEar ms (frame $frameMs + mic ~$micLatencyMs + network $p95 + " +
                "buffer $buffered + speaker $speakerLatencyMs)"
        }
        log("Voice: " + parts.joinToString("; "))
    }

    companion object {
        const val PORT = 48154

        /** DSCP EF. */
        const val VOICE_TOS = 0xB8
        const val SPEAKER_IDLE_MS = 1_000L
        const val STATS_EVERY_MS = 10_000L
        private const val MAX_PACKET_BYTES = 2_048
    }
}
