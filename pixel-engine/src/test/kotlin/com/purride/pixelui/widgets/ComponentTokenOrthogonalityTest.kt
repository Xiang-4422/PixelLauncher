package com.purride.pixelui.widgets

import com.purride.pixelcore.PixelColor
import com.purride.pixelui.Alignment
import com.purride.pixelui.Container
import com.purride.pixelui.EdgeInsets
import com.purride.pixelui.PixelColorRole
import com.purride.pixelui.PixelColorScheme
import com.purride.pixelui.PixelComponentColorTokens
import com.purride.pixelui.PixelComponentTokens
import com.purride.pixelui.PixelControlState
import com.purride.pixelui.PixelControlStateSet
import com.purride.pixelui.PixelElevationRole
import com.purride.pixelui.PixelFocusIndicatorTokens
import com.purride.pixelui.PixelKey
import com.purride.pixelui.PixelSemanticsNode
import com.purride.pixelui.PixelSizeTokens
import com.purride.pixelui.PixelSpacingTokens
import com.purride.pixelui.PixelStateProperty
import com.purride.pixelui.PixelTheme
import com.purride.pixelui.PixelThemeTokens
import com.purride.pixelui.Slidable
import com.purride.pixelui.SlidableAction
import com.purride.pixelui.Switch
import com.purride.pixelui.Tabs
import com.purride.pixelui.Text
import com.purride.pixelui.TextAlign
import com.purride.pixelui.TextOverflow
import com.purride.pixelui.TextStyle
import com.purride.pixelui.Widget
import com.purride.pixelui.testing.PixelTester
import com.purride.pixelui.testing.find
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 组件 token 独立绘制、几何与 token 传播的回归覆盖。 Regression coverage for independent component-token paint, geometry, and token propagation. */
class ComponentTokenOrthogonalityTest {
    /** Equivalent encoded and literal geometry keeps Switch and Tabs frames byte-for-byte equal. */
    @Test
    fun geometryOnlyTokenCopiesKeepExactStatePixels() {
        /** Distinct role palette making every accidental channel switch observable. */
        val colors = orthogonalColors()
        /** Foundation sizes that resolve the default Switch encodings to non-default geometry. */
        val sizes = PixelSizeTokens.Default.copy(switchWidth = 19, trackHeight = 11)
        /** Foundation spacing that resolves encoded Tab padding to uncommon literal values. */
        val spacing = PixelSpacingTokens.Default.copy(extraSmall = 3, small = 5)
        /** Theme retaining encoded component geometry. */
        val encodedTheme = PixelThemeTokens.Dark.copy(
            colors = colors,
            sizes = sizes,
            spacing = spacing,
        )
        /** Same resolved geometry expressed as literal copied component fields. */
        val literalTheme = encodedTheme.copy(
            components = encodedTheme.components.copy(
                switch = encodedTheme.components.switch.copy(
                    minimumWidth = 19,
                    minimumHeight = 11,
                ),
                tabs = encodedTheme.components.tabs.copy(
                    padding = EdgeInsets.all(5),
                ),
            ),
        )

        ORTHOGONAL_STATES.forEach { states ->
            /** Controlled checked value matching the selected state in this matrix row. */
            val checked = PixelControlState.Selected in states
            /** 标准编码 token 渲染出的 Switch 帧，作为逐像素参考。 */
            val encodedSwitch = renderPixels(
                widget = Switch(
                    checked = checked,
                    onChanged = {},
                    states = states,
                    semanticLabel = "ORTHOGONAL SWITCH",
                ),
                theme = encodedTheme,
            )
            /** Literal-geometry Switch frame that must preserve every paint and feedback pixel. */
            val literalSwitch = renderPixels(
                widget = Switch(
                    checked = checked,
                    onChanged = {},
                    states = states,
                    semanticLabel = "ORTHOGONAL SWITCH",
                ),
                theme = literalTheme,
            )
            assertTrue(
                "Switch geometry copy changed ${states.highestPriority()} pixels",
                encodedSwitch.contentEquals(literalSwitch),
            )

            /** Encoded Tab frame covering selected and unselected items together. */
            val encodedTabs = renderPixels(
                widget = Tabs(
                    labels = listOf("A", "B"),
                    selectedIndex = if (checked) 0 else 1,
                    onSelected = {},
                    states = states,
                ),
                theme = encodedTheme,
            )
            /** Literal-padding Tab frame with the same fully resolved geometry. */
            val literalTabs = renderPixels(
                widget = Tabs(
                    labels = listOf("A", "B"),
                    selectedIndex = if (checked) 0 else 1,
                    onSelected = {},
                    states = states,
                ),
                theme = literalTheme,
            )
            assertTrue(
                "Tabs geometry copy changed ${states.highestPriority()} pixels",
                encodedTabs.contentEquals(literalTabs),
            )
        }

        /** Actual pointer-hover frame covering the retained feedback-motion target. */
        val encodedHover = renderHoveredSwitch(encodedTheme)
        /** Literal-geometry pointer-hover frame that must use the identical feedback fraction. */
        val literalHover = renderHoveredSwitch(literalTheme)
        assertTrue(
            "Switch geometry copy changed runtime hover feedback pixels",
            encodedHover.contentEquals(literalHover),
        )
    }

