package com.purride.pixelui.regression

import com.purride.pixelcore.PixelBitmapFont
import com.purride.pixelcore.PixelColor
import com.purride.pixelcore.ScreenProfile
import com.purride.pixelui.Column
import com.purride.pixelui.Container
import com.purride.pixelui.Directionality
import com.purride.pixelui.HostCapabilities
import com.purride.pixelui.HostCapabilitiesData
import com.purride.pixelui.MediaQuery
import com.purride.pixelui.MediaQueryData
import com.purride.pixelui.OutlinedButton
import com.purride.pixelui.PixelControlState
import com.purride.pixelui.PixelControlStateSet
import com.purride.pixelui.PixelTheme
import com.purride.pixelui.PixelThemeTokens
import com.purride.pixelui.PixelTypographyToken
import com.purride.pixelui.PixelTypographyTokens
import com.purride.pixelui.Row
import com.purride.pixelui.Text
import com.purride.pixelui.TextDirection
import com.purride.pixelui.TextStyle
import com.purride.pixelui.Widget
import com.purride.pixelui.animation.Curves
import com.purride.pixelui.testing.PixelTester
import com.purride.pixelui.widgets.animated.AnimatedOpacity
import java.io.File
import kotlin.time.Duration.Companion.milliseconds
import org.junit.Test

/**
 * M8-1 固定字体、时钟和设备 profile 的 exact-ARGB 综合 golden。
 *
 * 静态帧覆盖标准组件状态、RTL、textScale 和主题；动画帧由 [PixelTester] 的手动时钟推进。
 */
class M81DeterministicPixelGoldenTest {
    /** 综合场景必须与人工审阅的逐像素基线完全一致。 */
    @Test
    fun componentEnvironmentAndAnimationFramesMatchReviewedExactArgbGolden() {
        /** 所有环境和动画帧按固定顺序拼成一个可审阅 artifact。 */
        val actual = buildString {
            environmentCases().forEach { case ->
                append(captureEnvironment(case))
            }
            append(captureAnimationFrames())
        }
        ReviewedGoldenVerifier.assertMatches(
            baselineFile = File(GOLDEN_PATH),
            actual = actual,
            reportStem = File(REPORT_STEM_PATH),
        )
    }

    /** 一个固定 profile 下的主题、方向、缩放与组件状态组合。 */
    private data class EnvironmentCase(
        /** 写入 golden 的稳定场景名。 */
        val name: String,
        /** 当前场景使用的完整主题。 */
        val theme: PixelThemeTokens,
        /** 当前场景的文字方向。 */
        val direction: TextDirection,
        /** Host 文字缩放倍率。 */
        val textScale: Float,
        /** OutlinedButton 的确定性状态。 */
        val controlState: PixelControlState,
    )

    /** 返回覆盖状态、主题、RTL 和 textScale 的最小正交场景集。 */
    private fun environmentCases(): List<EnvironmentCase> {
        return listOf(
            EnvironmentCase(
                name = "light-normal-ltr-1x",
                theme = PixelThemeTokens.Light,
                direction = TextDirection.LTR,
                textScale = 1f,
                controlState = PixelControlState.Normal,
            ),
            EnvironmentCase(
                name = "dark-focused-ltr-1x",
                theme = PixelThemeTokens.Dark,
                direction = TextDirection.LTR,
                textScale = 1f,
                controlState = PixelControlState.Focused,
            ),
            EnvironmentCase(
                name = "high-contrast-disabled-ltr-1x",
                theme = PixelThemeTokens.HighContrastDark,
                direction = TextDirection.LTR,
                textScale = 1f,
                controlState = PixelControlState.Disabled,
            ),
            EnvironmentCase(
                name = "light-normal-rtl-1x",
                theme = PixelThemeTokens.Light,
                direction = TextDirection.RTL,
                textScale = 1f,
                controlState = PixelControlState.Normal,
            ),
            EnvironmentCase(
                name = "light-normal-ltr-2x",
                theme = PixelThemeTokens.Light,
                direction = TextDirection.LTR,
                textScale = 2f,
                controlState = PixelControlState.Normal,
            ),
        )
    }

