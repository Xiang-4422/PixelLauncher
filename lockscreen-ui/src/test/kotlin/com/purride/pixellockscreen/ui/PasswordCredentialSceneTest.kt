package com.purride.pixellockscreen.ui

import com.purride.pixeldesign.ProductThemeBrightness
import com.purride.pixeldesign.ProductThemeCatalog
import com.purride.pixeldesign.ProductThemeFamily
import com.purride.pixelcore.PixelColor
import com.purride.pixelcore.ScreenProfile
import com.purride.pixelui.MediaQuery
import com.purride.pixelui.MediaQueryData
import com.purride.pixelui.Widget
import com.purride.pixelui.testing.PixelTester
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 验证像素密码场景只保存输入长度并覆盖系统 IME 所需的公开动作。 */
class PasswordCredentialSceneTest {
    /** 就绪和错误允许输入，校验与限流阶段禁止请求继续编辑。 */
    @Test
    fun stateExposesOnlySafeLengthAndAvailability() {
        assertTrue(state(PasswordCredentialFeedback.READY, 4).isInputEnabled)
        assertTrue(state(PasswordCredentialFeedback.ERROR, 0).isInputEnabled)
        assertFalse(state(PasswordCredentialFeedback.CHECKING, 4).isInputEnabled)
        assertFalse(state(PasswordCredentialFeedback.LOCKED_OUT, 0).isInputEnabled)
        assertTrue(
            runCatching { PasswordCredentialUiState(promptText = "ENTER PASSWORD", inputLength = -1) }
                .exceptionOrNull() is IllegalArgumentException,
        )
        assertTrue(
            runCatching { PasswordCredentialUiState(promptText = " ") }.exceptionOrNull()
                is IllegalArgumentException,
        )
    }

    /** 密码状态应覆盖原生输入框的完整长度边界，但拒绝越界值。 */
    @Test
    fun stateMatchesNativePasswordLengthBoundary() {
        assertEquals(
            500,
            PasswordCredentialUiState(
                promptText = "ENTER PASSWORD",
                inputLength = 500,
            ).inputLength,
        )
        assertTrue(
            runCatching {
                PasswordCredentialUiState(
                    promptText = "ENTER PASSWORD",
                    inputLength = 501,
                )
            }.exceptionOrNull() is IllegalArgumentException,
        )
    }

    /** 方屏的输入、输入法和紧急区域都必须保持独立并位于视口内。 */
    @Test
    fun layoutsContainIndependentActionsWithinViewport() {
        /** 当前方屏密码布局。 */
        val layout = passwordCredentialLayout()
        /** 当前布局全部动作区域。 */
        val actions = listOf(layout.inputAction, layout.imeSwitcherAction, layout.emergencyAction)
        assertEquals(3, actions.map(PasswordActionSpec::action).distinct().size)
        actions.forEach { action ->
            assertTrue(action.left >= 0 && action.top >= 0)
            assertTrue(action.left + action.width <= layout.logicalWidth)
            assertTrue(action.top + action.height <= layout.logicalHeight)
        }
        assertEquals(
            PasswordCredentialAction.INPUT,
            layout.actionAt(
                layout.inputAction.left + 1,
                layout.inputAction.top + 1,
                includeImeSwitcher = true,
            ),
        )
        assertNull(
            layout.actionAt(
                layout.imeSwitcherAction.left + 1,
                layout.imeSwitcherAction.top + 1,
                includeImeSwitcher = false,
            ),
        )
    }

    /** 方屏密码长度、焦点高亮和透明四角应同时正确绘制。 */
    @Test
    fun squareSceneDrawsLengthAndFocusOverTransparency() {
        /** 当前测试布局。 */
        val layout = passwordCredentialLayout()
        /** 当前测试色板。 */
        val palette = ProductThemeCatalog.resolve(
            ProductThemeFamily.CITRUS,
            ProductThemeBrightness.DARK,
        )
        /** 离屏像素宿主。 */
        val tester = PixelTester()
        try {
            tester.pumpWidget(
                mediaRoot(
                    buildPasswordCredentialScene(
                        PasswordCredentialSceneRequest(
                            state = state(
                                PasswordCredentialFeedback.READY,
                                inputLength = 4,
                                hasInputFocus = true,
                                isImeSwitcherVisible = true,
                            ),
                            family = palette.family,
                            brightness = palette.brightness,
                            pressedAction = null,
                        ),
                    ),
                    layout,
                ),
                logicalWidth = layout.logicalWidth,
                logicalHeight = layout.logicalHeight,
            )

            assertEquals(PixelColor.Transparent, tester.pixelAt(0, 0))
            assertEquals(
                palette.primary,
                tester.pixelAt(layout.inputAction.left + 1, layout.inputAction.top + 1),
            )
            assertTrue(tester.hasPixel(palette.alert))
            assertTrue(tester.hasPixel(palette.secondary))
        } finally {
            tester.dispose()
        }
    }