    /** Container, content, and border overrides change only their own Switch and Tab pixels. */
    @Test
    fun colorOnlyCopiesChangeOnlyTheTargetChannel() {
        /** Distinct role palette used to identify exact from/to channel replacements. */
        val colors = orthogonalColors()
        /** Unmodified theme used as both component baselines. */
        val baseTheme = PixelThemeTokens.Dark.copy(colors = colors)
        /** Default Switch tokens whose three fields are varied independently. */
        val switchTokens = baseTheme.components.switch
        /** Baseline unchecked Switch frame with inactive outline and thumb. */
        val baseSwitch = renderSwitch(baseTheme)

        /** Container-only Switch theme replacing the track Surface role. */
        val switchContainerTheme = baseTheme.withSwitchTokens(
            switchTokens.copy(
                containerColor = PixelStateProperty.constant(PixelColorRole.Warning),
            ),
        )
        assertOnlyColorReplacement(
            before = baseSwitch,
            after = renderSwitch(switchContainerTheme),
            from = colors.surface,
            to = colors.warning,
            message = "Switch container",
        )

        /** Content-only Switch theme replacing the thumb's inactive role. */
        val switchContentTheme = baseTheme.withSwitchTokens(
            switchTokens.copy(
                contentColor = PixelStateProperty.constant(PixelColorRole.Danger),
            ),
        )
        assertOnlyColorReplacement(
            before = baseSwitch,
            after = renderSwitch(switchContentTheme),
            from = colors.inactive,
            to = colors.danger,
            message = "Switch content",
        )

        /** Border-only Switch theme replacing the outline's inactive role. */
        val switchBorderTheme = baseTheme.withSwitchTokens(
            switchTokens.copy(
                borderColor = PixelStateProperty.constant(PixelColorRole.Selection),
            ),
        )
        assertOnlyColorReplacement(
            before = baseSwitch,
            after = renderSwitch(switchBorderTheme),
            from = colors.inactive,
            to = colors.selection,
            message = "Switch border",
        )

        /** Default Tab tokens whose selected item exposes all three independent channels. */
        val tabTokens = baseTheme.components.tabs
        /** Baseline selected single-Tab frame. */
        val baseTabs = renderTabs(baseTheme)
        /** 只改容器角色的 Tab 主题，用于填充默认透明的标签表面。 */
        val tabContainerTheme = baseTheme.withTabTokens(
            tabTokens.copy(
                containerColor = PixelStateProperty.constant(PixelColorRole.SurfaceVariant),
            ),
        )
        assertOnlyColorReplacement(
            before = baseTabs,
            after = renderTabs(tabContainerTheme),
            from = PixelColor.Transparent,
            to = colors.surfaceVariant,
            message = "Tabs container",
        )

        /** Content-only Tab theme replacing the selected label foreground. */
        val tabContentTheme = baseTheme.withTabTokens(
            tabTokens.copy(
                contentColor = PixelStateProperty.constant(PixelColorRole.Danger),
            ),
        )
        assertOnlyColorReplacement(
            before = baseTabs,
            after = renderTabs(tabContentTheme),
            from = colors.onBackground,
            to = colors.danger,
            message = "Tabs content",
        )

        /** Border-only Tab theme replacing the selected Primary outline. */
        val tabBorderTheme = baseTheme.withTabTokens(
            tabTokens.copy(
                borderColor = PixelStateProperty.constant(PixelColorRole.Warning),
            ),
        )
        assertOnlyColorReplacement(
            before = baseTabs,
            after = renderTabs(tabBorderTheme),
            from = colors.primary,
            to = colors.warning,
            message = "Tabs border",
        )
    }

