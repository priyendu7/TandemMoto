# Device & peripheral test matrix

Wi‑Fi Direct, Bluetooth media controls, audio routing and battery optimisation all behave differently across phones and accessories (see *Key risks* in [`DEVELOPMENT_PLAN.md`](DEVELOPMENT_PLAN.md) §5). Emulators can't test any of this, so TandemMoto is validated on the real devices listed here.

**How to use this file**
- Refer to devices by their ID (`P1`, `R1`, …) in PR descriptions, bug reports and the results log.
- Before a phase's exit criteria can be ticked, run that phase's tests from the [test catalogue](#test-catalogue) on at least the [minimum coverage](#minimum-coverage).
- Own something that isn't listed, especially a **Wanted** item? Add it in a PR, or comment on the tracking issue, with your GitHub handle as owner.

---

## Devices

### Phones

| ID | Model | Android | Skin | Battery optimisation | Owner | Notes |
|---|---|---|---|---|---|---|
| P1 | Samsung Galaxy S25 | 16 | One UI | Moderate: "Sleeping apps" / "Deep sleeping apps" | @priyendu7 | Android 13+ path (Nearby devices permission) |
| P2 | Motorola Edge 40 Neo | 14 | My UX (near-stock) | Light | @priyendu7 | Android 13+ path; near-stock baseline |
| P3 | realme (exact model to confirm) | 10 | realme UI | Aggressive: auto-launch and background restrictions | @priyendu7 | Android ≤ 12 path (precise location permission for Wi‑Fi Direct) |
| P4 | Xiaomi Redmi Y2 | 9 | MIUI | Very aggressive: autostart, battery saver, background restrictions | @priyendu7 | Android ≤ 12 path; Xiaomi skin. No USB data connection to the dev laptop: install via Play QA link or APK, read logs via Settings → Export diagnostic logs |
| — | **Wanted:** Xiaomi/Redmi/POCO on HyperOS | 14+ | | Very aggressive | | Current Xiaomi skin, the most common aggressive skin in India |
| — | **Not usable:** Xiaomi Redmi 3S | 6 | | | @priyendu7 | Below minSdk 26: the app can't be installed |

### Handlebar controls

The rider controls music from the bike. TandemMoto expects those controls to reach Android as standard media-button events through `MediaSession`, whether the bike acts as a Bluetooth HID remote or as an AVRCP controller.

| ID | Model | Connection | Buttons used | Key codes / events (fill in during T7) | Owner | Notes |
|---|---|---|---|---|---|---|
| R1 | Harley‑Davidson X440 T: handlebar switchgear via the bike's TFT Bluetooth link | Bike ↔ phone Bluetooth (profile to be confirmed: HID or AVRCP) | TBD | TBD | @priyendu7 | Owner has controlled phone music from the handlebar buttons before, so the bike does send media controls. **To verify in T7:** which event each button sends, and that TandemMoto's `MediaSession` receives them on both P1 and P2. Owner also reports phone audio doesn't route to the bike; recheck with TandemMoto running: see [quirks](#oem-and-device-quirks) |
| — | **Wanted:** a standalone handlebar Bluetooth media remote | BT HID | | | | Generic ₹500–1500 remotes; the most common setup for bikes without connected switchgear |

### Bluetooth earbuds (pillion)

| ID | Model | A2DP (music) | HFP (mic for intercom and calls) | Owner | Notes |
|---|---|---|---|---|---|
| E1 | boAt Nirvana Ion | Yes | Yes | @priyendu7 | Budget TWS; check SCO mic quality and whether calls take the headset back cleanly (T9) |
| — | **Wanted:** Samsung Galaxy Buds (any) | | | | Pairs with P1; tests Samsung's own audio stack |
| — | **Wanted:** another popular budget TWS (e.g. OnePlus Nord Buds, Noise, JBL) | | | | |

### Wired earphones (rider)

| ID | Model | Connector | Inline mic / button | Owner | Notes |
|---|---|---|---|---|---|
| W1 | Apple EarPods (USB‑C) | USB‑C (digital USB audio) | Yes / yes | @priyendu7 | USB audio device, not analogue. Check the inline button reaches `MediaSession` on Android |
| — | **Wanted:** 3.5 mm earphones with inline mic | 3.5 mm | Yes | | Only for phones that still have a headphone jack |

---

## Minimum coverage

A phase's device tests count as done when they pass on:
1. **One mixed-vendor phone pair.** Default: **P1 ↔ P2** (Samsung ↔ Motorola), or **P1 ↔ P4** (Samsung ↔ Xiaomi), the pair available for Phase 1. Wi‑Fi Direct negotiation between different vendors is the riskiest case.
2. **Both permission paths:** one phone on Android 13+ (P1 or P2) and one on Android 12 or older (**P3** or **P4**).
3. **One aggressive-battery phone** for the screen-off tests (T10). Default: **P4** (MIUI), else **P3**.
4. **Rider setup** W1 (+ R1 from Phase 3) on one phone, **pillion setup** E1 on the other.

---

## Test catalogue

Pass thresholds come from the [PRD](PRD.md) §4 and the phase exit criteria in the [development plan](DEVELOPMENT_PLAN.md) §3.

| ID | Test | From phase | Steps | Pass |
|---|---|---|---|---|
| T0 | Launch + permission prompts | 0 | Fresh install → splash → Home; in the connection bar tap Allow; deny twice → Open settings; on Android ≤ 12 pick "Approximate" | Splash closes by itself and Home opens; the connection bar shows the prompt until Nearby (or precise location) is granted, then "Not paired · Tap to pair"; Open settings appears after two denials; Precise hint shows; the rest of Home works throughout |
| T1 | Discovery | 1 | Both phones open Pair | Each phone sees the other within ~10 s |
| T2 | Pair + remembered partner | 1 | Tap partner, accept on the other phone; restart both apps | Group forms (the partner accepts Android's prompt the first time only); after restart, reconnects to that partner only. Either phone may be group owner: the first pairing fixes the roles |
| T3 | Airplane-mode drop | 1 | Airplane mode on one phone for 10 s, then off | Link restores with no taps on either phone (**Phase 1 exit criterion**) |
| T4 | Range walk-out | 1 | Walk apart until the link drops, then walk back | Reconnects automatically; note the distance at drop |
| T5 | Song transfer | 2 | Add a 25 MB song on one phone; repeat with a mid-transfer link drop; play through the playlist | Completes and verifies; resumes after the drop; songs inside the window are ready before their turn; record the time taken |
| T6 | Playback mirror | 3 | Play / pause / skip / seek from each phone | Other phone follows within ~0.5 s |
| T7 | Handlebar and inline controls | 3 | Press every R1 and W1 button during playback | Each button works on both phones; key codes recorded in the device table |
| T8 | Mic mode + latency | 4 | Pause → talk both ways; measure the round trip (clap test) | Intercom opens on both phones; round trip under ~300 ms |
| T8b | Self-mute | 4 | In mic mode, mute P1; talk from both sides; unmute. Then mute, pause/resume music, drop the link, make a call and restart the app | Muted phone's voice stops and its mic indicator turns off within ~0.5 s while it still hears the partner; partner shows "Partner muted"; stays muted through pause/resume, reconnect, call hold and restart until unmuted by hand |
| T9 | Call hold | 5 | Incoming and outgoing call on each phone: cellular + one VoIP app (e.g. WhatsApp) | Both phones pause and the intercom closes within ~1 s; the call is audible on W1 and E1; both phones land in Paused + mic mode after; no stuck mic or audio route |
| T10 | Screen-off ride | 5 | Screen off, music + intercom in use for 1 h | Link and service stay alive; record battery % used per phone |
| T11 | Real ride | 6 | P1 + P2 on the X440 T at riding speed, R1 + W1 rider, E1 pillion | T3, T6, T8 and T9 behaviour holds; note wind/engine noise and intercom clarity |

---

## Results log

Newest first. One row per test run. Link an issue for every ❌.

| Date | App version | Test | Phones | Peripherals | Result | Notes / issue |
|---|---|---|---|---|---|---|
| 2026-09-26 | TandemMoto #60 build (`pr64-8c120ef`, PR #64) | Playback mirroring | P1 ↔ P4 | E1 | ✅ | Play, pause, next, previous, seek (Home and Playlist bar) and Playlist taps on either phone followed on the other; notification, lock screen and earbuds mirrored; pause pressed on both at once ended paused on both; airplane mode mid-song kept both playing, a skip made offline won on reconnect; a reopened app joined the partner's song; a song not yet downloaded jumped ahead of other downloads. Follow delays not recorded (no logs sent). Known until #61: the phone that has the song starts without the other |
| 2026-09-26 | `main` after PR #58 | Phase 2 exit (#52): song ready before its turn | P1 ↔ P4 | — | ✅ | A new P1 song at position 2 showed "On this phone" on P4 before playback; playing song 1 and pressing Next through the first songs started each at once, no "Getting song…" (FLAC songs skipped as expected on P4) |
| 2026-09-26 | TandemMoto #51 build (`pr58-7d9de4a`, PR #58) | Player fixes: unsupported format, notifications | P1 ↔ P4 | — | ✅ | Songs 2 and 3 are FLAC files P4 (Android 9) can't decode: now skipped and marked "Can't play on this phone". P1 shows the media card and a separate "Connected to …" notification. P4's notifications clear after Disconnect/Close |
| 2026-09-26 | TandemMoto #51 build (`pr58-b91d7aa`, PR #58) | Local player + T5 speed | P1 ↔ P4 | E1, W1 | ⚠️ | Play/pause/next/previous from Home, notification and lock screen; headset buttons and unplug-to-pause; Getting song → plays; skip after 20 s apart; locked 5+ min; reorder without a gap. **T5 speed:** 22–45 MB songs in 8.4–20.6 s (14–27 Mbit/s) P1 → P4. On P4, songs 2 and 3 (35 and 37 MB) didn't play and logged no error (most likely an audio format P4 can't decode); on P1 the link's line was hidden in the media card; P4's notification couldn't be removed. Fixes in PR #58 |
| 2026-09-26 | TandemMoto #50 build (`pr57-9c1ff58`, PR #57) | T5 Song transfer + song window | P1 ↔ P4 | — | ✅ | Top 8 of P1's songs arrived on P4 (On both phones on P1); a big song resumed after airplane mode mid-transfer and finished; reorder into/out of the window downloaded/deleted; ahead 7 → 3 and Remove songs from partner worked; Waiting for connection while apart. Transfer speed not recorded (no log sent). Settings didn't scroll to Export logs: fixed in PR #57 |
| 2026-09-26 | TandemMoto build `pr56-af00742` (PR #56) | Status wording: partner disconnected, Stop while searching | P1 ↔ P4 | — | ✅ | "… disconnected · Ask them to tap Connect" on the other phone after Disconnect; while looking or reconnecting, the bar's dialog, TalkBack and the notification say Stop |
| 2026-09-26 | TandemMoto #49 build (`pr55-3b4d36a`, PR #55) | Ride playlist sync + reconnect after Disconnect | P1 ↔ P4 | — | ✅ | Songs added on each phone show on both with Added by you / From …; reorder and remove follow on the other; edits made apart merge on reconnect; survives restarts. Songs from the #48 build join the new playlist on first start (the S25 showed empty before that fix). Disconnect → Connect on either phone reconnects without restarting the apps |
| 2026-09-26 | TandemMoto #49 build (`pr55-16c46fa`, PR #55) | Reconnect after Disconnect → Connect | P1 ↔ P4 | — | ❌ fixed | P1's Android formed the group, but P4's app never saw it (most likely P4 reported the group without naming the other phone); connected only after restarting both apps. Fixed in `3b4d36a` |
| 2026-09-26 | TandemMoto #48 build (`pr54-7d42e39`, PR #54) | My songs: add, folder, reorder, missing file | P1 | — | ✅ | Add songs and Add a folder (20+ songs) with titles and durations; picking a song again says "Already in the playlist"; song count, length and numbers shown; reorder by drag (auto-scrolls near the edges), menu Move up/down/to top/to bottom, view stays put; survives a restart; a deleted original shows "File missing". The 128-permission limit on P4 (Android 9) wasn't exercised: no songs on P4 |
| 2026-09-26 | TandemMoto #26 build (`pr47-2493c01`, PR #47) | Status bar states + TalkBack | P1 ↔ P4 | — | ✅ | Give-up after 2 min shows "Couldn't reach … · Tap to try again" and a tap reconnects; Disconnect shows "Not connected · Tap to connect". TalkBack (P1): each change read once, the bar reads the status then "double-tap to …" |
| 2026-09-26 | TandemMoto #27 build (`pr45-9ccf8a4`, PR #45) | T3 Airplane mode / Wi‑Fi off-on, auto-reconnect | P1 ↔ P4 | — | ✅ | No taps on either phone. Connected again 5.5–7.5 s after Wi‑Fi returned (target 5–20 s), also with both screens locked. P4's silent-partner detection removed the dead group at once |
| 2026-09-26 | TandemMoto #27 build (`pr45-9ccf8a4`, PR #45) | T4 Out of range and back | P1 ↔ P4 | — | ✅ | Reconnecting… while away (33 s incl. walking back), then Connected with no taps |
| 2026-09-26 | TandemMoto #27 build (`pr45-9ccf8a4`, PR #45) | Disconnect, reconnect, give-up | P1 ↔ P4 | — | ✅ | Disconnect on P1 → P4 shows "… disconnected" and listens; one Connect tap on P1 reconnected both in 7 s. Partner's Wi‑Fi off > 2 min → "Not connected · Tap to connect"; one tap on P1 then reconnected |
| 2026-09-25 | TandemMoto #40 build (`pr44-abab021`, PR #44) | Notification buttons, reconnect after Disconnect / Wi‑Fi off | P1 ↔ P4 | — | ⚠️ expected | Buttons follow the state (Disconnect / Connect + Close / Turn on Wi‑Fi + Close). But reconnecting needs a tap on **both** phones: only the initiator (P1) connects, and a phone only receives an invitation while searching. After Disconnect or Wi‑Fi off/on, neither is searching until tapped. Fixed by #27 |
| 2026-09-25 | TandemMoto #40 build (`pr44-8b41d11`, PR #44) | Screen off with the link service (T10 precursor) | P1 ↔ P4 | — | ✅ | Both screens locked ≈ 9.5 min: no drop on either phone. Disconnect from the notification reached the partner ("… disconnected"). Swiping the app away from recents kept the link on both. P4 (Android 9) has no notification prompt, as expected |
| 2026-09-25 | TandemMoto #40 build (`pr44-8b41d11`, PR #44) | Heartbeat latency, 10 pings/s | P1 ↔ P4 | — | ✅ screens on, ⚠️ locked | Screens on: median 6–10 ms, p95 mostly 11–15 ms. Locked: median 9–66 ms, p95 mostly 45–75 ms, 7 of ~57 ten-second windows over 100 ms (max 188). Two pauses with screens on (6 s and 4 s), cause unknown |
| 2026-09-25 | TandemMoto #25 build (`pr43-e5afd0f`, PR #43) | Command channel: Hello, status, Wi‑Fi off | P1 ↔ P4 | — | ✅ | Both show Connected only once the other app answers. Closing the app on one phone → the other shows "Open TandemMoto on …", and reopening reconnects with no tap. Wi‑Fi off on one phone → that phone shows "Wi‑Fi is off", the other shows Not connected (earlier builds wrongly said "Open TandemMoto" or "Looking for …"). Forget + re-pair learns the install IDs. Latency at 10 pings/s not measured yet |
| 2026-09-25 | TandemMoto #25 build (`pr43-272ad5c`, PR #43) | Heartbeat latency, 5 pings/s, screens on | P1 ↔ P4 | — | ⚠️ | Median 11–36 ms (target < 50 ✅). p95 55–99 ms in 13 of 15 ten-second windows, 148 and 165 ms in the other two (target < ~100). Heartbeat raised to 10/s |
| 2026-09-25 | TandemMoto #24 build (`pr42-9bed100`, PR #42) | T2 Pair + remembered partner | P1 ↔ P4 | — | ✅ | P4 tapped P1: paired in ≈ 1.7 s. Forget partner on both, then P1 tapped: paired, and P1 made the reconnects after restart. Status shows the partner's phone name. Invite with the partner's Wi‑Fi off: "didn't accept" message after ≈ 45 s, can tap again. Re-pairing two phones that paired before shows **no accept prompt** (Android remembers the group); the decline path was not testable for that reason |
| 2026-09-25 | TandemMoto #24 build (`pr42-9434f78`, PR #42) | T3 Wi‑Fi off/on and airplane mode (baseline) | P1 ↔ P4 | — | ❌ expected | Drop seen on both. Nothing reconnected until the app was reopened; then the startup attempt reconnected in ≈ 3–17 s with no prompt. Android doesn't reconnect by itself; automatic reconnect is #27 |
| 2026-09-25 | TandemMoto #21 build (PR #41) | T1 Discovery | P1 ↔ P4 | — | ✅ | Each phone listed the other on the Pair screen. P4 searches immediately with Wi‑Fi on (false "Wi‑Fi off" fixed) and discovers with Location off. 60 s search window |
| 2026-09-25 | Wi‑Fi Direct lab v2 (`pr37-136f1d6`, #22) | Latency L1–L4 | P1 ↔ P4 | — | ✅ with steady traffic | 1 packet/s: p95 up to 1.4 s. 20 packets/s: median ≈ 10 ms, p95 ≈ 40–75 ms. A low-latency Wi‑Fi lock helps ~20–30% more |
| 2026-09-25 | Wi‑Fi Direct lab v2 (`pr37-136f1d6`, #22) | T4 Range walk-out | P1 ↔ P4 | — | ✅ | P1 carried ≈ 18 m (60 ft), screen on; P4 screen off. Stayed connected; P1 latency ≈ 60 ms median, p95 ≈ 0.35 s at 1 packet/s |
| 2026-09-25 | Wi‑Fi Direct lab v2 (`pr37-136f1d6`, #22) | Screen off with foreground service | P1 ↔ P4 | — | ✅ | P1 locked with the foreground service: no drop for ≈ 6 min. P4 still throttled by MIUI (3–4 s gaps) but connected |
| 2026-09-25 | Wi‑Fi Direct lab (`pr37-78169fe`, #22) | T1 Discovery | P1 ↔ P4 | — | ⚠️ | Partner seen in 1.1–13.5 s (median ≈ 7 s); 4 of 14 runs over the ~10 s target. See [`spikes/wifi-direct.md`](spikes/wifi-direct.md) |
| 2026-09-25 | Wi‑Fi Direct lab (`pr37-78169fe`, #22) | T2 Pair (group formation) | P1 ↔ P4 | — | ✅ | First pairing ≈ 15 s with one prompt on the partner; reconnections 1.4–7 s, no prompt. P1 was group owner every time (persistent group) |
| 2026-09-25 | Wi‑Fi Direct lab (`pr37-78169fe`, #22) | T3 Airplane-mode drop (baseline) | P1 ↔ P4 | — | ❌ expected | Drop detected in < 1 s on both, but nothing reconnects automatically (no reconnect logic yet; #27). Manual recovery 5–20 s |
| 2026-09-25 | Wi‑Fi Direct lab (`pr37-78169fe`, #22) | T4 Range walk-out | P1 ↔ P4 | — | ➖ inconclusive | ~12 m: survived an 11.7 s silence; final drop was most likely P1's screen turning off, not range |
| 2026-09-25 | Wi‑Fi Direct lab (`pr37-78169fe`, #22) | Screen off (T10 precursor) | P1 ↔ P4 | — | ❌ | P4 screen off: stays connected (throttled, 3–4 s gaps). P1 screen off: socket killed within ~1 s. Needs a foreground service |
| 2026-09-25 | — (Android's own Wi‑Fi Direct screen, not TandemMoto) | Baseline discovery | P1 ↔ P4 | — | ✅ | Each phone listed the other in 2–3 s. Confirms both phones' Wi‑Fi Direct works before the #22 spike |

---

## OEM and device quirks

Record anything a tester had to change or work around, with the device ID.

- **Battery optimisation:** before T10, set TandemMoto to *Unrestricted* / *Don't optimise* on every phone. Per-skin steps: [dontkillmyapp.com](https://dontkillmyapp.com).
  - P1 (One UI): Settings → Apps → TandemMoto → Battery → **Unrestricted**; make sure it isn't in *Sleeping apps*.
  - P3 (realme UI): also allow **Auto launch** and **background activity** for TandemMoto.
- **R1, bike as an audio device:** in everyday use the phone's audio doesn't go to the bike (owner-reported). Recheck during T7 and T9 with TandemMoto running, especially during calls and while the intercom holds the mic, and record which Bluetooth profiles the bike connects with.
- **Where Android's own Wi‑Fi Direct screen is** (useful as a baseline, with Wi‑Fi on):
  - P1 (One UI): Settings → Connections → Wi‑Fi → ⋮ (top right) → **Wi‑Fi Direct**.
  - P4 (MIUI): Settings → Wi‑Fi → Additional settings → **Other connection types** → **Wi‑Fi Direct**. It's easy to miss: it isn't directly under Additional settings.
  - On Android 9 and older, switch Location on too if discovery finds nothing.
- **P4 has no USB data connection** to the dev laptop (micro‑USB), so no `adb`. Install through Play (`/play-test` or the QA track) or by sideloading the CI debug APK, and use the in-app log export.
- **W1 is USB audio:** it shows up as a USB headset, not a wired analogue headset. Headset-disconnect detection (Phase 5) must handle `TYPE_USB_HEADSET`.
- **P1 kills the app's socket when its screen turns off** (`Software caused connection abort`) unless a foreground service is running (confirmed with lab v2); P4 (Android 9) keeps it, throttled, with or without one.
- **P4's Wi‑Fi Direct can get stuck returning `BUSY`** to Discover/Stop; switching Wi‑Fi off and on clears it.
- **P4 never reports its initial Wi‑Fi Direct state** when an app starts listening (P1 does), and **discovers with Location switched off** (Android 9). See [`spikes/wifi-direct.md`](spikes/wifi-direct.md).
- **Unrelated Wi‑Fi Direct devices nearby** (a TV, printer…) show up in discovery; always match the remembered partner.
- **Android auto-accepts invitations from a phone it paired with before** (P1 and P4): the accept prompt only appears the first time two phones pair. TandemMoto's *Forget partner* can't clear Android's remembered group (hidden system API), and neither phone lists it in its Wi‑Fi Direct screen; only *Reset network settings* would.
- **A Wi‑Fi Direct group outlives the app** (P1 and P4): after closing TandemMoto, Android keeps the phones connected, so "Connected" alone doesn't prove the partner app is running. The heartbeat (#25) fixes this.
- **P4 keeps a dead group for ~13 s** after P1's Wi‑Fi goes off (P1 drops it at once). The command channel's silence timeout (6 s) notices first, so the app decides the link is down, not the group.
- **Android's file picker browses by folder**; search (🔍) or the side menu's Audio section helps, and *Add a folder* adds many songs at once. An app keeps at most 512 picked-file permissions (128 on Android 10 and older, e.g. P4), which is why folders exist.
- **P4 can report a Wi‑Fi Direct group without naming the other phone** (seen once after Disconnect → Connect). The driver asks Android again, and after 3 s the link lets the Hello install-ID check decide.
- **P4 (Android 9) can't decode some FLAC files** (likely high-resolution) that P1 plays: the player skips them and marks them "Can't play on this phone".
- **Android 13+ turns a media-style notification into the media card** (P1), which shows only the song: the link needs its own notification next to it.
- **P1 discovery without Nearby devices permission** fails with a generic `ERROR` instead of a permission error.
