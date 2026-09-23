package com.tandemmoto.service

import android.app.Service
import android.content.Intent
import android.os.IBinder

// TODO(Phase 4/5): startForeground() with notification; host link, mic-mode and playback lifecycles.
class TandemMotoForegroundService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null
}