    /** Tabs 保持一像素默认间距，显式间距 token 控制主题化布局。 Tabs keeps its one-pixel default gap while explicit spacing tokens control themed layouts. */
    @Test
    fun tabsSpacingUsesFoundationTokenWithExactDefault() {
        assertEquals(1, tabGap(PixelThemeTokens.Dark))
        /** 紧凑间距哨兵值必须替换默认一像素间距的主题。 Theme whose compact spacing sentinel must replace the default one-pixel gap. */
        val spacedTheme = PixelThemeTokens.Dark.copy(
            spacing = PixelSpacingTokens.Default.copy(extraSmall = 4),
        )
        assertEquals(4, tabGap(spacedTheme))
    }

    /** 无 PixelTheme 提供者时，简洁 Slidable API 与状态化实现渲染出完全一致的像素。 */
    @Test
    fun conciseSlidableFacadesMatchStateAwareWithoutProvider() {
        /** 显式动作背景哨兵色。 Explicit action background sentinel. */
        val background = PixelColor.fromRgb(171, 43, 67)
        /** 显式动作前景哨兵色。 Explicit action foreground sentinel. */
        val foreground = PixelColor.fromRgb(239, 231, 197)
        /** 简洁 SlidableAction 入口的当前帧。 */
        val concisePixels = renderPixels(
            widget = SlidableAction(
                label = "A",
                backgroundColor = background,
                foregroundColor = foreground,
                onPressed = {},
            ),
            theme = null,
            logicalWidth = 32,
            logicalHeight = 16,
        )
        /** 现代状态化入口在同一输入下的参考帧。 */
        val stateAwarePixels = renderPixels(
            widget = SlidableAction(
                label = "A",
                onPressed = {},
                states = PixelControlStateSet.Normal,
                backgroundColor = background,
                foregroundColor = foreground,
            ),
            theme = null,
            logicalWidth = 32,
            logicalHeight = 16,
        )
        assertTrue(concisePixels.contentEquals(stateAwarePixels))

        /** 行子内容颜色，证明两个入口共用同一表面组合。 Row child color proving both entry points share one surface composition. */
        val rowColor = PixelColor.fromRgb(29, 103, 157)
        /** 简洁 Slidable 行的当前帧。 */
        val conciseRowPixels = renderPixels(
            widget = conciseRow(rowColor),
            theme = null,
            logicalWidth = 40,
            logicalHeight = 16,
        )
        /** 现代状态化 Slidable 行的参考帧。 */
        val stateAwareRowPixels = renderPixels(
            widget = stateAwareRow(rowColor),
            theme = null,
            logicalWidth = 40,
            logicalHeight = 16,
        )
        assertTrue(conciseRowPixels.contentEquals(stateAwareRowPixels))
    }

