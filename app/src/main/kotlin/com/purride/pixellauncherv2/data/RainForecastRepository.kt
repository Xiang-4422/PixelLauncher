package com.purride.pixellauncherv2.data

import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

class RainForecastRepository(
    private val connectionFactory: (URL) -> HttpURLConnection = { url ->
        url.openConnection() as HttpURLConnection
    },
) {

    /** 从 Open-Meteo 拉取当前天气和温度摘要。 */
    fun fetchWeatherSummary(latitude: Double, longitude: Double): String? {
        val requestUrl = URL(
            String.format(
                Locale.ENGLISH,
                weatherUrlTemplate,
                latitude,
                longitude,
            ),
        )
        val connection = connectionFactory(requestUrl)
        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = networkTimeoutMs
            connection.readTimeout = networkTimeoutMs
            connection.setRequestProperty("Accept", "application/json")
            connection.connect()

            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                throw IllegalStateException("Weather request failed with code $responseCode")
            }

            val responseBody = BufferedReader(
                InputStreamReader(connection.inputStream),
            ).use { reader -> reader.readText() }
            return parseWeatherSummary(responseBody)
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        private const val networkTimeoutMs = 5_000
        private const val weatherUrlTemplate =
            "https://api.open-meteo.com/v1/forecast?" +
                "latitude=%.6f&longitude=%.6f" +
                "&current=temperature_2m,weather_code&timezone=auto"

        /** 解析 API 响应，并提取 Home 使用的当前天气与温度。 */
        fun parseWeatherSummary(responseBody: String): String? {
            val root = JSONObject(responseBody)
            val current = root.optJSONObject("current") ?: return null
            return buildWeatherSummary(
                temperatureCelsius = current.optDouble("temperature_2m", Double.NaN),
                weatherCode = current.optInt("weather_code", Int.MIN_VALUE),
            )
        }

        /** 供测试和 JSON 解析路径复用的重载方法。 */
        fun buildWeatherSummary(
            temperatureCelsius: Double,
            weatherCode: Int,
        ): String? {
            if (temperatureCelsius.isNaN() || weatherCode == Int.MIN_VALUE) {
                return null
            }
            val weatherLabel = weatherCodeToLabel(weatherCode)
            val roundedTemperature = temperatureCelsius.toInt()
            return "$weatherLabel ${roundedTemperature}C"
        }

        fun weatherCodeToLabel(weatherCode: Int): String {
            return when (weatherCode) {
                0 -> "CLEAR"
                1, 2 -> "LIGHT CLOUD"
                3 -> "HEAVY CLOUD"
                45, 48 -> "FOG"
                51, 53, 55, 56, 57 -> "DRIZZLE"
                61, 63, 65, 66, 67, 80, 81, 82 -> "RAIN"
                71, 73, 75, 77, 85, 86 -> "SNOW"
                95, 96, 99 -> "STORM"
                else -> "UNKNOWN"
            }
        }
    }
}
