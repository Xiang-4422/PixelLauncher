package com.purride.pixelcore

import android.view.View

/**
 * 像素帧宿主契约。
 *
 * 这层只定义“像素帧如何提交给宿主 View”，不定义更高层的页面运行时。
 */
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

    fun setPalette(palette: PixelPalette)

    fun setPixelGapEnabled(enabled: Boolean) = Unit

    fun asView(): View

    fun onHostResume() = Unit

    fun onHostPause() = Unit
}
