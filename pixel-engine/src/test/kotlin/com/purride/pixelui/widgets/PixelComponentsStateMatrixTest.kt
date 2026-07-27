package com.purride.pixelui.widgets

import com.purride.pixelcore.PixelColor
import com.purride.pixelui.ActivityIndicator
import com.purride.pixelui.Badge
import com.purride.pixelui.BottomSheet
import com.purride.pixelui.Checkbox
import com.purride.pixelui.ConfirmDialog
import com.purride.pixelui.Dialog
import com.purride.pixelui.EdgeInsets
import com.purride.pixelui.Focus
import com.purride.pixelui.FocusNode
import com.purride.pixelui.ListTile
import com.purride.pixelui.ModalBarrier
import com.purride.pixelui.OutlinedButton
import com.purride.pixelui.PixelBorderTokens
import com.purride.pixelui.PixelColorRole
import com.purride.pixelui.PixelColorScheme
import com.purride.pixelui.PixelComponentColorTokens
import com.purride.pixelui.PixelComponentTokens
import com.purride.pixelui.PixelControlState
import com.purride.pixelui.PixelControlStateSet
import com.purride.pixelui.PixelFocusIndicatorTokens
import com.purride.pixelui.PixelLabelTokens
import com.purride.pixelui.PixelLoadingBar
import com.purride.pixelui.PixelMotionSettings
import com.purride.pixelui.PixelMotionScope
import com.purride.pixelui.PixelSemanticRole
import com.purride.pixelui.PixelSemanticsAction
import com.purride.pixelui.PixelSizeTokens
import com.purride.pixelui.PixelSpacingTokens
import com.purride.pixelui.PixelStateMap
import com.purride.pixelui.PixelTextStyle
import com.purride.pixelui.PixelTheme
import com.purride.pixelui.PixelThemeTokens
import com.purride.pixelui.ProgressBar
import com.purride.pixelui.SegmentedControl
import com.purride.pixelui.ShortcutHint
import com.purride.pixelui.Snackbar
import com.purride.pixelui.Stack
import com.purride.pixelui.Stepper
import com.purride.pixelui.Switch
import com.purride.pixelui.Tabs
import com.purride.pixelui.Text
import com.purride.pixelui.Toast
import com.purride.pixelui.ValueAdjuster
import com.purride.pixelui.ValueListenableBuilder
import com.purride.pixelui.ValueNotifier
import com.purride.pixelui.Widget
import com.purride.pixelui.testing.PixelTester
import com.purride.pixelui.testing.find
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** State, theme, focus, capability, and cleanup contracts for migrated PixelComponents widgets. */
class PixelComponentsStateMatrixTest {
    /** Every migrated component consumes all eight states and the shared fixed priority order. */
    @Test
    fun allComponentsResolveStateMatrixAndCombinedPriority() {
        /** Theme whose state roles map to unique observable paint sentinels. */
        val theme = matrixTheme()
        /** Reused off-screen runtime for the complete component/state cross product. */
        val tester = PixelTester()
        try {
            ComponentCase.entries.forEach { component ->
                STATE_CASES.forEach { stateCase ->
                    /** Caller state set for this single-state matrix cell. */
                    val states = stateSet(stateCase.state)
                    /** Explicit node used only to make Focused an actual additive focus state. */
                    val focusNode = FocusNode(debugLabel = "${component.name}-${stateCase.state}")
                    /** Component configured with stable callbacks so state alone controls capability. */
                    val componentWidget = componentWidget(
                        component = component,
                        states = states,
                        onMutation = {},
                        key = "matrix-${component.name}",
                    )
                    /** Focus wrapper used by interactive controls for the Focused matrix row. */
                    val focusedWidget = if (
                        stateCase.state == PixelControlState.Focused && component.focusable
                    ) {
                        Focus(node = focusNode, child = componentWidget)
                    } else {
                        componentWidget
                    }
                    tester.pumpWidget(
                        widget = PixelTheme(tokens = theme, child = focusedWidget),
                        logicalWidth = 128,
                        logicalHeight = 80,
                    )
                    if (stateCase.state == PixelControlState.Focused && component.focusable) {
                        focusNode.requestFocus()
                        tester.pumpFrame(0)
                    }

                    /** Expected concrete state sentinel, except ModalBarrier's state-neutral scrim. */
                    val expectedColor = if (component == ComponentCase.ModalBarrier) {
                        theme.colors.scrim
                    } else {
                        theme.colors.resolve(stateCase.role)
                    }
                    assertTrue(
                        "${component.name} did not paint ${stateCase.state} sentinel $expectedColor",
                        tester.hasPixel(expectedColor),
                    )
                }

                PRIORITY_CASES.forEach { priorityCase ->
                    /** Explicit focus owner used when Focused participates in this combination. */
                    val priorityFocusNode = FocusNode(
                        debugLabel = "priority-${component.name}",
                    )
                    /** Component rendered with a deliberately competing state combination. */
                    val rawCombinedWidget = componentWidget(
                        component = component,
                        states = priorityCase.states,
                        onMutation = {},
                        key = "priority-${component.name}",
                    )
                    /** Real focus wrapper preserving Focused as an independent additive layer. */
                    val combinedWidget = if (
                        PixelControlState.Focused in priorityCase.states && component.focusable
                    ) {
                        Focus(node = priorityFocusNode, child = rawCombinedWidget)
                    } else {
                        rawCombinedWidget
                    }
                    tester.pumpWidget(
                        widget = PixelTheme(tokens = theme, child = combinedWidget),
                        logicalWidth = 128,
                        logicalHeight = 80,
                    )
                    if (PixelControlState.Focused in priorityCase.states && component.focusable) {
                        priorityFocusNode.requestFocus()
                        tester.pumpFrame(0)
                    }
                    /** Expected highest-priority color, with ModalBarrier retaining its scrim. */
                    val expectedColor = if (component == ComponentCase.ModalBarrier) {
                        theme.colors.scrim
                    } else {
                        theme.colors.resolve(priorityCase.role)
                    }
                    assertTrue(
                        "${component.name} did not honor ${priorityCase.states.highestPriority()}",
                        tester.hasPixel(expectedColor),
                    )
                }
            }
        } finally {
            tester.dispose()
        }
    }

