package com.purride.pixellauncherv2.launcher

enum class PixelTheme(
    val fileName: String,
    val displayLabel: String,
) {
    DAY("day.json", "DAY"),
    NIGHT("night.json", "NIGHT"),

    // AUTO is a setting value, not a real theme file; it resolves to DAY/NIGHT via
    // [resolve]. fileName is a defensive fallback should it ever reach file loading
    // unresolved.
    AUTO("day.json", "AUTO"),
    ;

    /**
     * 把"设置值"解析成实际生效的主题。
     *
     * AUTO 跟随系统暗色：系统暗色 → [NIGHT]，否则 → [DAY]；DAY / NIGHT 原样返回。
     * 在渲染边界（读取系统 `uiMode` 的地方）调用，保证 AUTO 不会进入主题文件加载。
     */
    fun resolve(systemInDarkMode: Boolean): PixelTheme = when (this) {
        AUTO -> if (systemInDarkMode) NIGHT else DAY
        else -> this
    }
}
