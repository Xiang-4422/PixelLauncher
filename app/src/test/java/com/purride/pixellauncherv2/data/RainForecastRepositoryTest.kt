package com.purride.pixellauncherv2.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Coverage for the pure weather-summary helpers in [RainForecastRepository].
 *
 * Only the companion formatting/mapping functions are exercised — the network
 * path and `parseWeatherSummary` rely on `org.json`, which is not available in
 * plain JVM unit tests. `buildWeatherSummary` exists precisely so the formatting
 * logic can be tested without JSON.
 */
class RainForecastRepositoryTest {

    @Test
    fun weatherCodeToLabel_mapsWmoCodes() {
        assertEquals("CLEAR", RainForecastRepository.weatherCodeToLabel(0))
        assertEquals("LIGHT CLOUD", RainForecastRepository.weatherCodeToLabel(1))
        assertEquals("LIGHT CLOUD", RainForecastRepository.weatherCodeToLabel(2))
        assertEquals("HEAVY CLOUD", RainForecastRepository.weatherCodeToLabel(3))
        assertEquals("FOG", RainForecastRepository.weatherCodeToLabel(45))
        assertEquals("DRIZZLE", RainForecastRepository.weatherCodeToLabel(51))
        assertEquals("RAIN", RainForecastRepository.weatherCodeToLabel(65))
        assertEquals("SNOW", RainForecastRepository.weatherCodeToLabel(75))
        assertEquals("STORM", RainForecastRepository.weatherCodeToLabel(95))
        assertEquals("UNKNOWN", RainForecastRepository.weatherCodeToLabel(12345))
    }

    @Test
    fun buildWeatherSummary_combinesLabelAndTruncatedTemperature() {
        assertEquals("CLEAR 22C", RainForecastRepository.buildWeatherSummary(22.7, 0))
        assertEquals("SNOW -3C", RainForecastRepository.buildWeatherSummary(-3.9, 71))
        assertEquals("RAIN 0C", RainForecastRepository.buildWeatherSummary(0.4, 61))
    }

    @Test
    fun buildWeatherSummary_returnsNullForMissingData() {
        assertNull(RainForecastRepository.buildWeatherSummary(Double.NaN, 0))
        assertNull(RainForecastRepository.buildWeatherSummary(20.0, Int.MIN_VALUE))
    }
}
