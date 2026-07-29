package com.purride.pixelshowcase

import com.purride.pixelcore.PixelColor
import com.purride.pixelui.PixelColorScheme
import com.purride.pixelui.PixelThemeBrightness
import com.purride.pixelui.PixelThemeTokens

/**
 * 五套"整机气质"：一套配色同时驱动应用自绘六色与引擎组件的完整
 * color scheme——点一下，整台机器换一种年代。
 */
enum class ShowcasePalette(
    val label: String,
    val subtitle: String,
    val background: PixelColor,
    val title: PixelColor,
    val dim: PixelColor,
    val faint: PixelColor,
    val border: PixelColor,
    val alert: PixelColor,
) {
    MIDNIGHT(
        label = "MIDNIGHT",
        subtitle = "DEEP BLUE",
        background = PixelColor.fromRgb(10, 14, 26),
        title = PixelColor.fromRgb(236, 244, 255),
        dim = PixelColor.fromRgb(140, 165, 200),
        faint = PixelColor.fromRgb(80, 100, 130),
        border = PixelColor.fromRgb(70, 95, 130),
        alert = PixelColor.fromRgb(216, 72, 56),
    ),
    CRT(
        label = "CRT GREEN",
        subtitle = "PHOSPHOR",
        background = PixelColor.fromRgb(3, 10, 4),
        title = PixelColor.fromRgb(130, 255, 130),
        dim = PixelColor.fromRgb(70, 185, 70),
        faint = PixelColor.fromRgb(36, 110, 40),
        border = PixelColor.fromRgb(32, 100, 36),
        alert = PixelColor.fromRgb(255, 200, 60),
    ),
    AMBER(
        label = "AMBER",
        subtitle = "OLD TERMINAL",
        background = PixelColor.fromRgb(14, 8, 2),
        title = PixelColor.fromRgb(255, 190, 60),
        dim = PixelColor.fromRgb(198, 140, 42),
        faint = PixelColor.fromRgb(118, 84, 26),
        border = PixelColor.fromRgb(106, 76, 24),
        alert = PixelColor.fromRgb(255, 96, 60),
    ),
    GAMEBOY(
        label = "GAMEBOY",
        subtitle = "FOUR GREENS",
        background = PixelColor.fromRgb(15, 56, 15),
        title = PixelColor.fromRgb(155, 188, 15),
        dim = PixelColor.fromRgb(139, 172, 15),
        faint = PixelColor.fromRgb(48, 98, 48),
        border = PixelColor.fromRgb(48, 98, 48),
        alert = PixelColor.fromRgb(155, 188, 15),
    ),
    PAPER(
        label = "PAPER",
        subtitle = "E-INK LOOK",
        background = PixelColor.fromRgb(233, 228, 214),
        title = PixelColor.fromRgb(24, 22, 20),
        dim = PixelColor.fromRgb(84, 80, 74),
        faint = PixelColor.fromRgb(152, 146, 136),
        border = PixelColor.fromRgb(122, 117, 108),
        alert = PixelColor.fromRgb(178, 42, 32),
    ),
    ;

    /**
     * 引擎组件走的 token 树：语义角色全部从本套六色推导，中间调
     * 用背景与前景插值生成，保证任何一套都不会出现"外来色"。
     */
    val engineTokens: PixelThemeTokens by lazy {
        val base = if (this == PAPER) PixelThemeTokens.Light else PixelThemeTokens.Dark
        base.copy(
            brightness = if (this == PAPER) PixelThemeBrightness.Light else PixelThemeBrightness.Dark,
            colors = base.colors.copy(
                background = background,
                onBackground = title,
                surface = background,
                onSurface = title,
                surfaceVariant = mix(background, title, 0.12f),
                onSurfaceVariant = dim,
                outline = border,
                outlineVariant = faint,
                primary = title,
                onPrimary = background,
                danger = alert,
                onDanger = background,
                warning = alert,
                onWarning = background,
                disabled = faint,
                onDisabled = mix(background, faint, 0.5f),
                inactive = faint,
                track = mix(background, title, 0.18f),
                focus = title,
                selection = dim,
            ),
        )
    }

    private companion object {
        /** 两色线性插值：t=0 取 from，t=1 取 to。 */
        fun mix(from: PixelColor, to: PixelColor, t: Float): PixelColor = PixelColor.fromRgb(
            (from.red + (to.red - from.red) * t).toInt(),
            (from.green + (to.green - from.green) * t).toInt(),
            (from.blue + (to.blue - from.blue) * t).toInt(),
        )
    }
}
