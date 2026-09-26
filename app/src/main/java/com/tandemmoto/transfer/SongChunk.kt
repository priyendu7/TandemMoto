package com.tandemmoto.transfer

import com.tandemmoto.state.Framing
import java.nio.ByteBuffer

/**
 * One piece of a song on the transfer connection (#50). Binary, not JSON: a byte of type, the
 * song ID (64 hex characters), the offset and the song's total size (8 bytes each), then the
 * data. Fits one frame ([Framing.MAX_FRAME_BYTES]).
 */
class SongChunk(val id: String, val offset: Long, val total: Long, val data: ByteArray) {
    val last: Boolean get() = offset + data.size >= total

    fun encode(): ByteArray {
        val idBytes = id.toByteArray(Charsets.US_ASCII)
        require(idBytes.size == ID_BYTES) { "Song IDs are 64 hex characters" }
        return ByteBuffer.allocate(HEADER_BYTES + data.size)
            .put(TYPE)
            .put(idBytes)
            .putLong(offset)
            .putLong(total)
            .put(data)
            .array()
    }

    companion object {
        /** 60 KB of song per chunk: under the 64 KB frame limit with the header. */
        const val DATA_BYTES = 60 * 1024

        private const val TYPE: Byte = 1
        private const val ID_BYTES = 64
        private const val HEADER_BYTES = 1 + ID_BYTES + 8 + 8

        /** Null for anything that isn't a well-formed chunk. */
        fun decode(frame: ByteArray): SongChunk? {
            if (frame.size < HEADER_BYTES || frame[0] != TYPE) return null
            val buffer = ByteBuffer.wrap(frame)
            buffer.get()
            val idBytes = ByteArray(ID_BYTES).also { buffer.get(it) }
            val offset = buffer.long
            val total = buffer.long
            if (offset < 0 || total < 0) return null
            val data = ByteArray(buffer.remaining()).also { buffer.get(it) }
            return SongChunk(String(idBytes, Charsets.US_ASCII), offset, total, data)
        }

        init {
            check(HEADER_BYTES + DATA_BYTES <= Framing.MAX_FRAME_BYTES)
        }
    }
}
