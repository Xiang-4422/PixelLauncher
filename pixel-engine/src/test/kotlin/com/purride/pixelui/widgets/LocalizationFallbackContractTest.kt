package com.purride.pixelui.widgets

import com.purride.pixelcore.PixelColor
import com.purride.pixelui.Dialog
import com.purride.pixelui.OutlinedButton
import com.purride.pixelui.PixelButtonStyle
import com.purride.pixelui.PixelLabelTokens
import com.purride.pixelui.PixelSemanticRole
import com.purride.pixelui.PixelTextButtonStyle
import com.purride.pixelui.PixelTextStyle
import com.purride.pixelui.PixelTheme
import com.purride.pixelui.PixelThemeTokens
import com.purride.pixelui.ProgressBar
import com.purride.pixelui.SizedBox
import com.purride.pixelui.TextButton
import com.purride.pixelui.Tooltip
import com.purride.pixelui.Widget
import com.purride.pixelui.testing.PixelTester
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Runtime fallback contract when no localization provider is installed.
 *
 * These tests intentionally use only the concise facades, [PixelTheme], and [PixelTester]. They
 * freeze the label fallback boundary without coupling to the localization API.
 */
class LocalizationFallbackContractTest {
    /** 无提供者时简洁入口保留调用方显式样式并使用英文语义兜底。 */
    @Test
    fun conciseFacadesKeepExplicitStylesAndEnglishSemanticFallbacks() {
        /** 唯一描边按钮表面色，证明显式样式已绘制。 Unique outlined-button surface color proving the explicit style painted. */
        val buttonFill = PixelColor.fromRgb(31, 73, 127)
        /** 唯一描边按钮边框色，证明显式样式优先。 Unique outlined-button border color proving explicit style precedence. */
        val buttonBorder = PixelColor.fromRgb(211, 59, 83)
        /** 唯一描边按钮字形色，证明显式排版仍生效。 Unique outlined-button glyph color proving explicit typography remains active. */
        val buttonText = PixelColor.fromRgb(239, 193, 47)
        /** 唯一 TextButton 字形色，证明其显式样式已绘制。 Unique TextButton glyph color proving its explicit style painted. */
        val textButtonText = PixelColor.fromRgb(53, 227, 173)
        /** 显式进度前景哨兵色。 Explicit progress foreground sentinel. */
        val progressFill = PixelColor.fromRgb(23, 149, 83)
        /** 显式进度轨道哨兵色。 Explicit progress track sentinel. */
        val progressTrack = PixelColor.fromRgb(41, 43, 47)
        /** 唯一 Dialog 表面哨兵色，证明浮层表面仍被挂载。 Unique Dialog surface sentinel proving the overlay surface remained mounted. */
        val dialogFill = PixelColor.fromRgb(17, 29, 71)
        /** 唯一 Dialog 边框哨兵色，证明显式浮层样式仍生效。 Unique Dialog outline sentinel proving explicit overlay styling remained active. */
        val dialogBorder = PixelColor.fromRgb(197, 181, 37)
        /** Reused off-screen runtime prevents platform fonts or Android accessibility from intervening. */
        val tester = PixelTester()
        try {
            tester.pumpWidget(
                widget = OutlinedButton(
                    text = "EXPLICIT BUTTON",
                    onPressed = {},
                    style = PixelButtonStyle(
                        fillColor = buttonFill,
                        borderColor = buttonBorder,
                        textStyle = PixelTextStyle(color = buttonText),
                    ),
                ),
                logicalWidth = 96,
                logicalHeight = 20,
            )
            /** 调用方文本同时作为可见内容与朗读标签。 Caller text remains both the visible content and the spoken label. */
            val outlinedNode = tester.semanticsNodesByLabel("EXPLICIT BUTTON").single()
            assertEquals(PixelSemanticRole.BUTTON, outlinedNode.role)
            assertTrue(outlinedNode.enabled)
            assertTrue(tester.hasPixel(buttonFill))
            assertTrue(tester.hasPixel(buttonBorder))
            assertTrue(tester.hasPixel(buttonText))

            tester.pumpWidget(
                widget = TextButton(
                    text = "EXPLICIT TEXT BUTTON",
                    onPressed = {},
                    style = PixelTextButtonStyle(
                        textStyle = PixelTextStyle(color = textButtonText),
                    ),
                ),
                logicalWidth = 96,
                logicalHeight = 16,
            )
            /** TextButton keeps caller text instead of consulting a hidden default provider. */
            val textButtonNode = tester.semanticsNodesByLabel("EXPLICIT TEXT BUTTON").single()
            assertEquals(PixelSemanticRole.BUTTON, textButtonNode.role)
            assertTrue(tester.hasPixel(textButtonText))

            tester.pumpWidget(
                widget = ProgressBar(
                    progress = 0.5f,
                    width = 10,
                    height = 5,
                    color = progressFill,
                    trackColor = progressTrack,
                ),
                logicalWidth = 16,
                logicalHeight = 8,
            )
            /** 无提供者的 ProgressBar 使用英文 label token 兜底，并保留显式颜色。 */
            assertEquals(1, tester.semanticsNodesByLabel(PixelLabelTokens.Default.progress).size)
            assertTrue(tester.hasPixel(progressFill))
            assertTrue(tester.hasPixel(progressTrack))

            tester.pumpWidget(
                widget = Dialog(
                    content = SizedBox(width = 4, height = 3),
                    fillColor = dialogFill,
                    borderColor = dialogBorder,
                    modal = false,
                ),
                logicalWidth = 40,
                logicalHeight = 28,
            )
            /** 省略 Dialog 语义名称时保留公开的英文默认值。 Omitted Dialog semantics retain the published English default. */
            val dialogNode = tester.semanticsNodesByLabel("Dialog").single()
            assertEquals(PixelSemanticRole.DIALOG, dialogNode.role)
            assertTrue(tester.hasPixel(dialogFill))
            assertTrue(tester.hasPixel(dialogBorder))

            tester.pumpWidget(
                widget = Tooltip(
                    message = "",
                    visible = true,
                    child = SizedBox(width = 4, height = 3),
                ),
                logicalWidth = 40,
                logicalHeight = 28,
            )
            /** 空 Tooltip 消息使用文档规定的英文无障碍兜底。 Empty Tooltip messages use the documented English accessibility fallback. */
            val tooltipNode = tester.semanticsNodesByLabel("Tooltip").single()
            assertEquals(PixelSemanticRole.GENERIC, tooltipNode.role)
        } finally {
            tester.dispose()
        }
    }

