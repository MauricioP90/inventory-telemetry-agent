package com.flotalamacarena.telemetry.domain.ports

import com.flotalamacarena.telemetry.domain.models.DeviceConfig

/**
 * Puerto para persistir y recuperar de forma segura los parámetros de configuración.
 */
interface ConfigRepository {
    fun getConfig(): DeviceConfig
    fun saveConfig(config: DeviceConfig): Boolean
    fun isEnrolled(): Boolean
    fun clearConfig(): Boolean
    fun saveLastKnownLocation(lat: Double, lng: Double, timestamp: Long)
    fun getLastKnownCoordinates(): Pair<Double, Double>?
}
