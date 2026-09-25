# Wi‑Fi Direct spike: findings (#22)

**Date:** 2026-09-25 (round 1: lab v1; round 2: lab v2) · **Phones:** P1 Samsung Galaxy S25 (Android 16, One UI) ↔ P4 Xiaomi Redmi Y2 (Android 9, MIUI), see [`TEST_MATRIX.md`](../TEST_MATRIX.md) · **Build:** the throwaway Wi‑Fi Direct lab on draft PR #37, QA builds via `/play-test`: v1 `pr37-78169fe`, v2 `pr37-136f1d6` (adds a low-latency Wi‑Fi lock, a 1/s vs 20/s ping rate, a foreground-service switch and per-peer timings) · **Setup:** phones side by side (~1 m) except S8 (~12 m); **neither phone connected to a Wi‑Fi network**, which is also the on-bike situation.

The lab logged every Wi‑Fi Direct event, timing and ping to the in-app diagnostic log; these findings come from the log exports of round 1 (13:00–13:46, v1) and round 2 (15:22–15:51, v1 then v2).

## Summary

| Question | Answer |
|---|---|
| Is Wi‑Fi Direct usable on both phones? | ✅ Declared and enabled on both. Discovery, group formation and a TCP socket all work across Samsung ↔ Xiaomi and Android 16 ↔ 9 |
| Does the socket need `INTERNET`? | ✅ **Yes.** Without it, *any* socket fails at creation (`socket failed: EPERM`, emulator self-test). #25 must add it permanently |
| Discovery time | Partner seen **1–14 s** after Discover (median ≈ 7 s over 14 runs). Both phones must be discovering |
| First connection | ≈ **15 s**, including the user accepting Android's invitation on the other phone (asked **once**) |
| Reconnection | **1.4–7 s**, no prompt (Android reuses a *persistent group*) |
| Which phone is group owner? | **Fixed by the first pairing** (P1 in all 7 connections), whatever the initiator or `groupOwnerIntent` |
| Socket after group formed | First attempt always fails (`ENETUNREACH`); connected **0.55–1.1 s** after the group formed |
| Detecting a drop (airplane mode, disconnect) | **< 1 s**, from Android's own events (`Wi-Fi Direct disabled`, group removed) |
| Automatic reconnection by Android | ❌ **None.** The app must rediscover and reconnect |
| Screen off | P4 (Android 9): **stays connected**, slowed down. P1 (Android 16): **socket killed within ~1 s** (`Software caused connection abort`), **unless a foreground service is running**: then it stays connected (≈ 6 min tested) |
| Range | ✅ Connected at ≈ 18 m (60 ft) with higher latency (p95 ≈ 0.35 s at 1 packet/s); not tested further |
| Latency | With sparse traffic (1 packet/s): median ≈ 50–250 ms, **p95 up to 2.4 s** (Wi‑Fi power saving). With **steady traffic (20 packets/s): median ≈ 10 ms, p95 ≈ 40–75 ms** ✅. A low-latency Wi‑Fi lock helps a little more |

## Results by step

### S0: is Wi‑Fi Direct usable?
- Both phones: feature declared, Wi‑Fi Direct enabled, loopback socket self-test OK.
- **P1 without the Nearby devices permission:** `discoverPeers` doesn't throw; it fails through its listener with a generic **`ERROR`**. The app must check the permission itself before calling it.
- **P1** discovered with **Location switched off** (Android 13+ uses Nearby devices with `neverForLocation`).
- **P4 (Android 9)** discovered **before the location permission was granted**, with Location switched on.

