package com.flotalamacarena.telemetry.application

import com.flotalamacarena.telemetry.application.usecases.SaveDeviceConfigUseCase
import com.flotalamacarena.telemetry.domain.models.DeviceConfig
import com.flotalamacarena.telemetry.domain.ports.ConfigRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SaveDeviceConfigUseCaseTest {

    private lateinit var useCase: SaveDeviceConfigUseCase
    private lateinit var fakeRepository: FakeConfigRepository

    @Before
    fun setUp() {
        fakeRepository = FakeConfigRepository()
        useCase = SaveDeviceConfigUseCase(fakeRepository)
    }

    @Test
    fun `guardar configuracion con datos validos debe ser exitoso y transformar placa a mayusculas`() {
        val result = useCase.execute(
            placa = "pos-bog-01",
            serverUrl = "http://192.168.1.100:3000/",
            apiKey = "test-secret-key-123",
            intervalMinutes = 15
        )

        assertTrue(result is SaveDeviceConfigUseCase.Result.Success)
        val saved = fakeRepository.getConfig()
        assertEquals("POS-BOG-01", saved.placa)
        assertEquals("http://192.168.1.100:3000", saved.serverUrl)
        assertEquals("test-secret-key-123", saved.apiKey)
        assertTrue(saved.isEnrolled)
    }

    @Test
    fun `guardar con placa vacia debe retornar error de validacion`() {
        val result = useCase.execute(
            placa = "   ",
            serverUrl = "http://192.168.1.100:3000",
            apiKey = "key"
        )

        assertTrue(result is SaveDeviceConfigUseCase.Result.ValidationError)
        val error = result as SaveDeviceConfigUseCase.Result.ValidationError
        assertEquals("placa", error.field)
    }

    @Test
    fun `guardar con url sin protocolo http o https debe retornar error de validacion`() {
        val result = useCase.execute(
            placa = "POS-01",
            serverUrl = "ftp://192.168.1.100",
            apiKey = "key"
        )

        assertTrue(result is SaveDeviceConfigUseCase.Result.ValidationError)
        val error = result as SaveDeviceConfigUseCase.Result.ValidationError
        assertEquals("serverUrl", error.field)
    }

    // Fake in-memory repository for unit testing
    class FakeConfigRepository : ConfigRepository {
        private var config = DeviceConfig()
        private var lastCoords: Pair<Double, Double>? = null

        override fun getConfig(): DeviceConfig = config
        override fun saveConfig(newConfig: DeviceConfig): Boolean {
            config = newConfig
            return true
        }
        override fun isEnrolled(): Boolean = config.isEnrolled
        override fun clearConfig(): Boolean {
            config = DeviceConfig()
            return true
        }
        override fun saveLastKnownLocation(lat: Double, lng: Double, timestamp: Long) {
            lastCoords = Pair(lat, lng)
        }
        override fun getLastKnownCoordinates(): Pair<Double, Double>? = lastCoords
    }
}