    /** Loading retains focus but removes actions, while Disabled removes focus eligibility. */
    @Test
    fun activeComponentsKeepLoadingFocusAndLoseDisabledFocus() {
        ActiveCase.entries.forEach { component ->
            /** Persistent state source driving Normal, Loading, and Disabled without remounting. */
            val states = ValueNotifier(PixelControlStateSet.Normal)
            /** Caller-owned node proving capability changes update the same focus owner. */
            val focusNode = FocusNode(debugLabel = "capability-${component.name}")
            /** Mutation count shared by pointer, keyboard, and semantic callbacks. */
            var mutations = 0
            /** Isolated runtime for this component's retained focus transition. */
            val tester = PixelTester()
            try {
                tester.pumpWidget(
                    widget = Focus(
                        node = focusNode,
                        child = ValueListenableBuilder(states) { _, currentStates ->
                            activeWidget(
                                component = component,
                                states = currentStates,
                                onMutation = { mutations += 1 },
                                key = "capability-control",
                            )
                        },
                    ),
                    logicalWidth = 96,
                    logicalHeight = 32,
                )
                focusNode.requestFocus()
                tester.pumpFrame(0)
                /** Unique focused node among any visible-text and group nodes sharing the label. */
                val normalNode = tester.semanticsNodesByLabel(component.focusLabel)
                    .single { node -> node.focused }
                /** Stable semantic identity retained through Loading and Disabled rebuilds. */
                val retainedNodeId = normalNode.id
                assertTrue("${component.name} failed to acquire focus", normalNode.focused)
                assertTrue("${component.name} should start enabled", normalNode.enabled)

                states.value = PixelControlStateSet.of(PixelControlState.Loading)
                tester.pumpFrame(0)
                /** Same focus stop after Loading makes every mutation channel inert. */
                val loadingNode = tester.semanticsNodes().single { node -> node.id == retainedNodeId }
                assertTrue("${component.name} Loading lost focus", loadingNode.focused)
                assertFalse("${component.name} Loading remained enabled", loadingNode.enabled)
                assertTrue(focusNode.isFocused)
                mutationNodes(component, tester).forEach { node ->
                    assertFalse(PixelSemanticsAction.CLICK in node.actions)
                    assertFalse(node.enabled)
                    assertFalse(tester.performSemanticsAction(node.id, PixelSemanticsAction.CLICK))
                }
                assertFalse(tester.pressKey(com.purride.pixelui.PixelKey.ENTER))
                assertEquals(0, mutations)

                states.value = PixelControlStateSet.of(PixelControlState.Disabled)
                tester.pumpFrame(0)
                /** Disabled snapshot after the automatic focus binding becomes ineligible. */
                val disabledNode = tester.semanticsNodes().single { node -> node.id == retainedNodeId }
                assertFalse("${component.name} Disabled retained semantic focus", disabledNode.focused)
                assertFalse(disabledNode.enabled)
                assertFalse(focusNode.isFocused)
                assertEquals(0, mutations)
            } finally {
                tester.dispose()
            }
        }
    }

