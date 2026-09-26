package com.tandemmoto.library

import java.io.File
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

interface LibraryStore {
    suspend fun load(): LibraryData

    suspend fun save(data: LibraryData)
}

/**
 * [LibraryData] as one JSON file in app storage. Written to a temporary file and renamed, so a
 * crash mid-write never leaves a broken list; an unreadable file starts an empty library.
 */
class JsonFileLibraryStore(
    private val file: File,
    private val io: CoroutineDispatcher = Dispatchers.IO
) : LibraryStore {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    override suspend fun load(): LibraryData = withContext(io) {
        if (!file.exists()) return@withContext LibraryData()
        runCatching { json.decodeFromString(LibraryData.serializer(), file.readText()) }
            .getOrDefault(LibraryData())
    }

    override suspend fun save(data: LibraryData) = withContext(io) {
        file.parentFile?.mkdirs()
        val temp = File(file.parentFile, file.name + ".tmp")
        temp.writeText(json.encodeToString(LibraryData.serializer(), data))
        if (!temp.renameTo(file)) {
            file.delete()
            temp.renameTo(file)
        }
        Unit
    }
}