    /** 在独立 runtime 中渲染一个固定环境并编码完整 ARGB buffer。 */
    private fun captureEnvironment(case: EnvironmentCase): String {
        /** 场景独占 tester，避免状态、字体缓存和手动时钟相互污染。 */
        val tester = PixelTester()
        return try {
            tester.pumpWidget(
                widget = environmentWidget(case),
                logicalWidth = TEST_PROFILE.logicalWidth,
                logicalHeight = TEST_PROFILE.logicalHeight,
            )
            encodeFrame(name = case.name, tester = tester)
        } finally {
            tester.dispose()
        }
    }

    /** 构造与真实 Host 相同的 capability、方向、MediaQuery 和主题作用域顺序。 */
    private fun environmentWidget(case: EnvironmentCase): Widget {
        /** 固定刷新率、density、方向和 textScale 的 Host capability。 */
        val capabilities = HostCapabilitiesData(
            layoutDirection = case.direction,
            textScaleFactor = case.textScale,
            density = 1f,
            refreshRateHz = 60f,
        )
        /** 固定逻辑尺寸、dot size 和零 Insets 的设备查询。 */
        val mediaQuery = MediaQueryData(
            logicalWidth = TEST_PROFILE.logicalWidth,
            logicalHeight = TEST_PROFILE.logicalHeight,
            screenProfile = TEST_PROFILE,
        )
        /** 所有文字角色显式绑定仓库内置 5x7 测试字体。 */
        val tokens = case.theme.copy(typography = case.theme.typography.withTestFont())
        return HostCapabilities(
            data = capabilities,
            child = Directionality(
                textDirection = case.direction,
                child = MediaQuery(
                    data = mediaQuery,
                    child = PixelTheme(
                        tokens = tokens,
                        child = Container(
                            fillColor = tokens.colors.background,
                            child = Column(
                                children = listOf(
                                    Row(
                                        children = listOf(
                                            colorMarker(PixelColor.fromRgb(241, 67, 54)),
                                            colorMarker(PixelColor.fromRgb(76, 175, 80)),
                                            colorMarker(PixelColor.fromRgb(33, 150, 243)),
                                        ),
                                        spacing = 1,
                                    ),
                                    Text(
                                        data = "M8",
                                        style = TextStyle(
                                            color = tokens.colors.onBackground,
                                            textRasterizer = TEST_FONT,
                                        ),
                                    ),
                                    OutlinedButton(
                                        text = "OK",
                                        onPressed = {},
                                        states = PixelControlStateSet.of(case.controlState),
                                        key = "${case.name}-button",
                                    ),
                                ),
                                spacing = 1,
                            ),
                        ),
                    ),
                ),
            ),
        )
    }

    /** 创建一个方向敏感的固定色块。 */
    private fun colorMarker(color: PixelColor): Widget {
        return Container(width = 4, height = 3, fillColor = color)
    }

    /** 用 tester 的固定手动时钟捕获 0%、50% 和 100% 动画帧。 */
    private fun captureAnimationFrames(): String {
        /** 动画独占 tester，同时提供固定 [PixelTester.scheduler] 和 ticker。 */
        val tester = PixelTester()
        return try {
            tester.pumpWidget(
                widget = animatedFrame(opacity = 0f, tester = tester),
                logicalWidth = ANIMATION_WIDTH,
                logicalHeight = ANIMATION_HEIGHT,
            )
            tester.pumpWidget(
                widget = animatedFrame(opacity = 1f, tester = tester),
                logicalWidth = ANIMATION_WIDTH,
                logicalHeight = ANIMATION_HEIGHT,
            )
            tester.pumpFrame(deltaMs = 0L)
            buildString {
                append(encodeFrame(name = "animation-0ms", tester = tester))
                tester.pumpFrame(deltaMs = 500L)
                append(encodeFrame(name = "animation-500ms", tester = tester))
                tester.pumpFrame(deltaMs = 500L)
                append(encodeFrame(name = "animation-1000ms", tester = tester))
            }
        } finally {
            tester.dispose()
        }
    }

