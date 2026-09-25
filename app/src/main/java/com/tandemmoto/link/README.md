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

## Pairing and the partner (#24)

- `Link` pairs, remembers the partner (`Partner`, `PartnerStore`) and reconnects to it on start.
  The phone where the user tapped is the `Initiator` and makes every later connection; the other
  only stays visible (both connecting deadlocks). A group with any other phone is removed (the
  wrong-device guard).

## Command channel (#25)

- `CommandChannel`: once a group with the partner forms, the group owner listens on TCP port
  48152 and the client connects to the owner's address, retrying every 500 ms for ~10 s, then
  every 2 s (the first attempt after a group forms always fails with ENETUNREACH). The socket
  sits behind `FrameTransport` (`SocketFrameTransport` on phones, an in-memory fake in tests).
- Every connection starts with `Hello` from both sides: app version, a random install ID
  (`InstallId`), the install ID the sender thinks its partner has, and its role. The partner's ID
  is learned from its first Hello, so each phone knows exactly which app it paired with. A
  different ID, or a Hello naming someone else, is refused with `Bye(NotYourPartner)`; the
  wrong-device guard sends that too before removing a group, and so does Forget partner.
- **Connected means the partner's app answered**, not just that Android has a group: the group
  outlives the apps. With a group but no answer for 5 s the status says to open TandemMoto on the
  partner's phone, and the socket keeps retrying, so reopening the app reconnects on its own.
- Heartbeat: both phones send a `Ping` every 100 ms and answer every `Ping` with a `Pong`
  (at 200 ms the phones' p95 went over 100 ms in 2 of 15 ten-second windows). It
  also keeps the radio awake (sparse traffic gave a p95 of up to 1.4 s in the spike), together
  with a low-latency Wi-Fi lock (`LowLatencyWifiLock`) while the channel is open. Round trips
  are logged every 10 s (`RTT median … p95 …`).
- A connection is lost when the socket closes or breaks, or after 6 s without any message (not
  3 s: MIUI freezes the app for 3–4 s with the screen off). A clean close means the partner's
  app was closed ("Open TandemMoto on …"); silence or a broken socket means the phone went away
  ("Not connected"). Android can take ~13 s to drop the group after the other phone's Wi-Fi goes
  off (Redmi), so the channel decides that, not the group.
- Re-forming a dropped group is auto-reconnect (#27); the channel only reopens the socket while
  the group exists.

Design notes come from the spike: [`docs/spikes/wifi-direct.md`](../../../../../../../docs/spikes/wifi-direct.md).

Development plan: Phase 1. Exit criteria: two phones pair, see each other's
connection state, and survive a simulated link drop without user intervention.
