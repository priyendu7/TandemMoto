package com.tandemmoto

import android.app.Application
import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import com.tandemmoto.diagnostics.AppLog
import com.tandemmoto.diagnostics.LogLevel
import com.tandemmoto.diagnostics.RollingFileLog
import com.tandemmoto.link.AndroidDiscoveryPreconditions
import com.tandemmoto.link.AndroidWifiP2pDriver
import com.tandemmoto.link.DataStorePartnerStore
import com.tandemmoto.link.Link
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

private val Context.partnerDataStore by preferencesDataStore(name = "partner")

class TandemMotoApp : Application() {
    /** The app's single Wi-Fi Direct link, shared by Home, Pair, Settings (and later the service). */
    lateinit var link: Link
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        AppLog.init(
            RollingFileLog(File(filesDir, "logs")),
            minFileLevel = if (BuildConfig.DEBUG) LogLevel.DEBUG else LogLevel.INFO
        )
        AppLog.installCrashHandler()
        AppLog.i("App", "Started: ${AppLog.environmentSummary()}")
        link = Link(
            driver = AndroidWifiP2pDriver(this),
            preconditions = AndroidDiscoveryPreconditions(this),
            store = DataStorePartnerStore(partnerDataStore),
            scope = appScope,
            log = { AppLog.i("Link", it) }
        )
        link.start()
    }
}

/** The shared [Link], for view models: `(application as TandemMotoApp).link`. */
val Context.link: Link get() = (applicationContext as TandemMotoApp).link
