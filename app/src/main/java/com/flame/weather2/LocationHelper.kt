package com.flame.weather2

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.util.Log

class LocationHelper(private val context: Context, private val onLocationReceived: (Double, Double) -> Unit) {

    var latestLocation: Pair<Double, Double>? = null

    private val locationManager: LocationManager =
        context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            val latitude = location.latitude
            val longitude = location.longitude
            latestLocation = Pair(latitude, longitude)
            onLocationReceived(latitude, longitude)
            Log.d("GPS_DATA", "latitude: $latitude, longitude: $longitude")

            onLocationReceived(latitude, longitude)
        }

        override fun onProviderEnabled(provider: String) { Log.d("GPS", "GPS is on") }
        override fun onProviderDisabled(provider: String) { Log.d("GPS", "GPS is turned off") }
    }

    @SuppressLint("MissingPermission")
    fun startLocationUpdates() {
        val isGpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        val isNetworkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)

        if (isGpsEnabled || isNetworkEnabled) {
            val provider = if (isGpsEnabled) LocationManager.GPS_PROVIDER else LocationManager.NETWORK_PROVIDER

            locationManager.requestLocationUpdates(
                provider,
                5000L,
                100f,
                locationListener
            )
        } else {
            Log.e("GPS", "GPS Provider Unavailable")
        }
    }

    fun stopLocationUpdates() {
        locationManager.removeUpdates(locationListener)
    }
}