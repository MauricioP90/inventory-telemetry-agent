package com.flotalamacarena.telemetry.infrastructure.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.util.Log
import com.flotalamacarena.telemetry.domain.models.LocationPoint
import com.flotalamacarena.telemetry.domain.ports.LocationProvider
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Proveedor de Ubicación Híbrido:
 * 1. Intenta utilizar Google FusedLocationProviderClient si Google Play Services está disponible
 *    (smartphones modernos con Android 11 a 17).
 * 2. Conmuta automáticamente al LocationManager nativo (GPS + Red celular/Wi-Fi) si el dispositivo
 *    es AOSP puro sin Google Play (terminales POS Ciontek CS30 y Topwise T6D).
 * 3. Incorpora detección activa de ubicaciones falsificadas (Anti-Mock GPS).
 */
class HybridLocationProvider(
    private val context: Context
) : LocationProvider {

    private val locationManager: LocationManager by lazy {
        context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    }

    private val fusedLocationClient: FusedLocationProviderClient? by lazy {
        if (isGooglePlayServicesAvailable()) {
            LocationServices.getFusedLocationProviderClient(context)
        } else {
            null
        }
    }

    private fun isGooglePlayServicesAvailable(): Boolean {
        return try {
            val availability = GoogleApiAvailability.getInstance()
            val resultCode = availability.isGooglePlayServicesAvailable(context)
            resultCode == ConnectionResult.SUCCESS
        } catch (e: Throwable) {
            Log.w(TAG, "Google Play Services no disponible en este dispositivo (AOSP): ${e.message}")
            false
        }
    }

    @SuppressLint("MissingPermission")
    override suspend fun getCurrentLocation(timeoutMillis: Long): LocationPoint? {
        if (!isLocationEnabled()) {
            Log.w(TAG, "Los servicios de ubicación del dispositivo están desactivados.")
            return null
        }

        return withTimeoutOrNull(timeoutMillis) {
            // Intento 1: Proveedor Fused de Google (si está disponible)
            var point = getLocationFromFused()
            if (point != null && point.isValidCoordinates) {
                Log.d(TAG, "Ubicación obtenida exitosamente vía FusedLocationProvider: (${point.latitude}, ${point.longitude})")
                return@withTimeoutOrNull point
            }

            // Intento 2: Fallback a LocationManager nativo (GPS de hardware o Red)
            point = getLocationFromNativeManager()
            if (point != null && point.isValidCoordinates) {
                Log.d(TAG, "Ubicación obtenida exitosamente vía LocationManager nativo: (${point.latitude}, ${point.longitude})")
                return@withTimeoutOrNull point
            }

            // Intento 3: Última ubicación en caché del sistema operativo
            getLastKnownLocation()
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun getLocationFromFused(): LocationPoint? {
        val client = fusedLocationClient ?: return null
        return suspendCancellableCoroutine { continuation ->
            val cts = CancellationTokenSource()
            continuation.invokeOnCancellation { cts.cancel() }

            try {
                client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.token)
                    .addOnSuccessListener { location: Location? ->
                        if (location != null) {
                            continuation.resume(mapToLocationPoint(location, "fused"))
                        } else {
                            continuation.resume(null)
                        }
                    }
                    .addOnFailureListener { error ->
                        Log.w(TAG, "Fallo al obtener ubicación Fused: ${error.message}")
                        continuation.resume(null)
                    }
            } catch (e: Exception) {
                Log.e(TAG, "Excepción en FusedLocation: ${e.message}")
                continuation.resume(null)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun getLocationFromNativeManager(): LocationPoint? {
        return suspendCancellableCoroutine { continuation ->
            val isGpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
            val isNetworkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)

            val targetProvider = when {
                isGpsEnabled -> LocationManager.GPS_PROVIDER
                isNetworkEnabled -> LocationManager.NETWORK_PROVIDER
                else -> null
            }

            if (targetProvider == null) {
                continuation.resume(null)
                return@suspendCancellableCoroutine
            }

            val listener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    locationManager.removeUpdates(this)
                    continuation.resume(mapToLocationPoint(location, targetProvider))
                }

                @Deprecated("Deprecated in Java")
                override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
                override fun onProviderEnabled(provider: String) {}
                override fun onProviderDisabled(provider: String) {}
            }

            continuation.invokeOnCancellation {
                locationManager.removeUpdates(listener)
            }

            try {
                locationManager.requestSingleUpdate(
                    targetProvider,
                    listener,
                    Looper.getMainLooper()
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error al solicitar actualización en LocationManager: ${e.message}")
                continuation.resume(null)
            }
        }
    }

    @SuppressLint("MissingPermission")
    override fun getLastKnownLocation(): LocationPoint? {
        return try {
            val providers = listOf(
                LocationManager.GPS_PROVIDER,
                LocationManager.NETWORK_PROVIDER,
                LocationManager.PASSIVE_PROVIDER
            )

            var bestLocation: Location? = null
            for (provider in providers) {
                if (locationManager.isProviderEnabled(provider)) {
                    val loc = locationManager.getLastKnownLocation(provider)
                    if (loc != null) {
                        if (bestLocation == null || loc.accuracy < bestLocation.accuracy) {
                            bestLocation = loc
                        }
                    }
                }
            }

            bestLocation?.let { mapToLocationPoint(it, "last_known") }
        } catch (e: Exception) {
            Log.e(TAG, "Error obteniendo última ubicación conocida: ${e.message}")
            null
        }
    }

    override fun isLocationEnabled(): Boolean {
        return try {
            val isGps = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
            val isNetwork = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
            isGps || isNetwork
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Convierte el Location nativo de Android a nuestro LocationPoint de dominio,
     * evaluando si la coordenada fue falseada mediante Anti-Mock GPS.
     */
    private fun mapToLocationPoint(location: Location, providerName: String): LocationPoint {
        val isMock = isMockLocation(location)
        if (isMock) {
            Log.w(TAG, "⚠️ ALERTA ANTI-MOCK: Se detectó ubicación simulada en el dispositivo.")
        }

        return LocationPoint(
            latitude = location.latitude,
            longitude = location.longitude,
            accuracyMeters = location.accuracy,
            isMock = isMock,
            provider = providerName,
            timestamp = location.time
        )
    }

    /**
     * Detección multi-versión de ubicaciones falsificadas (Anti-Mock GPS):
     * - Android 12+ (API 31+): location.isMock
     * - Android 10-11 (API 29-30): location.isFromMockProvider
     */
    @Suppress("DEPRECATION")
    private fun isMockLocation(location: Location): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            location.isMock
        } else {
            location.isFromMockProvider
        }
    }

    companion object {
        private const val TAG = "HybridLocationProvider"
    }
}
