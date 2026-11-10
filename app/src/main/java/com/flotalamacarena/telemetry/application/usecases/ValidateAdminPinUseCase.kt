package com.flotalamacarena.telemetry.application.usecases

import java.security.MessageDigest

/**
 * Caso de uso criptográfico para autenticar al Administrador de TI mediante
 * hashing SHA-256 con Salt corporativo y protección anti-fuerza bruta (Rate Limiting).
 * 
 * Capa Application: 100% Kotlin puro sin dependencias de android.*.
 */
class ValidateAdminPinUseCase(
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

    fun execute(enteredSecret: String, currentTimeMillis: Long = System.currentTimeMillis()): AuthResult {
        // Verificar si se encuentra bloqueado por exceder intentos
        if (currentTimeMillis < lockoutUntilTimestamp) {
            val remainingSec = (lockoutUntilTimestamp - currentTimeMillis) / 1000L
            return AuthResult.LockedOut(remainingSec)
        }

        if (enteredSecret.isBlank()) {
            return registerFailedAttempt(currentTimeMillis)
        }

        val calculatedHash = computeSha256(salt + enteredSecret.trim())
        return if (calculatedHash.equals(expectedHash, ignoreCase = true)) {
            // Éxito: Reiniciar contador de intentos fallidos
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
