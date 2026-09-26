# transfer

Song transfer and the song window (#50): each phone downloads the partner's songs near the
current song, never its own (those are played in place).

- `SongWindow` (pure): which of the partner's songs to hold, in download order (current, then
  ahead nearest first, then behind) and which to delete. Default 2 behind · current · 7 ahead
  (`WindowSettings`, per phone, Settings → Songs from partner). It shrinks to what fits above
  500 MB free, cutting behind first, then the far end ahead; the current song may still come in
  while 100 MB stays free (deleted after playing, #51); under that, "Phone storage full".
- `SongTransfers`: asks for the next wanted song with `SongRequest(id, offset)` on the command
  channel and serves the partner's requests for its own songs, one at a time. A drop resumes from
  the bytes already in the part file as soon as the connection is back; a request with no answer
  is asked again after 20 s. Each phone tells the other which songs it can play (`SongsOnPhone`)
  for "On both phones". Logs speed per song (`Received song-…: 25.1 MB in 7.3 s (27 Mbit/s)`).
- `TransferConnection`: its own TCP connection (port 48153, the group owner listens), so a big
  song never delays the heartbeat or the playlist. Carries `SongChunk`s: binary, 60 KB of song
  plus a small header, one per frame.
- `SongStore`: `partner_songs/` in app-private storage; `<id>.part` while arriving, kept only if
  the fingerprint matches the ID (a broken or tampered file is never played).

Until the player (#51) moves it, the current song is the first in the playlist.
