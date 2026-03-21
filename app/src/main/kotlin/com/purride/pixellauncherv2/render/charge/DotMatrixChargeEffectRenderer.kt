package com.purride.pixellauncherv2.render.charge

import com.purride.pixellauncherv2.render.ChargeIdleEffectRenderer
import com.purride.pixellauncherv2.render.IdleMaskFrame
import kotlin.math.max

object DotMatrixChargeEffectRenderer : ChargeIdleEffectRenderer {
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
        val inset = max(2, safeWidth / 12)
        val cellSize = max(1, minOf(2, safeWidth / 48))
        val spacing = cellSize + 1
        val cols = ((safeWidth - (inset * 2)) / spacing).coerceAtLeast(1)
        val rows = ((safeHeight - (inset * 2)) / spacing).coerceAtLeast(1)
        val total = cols * rows
        val lit = (total * batteryLevel.coerceIn(0, 100)) / 100
        var index = 0
        for (col in 0 until cols) {
            for (row in rows - 1 downTo 0) {
                val x = inset + (col * spacing)
                val y = inset + (row * spacing)
                if (index < lit || (index == lit && (nowUptimeMs / 220L) % 2L == 0L)) {
                    builder.fillRect(x, y, x + cellSize - 1, y + cellSize - 1)
                }
                index += 1
            }
        }
        return builder.frame(sequence)
    }
}
