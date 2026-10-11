# voice

The intercom's audio (Phase 4). So far:

- `MicSource` / `AudioRecordSource`: the mic at 16 kHz mono, 16-bit, from the voice-call source
  (the maker's call processing, plus Android's effects: see #73 below). `MicCapture` is closed to release the
  mic, which turns Android's mic indicator off.
- `MicTest` (#70): Settings → Diagnostics → Mic test. Waits 10 s or 5 min (time to lock the
  screen, or leave the app and make it reconnect), records 5 s, and logs the loudness every
  second. **All zeros means Android blocked the mic** (it gives a blocked app silence, not an
  error). Nothing is kept.

Next: the voice channel over UDP (#71), and mic mode (#72).

## Voice channel (#71)

```
mic (16 kHz, 20 ms frames) → VoicePacket → UDP 48154 (DSCP EF) → partner → JitterBuffer → speaker
```

- `VoicePacket` / `VoicePackets`: magic `TV`, version, format id, sequence number, send time
  (sender's clock), then the payload. `VoiceFormat` is `Pcm16k` (uncompressed, as decided in
  Phase 4 planning); Opus could replace it with a new id.
- `JitterBuffer`: back into order by sequence number; playback runs 2–6 frames (40–120 ms) behind
  arrival, following the p95 spread of recent arrivals; a late packet is dropped; a missing one
  is filled with the last frame fading out, then silence; after a gap it fills up again. An app
  restart that counts from 0 again is followed.
- `VoiceChannel`: runs while the command channel knows the partner's address
  (`CommandChannel.partnerHost`); **sends only while `setSending(true)`** (mic mode #72, or the
  Talk test) and plays whatever voice arrives, closing the speaker after 1 s of silence. Capture,
  receive and playback are threads at Android's urgent-audio priority; the speaker's blocking
  write sets the pace. Every 10 s: `Voice: sent …; received …, filled in …, late …; network
  median/p95; mouth-to-ear about … ms (frame + mic + network + buffer + speaker)`.
- `TalkTest` (Settings → Diagnostics, while linked): sends this phone's mic, in call mode, for
  at most 2 minutes.

## Mic mode and self-mute (#72)

- **Mic mode = linked, the intercom wanted, and the music not playing** (`IntercomRules`). "Wanted"
  is part of the shared playback state (`PlaybackMirror.intercomWanted`, newest control wins): it
  turns on the first time music plays in a ride, or with **Start intercom** on Home, and off with
  **Stop intercom** or Disconnect. So it isn't on straight after connecting, and it works with no
  music at all. A held song ("Getting song…") counts as playing.
- `Intercom` opens the audio route (`AudioRoute`), then sends the mic unless muted; every way out
  (music plays, Stop, the link drops, Disconnect) stops sending and closes the route, so no mic
  is left open. Music resuming while the earbuds are still switching closes it too.
- **Bluetooth earbuds stay in music mode by default** (`RouteKind.EarbudsMusicMode`): no call
  mode; the partner's voice plays as media on the earbuds and you talk into the phone's mic. On
  the Redmi Y2 with the boAt Nirvana, call mode dropped the earbuds' music connection around
  every switch (1.4–2.2 s, sometimes not back by the next pause) and, sharing one radio with
  Wi-Fi, slowed the voice to 0.4–1.4 s (phone test on #72). **Settings → Advanced → Use earbud
  mic for the intercom** turns call mode back on for earbuds that handle it.
- `AndroidAudioRoute`: call mode; Android 12+ picks the communication device (earbuds, else a
  wired headset, else the phone); before 12 it starts the earbuds' hands-free link (SCO) and waits
  up to 4 s, falling back to the phone's mic and speaker. Closing restores the mode, so music goes
  back to the earbuds' music mode.
- **Self-mute** (`MuteStore`, merged from #33): this phone's alone, only the button changes it,
  remembered across restarts. Muted releases the mic but keeps the route, so the partner is still
  heard. `Message.Muted` goes to the partner on every change and every connection ("… is muted").
- Logs: mic mode on/off, the route used, the earbuds' switch time, and the time from the pause to
  talking.

## Noise suppression and gain control (#73)

- With **voice processing on** (the default), `AudioRecordSource` uses the voice-call source and
  attaches Android's `NoiseSuppressor`, `AutomaticGainControl` and `AcousticEchoCanceler` where
  the phone has them (`MicEffects`). They belong to the open mic's audio session, so they run
  only while a mic is open (mic mode, the Talk test, the Mic test) and are released with it.
  Each open logs what it got: `Mic open with the voice-call source; noise suppressor on, …`.
- **Settings → Advanced → Voice processing (test)** turns it off for A/B tests: the
  voice-recognition source (Android requires it to be unprocessed) and no effects. It applies the
  next time the mic opens (toggle the Talk test off and on). The row lists the effects this phone
  has.
- The Talk test shows a **level meter** (`VoiceChannel.micLevelDb`, the loudest 20 ms frame of
  every 100 ms), and the 10 s voice log adds `mic level median … dBFS, p95 … dBFS` (median is
  mostly background, p95 speech), to compare gain between phones.
- The mic delay in the mouth-to-ear estimate is now measured from Android's capture timestamp
  (refreshed every 100 ms) instead of guessed from the buffer size. It covers buffering, not an
  effect's own algorithmic delay; that needs the bench A/B.
- WebRTC's audio processing (native) is the fallback if the bike test (Phase 6) shows this isn't
  enough.
