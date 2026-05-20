package com.purride.pixelcore

/**
 * 像素颜色——彩色模式下的像素值类型。
 *
 * 存储格式为 ARGB 32-bit（与 Android Bitmap.Config.ARGB_8888 一致）：
 * bits 31-24 = alpha, bits 23-16 = red, bits 15-8 = green, bits 7-0 = blue。
 *
 * value class 保证热路径零 boxing 开销。
 * 不提供预定义颜色常量，也不提供 lerp——颜色插值由 PixelColorTween 负责。
 */
@JvmInline
public value class PixelColor(public val argb: Int) {

    public val alpha: Int get() = (argb ushr 24) and 0xFF
    public val red: Int get() = (argb ushr 16) and 0xFF
    public val green: Int get() = (argb ushr 8) and 0xFF
    public val blue: Int get() = argb and 0xFF

    public companion object {
        public fun fromArgb(a: Int, r: Int, g: Int, b: Int): PixelColor {
            return PixelColor(
                ((a and 0xFF) shl 24) or
                    ((r and 0xFF) shl 16) or
                    ((g and 0xFF) shl 8) or
                    (b and 0xFF),
            )
        }

        public fun fromRgb(r: Int, g: Int, b: Int): PixelColor = fromArgb(0xFF, r, g, b)

        public val Transparent: PixelColor = PixelColor(0)
    }
}