    /** SystemUI 隐藏紧急入口时密码场景不得绘制对应警示色动作。 */
    @Test
    fun hiddenEmergencyActionIsNotDrawn() {
        /** 当前方屏布局。 */
        val layout = passwordCredentialLayout()
        /** 当前测试色板。 */
        val palette = ProductThemeCatalog.resolve(
            ProductThemeFamily.CITRUS,
            ProductThemeBrightness.DARK,
        )
        /** 离屏像素宿主。 */
        val tester = PixelTester()
        try {
            tester.pumpWidget(
                mediaRoot(
                    buildPasswordCredentialScene(
                        PasswordCredentialSceneRequest(
                            state = state(
                                PasswordCredentialFeedback.READY,
                                inputLength = 0,
                            ).copy(isEmergencyAvailable = false),
                            family = palette.family,
                            brightness = palette.brightness,
                            pressedAction = null,
                        ),
                    ),
                    layout,
                ),
                logicalWidth = layout.logicalWidth,
                logicalHeight = layout.logicalHeight,
            )

            assertFalse(tester.hasPixel(palette.alert))
        } finally {
            tester.dispose()
        }
    }

    /** 方屏四种反馈状态都必须在固定逻辑视口内完成绘制。 */
    @Test
    fun squareFeedbackStatesRenderWithoutClipping() {
        PasswordCredentialFeedback.entries.forEach { feedback ->
            /** 当前方屏布局。 */
            val layout = passwordCredentialLayout()
            /** 离屏像素宿主。 */
            val tester = PixelTester()
            try {
                tester.pumpWidget(
                    mediaRoot(
                        buildPasswordCredentialScene(
                            PasswordCredentialSceneRequest(
                                state = state(feedback, inputLength = 14),
                                family = ProductThemeFamily.MIDNIGHT,
                                brightness = ProductThemeBrightness.LIGHT,
                                pressedAction = PasswordCredentialAction.EMERGENCY,
                            ),
                        ),
                        layout,
                    ),
                    logicalWidth = layout.logicalWidth,
                    logicalHeight = layout.logicalHeight,
                )
                assertEquals(PixelColor.Transparent, tester.pixelAt(0, 0))
            } finally {
                tester.dispose()
            }
        }
    }

    /** 构造只包含输入长度、焦点和公开入口状态的密码测试状态。 */
    private fun state(
        feedback: PasswordCredentialFeedback,
        inputLength: Int,
        hasInputFocus: Boolean = false,
        isImeSwitcherVisible: Boolean = false,
    ): PasswordCredentialUiState = PasswordCredentialUiState(
        promptText = "ENTER PASSWORD",
        inputLength = inputLength,
        feedbackText = when (feedback) {
            PasswordCredentialFeedback.ERROR -> "TRY AGAIN"
            PasswordCredentialFeedback.LOCKED_OUT -> "WAIT 30S"
            PasswordCredentialFeedback.CHECKING -> "CHECKING"
            PasswordCredentialFeedback.READY -> ""
        },
        feedback = feedback,
        hasInputFocus = hasInputFocus,
        isImeSwitcherVisible = isImeSwitcherVisible,
    )

    /** 为离屏场景补充与真实 PixelHostView 一致的视口环境。 */
    private fun mediaRoot(child: Widget, layout: PasswordCredentialLayout): Widget = MediaQuery(
        data = MediaQueryData(
            logicalWidth = layout.logicalWidth,
            logicalHeight = layout.logicalHeight,
            screenProfile = ScreenProfile(
                logicalWidth = layout.logicalWidth,
                logicalHeight = layout.logicalHeight,
                dotSizePx = 1,
            ),
        ),
        child = child,
    )
}
