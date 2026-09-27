package com.tandemmoto.voice

import java.net.DatagramSocket
import java.util.Collections
import kotlin.math.PI
import kotlin.math.sin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Two voice channels on this machine, over real UDP, with a fake mic and speaker (#71). */
class VoiceChannelLoopbackTest {
    /** A mic playing a 440 Hz tone at the real pace (20 ms per frame). */
    private class ToneMic : MicSource {
        var opened = 0
        var closed = 0
        private var position = 0

        override fun open(): MicCapture {
            opened++
            return object : MicCapture {
                override fun read(buffer: ShortArray): Int {
                    Thread.sleep(buffer.size * 1_000L / MicSource.SAMPLE_RATE)
                    for (i in buffer.indices) {
                        buffer[i] =
                            (8_000 * sin(2 * PI * 440 * position++ / 16_000)).toInt().toShort()
                    }
                    return buffer.size
                }

                override fun close() {
                    closed++
                }
            }
        }
    }

    /** A speaker that plays at the real pace and remembers what it heard. */
    private class RecordingSpeaker : SpeakerSource {
        val frames: MutableList<ShortArray> = Collections.synchronizedList(mutableListOf())

        @Volatile
        var open = false

        override fun open(): SpeakerOutput {
            open = true
            return object : SpeakerOutput {
                override fun write(samples: ShortArray): Int {
                    Thread.sleep(samples.size * 1_000L / MicSource.SAMPLE_RATE)
                    frames += samples.copyOf()
                    return samples.size
                }

                override val latencyMs = 40

                override fun close() {
                    open = false
                }
            }
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val logsA = Collections.synchronizedList(mutableListOf<String>())
    private val logsB = Collections.synchronizedList(mutableListOf<String>())

    @After
    fun tearDown() = scope.cancel()

    /** Two different free ports (asking twice in a row can give the same one back). */
    private fun twoFreePorts(): Pair<Int, Int> = DatagramSocket(0).use { first ->
        DatagramSocket(0).use { second -> first.localPort to second.localPort }
    }

    @Test
    fun voiceFromOnePhoneIsHeardOnTheOther() {
        val (portA, portB) = twoFreePorts()
        val micA = ToneMic()
        val speakerB = RecordingSpeaker()
        val offset = MutableStateFlow<Long?>(0L) // same clock
        val a = VoiceChannel(
            micA, RecordingSpeaker(), MutableStateFlow("127.0.0.1"), offset, scope,
            log = { logsA += it }, localPort = portA, partnerPort = portB, statsEveryMs = 500
        )
        val b = VoiceChannel(
            ToneMic(), speakerB, MutableStateFlow("127.0.0.1"), offset, scope,
            log = { logsB += it }, localPort = portB, partnerPort = portA, statsEveryMs = 500
        )
        a.start()
        b.start()
        Thread.sleep(200)
        a.setSending(true)
        Thread.sleep(1_500)

        assertTrue("B's speaker opened: A $logsA, B $logsB", speakerB.open)
        val heard = synchronized(speakerB.frames) { speakerB.frames.toList() }
        assertTrue(
            "B heard the tone: ${heard.size} frames",
            heard.count { f -> f.any { it > 4_000 } } > 20
        )
        assertTrue(
            logsB.toString(),
            logsB.any {
                it.startsWith("Voice: received") &&
                    it.contains("mouth-to-ear")
            }
        )
        assertTrue(logsA.any { it.startsWith("Voice: sent") })

        a.setSending(false)
        Thread.sleep(1_800)
        assertEquals("the mic is released", micA.opened, micA.closed)
        assertFalse("B's speaker closes after a second of silence", speakerB.open)
        a.stop()
        b.stop()
    }

    @Test
    fun withoutThePartnersAddressNothingIsSent() {
        val mic = ToneMic()
        val host = MutableStateFlow<String?>(null)
        val (local, remote) = twoFreePorts()
        val channel = VoiceChannel(
            mic,
            RecordingSpeaker(),
            host,
            MutableStateFlow(0L),
            scope,
            localPort = local,
            partnerPort = remote
        )
        channel.start()
        channel.setSending(true)
        Thread.sleep(300)
        assertEquals(0, mic.opened)
        channel.stop()
    }
}
