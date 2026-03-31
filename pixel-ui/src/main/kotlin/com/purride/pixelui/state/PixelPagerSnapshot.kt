package com.purride.pixelui.state

import com.purride.pixelcore.PixelAxis

/**
 * 分页渲染快照。
 *
 * 渲染层只需要知道当前锚点页、相邻页以及当前可视偏移，
 * 不需要知道完整手势推导过程。
 */
data class PixelPagerSnapshot(
    val axis: PixelAxis,
    val anchorPage: Int,
    val adjacentPage: Int?,
    val pageCount: Int,
    val dragOffsetPx: Float,
)
