package com.tandemmoto.spike

/**
 * Spike-only (#22) line protocol: each side sends `PING <seq> <sentAtNanos>` every second and
 * answers every ping with `PONG <seq> <sentAtNanos>`, echoing the sender's own timestamp so the
 * round trip is measured on one clock.
 */
sealed interface PingMessage {
    val seq: Int
    val sentAtNanos: Long

    data class Ping(override val seq: Int, override val sentAtNanos: Long) : PingMessage

    data class Pong(override val seq: Int, override val sentAtNanos: Long) : PingMessage
}

object PingProtocol {
    fun ping(seq: Int, sentAtNanos: Long) = "PING $seq $sentAtNanos"

    fun pong(seq: Int, sentAtNanos: Long) = "PONG $seq $sentAtNanos"

    fun parse(line: String): PingMessage? {
        val parts = line.trim().split(' ')
        if (parts.size != 3) return null
        val seq = parts[1].toIntOrNull() ?: return null
        val sentAt = parts[2].toLongOrNull() ?: return null
        return when (parts[0]) {
            "PING" -> PingMessage.Ping(seq, sentAt)
            "PONG" -> PingMessage.Pong(seq, sentAt)
            else -> null
        }
    }
}
