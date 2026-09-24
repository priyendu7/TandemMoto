package com.tandemmoto

import android.app.Application
import com.tandemmoto.diagnostics.AppLog
import com.tandemmoto.diagnostics.LogLevel
import com.tandemmoto.diagnostics.RollingFileLog
import java.io.File

class TandemMotoApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppLog.init(
            RollingFileLog(File(filesDir, "logs")),
            minFileLevel = if (BuildConfig.DEBUG) LogLevel.DEBUG else LogLevel.INFO
        )
        AppLog.installCrashHandler()
        AppLog.i("App", "Started: ${AppLog.environmentSummary()}")
    }
}
