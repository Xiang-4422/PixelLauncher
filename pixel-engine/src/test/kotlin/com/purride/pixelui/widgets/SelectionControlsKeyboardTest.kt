package com.purride.pixelui.widgets

import com.purride.pixelcore.PixelBitmap
import com.purride.pixelcore.PixelColor
import com.purride.pixelui.Column
import com.purride.pixelui.IconButton
import com.purride.pixelui.OutlinedButton
import com.purride.pixelui.PixelControlState
import com.purride.pixelui.PixelControlStateSet
import com.purride.pixelui.PixelIconData
import com.purride.pixelui.PixelKey
import com.purride.pixelui.PixelRadioOption
import com.purride.pixelui.PixelSemanticRole
import com.purride.pixelui.Radio
import com.purride.pixelui.RadioGroup
import com.purride.pixelui.testing.PixelTester
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Verifies M5-2 selection controls own complete automatic keyboard and focus behavior. */
class SelectionControlsKeyboardTest {
    /** Standalone Radio and IconButton activate from Enter and Space without caller Focus wrappers. */
    @Test
    fun standaloneControlsActivateFromKeyboard() {
        /** Ordered trace proving each key is routed to exactly one focused control. */
        val actions = mutableListOf<String>()
        /** Off-screen runtime owning one isolated automatic focus tree. */
        val tester = PixelTester()
        try {
            tester.pumpWidget(
                widget = Column(
                    children = listOf(
                        Radio(
                            selected = false,
                            onSelected = { actions += "radio" },
                            semanticLabel = "Mode",
                        ),
                        IconButton(
                            icon = opaqueIcon(),
                            onPressed = { actions += "icon" },
                            semanticLabel = "Save",
                        ),
                    ),
                ),
                logicalWidth = 64,
                logicalHeight = 48,
            )

            assertTrue(tester.pressKey(PixelKey.TAB))
            assertTrue(tester.semanticsNodesByLabel("Mode").single().focused)
            assertTrue(tester.pressKey(PixelKey.ENTER))
            assertTrue(tester.pressKey(PixelKey.TAB))
            assertTrue(tester.semanticsNodesByLabel("Save").single().focused)
            assertTrue(tester.pressKey(PixelKey.SPACE))
            assertEquals(listOf("radio", "icon"), actions)
        } finally {
            tester.dispose()
        }
    }

    /** RadioGroup is one Tab stop and four directions update a genuinely controlled selection. */
    @Test
    fun radioGroupUsesOneStopAndFourDirectionControlledSelection() {
        /** Stable business options retained across every controlled rebuild. */
        val options = listOf(
            PixelRadioOption(id = "a", label = "A"),
            PixelRadioOption(id = "b", label = "B"),
            PixelRadioOption(id = "c", label = "C"),
        )
        /** Caller-owned selected business id changed only by the callback. */
        var selectedId = "b"
        /** Every selection request, including explicit current-item activation. */
        val requests = mutableListOf<String>()
        /** Off-screen runtime retaining group focus across root rebuilds. */
        val tester = PixelTester()
        try {
            /** Builds the current controlled group followed by a traversal sentinel. */
            fun buildTree() = Column(
                children = listOf(
                    RadioGroup(
                        options = options,
                        selectedId = selectedId,
                        onSelected = { requestedId ->
                            requests += requestedId
                            selectedId = requestedId
                        },
                        semanticLabel = "Choice",
                        key = "choice-group",
                    ),
                    OutlinedButton(text = "NEXT", onPressed = {}, key = "next"),
                ),
            )

            /** Rebuilds after a controlled request and verifies exactly one focused checked item. */
            fun rebuildAndAssert(expectedId: String) {
                tester.pumpWidget(buildTree(), logicalWidth = 64, logicalHeight = 64)
                /** Current Radio nodes in business order. */
                val radios = tester.semanticsNodes().filter { node ->
                    node.role == PixelSemanticRole.RADIO_BUTTON
                }
                assertEquals(1, radios.count { node -> node.checked == true })
                assertEquals(1, radios.count { node -> node.focused })
                assertEquals(true, tester.semanticsNodesByLabel(expectedId.uppercase()).single().checked)
                assertTrue(tester.semanticsNodesByLabel(expectedId.uppercase()).single().focused)
            }

            tester.pumpWidget(buildTree(), logicalWidth = 64, logicalHeight = 64)
            assertTrue(tester.pressKey(PixelKey.TAB))
            assertTrue(tester.semanticsNodesByLabel("B").single().focused)

            assertTrue(tester.pressKey(PixelKey.ARROW_RIGHT))
            rebuildAndAssert("c")
            assertTrue(tester.pressKey(PixelKey.ARROW_DOWN))
            rebuildAndAssert("a")
            assertTrue(tester.pressKey(PixelKey.ARROW_LEFT))
            rebuildAndAssert("c")
            assertTrue(tester.pressKey(PixelKey.ARROW_UP))
            rebuildAndAssert("b")
            assertTrue(tester.pressKey(PixelKey.ENTER))
            rebuildAndAssert("b")
            assertTrue(tester.pressKey(PixelKey.SPACE))
            rebuildAndAssert("b")
            assertEquals(listOf("c", "a", "c", "b", "b", "b"), requests)

            assertTrue(tester.pressKey(PixelKey.TAB))
            assertTrue(tester.semanticsNodesByLabel("NEXT").single().focused)
        } finally {
            tester.dispose()
        }
    }