    /** Loading during an active Switch press clears pointer ownership, actions, and motion work. */
    @Test
    fun loadingTransitionCancelsPointerAndCleansMotion() {
        /** Persistent state source switched while the pointer still owns its down sequence. */
        val states = ValueNotifier(PixelControlStateSet.Normal)
        /** Callback count proving the stale pointer-up cannot toggle after Loading. */
        var mutations = 0
        /** Runtime with a shared motion provider so ticker cleanup is observable. */
        val tester = PixelTester()
        try {
            tester.pumpWidget(
                widget = PixelMotionScope(
                    vsync = tester.vsync,
                    settings = PixelMotionSettings.Default,
                    child = ValueListenableBuilder(states) { _, currentStates ->
                        Switch(
                            checked = false,
                            onChanged = { mutations += 1 },
                            states = currentStates,
                            semanticLabel = "POINTER SWITCH",
                            key = "pointer-switch",
                        )
                    },
                ),
                logicalWidth = 32,
                logicalHeight = 16,
            )
            tester.hover(find.byKey("pointer-switch"))
            /** Captured pointer sequence entering Pressed before capability changes. */
            val gesture = tester.startGesture(find.byKey("pointer-switch"))

            states.value = PixelControlStateSet.of(PixelControlState.Loading)
            tester.pumpAndSettle(maxFrames = 80)
            /** Loading semantics after the interactive detector has been removed. */
            val loadingNode = tester.semanticsNodesByLabel("POINTER SWITCH").single()
            assertTrue(tester.renderResult!!.clickTargets.isEmpty())
            assertFalse(PixelSemanticsAction.CLICK in loadingNode.actions)
            assertEquals(0, tester.vsync.activeTickerCount)

            gesture.up()
            tester.exitHover()
            assertEquals(0, mutations)
            assertEquals(0, tester.scheduler.pendingCount)
        } finally {
            tester.dispose()
        }
        assertEquals(0, tester.vsync.liveTickerCount)
    }

    /** Snackbar capability states remove an arbitrary action subtree and every mutation channel. */
    @Test
    fun snackbarLoadingAndDisabledRemoveSuppliedAction() {
        /** Caller callback count proving removed action widgets cannot retain stale activation. */
        var activations = 0
        /** Reused action factory giving every state a fresh public Button subtree. */
        val action: () -> Widget = {
            OutlinedButton(text = "RETRY ACTION", onPressed = { activations += 1 })
        }
        /** Off-screen runtime inspecting structured semantics and render targets. */
        val tester = PixelTester()
        try {
            tester.pumpWidget(
                Snackbar(
                    message = "NORMAL",
                    states = PixelControlStateSet.Normal,
                    action = action(),
                ),
                logicalWidth = 96,
                logicalHeight = 24,
            )
            /** Normal action node remains present and executable. */
            val normalAction = tester.semanticsNodesByLabel("RETRY ACTION").single()
            assertTrue(tester.performSemanticsAction(normalAction.id, PixelSemanticsAction.CLICK))
            assertEquals(1, activations)

            listOf(PixelControlState.Loading, PixelControlState.Disabled).forEach { state ->
                tester.pumpWidget(
                    Snackbar(
                        message = state.name,
                        states = PixelControlStateSet.of(state),
                        action = action(),
                    ),
                    logicalWidth = 96,
                    logicalHeight = 24,
                )
                assertTrue("$state Snackbar retained its action semantics", tester.semanticsNodesByLabel("RETRY ACTION").isEmpty())
            }
            assertEquals(1, activations)
        } finally {
            tester.dispose()
        }
    }

