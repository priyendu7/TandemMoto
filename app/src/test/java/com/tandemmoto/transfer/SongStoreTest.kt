package com.tandemmoto.transfer

import com.tandemmoto.library.Fingerprint
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SongStoreTest {
    private val store = SongStore(Files.createTempDirectory("songs").toFile())
    private val bytes = ByteArray(200_000) { (it * 7).toByte() }
    private val id = Fingerprint.of(bytes.inputStream())

    @Test
    fun partsResumeAndAVerifiedSongIsKept() {
        store.append(id, 0, bytes.copyOfRange(0, 50_000))
        assertEquals(50_000L, store.received(id))
        store.append(id, 50_000, bytes.copyOfRange(50_000, bytes.size))
        assertTrue(store.finish(id))
        assertEquals(mapOf(id to bytes.size.toLong()), store.stored())
        assertEquals(0L, store.received(id))
    }

    @Test
    fun aSongWhoseFingerprintDoesntMatchIsThrownAway() {
        val broken = bytes.copyOf().also { it[1000] = (it[1000] + 1).toByte() }
        store.append(id, 0, broken)
        assertFalse(store.finish(id))
        assertFalse(store.has(id))
        assertEquals(0L, store.received(id))
    }

    @Test
    fun aChunkMustContinueWhereThePartEnds() {
        store.append(id, 0, bytes.copyOfRange(0, 1000))
        assertThrows(IllegalStateException::class.java) { store.append(id, 5000, ByteArray(10)) }
    }

    @Test
    fun deletingPartsKeepsTheWantedOnes() {
        store.append("keep", 0, ByteArray(10))
        store.append("drop", 0, ByteArray(10))
        store.deletePartsExcept(setOf("keep"))
        assertEquals(10L, store.received("keep"))
        assertEquals(0L, store.received("drop"))
        store.deleteAll()
        assertTrue(store.stored().isEmpty())
    }
}
