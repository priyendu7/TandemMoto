package com.tandemmoto.link

import com.tandemmoto.state.Envelope
import com.tandemmoto.state.Message
import com.tandemmoto.state.MessageCodec
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** One end of an in-memory connection; closing it is a clean end of stream for the other end. */
class PipeEnd(private val inbox: Channel<ByteArray>, private val outbox: Channel<ByteArray>) :
    FrameConnection {
    var closed = false
        private set

    override suspend fun send(frame: ByteArray) {
        if (closed || outbox.trySend(frame).isFailure) throw IOException("Connection closed")
    }

    override suspend fun receive(): ByteArray? =
        if (closed) throw IOException("Closed") else inbox.receiveCatching().getOrNull()

    override fun close() {
        closed = true
        outbox.close()
    }

    suspend fun sendMessage(message: Message, seq: Long = 0) =
        send(MessageCodec.encode(message, seq))

    /** The next known message, or null at end of stream. */
    suspend fun receiveMessage(): Message? {
        while (true) {
            val frame = receive() ?: return null
            val envelope = MessageCodec.decode(frame)
            if (envelope is Envelope.Known) return envelope.message
        }
    }
}

fun connectionPair(): Pair<PipeEnd, PipeEnd> {
    val aToB = Channel<ByteArray>(Channel.UNLIMITED)
    val bToA = Channel<ByteArray>(Channel.UNLIMITED)
    return PipeEnd(inbox = bToA, outbox = aToB) to PipeEnd(inbox = aToB, outbox = bToA)
}

/**
 * The partner phone's app, scripted: says Hello, answers Pings, and records what it got.
 * [refuseWith] makes it refuse our Hello; [answersPings] false makes it go silent.
 */
class FakePartnerApp(
    private val scope: CoroutineScope,
    val hello: Message.Hello = Message.Hello("0.1.0", "partner-install", null, "Acceptor"),
    var refuseWith: Message.Bye.Reason? = null,
    var answersPings: Boolean = true
) {
    val hellosReceived = mutableListOf<Message.Hello>()
    val byesReceived = mutableListOf<Message.Bye>()
    var pingsReceived = 0
        private set
    private val open = mutableListOf<PipeEnd>()

    fun connect(): FrameConnection {
        val (ours, theirs) = connectionPair()
        open += theirs
        scope.launch { serve(theirs) }
        return ours
    }

    /** The app is killed: every connection closes. */
    fun quit() {
        open.forEach { it.close() }
        open.clear()
    }

    private suspend fun serve(end: PipeEnd) {
        try {
            end.sendMessage(hello)
            while (true) {
                when (val message = end.receiveMessage() ?: break) {
                    is Message.Hello -> {
                        hellosReceived += message
                        refuseWith?.let {
                            end.sendMessage(Message.Bye(it))
                            end.close()
                            return
                        }
                    }
                    is Message.Ping -> {
                        pingsReceived++
                        if (answersPings) end.sendMessage(Message.Pong(message.sentAtNanos))
                    }
                    is Message.Pong -> Unit
                    is Message.Bye -> {
                        byesReceived += message
                        end.close()
                        return
                    }
                }
            }
        } catch (e: IOException) {
            // closed by quit()
        }
    }
}

/** [FrameTransport] reaching a [FakePartnerApp]; null [partnerApp] = the app isn't running. */
class FakeTransport(partnerApp: FakePartnerApp? = null) : FrameTransport {
    val partnerApp = MutableStateFlow(partnerApp)
    var connectCalls = 0
        private set

    override suspend fun accept(port: Int): FrameConnection =
        partnerApp.first { it != null }!!.connect()

    override suspend fun connect(host: String, port: Int): FrameConnection {
        connectCalls++
        return partnerApp.value?.connect() ?: throw IOException("Connection refused")
    }
}
