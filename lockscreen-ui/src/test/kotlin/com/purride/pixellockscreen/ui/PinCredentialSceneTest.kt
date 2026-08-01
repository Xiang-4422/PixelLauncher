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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 验证像素 PIN 场景不保存数字内容并覆盖全部稳定按键。 */
class PinCredentialSceneTest {
    /** 就绪和错误允许输入，校验与限流阶段禁止输入。 */
    @Test
    fun stateExposesOnlySafeLengthAndAvailability() {
        assertTrue(state(PinCredentialFeedback.READY, 4).isInputEnabled)
        assertTrue(state(PinCredentialFeedback.ERROR, 0).isInputEnabled)
        assertFalse(state(PinCredentialFeedback.CHECKING, 4).isInputEnabled)
        assertFalse(state(PinCredentialFeedback.LOCKED_OUT, 0).isInputEnabled)
        assertTrue(
            runCatching { PinCredentialUiState(promptText = "ENTER PIN", inputLength = -1) }
                .exceptionOrNull() is IllegalArgumentException,
        )
        assertTrue(
            runCatching { PinCredentialUiState(promptText = " ") }.exceptionOrNull()
                is IllegalArgumentException,
        )
    }

    /** 横竖屏均应提供唯一的十二键键盘和独立紧急区域。 */
    @Test
    fun layoutsContainUniqueKeysWithinViewport() {
        listOf(false, true).forEach { isLandscape ->
            /** 当前方向的 PIN 布局。 */
            val layout = pinCredentialLayout(isLandscape)
            assertEquals(12, layout.keys.size)
            assertEquals(12, layout.keys.map(PinKeySpec::id).distinct().size)
            assertEquals((0..11).toSet(), layout.keys.map(PinKeySpec::id).toSet())
            layout.keys.forEach { key ->
                assertTrue(key.left >= 0 && key.top >= 0)
                assertTrue(key.left + key.width <= layout.logicalWidth)
                assertTrue(key.top + key.height <= layout.logicalHeight)
                assertNotNull(layout.keyAt(key.left + key.width / 2, key.top + key.height / 2))
            }
            assertNull(layout.keyAt(0, 0))
            assertTrue(
                layout.containsEmergency(
                    layout.emergencyLeft + layout.emergencyWidth / 2,
                    layout.emergencyTop + layout.emergencyHeight / 2,
                ),
            )
        }
    }

    /** 纵屏 PIN 长度、按下高亮和透明四角应同时正确绘制。 */
    @Test
    fun portraitSceneDrawsLengthAndPressedKeyOverTransparency() {
        /** 当前测试布局。 */
        val layout = pinCredentialLayout(isLandscape = false)
        /** 当前测试色板。 */
        val palette = ProductThemeCatalog.resolve(
            ProductThemeFamily.ARCADE,
            ProductThemeBrightness.DARK,
        )
        /** 当前按下的数字五按键。 */
        val pressedKey = layout.keys.single { key -> key.id == 5 }
        /** 离屏像素宿主。 */
        val tester = PixelTester()
        try {
            tester.pumpWidget(
                mediaRoot(
                    buildPinCredentialScene(
                        PinCredentialSceneRequest(
                            state = state(PinCredentialFeedback.READY, inputLength = 4),
                            family = palette.family,
                            brightness = palette.brightness,
                            isLandscape = false,
                            pressedKeyId = pressedKey.id,
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
                tester.pixelAt(pressedKey.left + 1, pressedKey.top + 1),
            )
            assertTrue(tester.hasPixel(palette.alert))
        } finally {
            tester.dispose()
        }
    }

    /** SystemUI 隐藏紧急入口时 PIN 场景不得绘制对应警示色区域。 */
    @Test
    fun hiddenEmergencyActionIsNotDrawn() {
        /** 当前纵屏布局。 */
        val layout = pinCredentialLayout(isLandscape = false)
        /** 当前测试色板。 */
        val palette = ProductThemeCatalog.resolve(
            ProductThemeFamily.ARCADE,
            ProductThemeBrightness.DARK,
        )
        /** 离屏像素宿主。 */
        val tester = PixelTester()
        try {
            tester.pumpWidget(
                mediaRoot(
                    buildPinCredentialScene(
                        PinCredentialSceneRequest(
                            state = state(PinCredentialFeedback.READY, inputLength = 0).copy(
                                isEmergencyAvailable = false,
                            ),
                            family = palette.family,
                            brightness = palette.brightness,
                            isLandscape = false,
                            pressedKeyId = null,
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

    /** 横屏四种反馈状态都必须在固定逻辑视口内完成绘制。 */
    @Test
    fun landscapeFeedbackStatesRenderWithoutClipping() {
        PinCredentialFeedback.entries.forEach { feedback ->
            /** 当前横屏布局。 */
            val layout = pinCredentialLayout(isLandscape = true)
            /** 离屏像素宿主。 */
            val tester = PixelTester()
            try {
                tester.pumpWidget(
                    mediaRoot(
                        buildPinCredentialScene(
                            PinCredentialSceneRequest(
                                state = state(feedback, inputLength = 6),
                                family = ProductThemeFamily.MIDNIGHT,
                                brightness = ProductThemeBrightness.LIGHT,
                                isLandscape = true,
                                pressedKeyId = null,
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

    /** 构造只包含输入长度的非敏感 PIN 状态。 */
    private fun state(feedback: PinCredentialFeedback, inputLength: Int): PinCredentialUiState =
        PinCredentialUiState(
            promptText = "ENTER PIN",
            inputLength = inputLength,
            feedbackText = when (feedback) {
                PinCredentialFeedback.ERROR -> "TRY AGAIN"
                PinCredentialFeedback.LOCKED_OUT -> "WAIT 30S"
                PinCredentialFeedback.CHECKING -> "CHECKING"
                PinCredentialFeedback.READY -> ""
            },
            feedback = feedback,
        )

    /** 为离屏场景补充与真实 PixelHostView 一致的视口环境。 */
    private fun mediaRoot(child: Widget, layout: PinCredentialLayout): Widget = MediaQuery(
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
