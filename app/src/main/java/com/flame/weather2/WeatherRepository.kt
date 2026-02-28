package com.flame.weather2

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
        // English comment: Build API URL with 4-decimal precision
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

                val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'").apply { timeZone = TimeZone.getTimeZone("UTC") }
                val displayFormat = SimpleDateFormat("MM-dd").apply { timeZone = TimeZone.getDefault() }
                val forecastList = mutableListOf<DailyForecast>()
                val datesSeen = mutableSetOf<String>()
                val todayStr = displayFormat.format(System.currentTimeMillis())

                for (i in 0 until timeseries.length()) {
                    val entry = timeseries.getJSONObject(i)
                    val time = entry.getString("time")
                    val date = inputFormat.parse(time) ?: continue
                    val localDate = displayFormat.format(date)

                    // English comment: Extract daily forecast at 12:00 UTC
                    if (localDate != todayStr && !datesSeen.contains(localDate) && time.contains("T12:00:00Z")) {
                        val data = entry.getJSONObject("data")
                        val temp = data.getJSONObject("instant").getJSONObject("details").getDouble("air_temperature")
                        val fSymbol = data.optJSONObject("next_6_hours")?.getJSONObject("summary")?.optString("symbol_code")
                        forecastList.add(DailyForecast(localDate, "${temp.toInt()}°C", fSymbol))
                        datesSeen.add(localDate)
                    }
                }
                WeatherInfo("", "${instant.getDouble("air_temperature")}°C", "Wind: ${instant.getDouble("wind_speed")} m/s", "Rain: ${now.optJSONObject("next_1_hours")?.getJSONObject("details")?.optDouble("precipitation_amount") ?: 0.0} mm", "AQI: Normal", forecastList, currentSymbol)
            }
        } catch (e: Exception) { null }
    }
}