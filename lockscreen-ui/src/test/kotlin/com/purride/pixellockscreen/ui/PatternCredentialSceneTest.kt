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
import org.junit.Assert.assertTrue
import org.junit.Test

/** 验证像素图案场景的透明绘制、路径和非敏感状态边界。 */
class PatternCredentialSceneTest {
    /** 主提示不能为空，错误和就绪允许输入，校验与限流禁止输入。 */
    @Test
    fun stateExposesOnlySafeInputAvailability() {
        assertTrue(state(PatternCredentialFeedback.READY).isInputEnabled)
        assertTrue(state(PatternCredentialFeedback.ERROR).isInputEnabled)
        assertFalse(state(PatternCredentialFeedback.CHECKING).isInputEnabled)
        assertFalse(state(PatternCredentialFeedback.LOCKED_OUT).isInputEnabled)
        assertTrue(
            runCatching { PatternCredentialUiState(promptText = " ") }.exceptionOrNull()
                is IllegalArgumentException,
        )
        assertTrue(
            runCatching {
                PatternCredentialUiState(
                    promptText = "DRAW PATTERN",
                    emergencyAccessibilityLabel = " ",
                )
            }.exceptionOrNull() is IllegalArgumentException,
        )
    }

    /** 纵屏场景保留透明四角，同时绘制九个节点和有序连接线。 */
    @Test
    fun portraitSceneDrawsPixelPatternOverTransparentBackground() {
        /** 当前测试色板。 */
        val palette = ProductThemeCatalog.resolve(
            ProductThemeFamily.ARCADE,
            ProductThemeBrightness.DARK,
        )
        /** 具有 0→4→8 路径的可清零跟踪器。 */
        val tracker = activeTracker(isLandscape = false, cells = listOf(0, 4, 8))
        /** 当前方向布局。 */
        val layout = patternCredentialLayout(isLandscape = false)
        /** 离屏像素宿主。 */
        val tester = PixelTester()
        try {
            tester.pumpWidget(
                mediaRoot(
                    buildPatternCredentialScene(
                        PatternCredentialSceneRequest(
                            state = state(PatternCredentialFeedback.READY),
                            family = palette.family,
                            brightness = palette.brightness,
                            isLandscape = false,
                        ),
                        tracker,
                    ),
                    layout,
                ),
                logicalWidth = layout.logicalWidth,
                logicalHeight = layout.logicalHeight,
            )

            assertEquals(PixelColor.Transparent, tester.pixelAt(0, 0))
            assertEquals(palette.primary, tester.pixelAt(layout.centerX(4), layout.centerY(4)))
            assertTrue(tester.hasPixel(palette.background))
        } finally {
            tracker.cancel()
            tester.dispose()
        }
    }

    /** 横屏错误和限流状态均应在固定逻辑视口内完成绘制。 */
    @Test
    fun landscapeFeedbackStatesRenderWithoutClipping() {
        listOf(PatternCredentialFeedback.ERROR, PatternCredentialFeedback.LOCKED_OUT).forEach { feedback ->
            /** 当前横屏布局。 */
            val layout = patternCredentialLayout(isLandscape = true)
            /** 当前空路径。 */
            val path = EmptyPatternPath
            /** 离屏像素宿主。 */
            val tester = PixelTester()
            try {
                tester.pumpWidget(
                    mediaRoot(
                        buildPatternCredentialScene(
                            PatternCredentialSceneRequest(
                                state = state(feedback),
                                family = ProductThemeFamily.MIDNIGHT,
                                brightness = ProductThemeBrightness.LIGHT,
                                isLandscape = true,
                            ),
                            path,
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

    /** 构造指定反馈状态的非敏感 UI 状态。 */
    private fun state(feedback: PatternCredentialFeedback): PatternCredentialUiState =
        PatternCredentialUiState(
            promptText = "DRAW PATTERN",
            feedbackText = when (feedback) {
                PatternCredentialFeedback.ERROR -> "TRY AGAIN"
                PatternCredentialFeedback.LOCKED_OUT -> "WAIT 30S"
                PatternCredentialFeedback.CHECKING -> "CHECKING"
                PatternCredentialFeedback.READY -> ""
            },
            feedback = feedback,
        )

    /** 创建一条尚未抬起的测试路径。 */
    private fun activeTracker(isLandscape: Boolean, cells: List<Int>): PatternGestureTracker {
        /** 当前方向布局。 */
        val layout = patternCredentialLayout(isLandscape)
        /** 仅用于渲染的跟踪器。 */
        val tracker = PatternGestureTracker(
            layout = layout,
            onStarted = {},
            onCellAdded = {},
            onCompleted = {},
            onCancelled = {},
            onVisualChanged = {},
        )
        /** 首枚格子。 */
        val firstCell = cells.first()
        tracker.start(layout.centerX(firstCell), layout.centerY(firstCell))
        cells.drop(1).forEach { cellId ->
            tracker.update(layout.centerX(cellId), layout.centerY(cellId))
        }
        return tracker
    }

    /** 为离屏场景补充与真实 PixelHostView 一致的视口环境。 */
    private fun mediaRoot(child: Widget, layout: PatternCredentialLayout): Widget = MediaQuery(
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

    /** 不包含任何图案格子的稳定空路径。 */
    private data object EmptyPatternPath : PatternVisualPath {
        /** 空路径长度。 */
        override val size: Int = 0

        /** 空路径不允许读取。 */
        override fun cellAt(index: Int): Int = throw IndexOutOfBoundsException("empty_pattern_path")
    }
}