    /** 简洁 Badge 入口等价于 `states = Error` 的状态化入口，并与 Normal 状态明确不同。 */
    @Test
    fun badgeConciseFacadeMatchesErrorStatesAndDiffersFromNormal() {
        /** Normal notification surface sentinel. */
        val normal = PixelColor.fromRgb(23, 71, 109)
        /** Error 状态哨兵色，用于证明简洁入口选择的正是 Error 角色。 */
        val error = PixelColor.fromRgb(211, 37, 59)
        /** Theme exposing both states as exact observable colors. */
        val theme = PixelThemeTokens.Default.copy(
            colors = PixelThemeTokens.Default.colors.copy(surface = normal, danger = error),
        )
        /** Reused runtime comparing the two public overload contracts. */
        val tester = PixelTester()
        try {
            tester.pumpWidget(
                PixelTheme(
                    tokens = theme,
                    child = Badge(
                        child = Text("BODY"),
                        label = Text("1"),
                        states = PixelControlStateSet.Normal,
                    ),
                ),
                logicalWidth = 48,
                logicalHeight = 18,
            )
            /** Normal 状态化入口的参考帧。 */
            val normalPixels = requireNotNull(tester.renderResult).buffer.pixels.copyOf()
            assertTrue(tester.hasPixel(normal))
            assertFalse(tester.hasPixel(error))

            tester.pumpWidget(
                PixelTheme(
                    tokens = theme,
                    child = Badge(
                        child = Text("BODY"),
                        label = Text("1"),
                        states = PixelControlStateSet.of(PixelControlState.Error),
                    ),
                ),
                logicalWidth = 48,
                logicalHeight = 18,
            )
            /** Error 状态化入口的参考帧。 */
            val errorStatePixels = requireNotNull(tester.renderResult).buffer.pixels.copyOf()

            tester.pumpWidget(
                PixelTheme(
                    tokens = theme,
                    child = Badge(child = Text("BODY"), label = Text("1")),
                ),
                logicalWidth = 48,
                logicalHeight = 18,
            )
            /** 简洁入口的当前帧。 */
            val concisePixels = requireNotNull(tester.renderResult).buffer.pixels.copyOf()

            // 简洁入口直接委托到 Error states，因此与状态化 Error 帧逐像素相同。
            assertTrue(concisePixels.contentEquals(errorStatePixels))
            assertFalse(concisePixels.contentEquals(normalPixels))
            assertTrue(tester.hasPixel(error))
            assertFalse(tester.hasPixel(normal))

            tester.pumpWidget(
                Badge(child = Text("BODY"), label = Text("1")),
                logicalWidth = 48,
                logicalHeight = 18,
            )
            /** 未挂载提供者时同一简洁入口的帧。 */
            val withoutProviderPixels = requireNotNull(tester.renderResult).buffer.pixels.copyOf()
            tester.pumpWidget(
                PixelTheme(
                    tokens = PixelThemeTokens.Default,
                    child = Badge(child = Text("BODY"), label = Text("1")),
                ),
                logicalWidth = 48,
                logicalHeight = 18,
            )
            /** 挂载与默认值相同的 token 图后，同一简洁入口的帧。 */
            val defaultProviderPixels = requireNotNull(tester.renderResult).buffer.pixels.copyOf()
            // 有无 PixelTheme 只改变 token 解析结果；Default 图与无提供者必须完全一致。
            assertTrue(withoutProviderPixels.contentEquals(defaultProviderPixels))
        } finally {
            tester.dispose()
        }
    }

