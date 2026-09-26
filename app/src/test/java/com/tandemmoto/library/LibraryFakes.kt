package com.tandemmoto.library

/** Scriptable [SongSource]: [files] maps a uri to what reading it finds (null = unreadable). */
class FakeSongSource(override var maxAccessCount: Int = 512) : SongSource {
    val files = mutableMapOf<String, SongFile?>()
    val folders = mutableMapOf<String, List<String>>()
    val held = mutableSetOf<String>()
    val gone = mutableSetOf<String>()
    val refuse = mutableSetOf<String>()
    var reads = 0

    fun file(uri: String, title: String, artist: String? = "Artist", durationMs: Long = 200_000) {
        files[uri] = SongFile("id-$uri", title, artist, durationMs, 5_000_000)
    }

    override suspend fun read(uri: String): SongFile? {
        reads++
        return files[uri]
    }

    override suspend fun listFolder(folder: String): List<String> = folders[folder].orEmpty()

    override fun displayName(uri: String): String = "$uri.mp3"

    override fun keepAccess(uri: String): Boolean {
        if (uri in refuse) return false
        held += uri
        return true
    }

    override fun releaseAccess(uri: String) {
        held -= uri
    }

    override fun heldAccessCount(): Int = held.size

    override suspend fun exists(uri: String): Boolean = uri !in gone
}

class InMemoryLibraryStore(var data: LibraryData = LibraryData()) : LibraryStore {
    var saves = 0

    override suspend fun load() = data

    override suspend fun save(data: LibraryData) {
        this.data = data
        saves++
    }
}
