package com.tandemmoto.library

import kotlinx.serialization.Serializable

/**
 * A song this phone added (#48). The file stays where it is: [uri] is read through the permission
 * the file picker granted, never copied. [id] is the SHA-256 fingerprint of the file's contents,
 * the same on both phones for the same file.
 */
@Serializable
data class Song(
    val id: String,
    val uri: String,
    val title: String,
    /** Null when the file has no artist tag ("Unknown artist" in the UI). */
    val artist: String?,
    val durationMs: Long,
    val sizeBytes: Long,
    /** The folder it was added with (its permission covers the song), or null for a single file. */
    val folder: String? = null
) {
    /** Logged instead of the title or file name: no song details in logs. */
    val logId: String get() = "song-" + id.take(8)
}

/** What this phone keeps about its songs, saved as one JSON file. */
@Serializable
data class LibraryData(
    val songs: List<Song> = emptyList(),
    /** Folders added with "Add a folder"; each holds one permission for everything inside. */
    val folders: List<String> = emptyList(),
    /** Songs removed from a folder, so "Check folders for new songs" doesn't add them back. */
    val removedFromFolders: Set<String> = emptySet(),
    /**
     * This phone's own file for a partner's song (the same track, another file; #49): ride
     * playlist song ID → this phone's song ID. It's played instead of downloading (#50, #51).
     */
    val copyOf: Map<String, String> = emptyMap()
)