    /** Custom labels and geometry propagate, while explicit paint and text parameters stay first. */
    @Test
    fun customThemeGeometryLabelsAndExplicitOverridesPropagate() {
        /** Key-cap surface sentinel inherited by ShortcutHint. */
        val surface = PixelColor.fromRgb(17, 31, 47)
        /** Key-cap outline sentinel inherited by ShortcutHint. */
        val outline = PixelColor.fromRgb(59, 73, 89)
        /** Shortcut text sentinel inherited from label typography and component content. */
        val onSurface = PixelColor.fromRgb(101, 113, 127)
        /** Shortcut description sentinel inherited from caption typography. */
        val onSurfaceVariant = PixelColor.fromRgb(139, 151, 163)
        /** Disabled ValueAdjuster action fill sentinel. */
        val disabled = PixelColor.fromRgb(173, 43, 67)
        /** Disabled ValueAdjuster glyph sentinel that must not fall back to black. */
        val onDisabled = PixelColor.fromRgb(211, 223, 79)
        /** Explicit fill sentinel that must override notification component tokens. */
        val explicitFill = PixelColor.fromRgb(7, 191, 211)
        /** Explicit foreground sentinel that must override inherited typography. */
        val explicitText = PixelColor.fromRgb(239, 97, 13)
        /** Localized labels proving defaults resolve inside the exact themed subtree. */
        val labels = PixelLabelTokens.Default.copy(
            checkbox = "TOKEN CHECK",
            switch = "TOKEN SWITCH",
            valueAdjuster = "TOKEN ADJUSTER",
            decrease = "TOKEN DECREASE",
            increase = "TOKEN INCREASE",
            dialog = "TOKEN DIALOG",
            bottomSheet = "TOKEN SHEET",
            progress = "TOKEN PROGRESS",
            loading = "TOKEN LOADING",
            error = "TOKEN ERROR",
        )
        /** Foundation geometry values selected through encoded component geometry tokens. */
        val sizes = PixelSizeTokens.Default.copy(
            selectionControlExtent = 13,
            switchWidth = 19,
            trackHeight = 11,
            overlayMinimumWidth = 31,
        )
        /** Foundation spacing values consumed by ShortcutHint and default progress width. */
        val spacing = PixelSpacingTokens.Default.copy(extraSmall = 2, small = 3, large = 7)
        /** Scheme carrying all custom paint sentinels. */
        val colors = PixelColorScheme.Dark.copy(
            surface = surface,
            outline = outline,
            onSurface = onSurface,
            onSurfaceVariant = onSurfaceVariant,
            disabled = disabled,
            onDisabled = onDisabled,
        )
        /** Complete custom theme used by every propagation assertion. */
        val theme = PixelThemeTokens.Default.copy(
            colors = colors,
            labels = labels,
            sizes = sizes,
            spacing = spacing,
        )
        /** Reused runtime for geometry, labels, token paint, and explicit precedence snapshots. */
        val tester = PixelTester()
        try {
            tester.pumpWidget(
                PixelTheme(
                    tokens = theme,
                    child = Checkbox(checked = false, onChanged = {}, states = PixelControlStateSet.Normal),
                ),
                logicalWidth = 40,
                logicalHeight = 24,
            )
            /** Token-labeled Checkbox semantics with size-scale-resolved geometry. */
            val checkbox = tester.semanticsNodesByLabel("TOKEN CHECK").single()
            assertEquals(13, checkbox.width)
            assertEquals(13, checkbox.height)

            tester.pumpWidget(
                PixelTheme(
                    tokens = theme,
                    child = Switch(checked = false, onChanged = {}, states = PixelControlStateSet.Normal),
                ),
                logicalWidth = 40,
                logicalHeight = 24,
            )
            /** Token-labeled Switch semantics with independently resolved width and track height. */
            val switch = tester.semanticsNodesByLabel("TOKEN SWITCH").single()
            assertEquals(19, switch.width)
            assertEquals(11, switch.height)

            tester.pumpWidget(
                PixelTheme(tokens = theme, child = ProgressBar(progress = 0.5f, states = PixelControlStateSet.Normal)),
                logicalWidth = 64,
                logicalHeight = 24,
            )
            /** Progress geometry derived from overlay width plus large spacing and track height. */
            val progress = tester.semanticsNodesByLabel("TOKEN PROGRESS").single()
            assertEquals(38, progress.width)
            assertEquals(11, progress.height)
            assertEquals(PixelSemanticRole.PROGRESS_BAR, progress.role)

            tester.pumpWidget(
                PixelTheme(
                    tokens = theme,
                    child = ValueAdjuster(
                        valueText = "1",
                        onDecrease = null,
                        onIncrease = {},
                        states = PixelControlStateSet.Normal,
                    ),
                ),
                logicalWidth = 64,
                logicalHeight = 24,
            )
            assertTrue(tester.hasPixel(disabled))
            assertTrue(tester.hasPixel(onDisabled))
            assertTrue(tester.semanticsNodesByLabel("TOKEN ADJUSTER").isNotEmpty())
            assertTrue(tester.semanticsNodesByLabel("TOKEN DECREASE").isNotEmpty())
            assertTrue(tester.semanticsNodesByLabel("TOKEN INCREASE").isNotEmpty())

            tester.pumpWidget(
                PixelTheme(tokens = theme, child = ShortcutHint(shortcut = "K", label = "COMMAND")),
                logicalWidth = 80,
                logicalHeight = 24,
            )
            listOf(surface, outline, onSurface, onSurfaceVariant).forEach { sentinel ->
                assertTrue("ShortcutHint missed theme sentinel $sentinel", tester.hasPixel(sentinel))
            }

            tester.pumpWidget(
                PixelTheme(
                    tokens = theme,
                    child = Toast(
                        message = "EXPLICIT",
                        states = PixelControlStateSet.of(PixelControlState.Error),
                        fillColor = explicitFill,
                        textStyle = PixelTextStyle(color = explicitText),
                    ),
                ),
                logicalWidth = 80,
                logicalHeight = 24,
            )
            assertTrue(tester.hasPixel(explicitFill))
            assertTrue(tester.hasPixel(explicitText))

            tester.pumpWidget(
                PixelTheme(
                    tokens = theme,
                    child = Dialog(
                        content = Text("D"),
                        states = PixelControlStateSet.Normal,
                        modal = false,
                    ),
                ),
                logicalWidth = 64,
                logicalHeight = 40,
            )
            assertTrue(tester.semanticsNodesByLabel("TOKEN DIALOG").isNotEmpty())

            tester.pumpWidget(
                PixelTheme(
                    tokens = theme,
                    child = BottomSheet(
                        content = Text("S"),
                        states = PixelControlStateSet.Normal,
                        modal = false,
                    ),
                ),
                logicalWidth = 64,
                logicalHeight = 40,
            )
            assertTrue(tester.semanticsNodesByLabel("TOKEN SHEET").isNotEmpty())
        } finally {
            tester.dispose()
        }
    }

