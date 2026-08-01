package com.purride.pixellockscreen.ui

import com.purride.pixelcore.PixelShape
import com.purride.pixelcore.PixelBitmapFont
import com.purride.pixelcore.PixelTextRasterizer
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
    /** 用户当前选择的完整产品字体栅格器。 */
    public val defaultTextRasterizer: PixelTextRasterizer = PixelBitmapFont.Default,
    /** 同家族紧凑信息和认证控件使用的产品字体栅格器。 */
    public val chromeTextRasterizer: PixelTextRasterizer = defaultTextRasterizer,
)

/** 按当前 SystemUI 明暗配置把共享产品设置解析成可直接绘制的锁屏外观。 */
public fun ProductAppearance.resolveLockscreenAppearance(
    /** 当前 SystemUI 是否处于夜间模式。 */
    systemInDarkMode: Boolean,
    /** 已由宿主异步准备的用户字体。 */
    defaultTextRasterizer: PixelTextRasterizer = PixelBitmapFont.Default,
    /** 已由宿主异步准备的紧凑文本字体。 */
    chromeTextRasterizer: PixelTextRasterizer = defaultTextRasterizer,
): LockscreenAppearance = LockscreenAppearance(
    pixelShape = pixelShape,
    dotSizePx = dotSizePx,
    pixelGapEnabled = pixelGapEnabled,
    themeFamily = themeFamily,
    brightness = themeMode.resolve(systemInDarkMode),
    defaultTextRasterizer = defaultTextRasterizer,
    chromeTextRasterizer = chromeTextRasterizer,
)

/** 根据物理宿主尺寸和共享点大小返回当前可绘制的逻辑网格尺寸。 */
internal fun lockscreenLogicalSize(widthPx: Int, heightPx: Int, dotSizePx: Int): Pair<Int, Int> =
    (widthPx / dotSizePx).coerceAtLeast(1) to (heightPx / dotSizePx).coerceAtLeast(1)
