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
| T5 | Song transfer | 2 | Send a 25 MB song; repeat with a mid-transfer link drop | Completes; resumes after the drop; record the time taken |
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
- **P1 kills the app's socket when its screen turns off** (`Software caused connection abort`) unless the app is kept in the foreground; P4 (Android 9) keeps it, throttled. See [`spikes/wifi-direct.md`](spikes/wifi-direct.md).
- **Unrelated Wi‑Fi Direct devices nearby** (a TV, printer…) show up in discovery; always match the remembered partner.
- **P1 discovery without Nearby devices permission** fails with a generic `ERROR` instead of a permission error.
