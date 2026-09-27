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
