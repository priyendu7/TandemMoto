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
| Background execution | Foreground `Service` (`connectedDevice`, plus `microphone` from Phase 4) with a persistent notification, from Phase 1 | Required to keep link/mic alive, screen off: the spike showed the S25 killing the socket on screen-off without it |
| State sync | A small custom protocol (protobuf or simple JSON) over a persistent socket, separate from the audio/file sockets | Keeps command/state messages low-latency and independent of file transfer load |
| Local storage | App-private storage for cached song files + a lightweight local DB (Room) for playlist metadata | |

---

## 3. Development phases

### Phase 0 — Foundations (setup)

- Repo, CI, code style, crash reporting (offline-friendly, e.g. local logs exportable for debugging since there's no cloud dependency)
- Basic Compose app shell, permissions flow (location — required for Wi‑Fi Direct discovery, mic, media, notifications, phone state — for call detection)
- Device/peripheral test matrix defined (2–3 bike BT remotes, 2–3 popular earbud models, 2–3 phone OEMs)

### Phase 1 — Phone-to-phone link

Shaped by the Wi‑Fi Direct spike (#22), measured on Samsung ↔ Xiaomi, Android 16 ↔ 9: see [`spikes/wifi-direct.md`](spikes/wifi-direct.md).

- Wi‑Fi Direct discovery, pairing UI, group formation
  - Discover until the **remembered partner** appears (1–14 s measured; unrelated Wi‑Fi Direct devices show up too)
  - **One-initiator rule:** exactly one phone calls `connect()`, decided by a fixed rule, and never while a group exists or is forming (two phones connecting at once deadlocks)
  - Both phones handle either role: the first pairing fixes the group owner, and `groupOwnerIntent` is ignored after that
- Persistent socket for command/state channel
  - Needs the `INTERNET` permission (Android refuses any socket without it), with a `PRIVACY.md` note
  - **The heartbeat doubles as a keep-awake:** small packets at roughly 5–10/s plus a low-latency Wi‑Fi lock while connected. With sparse traffic the radio dozes (p95 up to 1.4 s); with steady traffic it's ~10 ms
- **Foreground service** (`connectedDevice`) keeping the link alive with the screen off, moved here from Phase 5: without it the S25 kills the socket as soon as its screen turns off. Ships with its Play Console declaration and demo video
- Auto-reconnect logic + connection status UI + "peer disconnected" error state
  - Drops are reported by Android in under 1 s, but Android never reconnects on its own: rediscover, reconnect, reopen the socket (5–20 s budget)
- **Exit criteria:** two phones can pair, see each other's connection state, survive a simulated link drop (airplane-mode toggle test) without user intervention, and **stay connected for 10 minutes with both screens locked**

### Phase 2 — Shared local music player

Decided in planning (issues #48–#52):

- **My songs** (#48): added with Android's **file picker** only (no storage permission, no library scan). Songs are **remembered in place, not copied**. A content fingerprint (SHA‑256) is the song ID; the same file, or the same title + artist + duration (±2 s), counts as one song
- **Ride playlist** (#49): one shared, ordered list; either phone edits; saved across restarts (a small JSON file, not Room); synced over the command channel with versioned state; combined on the first connection with a partner; a new partner keeps your songs and drops the previous partner's
- **Song transfer and the song window** (#50): chunked, resumable, verified transfer on its own socket. Each phone keeps only a **window of the partner's songs** (default 2 behind · current · 7 ahead, editable per phone), shrinking to what fits above 500 MB free. Nothing fits → download on demand when the song's turn comes (≥ 100 MB kept free)
- **Local player** (#51): Media3 ExoPlayer + `MediaSessionService`; "Getting song…" for a song not on the phone yet, skipping only as the last resort. The link service (#40) merges into it: **one** media-style notification with the link status
- **Exit criteria** (#52): selecting a song on one phone results in it being cached and playable on the other before playback starts

### Phase 3 — Playback command mirroring

Decided in planning (issues #60–#62):

- **Mirroring** (#60): one **shared playback state** (song, playing/paused, position, time, Lamport stamp), not forwarded button presses; the newest control wins, as in the ride playlist. Every control goes through it: Home (with a seek bar), Playlist, the notification, the lock screen and earbuds. The heartbeat's `Pong` carries the receiver's time for the clock offset. Link down: each phone plays on its own; on reconnect the newest control wins
- **Start together** (#61): a new song starts only when **both** phones have it ("Getting song on …"), at a start time planned ~0.3 s ahead; the phones correct drift over ~0.5 s. A song one phone can't play is skipped on both and marked "Can't play on …"
- **Exit criteria** (#62): either phone's control changes playback on both within 0.3 s (p95), and the phones are within 0.2 s after a start

**Handlebar remote spike** (after Phase 3): the bike's own switches (Harley X440 T) reach the rider's phone as Bluetooth input through the bike's TFT. The spike covers what key codes arrive, mapping them to play/pause/next/previous, a "Test your buttons" screen, and where audio goes while the bike is connected. Build an abstraction over media button events rather than hard-coding key codes

### Phase 4 — Mic mode + voice channel

- Mic-mode state machine: pause → both phones enter mic mode; resume → both exit, synced via the command channel (not independently triggered, to avoid a race where one side is "on" and the other "off")
- Design the state machine with a **call-hold** state from the start (Playing / Paused + mic mode / Call hold). Call hold overrides both other states, and clears into Paused + mic mode. The call-detection wiring is done in Phase 5
- Opus encode/decode voice channel over a dedicated low-latency socket, separate from file transfer
- Noise suppression + AGC applied only while mic mode is active
- **Self-mute** (PRD v1.2): a per-phone `muted` flag, separate from the shared mic-mode state. Mic mode decides whether the voice channel is open; `muted` only gates the local send path
  - Muted: release `AudioRecord` (no capture, no NS/AGC, mic indicator off) but keep receiving and playing the partner's voice
  - Publish `muted` over the command channel so the partner shows "Partner muted"; re-send it on reconnect
  - Persist it locally (DataStore). Nothing but the user's toggle changes it: not pause/resume, call hold, link drops or restarts
  - In-app toggle on the Ride screen, usable any time; the handlebar buttons stay play/pause/skip
  - Pillion on BT earbuds: while muted, check whether the partner's voice can play over A2DP instead of holding the HFP/SCO route (better audio quality); decide during Phase 4 testing
- **Exit criteria:** pausing music on either phone opens a clear two-way voice channel on both within the ~300ms latency target (bench-tested with wired mic/earphone loopback before road testing); muting one phone stops its voice and releases its mic within ~0.5s while it keeps hearing the partner, and the partner sees "Partner muted"

### Phase 5 — Robustness & edge cases

- Peer disconnect mid-call: mics/audio must not get stuck open — force mic mode off and surface a clear error
- Headset disconnect (rider unplugs earphones, pillion's earbuds drop BT) → pause playback and/or mic mode gracefully, with a UI state for it
- Mic permission missing/revoked → blocking, clear error, no silent failure
- Phone-call interruption (call hold):
  - Call Interrupt Handler detects ringing / off-hook / idle for cellular and VoIP calls, and ignores the app's own voice channel
  - On a call starting on either phone: pause playback and tear down the voice channel on both phones, release the mic and SCO/HFP so the call gets the headset, block play/resume, and show "Your partner is on a call" on the peer
  - On call end (hung up, declined, missed): clear call hold on both phones → Paused + mic mode; no music auto-resume
  - Edge cases: both phones on calls at once (clear only when both have ended), link down during the call (local pause, re-sync call-hold state on reconnect), call starts during a file transfer (transfer continues), phone-state permission denied (fall back to audio-focus detection and warn in setup)
  - **Exit criteria:** an incoming call on either phone pauses music and the intercom on both within ~1s, the call is clearly audible on wired earphones and on BT earbuds, and after the call both phones land in Paused + mic mode with no stuck mic or audio route
- Battery and behavior testing of the foreground service (built in Phase 1) over a simulated full ride duration, including the keep-awake heartbeat's cost

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
| Wi‑Fi Direct behaves inconsistently across OEMs | Spike done (#22, [`spikes/wifi-direct.md`](spikes/wifi-direct.md)) on Samsung ↔ Xiaomi: works, with quirks (persistent groups fix the group owner, `BUSY` needing a Wi‑Fi toggle). Validate each new phone against [`TEST_MATRIX.md`](TEST_MATRIX.md) |
| Sparse traffic lets Wi‑Fi doze, so commands arrive late (p95 up to 1.4 s measured) | Heartbeat doubles as a keep-awake (~5–10 packets/s) plus a low-latency Wi‑Fi lock while connected; measured ~10 ms with steady traffic |
| Both phones call `connect()` at once and deadlock (both stuck *invited*) | One-initiator rule: a fixed rule picks the phone that connects; the other only discovers and accepts |
| Voice latency exceeds 300ms once NS/AGC is added | The network isn't the bottleneck with steady traffic (~10 ms in the spike). Bench-test the codec + NS/AGC pipeline in isolation (Phase 4) before integrating; keep NS/AGC toggleable for A/B latency testing |
| Background/foreground service killed by OEM battery optimizers | Confirmed in the spike: without a foreground service the S25 kills the socket on screen-off; with one it stays connected, but MIUI still throttles the app (3–4 s gaps), so keep a ~20–30 s give-up timeout. Document the battery-optimization exemption steps per skin |
| BT HID remote key mapping varies by remote model | Build an abstraction layer over media button events rather than hardcoding key codes; validate against the test matrix |
| Call detection is unreliable (VoIP apps, OEM telephony differences, permission denied) or confuses the app's own voice channel with a call | Combine the call-state listener with audio-focus loss; tag the app's own audio session so it's ignored; test on the Phase 0 OEM matrix with cellular + popular VoIP apps |
| Pillion's BT earbuds get stuck on the app's SCO/HFP route when a call arrives, so the call audio is lost | Release SCO and the mic immediately on call hold, before the call is answered; test on the earbud matrix |
| Mic-mode race conditions (one phone thinks it's on, other thinks off) | Treat mic-mode as a single synced state machine driven by the command channel, not two independently-triggered local states |

---

## 6. Out of scope reminder

Per the [PRD](PRD.md), nothing beyond the locked MVP feature list should be built without updating the PRD first — notably: multi-rider support, VAD/hands-free talk mode, live streaming, iOS, streaming-service integration, and ride history/social features are all explicitly deferred.
