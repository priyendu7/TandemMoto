# permissions

Runtime permissions as the user sees them (`AppPermission`), and a pure mapping from Android's
answers to `PermissionsState` (granted / denied / permanently denied).

There's no permissions screen. Each Home section asks for its own permission in place
(`ui/components/PermissionPrompt.kt`), so a missing permission only disables its own feature.
Nearby devices (location on Android 12 and older) is asked for in the connection bar.
Notifications (Android 13+, optional, #40) are the exception to "in place": there's no Home
section for them, so they're offered once, the first time the link connects (`AskedOnce`), and
from Settings. Permissions are added in the PR that builds the feature using them, never ahead of
it: the microphone with the intercom (Phase 4, optional: music sharing works without it),
`READ_PHONE_STATE` with call hold (Phase 5). There's
no Bluetooth permission: the handlebar remote and earbuds go through `MediaSession` and
`AudioManager`.

Adding a permission: one `AppPermission` entry (with its API range and strings), the manifest
line, a row in `docs/PRIVACY.md`, and a `PermissionPrompt` in the Home section it gates.

Development plan: Phase 0 (#17), reworked to in-place prompts before Phase 1.
