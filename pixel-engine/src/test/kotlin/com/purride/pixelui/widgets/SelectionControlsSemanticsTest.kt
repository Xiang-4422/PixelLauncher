package com.purride.pixelui.widgets

import com.purride.pixelcore.PixelBitmap
import com.purride.pixelcore.PixelColor
import com.purride.pixelui.Column
import com.purride.pixelui.IconButton
import com.purride.pixelui.PixelIconData
import com.purride.pixelui.PixelRadioOption
import com.purride.pixelui.PixelSemanticRole
import com.purride.pixelui.PixelSemanticsAction
import com.purride.pixelui.PixelSemanticsSelectionMode
import com.purride.pixelui.Radio
import com.purride.pixelui.RadioGroup
import com.purride.pixelui.testing.PixelTester
import com.purride.pixelui.testing.find
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** Locks controlled actions, stable identity, and structured semantics for M5-2 selection controls. */
class SelectionControlsSemanticsTest {
    /** Standalone Radio and IconButton expose one merged action node for pointer and semantics. */
    @Test
    fun standaloneControlsExportOneMergedActionNode() {
        /** Radio activation count shared by pointer and accessibility routes. */
        var radioSelections = 0
        /** Icon-button activation count shared by pointer and accessibility routes. */
        var iconActivations = 0
        /** Off-screen runtime collecting semantic structure and pointer targets. */
        val tester = PixelTester()
        try {
            tester.pumpWidget(
                widget = Column(
                    children = listOf(
                        Radio(
                            selected = false,
                            onSelected = { radioSelections += 1 },
                            semanticLabel = "Choose archive",
                            key = "radio",
                        ),
                        IconButton(
                            icon = opaqueIcon(),
                            onPressed = { iconActivations += 1 },
                            semanticLabel = "Save document",
                            selected = true,
                            key = "icon-button",
                        ),
                    ),
                ),
                logicalWidth = 72,
                logicalHeight = 48,
            )

            /** Exact standalone Radio semantic node. */
            val radio = tester.semanticsNodesByLabel("Choose archive").single()
            /** Exact IconButton semantic node with its visual image descendants excluded. */
            val iconButton = tester.semanticsNodesByLabel("Save document").single()
            assertEquals(PixelSemanticRole.RADIO_BUTTON, radio.role)
            assertEquals(false, radio.checked)
            assertFalse(radio.selected)
            assertEquals(PixelSemanticRole.BUTTON, iconButton.role)
            assertTrue(iconButton.selected)
            assertTrue(iconButton.width >= 24)
            assertTrue(iconButton.height >= 24)
            assertTrue(tester.semanticsNodes().none { node -> node.role == PixelSemanticRole.IMAGE })

            tester.tap(find.byKey("radio"))
            assertEquals(1, radioSelections)
            assertFalse(tester.semanticsNodesByLabel("Choose archive").single().selected)
            assertTrue(tester.performSemanticsAction(iconButton.id, PixelSemanticsAction.CLICK))
            assertEquals(1, iconActivations)
        } finally {
            tester.dispose()
        }
    }

