# Releasing TandemMoto

Every build is **built once** from a git tag, uploaded to Google Play's **internal testing** track, and then **promoted** — the exact same binary — through closed testing, open testing, and a staged production rollout. Nothing is rebuilt between stages.

This covers the **production app** (`com.tandemmoto`). Testing unmerged PRs happens in the separate **TandemMoto QA** app — see [TESTING_ON_PLAY.md](TESTING_ON_PLAY.md).

## Branch model

- `main` holds **production-ready code only**. Everything lands via reviewed PRs with green CI, after any needed Play testing of the PR itself.
- Releases are tags on `main`. `release.yml` refuses to build a tag whose commit isn't on `main`.
- **Revert/hotfix rule:** if a tagged build turns out bad in internal or closed testing, fix `main` through a PR (a revert or a fix) and tag the next patch version (`v0.2.1`). Never move or reuse a tag.

```
PR ──► CI (ci.yml) + optional /play-test (QA app) ──► merge to main
                            │
             git tag v0.2.0 && git push origin v0.2.0
                            │
      release.yml (automatic) ── verify ── signed AAB ──► Play: internal
                            │                          └► GitHub: pre-release (+ APK)
                            │
      promote.yml (manual, approval required)
         internal ──► alpha (closed testing) ──► beta (open testing, optional)
                            │
                            └──► production 10% ─► 50% ─► 100% ──► GitHub: "Latest" release
                                      └─ halt if crash rate / reviews go bad
```

| Stage | Play track | Who gets it | Review by Google | Typical use |
|---|---|---|---|---|
| Internal testing | `internal` | Up to 100 testers you list by email | None — available in minutes | Every tagged build; maintainers + a couple of riders |
| Closed testing | `alpha` | Testers in email lists / Google Groups | Yes (usually hours–days) | Field testing on real bikes (Phase 6) |
| Open testing | `beta` | Anyone who opts in from the Play listing | Yes | Public beta once the core is stable |
| Production | `production` | Everyone, as a % staged rollout | Yes | Stable releases |

## Day-to-day

### 1. Cut a build → internal testing

```bash
git checkout main && git pull
git tag v0.2.0
git push origin v0.2.0
```

`release.yml` then:
- runs ktlint, lint, and unit tests,
- checks the tagged commit is on `main`,
- builds a signed AAB + APK with `versionName = 0.2.0`, `versionCode = <release workflow run number>` (don't rename `release.yml` — that resets the run number; if you ever must, add an offset),
- uploads the AAB to Play **internal** with release notes built from commit subjects since the previous tag,
- creates a GitHub **pre-release** with the APK, checksums, and generated notes.

Internal testers get the update through the Play Store within minutes (they may need to pull-to-refresh in *Manage apps*).

Version names only need to be unique per build. If an internal build is bad, fix it on `main` and tag `v0.2.1` — don't reuse or move tags.

### 2. Promote → closed / open testing

GitHub → **Actions** → **Promote** → **Run workflow**:
- `internal → alpha (closed testing)` — promotes the latest internal build to closed testers.
- `alpha → beta (open testing)` — optional public beta.

Approve the run when GitHub asks (the `play-production` environment gate). Google reviews closed/open/production releases; the Play Console shows review status.

### 3. Promote → production (staged rollout)

1. Run **Promote** with `alpha → production (staged)` (or `beta → production`) and `user_fraction = 0.1` → 10% of users.
2. Watch Play Console → *Android vitals* (crashes, ANRs) and reviews for a day or two.
3. Run **Promote** with `update production rollout` and `0.5`, then again with `1.0`.
   Set `tag` (e.g. `v0.2.0`) on the `1.0` run so the GitHub release is marked **Latest**.
4. Something wrong? Run `halt production rollout` — no new users get the build. Ship a fix as a new tag and promote it.

## One-time setup

### GitHub

1. **Secrets** (Settings → Secrets and variables → Actions → *Repository secrets*):

   | Secret | Value |
   |---|---|
   | `KEYSTORE_BASE64` | `base64 -i tandemmoto-release.jks \| pbcopy` |
   | `KEYSTORE_PASSWORD` | keystore password |
   | `KEY_ALIAS` | key alias (e.g. `tandemmoto`) |
   | `KEY_PASSWORD` | key password |
   | `PLAY_SERVICE_ACCOUNT_JSON` | full contents of the Play service-account JSON key (below) |

2. **Environment** (Settings → Environments → *New environment* → `play-production`):
   - *Required reviewers*: add yourself (and future co-maintainers).
   - *Deployment branches and tags*: **Selected branches and tags** → add `main`.
   - Move `PLAY_SERVICE_ACCOUNT_JSON` here as an *environment secret* if you want Play credentials usable only by approved promote runs (then also add `environment: play-production` to the release job).

3. **Variable** (Settings → Secrets and variables → Actions → *Variables*): until your app has passed its first Play review, set `PLAY_RELEASE_STATUS = draft`. Uploads then land as drafts you roll out by hand in the Play Console. Delete the variable afterwards.

4. **Tag protection** (Settings → Rules → Rulesets → *New tag ruleset*): target `v*`, restrict creation/updates/deletions to admins so only maintainers can trigger releases.

### Google Play Console

1. Create a developer account at [play.google.com/console](https://play.google.com/console) (one-time US$25). **Personal accounts** created after Nov 2023 must run a **closed test with at least 12 testers opted in for 14 consecutive days** before production access is granted — plan for this in Phase 6.
2. **Create app** → name *TandemMoto*, app, free.
3. Complete *Set up your app*: privacy policy URL (required — the app uses microphone and location permissions), app access, ads (none), content rating, target audience, data safety, and the store listing (icon, screenshots, descriptions).
4. **Play App Signing** is on by default: Google holds the app signing key; the keystore in GitHub secrets is your **upload key**. If the upload key is ever lost, it can be reset via Play support — the app key is safe with Google.
5. **First upload is manual** (the Play API can't create the first release): download the AAB from a CI run — or build locally with the upload key — and upload it in *Testing → Internal testing → Create new release*. Package name `com.tandemmoto` is locked in from then on.
6. *Testing → Internal testing → Testers*: create an email list, add testers, and share the opt-in link with them.

### Service account for CI

1. Google Cloud Console → create (or pick) a project → **enable the *Google Play Android Developer API***.
2. *IAM & Admin → Service accounts* → create `github-play-publisher` (no project roles needed) → *Keys → Add key → JSON* → download.
3. Play Console → *Users and permissions → Invite new users* → the service account's email → *App permissions* → TandemMoto → grant **Release to testing tracks**, **Release to production, exclude devices, and use Play App Signing**, and **Manage testing tracks and edit tester lists**.
4. Paste the JSON into the `PLAY_SERVICE_ACCOUNT_JSON` secret, then **delete the local file**. Never commit it (`play-service-account*.json` is git-ignored).

Until `PLAY_SERVICE_ACCOUNT_JSON` is set, `release.yml` still builds and publishes the GitHub pre-release and just skips the Play upload with a warning.
