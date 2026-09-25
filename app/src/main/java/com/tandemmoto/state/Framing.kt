package com.tandemmoto.state

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/** A frame that can't be read: bad length or the stream ended part-way through. */
class FramingException(message: String) : IOException(message)

/**
 * Length-prefixed frames on a byte stream: a 4-byte big-endian length, then that many bytes.
 * TCP delivers a stream, not messages, so a frame can arrive split over several reads, or several
 * frames in one read; the length prefix puts them back together.
 */
object Framing {
    /** Command messages are tiny; anything this big is a bug or not our protocol. */
    const val MAX_FRAME_BYTES = 64 * 1024

    fun write(output: OutputStream, frame: ByteArray) {
        require(frame.size <= MAX_FRAME_BYTES) { "Frame of ${frame.size} bytes is too big" }
        val data = DataOutputStream(output)
        data.writeInt(frame.size)
        data.write(frame)
        data.flush()
    }

    /** The next frame, or null when the stream ended cleanly between frames. */
    fun read(input: InputStream): ByteArray? {
        val data = DataInputStream(input)
        val first = data.read()
        if (first == -1) return null
        val length = try {
            (first shl 24) or (data.readUnsignedByte() shl 16) or
                (data.readUnsignedByte() shl 8) or data.readUnsignedByte()
        } catch (e: EOFException) {
            throw FramingException("Stream ended inside a frame length")
        }
        if (length < 0 || length > MAX_FRAME_BYTES) {
            throw FramingException("Frame length $length out of range")
        }
        val frame = ByteArray(length)
        try {
            data.readFully(frame)
        } catch (e: EOFException) {
            throw FramingException("Stream ended inside a frame")
        }
        return frame
    }
}
