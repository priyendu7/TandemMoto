package com.tandemmoto.voice

import java.nio.ByteBuffer
import java.nio.ByteOrder

/** 20 ms of voice from the partner's phone (#71). [sentAtNanos] is on the sender's clock. */
class VoicePacket(val seq: Int, val sentAtNanos: Long, val samples: ShortArray)

/**
 * How voice samples go into a packet (#71). Uncompressed 16 kHz for now (Phase 4 planning:
 * about 1 % of the link, no encoding delay, no native code); Opus could replace it later with a
 * new [id], and nothing else changes.
 */
interface VoiceFormat {
    val id: Int

    fun encode(samples: ShortArray): ByteArray

    /** Null when [bytes] isn't a valid payload. */
    fun decode(bytes: ByteArray): ShortArray?
}

/** 16-bit little-endian PCM, 16 kHz mono, as the mic gives it. */
object Pcm16k : VoiceFormat {
    override val id = 0

    override fun encode(samples: ShortArray): ByteArray {
        val buffer = ByteBuffer.allocate(samples.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        buffer.asShortBuffer().put(samples)
        return buffer.array()
    }

    override fun decode(bytes: ByteArray): ShortArray? {
        if (bytes.size % 2 != 0) return null
        val samples = ShortArray(bytes.size / 2)
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(samples)
        return samples
    }
}

/**
 * The voice datagram: magic `TV`, version, format id, sequence number, send time, then the
 * format's payload. Anything else (another app on the port, a newer version) is ignored.
 */
object VoicePackets {
    const val VERSION = 1
    private const val MAGIC_T = 'T'.code.toByte()
    private const val MAGIC_V = 'V'.code.toByte()
    const val HEADER_BYTES = 2 + 1 + 1 + 4 + 8

    fun encode(packet: VoicePacket, format: VoiceFormat = Pcm16k): ByteArray {
        val payload = format.encode(packet.samples)
        return ByteBuffer.allocate(HEADER_BYTES + payload.size)
            .put(MAGIC_T)
            .put(MAGIC_V)
            .put(VERSION.toByte())
            .put(format.id.toByte())
            .putInt(packet.seq)
            .putLong(packet.sentAtNanos)
            .put(payload)
            .array()
    }

    fun decode(
        bytes: ByteArray,
        length: Int = bytes.size,
        format: VoiceFormat = Pcm16k
    ): VoicePacket? {
        if (length < HEADER_BYTES) return null
        val buffer = ByteBuffer.wrap(bytes, 0, length)
        if (buffer.get() != MAGIC_T || buffer.get() != MAGIC_V) return null
        if (buffer.get().toInt() != VERSION || buffer.get().toInt() != format.id) return null
        val seq = buffer.int
        val sentAt = buffer.long
        val payload = ByteArray(buffer.remaining()).also { buffer.get(it) }
        val samples = format.decode(payload) ?: return null
        return VoicePacket(seq, sentAt, samples)
    }
}
