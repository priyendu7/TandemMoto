package com.tandemmoto.library

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryTest {
    private val source = FakeSongSource()
    private val store = InMemoryLibraryStore()

    private fun TestScope.library() = Library(
        source = source,
        store = store,
        scope = backgroundScope,
        io = StandardTestDispatcher(testScheduler)
    ).also {
        it.start()
        runCurrent()
    }

    private val Library.titles get() = state.value.songs.map { it.title }

    /** Runs [action] and returns the import summary it produced. */
    private suspend fun TestScope.summaryOf(library: Library, action: () -> Unit): ImportSummary {
        val summary = backgroundScope.async { library.summaries.first() }
        runCurrent()
        action()
        runCurrent()
        return summary.await()
    }

    @Test
    fun addedFilesAppearInOrderAndAreKeptWithoutCopies() = runTest {
        source.file("a", "Alpha")
        source.file("b", "Bravo")
        val library = library()
        val summary = summaryOf(library) { library.addFiles(listOf("a", "b")) }
        assertEquals(ImportSummary(added = 2, alreadyThere = 0, unreadable = 0), summary)
        assertEquals(listOf("Alpha", "Bravo"), library.titles)
        assertEquals(setOf("a", "b"), source.held) // permission kept, the file stays in place
        assertEquals(listOf("a", "b"), store.data.songs.map { it.uri })
    }

    @Test
    fun filesShowAsReadingUntilTheirDetailsAreIn() = runTest {
        source.file("a", "Alpha")
        val library = library()
        library.addFiles(listOf("a"))
        runCurrent()
        // The pending row appears first; reading happens on the IO dispatcher.
        val seen = library.state.value
        assertTrue(seen.reading.isNotEmpty() || seen.songs.isNotEmpty())
        runCurrent()
        assertTrue(library.state.value.reading.isEmpty())
        assertEquals(listOf("Alpha"), library.titles)
    }

    @Test
    fun theSameFileOrTrackIsNotAddedTwice() = runTest {
        source.file("a", "Alpha")
        source.files["copy"] = source.files["a"] // same fingerprint, another location
        source.file("flac", "  ALPHA ", durationMs = 201_500) // same track, another file
        val library = library()
        summaryOf(library) { library.addFiles(listOf("a")) }
        val summary = summaryOf(library) { library.addFiles(listOf("copy", "flac")) }
        assertEquals(ImportSummary(added = 0, alreadyThere = 2, unreadable = 0), summary)
        assertEquals(listOf("Alpha"), library.titles)
        assertEquals(setOf("a"), source.held) // duplicates' permissions are given back
    }

    @Test
    fun pickingTheSameFileAgainSaysItsAlreadyThere() = runTest {
        // Phone test on #48: it said "Added 0 songs".
        source.file("a", "Alpha")
        val library = library()
        summaryOf(library) { library.addFiles(listOf("a")) }
        val summary = summaryOf(library) { library.addFiles(listOf("a")) }
        assertEquals(ImportSummary(added = 0, alreadyThere = 1, unreadable = 0), summary)
        assertEquals(setOf("a"), source.held) // still held for the song that's there
    }

    @Test
    fun unreadableFilesAreReportedAndReleased() = runTest {
        source.files["bad"] = null
        source.file("a", "Alpha")
        val library = library()
        val summary = summaryOf(library) { library.addFiles(listOf("bad", "a")) }
        assertEquals(ImportSummary(added = 1, alreadyThere = 0, unreadable = 1), summary)
        assertFalse("bad" in source.held)
    }

    @Test
    fun filesBeyondAndroidsPermissionLimitAreNotAdded() = runTest {
        source.maxAccessCount = 3
        listOf("a", "b", "c", "d", "e").forEach { source.file(it, it.uppercase()) }
        val library = library()
        val summary = summaryOf(library) { library.addFiles(listOf("a", "b", "c", "d", "e")) }
        assertEquals(
            ImportSummary(added = 3, alreadyThere = 0, unreadable = 0, overLimit = 2),
            summary
        )
        assertEquals(0, library.fileSlotsLeft)
    }

    @Test
    fun aFolderAddsEverythingInsideUnderOnePermission() = runTest {
        source.maxAccessCount = 1 // a folder isn't limited by the file count
        source.folders["music"] = listOf("music/1", "music/2", "music/sub/3")
        source.folders["music"]!!.forEachIndexed { i, uri -> source.file(uri, "Song $i") }
        val library = library()
        val summary = summaryOf(library) { library.addFolder("music") }
        assertEquals(3, summary.added)
        assertTrue(summary.fromFolder)
        assertEquals(setOf("music"), source.held)
        assertEquals(listOf("music"), library.state.value.folders)
        assertTrue(library.state.value.songs.all { it.folder == "music" })
    }

    @Test
    fun checkingFoldersAddsNewSongsButNotRemovedOnes() = runTest {
        source.folders["music"] = listOf("music/1", "music/2")
        source.file("music/1", "One")
        source.file("music/2", "Two")
        val library = library()
        summaryOf(library) { library.addFolder("music") }
        library.remove("id-music/2")
        runCurrent()

        source.folders["music"] = listOf("music/1", "music/2", "music/3")
        source.file("music/3", "Three")
        val summary = summaryOf(library) { library.checkFolders() }
        assertEquals(1, summary.added)
        assertEquals(listOf("One", "Three"), library.titles)
    }

    @Test
    fun anEmptyFolderSaysSo() = runTest {
        source.folders["empty"] = emptyList()
        val library = library()
        val summary = summaryOf(library) { library.addFolder("empty") }
        assertEquals(ImportSummary(0, 0, 0, fromFolder = true), summary)
    }

    @Test
    fun removingASongGivesItsPermissionBack() = runTest {
        source.file("a", "Alpha")
        val library = library()
        summaryOf(library) { library.addFiles(listOf("a")) }
        library.remove("id-a")
        runCurrent()
        assertTrue(library.titles.isEmpty())
        assertTrue(source.held.isEmpty())
    }

    @Test
    fun removingAFoldersLastSongLetsGoOfTheFolder() = runTest {
        source.folders["music"] = listOf("music/1")
        source.file("music/1", "One")
        val library = library()
        summaryOf(library) { library.addFolder("music") }
        library.remove("id-music/1")
        runCurrent()
        assertTrue(source.held.isEmpty())
        assertTrue(library.state.value.folders.isEmpty())

        summaryOf(library) { library.addFolder("music") } // added again later: all of it comes back
        assertEquals(listOf("One"), library.titles)
    }

    @Test
    fun songsCanBeReordered() = runTest {
        listOf("a", "b", "c").forEach { source.file(it, it.uppercase()) }
        val library = library()
        summaryOf(library) { library.addFiles(listOf("a", "b", "c")) }
        library.move(2, 0)
        runCurrent()
        assertEquals(listOf("C", "A", "B"), library.titles)
        library.move(0, 5) // out of range: ignored
        runCurrent()
        assertEquals(listOf("C", "A", "B"), store.data.songs.map { it.title })
    }

    @Test
    fun missingFilesAreMarkedNotRemoved() = runTest {
        source.file("a", "Alpha")
        source.file("b", "Bravo")
        val library = library()
        summaryOf(library) { library.addFiles(listOf("a", "b")) }
        source.gone += "b"
        library.checkFiles()
        runCurrent()
        assertEquals(setOf("id-b"), library.state.value.missing)
        assertEquals(listOf("Alpha", "Bravo"), library.titles)
    }

    @Test
    fun theListSurvivesARestart() = runTest {
        source.file("a", "Alpha")
        source.file("b", "Bravo")
        val first = library()
        summaryOf(first) { first.addFiles(listOf("a", "b")) }
        first.move(1, 0)
        runCurrent()

        val again = library() // same store: the app restarted
        assertEquals(listOf("Bravo", "Alpha"), again.titles)
        assertTrue(again.state.value.loaded)
    }

    @Test
    fun aFolderAndroidWontKeepSaysSo() = runTest {
        source.refuse += "music"
        val library = library()
        val summary = summaryOf(library) { library.addFolder("music") }
        assertTrue(summary.folderRefused)
        assertTrue(library.state.value.folders.isEmpty())
    }

    @Test
    fun aRefusedPermissionCountsAsUnreadable() = runTest {
        source.file("a", "Alpha")
        source.refuse += "a"
        val library = library()
        val summary = summaryOf(library) { library.addFiles(listOf("a")) }
        assertEquals(ImportSummary(added = 0, alreadyThere = 0, unreadable = 1), summary)
    }
}
