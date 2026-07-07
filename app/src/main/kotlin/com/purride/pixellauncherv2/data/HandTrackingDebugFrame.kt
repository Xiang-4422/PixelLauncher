package com.purride.pixellauncherv2.data

import android.graphics.Bitmap

internal data class HandTrackingDebugFrame(
    val bitmap: Bitmap,
    val normalizedX: FloatArray?,
    val normalizedY: FloatArray?,
    val label: String,
    val confidence: Float,
) {
    val hasHand: Boolean
        get() = normalizedX != null && normalizedY != null
}
