package com.tandemmoto.player

import android.net.Uri
import androidx.core.net.toUri
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import java.io.IOException
import java.io.InterruptedIOException

/** What [SongDataSource] asks the player about a song. Called on the player's loading thread. */
interface SongAvailability {
    fun find(id: String): SongFileState

    fun decide(id: String): WaitDecision

    /** [id] started or stopped waiting for its file ("Getting song…"). */
    fun waiting(id: String, waiting: Boolean)
}

/**
 * Opens `tandem://song/<id>` (#51): the song's file on this phone ([SongFiles]), or, when it isn't
 * here yet, waits for it ("Getting song…") while the song window fetches it (#50), until
 * [SongAvailability.decide] gives up: then the player skips the song.
 */
@UnstableApi
class SongDataSource(private val upstream: DataSource, private val availability: SongAvailability) :
    DataSource {
    override fun addTransferListener(transferListener: TransferListener) =
        upstream.addTransferListener(transferListener)

    override fun open(dataSpec: DataSpec): Long {
        val uri = dataSpec.uri
        if (uri.scheme != SCHEME) return upstream.open(dataSpec)
        val id = uri.lastPathSegment ?: throw IOException("No song ID")
        var waiting = false
        try {
            while (true) {
                val file = availability.find(id)
                if (file is SongFileState.Ready) {
                    return upstream.open(dataSpec.buildUpon().setUri(file.uri.toUri()).build())
                }
                if (availability.decide(id) == WaitDecision.GiveUp) {
                    throw IOException("song-${id.take(8)} isn't on this phone")
                }
                if (!waiting) {
                    waiting = true
                    availability.waiting(id, true)
                }
                try {
                    Thread.sleep(POLL_MS)
                } catch (e: InterruptedException) {
                    Thread.currentThread().interrupt()
                    throw InterruptedIOException("Stopped waiting for the song")
                }
            }
        } finally {
            if (waiting) availability.waiting(id, false)
        }
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
        upstream.read(buffer, offset, length)

    override fun getUri(): Uri? = upstream.uri

    override fun close() = upstream.close()

    companion object {
        const val SCHEME = "tandem"
        private const val POLL_MS = 250L

        fun uriFor(id: String): Uri = "$SCHEME://song/$id".toUri()
    }
}
