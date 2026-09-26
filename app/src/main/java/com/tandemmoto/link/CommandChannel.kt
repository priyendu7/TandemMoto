package com.tandemmoto.link

import com.tandemmoto.state.Envelope
import com.tandemmoto.state.MalformedMessageException
import com.tandemmoto.state.Message
import com.tandemmoto.state.Message.Bye
import com.tandemmoto.state.MessageCodec
import com.tandemmoto.state.PROTOCOL_VERSION
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface ChannelState {
    /** No group, so nothing to connect. */
    data object Idle : ChannelState

    /** In a group, connecting or waiting for the partner's app (it may not be running). */
    data object Opening : ChannelState

    /** Hello exchanged and accepted: the partner's app is running and answering. */
    data class Open(val partner: Message.Hello) : ChannelState

    /** One side refused the other; not retried. */
    data class Refused(val reason: Bye.Reason) : ChannelState
}

/** How the last open connection ended; tells a closed app apart from a vanished phone. */
enum class ChannelLoss {
    /** No connection has ended since [CommandChannel.open]. */
    None,

    /** The partner closed it cleanly: its app was closed (the phone is still there). */
    ClosedByPartner,

    /** Nothing heard, or the socket broke: the phone went away (Wi-Fi off, out of range). */
    Vanished
}

/** Where the other phone is: the group owner listens, the client connects to its address. */
data class Endpoint(val isGroupOwner: Boolean, val ownerHost: String?)

/**
 * The command/state socket between the two phones (#25), carried over the Wi-Fi Direct group.
 *
 * While [open], it keeps a connection up: the group owner listens on [PORT], the client connects
 * and retries (the first attempt after a group forms always fails with ENETUNREACH, per the
 * spike). Each connection starts with a Hello from both sides, then both send a Ping every
 * [heartbeatMs], which also keeps the Wi-Fi radio from dozing (sparse traffic gave a p95 of up to
 * 1.4 s in the spike). A connection is lost when the socket closes or breaks, or after
 * [silenceTimeoutMs] with nothing received; the channel then reconnects for as long as it's open.
 */
