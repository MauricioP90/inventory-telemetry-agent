package com.flotalamacarena.telemetry.domain.models

/**
 * Resultado estructurado del intento de envío de latido de telemetría.
 */
sealed class HeartbeatResult {
    data class Success(
        val message: String,
        val statusCode: Int = 200,
        val rawResponse: String = ""
    ) : HeartbeatResult()

    data class Error(
        val reason: String,
        val statusCode: Int? = null,
        val canRetry: Boolean = true,
        val isNetworkError: Boolean = false
    ) : HeartbeatResult()

    data class Skipped(
        val reason: String
    ) : HeartbeatResult()
}
