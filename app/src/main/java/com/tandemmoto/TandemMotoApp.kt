package com.tandemmoto

import android.app.Application
import android.content.Context
import android.os.StatFs
import androidx.core.net.toUri
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
import com.tandemmoto.playlist.JsonFileRidePlaylistStore
import com.tandemmoto.playlist.PlaylistSync
import com.tandemmoto.playlist.RidePlaylist
import com.tandemmoto.service.LinkService
import com.tandemmoto.service.LinkSession
import com.tandemmoto.transfer.SongStore
import com.tandemmoto.transfer.SongTransfers
import com.tandemmoto.transfer.TransferConnection
import com.tandemmoto.transfer.WindowSettingsStore
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow

private val Context.partnerDataStore by preferencesDataStore(name = "partner")
private val Context.identityDataStore by preferencesDataStore(name = "identity")

class TandemMotoApp : Application() {
    /** The app's single Wi-Fi Direct link, shared by Home, Pair, Settings (and later the service). */
    lateinit var link: Link
        private set

    /** This phone's songs (#48). */
    lateinit var library: Library
        private set

    /** The shared ride playlist (#49). */
    lateinit var ridePlaylist: RidePlaylist
        private set

    /** Song transfer and the song window (#50). */
    lateinit var transfers: SongTransfers
        private set

    lateinit var windowSettings: WindowSettingsStore
        private set

    /**
     * The current song's place in the ride playlist, which the song window follows. The player
     * (#51) moves it; until then it's the first song.
     */
    val currentSongIndex = MutableStateFlow(0)

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
        val installId = InstallId(identityDataStore)
        link = Link(
            driver = AndroidWifiP2pDriver(this),
            preconditions = AndroidDiscoveryPreconditions(this),
            store = DataStorePartnerStore(partnerDataStore),
            scope = appScope,
            log = { AppLog.i("Link", it) },
            transport = SocketFrameTransport(),
            installId = installId::get,
            appVersion = BuildConfig.VERSION_NAME,
            keepAwake = LowLatencyWifiLock(this)::hold
        )
        link.start()
        ridePlaylist = RidePlaylist(
            store = JsonFileRidePlaylistStore(File(filesDir, "library/ride_playlist.json")),
            installId = installId::get,
            scope = appScope,
            log = { AppLog.i("Playlist", it) }
        )
        library = Library(
            source = AndroidSongSource(this),
            store = JsonFileLibraryStore(File(filesDir, "library/my_songs.json")),
            scope = appScope,
            log = { AppLog.i("Library", it) },
            partnerSongs = { ridePlaylist.partnerSongs }
        )
        ridePlaylist.start()
        library.start()
        PlaylistSync(
            playlist = ridePlaylist,
            library = library,
            channelState = link.channel.state,
            incoming = link.channel.incoming,
            send = link.channel::send,
            partner = link.partner,
            scope = appScope,
            log = { AppLog.i("Playlist", it) }
        ).start()
        windowSettings = WindowSettingsStore(this)
        transfers = SongTransfers(
            playlist = ridePlaylist.state,
            library = library.state,
            currentIndex = currentSongIndex,
            settings = windowSettings.settings,
            channelState = link.channel.state,
            incoming = link.channel.incoming,
            send = link.channel::send,
            endpoint = link.endpoint,
            connection = TransferConnection(SocketFrameTransport(), appScope) {
                AppLog.i("Transfer", it)
            },
            store = SongStore(File(filesDir, "partner_songs")),
            freeBytes = { StatFs(filesDir.path).availableBytes },
            openSong = { uri -> contentResolver.openInputStream(uri.toUri()) },
            scope = appScope,
            log = { AppLog.i("Transfer", it) }
        )
        transfers.start()
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
