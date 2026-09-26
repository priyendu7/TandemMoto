package com.tandemmoto.transfer

import com.tandemmoto.playlist.RideEntry

/** How much of the playlist around the current song to keep the partner's songs for (#50). */
data class WindowSettings(val behind: Int = DEFAULT_BEHIND, val ahead: Int = DEFAULT_AHEAD) {
    companion object {
        const val DEFAULT_BEHIND = 2
        const val DEFAULT_AHEAD = 7
        val BEHIND_RANGE = 0..5
        val AHEAD_RANGE = 1..20
    }
}

/**
 * What this phone should hold of the partner's songs: [wanted] in download order (current, then
 * ahead nearest first, then behind), and [delete] for downloaded songs that left the window.
 */
data class WindowPlan(
    val wanted: List<RideEntry>,
    val delete: Set<String>,
    /** Ahead songs kept when free space cut the window short (null: not limited). */
    val aheadLimitedTo: Int? = null,
    /** Not even the current song fits while keeping [SongWindow.HARD_MIN_FREE_BYTES] free. */
    val storageFull: Boolean = false,
    /** The current song only fits by dipping under the floor: delete it once it has played. */
    val currentOnDemand: Boolean = false
)

/**
 * The song window (#50), pure so it's tested on the JVM. Around the current song (playlist
 * positions: [WindowSettings.behind] before, [WindowSettings.ahead] after), the partner's songs
 * this phone has no file for are downloaded; the rest are deleted. Free space decides too: the
 * window shrinks to what fits above [FLOOR_FREE_BYTES], cutting the songs behind first, then the
 * far end ahead. The current song may still come in as long as [HARD_MIN_FREE_BYTES] stays free.
 */
object SongWindow {
    const val FLOOR_FREE_BYTES = 500L * 1024 * 1024
    const val HARD_MIN_FREE_BYTES = 100L * 1024 * 1024

    /**
     * [downloaded]: partner songs stored now and their size. [hasOwnFile]: this phone's own song
     * or its copy of the partner's (#48, #49): never downloaded.
     */
    fun plan(
        ordered: List<RideEntry>,
        currentIndex: Int,
        me: String,
        settings: WindowSettings,
        downloaded: Map<String, Long>,
        hasOwnFile: (String) -> Boolean,
        freeBytes: Long
    ): WindowPlan {
        if (ordered.isEmpty()) return WindowPlan(emptyList(), downloaded.keys)
        val current = currentIndex.coerceIn(ordered.indices)
        fun needs(entry: RideEntry) = entry.owner != me && !hasOwnFile(entry.id)

        // Space downloads may use: what's free above the floor, plus what the window's own
        // stored songs already take (they stay; everything else stored can be deleted).
        val reclaimable = downloaded.values.sum()
        var budget = freeBytes + reclaimable - FLOOR_FREE_BYTES
        val wanted = mutableListOf<RideEntry>()
        fun take(entry: RideEntry): Boolean {
            val size = downloaded[entry.id] ?: entry.sizeBytes
            if (size > budget) return false
            budget -= size
            wanted += entry
            return true
        }

        var storageFull = false
        var onDemand = false
        val currentEntry = ordered[current]
        if (needs(currentEntry)) {
            val size = downloaded[currentEntry.id] ?: currentEntry.sizeBytes
            when {
                size <= budget -> take(currentEntry)
                // Below the floor, but the current song may still come in above the hard minimum.
                freeBytes + reclaimable - size >= HARD_MIN_FREE_BYTES -> {
                    wanted += currentEntry
                    budget = 0
                    onDemand = true
                }
                else -> storageFull = true
            }
        }

        val ahead = (1..settings.ahead).mapNotNull { ordered.getOrNull(current + it) }
        var aheadKept = 0
        var aheadCut = false
        for (entry in ahead) {
            if (!needs(entry)) {
                aheadKept++
                continue
            }
            if (onDemand || storageFull || !take(entry)) {
                aheadCut = true
                break
            }
            aheadKept++
        }
        if (!aheadCut) {
            for (entry in (1..settings.behind).mapNotNull { ordered.getOrNull(current - it) }) {
                if (needs(entry) && !take(entry)) break
            }
        }
        val keep = wanted.map { it.id }.toSet()
        return WindowPlan(
            wanted = wanted,
            delete = downloaded.keys - keep,
            aheadLimitedTo = aheadKept.takeIf { aheadCut },
            storageFull = storageFull,
            currentOnDemand = onDemand
        )
    }
}
