package com.purride.pixelui.widgets

import com.purride.pixelcore.PixelColor
import com.purride.pixelui.PixelComponentTokens
import com.purride.pixelui.PixelControlStateSet
import com.purride.pixelui.PixelLabelTokens
import com.purride.pixelui.PixelKey
import com.purride.pixelui.PixelTheme
import com.purride.pixelui.PixelThemeTokens
import com.purride.pixelui.ProgressBar
import com.purride.pixelui.SizedBox
import com.purride.pixelui.Stack
import com.purride.pixelui.Container
import com.purride.pixelui.ValueAdjuster
import com.purride.pixelui.ValueAdjusterStyle
import com.purride.pixelui.testing.PixelTester
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Focused regression coverage for ValueAdjuster and determinate ProgressBar production contracts. */
class ValueAdjusterProgressContractTest {
    /**
     * 简洁 ValueAdjuster 入口等价于 `states = Normal` 的状态化入口；挂载 `PixelTheme` 只改变
     * token 解析结果，不改变入口选择或 widget 树。
     */
    @Test
    fun valueAdjusterConciseFacadeMatchesNormalStatesAndResolvesTokens() {
        /** 同时用于等价性比较与显式主题分支的复用离屏运行时。 */
        val tester = PixelTester()
        try {
            tester.pumpWidget(
                ValueAdjuster(
                    valueText = "5",
                    onDecrease = {},
                    onIncrease = {},
                    key = "adjuster",
                ),
                logicalWidth = 96,
                logicalHeight = 32,
            )
            /** 简洁入口在无提供者时的完整帧。 */
            val concisePixels = requireNotNull(tester.renderResult).buffer.pixels.copyOf()
            /** 简洁入口导出的两个动作命中目标。 */
            val conciseTargets = requireNotNull(tester.renderResult).clickTargets.sortedBy { target ->
                target.bounds.left
            }
            /** 简洁入口导出的整组语义节点。 */
            val conciseGroup = tester
                .semanticsNodesByLabel(PixelLabelTokens.Default.valueAdjuster)
                .single()

            tester.pumpWidget(
                ValueAdjuster(
                    valueText = "5",
                    onDecrease = {},
                    onIncrease = {},
                    states = PixelControlStateSet.Normal,
                    key = "adjuster",
                ),
                logicalWidth = 96,
                logicalHeight = 32,
            )
            /** 状态化入口在同一输入下的参考帧。 */
            val stateAwarePixels = requireNotNull(tester.renderResult).buffer.pixels.copyOf()
            /** 状态化入口导出的参考命中目标。 */
            val stateAwareTargets = requireNotNull(tester.renderResult).clickTargets
                .sortedBy { target -> target.bounds.left }
            /** 状态化入口导出的参考整组语义节点。 */
            val stateAwareGroup = tester
                .semanticsNodesByLabel(PixelLabelTokens.Default.valueAdjuster)
                .single()

            assertTrue(concisePixels.contentEquals(stateAwarePixels))
            assertEquals(stateAwareTargets.size, conciseTargets.size)
            assertEquals(stateAwareTargets[0].bounds, conciseTargets[0].bounds)
            assertEquals(stateAwareTargets[1].bounds, conciseTargets[1].bounds)
            assertEquals(stateAwareGroup.width, conciseGroup.width)
            assertEquals(stateAwareGroup.height, conciseGroup.height)
            // 默认 token 解析出的动作单元宽度：9px 最小宽度加两侧一像素边框。
            assertEquals(2, conciseTargets.size)
            assertEquals(11, conciseTargets[0].bounds.width)
            assertEquals(11, conciseTargets[1].bounds.width)
            assertEquals(50, conciseGroup.width)
            assertEquals(13, conciseGroup.height)

            tester.pumpWidget(
                PixelTheme(
                    tokens = PixelThemeTokens.Default,
                    child = ValueAdjuster(
                        valueText = "5",
                        onDecrease = {},
                        onIncrease = {},
                        key = "adjuster",
                    ),
                ),
                logicalWidth = 96,
                logicalHeight = 32,
            )
            /** 挂载与默认值相同的 token 图后，同一简洁入口的帧。 */
            val defaultProviderPixels = requireNotNull(tester.renderResult).buffer.pixels.copyOf()
            // 挂载 PixelTheme 只改变 token 解析结果；提供 Default 图与不提供必须完全一致。
            assertTrue(defaultProviderPixels.contentEquals(concisePixels))

            /** Disabled action fill sentinel resolved from the explicit theme. */
            val disabledFill = PixelColor.fromRgb(173, 43, 67)
            /** 禁用态字形哨兵色，证明前景来自 onDisabled 角色而不是任何固定值。 */
            val disabledGlyph = PixelColor.fromRgb(211, 223, 79)
            /** Explicit token graph with independently observable labels, geometry, and colors. */
            val themedTokens = PixelThemeTokens.Default.copy(
                colors = PixelThemeTokens.Default.colors.copy(
                    disabled = disabledFill,
                    onDisabled = disabledGlyph,
                ),
                labels = PixelLabelTokens.Default.copy(
                    valueAdjuster = "TOKEN ADJUSTER",
                    decrease = "TOKEN DECREASE",
                    increase = "TOKEN INCREASE",
                ),
                components = PixelComponentTokens.Default.copy(
                    valueAdjuster = PixelComponentTokens.Default.valueAdjuster.copy(
                        minimumWidth = 17,
                        minimumHeight = 15,
                    ),
                ),
            )
            tester.pumpWidget(
                PixelTheme(
                    tokens = themedTokens,
                    child = ValueAdjuster(
                        valueText = "5",
                        onDecrease = null,
                        onIncrease = {},
                        key = "themed-adjuster",
                    ),
                ),
                logicalWidth = 96,
                logicalHeight = 32,
            )
            assertTrue(tester.hasPixel(disabledFill))
            assertTrue(tester.hasPixel(disabledGlyph))
            assertTrue(tester.semanticsNodesByLabel("TOKEN ADJUSTER").isNotEmpty())
            /** Token minimum width plus both one-pixel outline edges defines each action cell. */
            val themedIncrease = tester.semanticsNodesByLabel("TOKEN INCREASE").single()
            assertEquals(19, themedIncrease.width)
            assertEquals(15, themedIncrease.height)
            // 主题只替换解析结果；默认 token 下的整组几何不再出现。
            assertTrue(tester.semanticsNodesByLabel(PixelLabelTokens.Default.valueAdjuster).isEmpty())
        } finally {
            tester.dispose()
        }
    }