    /** 显式主题让两个简洁 Slidable 入口消费全部几何与焦点 token。 Explicit themes opt both concise Slidable facades into every geometry and focus token. */
    @Test
    fun explicitThemePropagatesThroughConciseSlidableFacades() {
        /** 必须优先于主题容器角色的显式动作填充色。 Explicit action fill that must outrank the theme container role. */
        val explicitBackground = PixelColor.fromRgb(181, 47, 73)
        /** 必须优先于主题内容角色的显式动作前景色。 Explicit action foreground that must outrank the theme content role. */
        val explicitForeground = PixelColor.fromRgb(247, 235, 199)
        /** Row child sentinel retained inside the themed Slidable surface. */
        val rowColor = PixelColor.fromRgb(31, 109, 163)
        /** Complete themed Slidable graph with unique geometry/elevation/focus sentinels. */
        val themedTokens = slidableTheme(cornerRadius = 2)

        /** 无提供者时默认 token 的动作几何参考。 Default-token action geometry reference. */
        val scopeLessAction = captureSnapshot(
            widget = SlidableAction("A", explicitBackground, explicitForeground, onPressed = {}),
            theme = null,
            label = "A",
        )
        /** 同一简洁入口置于显式 token 提供者之下。 The same concise facade under an explicit token provider. */
        val themedAction = captureSnapshot(
            widget = SlidableAction("A", explicitBackground, explicitForeground, onPressed = {}),
            theme = themedTokens,
            label = "A",
        )
        assertTrue(themedAction.node.width > scopeLessAction.node.width)
        assertTrue(themedAction.node.height > scopeLessAction.node.height)
        assertTrue(themedAction.hasColor(explicitBackground))
        assertTrue(themedAction.hasColor(explicitForeground))
        assertTrue(themedAction.hasColor(themedTokens.colors.outline))
        assertTrue(themedAction.hasColor(themedTokens.colors.shadow))

        /** 方角变体，证明简洁入口消费圆角字段。 Square-corner variant proving the concise facade consumes the radius field. */
        val squareAction = captureSnapshot(
            widget = SlidableAction("A", explicitBackground, explicitForeground, onPressed = {}),
            theme = slidableTheme(cornerRadius = 0),
            label = "A",
        )
        assertEquals(themedAction.node.width, squareAction.node.width)
        assertEquals(themedAction.node.height, squareAction.node.height)
        assertTrue(!themedAction.pixels.contentEquals(squareAction.pixels))

        /** 聚焦的主题化动作，证明 slidable 焦点 token 参与解析。 Focused themed action proving the slidable focus token participates. */
        val focusedAction = captureSnapshot(
            widget = SlidableAction("A", explicitBackground, explicitForeground, onPressed = {}),
            theme = themedTokens,
            label = "A",
            focusFirst = true,
        )
        assertTrue(focusedAction.hasColor(themedTokens.colors.focus))

        /** 无提供者时默认 token 的行几何参考。 Default-token row geometry reference. */
        val scopeLessRow = captureSnapshot(
            widget = conciseRow(rowColor),
            theme = null,
            label = DEFAULT_SLIDABLE_LABEL,
        )
        /** 同一简洁行在显式主题下消费完整表面 token。 The same concise row after an explicit theme opts it into complete surface tokens. */
        val themedRow = captureSnapshot(
            widget = conciseRow(rowColor),
            theme = themedTokens,
            label = DEFAULT_SLIDABLE_LABEL,
        )
        assertTrue(themedRow.node.height > scopeLessRow.node.height)
        assertTrue(themedRow.hasColor(rowColor))
        assertTrue(themedRow.hasColor(themedTokens.colors.surfaceVariant))
        assertTrue(themedRow.hasColor(themedTokens.colors.outline))
        assertTrue(themedRow.hasColor(themedTokens.colors.shadow))

        /** 聚焦的主题化行，证明简洁入口消费焦点指示器 token。 Focused themed row proving the concise facade consumes the focus indicator token. */
        val focusedRow = captureSnapshot(
            widget = conciseRow(rowColor),
            theme = themedTokens,
            label = DEFAULT_SLIDABLE_LABEL,
            focusFirst = true,
        )
        assertTrue(focusedRow.hasColor(themedTokens.colors.focus))
    }

