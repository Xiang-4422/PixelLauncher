package com.purride.pixelui.widgets

import com.purride.pixelcore.PixelColor
import com.purride.pixelui.Container
import com.purride.pixelui.EdgeInsets
import com.purride.pixelui.ListViewBuilder
import com.purride.pixelui.PixelColorRole
import com.purride.pixelui.PixelColorScheme
import com.purride.pixelui.PixelComponentTokens
import com.purride.pixelui.PixelControlStateSet
import com.purride.pixelui.PixelElevationRole
import com.purride.pixelui.PixelElevationTokens
import com.purride.pixelui.PixelLabelTokens
import com.purride.pixelui.PixelStateProperty
import com.purride.pixelui.PixelTheme
import com.purride.pixelui.PixelThemeTokens
import com.purride.pixelui.Row
import com.purride.pixelui.Scrollbar
import com.purride.pixelui.SizedBox
import com.purride.pixelui.Slider
import com.purride.pixelui.TextField
import com.purride.pixelui.Widget
import com.purride.pixelui.state.PixelListController
import com.purride.pixelui.state.PixelListState
import com.purride.pixelui.state.PixelTextFieldController
import com.purride.pixelui.testing.PixelTester
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 简洁与状态化 Slider、Scrollbar 的等价性与实时 token 解析契约。 */
class SliderScrollbarThemeResolutionTest {
    /** 简洁 Slider 与状态化实现等价，显式主题下每个颜色通道独立解析。 */
    @Test
    fun sliderConciseAndThemedColorChannelsResolveIndependently() {
        /** 无提供者时验证简洁入口与状态化入口渲染一致的运行时。 */
        val equivalenceTester = PixelTester()
        try {
            equivalenceTester.pumpWidget(
                widget = Slider(value = 0.5f),
                logicalWidth = SliderWidth,
                logicalHeight = SliderHeight,
            )
            /** 简洁入口的当前帧。 */
            val concisePixels = requireNotNull(equivalenceTester.renderResult).buffer.pixels.copyOf()
            equivalenceTester.pumpWidget(
                widget = Slider(
                    value = 0.5f,
                    states = PixelControlStateSet.Normal,
                    onDrag = {},
                    onRelease = {},
                ),
                logicalWidth = SliderWidth,
                logicalHeight = SliderHeight,
            )
            /** 状态化入口在同一输入下的参考帧。 */
            val stateAwarePixels = requireNotNull(equivalenceTester.renderResult).buffer.pixels.copyOf()
            assertTrue(concisePixels.contentEquals(stateAwarePixels))
            // 无显式主题时两条入口都解析默认 token：primary 前景与 track 容器。
            assertEquals(PixelColorScheme.Dark.primary, equivalenceTester.pixelAt(1, 2))
            assertEquals(PixelColorScheme.Dark.track, equivalenceTester.pixelAt(SliderWidth - 2, 2))
        } finally {
            equivalenceTester.dispose()
        }

        /** Explicit-theme runtime checking both default sentinel channels. */
        val themedTester = PixelTester()
        try {
            themedTester.pumpWidget(
                widget = sliderTheme(Slider(value = 0.5f)),
                logicalWidth = SliderWidth,
                logicalHeight = SliderHeight,
            )
            assertEquals(TokenActiveColor, themedTester.pixelAt(1, 2))
            assertEquals(TokenTrackColor, themedTester.pixelAt(SliderWidth - 2, 2))
        } finally {
            themedTester.dispose()
        }

        /** Caller active override paired with only the default track sentinel. */
        val activeOverrideTester = PixelTester()
        try {
            activeOverrideTester.pumpWidget(
                widget = sliderTheme(
                    Slider(value = 0.5f, activeColor = ExplicitActiveColor),
                ),
                logicalWidth = SliderWidth,
                logicalHeight = SliderHeight,
            )
            assertEquals(ExplicitActiveColor, activeOverrideTester.pixelAt(1, 2))
            assertEquals(TokenTrackColor, activeOverrideTester.pixelAt(SliderWidth - 2, 2))
        } finally {
            activeOverrideTester.dispose()
        }

        /** Caller track override paired with only the default active sentinel. */
        val trackOverrideTester = PixelTester()
        try {
            trackOverrideTester.pumpWidget(
                widget = sliderTheme(
                    Slider(value = 0.5f, trackColor = ExplicitTrackColor),
                ),
                logicalWidth = SliderWidth,
                logicalHeight = SliderHeight,
            )
            assertEquals(TokenActiveColor, trackOverrideTester.pixelAt(1, 2))
            assertEquals(ExplicitTrackColor, trackOverrideTester.pixelAt(SliderWidth - 2, 2))
        } finally {
            trackOverrideTester.dispose()
        }
    }

