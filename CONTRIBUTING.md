# Contributing to TandemMoto

Thanks for taking a look. This project is early — the fastest way to help right now is picking up a Phase 0/1 issue below.

By contributing, you agree your contributions are licensed under the project's [MIT License](LICENSE).

## Ground rule: scope is locked

The [PRD](docs/PRD.md) §5 has a locked MVP feature list. **Anything not on that list is out of scope by default** — multi-rider support, VAD hands-free talk, live streaming, iOS, streaming-service integration, ride history/social features, etc. If you think one of those should move into MVP, open an issue proposing a PRD change first; don't send a PR that quietly expands scope.

## Before you start

1. Check open issues / the project board for what's already claimed.
2. For anything nontrivial, open an issue first (or comment on an existing one) describing your approach before writing code — this project has real hardware-variability risk (Wi-Fi Direct across OEMs, BT HID remotes, earbud models) and it's easy to duplicate exploration work.
3. New to the codebase? Start with `docs/DEVELOPMENT_PLAN.md` §3 for the phase breakdown and §5 for known risks/mitigations.

## Development phases

Issues are labeled by phase (`phase-0` … `phase-7`) matching the development plan. Roughly:

| Phase | Focus |
|---|---|
| 0 | Repo/CI foundations, permissions flow, device test matrix |
| 1 | Wi-Fi Direct link, pairing, auto-reconnect |
| 2 | Shared local music player, playlist sync, file transfer |
| 3 | Playback command mirroring, HID remote input |
| 4 | Mic mode + voice channel (Opus, NS/AGC) |
| 5 | Robustness / edge cases (disconnects, permissions, stuck-open mics) |
| 6 | Field validation on real bikes/hardware |
| 7 | Polish, release readiness |

Phases 1–2 are prerequisites for almost everything else, so PRs against those are prioritized for review.

## Branching & commits

- Branch from `main`: `phase-N/short-description` (e.g. `phase-1/wifi-direct-discovery`).
- Keep commits scoped and use a short imperative subject line (`Add auto-reconnect backoff to ConnectionManager`), with body context for anything non-obvious.
- Reference the issue number in the PR description (`Closes #12`).

## Code style

- Kotlin + Jetpack Compose, following standard [Kotlin coding conventions](https://kotlinlang.org/docs/coding-conventions.html).
- Run `./gradlew ktlintCheck lintDebug testDebugUnitTest` before opening a PR — CI runs the same checks and must be green to merge. `./gradlew ktlintFormat` fixes most style issues.
- Prefer small, single-purpose modules matching the existing package layout (`link`, `player`, `filetransfer`, `voice`, `micmode`, `service`, `state`, `ui`) rather than cross-cutting changes.

## Testing expectations

- Anything touching `link/`, `voice/`, or `micmode/` state transitions should include unit tests around the state machine, not just manual verification — see the "mic-mode race condition" risk in `docs/DEVELOPMENT_PLAN.md` §5.
- Wi-Fi Direct and audio-hardware behavior genuinely needs two physical devices; note in your PR description what you tested on (device models, OS versions, BT peripherals) since emulator testing doesn't cover this.
- Each phase's PRD "exit criteria" (development plan §3) is the bar for calling that phase's work done, not just "compiles."

## Pull requests

- Keep PRs scoped to one phase/module where possible.
- Describe what you tested and on what hardware.
- Update `docs/` if your change affects architecture, scope, or the risk table.

## Continuous integration

Every PR and push to `main` runs [`.github/workflows/ci.yml`](.github/workflows/ci.yml): Gradle wrapper validation, ktlint, Android lint, unit tests, and a debug build. The debug APK is attached to the run as an artifact for quick on-device testing.

## Releasing (maintainers)

Releases are cut by pushing a semver tag; [`.github/workflows/release.yml`](.github/workflows/release.yml) verifies, builds a signed APK, and publishes a GitHub Release with auto-generated notes.

```bash
git tag v0.1.0
git push origin v0.1.0
```

- `versionName` comes from the tag (`v0.1.0` → `0.1.0`); `versionCode` is the workflow run number.
- Tags with a suffix (e.g. `v0.2.0-beta.1`) are published as pre-releases.
- Required repository secrets (Settings → Secrets and variables → Actions):
  - `KEYSTORE_BASE64` — `base64 -i release.jks | pbcopy` (macOS) / `base64 -w0 release.jks` (Linux)
  - `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`
- Never commit the keystore; `*.jks`/`*.keystore` are git-ignored. Back it up — losing it means users can't upgrade in place.

## Reporting bugs / requesting features

Use the issue templates. Bug reports on Wi-Fi Direct or Bluetooth behavior are far more useful with device model + Android version + OEM skin, since that variability is a known project risk.

## Code of Conduct

This project follows the [Contributor Covenant](CODE_OF_CONDUCT.md). Please read it before participating.
