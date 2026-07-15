package com.purride.pixelui.widgets

import com.purride.pixelcore.PixelColor
import com.purride.pixelui.Container
import com.purride.pixelui.PixelColorScheme
import com.purride.pixelui.PixelControlState
import com.purride.pixelui.PixelControlStateSet
import com.purride.pixelui.PixelKey
import com.purride.pixelui.PixelLabelTokens
import com.purride.pixelui.PixelSemanticRole
import com.purride.pixelui.PixelSemanticsAction
import com.purride.pixelui.PixelTheme
import com.purride.pixelui.PixelThemeTokens
import com.purride.pixelui.Slidable
import com.purride.pixelui.SlidableAction
import com.purride.pixelui.SlidableActionPane
import com.purride.pixelui.ValueListenableBuilder
import com.purride.pixelui.ValueNotifier
import com.purride.pixelui.testing.PixelTester
import com.purride.pixelui.testing.find
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Locks M5 theme propagation and combined-state capability for Slidable rows and actions. */
class SlidableThemeStateTest {
    /** Error+Pressed+Focused, Loading+Error, and Disabled+Loading follow the global priority. */
    @Test
    fun actionCombinedStatesResolvePriorityAndCleanupInput() {
        /** Normal Slidable surface sentinel. */
        val normal = PixelColor.fromRgb(24, 72, 120)
        /** Pressed role sentinel that remains below Error in the global priority. */
        val pressed = PixelColor.fromRgb(36, 188, 92)
        /** Error role sentinel visible before pointer down. */
        val error = PixelColor.fromRgb(220, 44, 76)
        /** Loading role sentinel that must win over Error and stale Pressed. */
        val loading = PixelColor.fromRgb(240, 184, 28)
        /** Disabled role sentinel that must win over Loading. */
        val disabled = PixelColor.fromRgb(76, 80, 88)
        /** Independent focus indicator sentinel retained during Loading. */
        val focus = PixelColor.fromRgb(24, 220, 236)
        /** Scheme whose Slidable state roles are all visually unique. */
        val colors = PixelColorScheme.Dark.copy(
            surface = normal,
            surfaceVariant = pressed,
            primary = pressed,
            danger = error,
            warning = loading,
            disabled = disabled,
            focus = focus,
        )
        /** Persistent states changed while the action owns pointer and focus. */
        val states = ValueNotifier(PixelControlStateSet.of(PixelControlState.Error))
        /** Callback count proving stale pointer and semantic actions are canceled. */
        var activations = 0
        /** Off-screen runtime rendering exact state colors and structured semantics. */
        val tester = PixelTester()
        try {
            tester.pumpWidget(
                widget = PixelTheme(
                    tokens = PixelThemeTokens.Default.copy(colors = colors),
                    child = ValueListenableBuilder(states) { _, currentStates ->
                        SlidableAction(
                            label = "ARCHIVE",
                            onPressed = { activations += 1 },
                            states = currentStates,
                            key = "state-action",
                        )
                    },
                ),
                logicalWidth = 48,
                logicalHeight = 18,
            )

            assertTrue(tester.hasPixel(error))
            assertTrue(tester.pressKey(PixelKey.TAB))
            /** Pointer sequence proving Error wins over Pressed while Focus remains additive. */
            val gesture = tester.startGesture(find.byKey("state-action"))
            assertTrue(tester.hasPixel(error))
            assertFalse(tester.hasPixel(pressed))
            assertTrue(tester.hasPixel(focus))

            states.value = PixelControlStateSet.of(
                PixelControlState.Loading,
                PixelControlState.Error,
            )
            tester.pumpFrame(0)
            /** Focus-retaining Loading node after its pointer target has been removed. */
            val loadingNode = tester.semanticsNodesByLabel("ARCHIVE").single()
            assertTrue(loadingNode.focused)
            assertFalse(loadingNode.enabled)
            assertEquals(PixelSemanticRole.BUTTON, loadingNode.role)
            assertFalse(PixelSemanticsAction.CLICK in loadingNode.actions)
            assertTrue(tester.hasPixel(loading))
            assertTrue(tester.hasPixel(focus))
            gesture.up()
            assertEquals(0, activations)
            assertFalse(tester.performSemanticsAction(loadingNode.id, PixelSemanticsAction.CLICK))

            states.value = PixelControlStateSet.of(
                PixelControlState.Disabled,
                PixelControlState.Loading,
            )
            tester.pumpFrame(0)
            /** Disabled node after focus traversal eligibility is revoked. */
            val disabledNode = tester.semanticsNodesByLabel("ARCHIVE").single()
            assertFalse(disabledNode.focused)
            assertFalse(disabledNode.enabled)
            assertTrue(tester.hasPixel(disabled))
            assertEquals(0, tester.scheduler.pendingCount)
            assertEquals(0, tester.vsync.activeTickerCount)
        } finally {
            tester.dispose()
        }
        assertEquals(0, tester.vsync.liveTickerCount)
    }

