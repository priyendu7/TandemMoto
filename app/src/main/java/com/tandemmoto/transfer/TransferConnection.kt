package com.tandemmoto.transfer

import com.tandemmoto.link.Endpoint
import com.tandemmoto.link.FrameConnection
import com.tandemmoto.link.FrameTransport
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The song transfer's own connection (#50), next to the command channel so a big song never
 * delays the heartbeat or the playlist: same rule (the group owner listens on [PORT], the other
 * phone connects), reopened while the group lasts. Carries [SongChunk]s both ways.
 */
class TransferConnection(
    private val transport: FrameTransport,
    private val scope: CoroutineScope,
    private val log: (String) -> Unit = {}
) {
    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    // No extra buffer: a slow receiver holds the reader back, which holds TCP back.
    private val _chunks = MutableSharedFlow<SongChunk>()
    val chunks: SharedFlow<SongChunk> = _chunks.asSharedFlow()

    private var job: Job? = null
    private var endpoint: Endpoint? = null

    @Volatile
    private var connection: FrameConnection? = null

    fun open(endpoint: Endpoint) {
        if (this.endpoint == endpoint && job?.isActive == true) return
        close()
        this.endpoint = endpoint
        job = scope.launch { run(endpoint) }
    }

    fun close() {
        job?.cancel()
        job = null
        endpoint = null
        connection?.close()
        connection = null
        _ready.value = false
    }

    /** False when there's no connection or it broke. */
    suspend fun send(chunk: SongChunk): Boolean {
        val current = connection ?: return false
        return try {
            current.send(chunk.encode())
            true
        } catch (e: IOException) {
            false
        }
    }

    private suspend fun run(endpoint: Endpoint) {
        while (true) {
            val opened = try {
                if (endpoint.isGroupOwner) {
                    transport.accept(PORT)
                } else {
                    transport.connect(endpoint.ownerHost ?: throw IOException("No owner"), PORT)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                delay(RETRY_MS)
                continue
            }
            connection = opened
            _ready.value = true
            log("Transfer connection open")
            try {
                while (true) {
                    val frame = opened.receive() ?: break
                    SongChunk.decode(frame)?.let { _chunks.emit(it) }
                }
            } catch (e: IOException) {
                // Broken: reconnect below.
            } finally {
                opened.close()
                connection = null
                _ready.value = false
            }
            log("Transfer connection closed")
            delay(RETRY_MS)
        }
    }

    companion object {
        const val PORT = 48153
        const val RETRY_MS = 500L
    }
}