    /** Creates one public component for matrix rendering with a shared controlled mutation hook. */
    private fun componentWidget(
        component: ComponentCase,
        states: PixelControlStateSet,
        onMutation: () -> Unit,
        key: Any,
    ): Widget {
        return when (component) {
            ComponentCase.ListTile -> ListTile(
                title = Text("L"),
                states = states,
                onTap = onMutation,
                semanticLabel = "MATRIX LIST",
                key = key,
            )
            ComponentCase.Checkbox -> Checkbox(
                checked = PixelControlState.Selected in states,
                onChanged = { onMutation() },
                states = states,
                semanticLabel = "MATRIX CHECK",
                key = key,
            )
            ComponentCase.Switch -> Switch(
                checked = PixelControlState.Selected in states,
                onChanged = { onMutation() },
                states = states,
                semanticLabel = "MATRIX SWITCH",
                key = key,
            )
            ComponentCase.Dialog -> Dialog(
                content = Text("D"),
                states = states,
                onDismissRequest = onMutation,
                modal = false,
                key = key,
            )
            ComponentCase.BottomSheet -> BottomSheet(
                content = Text("B"),
                states = states,
                onDismissRequest = onMutation,
                modal = false,
                key = key,
            )
            ComponentCase.ConfirmDialog -> ConfirmDialog(
                title = "C",
                message = "M",
                onConfirm = onMutation,
                states = states,
                onCancel = onMutation,
                key = key,
            )
            ComponentCase.ModalBarrier -> Stack(
                children = listOf(
                    ModalBarrier(
                        states = states,
                        dismissible = true,
                        onDismiss = onMutation,
                        key = key,
                    ),
                ),
            )
            ComponentCase.Toast -> Toast(message = "TOAST", states = states, key = key)
            ComponentCase.Snackbar -> Snackbar(message = "SNACK", states = states, key = key)
            ComponentCase.Tabs -> Tabs(
                labels = listOf("TAB A", "TAB B"),
                selectedIndex = 0,
                onSelected = { onMutation() },
                states = states,
                key = key,
            )
            ComponentCase.SegmentedControl -> SegmentedControl(
                labels = listOf("SEG A", "SEG B"),
                selectedIndex = 0,
                onSelected = { onMutation() },
                states = states,
                key = key,
            )
            ComponentCase.ValueAdjuster -> ValueAdjuster(
                valueText = "1",
                onDecrease = onMutation,
                onIncrease = onMutation,
                states = states,
                label = "MATRIX ADJUSTER",
                key = key,
            )
            ComponentCase.Stepper -> Stepper(
                value = 1,
                range = 0..2,
                onChanged = { onMutation() },
                states = states,
                label = "MATRIX STEPPER",
                key = key,
            )
            ComponentCase.ProgressBar -> ProgressBar(progress = 0.5f, states = states, key = key)
            ComponentCase.PixelLoadingBar -> PixelLoadingBar(progress = 0.5f, states = states, key = key)
            ComponentCase.ActivityIndicator -> ActivityIndicator(states = states, key = key)
        }
    }

