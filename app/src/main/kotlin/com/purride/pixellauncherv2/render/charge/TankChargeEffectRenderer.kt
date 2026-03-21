package com.purride.pixellauncherv2.render.charge

import com.purride.pixellauncherv2.render.ChargeIdleEffectRenderer
import com.purride.pixellauncherv2.render.IdleMaskFrame
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.sin

object TankChargeEffectRenderer : ChargeIdleEffectRenderer {
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
        val left = max(2, safeWidth / 6)
        val right = (safeWidth - left - 1).coerceAtLeast(left + 2)
        val top = max(2, safeHeight / 8)
        val bottom = (safeHeight - top - 1).coerceAtLeast(top + 4)
        builder.fillRect(left, top, right, top)
        builder.fillRect(left, bottom, right, bottom)
        builder.fillRect(left, top, left, bottom)
        builder.fillRect(right, top, right, bottom)
        val fillHeight = (((bottom - top - 1) * batteryLevel.coerceIn(0, 100)) / 100).coerceAtLeast(0)
        val phase = nowUptimeMs / 260.0
        for (x in (left + 1) until right) {
            val normalized = (x - left).toDouble() / (right - left).coerceAtLeast(1).toDouble()
            val wave = ((sin((normalized * PI * 2) + phase) + 1.0) * 0.5).toInt()
            val liquidTop = (bottom - fillHeight + 1 + wave).coerceIn(top + 1, bottom)
            for (y in liquidTop..(bottom - 1)) {
                builder.set(x, y)
            }
        }
        return builder.frame(sequence)
    }
}
