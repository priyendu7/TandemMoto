# voice

Push-to-talk voice channel: Opus encode/decode, jitter buffer, dedicated
low-latency socket separate from the file-transfer socket. Noise suppression
+ AGC applied only while mic mode is active.

Development plan: Phase 4. Target: <~300ms round-trip latency.
