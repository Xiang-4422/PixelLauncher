package com.purride.pixellauncherv2.launcher

import com.purride.pixellauncherv2.layout.LauncherLayoutProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticsBoundsModelTest {

    @Test
    fun snapshotReportsUsableBodyRowsForNormalScreen() {
        val snapshot = DiagnosticsBoundsModel.snapshot(
            LauncherLayoutProfile(logicalWidth = 120, logicalHeight = 240, dotSizePx = 4, statusBarHeight = 12),
        )

        assertTrue(snapshot.geometryOk)
        assertEquals(116, snapshot.contentWidthPx)
        assertEquals(224, snapshot.bodyHeightPx)
        assertEquals(18, snapshot.visibleRowCount)
        assertEquals("OK 18 ROW", snapshot.summary)
    }

    @Test
    fun snapshotReportsRiskWhenStatusConsumesBody() {
        val snapshot = DiagnosticsBoundsModel.snapshot(
            LauncherLayoutProfile(logicalWidth = 120, logicalHeight = 12, dotSizePx = 4, statusBarHeight = 12),
        )

        assertFalse(snapshot.geometryOk)
        assertEquals(0, snapshot.bodyHeightPx)
        assertEquals(0, snapshot.visibleRowCount)
        assertEquals("RISK 0 ROW", snapshot.summary)
    }
}
