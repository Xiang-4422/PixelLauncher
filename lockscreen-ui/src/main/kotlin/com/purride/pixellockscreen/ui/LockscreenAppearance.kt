package com.purride.pixellockscreen.ui

import com.purride.pixelcore.PixelShape
import com.purride.pixeldesign.ProductAppearance
import com.purride.pixeldesign.ProductThemeBrightness
import com.purride.pixeldesign.ProductThemeFamily

/** 锁屏一帧渲染使用的完整产品外观，包含已经解析完成的实际日夜亮度。 */
public data class LockscreenAppearance(
    /** 当前逻辑像素形状。 */
    public val pixelShape: PixelShape,
    /** 一个逻辑像素对应的物理像素边长。 */
    public val dotSizePx: Int,
    /** 逻辑像素之间是否显示间隙。 */
    public val pixelGapEnabled: Boolean,
    /** 当前产品主题家族。 */
    public val themeFamily: ProductThemeFamily,
    /** 已由宿主系统配置解析的实际主题亮度。 */
    public val brightness: ProductThemeBrightness,
)

/** 按当前 SystemUI 明暗配置把共享产品设置解析成可直接绘制的锁屏外观。 */
public fun ProductAppearance.resolveLockscreenAppearance(
    /** 当前 SystemUI 是否处于夜间模式。 */
    systemInDarkMode: Boolean,
): LockscreenAppearance = LockscreenAppearance(
    pixelShape = pixelShape,
    dotSizePx = dotSizePx,
    pixelGapEnabled = pixelGapEnabled,
    themeFamily = themeFamily,
    brightness = themeMode.resolve(systemInDarkMode),
)

/** 根据物理宿主尺寸和共享点大小返回当前可绘制的逻辑网格尺寸。 */
internal fun lockscreenLogicalSize(widthPx: Int, heightPx: Int, dotSizePx: Int): Pair<Int, Int> =
    (widthPx / dotSizePx).coerceAtLeast(1) to (heightPx / dotSizePx).coerceAtLeast(1)
