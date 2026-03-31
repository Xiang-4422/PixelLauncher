package com.purride.pixelui.state

/**
 * 通用分页状态。
 *
 * `currentPage` 表示最终落位页面，
 * `pageOffsetFraction` 表示当前拖动或吸附过程中的中间偏移。
 */
data class PixelPagerState(
    val currentPage: Int = 0,
    val pageOffsetFraction: Float = 0f,
)
