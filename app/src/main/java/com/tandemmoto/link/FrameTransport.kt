package com.tandemmoto.link

import com.tandemmoto.state.Framing
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.StandardSocketOptions
import java.nio.ByteBuffer
import java.nio.channels.ServerSocketChannel
import java.nio.channels.SocketChannel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** One connection carrying whole frames. Tests use an in-memory pair instead of a socket. */
interface FrameConnection {
    suspend fun send(frame: ByteArray)

    /** The next frame; null when the other side closed cleanly. Throws IOException if broken. */
    suspend fun receive(): ByteArray?

    fun close()
}

/** Opens [FrameConnection]s between the two phones. */
interface FrameTransport {
    /** Group owner: waits for the partner's app to connect to [port]. */
    suspend fun accept(port: Int): FrameConnection

    /** Client: connects to the group owner. Fails fast (e.g. ENETUNREACH, refused). */
    suspend fun connect(host: String, port: Int): FrameConnection
}

/**
 * TCP over the Wi-Fi Direct group. Uses NIO channels because they're interruptible: cancelling
 * the coroutine interrupts the blocked thread, which closes the channel, so a blocked read or
 * accept never outlives the channel (issue #25: no leaked threads).
 */
class SocketFrameTransport(private val io: CoroutineDispatcher = Dispatchers.IO) : FrameTransport {
    override suspend fun accept(port: Int): FrameConnection = runInterruptible(io) {
        ServerSocketChannel.open().use { server ->
            // The partner's app may reconnect right after we close; don't wait out TIME_WAIT.
            server.setOption(StandardSocketOptions.SO_REUSEADDR, true)
            server.bind(InetSocketAddress(port))
            SocketFrameConnection(server.accept(), io)
        }
    }

    override suspend fun connect(host: String, port: Int): FrameConnection = runInterruptible(io) {
        val channel = SocketChannel.open()
        try {
            channel.connect(InetSocketAddress(host, port))
            SocketFrameConnection(channel, io)
        } catch (e: IOException) {
            channel.close()
            throw e
        }
    }
}

private class SocketFrameConnection(
    private val channel: SocketChannel,
    private val io: CoroutineDispatcher
) : FrameConnection {
    // Not Channels.newInputStream/newOutputStream: on a blocking channel they share one lock, so
    // a read waiting for data would hold up every send (the heartbeat included).
    private val input = BufferedInputStream(ChannelInput(channel))

    // Buffered so a frame's length and body leave in one packet on flush.
    private val output = BufferedOutputStream(ChannelOutput(channel))
    private val sending = Mutex()

    init {
        // Heartbeats and commands are tiny: send them now rather than batching (Nagle).
        channel.setOption(StandardSocketOptions.TCP_NODELAY, true)
    }

    override suspend fun send(frame: ByteArray) = sending.withLock {
        runInterruptible(io) { Framing.write(output, frame) }
    }

    override suspend fun receive(): ByteArray? = runInterruptible(io) { Framing.read(input) }

    override fun close() {
        runCatching { channel.close() }
    }
}

/** Reads straight from the channel, which has separate read and write locks. */
private class ChannelInput(private val channel: SocketChannel) : InputStream() {
    override fun read(): Int {
        val one = ByteArray(1)
        return if (read(one, 0, 1) <= 0) -1 else one[0].toInt() and 0xFF
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
        if (length == 0) 0 else channel.read(ByteBuffer.wrap(buffer, offset, length))
}

private class ChannelOutput(private val channel: SocketChannel) : OutputStream() {
    override fun write(byte: Int) = write(byteArrayOf(byte.toByte()), 0, 1)

    override fun write(buffer: ByteArray, offset: Int, length: Int) {
        val bytes = ByteBuffer.wrap(buffer, offset, length)
        while (bytes.hasRemaining()) channel.write(bytes)
    }
}
