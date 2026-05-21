package com.purride.pixelcore

import android.view.View

/**
 * 像素帧宿主契约。
 *
 * 这层只定义"像素帧如何提交给宿主 View"，不定义更高层的页面运行时。
 */
public interface PixelFrameView {
    public interface InteractionListener {
        public fun onLogicalTap(x: Int, y: Int)
        public fun onSwipeUp()
        public fun onSwipeDown()
        public fun onSwipeLeft()
        public fun onSwipeRight()
        public fun onLogicalDragStart(x: Int, y: Int): Boolean
        public fun onLogicalDragMove(x: Int, y: Int): Boolean
        public fun onLogicalDragEnd(x: Int, y: Int, cancelled: Boolean): Boolean
    }

    public var interactionListener: InteractionListener?

    public fun submitFrame(pixelBuffer: PixelBuffer, screenProfile: ScreenProfile, backgroundColor: PixelColor)

    public fun setPixelGapEnabled(enabled: Boolean): Unit = Unit

    public fun asView(): View

    public fun onHostResume(): Unit = Unit

    public fun onHostPause(): Unit = Unit
}
