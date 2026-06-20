package com.purride.pixellauncherv2.launcher

object IdleSettings {
    const val DEFAULT_TIMEOUT_SECONDS = 30

    val timeoutOptionsSeconds: List<Int> = listOf(15, 30, 60, 120)

    fun normalizeTimeoutSeconds(seconds: Int): Int {
        return timeoutOptionsSeconds.minByOrNull { option -> kotlin.math.abs(option - seconds) }
            ?: DEFAULT_TIMEOUT_SECONDS
    }

    fun nextTimeoutSeconds(current: Int, direction: Int): Int {
        val normalized = normalizeTimeoutSeconds(current)
        val currentIndex = timeoutOptionsSeconds.indexOf(normalized).takeIf { it >= 0 } ?: 0
        val nextIndex = wrapIndex(currentIndex + direction, timeoutOptionsSeconds.size)
        return timeoutOptionsSeconds[nextIndex]
    }

    fun timeoutLabel(seconds: Int): String {
        return "${normalizeTimeoutSeconds(seconds)}S"
    }

    private fun wrapIndex(index: Int, size: Int): Int {
        if (size <= 0) return 0
        val mod = index % size
        return if (mod < 0) mod + size else mod
    }
}
