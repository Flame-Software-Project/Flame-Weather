package com.flame.weather2

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.flame.weather2.R

class WeatherWidget : AppWidgetProvider() {

    // Called when the widget update period is reached or the user adds a widget
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    // Receive update broadcasts from MainActivity or WeatherWorker
    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (AppWidgetManager.ACTION_APPWIDGET_UPDATE == intent.action) {
            val mgr = AppWidgetManager.getInstance(context)
            val component = ComponentName(context, WeatherWidget::class.java)
            val ids = mgr.getAppWidgetIds(component)
            for (id in ids) {
                updateAppWidget(context, mgr, id)
            }
        }
    }

    companion object {
        fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            val views = RemoteViews(context.packageName, R.layout.weather_widget)

            // Read the latest cached data from SharedPreferences
            val prefs = context.getSharedPreferences("weather_prefs", Context.MODE_PRIVATE)
            val temp = prefs.getString("last_temp", "--") ?: "--"
            val loc = prefs.getString("last_loc", "Locating...") ?: "Locating..."
            val symbolCode = prefs.getString("last_symbol", "") ?: ""

            // 1. Update UI text content
            views.setTextViewText(R.id.widget_temp, temp.replace("°C", "°"))
            views.setTextViewText(R.id.widget_location, loc)

            // 2. Update weather icons
            val iconRes = getIconResource(symbolCode)
            views.setImageViewResource(R.id.widget_icon, iconRes)

            // 3. Implement clicking the widget to jump to the main interface
            val mainIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                0,
                mainIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)

            // Last updated widget
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        private fun getIconResource(code: String?): Int {
            val s = code?.lowercase() ?: ""
            return when {
                s.contains("clearsky") || s.contains("fair") -> R.drawable.ic_sunny
                s.contains("cloudy") || s.contains("partlycloudy") -> R.drawable.ic_cloudy
                s.contains("rain") -> R.drawable.ic_rainy
                s.contains("snow") -> R.drawable.ic_snowy
                s.contains("thunder") -> R.drawable.ic_thunder
                else -> R.drawable.ic_cloudy // Default display: partly cloudy
            }
        }
    }
}