    /** Every declared Slider geometry and decoration token changes measured or painted output. */
    @Test
    fun sliderConsumesMinimumWidthPaddingBorderRadiusAndElevationTokens() {
        /** Token family whose geometry and decoration channels are all independently observable. */
        val sliderTokens = PixelComponentTokens.Default.slider.copy(
            containerColor = PixelStateProperty.constant(PixelColorRole.Track),
            contentColor = PixelStateProperty.constant(PixelColorRole.Primary),
            borderColor = PixelStateProperty.constant(PixelColorRole.Outline),
            focusIndicator = null,
            padding = EdgeInsets(left = 2, top = 1, right = 1, bottom = 2),
            minimumWidth = 11,
            minimumHeight = 9,
            borderWidth = 1,
            cornerRadius = 2,
            elevationRole = PixelElevationRole.Low,
        )
        /** Theme with a two-pixel low elevation and distinct paint colors. */
        val tokens = SliderThemeTokens.copy(
            elevations = PixelElevationTokens.Default.copy(low = 2),
            components = PixelComponentTokens.Default.copy(slider = sliderTokens),
        )
        /** Runtime using a shrink-wrapped Row so the Slider minimum width is measurable. */
        val tester = PixelTester()
        try {
            tester.pumpWidget(
                widget = PixelTheme(
                    tokens = tokens,
                    child = Row(
                        children = listOf(
                            Slider(
                                value = 1f,
                                states = PixelControlStateSet.Normal,
                                onDrag = {},
                                onRelease = {},
                                key = "token-slider",
                            ),
                        ),
                    ),
                ),
                logicalWidth = 24,
                logicalHeight = 16,
            )
            /** Drag target excludes the visual-only shadow but includes token minimum geometry. */
            val bounds = tester.renderResult!!.sliderTargets.single().bounds
            assertEquals(11, bounds.width)
            assertEquals(9, bounds.height)
            // Radius removes the outer corner; outline begins on the first stair-step pixel.
            assertEquals(PixelColor.Transparent, tester.pixelAt(0, 0))
            assertEquals(TokenBorderColor, tester.pixelAt(1, 0))
            // Left padding plus outline keeps active paint away from the component edge.
            assertEquals(TokenTrackColor, tester.pixelAt(2, 4))
            assertEquals(TokenActiveColor, tester.pixelAt(3, 4))
            // The two-pixel hard elevation remains visible beyond the eleven-pixel main surface.
            assertEquals(TokenShadowColor, tester.pixelAt(12, 4))
        } finally {
            tester.dispose()
        }
    }

