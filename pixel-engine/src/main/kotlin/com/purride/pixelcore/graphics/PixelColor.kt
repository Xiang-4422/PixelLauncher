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
public value class PixelColor(/** 记录 `PixelColor` 的 `argb` 配置或运行值，读取与更新均遵守所属类型约束。 */ public val argb: Int) {

    /** 记录 `PixelColor` 的 `alpha` 配置或运行值，读取与更新均遵守所属类型约束。 */
    public val alpha: Int get() = (argb ushr 24) and 0xFF
    /** 记录 `PixelColor` 的 `red` 配置或运行值，读取与更新均遵守所属类型约束。 */
    public val red: Int get() = (argb ushr 16) and 0xFF
    /** 记录 `PixelColor` 的 `green` 配置或运行值，读取与更新均遵守所属类型约束。 */
    public val green: Int get() = (argb ushr 8) and 0xFF
    /** 记录 `PixelColor` 的 `blue` 配置或运行值，读取与更新均遵守所属类型约束。 */
    public val blue: Int get() = argb and 0xFF

    /** 集中提供 `PixelColor` 共享的工厂、常量或无状态辅助入口。 */
    public companion object {
        /** 从调用方输入解析或构造 `PixelColor`，无效输入按声明契约拒绝。 */
        public fun fromArgb(a: Int, r: Int, g: Int, b: Int): PixelColor {
            return PixelColor(
                ((a and 0xFF) shl 24) or
                    ((r and 0xFF) shl 16) or
                    ((g and 0xFF) shl 8) or
                    (b and 0xFF),
            )
        }

        /** 从调用方输入解析或构造 `PixelColor`，无效输入按声明契约拒绝。 */
        public fun fromRgb(r: Int, g: Int, b: Int): PixelColor = fromArgb(0xFF, r, g, b)

        /** 提供 `PixelColor` 的 `Transparent` 稳定默认值或常量。 */
        public val Transparent: PixelColor = PixelColor(0)
        /** 提供 `PixelColor` 的 `Black` 稳定默认值或常量。 */
        public val Black: PixelColor = fromRgb(0, 0, 0)
        /** 提供 `PixelColor` 的 `White` 稳定默认值或常量。 */
        public val White: PixelColor = fromRgb(255, 255, 255)
    }
}
