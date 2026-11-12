package com.flotalamacarena.telemetry.domain.ports

import com.flotalamacarena.telemetry.domain.models.LocationPoint

/**
 * Puerto para obtener la ubicación física del dispositivo mediante GPS satelital o Red.
 */
interface LocationProvider {
    suspend fun getCurrentLocation(timeoutMillis: Long = 15000L): LocationPoint?
    fun getLastKnownLocation(): LocationPoint?
    fun isLocationEnabled(): Boolean
    fun hasLocationPermission(): Boolean
}
