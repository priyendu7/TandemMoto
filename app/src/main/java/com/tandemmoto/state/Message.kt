package com.tandemmoto.state

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put

/**
 * Messages on the command channel. Phase 3–5 add playback, mic-mode and call-hold messages; an
 * older app skips types it doesn't know, so new ones can be added without a protocol bump.
 */
sealed interface Message {
    /**
     * First message on every connection, from both phones. [partnerInstallId]: who the sender
     * thinks its partner is (null when it doesn't know yet, e.g. right after pairing).
     */
    @Serializable
    data class Hello(
        val appVersion: String,
        val installId: String,
        val partnerInstallId: String?,
        val role: String
    ) : Message

    /** Heartbeat; answered with a [Pong] carrying the same [sentAtNanos] (the sender's clock). */
    @Serializable
    data class Ping(val sentAtNanos: Long) : Message

    @Serializable
    data class Pong(val sentAtNanos: Long) : Message

    /** The sender is closing the connection, and why. */
    @Serializable
    data class Bye(val reason: Reason) : Message {
        enum class Reason {
            /** The receiver isn't (or is no longer) the sender's partner. */
            NotYourPartner,

            /** Different [PROTOCOL_VERSION]s: one phone needs an update. */
            ProtocolMismatch,

            /** An ordinary close; the other side may reconnect. */
            Closing
        }
    }
}

/** Bumped only for incompatible changes to existing messages or the envelope. */
const val PROTOCOL_VERSION = 1

/** A decoded frame. */
sealed interface Envelope {
    data class Known(val version: Int, val seq: Long, val message: Message) : Envelope

    /** A message type this version doesn't know (from a newer app): skipped. */
    data class Unknown(val version: Int, val type: String) : Envelope
}

class MalformedMessageException(message: String, cause: Throwable? = null) :
    Exception(message, cause)

/**
 * JSON envelope `{ "v": 1, "type": "ping", "seq": 7, "payload": { … } }`.
 * Chosen over protobuf: messages are a few dozen bytes, JSON is readable when debugging, and
 * kotlinx.serialization needs no code generation step.
 */
object MessageCodec {
    private val json = Json {
        ignoreUnknownKeys = true // newer apps may add fields
        encodeDefaults = true
    }

    fun encode(message: Message, seq: Long, version: Int = PROTOCOL_VERSION): ByteArray {
        val payload = when (message) {
            is Message.Hello -> json.encodeToJsonElement(Message.Hello.serializer(), message)
            is Message.Ping -> json.encodeToJsonElement(Message.Ping.serializer(), message)
            is Message.Pong -> json.encodeToJsonElement(Message.Pong.serializer(), message)
            is Message.Bye -> json.encodeToJsonElement(Message.Bye.serializer(), message)
        }
        val envelope = buildJsonObject {
            put("v", version)
            put("type", typeOf(message))
            put("seq", seq)
            put("payload", payload)
        }
        return envelope.toString().encodeToByteArray()
    }

    fun decode(bytes: ByteArray): Envelope {
        try {
            val envelope = json.parseToJsonElement(bytes.decodeToString()).jsonObject
            val version = envelope.getValue("v").jsonPrimitive.int
            val seq = envelope["seq"]?.jsonPrimitive?.long ?: 0L
            val type = envelope.getValue("type").jsonPrimitive.contentOrNull
                ?: throw MalformedMessageException("Missing type")
            val payload = envelope["payload"] ?: JsonObject(emptyMap())
            val message = when (type) {
                HELLO -> payload.decodeAs(Message.Hello.serializer())
                PING -> payload.decodeAs(Message.Ping.serializer())
                PONG -> payload.decodeAs(Message.Pong.serializer())
                BYE -> payload.decodeAs(Message.Bye.serializer())
                else -> return Envelope.Unknown(version, type)
            }
            return Envelope.Known(version, seq, message)
        } catch (e: MalformedMessageException) {
            throw e
        } catch (e: SerializationException) {
            throw MalformedMessageException("Not a valid message", e)
        } catch (e: IllegalArgumentException) {
            // Not an object, or a value of the wrong type (e.g. "v": "one").
            throw MalformedMessageException("Not a valid message", e)
        } catch (e: NoSuchElementException) {
            // A required envelope key is missing.
            throw MalformedMessageException("Not a valid message", e)
        }
    }

    private fun <T> JsonElement.decodeAs(serializer: KSerializer<T>): T =
        json.decodeFromJsonElement(serializer, this)

    private fun typeOf(message: Message) = when (message) {
        is Message.Hello -> HELLO
        is Message.Ping -> PING
        is Message.Pong -> PONG
        is Message.Bye -> BYE
    }

    private const val HELLO = "hello"
    private const val PING = "ping"
    private const val PONG = "pong"
    private const val BYE = "bye"
}
