package com.purride.pixelui.widgets

import com.purride.pixelcore.PixelColor
import com.purride.pixelui.PixelInspectorBoundsOverlay
import com.purride.pixelui.PixelInspectorSnapshot
import com.purride.pixelui.PixelInspectorTargetCounts
import com.purride.pixelui.PixelInspectorTargetKind
import com.purride.pixelui.PixelInspectorTargetSnapshot
import com.purride.pixelui.testing.PixelTester
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PixelInspectorBoundsOverlayTest {
    @Test
    fun overlayDrawsFilteredBoundsWithoutExportingHitTargets() {
        val tester = PixelTester()
        tester.pumpWidget(
            widget = PixelInspectorBoundsOverlay(
                snapshot = snapshot(
                    targets = listOf(
                        PixelInspectorTargetSnapshot(
                            kind = PixelInspectorTargetKind.CLICK,
                            left = 0,
                            top = 0,
                            width = 3,
                            height = 3,
                        ),
                        PixelInspectorTargetSnapshot(
                            kind = PixelInspectorTargetKind.LIST,
                            left = 2,
                            top = 2,
                            width = 5,
                            height = 4,
                        ),
                    ),
                ),
                width = 8,
                height = 8,
                kinds = setOf(PixelInspectorTargetKind.LIST),
            ),
            logicalWidth = 8,
            logicalHeight = 8,
        )

        val buffer = requireNotNull(tester.renderResult).buffer
        assertEquals(PixelColor.Transparent, buffer.getPixel(0, 0))
        assertEquals(PixelColor.fromRgb(70, 220, 110), buffer.getPixel(2, 2))
        assertEquals(PixelColor.Transparent, buffer.getPixel(3, 3))
        assertTrue(requireNotNull(tester.renderResult).clickTargets.isEmpty())
        tester.dispose()
    }

    private fun snapshot(targets: List<PixelInspectorTargetSnapshot>): PixelInspectorSnapshot {
        return PixelInspectorSnapshot(
            frameStats = null,
            allocationSample = null,
            targetCounts = PixelInspectorTargetCounts.Empty,
            targetSnapshots = targets,
            elementTree = "",
            renderTree = "",
            semanticsTree = "",
            hasPendingBuild = false,
            focusedTextInput = false,
            activePagerCount = 0,
            activeListCount = 0,
            activeSlider = false,
            activeScrollbar = false,
            activeRefresh = false,
        )
    }
}
