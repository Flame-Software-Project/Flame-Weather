package com.flame.weather2

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

class IPLocationHelper(private val context: Context) {
    suspend fun getIpLocation(): Pair<Double, Double>? = withContext(Dispatchers.IO) {
        try {
            val client = OkHttpClient()
            val request = Request.Builder()
                .url("https://freeipapi.com/api/json")
                .addHeader("User-Agent", "Mozilla/5.0")
                .build()
            client.newCall(request).execute().use { resp ->
                val body = resp.body?.string() ?: ""
                val json = JSONObject(body)
                Pair(json.getDouble("latitude"), json.getDouble("longitude"))
            }
        } catch (e: Exception) { null }
    }
}