# state

The command/state protocol between the two phones, over the persistent command socket
(`link/CommandChannel`), separate from the future audio and file-transfer sockets. Carries the
heartbeat now, and playback commands (Phase 3) and mic-mode state (Phase 4) later.

- `Message`: `Hello`, `Ping`/`Pong`, `Bye(reason)`. New types are added as new subclasses; an
  older app skips types it doesn't know, so adding one doesn't need a protocol bump.
- `MessageCodec`: a JSON envelope `{ "v": 1, "type": "ping", "seq": 7, "payload": { … } }`.
  `PROTOCOL_VERSION` (`v`) changes only for incompatible changes; the phones then refuse each
  other with `Bye(ProtocolMismatch)` and ask for an update.
- `Framing`: a 4-byte big-endian length, then the JSON; frames over 64 KB are rejected.

**Why JSON, not protobuf (#25):** the messages are a few dozen bytes at ~5–10 per second, so size
and parsing speed don't matter; JSON is readable in a packet capture or a test failure;
kotlinx.serialization maps the sealed class directly and runs on the JVM tests; protobuf would add
a code-generation step and a schema to keep in sync for no measurable gain. The framing is
independent of the encoding, so switching later only touches `MessageCodec`.

Development plan: Phase 1–3.
