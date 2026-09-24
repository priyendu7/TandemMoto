package com.tandemmoto.diagnostics

import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

enum class LogLevel(val letter: Char, val priority: Int) {
    DEBUG('D', Log.DEBUG),
    INFO('I', Log.INFO),
    WARN('W', Log.WARN),
    ERROR('E', Log.ERROR)
}

/**
 * Append-only log in app-private storage, capped at two files of [maxFileBytes] each: when the
 * current file would grow past the cap it becomes [previous] (replacing the older one).
 *
 * Every line is written and flushed immediately, so nothing is lost if the app crashes right
 * after logging. Plain JVM code, so it's unit-tested without Android.
 */
class RollingFileLog(
    private val dir: File,
    private val maxFileBytes: Long = DEFAULT_MAX_FILE_BYTES,
    private val clock: () -> ZonedDateTime = ZonedDateTime::now
) {
    val current = File(dir, "tandemmoto.log")
    val previous = File(dir, "tandemmoto.1.log")

    @Synchronized
    fun append(level: LogLevel, tag: String, message: String) {
        val bytes = formatLine(clock(), level, tag, message.take(MAX_MESSAGE_CHARS))
            .toByteArray(Charsets.UTF_8)
        dir.mkdirs()
        if (current.length() > 0 && current.length() + bytes.size > maxFileBytes) rotate()
        FileOutputStream(current, true).use { it.write(bytes) }
    }

    /** Existing log files, oldest first. */
    @Synchronized
    fun files(): List<File> = listOf(previous, current).filter { it.exists() }

    private fun rotate() {
        previous.delete()
        current.renameTo(previous)
    }

    companion object {
        const val DEFAULT_MAX_FILE_BYTES = 1_000_000L

        /** Keeps one runaway message from filling the whole file. */
        const val MAX_MESSAGE_CHARS = 16_000

        private val TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX")

        /**
         * `2026-09-24T19:42:10.123+05:30 I/Link: message`, with any further lines of the message
         * (e.g. a stack trace) indented under it.
         */
        fun formatLine(
            time: ZonedDateTime,
            level: LogLevel,
            tag: String,
            message: String
        ): String {
            val lines = message.trimEnd().lines()
            return buildString {
                append(TIMESTAMP.format(time)).append(' ')
                append(level.letter).append('/').append(tag).append(": ").append(lines.first())
                append('\n')
                lines.drop(1).forEach { append("    ").append(it).append('\n') }
            }
        }
    }
}
