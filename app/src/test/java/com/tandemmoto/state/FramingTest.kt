package com.tandemmoto.state

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class FramingTest {
    private fun framed(vararg frames: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        frames.forEach { Framing.write(out, it) }
        return out.toByteArray()
    }

    /** Hands out at most [chunk] bytes per read, like a slow TCP stream. */
    private class Trickle(bytes: ByteArray, private val chunk: Int) : InputStream() {
        private val source = ByteArrayInputStream(bytes)

        override fun read() = source.read()

        override fun read(b: ByteArray, off: Int, len: Int) = source.read(b, off, minOf(len, chunk))
    }

    @Test
    fun framesSplitAcrossManyReadsAreReassembled() {
        val a = "hello".encodeToByteArray()
        val b = ByteArray(5_000) { it.toByte() }
        val input = Trickle(framed(a, b), chunk = 1)
        assertArrayEquals(a, Framing.read(input))
        assertArrayEquals(b, Framing.read(input))
        assertNull(Framing.read(input))
    }

    @Test
    fun severalFramesInOneReadAreSeparated() {
        val frames = List(10) { "frame $it".encodeToByteArray() }
        val input = ByteArrayInputStream(framed(*frames.toTypedArray()))
        frames.forEach { assertArrayEquals(it, Framing.read(input)) }
        assertNull(Framing.read(input))
    }

    @Test
    fun anEmptyFrameIsAllowed() {
        assertArrayEquals(ByteArray(0), Framing.read(ByteArrayInputStream(framed(ByteArray(0)))))
    }

    @Test
    fun anOversizedLengthIsRejectedWithoutAllocatingIt() {
        val header = byteArrayOf(0x7F, 0x00, 0x00, 0x00) // ~2 GB
        assertThrows(FramingException::class.java) {
            Framing.read(ByteArrayInputStream(header))
        }
        assertThrows(IllegalArgumentException::class.java) {
            Framing.write(ByteArrayOutputStream(), ByteArray(Framing.MAX_FRAME_BYTES + 1))
        }
    }

    @Test
    fun aNegativeLengthIsRejected() {
        val header = byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte())
        assertThrows(FramingException::class.java) {
            Framing.read(ByteArrayInputStream(header))
        }
    }

    @Test
    fun aStreamEndingInsideAFrameIsAnError() {
        val whole = framed("hello".encodeToByteArray())
        assertThrows(FramingException::class.java) {
            Framing.read(ByteArrayInputStream(whole.copyOf(2))) // inside the length
        }
        assertThrows(FramingException::class.java) {
            Framing.read(ByteArrayInputStream(whole.copyOf(6))) // inside the body
        }
    }
}
