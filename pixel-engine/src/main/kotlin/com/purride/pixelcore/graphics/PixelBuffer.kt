package com.purride.pixelcore

/**
 * 像素帧缓冲——sealed interface。
 *
 * Phase A 将原来的 class 拆为双实现：
 * - [MonoPixelBuffer]：单通道 ByteArray，每像素一个 [PixelTone] 离散值（原有路径）。
 * - [ColorPixelBuffer]：四通道 IntArray，每像素 ARGB 32-bit（Phase A.3 新增）。
 *
 * 接口只暴露两种实现共用的最小协议，像素级读写由各自子类提供类型安全的 API。
 */
public sealed interface PixelBuffer {
    public val width: Int
    public val height: Int

    public fun clear()

    /**
     * 把 [source] buffer 的内容复制到当前 buffer 的 ([destX], [destY]) 位置。
     *
     * 跨类型 blit（Mono → Color 或反向）会抛 [UnsupportedOperationException]。
     */
    public fun blit(source: PixelBuffer, destX: Int, destY: Int)
}

/**
 * 像素色阶。
 *
 * 第一版只保留三个档位：背景关闭、主内容、强调色。
 * 这套离散值会映射到调色板中的真实颜色。
 */
public enum class PixelTone(public val value: Byte) {
    OFF(0),
    ON(1),
    ACCENT(2),
}