    /** RadioGroup exports one SINGLE collection and preserves item ids through business reordering. */
    @Test
    fun radioGroupExportsCollectionMetadataAndStableBusinessIdentity() {
        /** Initial visual order with one caller-owned selected business id. */
        var options = listOf(
            PixelRadioOption(id = "alpha", label = "Alpha"),
            PixelRadioOption(id = "beta", label = "Beta"),
            PixelRadioOption(id = "gamma", label = "Gamma"),
        )
        /** Controlled selection retained by the test owner rather than the component. */
        var selectedId = "beta"
        /** Ordered business ids requested by semantic actions. */
        val requests = mutableListOf<String>()
        /** Off-screen runtime used for controlled rebuild and semantic-id reconciliation. */
        val tester = PixelTester()
        try {
            /** Builds the latest controlled group while retaining one stable group identity. */
            fun buildGroup() = RadioGroup(
                options = options,
                selectedId = selectedId,
                onSelected = { requestedId -> requests += requestedId },
                semanticLabel = "Delivery speed",
                key = "delivery-group",
            )

            tester.pumpWidget(buildGroup(), logicalWidth = 96, logicalHeight = 48)
            /** Parent collection node carrying single-selection policy. */
            val group = tester.semanticsNodesByLabel("Delivery speed").single()
            /** Radio option nodes retained in visual row order. */
            val radios = tester.semanticsNodes().filter { node -> node.role == PixelSemanticRole.RADIO_BUTTON }
            assertEquals(3, group.collectionInfo?.rowCount)
            assertEquals(1, group.collectionInfo?.columnCount)
            assertEquals(PixelSemanticsSelectionMode.SINGLE, group.collectionInfo?.selectionMode)
            assertEquals(listOf(false, true, false), radios.map { node -> node.checked })
            assertEquals(listOf(0, 1, 2), radios.map { node -> node.collectionItemInfo?.rowIndex })
            assertTrue(radios.all { node -> node.parentId == group.id })
            /** Stable semantic id owned by the beta business option before reordering. */
            val betaSemanticId = tester.semanticsNodesByLabel("Beta").single().id

            /** Semantic click requests alpha but cannot mutate the caller-owned selected id. */
            val alphaNode = tester.semanticsNodesByLabel("Alpha").single()
            assertTrue(tester.performSemanticsAction(alphaNode.id, PixelSemanticsAction.CLICK))
            assertEquals(listOf("alpha"), requests)
            assertEquals(true, tester.semanticsNodesByLabel("Beta").single().checked)

            selectedId = "alpha"
            tester.pumpWidget(buildGroup(), logicalWidth = 96, logicalHeight = 48)
            assertEquals(true, tester.semanticsNodesByLabel("Alpha").single().checked)
            assertEquals(false, tester.semanticsNodesByLabel("Beta").single().checked)

            options = listOf(options[1], options[2], options[0])
            selectedId = "beta"
            tester.pumpWidget(buildGroup(), logicalWidth = 96, logicalHeight = 48)
            /** Reordered beta node retains identity while publishing its new row position. */
            val reorderedBeta = tester.semanticsNodesByLabel("Beta").single()
            assertEquals(betaSemanticId, reorderedBeta.id)
            assertEquals(0, reorderedBeta.collectionItemInfo?.rowIndex)
            assertEquals(true, reorderedBeta.collectionItemInfo?.selected)
        } finally {
            tester.dispose()
        }
    }

    /** Public entry points reject ambiguous selection and inaccessible labels before mounting. */
    @Test
    fun invalidSelectionAndLabelsFailFast() {
        /** Minimal opaque icon used by fail-fast IconButton construction. */
        val icon = opaqueIcon()
        assertThrows(IllegalArgumentException::class.java) {
            Radio(selected = false, onSelected = {}, semanticLabel = "   ")
        }
        assertThrows(IllegalArgumentException::class.java) {
            IconButton(icon = icon, onPressed = {}, semanticLabel = "")
        }
        assertThrows(IllegalArgumentException::class.java) {
            PixelRadioOption(id = "id", label = "\t")
        }
        assertThrows(IllegalArgumentException::class.java) {
            RadioGroup(
                options = listOf(PixelRadioOption(id = "one", label = "One")),
                selectedId = null,
                onSelected = {},
                semanticLabel = "Group",
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            RadioGroup(
                options = emptyList<PixelRadioOption<String>>(),
                selectedId = "ghost",
                onSelected = {},
                semanticLabel = "Group",
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            RadioGroup(
                options = listOf(
                    PixelRadioOption(id = "same", label = "First"),
                    PixelRadioOption(id = "same", label = "Second"),
                ),
                selectedId = "same",
                onSelected = {},
                semanticLabel = "Group",
            )
        }
    }

    /** Creates a tiny opaque alpha-mask icon for selection-control tests. */
    private fun opaqueIcon(): PixelIconData {
        /** Fully opaque source pixels whose RGB must be replaced by IconButton theme tint. */
        val pixels = IntArray(9) { PixelColor.White.argb }
        return PixelIconData(PixelBitmap(width = 3, height = 3, pixels = pixels))
    }
}
