package com.purride.pixelui.widgets

import com.purride.pixelcore.PixelColor
import com.purride.pixelui.Column
import com.purride.pixelui.EdgeInsets
import com.purride.pixelui.Focus
import com.purride.pixelui.FocusNode
import com.purride.pixelui.OutlinedButton
import com.purride.pixelui.PixelButtonStyle
import com.purride.pixelui.PixelColorScheme
import com.purride.pixelui.PixelControlState
import com.purride.pixelui.PixelControlStateSet
import com.purride.pixelui.PixelLabelTokens
import com.purride.pixelui.PixelMotionRole
import com.purride.pixelui.PixelMotionScope
import com.purride.pixelui.PixelMotionSettings
import com.purride.pixelui.PixelMotionSpec
import com.purride.pixelui.PixelMotionTheme
import com.purride.pixelui.PixelMotionThemeData
import com.purride.pixelui.PixelSemanticsAction
import com.purride.pixelui.PixelTextButtonStyle
import com.purride.pixelui.PixelTextFieldStyle
import com.purride.pixelui.PixelTextStyle
import com.purride.pixelui.PixelTheme
import com.purride.pixelui.PixelThemeData
import com.purride.pixelui.PixelThemeTokens
import com.purride.pixelui.Slider
import com.purride.pixelui.TextButton
import com.purride.pixelui.TextField
import com.purride.pixelui.ValueListenableBuilder
import com.purride.pixelui.ValueNotifier
import com.purride.pixelui.Widget
import com.purride.pixelui.animation.Curves
import com.purride.pixelui.state.PixelTextFieldController
import com.purride.pixelui.testing.PixelTester
import com.purride.pixelui.testing.find
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration.Companion.milliseconds

/** Focused state and theme-compatibility contracts for Button, TextField, and Slider. */
class StandardComponentStateGroupATest {
    /** Exact component-specific legacy colors survive the Normal token compatibility bridge. */
    @Test
    fun legacyThemeKeepsExactNormalComponentStyles() {
        /** Unique OutlinedButton fill sentinel. */
        val buttonFill = PixelColor.fromRgb(91, 17, 33)
        /** Unique OutlinedButton outline sentinel. */
        val buttonBorder = PixelColor.fromRgb(19, 117, 31)
        /** Unique OutlinedButton content sentinel. */
        val buttonText = PixelColor.fromRgb(27, 43, 199)
        /** Unique TextButton content sentinel. */
        val textButtonText = PixelColor.fromRgb(211, 47, 139)
        /** Unique TextField fill sentinel. */
        val fieldFill = PixelColor.fromRgb(77, 61, 11)
        /** Unique TextField outline sentinel. */
        val fieldBorder = PixelColor.fromRgb(13, 173, 181)
        /** Unique TextField content sentinel. */
        val fieldText = PixelColor.fromRgb(193, 137, 23)
        /** Controlled field owner used to paint non-placeholder content. */
        val controller = PixelTextFieldController()
        /** Non-empty field state that exercises the exact legacy textStyle color. */
        val state = controller.create(initialText = "I")
        /** Legacy data with component colors that cannot be represented by finite semantic roles. */
        val legacyTheme = PixelThemeData(
            buttonStyle = PixelButtonStyle(
                fillColor = buttonFill,
                borderColor = buttonBorder,
                textStyle = PixelTextStyle(color = buttonText),
            ),
            textButtonStyle = PixelTextButtonStyle(
                textStyle = PixelTextStyle(color = textButtonText),
                padding = EdgeInsets.all(1),
            ),
            textFieldStyle = PixelTextFieldStyle(
                fillColor = fieldFill,
                borderColor = fieldBorder,
                textStyle = PixelTextStyle(color = fieldText),
            ),
        )
        /** Off-screen runtime rendering every affected Normal compatibility path. */
        val tester = PixelTester()
        try {
            tester.pumpWidget(
                widget = PixelTheme(
                    data = legacyTheme,
                    child = Column(
                        children = listOf(
                            OutlinedButton(text = "I", onPressed = {}, key = "legacy-button"),
                            TextButton(text = "I", onPressed = {}, key = "legacy-text-button"),
                            TextField(
                                state = state,
                                controller = controller,
                                semanticLabel = "Legacy field",
                                key = "legacy-field",
                            ),
                        ),
                        spacing = 1,
                    ),
                ),
                logicalWidth = 64,
                logicalHeight = 40,
            )

            listOf(
                buttonFill,
                buttonBorder,
                buttonText,
                textButtonText,
                fieldFill,
                fieldBorder,
                fieldText,
            ).forEach { sentinel ->
                assertTrue("Missing exact legacy sentinel $sentinel", tester.hasPixel(sentinel))
            }
        } finally {
            tester.dispose()
        }
    }