    /** 显式 token 主题下，简洁 Slidable API 与状态化实现的像素与语义节点完全一致。 */
    @Test
    fun conciseSlidableFacadesMatchStateAwareUnderTokenTheme() {
        /** 同时暴露表面、边框与焦点通道的独立 token 图。 Distinct token graph exposing surface, outline, and focus channels. */
        val themedTokens = slidableTheme(cornerRadius = 2)
        /** 必须优先于解析出的 token 角色的显式动作颜色。 Explicit action colors that must remain above resolved token roles. */
        val actionBackground = PixelColor.fromRgb(179, 41, 69)
        val actionForeground = PixelColor.fromRgb(249, 237, 203)
        /** 简洁 SlidableAction 在显式主题下的帧与语义节点。 */
        val conciseAction = captureSnapshot(
            widget = SlidableAction("A", actionBackground, actionForeground, onPressed = {}),
            theme = themedTokens,
            label = "A",
        )
        /** 现代状态化 SlidableAction 的参考帧与语义节点。 */
        val stateAwareAction = captureSnapshot(
            widget = SlidableAction(
                label = "A",
                onPressed = {},
                states = PixelControlStateSet.Normal,
                backgroundColor = actionBackground,
                foregroundColor = actionForeground,
            ),
            theme = themedTokens,
            label = "A",
        )
        assertEquals(stateAwareAction.node.width, conciseAction.node.width)
        assertEquals(stateAwareAction.node.height, conciseAction.node.height)
        assertTrue(conciseAction.pixels.contentEquals(stateAwareAction.pixels))
        assertTrue(conciseAction.hasColor(actionBackground))
        assertTrue(conciseAction.hasColor(actionForeground))
        assertTrue(conciseAction.hasColor(themedTokens.colors.outline))
        /** Separate focused frame because the additive indicator intentionally covers a 1px border. */
        val focusedConciseAction = captureSnapshot(
            widget = SlidableAction("A", actionBackground, actionForeground, onPressed = {}),
            theme = themedTokens,
            label = "A",
            focusFirst = true,
        )
        assertTrue(focusedConciseAction.hasColor(themedTokens.colors.focus))

        /** 固定行子内容，证明两个 Slidable 入口共用同一装饰表面。 Fixed row child proving both Slidable entry points share one decorated surface. */
        val rowColor = PixelColor.fromRgb(23, 101, 159)
        /** 简洁 Slidable 行在显式主题下的帧与语义节点。 */
        val conciseRowSnapshot = captureSnapshot(
            widget = conciseRow(rowColor),
            theme = themedTokens,
            label = DEFAULT_SLIDABLE_LABEL,
        )
        /** 现代状态化 Slidable 行的参考帧与语义节点。 */
        val stateAwareRowSnapshot = captureSnapshot(
            widget = stateAwareRow(rowColor),
            theme = themedTokens,
            label = DEFAULT_SLIDABLE_LABEL,
        )
        assertEquals(stateAwareRowSnapshot.node.height, conciseRowSnapshot.node.height)
        assertTrue(conciseRowSnapshot.pixels.contentEquals(stateAwareRowSnapshot.pixels))
        assertTrue(conciseRowSnapshot.hasColor(rowColor))
        assertTrue(conciseRowSnapshot.hasColor(themedTokens.colors.surfaceVariant))
        assertTrue(conciseRowSnapshot.hasColor(themedTokens.colors.outline))
        /** 聚焦帧证明叠加焦点独立于其覆盖的边框。 Focused frame proves additive focus independently from its covered border. */
        val focusedConciseRow = captureSnapshot(
            widget = conciseRow(rowColor),
            theme = themedTokens,
            label = DEFAULT_SLIDABLE_LABEL,
            focusFirst = true,
        )
        assertTrue(focusedConciseRow.hasColor(themedTokens.colors.focus))
    }

    /** Renders one unchecked Switch frame for channel-isolation assertions. */
    private fun renderSwitch(theme: PixelThemeTokens): IntArray {
        return renderPixels(
            widget = Switch(
                checked = false,
                onChanged = {},
                states = PixelControlStateSet.Normal,
                semanticLabel = "CHANNEL SWITCH",
            ),
            theme = theme,
        )
    }

    /** Renders one real retained Switch hover transition without forcing a declarative state. */
    private fun renderHoveredSwitch(theme: PixelThemeTokens): IntArray {
        /** Off-screen runtime routing hover through the production interaction target. */
        val tester = PixelTester()
        return try {
            tester.pumpWidget(
                widget = PixelTheme(
                    tokens = theme,
                    child = Switch(
                        checked = false,
                        onChanged = {},
                        states = PixelControlStateSet.Normal,
                        semanticLabel = "HOVER SWITCH",
                        key = "hover-switch",
                    ),
                ),
                logicalWidth = 80,
                logicalHeight = 24,
            )
            tester.hover(find.byKey("hover-switch"))
            requireNotNull(tester.renderResult).buffer.pixels.copyOf()
        } finally {
            tester.dispose()
        }
    }

    /** Renders one selected Tab frame for channel-isolation assertions. */
    private fun renderTabs(theme: PixelThemeTokens): IntArray {
        return renderPixels(
            widget = Tabs(
                labels = listOf("A"),
                selectedIndex = 0,
                onSelected = {},
                states = PixelControlStateSet.Normal,
            ),
            theme = theme,
        )
    }

    /** Copies one Switch token set into an otherwise unchanged theme. */
    private fun PixelThemeTokens.withSwitchTokens(
        tokens: PixelComponentColorTokens,
    ): PixelThemeTokens {
        return copy(components = components.copy(switch = tokens))
    }

