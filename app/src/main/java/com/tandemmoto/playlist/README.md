# playlist

The ride playlist (#49): one ordered list of both phones' songs, the same on both, edited on
either, including while the phones are apart.

- `RideEntry`: a song with its owner (the install ID of the phone with the file) and small stamps:
  when it was added, its `position` (a sortable key, `FractionalIndex`) and when it was last moved,
  and whether it's removed. Stamps use a counter both phones keep in step (a Lamport clock), not
  the wall clock; ties break on the install ID.
- `RideList`: the merge rules. Lists merge **song by song**: songs added on either phone are all
  kept, and for each song the newest change wins. Merging gives the same result in any order, so
  the phones always converge. Removed songs stay as markers, so an old copy can't bring them back;
  adding one again does. The same track in two different files keeps the entry added first; the
  other phone keeps its file as its copy (`LibraryData.copyOf`).
- `RidePlaylist`: the list on this phone (saved as JSON), its edits (add at the end, remove, move)
  and meeting a partner: a different partner than last time drops the previous partner's songs,
  and the first time two lists combine, the **initiator's songs come first**, then the acceptor's.
- `PlaylistSync`: on every connection both phones send their whole list (`PlaylistEntries`, in
  batches of 100 to stay under the 64 KB frame limit) and merge the other's; after that each edit
  sends only the changed song. Songs added in `library/` join the list; a song leaving the list
  lets its owner release the file, and a copy is released with it.

The files themselves are sent by #50; the player is #51.
