# TandemMoto

[![CI](https://github.com/priyendu7/TandemMoto/actions/workflows/ci.yml/badge.svg)](https://github.com/priyendu7/TandemMoto/actions/workflows/ci.yml)
[![Release](https://img.shields.io/github/v/release/priyendu7/TandemMoto?include_prereleases)](https://github.com/priyendu7/TandemMoto/releases)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

Phone-to-phone shared music + push-to-talk intercom for motorcycle rider/pillion pairs — no dedicated Bluetooth intercom hardware required.

Two Android phones connect directly over **Wi-Fi Direct** (no internet, no router). They share one synced playlist and music library, mirror playback control between the rider (handlebar Bluetooth HID remote) and pillion (in-app controls), and automatically flip into a noise-suppressed push-to-talk intercom whenever either side pauses the music.

> **Status:** Pre-MVP / early development. See [`docs/PRD.md`](docs/PRD.md) and [`docs/DEVELOPMENT_PLAN.md`](docs/DEVELOPMENT_PLAN.md) for the full product and engineering plan.

## Why

Riders and pillions each run their own earphones off their own phone today — different music, no way to talk over helmet/wind/engine noise, short of buying a dedicated intercom headset. TandemMoto solves both problems in software, using only the two phones people already have.

## MVP scope (locked)

1. Wi-Fi Direct phone-to-phone link with auto-reconnect
2. Bike HID remote play/pause/skip (rider side)
3. In-app play/pause/skip controls (pillion side)
4. Built-in local music player, owns the Android `MediaSession`
5. Playlist sync + one-time song file transfer/caching between phones
6. Playback command mirroring (play/pause/skip/seek) over the data link
7. Pause-triggered mic mode, synced both ways
8. Push-to-talk voice channel (Opus, low latency)
9. On-device noise suppression + AGC during mic mode only
10. Independent per-device volume (native OS)
11. Setup/pairing flow + connection status UI
12. Basic error/edge-case handling (disconnects, permission issues)
13. Phone-call interruption ("call hold") on either phone pauses music and the intercom on both
14. Self-mute: mute your own voice while still hearing your partner (manual only)

Anything not on this list — multi-rider/mesh, voice-activated hands-free talk, live streaming, iOS, streaming-service integration, ride history/social — is explicitly **out of scope** until the PRD is updated. See `docs/PRD.md` §3 and §5.

## Tech stack

| Layer | Choice |
|---|---|
| Language/UI | Kotlin + Jetpack Compose |
| Phone-to-phone transport | `WifiP2pManager` (Wi-Fi Direct) |
| Media playback | Media3 / ExoPlayer + `MediaSessionService` |
| File transfer | TCP socket over the Wi-Fi Direct group, chunked with resume |
| Voice codec | Opus (native, low-latency profile) |
| Noise suppression / AGC | WebRTC `AudioProcessing`, or platform `NoiseSuppressor`/`AutomaticGainControl` where supported |
| Background execution | Foreground `Service` |
| State sync | Small custom protocol (protobuf/JSON) over a dedicated persistent socket |
| Local storage | App-private file storage + Room for playlist metadata |

Full rationale in `docs/DEVELOPMENT_PLAN.md`.

## Project layout

```
app/src/main/java/com/tandemmoto/
├── link/          Wi-Fi Direct discovery, pairing, connection manager, auto-reconnect
├── player/        ExoPlayer + MediaSessionService, HID remote input handling
├── filetransfer/  Chunked/resumable song file sync
├── voice/         Opus encode/decode, jitter buffer, voice socket
├── micmode/       Mic-mode state machine (pause ↔ intercom)
├── service/       Foreground service, notification, lifecycle
├── state/         Shared command/state protocol + sync
└── ui/            Compose screens (setup/pairing, now playing, status)
```

This mirrors the module boundaries in the development plan's Phase 1–4 breakdown.

## Getting started

Requires JDK 17+ and the Android SDK (API 35). The app is currently a buildable shell — feature modules are still stubs.

```bash
git clone https://github.com/priyendu7/TandemMoto.git
cd TandemMoto
./gradlew assembleDebug
```

Run the same checks CI runs on every PR:

```bash
./gradlew ktlintCheck lintDebug testDebugUnitTest assembleDebug
```

`./gradlew ktlintFormat` auto-fixes most style issues. Builds are distributed through Google Play testing tracks (internal → closed → open → production); signed APKs are also attached to each [GitHub release](https://github.com/priyendu7/TandemMoto/releases). See [`docs/RELEASING.md`](docs/RELEASING.md). Pull requests can be tested from the Play Store before merge via the separate TandemMoto QA app — see [`docs/TESTING_ON_PLAY.md`](docs/TESTING_ON_PLAY.md).

Requires two physical Android devices for any real link/voice testing — Wi-Fi Direct and BT HID/A2DP/HFP behavior can't be meaningfully validated on an emulator.

## Contributing

See [`CONTRIBUTING.md`](CONTRIBUTING.md) for branch/commit conventions, the build-order rationale, and how phases map to issues. Please read [`CODE_OF_CONDUCT.md`](CODE_OF_CONDUCT.md) as well.

## License

MIT — see [`LICENSE`](LICENSE). We chose a permissive license so the Wi-Fi Direct/Opus/NS-AGC integration work here is easy for others building similar rider-comms tooling to reuse; open an issue if there's a reason to reconsider this before the first tagged release.
