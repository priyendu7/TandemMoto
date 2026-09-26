package com.tandemmoto.library

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import androidx.core.net.toUri
import java.io.FilterInputStream
import java.io.InputStream

/**
 * [SongSource] on Android's storage access framework: files and folders the user picked, read
 * through the permission the picker granted. No storage permission is needed.
 */
class AndroidSongSource(context: Context) : SongSource {
    private val context = context.applicationContext
    private val resolver: ContentResolver = this.context.contentResolver

    override suspend fun read(uri: String): SongFile? {
        val parsed = uri.toUri()
        val tags = readTags(parsed) ?: return null
        var size = 0L
        val id = resolver.openInputStream(parsed)?.use { input ->
            Fingerprint.of(CountingStream(input) { size += it })
        } ?: return null
        val title = tags.title?.takeIf { it.isNotBlank() }
            ?: (displayName(uri) ?: parsed.lastPathSegment)
                ?.substringBeforeLast('.')?.takeIf { it.isNotBlank() }
            ?: return null
        return SongFile(
            id,
            title.trim(),
            tags.artist?.trim()?.takeIf {
                it.isNotBlank()
            },
            tags.durationMs,
            size
        )
    }

    private data class Tags(val title: String?, val artist: String?, val durationMs: Long)

    /** Null when Android can't find an audio track with a duration: not something it can play. */
    private fun readTags(uri: Uri): Tags? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            val hasAudio = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO)
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()
            if (hasAudio != "yes" || duration == null || duration <= 0) {
                null
            } else {
                Tags(
                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE),
                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST),
                    duration
                )
            }
        } catch (e: RuntimeException) {
            null // IllegalArgumentException / RuntimeException: unreadable or unsupported
        } finally {
            runCatching { retriever.release() }
        }
    }

    override suspend fun listFolder(folder: String): List<String> {
        val tree = folder.toUri()
        val found = mutableListOf<String>()
        val folders = ArrayDeque(listOf(DocumentsContract.getTreeDocumentId(tree)))
        while (folders.isNotEmpty()) {
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(
                tree,
                folders.removeFirst()
            )
            runCatching {
                resolver.query(
                    children,
                    arrayOf(
                        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                        DocumentsContract.Document.COLUMN_MIME_TYPE
                    ),
                    null,
                    null,
                    null
                )?.use { cursor ->
                    while (cursor.moveToNext()) {
                        val id = cursor.getString(0)
                        val mime = cursor.getString(1).orEmpty()
                        when {
                            mime == DocumentsContract.Document.MIME_TYPE_DIR -> folders += id
                            mime.startsWith("audio/") ->
                                found +=
                                    DocumentsContract.buildDocumentUriUsingTree(tree, id).toString()
                        }
                    }
                }
            }
        }
        return found.sorted()
    }

    override fun displayName(uri: String): String? = runCatching {
        resolver.query(uri.toUri(), arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { if (it.moveToFirst()) it.getString(0) else null }
    }.getOrNull()

    override fun keepAccess(uri: String): Boolean = runCatching {
        resolver.takePersistableUriPermission(uri.toUri(), Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }.isSuccess

    override fun releaseAccess(uri: String) {
        runCatching {
            resolver.releasePersistableUriPermission(
                uri.toUri(),
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
    }

    override fun heldAccessCount(): Int = resolver.persistedUriPermissions.size

    override val maxAccessCount: Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) 512 else 128

    override suspend fun exists(uri: String): Boolean = runCatching {
        resolver.openFileDescriptor(uri.toUri(), "r")?.use { true } ?: false
    }.getOrDefault(false)

    /** Counts bytes as they're read, so the size needs no second pass over the file. */
    private class CountingStream(input: InputStream, private val onRead: (Int) -> Unit) :
        FilterInputStream(input) {
        override fun read(): Int = super.read().also { if (it >= 0) onRead(1) }

        override fun read(b: ByteArray, off: Int, len: Int): Int =
            super.read(b, off, len).also { if (it > 0) onRead(it) }
    }
}
