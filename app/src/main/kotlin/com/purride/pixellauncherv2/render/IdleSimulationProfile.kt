package com.purride.pixellauncherv2.render

import kotlin.math.max
import kotlin.math.roundToInt

/**
 * 定义 Idle 流体模拟使用的独立网格尺寸。
 *
 * 这层把“模拟分辨率”和“显示逻辑分辨率”解耦，避免高显示分辨率直接放大物理成本。
 */
data class IdleSimulationProfile(
    val width: Int,
    val height: Int,
) {
    companion object {
        const val defaultMaxLongestSide = 64

        fun fromLogicalSize(
            logicalWidth: Int,
            logicalHeight: Int,
            maxLongestSide: Int = defaultMaxLongestSide,
        ): IdleSimulationProfile {
            val safeWidth = logicalWidth.coerceAtLeast(1)
            val safeHeight = logicalHeight.coerceAtLeast(1)
            val safeLongestSide = maxLongestSide.coerceAtLeast(1)
            val longestSide = max(safeWidth, safeHeight)
            if (longestSide <= safeLongestSide) {
                return IdleSimulationProfile(
                    width = safeWidth,
                    height = safeHeight,
                )
            }
            val scale = safeLongestSide.toFloat() / longestSide.toFloat()
            return IdleSimulationProfile(
                width = (safeWidth * scale).roundToInt().coerceAtLeast(1),
                height = (safeHeight * scale).roundToInt().coerceAtLeast(1),
            )
        }
    }
}
