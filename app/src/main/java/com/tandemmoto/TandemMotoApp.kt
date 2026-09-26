package com.tandemmoto

import android.app.Application
import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import com.tandemmoto.diagnostics.AppLog
import com.tandemmoto.diagnostics.LogLevel
import com.tandemmoto.diagnostics.RollingFileLog
import com.tandemmoto.library.AndroidSongSource
import com.tandemmoto.library.JsonFileLibraryStore
import com.tandemmoto.library.Library
import com.tandemmoto.link.AndroidDiscoveryPreconditions
import com.tandemmoto.link.AndroidWifiP2pDriver
import com.tandemmoto.link.DataStorePartnerStore
import com.tandemmoto.link.InstallId
import com.tandemmoto.link.Link
import com.tandemmoto.link.LowLatencyWifiLock
import com.tandemmoto.link.SocketFrameTransport
import com.tandemmoto.service.LinkService
import com.tandemmoto.service.LinkSession
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

private val Context.partnerDataStore by preferencesDataStore(name = "partner")
private val Context.identityDataStore by preferencesDataStore(name = "identity")

class TandemMotoApp : Application() {
    /** The app's single Wi-Fi Direct link, shared by Home, Pair, Settings (and later the service). */
    lateinit var link: Link
        private set

    /** This phone's songs (#48). */
    lateinit var library: Library
        private set

    /** Runs the foreground service while the phones are linked. */
    lateinit var linkSession: LinkSession
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
            log = { AppLog.i("Link", it) },
            transport = SocketFrameTransport(),
            installId = InstallId(identityDataStore)::get,
            appVersion = BuildConfig.VERSION_NAME,
            keepAwake = LowLatencyWifiLock(this)::hold
        )
        link.start()
        library = Library(
            source = AndroidSongSource(this),
            store = JsonFileLibraryStore(File(filesDir, "library/my_songs.json")),
            scope = appScope,
            log = { AppLog.i("Library", it) }
        )
        library.start()
        linkSession = LinkSession(
            status = link.status,
            scope = appScope,
            start = { LinkService.start(this) },
            stop = { LinkService.stop(this) },
            log = { AppLog.i("Service", it) }
        )
        linkSession.begin()
    }

    /** The user's Disconnect, from Home or the notification: drop the link and the service. */
    fun disconnect() {
        link.disconnect()
        linkSession.stopNow()
    }
}

/** The shared [Link], for view models: `(application as TandemMotoApp).link`. */
val Context.link: Link get() = (applicationContext as TandemMotoApp).link
