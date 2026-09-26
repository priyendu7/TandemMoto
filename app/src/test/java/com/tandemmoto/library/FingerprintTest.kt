package com.tandemmoto.library

import java.io.ByteArrayInputStream
import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Test

class FingerprintTest {
    @Test
    fun matchesKnownSha256Values() {
        assertEquals(
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            Fingerprint.of(ByteArrayInputStream(ByteArray(0)))
        )
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            Fingerprint.of(ByteArrayInputStream("abc".toByteArray()))
        )
    }

    @Test
    fun aBigFileReadInChunksGivesTheSameFingerprint() {
        val bytes = ByteArray(5 * 1024 * 1024 + 123) { (it * 31).toByte() } // not a chunk multiple
        val whole = MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it) }
        assertEquals(whole, Fingerprint.of(ByteArrayInputStream(bytes)))
    }
}
