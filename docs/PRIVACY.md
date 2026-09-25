# Privacy Policy — TandemMoto

_Last updated: September 24, 2026_

TandemMoto is free, open-source software. It has no ads and is built to respect your privacy.

## The short version

- **We don't collect any personal data.** There are no accounts, no analytics, no advertising SDKs, and no trackers.
- **We don't store your data on any server.** There is no TandemMoto server.
- **We don't share or sell anything**, because we don't have anything to share.
- Music, voice, and playlist data only ever travel **directly between your phone and your partner's phone**.

## Data on your device

TandemMoto stores, in its private app storage on your phone:

- songs you choose to share, cached so they play without interruption,
- your shared playlist and basic settings,
- your paired partner's phone name and Wi-Fi Direct address, so TandemMoto only ever connects to that phone (removed with **Settings → Forget partner**),
- diagnostic logs (see below).

Uninstalling the app deletes all of it.

## Diagnostic logs

To help fix problems, TandemMoto keeps a small technical log in its private storage: app events (for example "link connected" or "reconnecting"), errors, and crash details, together with the app version, Android version and phone model. It's capped at 2 MB, older entries are overwritten, and it never contains your music, song names, voice, contacts, phone numbers or location.

The log **never leaves your phone on its own.** It's shared only if you choose **Settings → Export diagnostic logs** and pick where to send it, for example an email to the developer. Whoever you send it to can read it.

## Permissions

The app only asks for permissions a feature needs, and only uses them for that feature:

| Permission | Why it's needed | Leaves your device? |
|---|---|---|
| Nearby Wi-Fi devices (Android 13+) | Find and connect directly to your partner's phone over Wi-Fi Direct | Only to your paired partner's phone |
| Location (Android 12 and older only) | Android requires it for Wi-Fi Direct discovery on these versions. TandemMoto never reads or stores your location | No |
| Wi-Fi and network state | Set up and monitor the direct phone-to-phone link | No |

## Network use

TandemMoto doesn't connect to the internet. It connects only to your partner's phone, directly, over Wi-Fi Direct. Nothing goes through a router, a cloud service, or any server.

## Google Play

When you download the app from Google Play, Google processes data under [Google's privacy policy](https://policies.google.com/privacy). We don't receive personal data from Google beyond the aggregate, anonymised statistics every developer sees in the Play Console (e.g. install counts and crash reports that Android sends if you've allowed it).

## Children

The app doesn't collect data from anyone, including children.

## Verify it yourself

TandemMoto is open source. You can read every line of code at https://github.com/priyendu7/TandemMoto.

## Changes and contact

If this policy ever changes, the new version will be published here with a new date. Questions: dr.jaikallabs@gmail.com
