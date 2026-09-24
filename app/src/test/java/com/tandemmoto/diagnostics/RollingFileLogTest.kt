package com.tandemmoto.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.ZoneOffset
import java.time.ZonedDateTime

class RollingFileLogTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val time =
        ZonedDateTime.of(2026, 9, 24, 19, 42, 10, 123_000_000, ZoneOffset.ofHoursMinutes(5, 30))

    private fun log(maxFileBytes: Long = RollingFileLog.DEFAULT_MAX_FILE_BYTES) =
        RollingFileLog(File(tmp.root, "logs"), maxFileBytes) { time }

    @Test
    fun lineFormat() {
        assertEquals(
            "2026-09-24T19:42:10.123+05:30 I/Link: Peer found\n",
            RollingFileLog.formatLine(time, LogLevel.INFO, "Link", "Peer found")
        )
    }

    @Test
    fun multiLineMessagesAreIndentedUnderTheFirstLine() {
        assertEquals(
            "2026-09-24T19:42:10.123+05:30 E/Crash: Boom\n" +
                "    at a.b(C.kt:1)\n" +
                "    at d.e(F.kt:2)\n",
            RollingFileLog.formatLine(
                time,
                LogLevel.ERROR,
                "Crash",
                "Boom\nat a.b(C.kt:1)\nat d.e(F.kt:2)\n"
            )
        )
    }

    @Test
    fun appendsToTheCurrentFile() {
        val log = log()
        log.append(LogLevel.INFO, "App", "one")
        log.append(LogLevel.WARN, "Link", "two")
        assertEquals(
            listOf(
                "2026-09-24T19:42:10.123+05:30 I/App: one",
                "2026-09-24T19:42:10.123+05:30 W/Link: two"
            ),
            log.current.readLines()
        )
        assertFalse(log.previous.exists())
    }

    @Test
    fun neverExceedsTheCap() {
        val log = log(maxFileBytes = 1_000)
        repeat(5_000) { log.append(LogLevel.INFO, "Test", "line $it") }
        assertTrue(log.current.length() <= 1_000)
        assertTrue(log.previous.length() <= 1_000)
        assertEquals(listOf(log.previous, log.current), log.files())
        assertEquals(2, log.current.parentFile!!.listFiles()!!.size)
    }

    @Test
    fun rotationKeepsTheNewestLines() {
        val log = log(maxFileBytes = 1_000)
        repeat(5_000) { log.append(LogLevel.INFO, "Test", "line $it") }
        assertTrue(log.current.readLines().last().endsWith("line 4999"))
        // The previous file ends exactly where the current one starts.
        val lastOfPrevious = log.previous.readLines().last().substringAfterLast(' ').toInt()
        val firstOfCurrent = log.current.readLines().first().substringAfterLast(' ').toInt()
        assertEquals(lastOfPrevious + 1, firstOfCurrent)
    }

    @Test
    fun oversizedMessagesAreTruncated() {
        val log = log()
        log.append(LogLevel.INFO, "Test", "x".repeat(RollingFileLog.MAX_MESSAGE_CHARS * 10))
        assertTrue(log.current.length() < RollingFileLog.MAX_MESSAGE_CHARS + 100)
    }

    @Test
    fun exportHasHeaderThenOlderThenNewerLogs() {
        val log = log(maxFileBytes = 100)
        log.append(LogLevel.INFO, "Test", "older")
        log.append(LogLevel.INFO, "Test", "newer")
        val out = LogExporter.buildExport("HEADER", log.files(), File(tmp.root, "out/export.txt"))
        val lines = out.readLines()
        assertEquals("HEADER", lines[0])
        assertEquals("", lines[1])
        assertTrue(lines[2].endsWith("older"))
        assertTrue(lines[3].endsWith("newer"))
    }
}
