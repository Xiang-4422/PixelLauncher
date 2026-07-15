package com.purride.pixelui.internal

/**
 * 在 widgets portal 与 runtime render pipeline 之间传递锚点全局边界的 sibling-artifact 协议。
 *
 * 该类型位于明确的 internal package，不属于第三方稳定 API；公开可见性只用于独立 artifact 编译。
 */
public class PixelAnchoredOverlayLink {
    /** Portal 最近一次绘制记录的全局左坐标。 */
    public var anchorLeft: Int = 0
        private set

    /** Portal 最近一次绘制记录的全局上坐标。 */
    public var anchorTop: Int = 0
        private set

    /** 最近一次 retained layout 的实际锚点宽度。 */
    public var anchorWidth: Int = 0
        private set

    /** 最近一次 retained layout 的实际锚点高度。 */
    public var anchorHeight: Int = 0
        private set

    /** 当前 retained 生命周期内是否已经发布可用锚点几何。 */
    public var hasAnchorBounds: Boolean = false
        private set

    /** 发布同帧 follower 使用的实际锚点几何。 */
    public fun updateAnchorBounds(left: Int, top: Int, width: Int, height: Int) {
        anchorLeft = left
        anchorTop = top
        anchorWidth = width.coerceAtLeast(0)
        anchorHeight = height.coerceAtLeast(0)
        hasAnchorBounds = true
    }

    /** Portal 离开 render tree 时使旧几何失效。 */
    public fun clearAnchorBounds() {
        hasAnchorBounds = false
        anchorWidth = 0
        anchorHeight = 0
    }
}