    /** Explicit theme label tokens remain the standard-component source without another provider. */
    @Test
    fun explicitThemeLabelsStillDriveStandardComponentsWithoutLocalizationProvider() {
        /** Sentinel labels cover both text-bearing controls and passive overlay/progress semantics. */
        val labels = PixelLabelTokens.Default.copy(
            button = "THEME BUTTON",
            textButton = "THEME TEXT BUTTON",
            dialog = "THEME DIALOG",
            tooltip = "THEME TOOLTIP",
            progress = "THEME PROGRESS",
        )
        /** Complete token graph installed directly, with no localization provider ancestor. */
        val tokens = PixelThemeTokens.Default.copy(labels = labels)
        /** 复用运行时在显式主题边界上逐个验证简洁入口。 Reused runtime exercises each concise facade at an explicit theme boundary. */
        val tester = PixelTester()
        try {
            assertSingleThemedSemanticLabel(
                tester = tester,
                tokens = tokens,
                expectedLabel = labels.button,
                englishFallbackLabel = "Button",
                child = OutlinedButton(text = "", onPressed = {}),
            )
            assertSingleThemedSemanticLabel(
                tester = tester,
                tokens = tokens,
                expectedLabel = labels.textButton,
                englishFallbackLabel = "Text button",
                child = TextButton(text = "", onPressed = {}),
            )
            assertSingleThemedSemanticLabel(
                tester = tester,
                tokens = tokens,
                expectedLabel = labels.progress,
                englishFallbackLabel = "Progress",
                child = ProgressBar(progress = 0.5f),
            )
            assertSingleThemedSemanticLabel(
                tester = tester,
                tokens = tokens,
                expectedLabel = labels.dialog,
                englishFallbackLabel = "Dialog",
                child = Dialog(
                    content = SizedBox(width = 4, height = 3),
                    modal = false,
                ),
            )
            assertSingleThemedSemanticLabel(
                tester = tester,
                tokens = tokens,
                expectedLabel = labels.tooltip,
                englishFallbackLabel = "Tooltip",
                child = Tooltip(
                    message = "",
                    visible = true,
                    child = SizedBox(width = 4, height = 3),
                ),
            )

        } finally {
            tester.dispose()
        }
    }

    /** Pumps one real themed component and requires exactly one matching semantic node. */
    private fun assertSingleThemedSemanticLabel(
        tester: PixelTester,
        tokens: PixelThemeTokens,
        expectedLabel: String,
        englishFallbackLabel: String,
        child: Widget,
    ) {
        tester.pumpWidget(
            widget = PixelTheme(tokens = tokens, child = child),
            logicalWidth = 96,
            logicalHeight = 48,
        )
        assertEquals(1, tester.semanticsNodesByLabel(expectedLabel).size)
        assertFalse(
            "Unexpected English fallback '$englishFallbackLabel'",
            tester.semanticsNodesByLabel(englishFallbackLabel).isNotEmpty(),
        )
    }
}
