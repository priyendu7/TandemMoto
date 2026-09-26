# player

The local player (#51): each phone plays the ride playlist on its own in Phase 2; Phase 3 mirrors
play/pause/skip/seek between the phones and adds the handlebar remote.

- `Playback`: Media3 **ExoPlayer** plus a **MediaSession** (lock screen, notification, headset and
  Bluetooth buttons), owned by the app, not a `MediaSessionService`: the foreground service and
  its notifications stay ours (`service/LinkService`), so the link keeps its foreground
  service even with an empty playlist. Audio focus and "becoming noisy" (earphones unplugged)
  pause it.
- Every song is queued as `tandem://song/<id>`; `SongDataSource` resolves it when the player opens
  it (`SongFiles`: this phone's song, its copy of the partner's, a downloaded partner song), or
  waits ("Getting song…") while the song window (#50) brings it. `WaitRules`: only the current
  song counts down; give up after 20 s not linked, 60 s linked, or at once when storage is full,
  and the player skips to the next song.
- `QueueSync`: edits from either phone reach the player as add/remove/move steps, so the current
  song keeps playing through a reorder.
- The current song's place goes to the song window (#50), which downloads around it.
- A song whose audio format the phone can't decode raises no error (ExoPlayer just has no audio
  track): `Playback` checks the tracks, logs the format, marks it "Can't play on this phone" and
  skips it (the Redmi Y2 on Android 9 with two large files, #51 phone test).
