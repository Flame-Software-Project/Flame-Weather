package com.flame.weather2

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.TimeZone

class WeatherRepository {
    private val client = OkHttpClient()

    suspend fun fetchFullWeather(lat: Double, lon: Double): WeatherInfo? = withContext(Dispatchers.IO) {
        // 坐标格式化
        val url = "https://api.met.no/weatherapi/locationforecast/2.0/complete?lat=${String.format("%.4f", lat)}&lon=${String.format("%.4f", lon)}"
        val request = Request.Builder()
            .url(url)
            .addHeader("User-Agent", "FlameWeather/1.0 https://github.com/Flame-Software-Project")
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: return@use null
                val json = JSONObject(body)
                val timeseries = json.getJSONObject("properties").getJSONArray("timeseries")

                val now = timeseries.getJSONObject(0).getJSONObject("data")
                val instant = now.getJSONObject("instant").getJSONObject("details")
                val currentSymbol = now.optJSONObject("next_1_hours")?.getJSONObject("summary")?.optString("symbol_code")

                val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'")
                inputFormat.timeZone = TimeZone.getTimeZone("UTC")
                val displayFormat = SimpleDateFormat("MM-dd")
                displayFormat.timeZone = TimeZone.getDefault()

                val forecastList = mutableListOf<DailyForecast>()
                val datesSeen = mutableSetOf<String>()

                val todayFormatted = displayFormat.format(System.currentTimeMillis())

                for (i in 0 until timeseries.length()) {
                    val entry = timeseries.getJSONObject(i)
                    val timeStr = entry.getString("time")

                    val date = inputFormat.parse(timeStr) ?: continue
                    val localDateStr = displayFormat.format(date)

                    if (localDateStr != todayFormatted && !datesSeen.contains(localDateStr) && timeStr.contains("T12:00:00Z")) {
                        val data = entry.getJSONObject("data")
                        val temp = data.getJSONObject("instant").getJSONObject("details").getDouble("air_temperature")
                        val fSymbol = data.optJSONObject("next_6_hours")?.getJSONObject("summary")?.optString("symbol_code")

                        forecastList.add(DailyForecast(localDateStr, "${temp.toInt()}°C", fSymbol))
                        datesSeen.add(localDateStr)
                    }
                }

                WeatherInfo(
                    locationName = "",
                    currentTemp = "${instant.getDouble("air_temperature")}°C",
                    wind = "风速: ${instant.getDouble("wind_speed")} m/s",
                    precipitation = "降雨: ${now.optJSONObject("next_1_hours")?.getJSONObject("details")?.optDouble("precipitation_amount") ?: 0.0} mm",
                    aqi = "AQI: 暂无数据",
                    forecast = forecastList,
                    symbolCode = currentSymbol
                )
            }
        } catch (e: Exception) {
            Log.e("WeatherRepo", "获取失败: ${e.message}")
            null
        }
    }
}