package com.purride.pixellauncherv2.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RainForecastRepositoryTest {

    @Test
    fun buildWeatherSummaryFormatsConditionAndTemperature() {
        val result = RainForecastRepository.buildWeatherSummary(
            temperatureCelsius = 23.7,
            weatherCode = 0,
        )

        assertEquals("CLEAR 23C", result)
    }

    @Test
    fun buildWeatherSummaryReturnsNullForInvalidPayload() {
        val result = RainForecastRepository.buildWeatherSummary(
            temperatureCelsius = Double.NaN,
            weatherCode = Int.MIN_VALUE,
        )

        assertNull(result)
    }

    @Test
    fun weatherCodeToLabelMapsRainGroup() {
        assertEquals("RAIN", RainForecastRepository.weatherCodeToLabel(63))
    }
}
