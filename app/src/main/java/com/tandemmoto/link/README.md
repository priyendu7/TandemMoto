# link

Wi-Fi Direct phone-to-phone connectivity: discovery, pairing, group formation,
persistent command/state socket, auto-reconnect, connection status.

## Discovery (#21)

- `WifiP2pDriver`: the slice of WifiP2pManager discovery needs. `AndroidWifiP2pDriver` wraps the
  platform API; tests use a fake.
- `PeerDiscovery`: searches for `SCAN_DURATION_MS` (60 s; 30 s felt too short on real phones),
  then stops; the Pair screen instead searches **continuously while open**, because a phone only
  receives an invitation while it's discovering. `pause()` ends the app's loop without asking
  Android to stop: stopping and connecting at once makes connect() fail (seen on both phones). Checks the permission before asking Android, restarts discovery whenever Android
  stops it, retries `BUSY` (1 s, 2 s, 4 s) before reporting `Stuck`, and resumes by itself when
  Wi-Fi comes back. Phones (device category 10) are listed before other devices.
- Location off doesn't block the search (the Redmi Y2 on Android 9 discovers without it); on
  Android ≤ 12 a search that finds nothing with Location off suggests turning it on.
- Wi-Fi is only "off" when Android says so: the driver seeds the state from `WifiManager`,
  because the Redmi Y2 never sent its initial Wi-Fi Direct state.
- Logs go to `AppLog` under the tag `Link`, with hashed peer IDs only.

Design notes come from the spike: [`docs/spikes/wifi-direct.md`](../../../../../../../docs/spikes/wifi-direct.md).

Development plan: Phase 1. Exit criteria: two phones pair, see each other's
connection state, and survive a simulated link drop without user intervention.
