package com.purride.pixelui

import com.purride.pixelcore.PixelGridGeometryResolver
import com.purride.pixelcore.ScreenProfile
import com.purride.pixelui.internal.host.PixelAccessibilityBounds
import com.purride.pixelui.internal.host.buildPixelAccessibilityNodeSnapshots
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PixelHostAccessibilityNodeProviderTest {
    @Test
    fun semanticsNodesMapToAndroidAccessibilitySnapshots() {
        val geometry = PixelGridGeometryResolver.resolve(
            viewWidth = 100,
            viewHeight = 80,
            profile = ScreenProfile(logicalWidth = 10, logicalHeight = 8, dotSizePx = 10),
            pixelGapEnabled = false,
            pixelGapRatio = 0f,
        )!!

        val snapshots = buildPixelAccessibilityNodeSnapshots(
            semanticsNodes = listOf(
                PixelSemanticsNode(
                    label = "OK",
                    role = PixelSemanticRole.BUTTON,
                    enabled = true,
                    focused = true,
                    left = 2,
                    top = 1,
                    width = 3,
                    height = 2,
                ),
            ),
            geometry = geometry,
        )

        val snapshot = snapshots.single()
        assertEquals(1, snapshot.virtualViewId)
        assertEquals("OK", snapshot.label)
        assertEquals("android.widget.Button", snapshot.className)
        assertTrue(snapshot.clickable)
        assertEquals(PixelAccessibilityBounds(left = 20, top = 10, right = 50, bottom = 30), snapshot.bounds)
        assertEquals(3, snapshot.centerLogicalX)
        assertEquals(2, snapshot.centerLogicalY)
    }

    @Test
    fun zeroSizedSemanticsNodesAreSkipped() {
        val geometry = PixelGridGeometryResolver.resolve(
            viewWidth = 100,
            viewHeight = 80,
            profile = ScreenProfile(logicalWidth = 10, logicalHeight = 8, dotSizePx = 10),
            pixelGapEnabled = false,
            pixelGapRatio = 0f,
        )!!

        val snapshots = buildPixelAccessibilityNodeSnapshots(
            semanticsNodes = listOf(
                PixelSemanticsNode(
                    label = "EMPTY",
                    role = PixelSemanticRole.TEXT,
                    enabled = true,
                    focused = false,
                    left = 0,
                    top = 0,
                    width = 0,
                    height = 2,
                ),
            ),
            geometry = geometry,
        )

        assertTrue(snapshots.isEmpty())
    }
}
