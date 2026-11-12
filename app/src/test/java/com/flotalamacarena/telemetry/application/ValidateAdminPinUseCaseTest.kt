package com.flotalamacarena.telemetry.application

import com.flotalamacarena.telemetry.application.usecases.ValidateAdminPinUseCase
import com.flotalamacarena.telemetry.domain.models.AdminAuthResult
import com.flotalamacarena.telemetry.domain.models.DeviceConfig
import com.flotalamacarena.telemetry.domain.models.HeartbeatResult
import com.flotalamacarena.telemetry.domain.models.TelemetryHeartbeat
import com.flotalamacarena.telemetry.domain.ports.ConfigRepository
import com.flotalamacarena.telemetry.domain.ports.TelemetryGateway
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ValidateAdminPinUseCaseTest {

    private lateinit var useCase: ValidateAdminPinUseCase
    private lateinit var fakeRepo: FakeConfigRepository
    private lateinit var fakeGateway: FakeTelemetryGateway

    @Before
    fun setUp() {
        fakeRepo = FakeConfigRepository()
        fakeGateway = FakeTelemetryGateway()
        useCase = ValidateAdminPinUseCase(
            configRepository = fakeRepo,
            telemetryGateway = fakeGateway
        )
    }

    @Test
    fun `cuando la clave es FLM#Admin2026 sin conexion debe retornar Success por fallback local`() = runBlocking {
        fakeRepo.setConfigData(DeviceConfig(isEnrolled = false)) // Sin enrolar
        val result = useCase.execute("FLM#Admin2026")
        assertTrue(result is ValidateAdminPinUseCase.AuthResult.Success)

        val resultWithSpaces = useCase.execute("  FLM#Admin2026  ")
        assertTrue(resultWithSpaces is ValidateAdminPinUseCase.AuthResult.Success)
    }

    @Test
    fun `cuando la clave es incorrecta debe retornar InvalidCredentials`() = runBlocking {
        fakeRepo.setConfigData(DeviceConfig(isEnrolled = false))
        val result = useCase.execute("2026")
        assertTrue(result is ValidateAdminPinUseCase.AuthResult.InvalidCredentials)

        val resultEmpty = useCase.execute("")
        assertTrue(resultEmpty is ValidateAdminPinUseCase.AuthResult.InvalidCredentials)
    }

    @Test
    fun `cuando el backend remoto autoriza 200 OK debe retornar Success`() = runBlocking {
        fakeRepo.setConfigData(
            DeviceConfig(
                placa = "POS-001",
                serverUrl = "http://localhost:3000",
                apiKey = "key123",
                isEnrolled = true
            )
        )
        fakeGateway.authResponseToReturn = AdminAuthResult.Authorized

        val result = useCase.execute("NuevaClaveRemota2027")
        assertTrue(result is ValidateAdminPinUseCase.AuthResult.Success)
        assertEquals("NuevaClaveRemota2027", fakeGateway.lastPasswordChecked)
    }

    @Test
    fun `cuando el backend remoto rechaza 401 Unauthorized debe retornar InvalidCredentials`() = runBlocking {
        fakeRepo.setConfigData(
            DeviceConfig(
                placa = "POS-001",
                serverUrl = "http://localhost:3000",
                apiKey = "key123",
                isEnrolled = true
            )
        )
        fakeGateway.authResponseToReturn = AdminAuthResult.Unauthorized("Clave invalida")

        val result = useCase.execute("clave-erronea")
        assertTrue(result is ValidateAdminPinUseCase.AuthResult.InvalidCredentials)
    }

    @Test
    fun `cuando el backend esta caido o sin red debe hacer fallback a clave local`() = runBlocking {
        fakeRepo.setConfigData(
            DeviceConfig(
                placa = "POS-001",
                serverUrl = "http://localhost:3000",
                apiKey = "key123",
                isEnrolled = true
            )
        )
        fakeGateway.authResponseToReturn = AdminAuthResult.NetworkUnavailable("Connection timed out")

        // Con clave local por defecto FLM#Admin2026 debe permitir acceso por fallback
        val result = useCase.execute("FLM#Admin2026")
        assertTrue(result is ValidateAdminPinUseCase.AuthResult.Success)
    }

    @Test
    fun `cuando se superan 3 intentos fallidos debe bloquear por 5 minutos`() = runBlocking {
        fakeRepo.setConfigData(DeviceConfig(isEnrolled = false))
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

    class FakeConfigRepository : ConfigRepository {
        private var storedConfig = DeviceConfig()

        fun setConfigData(config: DeviceConfig) {
            storedConfig = config
        }

        override fun getConfig(): DeviceConfig = storedConfig
        override fun saveConfig(newConfig: DeviceConfig): Boolean = true
        override fun isEnrolled(): Boolean = storedConfig.isEnrolled
        override fun clearConfig(): Boolean = true
        override fun saveLastKnownLocation(lat: Double, lng: Double, timestamp: Long) {}
        override fun getLastKnownCoordinates(): Pair<Double, Double>? = null
    }

    class FakeTelemetryGateway : TelemetryGateway {
        var authResponseToReturn: AdminAuthResult? = null
        var lastPasswordChecked: String? = null

        override suspend fun sendHeartbeat(
            heartbeat: TelemetryHeartbeat,
            serverUrl: String,
            apiKey: String
        ): HeartbeatResult = HeartbeatResult.Success("OK", 201)

        override suspend fun verifyAdminPassword(
            serverUrl: String,
            apiKey: String,
            placa: String,
            password: String
        ): AdminAuthResult {
            lastPasswordChecked = password
            return authResponseToReturn ?: if (password == "NuevaClaveRemota2027") {
                AdminAuthResult.Authorized
            } else {
                AdminAuthResult.Unauthorized("Credencial invalida")
            }
        }
    }
}
