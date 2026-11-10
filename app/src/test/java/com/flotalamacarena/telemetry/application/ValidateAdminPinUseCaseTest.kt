package com.flotalamacarena.telemetry.application

import com.flotalamacarena.telemetry.application.usecases.ValidateAdminPinUseCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ValidateAdminPinUseCaseTest {

    private lateinit var useCase: ValidateAdminPinUseCase

    @Before
    fun setUp() {
        useCase = ValidateAdminPinUseCase()
    }

    @Test
    fun `cuando la clave es FLM#Admin2026 debe retornar Success`() {
        val result = useCase.execute("FLM#Admin2026")
        assertTrue(result is ValidateAdminPinUseCase.AuthResult.Success)

        val resultWithSpaces = useCase.execute("  FLM#Admin2026  ")
        assertTrue(resultWithSpaces is ValidateAdminPinUseCase.AuthResult.Success)
    }

    @Test
    fun `cuando la clave es incorrecta debe retornar InvalidCredentials`() {
        val result = useCase.execute("2026")
        assertTrue(result is ValidateAdminPinUseCase.AuthResult.InvalidCredentials)

        val resultEmpty = useCase.execute("")
        assertTrue(resultEmpty is ValidateAdminPinUseCase.AuthResult.InvalidCredentials)
    }

    @Test
    fun `cuando se superan 3 intentos fallidos debe bloquear por 5 minutos`() {
        var baseTime = 1000000L

        // Intento 1
        val r1 = useCase.execute("wrong1", baseTime)
        assertTrue(r1 is ValidateAdminPinUseCase.AuthResult.InvalidCredentials)

        // Intento 2
        val r2 = useCase.execute("wrong2", baseTime + 1000)
        assertTrue(r2 is ValidateAdminPinUseCase.AuthResult.InvalidCredentials)

        // Intento 3: Debe disparar el bloqueo
        val r3 = useCase.execute("wrong3", baseTime + 2000)
        assertTrue(r3 is ValidateAdminPinUseCase.AuthResult.LockedOut)
        assertEquals(300L, (r3 as ValidateAdminPinUseCase.AuthResult.LockedOut).remainingSeconds)

        // Intento 4 incluso con la clave correcta durante el bloqueo: Debe permanecer bloqueado
        val r4 = useCase.execute("FLM#Admin2026", baseTime + 3000)
        assertTrue(r4 is ValidateAdminPinUseCase.AuthResult.LockedOut)

        // Tras pasar 5 minutos y 1 segundo: Debe permitir el acceso nuevamente
        val afterLockoutTime = baseTime + 2000 + (5 * 60 * 1000L) + 1000
        val r5 = useCase.execute("FLM#Admin2026", afterLockoutTime)
        assertTrue(r5 is ValidateAdminPinUseCase.AuthResult.Success)
    }
}
