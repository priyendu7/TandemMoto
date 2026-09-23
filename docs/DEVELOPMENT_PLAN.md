# TandemMoto — Development Plan

**Based on:** [TandemMoto PRD v1.1](PRD.md) (Android, rider ↔ pillion 1:1 pairing)\
**Goal:** Ship the locked MVP feature set end‑to‑end, validated on real bikes with real Bluetooth peripherals.

---

## 1. Architecture overview

```
┌─────────────────── Rider Phone ───────────────────┐        ┌────────────────── Pillion Phone ──────────────────┐
│  Bike HID remote (BT) → Media control receiver     │        │  In-app UI controls                                │
│  Wired earphones (audio out)                       │        │  BT wireless earbuds (A2DP audio out, HFP mic)     │
│                                                     │        │                                                     │
│  ┌───────────────────────────────────────────┐    │        │    ┌───────────────────────────────────────────┐  │
│  │ Local Music Player (owns MediaSession)     │◄───┼──WiFi──┼───►│ Local Music Player (owns MediaSession)     │  │
│  │ Playlist/File Sync Manager                 │    │ Direct │    │ Playlist/File Sync Manager                 │  │
│  │ Command Mirror (play/pause/skip/seek)      │    │  Link  │    │ Command Mirror                             │  │
│  │ Mic-Mode State Machine                     │    │        │    │ Mic-Mode State Machine                     │  │
│  │ Call Interrupt Handler                     │    │        │    │ Call Interrupt Handler                     │  │
│  │ Voice Channel (codec + jitter buffer)      │    │        │    │ Voice Channel                              │  │
│  │ Noise Suppression + AGC (mic-mode only)    │    │        │    │ Noise Suppression + AGC                    │  │
│  │ Connection Manager (auto-reconnect)        │    │        │    │ Connection Manager                         │  │
│  │ Foreground Service                         │    │        │    │ Foreground Service                         │  │
│  └───────────────────────────────────────────┘    │        │    └───────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────┘        └─────────────────────────────────────────────────┘
```

**Key design decision:** both phones run the same app/module set — there's no client/server split for the core link. Whoever initiates pairing becomes the Wi‑Fi Direct "group owner," but playback/mic state is treated as shared, bidirectionally-synced state, not owner-controlled.

**Call hold:** each phone's Call Interrupt Handler detects phone calls locally and publishes a "call hold" flag over the command channel. The shared state machine treats call hold as an override that takes priority over playback and mic mode: while either phone reports a call, both phones are paused with mic mode off, and play/resume is rejected.

---

## 2. Suggested tech stack

| Layer | Choice | Why |
|---|---|---|
| Language/UI | Kotlin + Jetpack Compose | Standard modern Android stack |
| Phone-to-phone transport | `WifiP2pManager` (Wi‑Fi Direct) | Required by PRD; no router/internet dependency |
| Media playback | Media3 / ExoPlayer + `MediaSessionService` | Native handling of BT HID play/pause/skip events |
| File transfer | Socket over the Wi‑Fi Direct group (TCP), chunked with resume | For 20–30MB song caching |
| Voice codec | Opus (via a small native lib, e.g. libopus) at a low-latency profile | Good quality at low bitrate/latency |
| Noise suppression / AGC | WebRTC's `AudioProcessing` module (NS + AGC) or Android's built-in `NoiseSuppressor`/`AutomaticGainControl` audio effects where device support allows | Avoids building DSP from scratch |
| Call detection | `AudioManager` audio-focus loss + `TelephonyCallback` call-state listener (`READ_PHONE_STATE`), with `AudioManager` mode changes (`MODE_IN_CALL` / `MODE_IN_COMMUNICATION`) as a fallback for VoIP apps | Covers cellular and VoIP calls; must tell other apps' calls apart from the app's own voice channel |
| Background execution | Foreground `Service` with a persistent notification | Required to keep link/mic alive, screen off |
| State sync | A small custom protocol (protobuf or simple JSON) over a persistent socket, separate from the audio/file sockets | Keeps command/state messages low-latency and independent of file transfer load |
| Local storage | App-private storage for cached song files + a lightweight local DB (Room) for playlist metadata | |

---

## 3. Development phases

### Phase 0 — Foundations (setup)

