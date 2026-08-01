package com.purride.pixellauncherv2.ui

import com.purride.pixelcore.PixelColor
import com.purride.pixelui.PixelSemanticRole
import com.purride.pixelui.PixelSemanticsAction
import com.purride.pixelui.PixelSystemIcon
import com.purride.pixelui.PixelSystemIconSize
import com.purride.pixelui.PixelSystemIcons
import com.purride.pixelui.SegmentedControlWidthPolicy
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
import com.purride.pixellauncherv2.ui.widget.SettingsSegmentedControlRow
import com.purride.pixellauncherv2.ui.widget.SettingsOptionStepperRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Settings 布尔封装必须隐藏底层 Tab 语义并重新导出标准 Switch 契约。 */
class SettingsSwitchSemanticsTest {
    /** 启用步进器必须为前后方向提供两个独立按钮语义，不能继续隐藏在标题和值上。 */
    @Test
    fun enabledStepperExportsPreviousAndNextButtons() {
        /** 记录前一项动作次数。 */
        var previousCount = 0
        /** 记录后一项动作次数。 */
        var nextCount = 0
        /** 使用离屏像素宿主读取步进器的两个方向按钮。 */
        val tester = PixelTester()
        try {
            tester.pumpWidget(
                SettingsOptionStepperRow(
                    title = "THEME",
                    valueLabel = "CRT",
                    theme = testTheme(),
                    onPrevious = { previousCount += 1 },
                    onNext = { nextCount += 1 },
                ),
                logicalWidth = 180,
                logicalHeight = 30,
            )
            /** 前一项按钮语义节点。 */
            val previous = tester.semanticsNodesByLabel("THEME PREVIOUS").single()
            /** 后一项按钮语义节点。 */
            val next = tester.semanticsNodesByLabel("THEME NEXT").single()

            assertEquals(PixelSemanticRole.BUTTON, previous.role)
            assertEquals(PixelSemanticRole.BUTTON, next.role)
            assertTrue(tester.performSemanticsAction(previous.id, PixelSemanticsAction.CLICK))
            assertTrue(tester.performSemanticsAction(next.id, PixelSemanticsAction.CLICK))
            assertEquals(1, previousCount)
            assertEquals(1, nextCount)
        } finally {
            tester.dispose()
        }
    }

    /** 三态 MODE 复用同一分段选择器，同时保留直接选择任意模式的 Tab 语义。 */
    @Test
    fun themeModeExportsDayAutoNightTabs() {
        /** 记录语义点击后请求的目标模式下标。 */
        var requestedIndex = -1
        /** 使用离屏像素宿主收集三态选择器语义树。 */
        val tester = PixelTester()
        try {
            tester.pumpWidget(
                SettingsSegmentedControlRow(
                    title = "MODE",
                    labels = listOf("DAY", "AUTO", "NIGHT"),
                    icons = listOf(
                        PixelSystemIcons.mask(PixelSystemIcon.DAY, PixelSystemIconSize.SMALL),
                        PixelSystemIcons.mask(PixelSystemIcon.AUTO, PixelSystemIconSize.SMALL),
                        PixelSystemIcons.mask(PixelSystemIcon.NIGHT, PixelSystemIconSize.SMALL),
                    ),
                    selectedIndex = 1,
                    theme = testTheme(),
                    widthPolicy = SegmentedControlWidthPolicy.EqualToWidest,
                    onSelected = { requestedIndex = it },
                ),
                logicalWidth = 180,
                logicalHeight = 24,
            )
            /** MODE 下的三个可直接选择语义节点。 */
            val tabs = tester.semanticsNodes().filter { node -> node.role == PixelSemanticRole.TAB }

            assertEquals(listOf("DAY", "AUTO", "NIGHT"), tabs.map { node -> node.label })
            assertEquals(listOf(false, true, false), tabs.map { node -> node.selected })
            assertTrue(tester.performSemanticsAction(tabs.last().id, PixelSemanticsAction.CLICK))
            assertEquals(2, requestedIndex)
        } finally {
            tester.dispose()
        }
    }

    /** 单一字体属性不可切换时，通用枚举行必须禁用底层分段语义与回调。 */
    @Test
    fun disabledEnumRowBlocksSelectionActions() {
        /** 记录禁用控件是否错误触发选择回调。 */
        var selectionCount = 0
        /** 使用离屏像素宿主读取禁用状态下的语义节点。 */
        val tester = PixelTester()
        try {
            tester.pumpWidget(
                SettingsSegmentedControlRow(
                    title = "SIZE",
                    labels = listOf("12PX"),
                    selectedIndex = 0,
                    theme = testTheme(),
                    enabled = false,
                    onSelected = { selectionCount += 1 },
                ),
                logicalWidth = 120,
                logicalHeight = 20,
            )
            /** 唯一字号候选仍可读，但不提供点击能力。 */
            val sizeTab = tester.semanticsNodes().single { node -> node.role == PixelSemanticRole.TAB }

            assertFalse(sizeTab.enabled)
            assertFalse(PixelSemanticsAction.CLICK in sizeTab.actions)
            assertFalse(tester.performSemanticsAction(sizeTab.id, PixelSemanticsAction.CLICK))
            assertEquals(0, selectionCount)
        } finally {
            tester.dispose()
        }
    }

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
                pressedFill = foreground,
                selectedText = background,
                unselectedText = foreground,
                filledSurface = foreground,
                filledText = background,
                disabledText = disabled,
            ),
            sms = SmsColors(
                sender = foreground,
                threadPreview = foreground,
                incomingMessage = foreground,
                outgoingMessage = foreground,
                composerText = foreground,
                timestamp = foreground,
                draftBorder = foreground,
                selectionFill = background,
                loadingTrack = background,
            ),
            semantic = SemanticColors(foreground, foreground, foreground, foreground),
        )
    }
}
