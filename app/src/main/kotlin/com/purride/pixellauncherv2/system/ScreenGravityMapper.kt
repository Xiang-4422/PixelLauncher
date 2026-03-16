package com.purride.pixellauncherv2.system

import android.view.Surface

object ScreenGravityMapper {

    fun mapToScreen(
        rawGravityX: Float,
        rawGravityY: Float,
        rawGravityZ: Float,
        rotation: Int,
    ): Pair<Float, Float> {
        // rawGravityZ is currently unused in 2D projection, but kept in signature
        // so the mapper can evolve without touching call sites.
        @Suppress("UNUSED_VARIABLE")
        val _ignoredZ = rawGravityZ

        return when (rotation) {
            Surface.ROTATION_0 -> Pair(-rawGravityX, rawGravityY)
            Surface.ROTATION_90 -> Pair(rawGravityY, rawGravityX)
            Surface.ROTATION_180 -> Pair(rawGravityX, -rawGravityY)
            Surface.ROTATION_270 -> Pair(-rawGravityY, -rawGravityX)
            else -> Pair(-rawGravityX, rawGravityY)
        }
    }
}