    /** Localizable token labels replace only omitted legacy defaults, never explicit labels. */
    @Test
    fun tokenLabelsReplaceLegacyDefaultSentinelsOnly() {
        /** Custom labels proving the components resolve semantics inside their themed build context. */
        val labels = PixelLabelTokens.Default.copy(
            slider = "TOKEN VOLUME",
            textField = "TOKEN FIELD",
        )
        /** First controlled field whose old empty default becomes an omitted sentinel. */
        val tokenController = PixelTextFieldController()
        /** Empty state for the theme-label field. */
        val tokenState = tokenController.create()
        /** Second field proving a non-default explicit label remains highest priority. */
        val explicitController = PixelTextFieldController()
        /** Empty state for the explicit-label field. */
        val explicitState = explicitController.create()
        /** Off-screen runtime collecting the final semantic labels. */
        val tester = PixelTester()
        try {
            tester.pumpWidget(
                widget = PixelTheme(
                    tokens = PixelThemeTokens.Default.copy(labels = labels),
                    child = Column(
                        children = listOf(
                            Slider(value = 0.5f, key = "token-slider"),
                            TextField(
                                state = tokenState,
                                controller = tokenController,
                                key = "token-field",
                            ),
                            Slider(
                                value = 0.5f,
                                semanticLabel = "EXPLICIT VOLUME",
                                key = "explicit-slider",
                            ),
                            TextField(
                                state = explicitState,
                                controller = explicitController,
                                semanticLabel = "EXPLICIT FIELD",
                                key = "explicit-field",
                            ),
                        ),
                        spacing = 1,
                    ),
                ),
                logicalWidth = 72,
                logicalHeight = 48,
            )

            assertEquals(1, tester.semanticsNodesByLabel("TOKEN VOLUME").size)
            assertEquals(1, tester.semanticsNodesByLabel("TOKEN FIELD").size)
            assertEquals(1, tester.semanticsNodesByLabel("EXPLICIT VOLUME").size)
            assertEquals(1, tester.semanticsNodesByLabel("EXPLICIT FIELD").size)
            assertTrue(tester.semanticsNodesByLabel("Slider").isEmpty())
        } finally {
            tester.dispose()
        }
    }

    /** Error base colors and the independent focus indicator remain observable together. */
    @Test
    fun errorAndFocusRemainDistinctForButtonAndTextField() {
        /** Error surface/content role sentinel. */
        val danger = PixelColor.fromRgb(203, 31, 53)
        /** Independent focus outline sentinel. */
        val focus = PixelColor.fromRgb(17, 211, 227)
        /** Scheme used by both focused controls under test. */
        val colors = PixelColorScheme.Dark.copy(danger = danger, focus = focus)
        /** Explicit node proving the automatic button wrapper reuses caller focus ownership. */
        val buttonFocus = FocusNode(debugLabel = "error-button")
        /** Off-screen button runtime. */
        val buttonTester = PixelTester()
        try {
            buttonTester.pumpWidget(
                widget = PixelTheme(
                    tokens = PixelThemeTokens.Default.copy(colors = colors),
                    child = Focus(
                        node = buttonFocus,
                        child = OutlinedButton(
                            text = "ERROR",
                            onPressed = {},
                            states = PixelControlStateSet.of(PixelControlState.Error),
                            key = "error-button",
                        ),
                    ),
                ),
                logicalWidth = 64,
                logicalHeight = 20,
            )
            buttonFocus.requestFocus()
            buttonTester.pumpFrame(0)

            /** Focused button semantic snapshot with shrink-wrapped bounds. */
            val buttonNode = buttonTester.semanticsNodesByLabel("ERROR").single()
            assertTrue(buttonNode.focused)
            assertTrue(buttonNode.width < 64)
            assertTrue(buttonTester.hasPixel(danger))
            assertTrue(buttonTester.hasPixel(focus))
        } finally {
            buttonTester.dispose()
        }

        /** Controlled field owner for the error-plus-focus combination. */
        val fieldController = PixelTextFieldController()
        /** Non-empty state ensuring the Error content role remains visible beside focus. */
        val fieldState = fieldController.create(initialText = "BAD")
        /** Explicit field focus node retained into the later Loading state. */
        val fieldFocus = FocusNode(debugLabel = "error-field")
        /** Off-screen field runtime. */
        val fieldTester = PixelTester()
        try {
            fieldTester.pumpWidget(
                widget = PixelTheme(
                    tokens = PixelThemeTokens.Default.copy(colors = colors),
                    child = TextField(
                        state = fieldState,
                        controller = fieldController,
                        states = PixelControlStateSet.Normal,
                        focusNode = fieldFocus,
                        semanticLabel = "Account",
                        semanticError = "Invalid account",
                        key = "error-field",
                    ),
                ),
                logicalWidth = 64,
                logicalHeight = 20,
            )
            fieldFocus.requestFocus()
            fieldTester.pumpFrame(0)

            /** Focused error-field semantics including the validation announcement. */
            val fieldNode = fieldTester.semanticsNodesByLabel("Account").single()
            assertTrue(fieldNode.focused)
            assertEquals("Invalid account", fieldNode.error)
            assertTrue(fieldTester.hasPixel(danger))
            assertTrue(fieldTester.hasPixel(focus))
        } finally {
            fieldTester.dispose()
        }
    }

