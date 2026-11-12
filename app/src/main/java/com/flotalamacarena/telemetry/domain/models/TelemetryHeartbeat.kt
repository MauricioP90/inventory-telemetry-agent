package com.flotalamacarena.telemetry.domain.models

/**
 * Carga útil (Payload) del latido de telemetría enviado a inventory-node.
 * Debe coincidir exactamente con el contrato esperado por TelemetryController.ts:
 * { "placa": String, "latitud": Double, "longitud": Double, "ipAddress": String }
 */
data class TelemetryHeartbeat(
    val placa: String,
    val latitud: Double,
    val longitud: Double,
    val ipAddress: String,
    val timestamp: Long = System.currentTimeMillis(),
    val tamperingAlert: String? = null
)