    /** Every style channel paints independently and the focused tree exports one focused node. */
    @Test
    fun valueAdjusterStyleChannelsAndGroupFocusRemainIndependent() {
        /** Outline sentinel for the base component border and dividers. */
        val border = PixelColor.fromRgb(19, 47, 83)
        /** Enabled action-cell fill sentinel. */
        val fill = PixelColor.fromRgb(101, 29, 59)
        /** Enabled plus/minus glyph sentinel. */
        val symbol = PixelColor.fromRgb(227, 191, 31)
        /** Controlled value text sentinel. */
        val value = PixelColor.fromRgb(37, 211, 173)
        /** Disabled action fill and glyph sentinel. */
        val disabled = PixelColor.fromRgb(127, 131, 137)
        /** Additive focus outline sentinel. */
        val focus = PixelColor.fromRgb(251, 71, 181)
        /** Complete explicit style whose six channels are all observable. */
        val style = ValueAdjusterStyle(
            borderColor = border,
            buttonFillColor = fill,
            buttonSymbolColor = symbol,
            valueTextColor = value,
            disabledColor = disabled,
            focusColor = focus,
        )
        /** Runtime owning the automatic focus node and rendered pixel buffer. */
        val tester = PixelTester()
        try {
            tester.pumpWidget(
                PixelTheme(
                    tokens = PixelThemeTokens.Default,
                    child = ValueAdjuster(
                        valueText = "7",
                        onDecrease = null,
                        onIncrease = {},
                        style = style,
                        key = "styled-adjuster",
                    ),
                ),
                logicalWidth = 96,
                logicalHeight = 32,
            )
            listOf(border, fill, symbol, value, disabled).forEach { channel ->
                assertTrue("ValueAdjuster missed style channel $channel", tester.hasPixel(channel))
            }

            assertTrue(tester.pressKey(PixelKey.TAB))
            assertTrue(tester.hasPixel(focus))
            /** Entire accessibility tree must expose the real group focus exactly once. */
            val focusedNodes = tester.semanticsNodes().filter { node -> node.focused }
            assertEquals(1, focusedNodes.size)
            assertEquals("ValueAdjuster", focusedNodes.single().label)
            assertTrue(tester.semanticsNodesByLabel("Decrease").none { node -> node.focused })
            assertTrue(tester.semanticsNodesByLabel("Increase").none { node -> node.focused })
        } finally {
            tester.dispose()
        }
    }

