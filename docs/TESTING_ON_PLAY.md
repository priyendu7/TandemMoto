# Testing pull requests through Google Play

Any pull request — including one from a fork — can be tested on real phones **through the Play Store before it is merged**. `main` only ever holds production-ready code, so testing happens on the PR.

PR builds go to a **separate app, "TandemMoto QA"** (`com.tandemmoto.qa`):

- it installs **alongside** the production app — testers never lose their real install or its data;
- it's signed with a separate QA key, so PR code is never signed as the production app;
- it can never reach production — only tags on `main` feed the production app (see [RELEASING.md](RELEASING.md)).

There are two ways to get a PR build to testers:

| | Internal app sharing link | Contributor closed track |
|---|---|---|
| Best for | Quick checks of a PR | Longer field testing with your own group of riders |
| Who can install | Anyone you send the link to | Members of your Google Group |
| Google review | None — ready in minutes | Yes — usually a few hours |
| Availability | Any PR, on request | Trusted contributors, granted by a maintainer |
| Expires | Link expires after 60 days | Stays until the next upload to the track |

## For contributors

1. Open your PR and make sure CI is green.
2. In the PR description, fill in the **Play testing** section: say whether you want a sharing link or an upload to your track.
3. A maintainer reviews the code and comments `/play-test <commit-sha>` (optionally with your track). The bot replies with the install link or confirms the track upload.
4. If you push new commits, ask again — each build is tied to the exact commit a maintainer reviewed.

### Getting your own closed track

If you'll be doing ongoing field testing (e.g. on your bike with a group of riders), open a **[Request a Play test track](../../../issues/new?template=play_test_track.yml)** issue. You'll need a **Google Group** for your testers — Google Play grants closed-track access by group, not by individual email:

1. Create a group at [groups.google.com](https://groups.google.com) (e.g. `tandemmoto-testers-<you>@googlegroups.com`) and add your testers.
2. Put the group address in the request issue.

Tracks are granted at the maintainer's discretion, usually after you've had a PR or two reviewed. Your track will be named `tester-<your-github-username>`.

## For testers

### Installing from an internal app sharing link

One-time setup on each phone:

1. Open the **Play Store** → tap your profile picture → **Settings** → **About**.
2. Tap **Play Store version** seven times until you see "You are now a developer".
3. Back in **Settings** → **General** → turn on **Internal app sharing**.

Then open the link from the PR on the phone (signed into the Play Store) and tap **Install**. The app appears as **TandemMoto QA**.

### Installing from a closed track

1. Join the contributor's Google Group with the Google account you use on the Play Store.
2. Open the opt-in link the contributor shares (from the track request issue), tap **Become a tester**.
3. Install **TandemMoto QA** from the Play Store. New uploads to the track arrive as normal Play Store updates.

### Good to know

- Internal sharing links are posted on the public PR, so anyone who can see the PR can install that build.
- QA builds are pre-release code: expect bugs, and please report them on the PR with your device model and Android version.

## For maintainers

### Running a test build

1. **Review the PR's code at a specific commit** — including Gradle files. The build runs whatever the PR contains at that commit.
2. Comment on the PR:
   - `/play-test <sha>` → internal app sharing link
   - `/play-test <sha> tester-<name>` → upload to that contributor's closed track
3. [`play-test.yml`](../.github/workflows/play-test.yml) then:
   - **gate** — checks you have write access, that the SHA belongs to the PR, and computes a unique version code;
   - **build** — builds the unsigned QA bundle from that SHA with **no secrets, a read-only token, and no Gradle cache**;
   - **publish** — (trusted code from `main` only) checks the bundle is `com.tandemmoto.qa`, not debuggable, with the expected version code; signs it with the QA upload key; uploads; comments the result.

Anyone without write access who tries `/play-test` gets a refusal comment. A track name must match `tester-<name>` and already exist in the Play Console, so PR builds can't target any other track.

### Adding a trusted contributor's track

1. Play Console → **TandemMoto QA** → **Test and release → Testing → Closed testing → Create track** → name it `tester-<github-username>`.
2. **Testers** tab → add their Google Group → save. Select countries if prompted.
3. Copy the **opt-in link** and post it on their request issue, then close it.
4. Their first upload to the track goes through Google review; later ones are usually faster.

To revoke access, remove the Google Group from the track (or pause the track).

### One-time setup

**Play Console — create the QA app**

1. **Create app** → name *TandemMoto QA*, free. Package name is set by the first upload: `com.tandemmoto.qa`.
2. Complete the minimum *Set up your app* tasks (privacy policy URL, app access, ads, content rating, target audience, data safety). The QA app never goes to production, so Google's 12-tester/14-day production-access requirement doesn't apply to it.
3. Create a **separate QA upload keystore** (never reuse the production one):
   ```bash
   keytool -genkeypair -v -keystore ~/tandemmoto-qa-upload.jks -alias tandemmoto-qa -keyalg RSA -keysize 4096 -validity 10000
   ```
4. **First upload is manual:** build and sign a QA bundle locally, then upload it in *Testing → Internal testing → Create new release*:
   ```bash
   VERSION_CODE=1 VERSION_NAME=qa-bootstrap ./gradlew bundleQa
   jarsigner -sigalg SHA256withRSA -digestalg SHA-256 -keystore ~/tandemmoto-qa-upload.jks app/build/outputs/bundle/qa/app-qa.aab tandemmoto-qa
   ```
5. **Users and permissions** → the existing CI service account → add *TandemMoto QA* with **Release to testing tracks** and **Manage testing tracks and edit tester lists**.
6. **Internal app sharing** (Play Console → *Setup → Internal app sharing*):
   - add the service account's email as an **uploader**;
   - set *Who can download* to **Anyone you shared the link with**.

**GitHub — `play-qa` environment** (Settings → Environments → New environment → `play-qa`)

- *Deployment branches and tags*: **Selected branches** → `main`.
- *Environment secrets*:

  | Secret | Value |
  |---|---|
  | `QA_KEYSTORE_BASE64` | `base64 -i ~/tandemmoto-qa-upload.jks \| pbcopy` |
  | `QA_KEYSTORE_PASSWORD` | QA keystore password |
  | `QA_KEY_ALIAS` | `tandemmoto-qa` |
  | `QA_KEY_PASSWORD` | QA key password |

- `PLAY_SERVICE_ACCOUNT_JSON` can stay a repository secret (shared with `release.yml`).

No required reviewers are needed on this environment: the maintainer's `/play-test` comment is the approval, and the environment's `main`-only rule keeps the secrets away from any other branch.

## Publishing a fork to your own Play account

If you'd rather run your own Play listing (e.g. for a long-lived fork), build with your own package name:

```bash
./gradlew bundleRelease -Ptandemmoto.applicationId=com.yourname.tandemmoto
```

and sign/upload it with your own keys and developer account.
