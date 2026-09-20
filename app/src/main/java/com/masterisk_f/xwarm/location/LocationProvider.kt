package com.masterisk_f.xwarm.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

data class Coordinates(val latitude: Double, val longitude: Double)

interface LocationProvider {
    fun hasPermission(): Boolean
    suspend fun getCurrentLocation(): Coordinates?
}

class AndroidLocationProvider(private val context: Context) : LocationProvider {

    private val locationManager: LocationManager? by lazy {
        context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
    }

    override fun hasPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    override suspend fun getCurrentLocation(): Coordinates? = withContext(Dispatchers.Main) {
        if (!hasPermission()) return@withContext null
        val lm = locationManager ?: return@withContext null

        // 1. Try to get last known location immediately (R1 quick response)
        val lastGps = tryGetLastKnown(lm, LocationManager.GPS_PROVIDER)
        val lastNet = tryGetLastKnown(lm, LocationManager.NETWORK_PROVIDER)
        val bestLast = pickBetterLocation(lastGps, lastNet)

        // 2. Request a fresh single location with timeout (e.g. 8 seconds)
        val fresh = withTimeoutOrNull(8000L) {
            requestSingleLocationUpdate(lm)
        }

        val finalLoc = fresh ?: bestLast
        finalLoc?.let { Coordinates(it.latitude, it.longitude) }
    }

    private fun tryGetLastKnown(lm: LocationManager, provider: String): Location? {
        return try {
            if (lm.isProviderEnabled(provider)) {
                lm.getLastKnownLocation(provider)
            } else null
        } catch (e: SecurityException) {
            null
        }
    }

    private fun pickBetterLocation(loc1: Location?, loc2: Location?): Location? {
        if (loc1 == null) return loc2
        if (loc2 == null) return loc1
        return if (loc1.time >= loc2.time) loc1 else loc2
    }

    private suspend fun requestSingleLocationUpdate(lm: LocationManager): Location? =
        suspendCancellableCoroutine { continuation ->
            val listener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    try {
                        lm.removeUpdates(this)
                    } catch (_: SecurityException) {}
                    if (continuation.isActive) {
                        continuation.resume(location)
                    }
                }

                @Deprecated("Deprecated in Java")
                override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
                override fun onProviderEnabled(provider: String) {}
                override fun onProviderDisabled(provider: String) {}
            }

            var requested = false
            try {
                if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                    lm.requestLocationUpdates(
                        LocationManager.GPS_PROVIDER,
                        0L,
                        0f,
                        listener,
                        Looper.getMainLooper()
                    )
                    requested = true
                }
                if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                    lm.requestLocationUpdates(
                        LocationManager.NETWORK_PROVIDER,
                        0L,
                        0f,
                        listener,
                        Looper.getMainLooper()
                    )
                    requested = true
                }
            } catch (e: SecurityException) {
                if (continuation.isActive) continuation.resume(null)
                return@suspendCancellableCoroutine
            }

            if (!requested) {
                if (continuation.isActive) continuation.resume(null)
                return@suspendCancellableCoroutine
            }

            continuation.invokeOnCancellation {
                try {
                    lm.removeUpdates(listener)
                } catch (_: SecurityException) {}
            }
        }
}
