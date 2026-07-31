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
    /** 透明背景按钮的普通文字颜色。 */
    val text: PixelColor,
    /** 控件轮廓与分隔线颜色。 */
    val border: PixelColor,
    /** 分段选择器选中指示块颜色。 */
    val pressedFill: PixelColor,
    /** 选中指示块覆盖区域内的文字颜色。 */
    val selectedText: PixelColor,
    /** 分段选择器中仍可操作的未选中项文字颜色。 */
    val unselectedText: PixelColor,
    /** 实心主操作与实心标签的背景颜色。 */
    val filledSurface: PixelColor,
    /** 实心主操作与实心标签的前景颜色。 */
    val filledText: PixelColor,
    /** 真正不可操作内容的弱化文字颜色。 */
    val disabledText: PixelColor,
)

data class SmsColors(
    /** 联系人、号码和未读来源等消息标题颜色。 */
    val sender: PixelColor,
    /** 会话列表中已读片段与搜索结果摘要颜色。 */
    val threadPreview: PixelColor,
    /** 会话详情中接收消息的正文颜色。 */
    val incomingMessage: PixelColor,
    /** 会话详情中发出消息的正文颜色。 */
    val outgoingMessage: PixelColor,
    /** 搜索框与草稿输入框的正文颜色。 */
    val composerText: PixelColor,
    /** 时间、发送进度与送达状态等元信息颜色。 */
    val timestamp: PixelColor,
    /** 搜索框、草稿框和发送按钮的轮廓颜色。 */
    val draftBorder: PixelColor,
    /** 文本输入选区的背景颜色，必须与输入正文保持可读对比度。 */
    val selectionFill: PixelColor,
    /** 短信加载动画的未激活轨道颜色。 */
    val loadingTrack: PixelColor,
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
