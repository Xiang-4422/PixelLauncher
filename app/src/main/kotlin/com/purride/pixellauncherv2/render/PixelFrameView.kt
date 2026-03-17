package com.purride.pixellauncherv2.render

import android.view.View

interface PixelFrameView {
    interface InteractionListener {
        fun onLogicalTap(x: Int, y: Int)
        fun onSwipeUp()
        fun onSwipeDown()
        fun onSwipeLeft()
        fun onSwipeRight()
        fun onLogicalDragStart(x: Int, y: Int): Boolean
        fun onLogicalDragMove(x: Int, y: Int): Boolean
        fun onLogicalDragEnd(x: Int, y: Int, cancelled: Boolean): Boolean
    }

    var interactionListener: InteractionListener?

    fun submitFrame(pixelBuffer: PixelBuffer, screenProfile: ScreenProfile, palette: PixelPalette)

    fun submitIdleMask(frame: IdleMaskFrame?) = Unit

    fun setPalette(palette: PixelPalette)

    fun setIdleContinuousRendering(enabled: Boolean) = Unit

    fun asView(): View

    fun onHostResume() = Unit

    fun onHostPause() = Unit
}
