package com.tandemmoto.library

import kotlin.math.abs

/**
 * When two songs count as the same (#48): the same file (fingerprint), or the same track in a
 * different file, e.g. an MP3 and a FLAC of one song: same title and artist, ignoring case and
 * spacing, and a duration within [DURATION_TOLERANCE_MS]. A live and a studio version differ in
 * length, so they stay separate.
 */
object Duplicates {
    const val DURATION_TOLERANCE_MS = 2_000L

    fun sameSong(a: Song, b: Song): Boolean = a.id == b.id || sameTrack(a, b)

    fun sameTrack(a: Song, b: Song): Boolean = normalize(a.title) == normalize(b.title) &&
        normalize(a.artist.orEmpty()) == normalize(b.artist.orEmpty()) &&
        abs(a.durationMs - b.durationMs) <= DURATION_TOLERANCE_MS

    /** The existing song [candidate] duplicates, or null. */
    fun findIn(existing: Collection<Song>, candidate: Song): Song? =
        existing.firstOrNull { sameSong(it, candidate) }

    private fun normalize(text: String) = text.trim().lowercase().replace(Regex("\\s+"), " ")
}
