package com.flotalamacarena.telemetry.application

import com.flotalamacarena.telemetry.application.usecases.ValidateAdminPinUseCase
import org.junit.Assert.assertFalse
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
    fun `cuando el PIN es 2026 debe retornar verdadero`() {
        assertTrue(useCase.execute("2026"))
        assertTrue(useCase.execute(" 2026 "))
    }

    @Test
    fun `cuando el PIN es incorrecto o vacio debe retornar falso`() {
        assertFalse(useCase.execute("1234"))
        assertFalse(useCase.execute("0000"))
        assertFalse(useCase.execute(""))
        assertFalse(useCase.execute("   "))
        assertFalse(useCase.execute("2025"))
        assertFalse(useCase.execute("admin"))
    }
}