class CommandChannel(
    private val transport: FrameTransport,
    private val scope: CoroutineScope,
    private val nanoTime: () -> Long = System::nanoTime,
    private val log: (String) -> Unit = {},
    /** Called with true while a connection is open: hold the low-latency Wi-Fi lock. */
    private val keepAwake: (Boolean) -> Unit = {},
    private val heartbeatMs: Long = HEARTBEAT_MS,
    private val silenceTimeoutMs: Long = SILENCE_TIMEOUT_MS
) {
    private val _state = MutableStateFlow<ChannelState>(ChannelState.Idle)
    val state: StateFlow<ChannelState> = _state.asStateFlow()

    private val _incoming = MutableSharedFlow<Message>(extraBufferCapacity = 64)

    /** Messages other than the handshake and heartbeat (playback, mic mode… from Phase 3). */
    val incoming: SharedFlow<Message> = _incoming.asSharedFlow()

    /** Set before [state] goes back to [ChannelState.Opening] after a connection ends. */
    @Volatile
    var lastLoss = ChannelLoss.None
        private set

    private var job: Job? = null
    private var connection: FrameConnection? = null
    private var seq = 0L

    /**
     * Starts connecting to [endpoint]. [hello] is sent first on every connection; [check] vets
     * the partner's Hello and returns a reason to refuse it, or null to accept.
     */
    fun open(
        endpoint: Endpoint,
        hello: () -> Message.Hello,
        check: suspend (Message.Hello) -> Bye.Reason?
    ) {
        close()
        lastLoss = ChannelLoss.None
        _state.value = ChannelState.Opening
        log("Channel opening as ${if (endpoint.isGroupOwner) "group owner" else "client"}")
        job = scope.launch { run(endpoint, hello, check) }
    }

    /** Sends [message] if a connection is open; false otherwise. */
    suspend fun send(message: Message): Boolean {
        val current = connection ?: return false
        if (_state.value !is ChannelState.Open) return false
        return try {
            current.send(MessageCodec.encode(message, ++seq))
            true
        } catch (e: IOException) {
            false
        }
    }

    /** Closes the connection and stops reconnecting. */
    fun close() {
        job?.cancel()
        job = null
        connection?.close()
        connection = null
        if (_state.value != ChannelState.Idle) {
            _state.value = ChannelState.Idle
            keepAwake(false)
            log("Channel closed")
        }
    }

    private suspend fun run(
        endpoint: Endpoint,
        hello: () -> Message.Hello,
        check: suspend (Message.Hello) -> Bye.Reason?
    ) {
        var failures = 0
        while (true) {
            val opened = try {
                connect(endpoint)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // IOException (ENETUNREACH, refused…), or SecurityException without INTERNET.
                failures++
                if (failures == 1 || failures % LOG_EVERY_FAILURES == 0) {
                    log("Channel connect failed ($failures): ${e.javaClass.simpleName}")
                }
                delay(if (failures < FAST_RETRIES) FAST_RETRY_MS else SLOW_RETRY_MS)
                continue
            }
            failures = 0
            connection = opened
            val end = try {
                session(opened, hello, check)
            } finally {
                opened.close()
                connection = null
                keepAwake(false)
            }
            when (end) {
                is End.Refused -> {
                    _state.value = ChannelState.Refused(end.reason)
                    log("Channel refused: ${end.reason}")
                    return
                }
                is End.Lost -> {
                    lastLoss = end.loss
                    _state.value = ChannelState.Opening
                    log("Channel lost: ${end.why}")
                    delay(FAST_RETRY_MS)
                }
            }
        }
    }

    private suspend fun connect(endpoint: Endpoint): FrameConnection = if (endpoint.isGroupOwner) {
        transport.accept(PORT)
    } else {
        val host = endpoint.ownerHost ?: throw IOException("Group owner address unknown")
        transport.connect(host, PORT)
    }

    private sealed interface End {
        data class Lost(val why: String, val loss: ChannelLoss = ChannelLoss.Vanished) : End

        data class Refused(val reason: Bye.Reason) : End
    }

    private suspend fun session(
        connection: FrameConnection,
        hello: () -> Message.Hello,
        check: suspend (Message.Hello) -> Bye.Reason?
    ): End = coroutineScope {
        val ended = CompletableDeferred<End>()
        var lastHeard = nanoTime()
        val stats = RttStats()

        suspend fun write(message: Message) = connection.send(MessageCodec.encode(message, ++seq))

        suspend fun refuse(reason: Bye.Reason): End {
            runCatching { write(Bye(reason)) }
            return End.Refused(reason)
        }

        suspend fun read(): End {
            while (true) {
                val frame = connection.receive()
                    ?: return End.Lost("closed by the partner", ChannelLoss.ClosedByPartner)
                lastHeard = nanoTime()
                val envelope = MessageCodec.decode(frame)
                val version = when (envelope) {
                    is Envelope.Known -> envelope.version
                    is Envelope.Unknown -> envelope.version
                }
                if (version != PROTOCOL_VERSION) {
                    log("Partner speaks protocol $version, this app $PROTOCOL_VERSION")
                    return refuse(Bye.Reason.ProtocolMismatch)
                }
                if (envelope !is Envelope.Known) continue // a newer app's message type: skip
                when (val message = envelope.message) {
                    is Message.Hello -> {
                        check(message)?.let { return refuse(it) }
                        _state.value = ChannelState.Open(message)
                        keepAwake(true)
                        log("Channel open (partner app ${message.appVersion})")
                    }
                    is Message.Ping -> write(Message.Pong(message.sentAtNanos))
                    is Message.Pong -> stats.add(nanoTime() - message.sentAtNanos)
                    is Bye -> return if (message.reason == Bye.Reason.Closing) {
                        End.Lost("the partner closed it", ChannelLoss.ClosedByPartner)
                    } else {
                        End.Refused(message.reason)
                    }
                    // Everything else is for the app (playlist, songs…), once the partner is known.
                    is Message.PlaylistEntries,
                    is Message.SongRequest,
                    is Message.SongUnavailable,
                    is Message.SongsOnPhone ->
                        if (_state.value is ChannelState.Open) _incoming.emit(message)
                    // New message types get a branch here that emits to _incoming once Open.
                }
            }
        }

        launch {
            val end = try {
                read()
            } catch (e: IOException) {
                End.Lost("read failed: ${e.javaClass.simpleName}")
            } catch (e: MalformedMessageException) {
                End.Lost("unreadable message")
            }
            ended.complete(end)
        }

        launch {
            try {
                write(hello())
                var sinceReport = 0L
                while (true) {
                    delay(heartbeatMs)
                    val silentMs = (nanoTime() - lastHeard) / NANOS_PER_MS
                    if (silentMs >= silenceTimeoutMs) {
                        ended.complete(End.Lost("nothing heard for $silentMs ms"))
                        break
                    }
                    write(Message.Ping(nanoTime()))
                    sinceReport += heartbeatMs
                    if (sinceReport >= STATS_EVERY_MS) {
                        sinceReport = 0
                        stats.summary()?.let(log)
                        stats.reset()
                    }
                }
            } catch (e: IOException) {
                ended.complete(End.Lost("send failed: ${e.javaClass.simpleName}"))
            }
        }

        val end = ended.await()
        coroutineContext.cancelChildren()
        end
    }

    companion object {
        /** Fixed port on the group owner. */
        const val PORT = 48152

        /**
         * 10 pings/s each way. At 5/s the phones' p95 was 55–99 ms with 2 of 15 windows over
         * 100 ms (148, 165), so it's 10/s now (#25 phone test).
         */
        const val HEARTBEAT_MS = 100L

        /**
         * Not the ~3 s first planned: MIUI freezes the app for 3–4 s with the screen off (spike).
         * Real disappearances (app closed, Wi-Fi off) close the socket or the group within ~1 s.
         */
        const val SILENCE_TIMEOUT_MS = 6_000L

        /** Client retries: every 500 ms for the first ~10 s, then every 2 s. */
        const val FAST_RETRY_MS = 500L
        const val FAST_RETRIES = 20
        const val SLOW_RETRY_MS = 2_000L

        const val STATS_EVERY_MS = 10_000L
        private const val LOG_EVERY_FAILURES = 10
        private const val NANOS_PER_MS = 1_000_000L
    }
}