    /** 省略 Scrollbar 颜色与宽度时统一由组件 token 解析，显式主题继续替换全部通道。 */
    @Test
    fun scrollbarOmittedChannelsResolveComponentTokens() {
        /** 用于推导等比 thumb 几何的滚动所有者。 Scroll owner used to derive proportional thumb geometry. */
        val defaultController = PixelListController()
        /** 与简洁入口配对的列表状态。 List state paired with the concise facade. */
        val defaultState = defaultController.create()
        /** 证明省略通道会解析默认 token 图的运行时。 Runtime proving omitted channels resolve the default token graph. */
        val defaultTester = PixelTester()
        try {
            defaultTester.pumpWidget(
                widget = conciseScrollbar(defaultState, defaultController),
                logicalWidth = ScrollbarViewportWidth,
                logicalHeight = ScrollbarViewportHeight,
            )
            assertEquals(
                PixelColorScheme.Dark.onSurface,
                defaultTester.pixelAt(ScrollbarViewportWidth - 1, 0),
            )
            assertEquals(
                PixelColorScheme.Dark.track,
                defaultTester.pixelAt(ScrollbarViewportWidth - 1, 6),
            )
            assertEquals(1, defaultTester.renderResult!!.scrollbarTargets.single().bounds.width)
        } finally {
            defaultTester.dispose()
        }

        /** 用于验证简洁入口 token 解析的主题化滚动所有者。 Themed scroll owner used to verify concise-facade token resolution. */
        val themedController = PixelListController()
        /** 与简洁入口配对的主题化列表状态。 Themed list state paired with the concise facade. */
        val themedState = themedController.create()
        /** 证明显式主题角色会替换全部省略通道的运行时。 */
        val themedTester = PixelTester()
        try {
            themedTester.pumpWidget(
                widget = scrollbarTheme(conciseScrollbar(themedState, themedController)),
                logicalWidth = ScrollbarViewportWidth,
                logicalHeight = ScrollbarViewportHeight,
            )
            assertEquals(TokenScrollbarThumbColor, themedTester.pixelAt(ScrollbarViewportWidth - 1, 0))
            assertEquals(TokenScrollbarTrackColor, themedTester.pixelAt(ScrollbarViewportWidth - 1, 6))
            assertEquals(2, themedTester.renderResult!!.scrollbarTargets.single().bounds.width)
        } finally {
            themedTester.dispose()
        }

        /** State-aware scroll owner proving its null track still resolves normal component tokens. */
        val stateAwareController = PixelListController()
        /** State-aware list state paired with the required-states overload. */
        val stateAwareState = stateAwareController.create()
        /** 守护状态化入口自身 token 解析不回归的运行时。 */
        val stateAwareTester = PixelTester()
        try {
            stateAwareTester.pumpWidget(
                widget = scrollbarTheme(
                    Scrollbar(
                        child = scrollViewport(stateAwareState, stateAwareController),
                        state = stateAwareState,
                        states = PixelControlStateSet.Normal,
                    ),
                ),
                logicalWidth = ScrollbarViewportWidth,
                logicalHeight = ScrollbarViewportHeight,
            )
            assertEquals(TokenScrollbarTrackColor, stateAwareTester.pixelAt(ScrollbarViewportWidth - 1, 6))
        } finally {
            stateAwareTester.dispose()
        }
    }

    /** 简洁 TextField 默认把非空 placeholder 作为朗读标签。 */
    @Test
    fun textFieldPlaceholderDefaultRemainsSpokenLabel() {
        /** 简洁公开入口使用的受控字段所有者。 Controlled field owner used by the concise public facade. */
        val controller = PixelTextFieldController()
        /** Empty controlled value allowing the placeholder to remain visible. */
        val state = controller.create()
        /** Runtime with a conflicting theme label exposing accidental placeholder omission. */
        val tester = PixelTester()
        try {
            tester.pumpWidget(
                widget = PixelTheme(
                    tokens = PixelThemeTokens.Default.copy(
                        labels = PixelLabelTokens.Default.copy(textField = "TOKEN FIELD"),
                    ),
                    child = TextField(
                        state = state,
                        controller = controller,
                        placeholder = "Email",
                    ),
                ),
                logicalWidth = 32,
                logicalHeight = 12,
            )
            assertEquals(1, tester.semanticsNodesByLabel("Email").size)
            assertTrue(tester.semanticsNodesByLabel("TOKEN FIELD").isEmpty())
        } finally {
            tester.dispose()
        }
    }

