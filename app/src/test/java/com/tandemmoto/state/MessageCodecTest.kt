package com.tandemmoto.state

import com.tandemmoto.playlist.Stamp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageCodecTest {
    private val hello = Message.Hello("0.1.0", "install-a", null, "Initiator")

    @Test
    fun everyMessageTypeRoundTrips() {
        val messages = listOf(
            hello,
            hello.copy(partnerInstallId = "install-b"),
            Message.Ping(123_456_789L),
            Message.Pong(123_456_789L),
            Message.Pong(123_456_789L, 987_654_321L),
            Message.PlaybackState(
                songId = "abc",
                playing = true,
                positionMs = 61_000,
                atNanos = 5_000_000_000,
                stamp = Stamp(4, "install-a"),
                control = "Seek"
            ),
            Message.PlaybackState(null, false, 0, 0, Stamp(0, "install-b"), "Connect"),
            Message.Bye(Message.Bye.Reason.NotYourPartner),
            Message.Bye(Message.Bye.Reason.ProtocolMismatch),
            Message.Bye(Message.Bye.Reason.Closing)
        )
        messages.forEachIndexed { seq, message ->
            val decoded = MessageCodec.decode(MessageCodec.encode(message, seq.toLong()))
            assertEquals(Envelope.Known(PROTOCOL_VERSION, seq.toLong(), message), decoded)
        }
    }

    @Test
    fun aPongFromAnAppBefore60HasNoReplyTime() {
        val frame = """{"v":1,"type":"pong","seq":2,"payload":{"sentAtNanos":7}}"""
        assertEquals(
            Envelope.Known(1, 2, Message.Pong(7, 0)),
            MessageCodec.decode(frame.encodeToByteArray())
        )
    }

    @Test
    fun theEnvelopeIsVersionedJson() {
        val text = MessageCodec.encode(Message.Ping(5), seq = 7).decodeToString()
        assertEquals("""{"v":1,"type":"ping","seq":7,"payload":{"sentAtNanos":5}}""", text)
    }

    @Test
    fun anUnknownTypeFromANewerAppIsReportedNotRejected() {
        val frame = """{"v":1,"type":"skip","seq":3,"payload":{"to":4}}""".encodeToByteArray()
        assertEquals(Envelope.Unknown(1, "skip"), MessageCodec.decode(frame))
    }

    @Test
    fun unknownFieldsAreIgnored() {
        val frame =
            """{"v":1,"type":"ping","seq":1,"payload":{"sentAtNanos":9,"extra":1},"x":0}"""
        assertEquals(
            Envelope.Known(1, 1, Message.Ping(9)),
            MessageCodec.decode(frame.encodeToByteArray())
        )
    }

    @Test
    fun anotherProtocolVersionIsStillDecodedSoTheChannelCanSaySo() {
        val frame = MessageCodec.encode(Message.Ping(1), seq = 1, version = 2)
        assertEquals(2, (MessageCodec.decode(frame) as Envelope.Known).version)
    }

    @Test
    fun garbageIsMalformed() {
        val inputs = listOf(
            "not json",
            "[]",
            """{"type":"ping"}""",
            """{"v":1,"type":"ping","payload":{}}""",
            """{"v":1,"type":"bye","payload":{"reason":"Whatever"}}""",
            """{"v":"one","type":"ping","payload":{"sentAtNanos":1}}"""
        )
        inputs.forEach { input ->
            val failed = runCatching { MessageCodec.decode(input.encodeToByteArray()) }
            assertTrue(input, failed.exceptionOrNull() is MalformedMessageException)
        }
        val bytes = byteArrayOf(0xFF.toByte(), 0xFE.toByte(), 0x00)
        assertTrue(
            runCatching {
                MessageCodec.decode(bytes)
            }.exceptionOrNull() is MalformedMessageException
        )
    }
}
