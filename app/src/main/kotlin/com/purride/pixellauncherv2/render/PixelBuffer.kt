package com.purride.pixellauncherv2.render

class PixelBuffer(
    val width: Int,
    val height: Int,
    val pixels: ByteArray = ByteArray(width * height),
) {

    fun clear(value: Byte = 0) {
        pixels.fill(value)
    }

    fun setPixel(x: Int, y: Int, value: Byte = 1) {
        if (x !in 0 until width || y !in 0 until height) {
            return
        }

        pixels[(y * width) + x] = value
    }

    fun getPixel(x: Int, y: Int): Byte {
        if (x !in 0 until width || y !in 0 until height) {
            return 0
        }
        return pixels[(y * width) + x]
    }

    companion object {
        const val OFF: Byte = 0
        const val ON: Byte = 1
        const val ACCENT: Byte = 2
    }
}
