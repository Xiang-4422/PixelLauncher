package com.purride.pixelcore

import android.graphics.Bitmap as AndroidBitmap

/**
 * 不可变的像素位图。
 *
 * 与 [PixelBuffer] 区别：buffer 是 engine 内部每帧重用的可变绘制目标；
 * bitmap 是用户提供的常驻图像源，由 [Image] widget 等通过 blit 写入 buffer。
 *
 * 像素以 ARGB 32-bit 打包整数存储（与 [PixelColor.argb] 兼容）。公开构造器和 [pixels]
 * getter 都使用 defensive copy，引擎内部通过只读约定的 [pixelsUnsafe] 避免逐帧复制。
 */
public class PixelBitmap private constructor(
    /** 位图宽度。 */
    public val width: Int,
    /** 位图高度。 */
    public val height: Int,
    /** 构造时已经独占的内部像素存储。 */
    private val pixelStorage: IntArray,
    /** 区分公开 defensive-copy 构造器的内部所有权标记。 */
    private val ownsStorage: Boolean,
) {
    /** 从调用方数组构造不可变 bitmap，并立即复制输入。 */
    public constructor(width: Int, height: Int, pixels: IntArray) :
        this(width = width, height = height, pixelStorage = pixels.copyOf(), ownsStorage = true)

    /** 返回像素副本，消费者修改该数组不会影响 bitmap。 */
    public val pixels: IntArray
        get() = pixelStorage.copyOf()

    /** 不复制地返回内部像素；仅限同模块只读热路径。 */
    internal val pixelsUnsafe: IntArray
        get() = pixelStorage

    /** ARGB 像素数据占用的确定性字节数。 */
    public val byteSize: Long
        get() = pixelStorage.size.toLong() * Int.SIZE_BYTES.toLong()

    init {
        check(ownsStorage) { "PixelBitmap internal storage must be exclusively owned" }
        require(width >= 0) { "width must be >= 0 but was $width" }
        require(height >= 0) { "height must be >= 0 but was $height" }
        require(width <= PixelResourceSafetyLimits.MaxDimension) {
            "width $width exceeds ${PixelResourceSafetyLimits.MaxDimension}"
        }
        require(height <= PixelResourceSafetyLimits.MaxDimension) {
            "height $height exceeds ${PixelResourceSafetyLimits.MaxDimension}"
        }
        /** 以 Long 计算的期望像素数量。 */
        val expectedSize = width.toLong() * height.toLong()
        require(expectedSize <= PixelResourceSafetyLimits.MaxBitmapPixels) {
            "width*height=$expectedSize exceeds ${PixelResourceSafetyLimits.MaxBitmapPixels}"
        }
        require(expectedSize == pixelStorage.size.toLong()) {
            "pixels.size=${pixelStorage.size} != width*height=$expectedSize"
        }
    }

    /** 读取单个像素而不暴露内部数组。 */
    public fun pixelAt(x: Int, y: Int): Int {
        require(x in 0 until width) { "x=$x is outside bitmap width $width" }
        require(y in 0 until height) { "y=$y is outside bitmap height $height" }
        return pixelStorage[(y * width) + x]
    }

    /** 集中提供 `PixelBitmap` 共享的工厂、常量或无状态辅助入口。 */
    public companion object {
        /**
         * 从 Android [AndroidBitmap] 拷贝像素到一个新的 [PixelBitmap]。
         *
         * 调用方负责对源 bitmap 做缩放或剪裁；本函数按源 bitmap 当前尺寸 1:1 抓取像素。
         */
        public fun fromAndroidBitmap(source: AndroidBitmap): PixelBitmap {
            /** Android bitmap 宽度。 */
            val width = source.width
            /** Android bitmap 高度。 */
            val height = source.height
            /** 在分配前完成的安全面积。 */
            val area = checkedPixelArea(
                width = width,
                height = height,
                maxPixels = PixelResourceSafetyLimits.MaxBitmapPixels,
                label = "Android bitmap",
            )
            /** 由本方法独占且无需再次 defensive copy 的像素数组。 */
            val pixels = IntArray(area)
            source.getPixels(pixels, 0, width, 0, 0, width, height)
            return PixelBitmap(
                width = width,
                height = height,
                pixelStorage = pixels,
                ownsStorage = true,
            )
        }

        /** 从已由解析器独占的数组构造对象，避免内部安全路径重复复制。 */
        internal fun fromOwnedPixels(width: Int, height: Int, pixels: IntArray): PixelBitmap {
            return PixelBitmap(
                width = width,
                height = height,
                pixelStorage = pixels,
                ownsStorage = true,
            )
        }
    }
}
