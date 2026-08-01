package com.purride.pixellockscreen.ui

/** 锁屏在非交互显示阶段使用的低信息量 AOD 状态。 */
public data class LockscreenAmbientUiState(
    /** 当前是否只绘制 AOD 必需信息。 */
    public val isAmbient: Boolean = false,
    /** 防烧屏内容在逻辑像素坐标中的水平偏移。 */
    public val burnInOffsetX: Int = 0,
    /** 防烧屏内容在逻辑像素坐标中的垂直偏移。 */
    public val burnInOffsetY: Int = 0,
) {
    /** 拒绝可能把内容移出安全区的异常偏移。 */
    init {
        require(burnInOffsetX in -MAXIMUM_BURN_IN_OFFSET..MAXIMUM_BURN_IN_OFFSET) {
            "lockscreen_ambient_offset_x"
        }
        require(burnInOffsetY in -MAXIMUM_BURN_IN_OFFSET..MAXIMUM_BURN_IN_OFFSET) {
            "lockscreen_ambient_offset_y"
        }
        require(isAmbient || (burnInOffsetX == 0 && burnInOffsetY == 0)) {
            "lockscreen_active_burn_in_offset"
        }
    }

    private companion object {
        /** AOD 内容允许移动的最大逻辑像素距离。 */
        const val MAXIMUM_BURN_IN_OFFSET: Int = 2
    }
}
