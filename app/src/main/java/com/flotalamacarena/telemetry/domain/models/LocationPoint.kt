package com.flotalamacarena.telemetry.domain.models

/**
 * Punto geográfico capturado por los sensores del dispositivo.
 * Incluye metadatos de precisión y verificación anti-falsificación (Anti-Mock).
 */
data class LocationPoint(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float = 0.0f,
    val isMock: Boolean = false,
    val provider: String = "unknown",
    val timestamp: Long = System.currentTimeMillis()
) {
    val isValidCoordinates: Boolean
        get() = latitude in -90.0..90.0 && longitude in -180.0..180.0 && !(latitude == 0.0 && longitude == 0.0)
}
