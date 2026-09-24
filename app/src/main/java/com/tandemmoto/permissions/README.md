# permissions

Runtime permissions as the user sees them (`AppPermission`), and a pure mapping from Android's
answers to `PermissionsState` (granted / denied / permanently denied). The UI lives in
`ui/setup/PermissionsScreen.kt`.

Today there's one permission, Nearby devices (location on Android 12 and older), and it's
required. Permissions are added in the PR that builds the feature using them, never ahead of it:
the microphone with the intercom (Phase 4, optional: music sharing works without it),
notifications with the foreground service, `READ_PHONE_STATE` with call hold (Phase 5). There's
no Bluetooth permission: the handlebar remote and earbuds go through `MediaSession` and
`AudioManager`.

Adding a permission: one `AppPermission` entry (with its API range, strings and whether it's
required), the manifest line, and a row in `docs/PRIVACY.md`. The screen and state need no change.

Development plan: Phase 0 (onboarding, #17).
