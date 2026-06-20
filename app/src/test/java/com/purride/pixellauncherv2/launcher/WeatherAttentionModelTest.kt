package com.purride.pixellauncherv2.launcher

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WeatherAttentionModelTest {

    @Test
    fun attentionWeatherIncludesRiskLabels() {
        listOf("DRIZZLE 12C", "RAIN 18C", "SNOW -2C", "STORM 25C", "FOG 9C").forEach { summary ->
            assertTrue(summary, WeatherAttentionModel.isAttentionWeather(summary))
        }
    }

    @Test
    fun ordinaryWeatherDoesNotNeedLauncherAttention() {
        listOf("", "CLEAR 22C", "LIGHT CLOUD 20C", "HEAVY CLOUD 18C", "UNKNOWN 19C").forEach { summary ->
            assertFalse(summary, WeatherAttentionModel.isAttentionWeather(summary))
        }
    }
}
