package com.tandemmoto.diagnostics

import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppLogTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var fileLog: RollingFileLog
    private val originalHandler = Thread.getDefaultUncaughtExceptionHandler()

    @Before
    fun setUp() {
        fileLog = RollingFileLog(File(tmp.root, "logs"))
        AppLog.init(fileLog, minFileLevel = LogLevel.INFO)
    }

    @After
    fun tearDown() {
        Thread.setDefaultUncaughtExceptionHandler(originalHandler)
    }

    private fun fileText() = fileLog.current.takeIf { it.exists() }?.readText().orEmpty()

    @Test
    fun levelsBelowTheFileMinimumOnlyGoToLogcat() {
        AppLog.d("Test", "debug detail")
        AppLog.i("Test", "info line")
        assertFalse(fileText().contains("debug detail"))
        assertTrue(fileText().contains("I/Test: info line"))
    }

    @Test
    fun errorsIncludeTheStackTrace() {
        AppLog.e("Test", "failed", IllegalStateException("bad state"))
        assertTrue(fileText().contains("E/Test: failed"))
        assertTrue(fileText().contains("    java.lang.IllegalStateException: bad state"))
    }

    @Test
    fun crashIsLoggedThenPassedToThePreviousHandler() {
        var passedOn: Throwable? = null
        val crash = RuntimeException("boom")
        CrashLogger { _, e -> passedOn = e }.uncaughtException(Thread.currentThread(), crash)
        assertTrue(fileText().contains("E/Crash: Uncaught exception on thread"))
        assertTrue(fileText().contains("java.lang.RuntimeException: boom"))
        assertSame(crash, passedOn)
    }

    @Test
    fun installingTheCrashHandlerTwiceDoesNotNestIt() {
        AppLog.installCrashHandler()
        val installed = Thread.getDefaultUncaughtExceptionHandler()
        AppLog.installCrashHandler()
        assertTrue(installed is CrashLogger)
        assertSame(installed, Thread.getDefaultUncaughtExceptionHandler())
    }
}
