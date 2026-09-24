# ui

Jetpack Compose UI. The app opens on **Home** (the Ride screen) straight after the system
splash. Home has five sections: app bar, connection bar, now playing, playback controls and
intercom. Pairing, playlist and settings open from it.

## Structure

| Package | Contents |
|---|---|
| `theme/` | `TandemMotoTheme`: Material You dynamic colour on Android 12+, fallback palette below; system font and Material's default text sizes |
| `navigation/` | `AppNavHost` and `Routes`: starts on Ride (Home); Pair opens from the connection bar, Playlist and Settings from the app bar |
| `setup/` | Pair screen (placeholder until Phase 1) |
| `ride/` | `RideScreen` (Home), `RideViewModel`, `RideUiState` |
| `components/` | `ConnectionStatusBar` + `ConnectionStatus`, `PermissionPrompt` + `rememberPermissionRequester`, `ControlButton`, `BackTopBar` |
| `playlist/`, `settings/` | Playlist (placeholder until Phase 2) and Settings (version, privacy policy, source, log export) |

## Decisions

- **Home first, no onboarding screens.** The system splash (`core-splashscreen`) shows the icon and
  closes by itself; there's no Welcome or Get started step.
- **Permissions in place.** Each Home section asks for the permission it needs, inside that
  section, via `PermissionPrompt`: the connection bar asks for Nearby devices (location on
  Android 12 and older); the music section (Phase 2) and intercom (Phase 4) add theirs. Everything
  else keeps working without it.
- **No rider/pillion roles.** Both phones run the identical app; messages refer to "your partner".
- **One main screen.** Home holds everything needed mid-ride.
- **Material You dynamic colour** and the **system font and text size**, so the app follows the
  user's light/dark, font and font-size settings.
- **Glove-friendly controls:** playback buttons are 72–96dp.
- **Stateless screens:** composables take state + callbacks, so previews and Robolectric tests
  (`app/src/test/.../ui/`) render any state without a device. View models own state; Phase 1+
  replaces `RideViewModel`'s fixed initial state with the real link and player.

Development plan: Phase 0 (shell), Phase 1 (pairing + status), Phase 7 (polish).
