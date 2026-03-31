package com.purride.pixelcore

import android.view.View

/**
 * 像素显示视图契约。
 *
 * 它定义了宿主层和具体显示实现之间最基础的交互协议，
 * 包括提交主帧、提交待机遮罩、设置调色板以及逻辑坐标输入回调。
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

    fun submitIdleMask(frame: IdleMaskFrame?) = Unit

    fun setPalette(palette: PixelPalette)

    fun setPixelGapEnabled(enabled: Boolean) = Unit

    fun setIdleContinuousRendering(enabled: Boolean) = Unit

    fun asView(): View

    fun onHostResume() = Unit

    fun onHostPause() = Unit
}