    /** Copies one Tabs token set into an otherwise unchanged theme. */
    private fun PixelThemeTokens.withTabTokens(
        tokens: PixelComponentColorTokens,
    ): PixelThemeTokens {
        return copy(components = components.copy(tabs = tokens))
    }

    /** Returns the logical gap between two public Tab semantic bounds. */
    private fun tabGap(theme: PixelThemeTokens): Int {
        /** Off-screen runtime exposing exact logical semantic coordinates. */
        val tester = PixelTester()
        return try {
            tester.pumpWidget(
                widget = PixelTheme(
                    tokens = theme,
                    child = Tabs(labels = listOf("A", "B"), selectedIndex = 0, onSelected = {}),
                ),
                logicalWidth = 64,
                logicalHeight = 20,
            )
            /** First public Tab semantic bound. */
            val first = tester.semanticsNodesByLabel("A").single()
            /** Second public Tab semantic bound. */
            val second = tester.semanticsNodesByLabel("B").single()
            second.left - (first.left + first.width)
        } finally {
            tester.dispose()
        }
    }

    /** 围绕一个固定高度着色子内容构建简洁 Slidable 行入口。 */
    private fun conciseRow(color: PixelColor): Widget {
        return Slidable(
            child = Container(width = 20, height = 7, fillColor = color),
            onTap = {},
            key = "concise-row",
        )
    }

    /** 构建作为等价性参考的状态化 Slidable 行。 Builds the state-aware Slidable row used as the equivalence reference. */
    private fun stateAwareRow(color: PixelColor): Widget {
        return Slidable(
            child = Container(width = 20, height = 7, fillColor = color),
            states = PixelControlStateSet.Normal,
            onTap = {},
            key = "concise-row",
        )
    }

    /** Creates a unique complete Slidable theme while varying only its radius encoding. */
    private fun slidableTheme(cornerRadius: Int): PixelThemeTokens {
        /** Distinct scheme exposing surface, border, shadow, and focus channels. */
        val colors = orthogonalColors().copy(
            surfaceVariant = PixelColor.fromRgb(43, 83, 127),
            outline = PixelColor.fromRgb(233, 137, 31),
            shadow = PixelColor.fromRgb(79, 27, 109),
            focus = PixelColor.fromRgb(13, 229, 241),
        )
        /** Slidable token graph carrying non-default padding, border, radius, elevation, and focus. */
        val slidable = PixelComponentTokens.Default.slidable.copy(
            containerColor = PixelStateProperty.constant(PixelColorRole.SurfaceVariant),
            contentColor = PixelStateProperty.constant(PixelColorRole.OnSurface),
            borderColor = PixelStateProperty.constant(PixelColorRole.Outline),
            focusIndicator = PixelFocusIndicatorTokens(
                colorRole = PixelColorRole.Focus,
                width = 1,
                inset = 0,
            ),
            padding = EdgeInsets.all(3),
            borderWidth = 2,
            cornerRadius = cornerRadius,
            elevationRole = PixelElevationRole.High,
        )
        return PixelThemeTokens.Dark.copy(
            colors = colors,
            components = PixelComponentTokens.Default.copy(slidable = slidable),
        )
    }

    /** Renders a widget into a defensive full-frame ARGB copy. */
    private fun renderPixels(
        widget: Widget,
        theme: PixelThemeTokens?,
        logicalWidth: Int = 80,
        logicalHeight: Int = 24,
    ): IntArray {
        /** Off-screen runtime owning the temporary retained tree. */
        val tester = PixelTester()
        return try {
            /** 可选显式提供者；null 走继承的 Default token 图。 Optional explicit provider; null exercises the inherited Default token graph. */
            val root = theme?.let { tokens -> PixelTheme(tokens = tokens, child = widget) } ?: widget
            tester.pumpWidget(root, logicalWidth = logicalWidth, logicalHeight = logicalHeight)
            requireNotNull(tester.renderResult).buffer.pixels.copyOf()
        } finally {
            tester.dispose()
        }
    }

