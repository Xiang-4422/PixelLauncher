package com.purride.pixelcore

import android.graphics.Bitmap as AndroidBitmap

/**
 * 不可变的像素位图。
 *
 * 与 [PixelBuffer] 区别：buffer 是 engine 内部每帧重用的可变绘制目标；
 * bitmap 是用户提供的常驻图像源，由 [Image] widget 等通过 blit 写入 buffer。
 *
 * 像素以 ARGB 32-bit 打包整数存储（与 [PixelColor.argb] 兼容）。
 */
public class PixelBitmap(
    public val width: Int,
    public val height: Int,
    public val pixels: IntArray,
) {
    init {
        require(width >= 0) { "width must be >= 0 but was $width" }
        require(height >= 0) { "height must be >= 0 but was $height" }
        require(pixels.size == width * height) {
            "pixels.size=${pixels.size} != width*height=${width * height}"
        }
    }

    public companion object {
        /**
         * 从 Android [Bitmap] 拷贝像素到一个新的 [PixelBitmap]。
         *
         * 调用方负责对源 bitmap 做缩放（[android.graphics.Bitmap.createScaledBitmap]）
         * 或剪裁；本函数按源 bitmap 当前尺寸 1:1 抓取像素。
         */
        public fun fromAndroidBitmap(source: AndroidBitmap): PixelBitmap {
            val w = source.width
            val h = source.height
            val pixels = IntArray(w * h)
            if (w > 0 && h > 0) source.getPixels(pixels, 0, w, 0, 0, w, h)
            return PixelBitmap(width = w, height = h, pixels = pixels)
        }
    }
}
