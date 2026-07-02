package com.purride.pixellauncherv2.launcher

import kotlin.math.roundToLong

data class MediaPlaybackSnapshot(
    val isActive: Boolean = false,
    val packageName: String = "",
    val title: String = "",
    val artist: String = "",
    val isPlaying: Boolean = false,
    val canPlayPause: Boolean = false,
    val canSkipPrevious: Boolean = false,
    val canSkipNext: Boolean = false,
    val canSeek: Boolean = false,
    val positionMillis: Long = 0L,
    val durationMillis: Long = 0L,
    val positionUpdatedRealtimeMillis: Long = 0L,
    val playbackSpeed: Float = 1f,
    val canToggleFavorite: Boolean = false,
    val isFavorite: Boolean = false,
) {
    val hasTrack: Boolean
        get() = isActive && title.isNotBlank()

    fun positionAt(elapsedRealtimeMillis: Long): Long {
        val base = positionMillis.coerceAtLeast(0L)
        if (!isPlaying || positionUpdatedRealtimeMillis <= 0L || durationMillis <= 0L) {
            return base.coerceAtMost(durationMillis.coerceAtLeast(0L))
        }
        val elapsed = ((elapsedRealtimeMillis - positionUpdatedRealtimeMillis).coerceAtLeast(0L) * playbackSpeed)
            .roundToLong()
        return (base + elapsed).coerceIn(0L, durationMillis)
    }

    fun progressAt(elapsedRealtimeMillis: Long): Float {
        if (durationMillis <= 0L) return 0f
        return (positionAt(elapsedRealtimeMillis).toFloat() / durationMillis.toFloat()).coerceIn(0f, 1f)
    }
}
