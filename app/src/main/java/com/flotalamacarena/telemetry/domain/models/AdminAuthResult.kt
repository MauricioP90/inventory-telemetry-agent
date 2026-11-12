package com.flotalamacarena.telemetry.domain.models

/**
 * Resultado de la verificación remota de credenciales administrativas en inventory-node.
 */
sealed class AdminAuthResult {
    object Authorized : AdminAuthResult()
    data class Unauthorized(val message: String) : AdminAuthResult()
    data class NetworkUnavailable(val reason: String) : AdminAuthResult()
}

