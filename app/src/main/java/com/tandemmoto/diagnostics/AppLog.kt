package com.tandemmoto.diagnostics

import android.os.Build
import android.util.Log
import com.tandemmoto.BuildConfig
import java.io.IOException

/**
 * App-wide logging: always to Logcat, and to the local [RollingFileLog] that the user can export
 * from Settings. Logs never leave the phone on their own.
 *
 * Never log personal data: no song titles or file names, no phone numbers or caller details, no
 * full device addresses (see diagnostics/README.md).
 */
object AppLog {
    @Volatile
    var fileLog: RollingFileLog? = null
        private set

    @Volatile
    private var minFileLevel = LogLevel.INFO

    fun init(fileLog: RollingFileLog, minFileLevel: LogLevel) {
        this.fileLog = fileLog
        this.minFileLevel = minFileLevel
    }

    fun d(tag: String, message: String, error: Throwable? = null) =
        log(LogLevel.DEBUG, tag, message, error)

    fun i(tag: String, message: String, error: Throwable? = null) =
        log(LogLevel.INFO, tag, message, error)

    fun w(tag: String, message: String, error: Throwable? = null) =
        log(LogLevel.WARN, tag, message, error)

    fun e(tag: String, message: String, error: Throwable? = null) =
        log(LogLevel.ERROR, tag, message, error)

    private fun log(level: LogLevel, tag: String, message: String, error: Throwable?) {
        val text = if (error == null) message else "$message\n${error.stackTraceToString()}"
        Log.println(level.priority, tag, text)
        if (level < minFileLevel) return
        try {
            fileLog?.append(level, tag, text)
        } catch (e: IOException) {
            // Logging must never take the app down.
            Log.w(TAG, "Couldn't write the log file", e)
        }
    }

    /**
     * Records uncaught exceptions in the log file, then hands them to the previous handler so the
     * app still crashes normally. Installing twice is a no-op.
     */
    fun installCrashHandler() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        if (previous is CrashLogger) return
        Thread.setDefaultUncaughtExceptionHandler(CrashLogger(previous))
    }

    /** App version, build and device: the first thing to know when reading a log. */
    fun environmentSummary(): String =
        "TandemMoto ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}, " +
            "${BuildConfig.BUILD_TYPE}) on Android ${Build.VERSION.RELEASE} " +
            "(API ${Build.VERSION.SDK_INT}), ${Build.MANUFACTURER} ${Build.MODEL}"

    private const val TAG = "AppLog"
}

internal class CrashLogger(
    private val previous: Thread.UncaughtExceptionHandler?
) : Thread.UncaughtExceptionHandler {
    override fun uncaughtException(thread: Thread, error: Throwable) {
        try {
            AppLog.e("Crash", "Uncaught exception on thread ${thread.name}", error)
        } catch (ignored: Throwable) {
            // Never let logging hide the original crash.
        }
        previous?.uncaughtException(thread, error)
    }
}
