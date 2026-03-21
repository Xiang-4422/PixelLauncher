package com.purride.pixellauncherv2.render.charge

import com.purride.pixellauncherv2.render.ChargeIdleEffectRenderer
import com.purride.pixellauncherv2.render.IdleMaskFrame
import kotlin.math.max

object CascadeChargeEffectRenderer : ChargeIdleEffectRenderer {
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
        val baseHeight = (((safeHeight - 4) * batteryLevel.coerceIn(0, 100)) / 100).coerceAtLeast(0)
        val reservoirTop = (safeHeight - 2 - baseHeight).coerceAtLeast(1)
        if (baseHeight > 0) {
            builder.fillRect(2, reservoirTop, safeWidth - 3, safeHeight - 2)
        }
        val laneCount = 4
        val laneSpacing = (safeWidth - 4) / laneCount.coerceAtLeast(1)
        val phaseBase = (nowUptimeMs / 70L).toInt()
        for (lane in 0 until laneCount) {
            val x = (2 + (lane * laneSpacing) + (laneSpacing / 2)).coerceIn(2, safeWidth - 3)
            val travel = (reservoirTop - 1).coerceAtLeast(1)
            val headY = 1 + ((phaseBase + (lane * 7)) % travel)
            for (trail in 0..2) {
                val y = (headY - trail).coerceAtLeast(1)
                if (y < reservoirTop) {
                    builder.set(x, y)
                }
            }
        }
        return builder.frame(sequence)
    }
}
