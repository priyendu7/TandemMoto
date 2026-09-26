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