    /** Narrow constraints clip paint, pointer targets, and semantics to one shared 21x7 viewport. */
    @Test
    fun valueAdjusterNarrowViewportKeepsEveryOutputChannelInsideBounds() {
        /** Guard background used to detect paint escaping the constrained adjuster viewport. */
        val guard = PixelColor.fromRgb(7, 13, 23)
        /** Number of successful decrement callbacks. */
        var decreases = 0
        /** Number of successful increment callbacks. */
        var increases = 0
        /** Runtime exposing rendered paint, click targets, and the complete semantics tree. */
        val tester = PixelTester()
        try {
            tester.pumpWidget(
                Stack(
                    children = listOf(
                        Container(width = 36, height = 16, fillColor = guard),
                        SizedBox(
                            width = 21,
                            height = 7,
                            child = ValueAdjuster(
                                valueText = "123456",
                                onDecrease = { decreases += 1 },
                                onIncrease = { increases += 1 },
                                key = "narrow-adjuster",
                            ),
                        ),
                    ),
                ),
                logicalWidth = 36,
                logicalHeight = 16,
            )
            /** Both constrained actions remain reachable after symmetric cell reflow. */
            val clickTargets = requireNotNull(tester.renderResult).clickTargets.sortedBy { target ->
                target.bounds.left
            }
            assertEquals(2, clickTargets.size)
            clickTargets.forEach { target ->
                assertTrue(target.bounds.left >= 0)
                assertTrue(target.bounds.top >= 0)
                assertTrue(target.bounds.right <= 21)
                assertTrue(target.bounds.bottom <= 7)
                target.onClick()
            }
            assertEquals(1, decreases)
            assertEquals(1, increases)

            tester.semanticsNodes().forEach { node ->
                assertTrue("semantic left escaped: $node", node.left >= 0)
                assertTrue("semantic top escaped: $node", node.top >= 0)
                assertTrue("semantic right escaped: $node", node.left + node.width <= 21)
                assertTrue("semantic bottom escaped: $node", node.top + node.height <= 7)
            }
            /** Pixels right of the viewport prove horizontal paint cannot leak through SizedBox. */
            val buffer = requireNotNull(tester.renderResult).buffer
            for (y in 0 until 16) {
                for (x in 21 until 36) {
                    assertEquals("paint escaped at ($x,$y)", guard, buffer.getPixel(x, y))
                }
            }
            /** Pixels below the viewport prove short-height glyphs and borders remain clipped. */
            for (y in 7 until 16) {
                for (x in 0 until 21) {
                    assertEquals("paint escaped at ($x,$y)", guard, buffer.getPixel(x, y))
                }
            }
        } finally {
            tester.dispose()
        }
    }

