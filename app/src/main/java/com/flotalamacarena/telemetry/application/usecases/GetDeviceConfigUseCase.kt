package com.flotalamacarena.telemetry.application.usecases

import com.flotalamacarena.telemetry.domain.models.DeviceConfig
import com.flotalamacarena.telemetry.domain.ports.ConfigRepository

/**
 * Caso de uso para obtener la configuración actual del dispositivo.
 */
class GetDeviceConfigUseCase(
    private val configRepository: ConfigRepository
) {
    fun execute(): DeviceConfig {
        return configRepository.getConfig()
    }

    fun isEnrolled(): Boolean {
        return configRepository.isEnrolled()
    }
}
