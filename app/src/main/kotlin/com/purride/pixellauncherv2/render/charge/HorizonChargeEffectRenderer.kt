package com.purride.pixellauncherv2.render.charge

import com.purride.pixellauncherv2.render.ChargeIdleEffectRenderer
import com.purride.pixellauncherv2.render.IdleMaskFrame
import kotlin.math.sqrt

object HorizonChargeEffectRenderer : ChargeIdleEffectRenderer {
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
        val totalCells = safeWidth * safeHeight
        val targetVisibleCells = ((totalCells * batteryLevel.coerceIn(0, 100)) / 200).coerceIn(0, totalCells)
        val builder = ChargeIdleMaskBuilder(safeWidth, safeHeight)
        if (targetVisibleCells <= 0) {
            return builder.frame(sequence)
        }
        val magnitude = sqrt((gravityX * gravityX) + (gravityY * gravityY))
        val downX = if (magnitude >= 1e-4f) gravityX / magnitude else 0f
        val downY = if (magnitude >= 1e-4f) gravityY / magnitude else 1f

        val order = ArrayList<Pair<Int, Float>>(totalCells)
        for (y in 0 until safeHeight) {
            for (x in 0 until safeWidth) {
                val projection = ((x + 0.5f) * downX) + ((y + 0.5f) * downY)
                order.add((y * safeWidth + x) to projection)
            }
        }
        order.sortByDescending { it.second }
        for (index in 0 until targetVisibleCells) {
            val cell = order[index].first
            builder.set(cell % safeWidth, cell / safeWidth)
        }
        return builder.frame(sequence)
    }
}
