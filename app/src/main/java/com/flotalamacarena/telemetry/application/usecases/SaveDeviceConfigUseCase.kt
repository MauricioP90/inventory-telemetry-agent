package com.flotalamacarena.telemetry.application.usecases

import com.flotalamacarena.telemetry.domain.models.DeviceConfig
import com.flotalamacarena.telemetry.domain.ports.ConfigRepository

/**
 * Caso de uso para validar y guardar de forma segura los parámetros de enrolamiento.
 */
class SaveDeviceConfigUseCase(
    private val configRepository: ConfigRepository
) {
    sealed class Result {
        object Success : Result()
        data class ValidationError(val field: String, val message: String) : Result()
        data class StorageError(val message: String) : Result()
    }

    fun execute(
        placa: String,
        serverUrl: String,
        apiKey: String,
        intervalMinutes: Int = DeviceConfig.DEFAULT_INTERVAL_MINUTES
    ): Result {
        val cleanPlaca = placa.trim().uppercase()
        if (cleanPlaca.isBlank()) {
            return Result.ValidationError("placa", "La placa o serial del equipo no puede estar vacía.")
        }

        val cleanServerUrl = serverUrl.trim().removeSuffix("/")
        if (!cleanServerUrl.startsWith("http://") && !cleanServerUrl.startsWith("https://")) {
            return Result.ValidationError("serverUrl", "La URL del servidor debe comenzar con http:// o https://")
        }

        val cleanApiKey = apiKey.trim()
        if (cleanApiKey.isBlank()) {
            return Result.ValidationError("apiKey", "La API Key (X-Device-Api-Key) es obligatoria.")
        }

        val validInterval = intervalMinutes.coerceIn(
            DeviceConfig.MIN_INTERVAL_MINUTES,
            DeviceConfig.MAX_INTERVAL_MINUTES
        )

        val newConfig = DeviceConfig(
            placa = cleanPlaca,
            serverUrl = cleanServerUrl,
            apiKey = cleanApiKey,
            syncIntervalMinutes = validInterval,
            isEnrolled = true
        )

        val saved = configRepository.saveConfig(newConfig)
        return if (saved) {
            Result.Success
        } else {
            Result.StorageError("No fue posible guardar la configuración cifrada en el dispositivo.")
        }
    }
}