    /** Loading during a row drag keeps focus and selection but cancels stale dismissal delivery. */
    @Test
    fun rowLoadingCancelsActiveGestureAndUsesLocalizedLabel() {
        /** Custom fallback proving omitted row labels resolve from the complete theme. */
        val localizedLabel = "Swipe actions"
        /** Persistent row state changed while an end-pane drag is active. */
        val states = ValueNotifier(PixelControlStateSet.Normal)
        /** Dismiss count that must remain zero after Loading cancels the pointer owner. */
        var dismissals = 0
        /** Row activation count that must remain zero while Loading. */
        var activations = 0
        /** Off-screen runtime with deterministic pointer ownership and focus. */
        val tester = PixelTester()
        try {
            tester.pumpWidget(
                widget = PixelTheme(
                    tokens = PixelThemeTokens.Default.copy(
                        labels = PixelLabelTokens.Default.copy(slidable = localizedLabel),
                    ),
                    child = ValueListenableBuilder(states) { _, currentStates ->
                        Slidable(
                            child = Container(
                                width = 40,
                                height = 10,
                                fillColor = PixelColor.fromRgb(28, 48, 72),
                            ),
                            states = currentStates,
                            endActionPane = SlidableActionPane(
                                children = listOf(
                                    SlidableAction(
                                        label = "DELETE",
                                        backgroundColor = PixelColor.fromRgb(180, 48, 56),
                                        foregroundColor = PixelColor.White,
                                        onPressed = {},
                                    ),
                                ),
                                extentRatio = 0.5f,
                                dismissible = true,
                                dismissThreshold = 0.5f,
                            ),
                            onTap = { activations += 1 },
                            onDismissed = { dismissals += 1 },
                            key = "state-row",
                        )
                    },
                ),
                logicalWidth = 48,
                logicalHeight = 18,
            )

            assertTrue(tester.pressKey(PixelKey.TAB))
            /** Active drag revealing the end pane before capability changes. */
            val gesture = tester.startGesture(find.byKey("state-row:gesture")).moveBy(-12, 0)
            states.value = PixelControlStateSet.of(PixelControlState.Loading)
            tester.pumpFrame(0)

            /** Localized Loading row remains focused and selected but exposes no click action. */
            val rowNode = tester.semanticsNodesByLabel(localizedLabel).single()
            assertTrue(rowNode.focused)
            assertTrue(rowNode.selected)
            assertFalse(rowNode.enabled)
            assertFalse(PixelSemanticsAction.CLICK in rowNode.actions)
            gesture.up()
            assertEquals(0, dismissals)
            assertEquals(0, activations)
            assertFalse(tester.pressKey(PixelKey.ENTER))
            assertEquals(0, tester.scheduler.pendingCount)
            assertEquals(0, tester.vsync.activeTickerCount)
        } finally {
            tester.dispose()
        }
        assertEquals(0, tester.vsync.liveTickerCount)
    }
}
