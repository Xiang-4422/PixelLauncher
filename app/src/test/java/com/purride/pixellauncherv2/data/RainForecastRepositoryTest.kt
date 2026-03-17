package com.purride.pixellauncherv2.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RainForecastRepositoryTest {

    @Test
    fun selectEarliestRainTimeReturnsFirstMatchingHour() {
        val result = RainForecastRepository.selectEarliestRainTime(
            times = listOf(
                "2026-03-17T09:00",
                "2026-03-17T10:00",
                "2026-03-17T11:00",
            ),
            precipitationProbabilities = listOf(20.0, 60.0, 90.0),
            precipitationAmounts = listOf(0.0, 0.3, 1.2),
        )

        assertEquals("10:00", result)
    }

    @Test
    fun selectEarliestRainTimeRequiresProbabilityAndPrecipitationThresholds() {
        val result = RainForecastRepository.selectEarliestRainTime(
            times = listOf(
                "2026-03-17T09:00",
                "2026-03-17T10:00",
                "2026-03-17T11:00",
            ),
            precipitationProbabilities = listOf(80.0, 40.0, 75.0),
            precipitationAmounts = listOf(0.1, 0.5, 0.2),
        )

        assertEquals("11:00", result)
    }

    @Test
    fun selectEarliestRainTimeReturnsNullWhenNothingMatches() {
        val result = RainForecastRepository.selectEarliestRainTime(
            times = listOf(
                "2026-03-17T09:00",
                "2026-03-17T10:00",
            ),
            precipitationProbabilities = listOf(30.0, 45.0),
            precipitationAmounts = listOf(0.0, 0.1),
        )

        assertNull(result)
    }

    @Test
    fun formatRainTimeReturnsNullForBlankValue() {
        assertNull(RainForecastRepository.formatRainTime(""))
    }
}
