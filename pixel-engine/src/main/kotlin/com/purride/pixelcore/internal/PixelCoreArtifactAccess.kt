package com.purride.pixelcore.internal

import com.purride.pixelcore.PixelBitmap
import com.purride.pixelcore.PixelBitmapFont
import com.purride.pixelcore.PixelResourceSafetyLimits
import com.purride.pixelcore.PixelStyledTextRasterizer
import com.purride.pixelcore.PixelTextRasterizer
import com.purride.pixelcore.readBoundedBytes
import com.purride.pixelcore.requireSha256
import java.io.InputStream

/**
 * Pixel SDK 内部访问 core 热路径与安全原语的非稳定入口。
 *
 * 该对象位于明确的 `internal` package，不属于消费者稳定 API；引擎内部各层通过它复用
 * bitmap 与字形转换逻辑，避免热路径复制。
 */
public object PixelCoreArtifactAccess {
    /** manifest、catalog 或 sprite JSON 的最大安全字符预算。 */
    public val maxJsonChars: Int
        get() = PixelResourceSafetyLimits.MaxJsonChars

    /** 单个 bitmap 编码输入的最大安全字节数。 */
    public val maxBitmapEncodedBytes: Int
        get() = PixelResourceSafetyLimits.MaxBitmapEncodedBytes

    /** bitmap、glyph 或 sprite 单轴的最大安全像素尺寸。 */
    public val maxDimension: Int
        get() = PixelResourceSafetyLimits.MaxDimension

    /** 单张 bitmap 解码后的最大安全像素数量。 */
    public val maxBitmapPixels: Long
        get() = PixelResourceSafetyLimits.MaxBitmapPixels

    /**
     * 在 [maxBytes] 预算内读取输入，超过上限时立即失败而不继续分配。
     */
    public fun readBoundedBytes(input: InputStream, maxBytes: Int, label: String): ByteArray {
        return input.readBoundedBytes(maxBytes, label)
    }

    /** 校验 [bytes] 是否匹配可选的规范 SHA-256。 */
    public fun requireSha256(bytes: ByteArray, expected: String?, label: String) {
        bytes.requireSha256(expected, label)
    }

    /**
     * 返回不可变 [PixelBitmap] 的 SDK 内部只读像素存储。
     *
     * 调用方不得修改返回数组或向消费者暴露其引用；该入口只为避免渲染热路径 defensive copy。
     */
    public fun pixelsUnsafe(bitmap: PixelBitmap): IntArray = bitmap.pixelsUnsafe

    /**
     * 对 core 自带栅格器执行无拼接的相邻文本测量。
     *
     * 返回 `null` 表示第三方实现不支持该优化，调用方必须回退到公开的 [PixelTextRasterizer.measureText]。
     */
    public fun measureAdjacentText(
        rasterizer: PixelTextRasterizer,
        first: String,
        second: String,
    ): Int? {
        return when (rasterizer) {
            is PixelBitmapFont -> rasterizer.measureAdjacentText(first, second)
            is PixelStyledTextRasterizer -> rasterizer.measureAdjacentText(first, second)
            else -> null
        }
    }
}
