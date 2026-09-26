# library

"My songs" (#48): the songs this phone adds to the ride playlist.

- Added with Android's **file picker** only: **Add songs** (files) or **Add a folder** (everything
  inside, including subfolders). No storage permission, no library scan.
- **Remembered in place, never copied:** each file (or folder) holds a persistable read
  permission. Android keeps at most 512 of those (Android 11+) or 128 (10 and older) and silently
  drops the oldest beyond that, so single files past the limit aren't added and the summary
  suggests a folder, which needs one permission for all its songs.
- `Song.id` is the SHA‑256 **fingerprint** of the file (`Fingerprint`), the same on both phones for
  the same file. `Duplicates`: the same fingerprint, or the same title + artist (ignoring case and
  spacing) with a duration within 2 s, is one song.
- The order lives in the shared ride playlist (`playlist/`, #49); the library is this phone's
  files, including its own copies of the partner's songs (`copyOf`), checked against the
  partner's songs when adding.
- `Library` removes (giving the permission back; a folder's last song lets go of
  the folder), re-checks folders for new songs, and marks songs whose file is gone as
  missing. Saved as one JSON file (`JsonFileLibraryStore`), not Room.
- `SongSource` is the Android boundary (`AndroidSongSource`: `MediaMetadataRetriever` for tags,
  the storage access framework for folders and permissions), so the rules run on the JVM.
- Logs never contain titles or file names: songs are `song-<first 8 of the fingerprint>`.

Next: the ride playlist shared with the partner (#49), song transfer (#50), the player (#51).
