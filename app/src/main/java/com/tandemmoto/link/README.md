# link

Wi-Fi Direct phone-to-phone connectivity: discovery, pairing, group formation,
persistent command/state socket, auto-reconnect, connection status.

## Discovery (#21)

- `WifiP2pDriver`: the slice of WifiP2pManager discovery needs. `AndroidWifiP2pDriver` wraps the
  platform API; tests use a fake.
- `PeerDiscovery`: searches for `SCAN_DURATION_MS` (30 s, to revisit after field testing), then
  stops. Checks permission and (Android ≤ 12) Location before asking Android, restarts discovery
  whenever Android stops it, retries `BUSY` (1 s, 2 s, 4 s) before reporting `Stuck`, and resumes
  by itself when Wi-Fi comes back. Phones (device category 10) are listed before other devices.
- Logs go to `AppLog` under the tag `Link`, with hashed peer IDs only.

Design notes come from the spike: [`docs/spikes/wifi-direct.md`](../../../../../../../docs/spikes/wifi-direct.md).

Development plan: Phase 1. Exit criteria: two phones pair, see each other's
connection state, and survive a simulated link drop without user intervention.