    /** Captures one rendered frame and its exact public semantic node. */
    private fun captureSnapshot(
        widget: Widget,
        theme: PixelThemeTokens?,
        label: String,
        focusFirst: Boolean = false,
    ): WidgetSnapshot {
        /** Off-screen runtime retained through optional keyboard focus. */
        val tester = PixelTester()
        return try {
            /** 可选提供者用于区分默认 token 与显式主题解析。 Optional provider distinguishing default-token and explicit-theme resolution. */
            val root = theme?.let { tokens -> PixelTheme(tokens = tokens, child = widget) } ?: widget
            tester.pumpWidget(root, logicalWidth = 48, logicalHeight = 24)
            if (focusFirst) {
                assertTrue(tester.pressKey(PixelKey.TAB))
                tester.pumpFrame(0)
            }
            /** Exact public semantic node after the final rendered state. */
            val node = tester.semanticsNodesByLabel(label).single()
            /** Defensive pixels retained after the temporary runtime is disposed. */
            val pixels = requireNotNull(tester.renderResult).buffer.pixels.copyOf()
            WidgetSnapshot(pixels = pixels, node = node)
        } finally {
            tester.dispose()
        }
    }

    /** Asserts that every changed pixel is exactly one intended color-channel replacement. */
    private fun assertOnlyColorReplacement(
        before: IntArray,
        after: IntArray,
        from: PixelColor,
        to: PixelColor,
        message: String,
    ) {
        assertEquals("$message frame size", before.size, after.size)
        /** Number of pixels changed through the intended isolated channel. */
        var replacements = 0
        before.indices.forEach { index ->
            if (before[index] != after[index]) {
                assertEquals("$message source at $index", from.argb, before[index])
                assertEquals("$message target at $index", to.argb, after[index])
                replacements += 1
            }
        }
        assertTrue("$message did not change a pixel", replacements > 0)
    }

    /** Returns a palette whose relevant semantic roles never alias one another. */
    private fun orthogonalColors(): PixelColorScheme {
        return PixelColorScheme.Dark.copy(
            onBackground = PixelColor.fromRgb(247, 239, 211),
            surface = PixelColor.fromRgb(19, 37, 59),
            onSurface = PixelColor.fromRgb(229, 223, 197),
            surfaceVariant = PixelColor.fromRgb(47, 79, 113),
            onSurfaceVariant = PixelColor.fromRgb(191, 181, 157),
            outline = PixelColor.fromRgb(211, 131, 43),
            primary = PixelColor.fromRgb(37, 173, 101),
            onPrimary = PixelColor.fromRgb(7, 43, 23),
            danger = PixelColor.fromRgb(219, 47, 79),
            onDanger = PixelColor.fromRgb(255, 219, 227),
            warning = PixelColor.fromRgb(241, 181, 29),
            onWarning = PixelColor.fromRgb(53, 31, 3),
            disabled = PixelColor.fromRgb(83, 89, 101),
            onDisabled = PixelColor.fromRgb(173, 177, 187),
            inactive = PixelColor.fromRgb(113, 127, 149),
            focus = PixelColor.fromRgb(17, 211, 229),
            selection = PixelColor.fromRgb(231, 71, 201),
            shadow = PixelColor.fromRgb(61, 23, 83),
        )
    }

    /** Defensive frame pixels paired with one public semantic geometry snapshot. */
    private data class WidgetSnapshot(
        /** Full logical ARGB frame in row-major order. */
        val pixels: IntArray,
        /** Exact public semantic node produced by the component. */
        val node: PixelSemanticsNode,
    ) {
        /** Reports whether this frame contains one exact color. */
        fun hasColor(color: PixelColor): Boolean = pixels.any { pixel -> pixel == color.argb }
    }

    /** Shared state matrix covering base, feedback, validation, and capability branches. */
    private companion object {
        /** State sets rendered through equivalent encoded and literal geometry. */
        val ORTHOGONAL_STATES: List<PixelControlStateSet> = listOf(
            PixelControlStateSet.Normal,
            PixelControlStateSet.of(PixelControlState.Selected),
            PixelControlStateSet.of(PixelControlState.Hovered),
            PixelControlStateSet.of(PixelControlState.Pressed),
            PixelControlStateSet.of(PixelControlState.Error),
            PixelControlStateSet.of(PixelControlState.Loading),
            PixelControlStateSet.of(PixelControlState.Disabled),
        )

        /** 省略 semanticLabel 时由主题 label token 解析出的行标签。 */
        const val DEFAULT_SLIDABLE_LABEL: String = "Slidable"
    }
}
