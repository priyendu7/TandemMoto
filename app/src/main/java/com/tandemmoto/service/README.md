# service

Foreground service keeping the Wi-Fi Direct link (and later mic mode and playback) alive with the
screen off. Without one, the S25 closed the socket within a second of the screen locking (spike
#22).

## Link service (#40), and music (#51)

- Since #51 the same service also runs while music plays (type `mediaPlayback`). `LinkSession`
  runs it while the link wants it **or** music plays, so Disconnect doesn't stop the music. Its
  own notification stays the link's; a second, **media** notification (song and ⏮ ⏯ ⏭, tied to
  the MediaSession) is shown while there are songs. On Android 13+ that one becomes the media
  card, which shows only the song, so the link's line couldn't share it (S25 phone test). Both
  are cleared when the service stops, so none is left behind that can't be removed (Redmi).
- `LinkService`: type `connectedDevice` (allowed by `CHANGE_WIFI_STATE`), no work of its own. Its
  notification ("Connection" channel, low importance, silent) follows the link status and has a
  **Disconnect** action: `Link.disconnect()` sends `Bye(Disconnected)` so the partner shows
  "… disconnected", removes the group, and the service stops.
- `LinkSession` decides when it runs, from the link status alone (JVM-tested): starts on the first
  Connected (the app is open then; Android 12+ refuses to start a foreground service from the
  background), keeps running through drops shorter than 2 minutes so a background reconnect (#27)
  still has it, and stops after 2 minutes not connected, on Disconnect, or when not paired.
- Notifications (Android 13+) are optional: offered once on the first connection, and from
  Settings. Denied, the service still runs; Android only hides the notification.
- Not sticky: after a crash, a restart from the background might not be allowed to go foreground.
- No CPU wake lock yet: the spike's foreground service kept the S25 connected while locked without
  one. Add a partial wake lock (`WAKE_LOCK` is declared) only if the 10-minute locked test drops.
- Play Console needs the foreground-service declaration: [`docs/RELEASING.md`](../../../../../../../docs/RELEASING.md).

Phase 4 adds the `microphone` type (and its own Play declaration) for the intercom; Phase 5 adds
headset-disconnect handling.

Development plan: Phase 1 (#40), Phase 4–5.

## The microphone type (#70)

Android 11+ only lets a foreground service use the mic in the background with the **microphone**
type, and Android 14+ only lets it *take* that type while the app is on screen. A pause from the
earbuds or the lock screen happens in the background, so `LinkService` takes the type whenever it
goes foreground with the app on screen and the mic permission granted, and keeps it until it
stops (`ServiceTypes`). Taking the type doesn't light Android's mic indicator; recording does.

- Started from the background (a reconnect with the app closed): no mic type, logged, and
  `MicAccess.OpenAppFirst`; the next time the app comes on screen (`TandemMotoApp.visibleTicks`)
  the service adds it.
- If Android refuses the type anyway, the service carries on without it: the link and the music
  never depend on the mic.
- Android 10 and older (the Redmi on 9) have no service types; a running foreground service is
  enough.
