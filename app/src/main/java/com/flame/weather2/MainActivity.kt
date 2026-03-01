package com.flame.weather2

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.flame.weather2.ui.theme.WeatherTheme
import kotlinx.coroutines.*
import java.util.*


enum class AppLanguage { EN, ZH }

data class WeatherInfo(
    val locationName: String,
    val currentTemp: String,
    val wind: String,
    val rain: String,
    val aqi: String,
    val forecast: List<DailyForecast>,
    val currentSymbol: String?
)

data class DailyForecast(
    val date: String,
    val temp: String,
    val symbol: String?
)

data class LocationSuggestion(
    val name: String,
    val detail: String,
    val lat: Double,
    val lon: Double
)

class MainActivity : AppCompatActivity() {
    private var weatherData by mutableStateOf<WeatherInfo?>(null)
    private var statusMessage by mutableStateOf("Initializing...")
    private var currentLang by mutableStateOf(AppLanguage.ZH)
    private var isManualLocation by mutableStateOf(false)

    // 搜索建议与防抖
    private var suggestions by mutableStateOf<List<LocationSuggestion>>(emptyList())
    private var searchJob: Job? = null


    private var showSettingsDialog by mutableStateOf(false)

    private lateinit var locationHelper: LocationHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        locationHelper = LocationHelper(this) { lat, lon ->
            if (!isManualLocation) {
                updateWeatherUI(lat, lon)
            }
        }

        setContent {
            WeatherTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    MainScreen()
                }
            }
        }

        checkPermissionsAndStart()
    }

    @Composable
    fun MainScreen() {
        Box(modifier = Modifier.fillMaxSize()) {
            WeatherDashboard(
                status = statusMessage,
                data = weatherData,
                lang = currentLang,
                suggestions = suggestions,
                onQueryChange = { query -> debounceSearch(query) },
                onLocationSelected = { suggestion ->
                    suggestions = emptyList()
                    isManualLocation = true
                    statusMessage = if (currentLang == AppLanguage.ZH) "定位: ${suggestion.name}" else "Location: ${suggestion.name}"
                    updateWeatherUI(suggestion.lat, suggestion.lon)
                },
                onResetLocation = {
                    isManualLocation = false
                    statusMessage = if (currentLang == AppLanguage.ZH) "切换至自动定位..." else "Switching to Auto..."
                    startSmartLocation()
                }
            )


            SmallFloatingActionButton(
                onClick = { showSettingsDialog = true },
                modifier = Modifier.align(Alignment.BottomEnd).padding(24.dp)
            ) {
                Icon(Icons.Rounded.Settings, contentDescription = "Settings")
            }


            if (showSettingsDialog) {
                SettingsDialog(
                    currentLang = currentLang,
                    onLanguageChange = { currentLang = it },
                    onDismiss = { showSettingsDialog = false }
                )
            }
        }
    }


    private fun debounceSearch(query: String) {
        searchJob?.cancel()
        if (query.isBlank()) {
            suggestions = emptyList()
            return
        }

        searchJob = lifecycleScope.launch {
            delay(500)
            fetchSuggestions(query)
        }
    }

    private suspend fun fetchSuggestions(query: String) {
        withContext(Dispatchers.IO) {
            try {
                val langParam = if (currentLang == AppLanguage.ZH) "zh" else "en"
                val url = "https://geocoding-api.open-meteo.com/v1/search?name=${query}&count=5&language=$langParam"

                val client = okhttp3.OkHttpClient()
                val request = okhttp3.Request.Builder().url(url).build()

                client.newCall(request).execute().use { response ->
                    val body = response.body?.string() ?: ""
                    val json = org.json.JSONObject(body)

                    val newList = mutableListOf<LocationSuggestion>()
                    if (json.has("results")) {
                        val results = json.getJSONArray("results")
                        for (i in 0 until results.length()) {
                            val item = results.getJSONObject(i)
                            newList.add(LocationSuggestion(
                                name = item.getString("name"),
                                detail = "${item.optString("admin1", "")}, ${item.optString("country", "")}",
                                lat = item.getDouble("latitude"),
                                lon = item.getDouble("longitude")
                            ))
                        }
                    }

                    withContext(Dispatchers.Main) {
                        suggestions = newList
                    }
                }
            } catch (e: Exception) {

            }
        }
    }

    private fun updateWeatherUI(lat: Double, lon: Double) {
        lifecycleScope.launch {
            val repo = WeatherRepository()
            val data = repo.fetchFullWeather(lat, lon)
            if (data != null) {
                weatherData = data
                if (!isManualLocation) statusMessage = if (currentLang == AppLanguage.ZH) "定位成功" else "Updated via GPS/IP"
            } else {
                statusMessage = if (currentLang == AppLanguage.ZH) "获取天气失败" else "Fetch failed"
            }
        }
    }

    private fun checkPermissionsAndStart() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED) {
            startSmartLocation()
        } else {
            statusMessage = if (currentLang == AppLanguage.ZH) "等待权限..." else "Waiting for permission..."
        }
    }

    private fun startSmartLocation() {
        locationHelper.startLocationUpdates()
        lifecycleScope.launch {
            val ipLoc = IPLocationHelper(this@MainActivity).getIpLocation()
            if (ipLoc != null && !isManualLocation && weatherData == null) {
                updateWeatherUI(ipLoc.first, ipLoc.second)
            }
        }
    }
}


