package com.tandemmoto.voice

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VoicePacketTest {
    private val samples = ShortArray(320) { (it * 100 - 16_000).toShort() }

    @Test
    fun aPacketRoundTrips() {
        val bytes = VoicePackets.encode(VoicePacket(42, 123_456_789_000L, samples))
        assertEquals(VoicePackets.HEADER_BYTES + 640, bytes.size)
        val back = VoicePackets.decode(bytes)!!
        assertEquals(42, back.seq)
        assertEquals(123_456_789_000L, back.sentAtNanos)
        assertArrayEquals(samples, back.samples)
    }

    @Test
    fun onlyTheReceivedLengthIsRead() {
        val bytes = VoicePackets.encode(VoicePacket(1, 2, samples)) + ByteArray(100)
        val back = VoicePackets.decode(bytes, length = bytes.size - 100)!!
        assertArrayEquals(samples, back.samples)
    }

    @Test
    fun garbageAndOtherVersionsAreIgnored() {
        assertNull(VoicePackets.decode(ByteArray(0)))
        assertNull(VoicePackets.decode("hello there, not voice".encodeToByteArray()))
        val bytes = VoicePackets.encode(VoicePacket(1, 2, samples))
        assertNull(VoicePackets.decode(bytes.copyOf().also { it[2] = 9 })) // version
        assertNull(VoicePackets.decode(bytes.copyOf().also { it[3] = 7 })) // format
        assertNull(VoicePackets.decode(bytes, length = bytes.size - 1)) // half a sample
    }
}