    /** Wraps [child] in the Slider color-token theme used by sentinel tests. */
    private fun sliderTheme(child: Widget): Widget = PixelTheme(tokens = SliderThemeTokens, child = child)

    /** Wraps [child] in the Scrollbar role and width-token theme. */
    private fun scrollbarTheme(child: Widget): Widget {
        /** Scrollbar tokens with an observable two-pixel width and both paint roles. */
        val scrollbarTokens = PixelComponentTokens.Default.scrollbar.copy(minimumWidth = 2)
        /** 安装在简洁入口或状态化入口之外的完整 token 图。 */
        val tokens = PixelThemeTokens.Default.copy(
            colors = PixelColorScheme.Dark.copy(
                onSurface = TokenScrollbarThumbColor,
                track = TokenScrollbarTrackColor,
            ),
            components = PixelComponentTokens.Default.copy(scrollbar = scrollbarTokens),
        )
        return PixelTheme(tokens = tokens, child = child)
    }

    /** 构建省略全部可选视觉通道的简洁 Scrollbar 入口。 Builds the concise Scrollbar facade with every optional visual channel omitted. */
    private fun conciseScrollbar(state: PixelListState, controller: PixelListController): Widget {
        return Scrollbar(
            child = scrollViewport(state, controller),
            state = state,
        )
    }

    /** Builds overflowing solid content so an absent track can be distinguished from paint. */
    private fun scrollViewport(state: PixelListState, controller: PixelListController): Widget {
        return ListViewBuilder(
            itemCount = 20,
            itemBuilder = {
                SizedBox(
                    height = 6,
                    child = Container(
                        width = ScrollbarViewportWidth,
                        height = 6,
                        fillColor = ScrollContentColor,
                    ),
                )
            },
            itemExtent = 6,
            state = state,
            controller = controller,
        )
    }

    private companion object {
        /** Shared Slider render width used by exact pixel assertions. */
        const val SliderWidth: Int = 12

        /** 默认 Slider token 解析出的轨道高度。 */
        const val SliderHeight: Int = 7

        /** Scrollable viewport width. */
        const val ScrollbarViewportWidth: Int = 12

        /** Scrollable viewport height. */
        const val ScrollbarViewportHeight: Int = 12

        /** Theme-resolved Slider active color. */
        val TokenActiveColor: PixelColor = PixelColor.fromRgb(19, 181, 103)

        /** Theme-resolved Slider track color. */
        val TokenTrackColor: PixelColor = PixelColor.fromRgb(23, 41, 73)

        /** 调用方显式传入的填充色覆写。 */
        val ExplicitActiveColor: PixelColor = PixelColor.fromRgb(211, 61, 83)

        /** 调用方显式传入的轨道色覆写。 */
        val ExplicitTrackColor: PixelColor = PixelColor.fromRgb(73, 113, 229)

        /** Theme-resolved Slider border color. */
        val TokenBorderColor: PixelColor = PixelColor.fromRgb(239, 199, 47)

        /** Theme-resolved Slider hard-shadow color. */
        val TokenShadowColor: PixelColor = PixelColor.fromRgb(103, 43, 137)

        /** 用于与滚动条轨道区分的实心子内容颜色。 */
        val ScrollContentColor: PixelColor = PixelColor.fromRgb(31, 79, 127)

        /** Theme-resolved Scrollbar thumb color. */
        val TokenScrollbarThumbColor: PixelColor = PixelColor.fromRgb(241, 87, 47)

        /** Theme-resolved Scrollbar track color. */
        val TokenScrollbarTrackColor: PixelColor = PixelColor.fromRgb(47, 199, 157)

        /** Shared explicit Slider theme with exact paint sentinels. */
        val SliderThemeTokens: PixelThemeTokens = PixelThemeTokens.Default.copy(
            colors = PixelColorScheme.Dark.copy(
                primary = TokenActiveColor,
                track = TokenTrackColor,
                outline = TokenBorderColor,
                shadow = TokenShadowColor,
            ),
        )
    }
}
