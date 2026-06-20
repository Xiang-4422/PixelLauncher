package com.purride.pixellauncherv2.launcher

import com.purride.pixellauncherv2.viewmodel.LauncherUiState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IdlePresentationModelTest {

    @Test
    fun nightWindowStartsAtTwentyTwoAndEndsBeforeSix() {
        assertTrue(IdlePresentationModel.presentation(LauncherUiState(currentTimeText = "22:00")).isNight)
        assertTrue(IdlePresentationModel.presentation(LauncherUiState(currentTimeText = "05:30")).isNight)
        assertFalse(IdlePresentationModel.presentation(LauncherUiState(currentTimeText = "06:00")).isNight)
        assertFalse(IdlePresentationModel.presentation(LauncherUiState(currentTimeText = "14:00")).isNight)
    }

    @Test
    fun invalidTimeTextKeepsNormalBrightness() {
        assertFalse(IdlePresentationModel.presentation(LauncherUiState(currentTimeText = "")).isNight)
        assertFalse(IdlePresentationModel.presentation(LauncherUiState(currentTimeText = "--:--")).isNight)
    }

    @Test
    fun chargingIdleHidesFooterToReduceInformationDensity() {
        assertFalse(IdlePresentationModel.presentation(LauncherUiState(isCharging = true)).showFooter)
        assertTrue(IdlePresentationModel.presentation(LauncherUiState(isCharging = false)).showFooter)
    }
}
