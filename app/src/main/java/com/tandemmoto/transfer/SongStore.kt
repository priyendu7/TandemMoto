package com.tandemmoto.transfer

import com.tandemmoto.library.Fingerprint
import java.io.File

/**
 * The partner's songs on this phone (#50), in app-private storage: `<id>` when complete and
 * verified, `<id>.part` while arriving. Only a window of them is kept; this phone's own songs are
 * never copied here. Blocking file I/O: call from an IO dispatcher.
 */
class SongStore(private val dir: File) {
    init {
        dir.mkdirs()
    }

    fun file(id: String) = File(dir, id)

    private fun part(id: String) = File(dir, "$id$PART")

    /** Complete songs and their size. */
    fun stored(): Map<String, Long> = dir.listFiles().orEmpty()
        .filter { it.isFile && !it.name.endsWith(PART) }
        .associate { it.name to it.length() }

    fun has(id: String) = file(id).isFile

    /** Bytes of [id] received so far (where to resume). */
    fun received(id: String): Long = part(id).takeIf { it.isFile }?.length() ?: 0L

    fun append(id: String, offset: Long, data: ByteArray) {
        val part = part(id)
        check(offset == received(id)) { "Chunk at $offset, have ${received(id)}" }
        part.appendBytes(data)
    }

    /**
     * The whole song arrived: keep it only if its fingerprint is its ID, so a broken or
     * tampered file is never played. False (and the part deleted) otherwise.
     */
    fun finish(id: String): Boolean {
        val part = part(id)
        val ok = part.isFile && part.inputStream().use { Fingerprint.of(it) } == id
        if (ok) {
            file(id).delete()
            part.renameTo(file(id))
        } else {
            part.delete()
        }
        return ok
    }

    fun delete(id: String) {
        file(id).delete()
        part(id).delete()
    }

    /** Partial files of songs no longer wanted. */
    fun deletePartsExcept(keep: Set<String>) {
        dir.listFiles().orEmpty()
            .filter { it.name.endsWith(PART) && it.name.removeSuffix(PART) !in keep }
            .forEach { it.delete() }
    }

    fun deleteAll() {
        dir.listFiles().orEmpty().forEach { it.delete() }
    }

    private companion object {
        const val PART = ".part"
    }
}