    /** Creates one focusable active component while preserving its case-specific semantic label. */
    private fun activeWidget(
        component: ActiveCase,
        states: PixelControlStateSet,
        onMutation: () -> Unit,
        key: Any,
    ): Widget = componentWidget(component.component, states, onMutation, key)

    /** Returns semantic nodes that own mutation actions for the active component case. */
    private fun mutationNodes(
        component: ActiveCase,
        tester: PixelTester,
    ) = when (component) {
        ActiveCase.ValueAdjuster,
        ActiveCase.Stepper,
        -> listOf("Decrease", "Increase").flatMap(tester::semanticsNodesByLabel)
        else -> tester.semanticsNodesByLabel(component.focusLabel)
    }

    /** Creates a state set whose Normal representation remains the canonical empty mask. */
    private fun stateSet(state: PixelControlState): PixelControlStateSet {
        return if (state == PixelControlState.Normal) {
            PixelControlStateSet.Normal
        } else {
            PixelControlStateSet.of(state)
        }
    }

    /** Builds component tokens whose every visual state resolves to a distinct semantic role. */
    private fun matrixComponentTokens(): PixelComponentColorTokens {
        /** State property reused by container and content so every component paints the sentinel. */
        val colors = PixelStateMap<PixelColorRole?>(
            normal = PixelColorRole.Surface,
            PixelControlState.Hovered to PixelColorRole.SurfaceVariant,
            PixelControlState.Pressed to PixelColorRole.Selection,
            PixelControlState.Focused to PixelColorRole.Focus,
            PixelControlState.Selected to PixelColorRole.Primary,
            PixelControlState.Disabled to PixelColorRole.Disabled,
            PixelControlState.Error to PixelColorRole.Danger,
            PixelControlState.Loading to PixelColorRole.Warning,
        )
        return PixelComponentColorTokens(
            containerColor = colors,
            contentColor = colors,
            borderColor = com.purride.pixelui.PixelStateProperty.constant(null),
            focusIndicator = PixelFocusIndicatorTokens(colorRole = PixelColorRole.Focus),
            padding = EdgeInsets.all(1),
            minimumWidth = 14,
            minimumHeight = 7,
            borderWidth = 0,
            cornerRadius = 0,
        )
    }

    /** Builds a complete theme assigning the matrix token family to every component under test. */
    private fun matrixTheme(): PixelThemeTokens {
        /** Shared state-property token used by active, passive, and progress components. */
        val matrixTokens = matrixComponentTokens()
        /** Unique concrete colors for every semantic role used by the state matrix. */
        val colors = PixelColorScheme.Dark.copy(
            surface = PixelColor.fromRgb(11, 23, 37),
            surfaceVariant = PixelColor.fromRgb(43, 59, 71),
            selection = PixelColor.fromRgb(79, 97, 109),
            focus = PixelColor.fromRgb(127, 139, 151),
            primary = PixelColor.fromRgb(163, 179, 191),
            disabled = PixelColor.fromRgb(199, 83, 101),
            danger = PixelColor.fromRgb(211, 47, 61),
            warning = PixelColor.fromRgb(229, 173, 31),
            scrim = PixelColor.fromRgb(17, 197, 181),
        )
        /** Every relevant component entry uses the same observable state mapping. */
        val components = PixelComponentTokens.Default.copy(
            button = matrixTokens,
            textButton = matrixTokens,
            listTile = matrixTokens,
            checkbox = matrixTokens,
            switch = matrixTokens,
            tabs = matrixTokens,
            segmented = matrixTokens,
            valueAdjuster = matrixTokens,
            dialog = matrixTokens,
            bottomSheet = matrixTokens,
            toast = matrixTokens,
            snackbar = matrixTokens,
            progress = matrixTokens,
        )
        return PixelThemeTokens.Default.copy(colors = colors, components = components)
    }