    /** Determinate progress safely normalizes non-finite input and malformed geometry. */
    @Test
    fun progressBarNormalizesNonFiniteValuesAndRejectsNegativeGeometryLeaks() {
        /** Runtime reused across every malformed progress input. */
        val tester = PixelTester()
        try {
            /** Input-to-semantic cases covering NaN and both infinity endpoints. */
            val cases = listOf(
                Float.NaN to "0%",
                Float.NEGATIVE_INFINITY to "0%",
                Float.POSITIVE_INFINITY to "100%",
            )
            cases.forEach { (input, expectedValue) ->
                tester.pumpWidget(
                    ProgressBar(
                        progress = input,
                        states = PixelControlStateSet.Normal,
                        width = 10,
                        height = 5,
                    ),
                    logicalWidth = 24,
                    logicalHeight = 16,
                )
                /** Progress semantics proving no non-finite value reaches percentage formatting. */
                val node = tester.semanticsNodesByLabel("Progress").single()
                assertEquals(expectedValue, node.value)
                assertTrue(node.width >= 0)
                assertTrue(node.height >= 0)
            }

            tester.pumpWidget(
                ProgressBar(
                    progress = 0.5f,
                    states = PixelControlStateSet.Normal,
                    width = -20,
                    height = -4,
                ),
                logicalWidth = 8,
                logicalHeight = 8,
            )
            /** Negative dimensions collapse safely before the component minimum is applied. */
            val malformedNode = tester.semanticsNodesByLabel("Progress").single()
            assertEquals(0, malformedNode.width)
            assertTrue(malformedNode.height >= 0)

            /** 无提供者的简洁入口同样必须接受畸形尺寸且不抛异常。 */
            tester.pumpWidget(
                ProgressBar(progress = 0.5f, width = -20, height = -4),
                logicalWidth = 8,
                logicalHeight = 8,
            )
            assertFalse(tester.hasPixel(PixelColor.fromRgb(80, 180, 110)))
        } finally {
            tester.dispose()
        }
    }

    /** 简洁 ProgressBar 与状态化实现等价，并同样消费组件 minimumWidth token。 */
    @Test
    fun progressBarConciseFacadeMatchesStateAwareAndConsumesMinimumWidth() {
        /** 显式填充哨兵色。 Explicit active fill sentinel. */
        val active = PixelColor.fromRgb(23, 149, 83)
        /** 显式轨道哨兵色。 Explicit track sentinel. */
        val track = PixelColor.fromRgb(41, 43, 47)
        /** 同时用于等价性比较与显式主题分支的复用运行时。 Runtime reused for the equivalence comparison and the explicit-theme branch. */
        val tester = PixelTester()
        try {
            tester.pumpWidget(
                ProgressBar(
                    progress = 0.5f,
                    width = 10,
                    height = 5,
                    color = active,
                    trackColor = track,
                ),
                logicalWidth = 16,
                logicalHeight = 8,
            )
            /** 简洁入口在无提供者时的像素与语义节点。 */
            val concisePixels = requireNotNull(tester.renderResult).buffer.pixels.copyOf()
            val conciseNode = tester.semanticsNodesByLabel(PixelLabelTokens.Default.progress).single()

            tester.pumpWidget(
                ProgressBar(
                    progress = 0.5f,
                    states = PixelControlStateSet.Normal,
                    width = 10,
                    height = 5,
                    color = active,
                    trackColor = track,
                ),
                logicalWidth = 16,
                logicalHeight = 8,
            )
            /** 状态化入口在同一输入下的参考像素与语义节点。 */
            val stateAwarePixels = requireNotNull(tester.renderResult).buffer.pixels.copyOf()
            val stateAwareNode = tester.semanticsNodesByLabel(PixelLabelTokens.Default.progress).single()
            assertTrue(concisePixels.contentEquals(stateAwarePixels))
            assertEquals(stateAwareNode.width, conciseNode.width)
            assertEquals(stateAwareNode.height, conciseNode.height)
            assertTrue(tester.hasPixel(active))
            assertTrue(tester.hasPixel(track))

            /** Explicit progress token with a minimum wider than both caller and foundation defaults. */
            val progressTokens = PixelThemeTokens.Default.copy(
                labels = PixelLabelTokens.Default.copy(progress = "TOKEN PROGRESS"),
                components = PixelComponentTokens.Default.copy(
                    progress = PixelComponentTokens.Default.progress.copy(minimumWidth = 63),
                ),
            )
            tester.pumpWidget(
                PixelTheme(
                    tokens = progressTokens,
                    child = ProgressBar(progress = 0.5f, width = 10, height = 5),
                ),
                logicalWidth = 80,
                logicalHeight = 16,
            )
            /** 简洁入口在显式提供者下同样委托到 token 实现。 */
            val themedNode = tester.semanticsNodesByLabel("TOKEN PROGRESS").single()
            assertEquals(63, themedNode.width)
            assertEquals(7, themedNode.height)
        } finally {
            tester.dispose()
        }
    }
}
