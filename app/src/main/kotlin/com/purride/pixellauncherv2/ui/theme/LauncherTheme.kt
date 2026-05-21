package com.purride.pixellauncherv2.ui.theme

import com.purride.pixelcore.PixelColor
import com.purride.pixellauncherv2.render.PixelTheme

/**
 * 新架构颜色主题。
 *
 * 对应旧 [com.purride.pixellauncherv2.render.PixelPalette] 的四色映射到 pixel-engine 三层：
 *
 * | 旧字段          | 新字段                                        |
 * |----------------|----------------------------------------------|
 * | `backgroundColor` | [backgroundColor] → `PixelHostView.backgroundColor` |
 * | `pixelOffColor`   | [pixelGridColor]  → `PixelHostView.pixelGridColor`  |
 * | `pixelOnColor`    | [primaryColor]    → widget 默认前景色          |
 * | `accentColor`     | [accentColor]     → 按钮边框 / 高亮 / 充电闪烁 |
 *
 * [dimColor] 为新增字段，用于次要文字（旧版无对应）。
 */
data class LauncherTheme(
    /** 屏幕外圈 bezel 色，也是 `PixelHostView.backgroundColor`。 */
    val backgroundColor: PixelColor,
    /** 像素格底色（dead pixel），也是 `PixelHostView.pixelGridColor`。 */
    val pixelGridColor: PixelColor,
    /** 主前景色，用于 widget 文字和主要图形元素。 */
    val primaryColor: PixelColor,
    /** 强调色，用于按钮边框、选中高亮和充电动画闪烁位置。 */
    val accentColor: PixelColor,
    /** 次要文字色（时间戳、说明行等）。 */
    val dimColor: PixelColor,
)

/** 5 套预设主题；与旧 [PixelTheme] 枚举一一对应。 */
object LauncherThemes {

    val GREEN_PHOSPHOR = LauncherTheme(
        backgroundColor = PixelColor.fromRgb(0,   0,   0  ),
        pixelGridColor  = PixelColor.fromRgb(8,   37,  13 ),
        primaryColor    = PixelColor.fromRgb(151, 255, 167),
        accentColor     = PixelColor.fromRgb(199, 255, 208),
        dimColor        = PixelColor.fromRgb(80,  160, 90 ),
    )

    val AMBER_CRT = LauncherTheme(
        backgroundColor = PixelColor.fromRgb(8,   3,   0  ),
        pixelGridColor  = PixelColor.fromRgb(46,  25,  5  ),
        primaryColor    = PixelColor.fromRgb(255, 191, 92 ),
        accentColor     = PixelColor.fromRgb(255, 225, 164),
        dimColor        = PixelColor.fromRgb(160, 100, 40 ),
    )

    val ICE_LCD = LauncherTheme(
        backgroundColor = PixelColor.fromRgb(3,   8,   14 ),
        pixelGridColor  = PixelColor.fromRgb(12,  34,  47 ),
        primaryColor    = PixelColor.fromRgb(184, 237, 255),
        accentColor     = PixelColor.fromRgb(235, 248, 255),
        dimColor        = PixelColor.fromRgb(80,  130, 160),
    )

    val MONO_LCD = LauncherTheme(
        backgroundColor = PixelColor.fromRgb(0,   0,   0  ),
        pixelGridColor  = PixelColor.fromRgb(12,  12,  12 ),
        primaryColor    = PixelColor.fromRgb(255, 255, 255),
        accentColor     = PixelColor.fromRgb(255, 255, 255),
        dimColor        = PixelColor.fromRgb(120, 120, 120),
    )

    val NIGHT_MONO = LauncherTheme(
        backgroundColor = PixelColor.fromRgb(0,   0,   0  ),
        pixelGridColor  = PixelColor.fromRgb(20,  20,  20 ),
        primaryColor    = PixelColor.fromRgb(176, 176, 176),
        accentColor     = PixelColor.fromRgb(156, 156, 156),
        dimColor        = PixelColor.fromRgb(80,  80,  80 ),
    )

    /** 将旧 [PixelTheme] 枚举映射到新 [LauncherTheme]。 */
    fun from(pixelTheme: PixelTheme): LauncherTheme = when (pixelTheme) {
        PixelTheme.GREEN_PHOSPHOR -> GREEN_PHOSPHOR
        PixelTheme.AMBER_CRT      -> AMBER_CRT
        PixelTheme.ICE_LCD        -> ICE_LCD
        PixelTheme.MONO_LCD       -> MONO_LCD
        PixelTheme.NIGHT_MONO     -> NIGHT_MONO
    }
}
