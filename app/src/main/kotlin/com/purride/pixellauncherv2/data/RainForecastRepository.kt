package com.purride.pixellauncherv2.data

import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

class RainForecastRepository(
    private val connectionFactory: (URL) -> HttpURLConnection = { url ->
        url.openConnection() as HttpURLConnection
    },
) {

    /**
     * 从 Open-Meteo 拉取未来六小时内最早的有效降雨时间。
     *
     * 返回 `null` 表示未来窗口内没有满足阈值的降雨，或响应无法解析。
     */
    fun fetchRainHint(latitude: Double, longitude: Double): String? {
        val requestUrl = URL(
            String.format(
                Locale.ENGLISH,
                forecastUrlTemplate,
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
                throw IllegalStateException("Rain forecast request failed with code $responseCode")
            }

            val responseBody = BufferedReader(
                InputStreamReader(connection.inputStream),
            ).use { reader -> reader.readText() }
            return parseRainHint(responseBody)
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        private const val probabilityThresholdPercent = 50.0
        private const val precipitationThresholdMm = 0.2
        private const val networkTimeoutMs = 5_000
        private const val forecastUrlTemplate =
            "https://api.open-meteo.com/v1/forecast?" +
                "latitude=%.6f&longitude=%.6f" +
                "&hourly=precipitation_probability,precipitation" +
                "&forecast_hours=6&timezone=auto"

        private val inputTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME
        private val outputTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH)

        /** 解析 API 响应，并提取 Home 使用的第一条有效降雨时间。 */
        fun parseRainHint(responseBody: String): String? {
            val root = JSONObject(responseBody)
            val hourly = root.optJSONObject("hourly") ?: return null
            return selectEarliestRainTime(
                times = hourly.optJSONArray("time"),
                precipitationProbabilities = hourly.optJSONArray("precipitation_probability"),
                precipitationAmounts = hourly.optJSONArray("precipitation"),
            )
        }

        /** 供测试和 JSON 解析路径复用的重载方法。 */
        fun selectEarliestRainTime(
            times: JSONArray?,
            precipitationProbabilities: JSONArray?,
            precipitationAmounts: JSONArray?,
        ): String? {
            return selectEarliestRainTime(
                times = times.toStringList(),
                precipitationProbabilities = precipitationProbabilities.toDoubleList(),
                precipitationAmounts = precipitationAmounts.toDoubleList(),
            )
        }

        /**
         * 选择同时满足降雨概率阈值和降雨量阈值的最早小时。
         */
        fun selectEarliestRainTime(
            times: List<String>,
            precipitationProbabilities: List<Double>,
            precipitationAmounts: List<Double>,
        ): String? {
            val count = minOf(
                times.size,
                precipitationProbabilities.size,
                precipitationAmounts.size,
            )
            if (count <= 0) {
                return null
            }

            for (index in 0 until count) {
                val probability = precipitationProbabilities[index]
                val precipitation = precipitationAmounts[index]
                if (probability < probabilityThresholdPercent || precipitation < precipitationThresholdMm) {
                    continue
                }
                val isoTime = times[index]
                return formatRainTime(isoTime)
            }
            return null
        }

        /** 把 Open-Meteo 的 ISO 本地时间转换成 Home 固定使用的 `HH:mm` 格式。 */
        fun formatRainTime(isoTime: String): String? {
            if (isoTime.isBlank()) {
                return null
            }
            return runCatching {
                LocalDateTime.parse(isoTime, inputTimeFormatter).format(outputTimeFormatter)
            }.getOrNull()
        }

        private fun JSONArray?.toStringList(): List<String> {
            val array = this ?: return emptyList()
            return List(array.length()) { index ->
                array.optString(index)
            }
        }

        private fun JSONArray?.toDoubleList(): List<Double> {
            val array = this ?: return emptyList()
            return List(array.length()) { index ->
                array.optDouble(index, Double.NaN)
            }
        }
    }
}
