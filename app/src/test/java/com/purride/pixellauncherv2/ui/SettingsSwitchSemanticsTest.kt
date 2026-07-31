package com.purride.pixellauncherv2.ui

import com.purride.pixelcore.PixelColor
import com.purride.pixelui.PixelSemanticRole
import com.purride.pixelui.PixelSemanticsAction
import com.purride.pixelui.testing.PixelTester
import com.purride.pixellauncherv2.launcher.LauncherThemeBrightness
import com.purride.pixellauncherv2.ui.theme.ButtonColors
import com.purride.pixellauncherv2.ui.theme.DrawerColors
import com.purride.pixellauncherv2.ui.theme.LauncherTheme
import com.purride.pixellauncherv2.ui.theme.SemanticColors
import com.purride.pixellauncherv2.ui.theme.SettingsColors
import com.purride.pixellauncherv2.ui.theme.SmsColors
import com.purride.pixellauncherv2.ui.theme.StatusBarColors
import com.purride.pixellauncherv2.ui.theme.SurfaceColors
import com.purride.pixellauncherv2.ui.theme.TextColors
import com.purride.pixellauncherv2.ui.widget.SettingsSwitchRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Settings 布尔封装必须隐藏底层 Tab 语义并重新导出标准 Switch 契约。 */
class SettingsSwitchSemanticsTest {
    /** Switch 节点包含 checked 与 click，内部 OFF/ON Tab 不暴露给无障碍树。 */
    @Test
    fun binaryWrapperExportsOneSwitchNode() {
        /** 记录无障碍点击是否复用业务 toggle 回调。 */
        var toggleCount = 0
        /** 使用离屏像素宿主收集完整虚拟语义树。 */
        val tester = PixelTester()
        try {
            tester.pumpWidget(
                SettingsSwitchRow(
                    title = "GAP",
                    checked = true,
                    theme = testTheme(),
                    onToggle = { toggleCount += 1 },
                ),
                logicalWidth = 120,
                logicalHeight = 20,
            )
            /** 标题为 GAP 且角色为 Switch 的唯一语义节点。 */
            val switchNode = tester.semanticsNodes()
                .single { node -> node.label == "GAP" && node.role == PixelSemanticRole.SWITCH }

            assertEquals(true, switchNode.checked)
            assertTrue(PixelSemanticsAction.CLICK in switchNode.actions)
            assertFalse(tester.semanticsNodes().any { node -> node.role == PixelSemanticRole.TAB })
            assertTrue(tester.performSemanticsAction(switchNode.id, PixelSemanticsAction.CLICK))
            assertEquals(1, toggleCount)
        } finally {
            tester.dispose()
        }
    }

    /** 构造不依赖 Android JSONObject 桩的最小稳定测试主题。 */
    private fun testTheme(): LauncherTheme {
        /** 普通前景色。 */
        val foreground = PixelColor.White
        /** 普通背景色。 */
        val background = PixelColor.Black
        /** 选中填充色。 */
        val accent = PixelColor.fromRgb(40, 100, 220)
        /** 禁用文字色。 */
        val disabled = PixelColor.fromRgb(90, 90, 90)
        return LauncherTheme(
            id = "test",
            label = "Test",
            mode = LauncherThemeBrightness.DARK,
            surface = SurfaceColors(background, background, background, background),
            text = TextColors(foreground, foreground, disabled, background),
            statusBar = StatusBarColors(
                text = foreground,
                mutedText = disabled,
                batteryHigh = foreground,
                batteryMedium = foreground,
                batteryLow = foreground,
                searchText = foreground,
                searchPlaceholder = disabled,
            ),
            drawer = DrawerColors(foreground, disabled, foreground, disabled),
            settings = SettingsColors(foreground, foreground),
            button = ButtonColors(
                text = foreground,
                border = foreground,
                pressedFill = accent,
                selectedText = background,
                unselectedText = foreground,
                filledSurface = foreground,
                filledText = background,
                disabledText = disabled,
            ),
            sms = SmsColors(foreground, foreground, foreground, foreground),
            semantic = SemanticColors(foreground, foreground, foreground, foreground),
        )
    }
}
