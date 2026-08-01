package com.purride.pixellauncherv2.ui.theme

import com.purride.pixelcore.PixelColor
import com.purride.pixeldesign.ProductPalette
import com.purride.pixeldesign.ProductThemeCatalog
import com.purride.pixellauncherv2.launcher.LauncherThemeBrightness
import com.purride.pixellauncherv2.launcher.LauncherThemeFamily

/** Launcher 业务主题目录，把共享产品色板扩展为应用页面所需的完整语义角色。 */
internal object LauncherThemeCatalog {
    /** 主题家族与亮度到完整 Launcher 运行时主题的映射。 */
    val byVariant: Map<LauncherThemeVariant, LauncherTheme> = buildMap {
        LauncherThemeFamily.entries.forEach { family ->
            LauncherThemeBrightness.entries.forEach { brightness ->
                /** 当前共享产品主题变体对应的 Launcher 查询键。 */
                val variant = LauncherThemeVariant(family = family, brightness = brightness)
                put(variant, buildTheme(ProductThemeCatalog.resolve(family, brightness)))
            }
        }
    }

    /** 从共享六色产品色板派生 Launcher 页面所需的全部业务语义颜色。 */
    private fun buildTheme(palette: ProductPalette): LauncherTheme {
        /** 未点亮像素使用背景向主色轻微插值得到的颜色。 */
        val offPixel = mix(palette.background, palette.primary, 0.06f)
        /** 次级面板使用更明显但仍属于本主题的插值颜色。 */
        val panelSubtle = mix(palette.background, palette.primary, 0.12f)
        return LauncherTheme(
            id = palette.id,
            label = palette.label,
            mode = palette.brightness,
            surface = SurfaceColors(
                bezelColor = palette.background,
                offPixelColor = offPixel,
                panel = palette.background,
                panelSubtle = panelSubtle,
            ),
            text = TextColors(
                primary = palette.primary,
                secondary = palette.secondary,
                muted = palette.muted,
                inverse = palette.background,
            ),
            statusBar = StatusBarColors(
                text = palette.primary,
                mutedText = palette.muted,
                batteryHigh = palette.primary,
                batteryMedium = palette.alert,
                batteryLow = palette.alert,
                searchText = palette.primary,
                searchPlaceholder = palette.muted,
            ),
            drawer = DrawerColors(
                itemText = palette.primary,
                itemTextMuted = palette.muted,
                searchText = palette.primary,
                searchPlaceholder = palette.muted,
            ),
            settings = SettingsColors(
                itemTitle = palette.primary,
                itemValue = palette.secondary,
            ),
            button = ButtonColors(
                text = palette.primary,
                border = palette.outline,
                pressedFill = palette.outline,
                selectedText = palette.background,
                unselectedText = palette.secondary,
                filledSurface = palette.outline,
                filledText = palette.background,
                disabledText = palette.muted,
            ),
            sms = SmsColors(
                sender = palette.primary,
                threadPreview = palette.secondary,
                incomingMessage = palette.primary,
                outgoingMessage = palette.secondary,
                composerText = palette.primary,
                timestamp = palette.muted,
                draftBorder = palette.outline,
                selectionFill = panelSubtle,
                loadingTrack = offPixel,
            ),
            semantic = SemanticColors(
                success = palette.primary,
                warning = palette.alert,
                danger = palette.alert,
                info = palette.secondary,
            ),
        )
    }

    /** 按逐通道线性插值生成 Launcher 内部层级颜色。 */
    private fun mix(from: PixelColor, to: PixelColor, fraction: Float): PixelColor = PixelColor.fromRgb(
        (from.red + (to.red - from.red) * fraction).toInt(),
        (from.green + (to.green - from.green) * fraction).toInt(),
        (from.blue + (to.blue - from.blue) * fraction).toInt(),
    )
}
