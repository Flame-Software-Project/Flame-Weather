package com.flame.weather2

import android.net.Uri
import android.content.Intent
import android.provider.Settings
import android.os.Bundle
import android.location.Geocoder
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.*
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
import androidx.work.*
import com.flame.weather2.ui.theme.WeatherTheme
import com.google.android.material.color.DynamicColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.*
import java.util.Locale
import java.util.concurrent.TimeUnit

data class DailyForecast(val date: String, val temp: String, val symbolCode: String?)
data class WeatherInfo(
    val locationName: String,
    val currentTemp: String,
    val wind: String,
    val precipitation: String,
    val aqi: String,
    val forecast: List<DailyForecast>,
    val symbolCode: String?
)
enum class AppLanguage { EN, ZH }

class MainActivity : AppCompatActivity() {
    private lateinit var locationHelper: LocationHelper
    private val weatherRepository = WeatherRepository()
    private val ipLocationHelper by lazy { IPLocationHelper(this) }

    private var statusMessage by mutableStateOf("Initializing...")
    private var weatherData by mutableStateOf<WeatherInfo?>(null)
    private var isSettingPage by mutableStateOf(false)
    private var currentLang by mutableStateOf(AppLanguage.ZH)
    private var lastLocation by mutableStateOf<Pair<Double, Double>?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        DynamicColors.applyToActivityIfAvailable(this)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        locationHelper = LocationHelper(this) { lat, lon ->
            lastLocation = Pair(lat, lon)
            updateWeatherUI(lat, lon)
            setupBackgroundWork(lat, lon)
            locationHelper.stopLocationUpdates()
        }

        setContent {
            WeatherTheme {
                LaunchedEffect(Unit) {
                    while (true) {
                        startSmartLocation()
                        delay(20 * 60 * 1000L)
                    }
                }

                Surface(color = MaterialTheme.colorScheme.background) {
                    AnimatedContent(targetState = isSettingPage, label = "Nav") { settingActive ->
                        if (settingActive) {
                            SettingsPage(
                                lang = currentLang,
                                onLangChange = { newLang ->
                                    currentLang = newLang
                                    lastLocation?.let { updateWeatherUI(it.first, it.second) }
                                },
                                onBack = { isSettingPage = false }
                            )
                        } else {
                            MainScreen()
                        }
                    }
                }
            }
        }
        checkAndHandlePermission()
    }

    @Composable
    fun MainScreen() {
        Box(modifier = Modifier.fillMaxSize()) {
            WeatherDashboard("Bruce-Liu-28", statusMessage, weatherData, currentLang, Modifier.padding(top = 32.dp))
            IconButton(
                onClick = { isSettingPage = true },
                modifier = Modifier.align(Alignment.TopEnd).padding(top = 40.dp, end = 16.dp)
            ) {
                Icon(Icons.Rounded.Settings, "Settings", modifier = Modifier.size(32.dp))
            }
        }
    }

    private fun startSmartLocation() {
        lifecycleScope.launch {
            // Check location permission before accessing GPS
            val hasPerm = ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

            if (hasPerm) {
                statusMessage = "Locating..."
                locationHelper.startLocationUpdates()
                val gps = withTimeoutOrNull(8000) {
                    while (locationHelper.latestLocation == null) delay(500)
                    locationHelper.latestLocation
                }
                locationHelper.stopLocationUpdates()

                if (gps != null) {
                    updateWeatherUI(gps.first, gps.second)
                    return@launch
                }
            }

            // Fallback to IP if GPS is unavailable or permission is denied
            statusMessage = "Using IP Location..."
            val ipLoc = withContext(Dispatchers.IO) { ipLocationHelper.getIpLocation() }
            if (ipLoc != null) updateWeatherUI(ipLoc.first, ipLoc.second)
            else statusMessage = "Location Failed"
        }
    }

    private fun updateWeatherUI(lat: Double, lon: Double) {
        lifecycleScope.launch {
            statusMessage = "Syncing..."
            val resolvedName = withContext(Dispatchers.IO) {
                try {
                    val locale = if (currentLang == AppLanguage.ZH) Locale.CHINA else Locale.US
                    val geocoder = Geocoder(this@MainActivity, locale)
                    val addr = geocoder.getFromLocation(lat, lon, 1)?.get(0)
                    val city = addr?.locality ?: addr?.adminArea ?: ""
                    val district = addr?.subLocality ?: ""
                    if (city.isNotEmpty()) "$city, $district" else "Unknown"
                } catch (e: Exception) { "Unknown" }
            }
            val result = weatherRepository.fetchFullWeather(lat, lon)
            if (result != null) {
                weatherData = result.copy(locationName = resolvedName)
                statusMessage = "Updated"
            } else { statusMessage = "Sync Error" }
        }
    }

    private fun setupBackgroundWork(lat: Double, lon: Double) {
        // Schedule periodic background refresh
        val data = Data.Builder().putDouble("lat", lat).putDouble("lon", lon).build()
        val request = PeriodicWorkRequestBuilder<WeatherWorker>(20, TimeUnit.MINUTES)
            .setInputData(data)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork("WeatherRefresh", ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    private fun checkAndHandlePermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            showPermissionDialog()
        }
    }

    private fun showPermissionDialog() {
        MaterialAlertDialogBuilder(this).setTitle("Location Access").setMessage("Please enable GPS for accurate results.").setPositiveButton("Settings") { _, _ -> startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))) }.setNegativeButton("Ignore", null).show()
    }
}

