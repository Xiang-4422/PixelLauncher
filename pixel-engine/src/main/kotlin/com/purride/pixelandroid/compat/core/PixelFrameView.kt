package com.purride.pixelcore

import android.view.View

/**
 * 像素帧宿主契约。
 *
 * 这层只定义"像素帧如何提交给宿主 View"，不定义更高层的页面运行时。
 */
public interface PixelFrameView {
    /** 定义 `InteractionListener` 的调用契约，使 `PixelFrameView` 实现可以在不泄漏具体类型的情况下替换。 */
    public interface InteractionListener {
        /** 处理 `PixelFrameView` 的 `onLogicalTap` 输入或事件，并按消费结果决定后续传播。 */
        public fun onLogicalTap(x: Int, y: Int)
        /** 处理 `PixelFrameView` 的 `onSwipeUp` 输入或事件，并按消费结果决定后续传播。 */
        public fun onSwipeUp()
        /** 处理 `PixelFrameView` 的 `onSwipeDown` 输入或事件，并按消费结果决定后续传播。 */
        public fun onSwipeDown()
        /** 处理 `PixelFrameView` 的 `onSwipeLeft` 输入或事件，并按消费结果决定后续传播。 */
        public fun onSwipeLeft()
        /** 处理 `PixelFrameView` 的 `onSwipeRight` 输入或事件，并按消费结果决定后续传播。 */
        public fun onSwipeRight()
        /** 处理 `PixelFrameView` 的 `onLogicalDragStart` 输入或事件，并按消费结果决定后续传播。 */
        public fun onLogicalDragStart(x: Int, y: Int): Boolean
        /** 处理 `PixelFrameView` 的 `onLogicalDragMove` 输入或事件，并按消费结果决定后续传播。 */
        public fun onLogicalDragMove(x: Int, y: Int): Boolean
        /** 处理 `PixelFrameView` 的 `onLogicalDragEnd` 输入或事件，并按消费结果决定后续传播。 */
        public fun onLogicalDragEnd(x: Int, y: Int, cancelled: Boolean): Boolean
    }

    /** 记录 `PixelFrameView` 的 `interactionListener` 配置或运行值，读取与更新均遵守所属类型约束；写入后由所属对象在下一次状态同步时生效。 */
    public var interactionListener: InteractionListener?

    /** 向 `PixelFrameView` 提交 `submitFrame` 数据或事件，并按所属类型的顺序与所有权规则保存。 */
    public fun submitFrame(pixelBuffer: PixelBuffer, screenProfile: ScreenProfile, backgroundColor: PixelColor)

    /** 更新 `PixelFrameView` 的 `setPixelGapEnabled` 状态，并保持相关边界与派生状态一致。 */
    public fun setPixelGapEnabled(enabled: Boolean): Unit = Unit

    /** 查询 `PixelFrameView` 的 `asView` 派生结果；该读取不会改变已保存状态。 */
    public fun asView(): View

    /** 处理 `PixelFrameView` 的 `onHostResume` 输入或事件，并按消费结果决定后续传播。 */
    public fun onHostResume(): Unit = Unit

    /** 处理 `PixelFrameView` 的 `onHostPause` 输入或事件，并按消费结果决定后续传播。 */
    public fun onHostPause(): Unit = Unit
}
