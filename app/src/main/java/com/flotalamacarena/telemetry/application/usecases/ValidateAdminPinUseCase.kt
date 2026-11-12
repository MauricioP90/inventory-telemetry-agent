package com.flotalamacarena.telemetry.application.usecases

import com.flotalamacarena.telemetry.domain.models.AdminAuthResult
import com.flotalamacarena.telemetry.domain.ports.ConfigRepository
import com.flotalamacarena.telemetry.domain.ports.TelemetryGateway
import java.security.MessageDigest

/**
 * Caso de uso para autenticar al Administrador de TI:
 * 1. Prioridad: Consulta remota centralizada en el backend (POST /api/telemetry/admin-auth).
 * 2. Fallback resiliente: Si no hay red o el servidor está inalcanzable, valida mediante
 *    hashing local SHA-256 + Salt corporativo.
 * 3. Protección activa anti-fuerza bruta (Rate Limiting de 3 intentos / 300 segundos).
 * 
 * Capa Application: 100% Kotlin puro sin dependencias de android.*.
 */
class ValidateAdminPinUseCase(
    private val configRepository: ConfigRepository? = null,
    private val telemetryGateway: TelemetryGateway? = null,
    private val salt: String = DEFAULT_SALT,
    private val expectedHash: String = DEFAULT_HASHED_PASSWORD
) {

    sealed class AuthResult {
        object Success : AuthResult()
        object InvalidCredentials : AuthResult()
        data class LockedOut(val remainingSeconds: Long) : AuthResult()
    }

    private var failedAttempts = 0
    private var lockoutUntilTimestamp = 0L

    suspend fun execute(
        enteredSecret: String,
        currentTimeMillis: Long = System.currentTimeMillis()
    ): AuthResult {
        // Verificar si se encuentra bloqueado por exceder intentos
        if (currentTimeMillis < lockoutUntilTimestamp) {
            val remainingSec = (lockoutUntilTimestamp - currentTimeMillis) / 1000L
            return AuthResult.LockedOut(remainingSec)
        }

        if (enteredSecret.isBlank()) {
            return registerFailedAttempt(currentTimeMillis)
        }

        // Intento 1: Validación remota contra el backend (tanto enrolado como con endpoint por defecto)
        if (configRepository != null && telemetryGateway != null) {
            val config = configRepository.getConfig()
            val serverUrl = if (config.serverUrl.isNotBlank() && config.serverUrl != "http://10.0.2.2:3000") {
                config.serverUrl
            } else {
                com.flotalamacarena.telemetry.domain.models.DeviceConfig.DEFAULT_SERVER_URL
            }
            val apiKey = if (config.apiKey.isNotBlank()) config.apiKey else com.flotalamacarena.telemetry.domain.models.DeviceConfig.DEFAULT_API_KEY
            val placa = if (config.placa.isNotBlank()) config.placa else "FLM-DEVICE"

            when (val remoteResult = telemetryGateway.verifyAdminPassword(
                serverUrl = serverUrl,
                apiKey = apiKey,
                placa = placa,
                password = enteredSecret.trim()
            )) {
                is AdminAuthResult.Authorized -> {
                    failedAttempts = 0
                    lockoutUntilTimestamp = 0L
                    return AuthResult.Success
                }
                is AdminAuthResult.Unauthorized -> {
                    // Si el servidor rechaza, se verifica si coincide con la clave maestra local de emergencia
                    if (validateLocalHashDirect(enteredSecret)) {
                        failedAttempts = 0
                        lockoutUntilTimestamp = 0L
                        return AuthResult.Success
                    }
                    return registerFailedAttempt(currentTimeMillis)
                }
                is AdminAuthResult.NetworkUnavailable -> {
                    // Fallback local: si el servidor está caído o sin red, recurre a la clave local
                    return validateLocalHash(enteredSecret, currentTimeMillis)
                }
            }
        }

        // Intento 2 (Fallback si no hay pasarela inyectada): Validación con Hash SHA-256 local
        return validateLocalHash(enteredSecret, currentTimeMillis)
    }

    private fun validateLocalHashDirect(enteredSecret: String): Boolean {
        val calculatedHash = computeSha256(salt + enteredSecret.trim())
        return calculatedHash.equals(expectedHash, ignoreCase = true)
    }

    private fun validateLocalHash(enteredSecret: String, currentTimeMillis: Long): AuthResult {
        return if (validateLocalHashDirect(enteredSecret)) {
            failedAttempts = 0
            lockoutUntilTimestamp = 0L
            AuthResult.Success
        } else {
            registerFailedAttempt(currentTimeMillis)
        }
    }

    private fun registerFailedAttempt(currentTimeMillis: Long): AuthResult {
        failedAttempts++
        if (failedAttempts >= MAX_ATTEMPTS) {
            lockoutUntilTimestamp = currentTimeMillis + (LOCKOUT_DURATION_MINUTES * 60 * 1000L)
            val remainingSec = LOCKOUT_DURATION_MINUTES * 60L
            return AuthResult.LockedOut(remainingSec)
        }
        return AuthResult.InvalidCredentials
    }

    fun resetLockout() {
        failedAttempts = 0
        lockoutUntilTimestamp = 0L
    }

    companion object {
        const val MAX_ATTEMPTS = 3
        const val LOCKOUT_DURATION_MINUTES = 5L
        private const val DEFAULT_SALT = "FLM_TELEMETRY_SALT_2026_MACARENA"

        // Hash SHA-256 de (DEFAULT_SALT + "FLM#Admin2026")
        const val DEFAULT_HASHED_PASSWORD = "85f7c912e02e64abe758c264adaa3140cfd57f18e20ec71ee3777c1b2770472c"

        fun computeSha256(input: String): String {
            val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
            return bytes.joinToString("") { "%02x".format(it) }
        }
    }
}
