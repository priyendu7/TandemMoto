package com.tandemmoto.service

import android.annotation.SuppressLint
import android.content.pm.ServiceInfo
import android.os.Build

/**
 * The link service's foreground types (#40, #51, #70). A pure function, so it's tested on the
 * JVM.
 *
 * **Microphone** (#70): Android 11+ lets a foreground service use the mic in the background only
 * with this type, and Android 14+ lets it *take* the type only while the app is on screen. A
 * pause from the earbuds or the lock screen happens in the background, so the type is taken as
 * soon as it's allowed ([appVisible], mic permission granted) and kept while the service runs
 * ([micTaken]), not added when mic mode starts. It doesn't light Android's mic indicator; only
 * recording does. Android 10 and older (the Redmi on 9) have no such limit.
 */
object ServiceTypes {
    /** Whether the service should hold the microphone type now. */
    fun wantsMic(sdk: Int, micGranted: Boolean, appVisible: Boolean, micTaken: Boolean): Boolean =
        sdk >= Build.VERSION_CODES.R && micGranted && (micTaken || appVisible)

    @SuppressLint("InlinedApi") // each type is used only on the [sdk] that has it
    fun forState(sdk: Int, linkWanted: Boolean, playing: Boolean, mic: Boolean): Int {
        if (sdk < Build.VERSION_CODES.Q) return 0
        var types = 0
        if (linkWanted) types = types or ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
        if (playing) types = types or ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
        if (mic && sdk >= Build.VERSION_CODES.R) {
            types = types or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        }
        return if (types == 0) ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE else types
    }
}

/** Whether this phone could open its mic for the intercom right now (#70). */
enum class MicAccess {
    Ready,

    /** The mic permission isn't granted: Home asks for it. */
    NoPermission,

    /**
     * Android 11+: the link service started in the background (a reconnect with the app closed),
     * so it couldn't take the microphone type; opening the app fixes it.
     */
    OpenAppFirst;

    companion object {
        fun of(sdk: Int, micGranted: Boolean, micTaken: Boolean): MicAccess = when {
            !micGranted -> NoPermission
            sdk < Build.VERSION_CODES.R || micTaken -> Ready
            else -> OpenAppFirst
        }
    }
}
