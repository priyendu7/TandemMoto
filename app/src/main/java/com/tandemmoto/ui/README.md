# ui

Jetpack Compose UI: setup flow, the Ride screen (now playing, playback controls, intercom
indicator), playlist, settings, and the connection status bar with the PRD's error states
(partner disconnected, headset disconnected, mic permission missing, partner on a call).

## Structure

| Package | Contents |
|---|---|
| `theme/` | `TandemMotoTheme`: Material You dynamic colour on Android 12+, fallback palette below; larger type scale |
| `navigation/` | `AppNavHost` and `Routes`: Welcome → Permissions → Pair → Ride, with Playlist and Settings opened from Ride |
| `setup/` | Welcome, Permissions (only Nearby devices is required; see `permissions/`) and Pair screens (Pair is a placeholder until Phase 1) |
| `ride/` | `RideScreen`, `RideViewModel`, `RideUiState` |
| `components/` | `ConnectionStatusBar` + `ConnectionStatus`, `ControlButton`, `BackTopBar` |
| `playlist/`, `settings/` | Playlist (placeholder until Phase 2) and Settings (version, privacy policy, source) |

## Decisions

- **No rider/pillion roles.** Both phones run the identical app; messages refer to "your partner".
- **One main screen.** Ride holds everything needed mid-ride; Playlist and Settings open from its top bar.
- **Material You dynamic colour**, following the system light/dark setting.
- **Glove-friendly controls:** playback buttons are 72–96dp; typography is one step larger than Material defaults.
- **Stateless screens:** composables take state + callbacks, so previews and Robolectric tests
  (`app/src/test/.../ui/`) render any state without a device. View models own state; Phase 1+
  replaces `RideViewModel`'s fixed initial state with the real link and player.

Development plan: Phase 0 (shell), Phase 1 (pairing + status), Phase 7 (polish).
