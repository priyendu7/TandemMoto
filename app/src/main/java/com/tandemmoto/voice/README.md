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