- Repo, CI, code style, crash reporting (offline-friendly, e.g. local logs exportable for debugging since there's no cloud dependency)
- Basic Compose app shell, permissions flow (location — required for Wi‑Fi Direct discovery, mic, media, notifications, phone state — for call detection)
- Device/peripheral test matrix defined (2–3 bike BT remotes, 2–3 popular earbud models, 2–3 phone OEMs)

### Phase 1 — Phone-to-phone link

- Wi‑Fi Direct discovery, pairing UI, group formation
- Persistent socket for command/state channel
- Auto-reconnect logic + connection status UI + "peer disconnected" error state
- **Exit criteria:** two phones can pair, see each other's connection state, and survive a simulated link drop (airplane-mode toggle test) without user intervention

### Phase 2 — Shared local music player

- Local music library (device storage scan or user-added files)
- ExoPlayer + MediaSessionService integration
- Playlist sync: shared playlist state pushed over the command channel
- File transfer: chunked, resumable transfer of song files pre-ride, with progress UI
- **Exit criteria:** selecting a song on one phone results in it being cached and playable on the other before playback starts

### Phase 3 — Playback command mirroring + rider hardware input

- Bike HID remote → intercepted as standard Android media button events → routed into MediaSession
- Pillion in-app controls → same MediaSession actions
- Mirror play/pause/skip/seek across the command channel so both phones' players stay in the same state
- **Exit criteria:** either phone's control (remote or on-screen) changes playback state on both phones within a fraction of a second

### Phase 4 — Mic mode + voice channel

- Mic-mode state machine: pause → both phones enter mic mode; resume → both exit, synced via the command channel (not independently triggered, to avoid a race where one side is "on" and the other "off")
- Design the state machine with a **call-hold** state from the start (Playing / Paused + mic mode / Call hold). Call hold overrides both other states, and clears into Paused + mic mode. The call-detection wiring is done in Phase 5
- Opus encode/decode voice channel over a dedicated low-latency socket, separate from file transfer
- Noise suppression + AGC applied only while mic mode is active
- **Exit criteria:** pausing music on either phone opens a clear two-way voice channel on both within the ~300ms latency target (bench-tested with wired mic/earphone loopback before road testing)

### Phase 5 — Robustness & edge cases

- Peer disconnect mid-call: mics/audio must not get stuck open — force mic mode off and surface a clear error
- Headset disconnect (rider unplugs earphones, pillion's earbuds drop BT) → pause playback and/or mic mode gracefully, with a UI state for it
- Mic permission missing/revoked → blocking, clear error, no silent failure
- Phone-call interruption (call hold):
  - Call Interrupt Handler detects ringing / off-hook / idle for cellular and VoIP calls, and ignores the app's own voice channel
  - On a call starting on either phone: pause playback and tear down the voice channel on both phones, release the mic and SCO/HFP so the call gets the headset, block play/resume, and show "Rider/Pillion is on a call" on the peer
  - On call end (hung up, declined, missed): clear call hold on both phones → Paused + mic mode; no music auto-resume
  - Edge cases: both phones on calls at once (clear only when both have ended), link down during the call (local pause, re-sync call-hold state on reconnect), call starts during a file transfer (transfer continues), phone-state permission denied (fall back to audio-focus detection and warn in setup)
  - **Exit criteria:** an incoming call on either phone pauses music and the intercom on both within ~1s, the call is clearly audible on wired earphones and on BT earbuds, and after the call both phones land in Paused + mic mode with no stuck mic or audio route
- Foreground service battery/behavior testing over a simulated full ride duration

### Phase 6 — Field validation

- Real-world testing: two phones, actual motorcycle, actual bike HID remote and BT earbuds, at riding speed and typical urban traffic separation distances
- Validate the OEM Wi‑Fi Direct/Bluetooth variation matrix from Phase 0
- Tune noise suppression against real wind/engine noise recordings, not just bench audio
- Real incoming/outgoing calls (cellular and at least one VoIP app) during a ride, on both the rider's (wired) and the pillion's (BT earbuds) phone

### Phase 7 — Polish & release readiness

- Setup/pairing UX polish, first-run flow
- Error copy pass, connection status iconography
- Battery draw measurement against the NFR target
- Internal beta → fix pass → release build

---

## 4. Suggested build order rationale

Phases 1–2 (link + player) are prerequisites for everything else and carry the most platform-variability risk (Wi‑Fi Direct across OEMs), so they're front-loaded. Phase 4 (voice) is technically the riskiest for the core promise of the app (sub-300ms, clear over wind noise) and benefits from having a stable link (Phase 1) and stable playback (Phase 3) already in place to build/test against. Phase 6 (field validation) is deliberately separated from unit/bench testing because Wi‑Fi Direct range/interference and wind-noise behavior can't be fully validated off-bike.

---

## 5. Key risks

| Risk | Mitigation |
|---|---|
| Wi‑Fi Direct behaves inconsistently across OEMs | Early spike in Phase 1 against the target device matrix before building on top of it |
| Voice latency exceeds 300ms once NS/AGC is added | Bench-test the codec + NS/AGC pipeline in isolation (Phase 4) before integrating; keep NS/AGC toggleable for A/B latency testing |
| Background/foreground service killed by OEM battery optimizers | Test against known aggressive OEMs (e.g. some Chinese Android skins) early; document required user-facing battery-optimization exemption steps |
| BT HID remote key mapping varies by remote model | Build an abstraction layer over media button events rather than hardcoding key codes; validate against the test matrix |
| Call detection is unreliable (VoIP apps, OEM telephony differences, permission denied) or confuses the app's own voice channel with a call | Combine the call-state listener with audio-focus loss; tag the app's own audio session so it's ignored; test on the Phase 0 OEM matrix with cellular + popular VoIP apps |
| Pillion's BT earbuds get stuck on the app's SCO/HFP route when a call arrives, so the call audio is lost | Release SCO and the mic immediately on call hold, before the call is answered; test on the earbud matrix |
| Mic-mode race conditions (one phone thinks it's on, other thinks off) | Treat mic-mode as a single synced state machine driven by the command channel, not two independently-triggered local states |

---

## 6. Out of scope reminder

Per the [PRD](PRD.md), nothing beyond the locked MVP feature list should be built without updating the PRD first — notably: multi-rider support, VAD/hands-free talk mode, live streaming, iOS, streaming-service integration, and ride history/social features are all explicitly deferred.