// --- UI COMPONENTS ---

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsPage(lang: AppLanguage, onLangChange: (AppLanguage) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    BackHandler { onBack() }
    Column(modifier = Modifier.fillMaxSize().padding(24.dp).padding(top = 32.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, null) }
            Text(if (lang == AppLanguage.ZH) "设置" else "Settings", style = MaterialTheme.typography.headlineMedium)
        }
        Spacer(modifier = Modifier.height(32.dp))
        Text(if (lang == AppLanguage.ZH) "界面语言" else "Language", style = MaterialTheme.typography.titleLarge)
        Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            FilterChip(selected = lang == AppLanguage.ZH, onClick = { onLangChange(AppLanguage.ZH) }, label = { Text("简体中文") })
            FilterChip(selected = lang == AppLanguage.EN, onClick = { onLangChange(AppLanguage.EN) }, label = { Text("English") })
        }
        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
        Card(modifier = Modifier.fillMaxWidth().clickable { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Flame-Software-Project/Flame-Weather"))) }) {
            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Info, null)
                Spacer(modifier = Modifier.width(16.dp))
                Text("GitHub: Flame Weather")
            }
        }
    }
}

@Composable
fun WeatherDashboard(author: String, status: String, data: WeatherInfo?, lang: AppLanguage, modifier: Modifier = Modifier) {
    LazyColumn(modifier = modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        item {
            Text("Flame Weather", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
            Text("Status: $status", style = MaterialTheme.typography.labelSmall)
            Spacer(modifier = Modifier.height(24.dp))
        }
        if (data != null) {
            item {
                // Main weather display
                Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(data.locationName, style = MaterialTheme.typography.titleMedium)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(data.currentTemp, style = MaterialTheme.typography.displayLarge, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.weight(1f))
                            Icon(mapWeatherCodeToIcon(data.symbolCode), null, modifier = Modifier.size(64.dp))
                        }
                        Text(translateSymbol(data.symbolCode, lang), style = MaterialTheme.typography.headlineSmall)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(data.wind)
                        Text(data.precipitation)
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
                Text(if (lang == AppLanguage.ZH) "未来预报" else "Daily Forecast", style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(12.dp))
            }
            // Forecast list with icons
            items(data.forecast) { day ->
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(day.date, modifier = Modifier.weight(1f))
                        Icon(mapWeatherCodeToIcon(day.symbolCode), null, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(day.temp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        } else {
            item {
                Box(modifier = Modifier.fillMaxWidth().padding(top = 100.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        }
    }
}

// Utility to map MET symbol codes to Material Icons
fun mapWeatherCodeToIcon(code: String?): ImageVector {
    val s = code?.lowercase() ?: ""
    return when {
        s.contains("clearsky") || s.contains("fair") -> Icons.Rounded.WbSunny
        s.contains("cloudy") -> Icons.Rounded.Cloud
        s.contains("rain") -> Icons.Rounded.Umbrella
        s.contains("snow") -> Icons.Rounded.AcUnit
        s.contains("thunder") -> Icons.Rounded.FlashOn
        else -> Icons.Rounded.WbCloudy
    }
}

// Translate API weather codes to readable strings
fun translateSymbol(code: String?, lang: AppLanguage): String {
    val s = code?.lowercase() ?: return "Unknown"
    val isZh = lang == AppLanguage.ZH
    return when {
        s.contains("clearsky") -> if (isZh) "晴朗" else "Clear Sky"
        s.contains("fair") -> if (isZh) "晴间多云" else "Fair"
        s.contains("partlycloudy") -> if (isZh) "多云" else "Partly Cloudy"
        s.contains("cloudy") -> if (isZh) "阴天" else "Overcast"
        s.contains("rain") -> if (isZh) "雨" else "Rain"
        s.contains("snow") -> if (isZh) "雪" else "Snow"
        s.contains("thunder") -> if (isZh) "雷阵雨" else "Thunderstorm"
        else -> s.replace("_", " ").replaceFirstChar { it.uppercase() }
    }
}