    /** Loading removes activation during an active press, retains focus, and releases every ticker. */
    @Test
    fun buttonLoadingTransitionCancelsPressRetainsFocusAndCleansTickers() {
        /** Persistent state source changed while the pointer still owns a down sequence. */
        val states = ValueNotifier(PixelControlStateSet.Normal)
        /** Focus node whose ownership must survive the inert Loading transition. */
        val focusNode = FocusNode(debugLabel = "loading-button")
        /** Callback count proving the stale pointer up and semantic action remain inert. */
        var activations = 0
        /** Off-screen runtime using a real shared feedback ticker. */
        val tester = PixelTester()
        try {
            tester.pumpWidget(
                widget = motionRoot(tester) {
                    Focus(
                        node = focusNode,
                        child = ValueListenableBuilder(states) { _, currentStates ->
                            OutlinedButton(
                                text = "SAVE",
                                onPressed = { activations += 1 },
                                states = currentStates,
                                key = "loading-button",
                            )
                        },
                    )
                },
                logicalWidth = 64,
                logicalHeight = 20,
            )
            focusNode.requestFocus()
            tester.pumpFrame(0)
            /** Pointer sequence that starts the Pressed feedback fragment. */
            val gesture = tester.startGesture(find.byKey("loading-button"))
            assertTrue(tester.vsync.activeTickerCount > 0)

            states.value = PixelControlStateSet.of(PixelControlState.Loading)
            tester.pumpFrame(0)
            /** Loading semantic node after the click target has disappeared. */
            val loadingNode = tester.semanticsNodesByLabel("SAVE").single()
            assertTrue(loadingNode.focused)
            assertFalse(loadingNode.enabled)
            assertFalse(PixelSemanticsAction.CLICK in loadingNode.actions)
            assertTrue(focusNode.isFocused)
            assertEquals(0, tester.vsync.activeTickerCount)
            assertEquals(0, tester.vsync.liveTickerCount)

            gesture.up()
            assertEquals(0, activations)
            assertFalse(tester.performSemanticsAction(loadingNode.id, PixelSemanticsAction.CLICK))
            assertEquals(0, tester.scheduler.pendingCount)
        } finally {
            tester.dispose()
        }
        assertEquals(0, tester.vsync.liveTickerCount)
    }

