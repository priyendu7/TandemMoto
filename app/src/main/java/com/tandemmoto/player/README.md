# player

The local player (#51): each phone plays the ride playlist, and `PlaybackMirror` keeps the two
players in step (#60). The handlebar remote is a spike after Phase 3.

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

## Mirroring (#60)

- **One shared state, not button presses.** After a control on either phone, `PlaybackMirror`
  sends the result: `PlaybackState(song, playing, position, atNanos, stamp)`. The newest Lamport
  stamp wins (ties: the install ID), as in the ride playlist, so two controls at the same moment
  still leave both phones the same. A state that isn't newer changes nothing.
- **Every control goes through `Playback`'s public methods**: Home (with the seek bar, which
  seeks when it's let go), Playlist taps, the notification, and the media session (lock screen,
  earbuds), whose player is a `ForwardingPlayer` that calls them. Each tells the listener, which
  sends the state. `Playback.apply` is the partner's state: quiet, never sent back.
- **Also mirrored:** earphones unplugged, another app taking the audio for good, a song ending
  (both move on), and skipping a song that can't play. **Not mirrored:** short interruptions (a
  navigation prompt, a call) only suppress playback and resume on their own; call hold is Phase 5.
- **Where the partner is now:** the heartbeat's `Pong` carries the answering phone's clock, and
  `ClockOffset` works out the difference from the fastest recent round trips. A playing state is
  joined at its position plus the time since it was sent; within 150 ms nothing seeks (it would
  be heard). A pause lands on the exact position, so resuming starts both together.
- **Link down:** each phone plays on its own. On every connection both send where they are with
  their latest control's stamp; the newer wins. A restarted app is at clock 0, so it joins the
  partner. A state whose song isn't in the queue yet waits for it (`onQueueChanged`).
- **Logs:** "Followed the partner's Pause in 18 ms" on the phone that follows (hashed song IDs).
  Keeping the positions in step over time and waiting for both phones to have a song is #61.
