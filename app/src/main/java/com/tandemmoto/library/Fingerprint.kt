package com.tandemmoto.library

import java.io.InputStream
import java.security.MessageDigest

/**
 * SHA-256 of a whole file, read in 64 KB chunks so a big song is never held in memory. The same
 * file gives the same fingerprint on any phone, so it identifies a song across the two phones.
 */
object Fingerprint {
    private const val CHUNK_BYTES = 64 * 1024

    fun of(input: InputStream): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(CHUNK_BYTES)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            digest.update(buffer, 0, read)
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