    /** Public components included in the complete visual state cross product. */
    private enum class ComponentCase(
        /** Whether Focused must be established through a real focus node. */
        val focusable: Boolean,
    ) {
        ListTile(true),
        Checkbox(true),
        Switch(true),
        Dialog(false),
        BottomSheet(false),
        ConfirmDialog(false),
        ModalBarrier(false),
        Toast(false),
        Snackbar(false),
        Tabs(true),
        SegmentedControl(true),
        ValueAdjuster(true),
        Stepper(true),
        ProgressBar(false),
        PixelLoadingBar(false),
        ActivityIndicator(false),
    }

    /** Focusable mutation components and the label of their single group focus stop. */
    private enum class ActiveCase(
        /** Matching complete-matrix component case. */
        val component: ComponentCase,
        /** Stable semantic label representing the group focus stop. */
        val focusLabel: String,
    ) {
        ListTile(ComponentCase.ListTile, "MATRIX LIST"),
        Checkbox(ComponentCase.Checkbox, "MATRIX CHECK"),
        Switch(ComponentCase.Switch, "MATRIX SWITCH"),
        Tabs(ComponentCase.Tabs, "TAB A"),
        SegmentedControl(ComponentCase.SegmentedControl, "SEG A"),
        ValueAdjuster(ComponentCase.ValueAdjuster, "MATRIX ADJUSTER"),
        Stepper(ComponentCase.Stepper, "MATRIX STEPPER"),
    }

    /** One single-state matrix row and its expected semantic color role. */
    private data class StateCase(
        /** Caller-visible component state. */
        val state: PixelControlState,
        /** Expected matrix role after state resolution. */
        val role: PixelColorRole,
    )

    /** One competing-state set and the role selected by global priority. */
    private data class PriorityCase(
        /** Combined states deliberately competing for visual resolution. */
        val states: PixelControlStateSet,
        /** Expected highest-priority matrix role. */
        val role: PixelColorRole,
    )

    private companion object {
        /** Every documented standard state and its unique matrix color role. */
        val STATE_CASES: List<StateCase> = listOf(
            StateCase(PixelControlState.Normal, PixelColorRole.Surface),
            StateCase(PixelControlState.Hovered, PixelColorRole.SurfaceVariant),
            StateCase(PixelControlState.Pressed, PixelColorRole.Selection),
            StateCase(PixelControlState.Focused, PixelColorRole.Focus),
            StateCase(PixelControlState.Selected, PixelColorRole.Primary),
            StateCase(PixelControlState.Disabled, PixelColorRole.Disabled),
            StateCase(PixelControlState.Error, PixelColorRole.Danger),
            StateCase(PixelControlState.Loading, PixelColorRole.Warning),
        )

        /** Adjacent priority pairs spanning Selected through Disabled. */
        val PRIORITY_CASES: List<PriorityCase> = listOf(
            PriorityCase(
                PixelControlStateSet.of(PixelControlState.Selected, PixelControlState.Hovered),
                PixelColorRole.SurfaceVariant,
            ),
            PriorityCase(
                PixelControlStateSet.of(PixelControlState.Hovered, PixelControlState.Focused),
                PixelColorRole.Focus,
            ),
            PriorityCase(
                PixelControlStateSet.of(PixelControlState.Focused, PixelControlState.Pressed),
                PixelColorRole.Selection,
            ),
            PriorityCase(
                PixelControlStateSet.of(PixelControlState.Pressed, PixelControlState.Error),
                PixelColorRole.Danger,
            ),
            PriorityCase(
                PixelControlStateSet.of(PixelControlState.Error, PixelControlState.Loading),
                PixelColorRole.Warning,
            ),
            PriorityCase(
                PixelControlStateSet.of(PixelControlState.Loading, PixelControlState.Disabled),
                PixelColorRole.Disabled,
            ),
        )
    }
}