    /** TextField Loading keeps its focused state while removing every mutation action. */
    @Test
    fun textFieldLoadingRetainsFocusButRemovesEditing() {
        /** Persistent state source changed after the field has acquired focus. */
        val states = ValueNotifier(PixelControlStateSet.Normal)
        /** Controlled field owner used to verify Loading cannot mutate text. */
        val controller = PixelTextFieldController()
        /** Initial non-empty field state retained across the transition. */
        val fieldState = controller.create(initialText = "LOCKED")
        /** Explicit node whose focus must remain owned during Loading. */
        val focusNode = FocusNode(debugLabel = "loading-field")
        /** Loading content and outline sentinel. */
        val warning = PixelColor.fromRgb(207, 137, 29)
        /** Additive focus sentinel that must remain visible with Loading. */
        val focus = PixelColor.fromRgb(19, 217, 193)
        /** Themed off-screen runtime. */
        val tester = PixelTester()
        try {
            tester.pumpWidget(
                widget = PixelTheme(
                    tokens = PixelThemeTokens.Default.copy(
                        colors = PixelColorScheme.Dark.copy(warning = warning, focus = focus),
                    ),
                    child = ValueListenableBuilder(states) { _, currentStates ->
                        TextField(
                            state = fieldState,
                            controller = controller,
                            states = currentStates,
                            focusNode = focusNode,
                            semanticLabel = "LOADING FIELD",
                            key = "loading-field",
                        )
                    },
                ),
                logicalWidth = 64,
                logicalHeight = 20,
            )
            focusNode.requestFocus()
            tester.pumpFrame(0)
            /** Editable focused node before the Loading transition. */
            val editableNode = tester.semanticsNodesByLabel("LOADING FIELD").single()
            assertTrue(editableNode.focused)
            assertTrue(editableNode.enabled)
            assertTrue(PixelSemanticsAction.SET_TEXT in editableNode.actions)

            states.value = PixelControlStateSet.of(PixelControlState.Loading)
            tester.pumpFrame(0)
            /** Focused but inert semantic snapshot after Loading normalization. */
            val loadingNode = tester.semanticsNodesByLabel("LOADING FIELD").single()
            assertTrue(loadingNode.focused)
            assertFalse(loadingNode.enabled)
            assertTrue(loadingNode.actions.isEmpty())
            assertTrue(focusNode.isFocused)
            assertEquals("LOCKED", fieldState.text)
            assertTrue(tester.renderResult!!.textInputTargets.isEmpty())
            assertTrue(tester.hasPixel(warning))
            assertTrue(tester.hasPixel(focus))
            assertFalse(tester.performSemanticsAction(loadingNode.id, PixelSemanticsAction.CLICK))
            assertEquals("LOCKED", fieldState.text)
        } finally {
            tester.dispose()
        }
    }

    /** Slider hover, pressed, Loading, and Disabled roles are distinct and inert states clean up. */
    @Test
    fun sliderStatesAreDistinctAndLoadingCancelsTheActivePointer() {
        /** Normal active-track sentinel. */
        val normal = PixelColor.fromRgb(31, 127, 211)
        /** Hover active-track sentinel. */
        val hovered = PixelColor.fromRgb(223, 149, 37)
        /** Pressed active-track sentinel. */
        val pressed = PixelColor.fromRgb(137, 53, 229)
        /** Loading track shares the Warning role used by Hovered active content. */
        val loadingTrack = hovered
        /** Loading active-track sentinel. */
        val loadingActive = PixelColor.fromRgb(13, 199, 157)
        /** Disabled track sentinel. */
        val disabledTrack = PixelColor.fromRgb(73, 79, 83)
        /** Disabled active-track sentinel. */
        val disabledActive = PixelColor.fromRgb(149, 157, 163)
        /** Scheme assigning unique concrete colors to every Slider role. */
        val colors = PixelColorScheme.Dark.copy(
            primary = normal,
            warning = hovered,
            selection = pressed,
            onWarning = loadingActive,
            disabled = disabledTrack,
            onDisabled = disabledActive,
        )
        /** Persistent state source changed while Slider owns an active pointer. */
        val states = ValueNotifier(PixelControlStateSet.Normal)
        /** Focus node retained during Loading but removed by Disabled. */
        val focusNode = FocusNode(debugLabel = "state-slider")
        /** Release count proving stale pointer-up cannot submit a Loading value. */
        var releases = 0
        /** Off-screen runtime with shared feedback animation. */
        val tester = PixelTester()
        try {
            tester.pumpWidget(
                widget = PixelTheme(
                    tokens = PixelThemeTokens.Default.copy(colors = colors),
                    child = motionRoot(tester) {
                        Focus(
                            node = focusNode,
                            child = ValueListenableBuilder(states) { _, currentStates ->
                                Slider(
                                    value = 0.5f,
                                    states = currentStates,
                                    onDrag = {},
                                    onRelease = { releases += 1 },
                                    semanticLabel = "STATE SLIDER",
                                    key = "state-slider",
                                )
                            },
                        )
                    },
                ),
                logicalWidth = 32,
                logicalHeight = 12,
            )
            assertTrue(tester.hasPixel(normal))
            tester.hover(find.byKey("state-slider"))
            tester.pumpAndSettle(maxFrames = 80)
            assertTrue(tester.hasPixel(hovered))

            focusNode.requestFocus()
            tester.pumpFrame(0)
            /** Pointer sequence that makes Pressed win over Hovered. */
            val gesture = tester.startGesture(find.byKey("state-slider"))
            tester.pumpAndSettle(maxFrames = 80)
            assertTrue(tester.hasPixel(pressed))

            states.value = PixelControlStateSet.of(PixelControlState.Loading)
            tester.pumpFrame(0)
            /** Focus-retaining Loading semantic snapshot. */
            val loadingNode = tester.semanticsNodesByLabel("STATE SLIDER").single()
            assertTrue(loadingNode.focused)
            assertFalse(loadingNode.enabled)
            assertFalse(PixelSemanticsAction.SET_PROGRESS in loadingNode.actions)
            assertTrue(tester.hasPixel(loadingTrack))
            assertTrue(tester.hasPixel(loadingActive))
            gesture.up()
            assertEquals(0, releases)
            assertEquals(0, tester.vsync.activeTickerCount)

            states.value = PixelControlStateSet.of(PixelControlState.Disabled)
            tester.pumpFrame(0)
            /** Disabled semantic snapshot after focus eligibility is removed. */
            val disabledNode = tester.semanticsNodesByLabel("STATE SLIDER").single()
            assertFalse(disabledNode.focused)
            assertFalse(disabledNode.enabled)
            assertTrue(tester.hasPixel(disabledTrack))
            assertTrue(tester.hasPixel(disabledActive))
            assertEquals(0, tester.vsync.liveTickerCount)
        } finally {
            tester.dispose()
        }
    }

