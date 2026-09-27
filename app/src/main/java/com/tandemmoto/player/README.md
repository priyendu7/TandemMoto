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

## Starting together and staying in step (#61)

- **Start gate (`StartGate`).** While linked, a song starts only when both phones have it: this
  phone's file (`SongFiles`) and the partner's `SongsOnPhone`. Otherwise the player **holds** at
  the song's start, still wanting to play (the button shows Pause): "Getting song…" on the phone
  without it, "Getting song on …" on the other. A partner that says it's playing the song has it.
  Not linked, each phone plays on its own. Controls, the partner's state and a song ending all go
  through the gate.
- **Start together.** When a held song reaches both phones (a download finished, the partner's
  list arrived: `Playback.recheck`), the phone that notices sends a start 300 ms ahead
  (`atNanos` in the future) and both start then. If both notice, the newer stamp wins and the
  other phone moves its start to match.
- **Giving up.** The phone without the song skips after its wait rules (60 s linked) and the
  skip is mirrored; a hold also gives up after 75 s as a backstop.
- **Can't play.** `SongsOnPhone.cantPlay` carries the songs a phone can't decode; the gate skips
  them on both phones, and Playlist says "Can't play on …".
- **Staying in step.** Every 5 s while playing, the phone that made the latest control sends a
  `Sync` with its position. Only the other phone corrects, and only past 0.5 s (a smaller jump
  would be heard for nothing).

## Phase 3 exit fixes (#62)

- **Stall check (`StallCheck`).** A song the player says is playing but whose position hasn't
  moved for 3 s (not held, not waiting for its file, not paused by another app's sound) is marked
  "Can't play on this phone" and skipped on both phones. The Redmi Y2 sat like that on two FLAC
  files its Files app plays, while Android reported them as playable.
- **Decoders.** Media3's decoder fallback tries the next decoder if one can't start. Two things
  were tried for the Redmi's 24-bit, 48 kHz FLAC files and dropped: float output (on the S25 it
  crackled and ran fast) and preferring a software FLAC decoder (the Redmi has none Media3 can
  use, so it still picked OMX.qti.audio.decoder.flac). Those files are skipped by the stall check;
  a bundled FLAC decoder is a Phase 7 item.
- **Diagnostics (`PlayerDiagnostics`).** Logs each song's decoder, format in and out, state
  changes, underruns, audio errors, and how long sound takes to start after a start or jump.
  The mirror logs the drift on every 5 s check ("In step with the partner: 42 ms").
