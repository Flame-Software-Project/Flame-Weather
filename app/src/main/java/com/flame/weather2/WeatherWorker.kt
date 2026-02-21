package com.flame.weather2

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import android.util.Log

class WeatherWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val lat = inputData.getDouble("lat", 0.0)
        val lon = inputData.getDouble("lon", 0.0)

        if (lat == 0.0) return Result.failure()

        return try {
            Log.d("WeatherWorker", "后台刷新任务开始: $lat, $lon")
            val repository = WeatherRepository()
            val result = repository.fetchFullWeather(lat, lon)

            if (result != null) {
                Result.success()
            } else {
                Result.retry()
            }
        } catch (e: Exception) {
            Result.retry()
        }
    }
}