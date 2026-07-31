package com.purride.pixellauncherv2.launcher

/** 用户选择的主题家族；每个家族都提供独立的日间与夜间配色。 */
enum class LauncherThemeFamily(
    /** 主题变体 ID 使用的稳定前缀。 */
    val idPrefix: String,
    /** 设置页展示的紧凑名称。 */
    val displayLabel: String,
) {
    MIDNIGHT("midnight", "MIDNIGHT"),
    CRT("crt", "CRT"),
    AMBER("amber", "AMBER"),
    GAMEBOY("gameboy", "GAMEBOY"),
    PAPER("paper", "PAPER"),
    ;

}

/** 用户选择的亮暗模式；AUTO 只在当前主题家族内部跟随系统。 */
enum class LauncherThemeMode(
    /** 设置页展示的紧凑名称。 */
    val displayLabel: String,
) {
    AUTO("AUTO"),
    DAY("DAY"),
    NIGHT("NIGHT"),
    ;

    /**
     * 把用户模式解析成实际主题亮度。
     *
     * AUTO 跟随系统暗色；DAY 与 NIGHT 始终分别解析为亮色与暗色。
     */
    fun resolve(systemInDarkMode: Boolean): LauncherThemeBrightness = when (this) {
        AUTO -> if (systemInDarkMode) LauncherThemeBrightness.DARK else LauncherThemeBrightness.LIGHT
        DAY -> LauncherThemeBrightness.LIGHT
        NIGHT -> LauncherThemeBrightness.DARK
    }
}

/** 主题实际采用的亮度，不能保存为用户设置。 */
enum class LauncherThemeBrightness(
    /** 主题变体 ID 中的稳定后缀。 */
    val idSuffix: String,
) {
    LIGHT("day"),
    DARK("night"),
}
