package com.tandemmoto.library

import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowMediaMetadataRetriever
import org.robolectric.shadows.util.DataSource

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AndroidSongSourceTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val source = AndroidSongSource(context)

    private fun song(name: String, vararg tags: Pair<Int, String>): Uri {
        val file = File(context.cacheDir, name).apply { writeBytes("audio of $name".toByteArray()) }
        val uri = Uri.fromFile(file)
        val data = DataSource.toDataSource(context, uri)
        tags.forEach { (key, value) -> ShadowMediaMetadataRetriever.addMetadata(data, key, value) }
        return uri
    }

    private val audio = arrayOf(
        MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO to "yes",
        MediaMetadataRetriever.METADATA_KEY_DURATION to "225000"
    )

    @Test
    fun readsTagsSizeAndFingerprint() = runTest {
        val uri = song(
            "tagged.mp3",
            *audio,
            MediaMetadataRetriever.METADATA_KEY_TITLE to "Highway Star",
            MediaMetadataRetriever.METADATA_KEY_ARTIST to "Deep Purple"
        )
        val read = source.read(uri.toString())!!
        assertEquals("Highway Star", read.title)
        assertEquals("Deep Purple", read.artist)
        assertEquals(225_000L, read.durationMs)
        assertEquals("audio of tagged.mp3".length.toLong(), read.sizeBytes)
        assertEquals(64, read.id.length)
    }

    @Test
    fun withoutTagsTheFileNameIsTheTitle() = runTest {
        val read = source.read(song("My Ride Song.mp3", *audio).toString())!!
        assertEquals("My Ride Song", read.title)
        assertNull(read.artist)
    }

    @Test
    fun aFileWithoutAudioIsNotASong() = runTest {
        val uri = song("notes.txt", MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO to "no")
        assertNull(source.read(uri.toString()))
    }
}