@Composable
fun SettingsDialog(
    currentLang: AppLanguage,
    onLanguageChange: (AppLanguage) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (currentLang == AppLanguage.ZH) "设置" else "Settings") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = if (currentLang == AppLanguage.ZH) "语言 / Language" else "Language",
                    style = MaterialTheme.typography.labelMedium
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    FilterChip(
                        selected = currentLang == AppLanguage.ZH,
                        onClick = { onLanguageChange(AppLanguage.ZH) },
                        label = { Text("中文") }
                    )
                    FilterChip(
                        selected = currentLang == AppLanguage.EN,
                        onClick = { onLanguageChange(AppLanguage.EN) },
                        label = { Text("English") }
                    )
                }

                HorizontalDivider()

                Text(
                    text = if (currentLang == AppLanguage.ZH) "关于" else "About",
                    style = MaterialTheme.typography.labelMedium
                )
                Text(
                    text = if (currentLang == AppLanguage.ZH)
                        "Flame Weather v1.1\n基于 Open-Meteo 与 Met.no 数据源"
                    else "Flame Weather v1.1\nPowered by Open-Meteo & Met.no",
                    style = MaterialTheme.typography.bodySmall
                )

                TextButton(
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Flame-Software-Project/Flame-Wea  ther"))
                        context.startActivity(intent)
                    },
                    modifier = Modifier.padding(0.dp)
                ) {
                    Icon(Icons.Rounded.Link, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("GitHub Repository", fontSize = 14.sp)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(if (currentLang == AppLanguage.ZH) "确定" else "OK")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeatherDashboard(
    status: String,
    data: WeatherInfo?,
    lang: AppLanguage,
    suggestions: List<LocationSuggestion>,
    onQueryChange: (String) -> Unit,
    onLocationSelected: (LocationSuggestion) -> Unit,
    onResetLocation: () -> Unit
) {
    var searchText by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
        contentPadding = PaddingValues(top = 48.dp, bottom = 24.dp)
    ) {
        item {
            Text(
                text = if (lang == AppLanguage.ZH) "天气预报" else "Weather",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(16.dp))


            OutlinedTextField(
                value = searchText,
                onValueChange = {
                    searchText = it
                    onQueryChange(it)
                },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(if (lang == AppLanguage.ZH) "输入城市名..." else "Search City...") },
                trailingIcon = {
                    if (searchText.isNotEmpty()) {
                        IconButton(onClick = {
                            searchText = ""
                            onQueryChange("")
                        }) {
                            Icon(Icons.Rounded.Close, null)
                        }
                    } else {
                        Icon(Icons.Rounded.Search, null)
                    }
                },
                singleLine = true
            )

            AnimatedVisibility(visible = suggestions.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Column {
                        suggestions.forEach { suggestion ->
                            ListItem(
                                headlineContent = { Text(suggestion.name) },
                                supportingContent = { Text(suggestion.detail, fontSize = 12.sp) },
                                modifier = Modifier.clickable {
                                    onLocationSelected(suggestion)
                                    searchText = ""
                                }
                            )
                            if (suggestion != suggestions.last()) {
                                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp)
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(status, style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = onResetLocation) {
                    Icon(Icons.Rounded.MyLocation, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(if (lang == AppLanguage.ZH) "自动定位" else "Auto", fontSize = 12.sp)
                }
            }
        }

        if (data != null) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Icon(mapWeatherCodeToIcon(data.currentSymbol), null, modifier = Modifier.size(48.dp))
                        Text(data.currentTemp, style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold)
                        Text(translateSymbol(data.currentSymbol, lang), style = MaterialTheme.typography.titleLarge)
                        Text("${data.wind}  |  ${data.rain}")
                    }
                }
            }

            items(data.forecast) { day ->
                ListItem(
                    headlineContent = { Text(day.date) },
                    supportingContent = { Text(translateSymbol(day.symbol, lang)) },
                    trailingContent = { Text(day.temp, fontWeight = FontWeight.Bold) },
                    leadingContent = { Icon(mapWeatherCodeToIcon(day.symbol), null) }
                )
            }
        }
    }
}

fun mapWeatherCodeToIcon(code: String?): ImageVector {
    val s = code?.lowercase() ?: ""
    return when {
        s.contains("clearsky") || s.contains("fair") -> Icons.Rounded.WbSunny
        s.contains("cloudy") || s.contains("partlycloudy") -> Icons.Rounded.Cloud
        s.contains("rain") -> Icons.Rounded.Umbrella
        s.contains("snow") -> Icons.Rounded.AcUnit
        s.contains("thunder") -> Icons.Rounded.FlashOn
        else -> Icons.Rounded.WbCloudy
    }
}

fun translateSymbol(code: String?, lang: AppLanguage): String {
    val s = code?.lowercase() ?: return "Unknown"
    val isZh = lang == AppLanguage.ZH
    return when {
        s.contains("clearsky") -> if (isZh) "晴朗" else "Clear"
        s.contains("fair") -> if (isZh) "晴间多云" else "Fair"
        s.contains("cloudy") -> if (isZh) "阴" else "Cloudy"
        s.contains("rain") -> if (isZh) "雨" else "Rain"
        s.contains("snow") -> if (isZh) "雪" else "Snow"
        s.contains("thunder") -> if (isZh) "雷阵雨" else "Thunderstorm"
        else -> s
    }
}