package com.purride.pixellauncherv2.render.charge

import com.purride.pixellauncherv2.render.ChargeIdleEffectRenderer
import com.purride.pixellauncherv2.render.IdleMaskFrame
import kotlin.math.max
import kotlin.math.min

object StackChargeEffectRenderer : ChargeIdleEffectRenderer {
    override fun render(
        width: Int,
        height: Int,
        batteryLevel: Int,
        isCharging: Boolean,
        gravityX: Float,
        gravityY: Float,
        nowUptimeMs: Long,
        sequence: Long,
    ): IdleMaskFrame? {
        if (!isCharging) {
            return null
        }
        val safeWidth = width.coerceAtLeast(1)
        val safeHeight = height.coerceAtLeast(1)
        val builder = ChargeIdleMaskBuilder(safeWidth, safeHeight)
        val inset = max(2, safeWidth / 10)
        val layerCount = max(6, min(14, safeHeight / 3))
        val level = batteryLevel.coerceIn(0, 100)
        val filledLayers = (layerCount * level) / 100
        val layerHeight = max(2, (safeHeight - 4) / layerCount)
        val pulse = ((nowUptimeMs / 180L) % 2L).toInt()
        for (layer in 0 until filledLayers) {
            val shrink = layer / 2
            val left = (inset + shrink).coerceAtMost(safeWidth - 2)
            val right = (safeWidth - inset - 1 - shrink).coerceAtLeast(left)
            val bottom = safeHeight - 2 - (layer * layerHeight)
            val top = (bottom - layerHeight + 1).coerceAtLeast(1)
            val animatedRight = if (layer == filledLayers - 1 && pulse == 1) {
                (right - 1).coerceAtLeast(left)
            } else {
                right
            }
            builder.fillRect(left, top, animatedRight, bottom.coerceAtMost(safeHeight - 2))
        }
        return builder.frame(sequence)
    }
}
