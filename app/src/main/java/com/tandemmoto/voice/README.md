# voice

The intercom's audio (Phase 4). So far:

- `MicSource` / `AudioRecordSource`: the mic at 16 kHz mono, 16-bit, from the voice-call source
  (the maker's call processing; noise suppression is #73). `MicCapture` is closed to release the
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
- `AndroidAudioRoute`: call mode; Android 12+ picks the communication device (earbuds, else a
  wired headset, else the phone); before 12 it starts the earbuds' hands-free link (SCO) and waits
  up to 4 s, falling back to the phone's mic and speaker. Closing restores the mode, so music goes
  back to the earbuds' music mode.
- **Self-mute** (`MuteStore`, merged from #33): this phone's alone, only the button changes it,
  remembered across restarts. Muted releases the mic but keeps the route, so the partner is still
  heard. `Message.Muted` goes to the partner on every change and every connection ("… is muted").
- Logs: mic mode on/off, the route used, the earbuds' switch time, and the time from the pause to
  talking.