    /** 创建一个使用固定线性曲线和固定 RGB 内容的动画节点。 */
    private fun animatedFrame(opacity: Float, tester: PixelTester): Widget {
        return AnimatedOpacity(
            opacity = opacity,
            duration = 1_000.milliseconds,
            vsync = tester.vsync,
            curve = Curves.Linear,
            key = "m8-animation",
            child = Container(
                width = ANIMATION_WIDTH,
                height = ANIMATION_HEIGHT,
                fillColor = ANIMATION_COLOR,
            ),
        )
    }

    /** 把当前完整 buffer 编码为不会丢失颜色或坐标的逐行 ARGB run。 */
    private fun encodeFrame(name: String, tester: PixelTester): String {
        /** 当前帧的不可空生产渲染结果。 */
        val result = requireNotNull(tester.renderResult)
        return buildString {
            append('[').append(name).append("]\n")
            append("size=").append(result.buffer.width).append('x').append(result.buffer.height).append('\n')
            for (y in 0 until result.buffer.height) {
                /** 当前逻辑行的完整 ARGB 像素。 */
                val row = IntArray(result.buffer.width) { x ->
                    result.buffer.pixels[y * result.buffer.width + x]
                }
                append("y=").append(y).append(':').append(row.toArgbRuns()).append('\n')
            }
        }
    }

    /** 把一行像素编码为保留长度和精确颜色的 `COUNT*ARGB` 序列。 */
    private fun IntArray.toArgbRuns(): String {
        if (isEmpty()) return ""
        /** 已完成的行程编码片段。 */
        val runs = mutableListOf<String>()
        /** 当前行程的 ARGB 值。 */
        var current = first()
        /** 当前行程包含的连续像素数。 */
        var count = 1
        for (index in 1 until size) {
            /** 当前待合并或开启新行程的像素。 */
            val next = this[index]
            if (next == current) {
                count += 1
            } else {
                runs += "$count*${current.hexArgb()}"
                current = next
                count = 1
            }
        }
        runs += "$count*${current.hexArgb()}"
        return runs.joinToString(",")
    }

    /** 把当前主题的所有文字角色绑定到同一个固定测试字体。 */
    private fun PixelTypographyTokens.withTestFont(): PixelTypographyTokens {
        /** 为单个文字角色复制固定 rasterizer 的转换函数。 */
        val fixed: (PixelTypographyToken) -> PixelTypographyToken = { token ->
            token.copy(textRasterizer = TEST_FONT)
        }
        return copy(
            body = fixed(body),
            label = fixed(label),
            title = fixed(title),
            caption = fixed(caption),
            button = fixed(button),
            input = fixed(input),
        )
    }

    /** 把 packed ARGB 转为固定八位大写十六进制。 */
    private fun Int.hexArgb(): String = toUInt().toString(radix = 16).padStart(8, '0').uppercase()

    private companion object {
        /** 仓库内置、跨 JVM/设备一致的 5x7 bitmap 测试字体。 */
        val TEST_FONT: PixelBitmapFont = PixelBitmapFont.Default

        /** 静态 golden 的固定逻辑设备 profile。 */
        val TEST_PROFILE: ScreenProfile = ScreenProfile(
            logicalWidth = 48,
            logicalHeight = 40,
            dotSizePx = 1,
        )

        /** 动画帧固定宽度。 */
        const val ANIMATION_WIDTH: Int = 6

        /** 动画帧固定高度。 */
        const val ANIMATION_HEIGHT: Int = 3

        /** 动画内容固定颜色。 */
        val ANIMATION_COLOR: PixelColor = PixelColor.fromRgb(40, 180, 240)

        /** 只能由显式源码 patch 更新的综合 golden。 */
        const val GOLDEN_PATH: String = "src/test/resources/golden/m8-1-deterministic-pixels.txt"

        /** 普通测试写入候选和 diff 的构建报告前缀。 */
        const val REPORT_STEM_PATH: String = "build/reports/golden/m8-1/deterministic-pixels"
    }
}
