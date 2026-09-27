package com.tandemmoto.link

import java.net.ServerSocket
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertArrayEquals
import org.junit.Test

/** Real sockets on this machine: both kinds of traffic connect and carry frames (#62). */
class SocketFrameTransportTest {
    private fun freePort() = ServerSocket(0).use { it.localPort }

    private fun roundTrip(traffic: SocketTraffic) = runBlocking {
        withTimeout(10_000) {
            val port = freePort()
            val transport = SocketFrameTransport(traffic = traffic)
            val owner = async { transport.accept(port) }
            var client: FrameConnection? = null
            while (client == null) {
                client = runCatching { transport.connect("127.0.0.1", port) }.getOrNull()
                if (client == null) delay(20)
            }
            val server = owner.await()
            val big = ByteArray(60_000) { it.toByte() }
            client.send(big)
            assertArrayEquals(big, server.receive())
            server.send(byteArrayOf(1, 2, 3))
            assertArrayEquals(byteArrayOf(1, 2, 3), client.receive())
            client.close()
            server.close()
        }
    }

    @Test
    fun commandTrafficCarriesFrames() = roundTrip(SocketTraffic.Commands)

    @Test
    fun bulkTrafficWithSmallBuffersCarriesFrames() = roundTrip(SocketTraffic.Bulk)
}
