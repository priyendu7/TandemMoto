# service

Foreground Service keeping the Wi-Fi Direct link, mic mode, and playback
alive with the screen off. Handles peer-disconnect and headset-disconnect
edge cases (force mic mode off, surface a clear error state).

Development plan: Phase 1 (basic), Phase 5 (robustness).

Not declared in the manifest yet. When the service is built, add it together with
`FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MICROPHONE`, `FOREGROUND_SERVICE_CONNECTED_DEVICE`
and `android:foregroundServiceType="microphone|connectedDevice"`, and update the Play Console
Foreground service declaration (task description plus a demo video for each type) in the same release.
