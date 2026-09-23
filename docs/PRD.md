# TandemMoto — Product Requirements Document

**Status:** Draft v1.1 (adds phone-call interruption handling)\
**Platform:** Android only (rider ↔ pillion, 1:1 pairing)

---

## 1. Problem statement

Riders and pillions on a motorcycle each wear their own earphones connected to their own phone. They listen to different music, and helmets plus wind/engine noise make it impossible to hear each other directly. There is currently no simple way for two riders to (a) share and control the same music together, each at their own volume, and (b) talk to each other like a live intercom, without buying dedicated Bluetooth intercom hardware.

TandemMoto solves this in software only, using two Android phones that talk to each other directly over Wi-Fi Direct — no intercom headset hardware, no internet connection required.

---

## 2. User stories

- **As the rider**, I want to control music playback (play/pause/skip) using my bike's handlebar Bluetooth remote, so I don't have to take my hands off the controls.
- **As the pillion**, I want to control music playback from my phone screen, so I can change songs even without a hardware remote.
- **As either rider or pillion**, when I pause the music, I want both of us to automatically be able to talk to each other clearly — like flipping to an intercom — without pressing any extra button.
- **As either rider or pillion**, when the music resumes, I want mic mode to switch off automatically so we're not left with open mics unintentionally.
- **As the rider**, I want wind and engine noise filtered out of my voice, so the pillion can actually understand me at riding speed.
- **As either rider or pillion**, I want to control my own earphone volume independently of the other person's.
- **As either rider or pillion**, I want the song I select to already be available on the other phone before it plays, so playback isn't interrupted by a weak Bluetooth link mid-ride.
- **As either rider or pillion**, when either of us receives (or makes) a phone call, I want the music and our phone-to-phone intercom to pause on both phones automatically, so the person on the call can hear it clearly and the other person isn't left with music playing or an open mic mid-conversation.
- **As either rider or pillion**, if our phone-to-phone connection briefly drops (traffic, distance, obstruction), I want it to reconnect automatically without either of us needing to intervene.
- **As a new user**, I want a simple one-time setup before a ride (pair phones, sync playlist) so there's nothing to configure once we're moving.

---

## 3. MVP scope vs. future scope

### MVP (build now)

- Android ↔ Android only, one rider + one pillion (1:1 pairing)
- Phone-to-phone link over Wi-Fi Direct (no internet/router needed, higher bandwidth + lower latency than Bluetooth Classic)
- Built-in local music player (own player, owns the media session) with a shared, synced playlist
- Song files cached locally on both phones **before** playback (one-time transfer, not live streaming) — Wi-Fi Direct's bandwidth also leaves room to revisit live streaming sooner if desired
- Play/pause/skip/seek from either phone mirrored to the other over the data link
- Rider: play/pause via bike's Bluetooth HID remote; audio via wired earphones
- Pillion: play/pause via in-app screen controls; audio via Bluetooth wireless earbuds (A2DP + HFP)
- Note: Bluetooth is still used locally on each phone for peripherals (rider's HID remote, pillion's earbuds) — only the phone-to-phone data link itself moves to Wi-Fi Direct
- Independent per-phone volume (native OS behavior, no custom logic needed)
- Push-to-talk via pause: pausing music (either side) switches both phones into mic mode; resuming playback switches mic mode off
- On-device wind/engine noise suppression + automatic gain control, active only during mic mode
- Phone-call interruption ("call hold"): when either phone has an incoming call (from the moment it starts ringing) or an active outgoing call — cellular or VoIP — music playback and the phone-to-phone voice channel pause on **both** phones
  - While call hold is active, mic mode stays off on both phones (even though music is paused), and play/resume is blocked on both phones so music can't restart under the call
  - The phone not on the call shows a clear status (e.g. "Your partner is on a call")
  - When the call ends (answered and hung up, declined, or missed), call hold clears on both phones and they return to the normal paused state — i.e. mic mode turns on per the push-to-talk-via-pause rule. Music does not auto-resume; either rider resumes it as usual
  - If both phones are on calls at the same time, call hold clears only once both calls have ended
- Auto-reconnect of the phone-to-phone link after a drop
- Basic setup/pairing flow, connection status indicator, and clear error states (peer disconnected, headset disconnected, mic permission missing, peer on a call)

### Future scope (explicitly out of MVP)

- Support for more than two riders (group/mesh connectivity)
- Hands-free, voice-activated (VAD) talk mode as an alternative to push-to-talk-via-pause
- Live audio streaming from host phone instead of pre-caching songs
- iOS support (would require a different, Wi-Fi-socket-based transport, since Android's phone-to-phone approach isn't portable to iOS)
- Integration with streaming services (Spotify, etc.) as the music source, instead of a local synced library
- Ride history, stats, or social features

---

## 4. Non-functional requirements

**Performance**

- Voice round-trip latency target: under ~300ms for a conversational feel
- Song transfer: Wi-Fi Direct's bandwidth should transfer a 20–30MB file well within a reasonable pre-ride window
- Music playback stays audibly in sync between phones during normal use (some drift is acceptable since each phone plays its own local copy independently)

**Reliability**

- Phone-to-phone link must auto-reconnect without user action after a temporary drop
- App must not crash or leave mics/audio in a stuck state if the peer disconnects mid-conversation
- Call hold must engage on both phones within ~1s of a call starting to ring, and must never leave either phone stuck in call hold after the call ends. If the phone-to-phone link is down when a call starts, the phone with the call still pauses its own music and voice channel locally, and syncs call-hold state with the peer on reconnect
- The app must release the microphone and audio routing (including the Bluetooth headset's HFP/SCO channel on the pillion's earbuds) during a call, so the phone call takes over the headset normally

**Offline support**

- Fully functional with no internet connection — music library, sync, and voice all run over the direct device-to-device Bluetooth link

**Battery**

- Foreground service (required to keep mic/link alive with screen off) should run for the duration of a normal ride without excessive drain; noise suppression and mic capture should only run while mic mode is active, not continuously

**Security/privacy**

- Phone-to-phone pairing (Wi-Fi Direct) should be scoped to a known, deliberately-paired peer device, not open to any nearby device
- No cloud dependency for MVP; no user audio or files leave the two paired devices
- Call detection only uses call **state** (ringing / in call / idle). Caller identity or number is never read, stored, or sent to the peer

**Platform**

- Android only, MVP targets a modern Android baseline (to be finalized with engineering, accounting for background execution and foreground service restrictions on newer Android versions)
- Must handle variation across device/OEM Wi-Fi Direct implementations for the phone-to-phone link, and across Bluetooth stacks for peripherals (rider's bike HID remote, pillion's wireless earbuds); validate against a small set of common bike Bluetooth remotes and popular wireless earbud models

---

## 5. Locked MVP feature list

1. Wi-Fi Direct phone-to-phone connectivity with auto-reconnect
2. Bike HID remote play/pause/skip detection (rider side)
3. In-app play/pause/skip controls (pillion side)
4. Built-in local music player owning the Android media session
5. Playlist sync + one-time song file transfer/caching between phones
6. Playback command mirroring (play/pause/skip/seek) over the data link
7. Pause-triggered mic mode (shared state, synced both ways)
8. Push-to-talk voice channel (compressed, low-latency codec)
9. On-device noise suppression + AGC during mic mode
10. Independent per-device volume (native)
11. Setup/pairing flow + connection status UI
12. Basic error/edge-case handling (disconnects, permission issues)
13. Phone-call interruption: an incoming/outgoing call on either phone pauses music and the voice channel on both phones (call hold), synced both ways

Anything not on this list is future scope and out of MVP by default — no scope additions without updating this document first.
