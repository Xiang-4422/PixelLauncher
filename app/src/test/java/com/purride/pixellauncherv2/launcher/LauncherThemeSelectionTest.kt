package com.purride.pixellauncherv2.launcher

import org.junit.Assert.assertEquals
import org.junit.Test

/** 锁定 Showcase 主题家族与用户亮暗模式解析规则。 */
class LauncherThemeModeTest {
    /** 设置页模式必须按 DAY、AUTO、NIGHT 的视觉与交互顺序排列。 */
    @Test
    fun modesKeepDayAutoNightOrder() {
        assertEquals(
            listOf(LauncherThemeMode.DAY, LauncherThemeMode.AUTO, LauncherThemeMode.NIGHT),
            LauncherThemeMode.entries,
        )
    }

    /** AUTO 必须跟随系统暗色状态解析成实际亮度。 */
    @Test
    fun resolve_autoFollowsSystemDarkMode() {
        assertEquals(
            LauncherThemeBrightness.DARK,
            LauncherThemeMode.AUTO.resolve(systemInDarkMode = true),
        )
        assertEquals(
            LauncherThemeBrightness.LIGHT,
            LauncherThemeMode.AUTO.resolve(systemInDarkMode = false),
        )
    }

    /** 显式 DAY/NIGHT 必须忽略系统模式并解析成固定亮度。 */
    @Test
    fun resolve_explicitThemesIgnoreSystem() {
        assertEquals(
            LauncherThemeBrightness.LIGHT,
            LauncherThemeMode.DAY.resolve(systemInDarkMode = true),
        )
        assertEquals(
            LauncherThemeBrightness.DARK,
            LauncherThemeMode.NIGHT.resolve(systemInDarkMode = false),
        )
    }

    /** 主题家族必须保持 Showcase 的稳定顺序和名称。 */
    @Test
    fun familiesMatchShowcaseMachineMoods() {
        assertEquals(
            listOf("MIDNIGHT", "CRT", "AMBER", "GAMEBOY", "PAPER"),
            LauncherThemeFamily.entries.map { family -> family.displayLabel },
        )
    }
}
