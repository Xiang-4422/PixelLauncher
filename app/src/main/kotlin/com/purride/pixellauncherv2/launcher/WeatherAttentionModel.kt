package com.purride.pixellauncherv2.launcher

object WeatherAttentionModel {

    fun isAttentionWeather(summary: String): Boolean {
        val label = summary.trim().substringBefore(' ')
        return label in attentionLabels
    }

    private val attentionLabels = setOf(
        "DRIZZLE",
        "RAIN",
        "SNOW",
        "STORM",
        "FOG",
    )
}
