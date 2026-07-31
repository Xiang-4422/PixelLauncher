package com.purride.pixellauncherv2.ui.theme

import com.purride.pixelcore.PixelColor
import com.purride.pixellauncherv2.launcher.LauncherThemeBrightness
import com.purride.pixellauncherv2.launcher.LauncherThemeFamily
import com.purride.pixellauncherv2.ui.text.LauncherTypography

data class LauncherTheme(
    val id: String,
    val label: String,
    val mode: LauncherThemeBrightness,
    val surface: SurfaceColors,
    val text: TextColors,
    val statusBar: StatusBarColors,
    val drawer: DrawerColors,
    val settings: SettingsColors,
    val button: ButtonColors,
    val sms: SmsColors,
    val semantic: SemanticColors,
    /** 当前字体选择以及供组件显式覆盖字号的入口。 */
    val typography: LauncherTypography = LauncherTypography.Default,
)

data class SurfaceColors(
    val bezelColor: PixelColor,
    val offPixelColor: PixelColor,
    val panel: PixelColor,
    val panelSubtle: PixelColor,
)

data class TextColors(
    val primary: PixelColor,
    val secondary: PixelColor,
    val muted: PixelColor,
    val inverse: PixelColor,
)

data class StatusBarColors(
    val text: PixelColor,
    val mutedText: PixelColor,
    val batteryHigh: PixelColor,
    val batteryMedium: PixelColor,
    val batteryLow: PixelColor,
    val searchText: PixelColor,
    val searchPlaceholder: PixelColor,
)

data class DrawerColors(
    val itemText: PixelColor,
    val itemTextMuted: PixelColor,
    val searchText: PixelColor,
    val searchPlaceholder: PixelColor,
)

data class SettingsColors(
    val itemTitle: PixelColor,
    val itemValue: PixelColor,
)

data class ButtonColors(
    val text: PixelColor,
    val border: PixelColor,
    val pressedFill: PixelColor,
    val disabledText: PixelColor,
)

data class SmsColors(
    val sender: PixelColor,
    val timestamp: PixelColor,
    val body: PixelColor,
    val draftBorder: PixelColor,
)

data class SemanticColors(
    val success: PixelColor,
    val warning: PixelColor,
    val danger: PixelColor,
    val info: PixelColor,
)

/** 唯一标识一个主题家族的具体亮度变体。 */
internal data class LauncherThemeVariant(
    /** 主题家族。 */
    val family: LauncherThemeFamily,
    /** 家族内部实际生效的亮度。 */
    val brightness: LauncherThemeBrightness,
)

/** 提供 Launcher 内置主题家族的具体亮度变体。 */
object LauncherThemes {
    /** 返回指定主题家族与亮度对应的完整运行时主题。 */
    fun resolve(
        family: LauncherThemeFamily,
        brightness: LauncherThemeBrightness,
    ): LauncherTheme {
        /** 当前主题变体的稳定缓存键。 */
        val variant = LauncherThemeVariant(family = family, brightness = brightness)
        return LauncherThemeCatalog.byVariant.getValue(variant)
    }
}
