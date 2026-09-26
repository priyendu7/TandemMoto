package com.tandemmoto.library

import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class JsonFileLibraryStoreTest {
    private val dir: File = Files.createTempDirectory("library").toFile()
    private val file = File(dir, "library/my_songs.json")

    @Test
    fun savesAndLoadsTheLibrary() = runTest {
        val store = JsonFileLibraryStore(file, StandardTestDispatcher(testScheduler))
        val data = LibraryData(
            songs = listOf(
                Song("id1", "content://a", "Alpha", "Artist", 200_000, 5_000_000),
                Song(
                    "id2",
                    "content://tree/doc",
                    "Bravo",
                    null,
                    100_000,
                    3_000_000,
                    folder = "content://tree"
                )
            ),
            folders = listOf("content://tree"),
            removedFromFolders = setOf("content://tree/doc2")
        )
        store.save(data)
        assertEquals(data, JsonFileLibraryStore(file, StandardTestDispatcher(testScheduler)).load())
    }

    @Test
    fun noFileOrABrokenOneStartsEmpty() = runTest {
        val store = JsonFileLibraryStore(file, StandardTestDispatcher(testScheduler))
        assertEquals(LibraryData(), store.load())
        file.parentFile!!.mkdirs()
        file.writeText("{ not json")
        assertEquals(LibraryData(), store.load())
    }
}
