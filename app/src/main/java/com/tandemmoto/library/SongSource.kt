package com.tandemmoto.library

/** What reading a picked file found: its fingerprint and tags. */
data class SongFile(
    val id: String,
    val title: String,
    val artist: String?,
    val durationMs: Long,
    val sizeBytes: Long
)

/**
 * The slice of Android's storage access framework the library needs, so [Library]'s rules run on
 * the JVM with a fake. Uris are strings here; [AndroidSongSource] turns them into content Uris.
 */
interface SongSource {
    /** Tags and fingerprint of [uri], or null when it isn't a playable audio file. */
    suspend fun read(uri: String): SongFile?

    /** Audio files in [folder] (a tree Uri from "Add a folder") and its subfolders. */
    suspend fun listFolder(folder: String): List<String>

    /** The file's name, shown while it's being read. */
    fun displayName(uri: String): String?

    /** Keeps access to [uri] after restarts (a persistable permission). False if refused. */
    fun keepAccess(uri: String): Boolean

    fun releaseAccess(uri: String)

    /** Permanent permissions this app holds now (files and folders). */
    fun heldAccessCount(): Int

    /** The most Android keeps: 512 on Android 11+, 128 before; beyond it the oldest drop. */
    val maxAccessCount: Int

    /** The file is still there and readable. */
    suspend fun exists(uri: String): Boolean
}
