package com.flotalamacarena.telemetry.domain.models

/**
 * Representa la configuración de identidad y conexión del agente en el dispositivo.
 * Esta información se almacena de forma cifrada en hardware.
 */
data class DeviceConfig(
    val placa: String = "",
    val serverUrl: String = DEFAULT_SERVER_URL,
    val apiKey: String = "",
    val syncIntervalMinutes: Int = DEFAULT_INTERVAL_MINUTES,
    val isEnrolled: Boolean = false
) {
    val isValid: Boolean
        get() = placa.isNotBlank() && serverUrl.isNotBlank() && apiKey.isNotBlank()

    companion object {
        const val DEFAULT_SERVER_URL = "http://localhost:3000"
        const val DEFAULT_API_KEY = "flm-telemetry-secret-key-2026"
        const val DEFAULT_INTERVAL_MINUTES = 15
        const val MIN_INTERVAL_MINUTES = 5
        const val MAX_INTERVAL_MINUTES = 120
    }
}
