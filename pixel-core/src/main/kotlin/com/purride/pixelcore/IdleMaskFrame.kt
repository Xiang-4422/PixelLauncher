package com.purride.pixelcore

/**
 * 待机遮罩帧。
 *
 * 它描述的是一张和逻辑屏幕坐标对齐的单通道遮罩图，
 * 用于在显示层叠加待机动效或充电动效。
 */
data class IdleMaskFrame(
    val sequence: Long,
    val width: Int,
    val height: Int,
    val mask: ByteArray,
)
