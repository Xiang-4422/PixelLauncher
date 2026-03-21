package com.purride.pixellauncherv2.render.charge

internal class ChargeIdleMaskBuilder(
    val width: Int,
    val height: Int,
) {
    private val safeWidth = width.coerceAtLeast(1)
    private val safeHeight = height.coerceAtLeast(1)
    private val mask = ByteArray(safeWidth * safeHeight)

    fun set(x: Int, y: Int) {
        if (x !in 0 until safeWidth || y !in 0 until safeHeight) {
            return
        }
        mask[(y * safeWidth) + x] = 0x7F
    }

    fun fillRect(left: Int, top: Int, rightInclusive: Int, bottomInclusive: Int) {
        for (y in top..bottomInclusive) {
            for (x in left..rightInclusive) {
                set(x, y)
            }
        }
    }

    fun frame(sequence: Long) = com.purride.pixellauncherv2.render.IdleMaskFrame(
        sequence = sequence,
        width = safeWidth,
        height = safeHeight,
        mask = mask,
    )
}