    /** Explicit parameters and styles remain above Error component tokens on every owned channel. */
    @Test
    fun explicitOverridesRemainAboveErrorTokens() {
        /** Explicit Button fill sentinel. */
        val buttonFill = PixelColor.fromRgb(101, 7, 151)
        /** Explicit Button border sentinel. */
        val buttonBorder = PixelColor.fromRgb(7, 181, 107)
        /** Explicit Button content sentinel. */
        val buttonText = PixelColor.fromRgb(239, 211, 31)
        /** Explicit field fill sentinel. */
        val fieldFill = PixelColor.fromRgb(33, 57, 81)
        /** Explicit field border sentinel. */
        val fieldBorder = PixelColor.fromRgb(211, 89, 43)
        /** Explicit field content sentinel. */
        val fieldText = PixelColor.fromRgb(113, 227, 67)
        /** Explicit Slider active sentinel. */
        val sliderActive = PixelColor.fromRgb(173, 29, 219)
        /** Explicit Slider track sentinel. */
        val sliderTrack = PixelColor.fromRgb(37, 193, 231)
        /** Controlled error field owner. */
        val controller = PixelTextFieldController()
        /** Non-empty error field state that paints the explicit content color. */
        val state = controller.create(initialText = "I")
        /** Off-screen runtime rendering all explicit override channels. */
        val tester = PixelTester()
        try {
            tester.pumpWidget(
                widget = Column(
                    children = listOf(
                        OutlinedButton(
                            text = "I",
                            onPressed = {},
                            states = PixelControlStateSet.of(PixelControlState.Error),
                            style = PixelButtonStyle(textStyle = PixelTextStyle(color = buttonText)),
                            fillColor = buttonFill,
                            borderColor = buttonBorder,
                        ),
                        TextField(
                            state = state,
                            controller = controller,
                            states = PixelControlStateSet.of(PixelControlState.Error),
                            style = PixelTextFieldStyle(
                                fillColor = fieldFill,
                                borderColor = fieldBorder,
                                textStyle = PixelTextStyle(color = fieldText),
                            ),
                            semanticLabel = "Explicit error field",
                        ),
                        Slider(
                            value = 0.5f,
                            states = PixelControlStateSet.of(PixelControlState.Error),
                            onDrag = {},
                            onRelease = {},
                            activeColor = sliderActive,
                            trackColor = sliderTrack,
                            semanticLabel = "Explicit error slider",
                        ),
                    ),
                    spacing = 1,
                ),
                logicalWidth = 64,
                logicalHeight = 40,
            )

            listOf(
                buttonFill,
                buttonBorder,
                buttonText,
                fieldFill,
                fieldBorder,
                fieldText,
                sliderActive,
                sliderTrack,
            ).forEach { sentinel ->
                assertTrue("Missing explicit sentinel $sentinel", tester.hasPixel(sentinel))
            }
        } finally {
            tester.dispose()
        }
    }

    /** Wraps one child in a deterministic linear Feedback motion environment. */
    private fun motionRoot(tester: PixelTester, child: () -> Widget): Widget {
        /** Linear long-running spec exposing ticker creation and cancellation deterministically. */
        val feedback = PixelMotionSpec(
            duration = 1_000.milliseconds,
            curve = Curves.Linear,
            role = PixelMotionRole.Feedback,
        )
        return PixelMotionScope(
            vsync = tester.vsync,
            settings = PixelMotionSettings.Default,
            child = PixelMotionTheme(
                data = PixelMotionThemeData.Default.copy(feedback = feedback),
                child = child(),
            ),
        )
    }
}
