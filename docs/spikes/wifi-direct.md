# Wi‑Fi Direct spike: findings (#22)

**Date:** 2026-09-25 · **Phones:** P1 Samsung Galaxy S25 (Android 16, One UI) ↔ P4 Xiaomi Redmi Y2 (Android 9, MIUI), see [`TEST_MATRIX.md`](../TEST_MATRIX.md) · **Build:** the throwaway Wi‑Fi Direct lab on draft PR #37 (`pr37-78169fe`, QA build via `/play-test`) · **Setup:** phones side by side (~1 m) except S8 (~12 m); **neither phone connected to a Wi‑Fi network**, which is also the on-bike situation.

The lab logged every Wi‑Fi Direct event, timing and ping to the in-app diagnostic log; these findings come from the two log exports of three sessions (13:00–13:12, 13:23–13:28, 13:38–13:46).

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
| Screen off | P4 (Android 9): **stays connected**, slowed down. P1 (Android 16): **socket killed within ~1 s** (`Software caused connection abort`) |
| Latency (1 ping/s) | ⚠️ **Median ≈ 150–250 ms, p95 ≈ 0.5–2.4 s**, occasionally 5–15 ms. Too slow for Phase 3/4 targets as is. **Open** |

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

## Recommendations

**#21 Discovery**
- Check the Nearby/location permission before `discoverPeers` (P1 reports only a generic `ERROR`). On Android ≤ 12, also check Location is switched on.
- Keep discovering until the **remembered partner** appears, with a timeout of **≥ 30 s**, and restart discovery rather than relying on one call. Never auto-pick "the first peer".

**#24 Pairing**
- Expect one Android invitation prompt on the partner phone the **first** time only; the UI should tell the partner to accept it.
- **Exactly one phone initiates.** Decide by a fixed rule (e.g. the phone with the smaller stored partner ID initiates; the other only discovers and accepts). **Never call `connect()` while a group with the partner exists or is forming.**
- Don't rely on `groupOwnerIntent`: the first pairing fixes the roles. Both phones must support both roles.

**#25 Command channel**
- Add `INTERNET` permanently, with a `docs/PRIVACY.md` note (used only for the direct phone-to-phone link; the Play listing will show "full network access").
- Group owner listens; the client connects to `groupOwnerAddress`, retrying every **250–500 ms for up to ~10 s** (the first attempt fails with `ENETUNREACH`).
- On startup, check for an existing group (`requestConnectionInfo`) and reuse it instead of forming a new one; judge the link only by the channel's own heartbeat, never by "group formed".

**#26 Status bar / #27 Auto-reconnect**
- Treat `Wi‑Fi Direct disabled`, group removed and socket errors as **immediate** loss (< 1 s).
- Heartbeat: show **Reconnecting…** after ~**5 s** without traffic but keep the socket; give up on it after ~**20–30 s** (the link survived 11.7 s and 3–4 s throttling gaps).
- Reconnect loop: rediscover the partner → the initiating phone connects → socket with retries. Budget ≈ **5–20 s** after Wi‑Fi returns. Android won't reconnect on its own.
- On an explicit user disconnect, remove the group (`removeGroup`); otherwise leave the persistent group alone.
- **Foreground service in Phase 1** (moved from Phase 5): without it, P1 kills the socket as soon as its screen turns off. Needs `FOREGROUND_SERVICE` + `_CONNECTED_DEVICE` (and `_MICROPHONE` from Phase 4), the Play declaration and demo video.

**Phase 3/4 (latency)**
- As measured, command latency (median ≈ 200 ms, p95 up to 2.4 s) would miss Phase 3's ~0.5 s mirroring and Phase 4's 300 ms voice targets. Resolve before Phase 3; see open questions.

## Open questions (lab v2 on draft PR #37)
1. **Latency cause and fix:** repeat S5 with a **low-latency Wi‑Fi lock** on/off, and with a denser stream (≈ 20 packets/s, closer to voice traffic) vs 1/s.
2. **Foreground service:** does it keep P1's socket alive with the screen off? (APK sideload: a Play upload with foreground-service permissions needs the Play declaration first.)
3. **Range:** a clean S8 walk-out with both screens kept on (lower priority).
