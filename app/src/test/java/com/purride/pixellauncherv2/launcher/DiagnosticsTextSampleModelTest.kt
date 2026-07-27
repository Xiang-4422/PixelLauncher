package com.purride.pixellauncherv2.launcher

import com.purride.pixellauncherv2.layout.LauncherLayoutProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticsTextSampleModelTest {

    @Test
    fun samplesMeasureHomeDataAndSettingsTextAgainstContentWidth() {
        val profile = LauncherLayoutProfile(logicalWidth = 120, logicalHeight = 240, dotSizePx = 4)
        val state = LauncherState(
            hasLocationPermission = true,
            screenUsageTimeText = "00:20",
            screenOpenCountText = "2",
        )
        val samples = DiagnosticsTextSampleModel.samples(state, profile)
        val byText = samples.associateBy { it.text }

        assertEquals(116, samples.first().availablePx)
        assertEquals(102, byText.getValue("USE 00:20  OPEN 2").widthPx)
        assertEquals(90, byText.getValue("USAGE NO ACCESS").widthPx)
        assertEquals(102, byText.getValue("EFFECT DOT MATRIX").widthPx)
    }

    @Test
    fun summaryReportsOkWithLargestSampleAndAvailableWidth() {
        val profile = LauncherLayoutProfile(logicalWidth = 120, logicalHeight = 240, dotSizePx = 4)

        assertEquals("OK 114/116", DiagnosticsTextSampleModel.summary(readyState(), profile))
    }

    @Test
    fun summaryReportsRiskWhenSamplesExceedAvailableWidth() {
        val profile = LauncherLayoutProfile(logicalWidth = 80, logicalHeight = 240, dotSizePx = 4)

        assertTrue(DiagnosticsTextSampleModel.summary(readyState(), profile).startsWith("RISK "))
        assertFalse(DiagnosticsTextSampleModel.samples(readyState(), profile).all { it.fits })
    }

    @Test
    fun estimatedTextWidthTreatsChineseAsWideGlyphs() {
        assertEquals(26, PixelFontCatalog.estimatedTextWidth("短信A"))
    }

    private fun readyState(): LauncherState {
        return LauncherState(
            hasUsageAccess = true,
            hasLocationPermission = true,
            hasCallLogPermission = true,
            hasSmsReadPermission = true,
            isDefaultSmsApp = true,
            smsPermissionState = SmsPermissionState.READY,
            hasPostNotificationPermission = true,
            hasNotificationListenerAccess = true,
        )
    }
}
