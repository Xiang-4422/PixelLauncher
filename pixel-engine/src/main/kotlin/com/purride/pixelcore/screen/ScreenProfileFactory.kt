package com.purride.pixelcore

/**
 * 像素屏幕配置工厂。
 *
 * 负责把物理像素尺寸换算成逻辑像素网格，
 * 并集中维护当前引擎支持的点阵尺寸选项。
 */
public object ScreenProfileFactory {

    /** 定义 `ScreenProfileFactory` 布局中的 `defaultDotSizePx` 逻辑像素度量。 */
    public const val defaultDotSizePx: Int = 12
    /** 定义 `ScreenProfileFactory` 布局中的 `supportedDotSizePxOptions` 逻辑像素度量。 */
    public val supportedDotSizePxOptions: List<Int> = listOf(7, 8, 10, 12, 14, 16)

    /** 创建 `ScreenProfileFactory` 所需的新对象，并在返回前建立其初始不变量。 */
    public fun create(
        widthPx: Int,
        heightPx: Int,
        dotSizePx: Int = defaultDotSizePx,
        pixelShape: PixelShape = PixelShape.SQUARE,
    ): ScreenProfile {
        val safeDotSizePx = dotSizePx.coerceAtLeast(1)
        val logicalWidth = (widthPx.coerceAtLeast(1) / safeDotSizePx).coerceAtLeast(1)
        val logicalHeight = (heightPx.coerceAtLeast(1) / safeDotSizePx).coerceAtLeast(1)
        return ScreenProfile(
            logicalWidth = logicalWidth,
            logicalHeight = logicalHeight,
            dotSizePx = safeDotSizePx,
            pixelShape = pixelShape,
        )
    }

    /**
     * 当前分辨率切换候选项。
     *
     * 第一版先直接返回全量支持列表，后续如果要按屏幕尺寸、
     * 性能档位或设备能力做裁剪，可以在这里集中演进。
     */
    public fun resolutionOptions(currentProfile: ScreenProfile?): List<Int> {
        return supportedDotSizePxOptions
    }
}