### S1: discovery
- "First peer" times (0.1–3.8 s) are misleading: **two unrelated Wi‑Fi Direct devices nearby** (`peer-2ee0`, `peer-48fd`) usually appeared first.
- The **partner** appeared 1.1–13.5 s after Discover (median ≈ 7 s over 14 runs), only while the partner was also discovering (a phone is visible only while it's in discovery).
- P1 was consistently slower to see P4 (3.8–13.5 s) than P4 was to see P1 (1.1–5.5 s).

### S2–S4: connecting and group owner

| Connection | Initiator, intent | Group formed after | Group owner | Prompt on the other phone |
|---|---|---|---|---|
| 1 | P1, 15 | 14.6 s | P1 | Yes (first pairing) |
| 2 | P4, 15 | 3.5 s | **P1** | No |
| 3 | P1, **0** | 4.5 s | **P1** | No |
| 4 | P1, 0 (P4 also tapped Connect) | 7.0–14.8 s | P1 | No |
| 5 | P4, 15 | 3.7 s | P1 | No |
| 6–7 (after airplane mode) | P4, 15 | 1.4–4.4 s | P1 | No |

After the first pairing, Android re-invokes the saved persistent group with its original roles; `groupOwnerIntent` is ignored.

**The group outlives the app.** Twice, both phones still showed each other as *connected* 10–15 minutes after the socket died and both labs were closed. A formed group says nothing about whether the app's link works.

### Socket setup
- The client's first `connect()` right after "group formed" always failed with `ENETUNREACH`: the P2P interface isn't up yet.
- Retrying every 500 ms, the socket connected 0.55–0.73 s after the group formed (client side), 0.8–1.1 s (group owner side). One run needed 3.6–5.4 s, during a double-Connect (below).

### S5: latency
With both screens on, ~1 m apart, no Wi‑Fi network connected, 1 ping per second each way:

| Session | Median | p95 | Longest gap |
|---|---|---|---|
| Connection 1 | 131 ms | 2.4 s | 4.0 s |
| Connection 3 | 172–235 ms | 0.57–0.88 s | 2.1 s |
| Connection 4 | 143–159 ms | 0.49–1.1 s | 2.5 s |

The first pings of each session were often 4–15 ms, and latency **dropped** (median ≈ 65 ms, many 5–15 ms) while P4's screen was off. Likely causes, not yet proven: **Wi‑Fi power saving** with sparse traffic, and **background Wi‑Fi scans** (a phone with Wi‑Fi on but not connected scans for networks, more often with the screen on).

### S6: screen off
- **P4 screen off** (13:25:30): connection **stayed up** for 2+ minutes. The app was throttled (10 s log ticks stretched to 20–25 s) and its pings arrived with **3–3.7 s gaps**.
- **P1 screen off** (13:27:50, and 13:08:15 with both screens off): **socket killed within a second**. P1 logged `Software caused connection abort`, P4 `Connection reset`. The Wi‑Fi Direct group was *not* removed. This is Android tearing down the backgrounded app's socket, consistent with the app losing network access; a foreground service should prevent it (**to confirm**).

### S7: airplane mode
- On either phone: the drop was seen on **both** phones in **< 1 s** (`Wi-Fi Direct disabled` locally; group removed on the partner).
- After airplane mode was switched off, Wi‑Fi Direct came back, but **nothing reconnected until Discover and Connect were tapped**.
- Manual recovery: partner found in ~5–11 s, group formed in 1.4–4.4 s, socket 0.55–3.6 s.

### S8: walking apart (~12 m)
- The socket survived an **11.7 s silence** and recovered without reconnecting (TCP held on).
- The run isn't a clean range measurement: the silence overlapped a stuck double-Connect (below), and the final drop was P1's `Software caused connection abort` with **no** group removal, i.e. most likely P1's screen turning off, not range. Rider and pillion are ~1 m apart on the bike, so range isn't a primary concern.

### Pitfall: both phones tapping Connect
In every reconnection both phones tapped Connect. The second `connect()` hit an already forming or formed group and left the partner stuck as **invited**. While it was stuck: the first socket took **5.4 s**, latency rose to a median of **0.5–4.3 s** (p95 up to 9.5 s) with an **11.7 s** silence; it recovered once the invitation cleared (≈ 60 s later).

## Round 2: lab v2 (15:41–15:51)

P1 ↔ P4, ~1 m, both screens on unless noted. P4 wasn't connected to a Wi‑Fi network; **P1 was** (the new Step 0 line showed it). Connect was tapped on P4 only: group formed in 1.7 s, socket 0.6 s later. Each step: same settings on both phones, stats reset, ~40–60 s.

### Latency (L1–L4)

| Step | Rate | Wi‑Fi lock | P1 median / p95 | P4 median / p95 | Longest gap |
|---|---|---|---|---|---|
| L1 | 1/s | off | 49–88 ms / 1.1–1.4 s | 48–55 ms / 1.05–1.3 s | 2.2 s |
| L2 | 1/s | on | 70–143 ms / 0.26–0.65 s | 40–45 ms / 0.14–0.52 s | 1.5 s |
| L3 | 20/s | off | 13–15 ms / 62–75 ms | 10–12 ms / 51–67 ms | 0.18 s |
| L4 | 20/s | on | 8–11 ms / 41–51 ms | 7–10 ms / 37–46 ms | 0.17 s |

The lock is Android's low-latency mode on P1 and the older "high performance" mode on P4 (Android 9).

- **Steady traffic is what fixes latency.** At 20 packets/s the radio stays awake: ~10 ms median, well inside Phase 4's 300 ms. With 1 packet/s it dozes between packets, which explains round 1's 150–250 ms.
- **The Wi‑Fi lock alone doesn't fix sparse traffic** (p95 still up to 0.65 s); on top of steady traffic it lowers latency by another ~20–30%.
- Latency was fine even with P1 connected to a Wi‑Fi network.

### Foreground service (F1/F2)
- Foreground service on both phones, 1/s, then P1 locked: **no drop for ≈ 6 minutes** (15:44:55–15:50:39); P1 kept pinging every second with median ≈ 35–60 ms. The session ended only when the lab was closed. **The foreground service fixes P1's screen-off socket kill.**
- P4, left on the desk with its screen off and its foreground service on, was still **throttled by MIUI** (3–4 s gaps, 10 s log ticks stretched to 13–19 s), but stayed connected.

### Range (end of round 2)
- P1 was carried **≈ 18 m (60 ft)** away with its screen on; P4 stayed behind with its screen off (foreground service on both, 1 packet/s).
- **Stayed connected, no drop.** At P1, latency rose from ≈ 35 ms to ≈ 60 ms median, with recent round trips at 110–160 ms and p95 ≈ 0.35 s; 1–2 pings missed.
- Round 1's ~12 m walk (S8) wasn't a clean range test; this one is. Rider and pillion are ~1 m apart on the bike, so range has plenty of margin.
- Not run: a 10–15 minute locked run with battery %. That belongs in T10 (Phase 5) with the real service.

### More pitfalls seen in round 2
- **Both phones tapping Connect at once deadlocks** (15:30–15:31 and 15:37–15:40): both sides stay *invited*, no group forms, and `cancelConnect` fails with `BUSY` until the group is removed. A per-phone guard can't prevent it; only a one-initiator rule can.
- **A stuck invitation survives an app restart:** after reopening the lab, the partner still showed *invited* with no group.
- **P4's Wi‑Fi Direct got stuck returning `BUSY`** to every Discover and Stop (13 times over ~1.5 min) until Wi‑Fi was switched off and on; then it worked immediately. The app can't reset Wi‑Fi itself.

## Recommendations

**#21 Discovery**
- Check the Nearby/location permission before `discoverPeers` (P1 reports only a generic `ERROR`). On Android ≤ 12, also check Location is switched on.
- Keep discovering until the **remembered partner** appears, with a timeout of **≥ 30 s**, and restart discovery rather than relying on one call. Never auto-pick "the first peer".
- If Discover keeps failing with `BUSY` (P4: 13 times in a row), ask the user to switch Wi‑Fi off and on; nothing else cleared it.

**#24 Pairing**
- Expect one Android invitation prompt on the partner phone the **first** time only; the UI should tell the partner to accept it.
- **Exactly one phone initiates.** Decide by a fixed rule (e.g. the phone with the smaller stored partner ID initiates; the other only discovers and accepts). **Never call `connect()` while a group with the partner exists or is forming.**
- Don't rely on `groupOwnerIntent`: the first pairing fixes the roles. Both phones must support both roles.
- On startup, if the partner shows as *invited* but no group exists, `cancelConnect` first: a stuck invitation survives app restarts.

**#25 Command channel**
- Add `INTERNET` permanently, with a `docs/PRIVACY.md` note (used only for the direct phone-to-phone link; the Play listing will show "full network access").
- Group owner listens; the client connects to `groupOwnerAddress`, retrying every **250–500 ms for up to ~10 s** (the first attempt fails with `ENETUNREACH`).
- On startup, check for an existing group (`requestConnectionInfo`) and reuse it instead of forming a new one; judge the link only by the channel's own heartbeat, never by "group formed".
- **The heartbeat doubles as a keep-awake.** Sparse traffic lets the radio doze (p95 up to 1.4 s at 1/s); steady traffic keeps latency at ~10 ms. Send small heartbeats at roughly **5–10 per second** while connected (20/s was measured; the lowest rate that keeps p95 under ~100 ms still has to be found), and hold a **low-latency Wi‑Fi lock** (`WIFI_MODE_FULL_LOW_LATENCY`, API 29+) while connected. Measure the battery cost in T10.

**#26 Status bar / #27 Auto-reconnect**
- Treat `Wi‑Fi Direct disabled`, group removed and socket errors as **immediate** loss (< 1 s).
- Heartbeat: show **Reconnecting…** after ~**5 s** without traffic but keep the socket; give up on it after ~**20–30 s** (the link survived 11.7 s and 3–4 s throttling gaps).
- Reconnect loop: rediscover the partner → the initiating phone connects → socket with retries. Budget ≈ **5–20 s** after Wi‑Fi returns. Android won't reconnect on its own.
- On an explicit user disconnect, remove the group (`removeGroup`); otherwise leave the persistent group alone.
- **Foreground service in Phase 1** (moved from Phase 5): without it, P1 kills the socket as soon as its screen turns off; with it (lab v2), P1 stayed connected while locked. Type `connectedDevice` (allowed by `CHANGE_WIFI_STATE`), plus `microphone` from Phase 4; needs `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_CONNECTED_DEVICE`, the Play declaration and demo video.
- Aggressive skins (P4, MIUI) still throttle the app while locked even with the service: 3–4 s gaps. Keep the ~20–30 s give-up timeout.

**Phase 3/4 (latency)**
- Voice (Phase 4) sends ~50 packets/s, which keeps the radio awake: expect ~10 ms network latency, leaving most of the 300 ms budget for codec and buffering.
- Playback commands (Phase 3) meet the ~0.5 s mirroring target only if the #25 keep-awake heartbeat is running.

## Carried forward (not blocking #22)
1. **Heartbeat rate vs battery:** find the lowest rate that keeps p95 under ~100 ms (try 5/s and 10/s) and its battery cost. Belongs in #25 / T10.
2. **Ride-length run:** 10–15+ minutes locked with the foreground service, with battery % (T10, Phase 5).
3. **Range beyond ≈ 18 m:** where the link actually drops (low priority: rider and pillion are ~1 m apart).
