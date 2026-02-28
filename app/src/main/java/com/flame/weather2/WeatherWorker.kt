package com.flame.weather2

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class WeatherWorker(appContext: Context, workerParams: WorkerParameters) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        val lat = inputData.getDouble("lat", 0.0)
        val lon = inputData.getDouble("lon", 0.0)
        if (lat == 0.0) return Result.failure()

        return try {
            val repository = WeatherRepository()
            val result = repository.fetchFullWeather(lat, lon)
            if (result != null) {
                // English comment: Persist background data for Widget use
                val prefs = applicationContext.getSharedPreferences("weather_prefs", Context.MODE_PRIVATE)
                prefs.edit().putString("last_temp", result.currentTemp).apply()

                // English comment: Trigger Widget UI update
                val mgr = AppWidgetManager.getInstance(applicationContext)
                val ids = mgr.getAppWidgetIds(ComponentName(applicationContext, WeatherWidget::class.java))
                ids.forEach { WeatherWidget.updateAppWidget(applicationContext, mgr, it) }

                Result.success()
            } else Result.retry()
        } catch (e: Exception) { Result.retry() }
    }
}