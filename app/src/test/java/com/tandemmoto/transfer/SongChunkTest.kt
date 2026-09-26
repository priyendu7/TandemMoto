package com.tandemmoto.transfer

import com.tandemmoto.state.Framing
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SongChunkTest {
    private val id = "a".repeat(64)

    @Test
    fun roundTripsAndFitsAFrame() {
        val data = ByteArray(SongChunk.DATA_BYTES) { it.toByte() }
        val bytes = SongChunk(id, 120_000, 5_000_000, data).encode()
        assertTrue(bytes.size <= Framing.MAX_FRAME_BYTES)
        val back = SongChunk.decode(bytes)!!
        assertEquals(id, back.id)
        assertEquals(120_000L, back.offset)
        assertEquals(5_000_000L, back.total)
        assertArrayEquals(data, back.data)
    }

    @Test
    fun theLastChunkEndsTheSong() {
        assertTrue(SongChunk(id, 90, 100, ByteArray(10)).last)
        assertTrue(!SongChunk(id, 80, 100, ByteArray(10)).last)
    }

    @Test
    fun anythingElseIsNotAChunk() {
        assertNull(SongChunk.decode(ByteArray(10)))
        assertNull(SongChunk.decode("{\"v\":1}".toByteArray()))
    }
}
