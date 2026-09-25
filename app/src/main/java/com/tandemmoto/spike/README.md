# spike (throwaway, #22)

The **Wi‑Fi Direct lab**: Settings → Wi‑Fi Direct lab. It measures how Wi‑Fi Direct behaves on
real phones before the link layer (#21, #24, #25, #27) is built.

**This branch is never merged.** It lives on a draft PR so it can be installed with
`/play-test` or the CI debug APK. Only the findings are merged, in `docs/spikes/wifi-direct.md`.

- `WifiDirectLab`: discovery, connect (group-owner intent 0 or 15), a TCP ping over the group
  (group owner listens on port 8988, the other phone connects), and a timestamped event timeline.
  Every event is also written with `AppLog("Spike", …)`, so Settings → Export diagnostic logs
  captures the whole run without `adb`. Peers appear by name on screen but only as a short hash
  in the log.
- `PingProtocol` / `PingStats`: pure Kotlin, unit-tested.

Needs `android.permission.INTERNET`: Android won't open any TCP socket without it, even one that
only goes over Wi‑Fi Direct. The real command channel (#25) adds it permanently, with a
`docs/PRIVACY.md` note.
