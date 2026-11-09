package com.flotalamacarena.telemetry.application.usecases

/**
 * Caso de uso para validar el PIN de seguridad de TI (2026) antes de permitir
 * cambios en la placa del activo, servidor receptor o API Key.
 */
class ValidateAdminPinUseCase {

    fun execute(enteredPin: String): Boolean {
        if (enteredPin.isBlank()) return false
        return enteredPin.trim() == TI_ADMIN_PIN
    }

    companion object {
        const val TI_ADMIN_PIN = "2026"
    }
}