    /** Disabled options are skipped and Loading stays focused without consuming activation. */
    @Test
    fun disabledOptionsAndLoadingRespectCapabilityFocusRules() {
        /** Requested option proving directional traversal skips the disabled selected row. */
        var requestedId: String? = null
        /** Standalone Loading Radio activation count, which must remain zero. */
        var loadingActivations = 0
        /** Off-screen runtime exercising compound and standalone capability states. */
        val tester = PixelTester()
        try {
            tester.pumpWidget(
                widget = Column(
                    children = listOf(
                        RadioGroup(
                            options = listOf(
                                PixelRadioOption(id = "a", label = "A"),
                                PixelRadioOption(id = "b", label = "B", enabled = false),
                                PixelRadioOption(id = "c", label = "C"),
                            ),
                            selectedId = "b",
                            onSelected = { id -> requestedId = id },
                            semanticLabel = "Skip disabled",
                        ),
                        Radio(
                            selected = true,
                            onSelected = { loadingActivations += 1 },
                            semanticLabel = "Loading radio",
                            states = PixelControlStateSet.of(PixelControlState.Loading),
                        ),
                        IconButton(
                            icon = opaqueIcon(),
                            onPressed = {},
                            semanticLabel = "Disabled icon",
                            enabled = false,
                        ),
                        OutlinedButton(text = "END", onPressed = {}),
                    ),
                ),
                logicalWidth = 72,
                logicalHeight = 96,
            )

            assertTrue(tester.pressKey(PixelKey.TAB))
            assertTrue(tester.semanticsNodesByLabel("A").single().focused)
            assertTrue(tester.pressKey(PixelKey.ARROW_RIGHT))
            assertEquals("c", requestedId)

            assertTrue(tester.pressKey(PixelKey.TAB))
            /** Loading keeps the Radio focused but omits its activation handler. */
            val loadingRadio = tester.semanticsNodesByLabel("Loading radio").single()
            assertTrue(loadingRadio.focused)
            assertFalse(loadingRadio.enabled)
            assertFalse(tester.pressKey(PixelKey.ENTER))
            assertEquals(0, loadingActivations)

            assertTrue(tester.pressKey(PixelKey.TAB))
            assertFalse(tester.semanticsNodesByLabel("Disabled icon").single().focused)
            assertTrue(tester.semanticsNodesByLabel("END").single().focused)
        } finally {
            tester.dispose()
        }
    }

    /** Creates a tiny opaque icon mask for keyboard-only IconButton tests. */
    private fun opaqueIcon(): PixelIconData {
        /** One opaque pixel sufficient to exercise layout and activation. */
        val pixels = intArrayOf(PixelColor.White.argb)
        return PixelIconData(PixelBitmap(width = 1, height = 1, pixels = pixels))
    }
}
