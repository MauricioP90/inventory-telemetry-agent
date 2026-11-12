package com.flotalamacarena.telemetry.application

import com.flotalamacarena.telemetry.application.usecases.SendHeartbeatUseCase
import com.flotalamacarena.telemetry.domain.models.DeviceConfig
import com.flotalamacarena.telemetry.domain.models.HeartbeatResult
import com.flotalamacarena.telemetry.domain.models.LocationPoint
import com.flotalamacarena.telemetry.domain.models.TelemetryHeartbeat
import com.flotalamacarena.telemetry.domain.ports.ConfigRepository
import com.flotalamacarena.telemetry.domain.ports.IpResolver
import com.flotalamacarena.telemetry.domain.ports.LocationProvider
import com.flotalamacarena.telemetry.domain.ports.TelemetryGateway
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SendHeartbeatUseCaseTest {

    private lateinit var useCase: SendHeartbeatUseCase
    private lateinit var fakeConfigRepo: FakeConfigRepository
    private lateinit var fakeLocationProvider: FakeLocationProvider
    private lateinit var fakeGateway: FakeTelemetryGateway
    private lateinit var fakeIpResolver: FakeIpResolver

    @Before
    fun setUp() {
        fakeConfigRepo = FakeConfigRepository()
        fakeLocationProvider = FakeLocationProvider()
        fakeGateway = FakeTelemetryGateway()
        fakeIpResolver = FakeIpResolver()

        useCase = SendHeartbeatUseCase(
            configRepository = fakeConfigRepo,
            locationProvider = fakeLocationProvider,
            telemetryGateway = fakeGateway,
            ipResolver = fakeIpResolver
        )
    }

    @Test
    fun `dispositivo no enrolado debe omitir envio silenciosamente`() = runBlocking {
        fakeConfigRepo.setConfigData(DeviceConfig(isEnrolled = false))

        val result = useCase.execute()

        assertTrue(result is HeartbeatResult.Skipped)
        assertEquals(0, fakeGateway.sentHeartbeats.size)
    }

    @Test
    fun `sin coordenadas validas debe retornar error reintentable`() = runBlocking {
        fakeConfigRepo.setConfigData(
            DeviceConfig(
                placa = "POS-001",
                serverUrl = "http://localhost:3000",
                apiKey = "key123",
                isEnrolled = true
            )
        )
        fakeLocationProvider.currentLocationPoint = null
        fakeLocationProvider.lastKnownLocationPoint = null

        val result = useCase.execute()

        assertTrue(result is HeartbeatResult.Error)
        val error = result as HeartbeatResult.Error
        assertTrue(error.canRetry)
        assertEquals(0, fakeGateway.sentHeartbeats.size)
    }

    @Test
    fun `dispositivo enrolado con coordenadas validas debe transmitir latido exitosamente`() = runBlocking {
        fakeConfigRepo.setConfigData(
            DeviceConfig(
                placa = "POS-BOG-01",
                serverUrl = "http://192.168.1.50:3000",
                apiKey = "secret-m2m-key",
                isEnrolled = true
            )
        )
        fakeLocationProvider.currentLocationPoint = LocationPoint(
            latitude = 4.70945,
            longitude = -74.06176,
            provider = "gps"
        )
        fakeIpResolver.ipToReturn = "192.168.1.155"

        val result = useCase.execute()

        assertTrue(result is HeartbeatResult.Success)
        assertEquals(1, fakeGateway.sentHeartbeats.size)
        val sent = fakeGateway.sentHeartbeats.first()
        assertEquals("POS-BOG-01", sent.placa)
        assertEquals(4.70945, sent.latitud, 0.0001)
        assertEquals(-74.06176, sent.longitud, 0.0001)
        assertEquals("192.168.1.155", sent.ipAddress)

        // Verifica que se guardaron las últimas coordenadas conocidas
        val lastCoords = fakeConfigRepo.getLastKnownCoordinates()
        assertEquals(4.70945, lastCoords?.first ?: 0.0, 0.0001)
        assertEquals(-74.06176, lastCoords?.second ?: 0.0, 0.0001)
    }

    @Test
    fun `cuando permisos de ubicacion son revocados debe emitir latido de alerta de sabotaje`() = runBlocking {
        fakeConfigRepo.setConfigData(
            DeviceConfig(
                placa = "POS-BOG-01",
                serverUrl = "http://192.168.1.50:3000",
                apiKey = "secret-m2m-key",
                isEnrolled = true
            )
        )
        fakeConfigRepo.saveLastKnownLocation(4.70945, -74.06176, 12345L)
        fakeLocationProvider.permissionGranted = false // Permiso revocado por el usuario

        val result = useCase.execute()

        assertTrue(result is HeartbeatResult.Success)
        assertEquals(1, fakeGateway.sentHeartbeats.size)
        val sent = fakeGateway.sentHeartbeats.first()
        assertEquals("POS-BOG-01", sent.placa)
        assertEquals("PERMISOS_UBICACION_REVOCADOS", sent.tamperingAlert)
        assertEquals(4.70945, sent.latitud, 0.0001)
        assertEquals(-74.06176, sent.longitud, 0.0001)
    }

    @Test
    fun `cuando gps esta apagado debe emitir latido de alerta GPS_DESACTIVADO`() = runBlocking {
        fakeConfigRepo.setConfigData(
            DeviceConfig(
                placa = "POS-BOG-01",
                serverUrl = "http://192.168.1.50:3000",
                apiKey = "secret-m2m-key",
                isEnrolled = true
            )
        )
        fakeConfigRepo.saveLastKnownLocation(4.70945, -74.06176, 12345L)
        fakeLocationProvider.permissionGranted = true
        fakeLocationProvider.isGpsActive = false // GPS apagado manualmente

        val result = useCase.execute()

        assertTrue(result is HeartbeatResult.Success)
        assertEquals(1, fakeGateway.sentHeartbeats.size)
        val sent = fakeGateway.sentHeartbeats.first()
        assertEquals("POS-BOG-01", sent.placa)
        assertEquals("GPS_DESACTIVADO", sent.tamperingAlert)
        assertEquals(4.70945, sent.latitud, 0.0001)
        assertEquals(-74.06176, sent.longitud, 0.0001)
    }

    @Test
    fun `cuando ubicacion es simulada debe emitir latido de alerta MOCK_LOCATION_DETECTED`() = runBlocking {
        fakeConfigRepo.setConfigData(
            DeviceConfig(
                placa = "POS-BOG-01",
                serverUrl = "http://192.168.1.50:3000",
                apiKey = "secret-m2m-key",
                isEnrolled = true
            )
        )
        fakeLocationProvider.permissionGranted = true
        fakeLocationProvider.isGpsActive = true
        fakeLocationProvider.currentLocationPoint = LocationPoint(
            latitude = 4.1500,
            longitude = -73.6300,
            accuracyMeters = 5.0f,
            provider = "gps",
            isMock = true
        )

        val result = useCase.execute()

        assertTrue(result is HeartbeatResult.Success)
        assertEquals(1, fakeGateway.sentHeartbeats.size)
        val sent = fakeGateway.sentHeartbeats.first()
        assertEquals("POS-BOG-01", sent.placa)
        assertEquals("MOCK_LOCATION_DETECTED", sent.tamperingAlert)
        assertEquals(4.1500, sent.latitud, 0.0001)
        assertEquals(-73.6300, sent.longitud, 0.0001)
    }

    // Fakes
    class FakeConfigRepository : ConfigRepository {
        private var currentConfig = DeviceConfig()
        private var lastCoords: Pair<Double, Double>? = null

        fun setConfigData(config: DeviceConfig) {
            currentConfig = config
        }

        override fun getConfig(): DeviceConfig = currentConfig
        override fun saveConfig(newConfig: DeviceConfig): Boolean {
            currentConfig = newConfig
            return true
        }
        override fun isEnrolled(): Boolean = currentConfig.isEnrolled
        override fun clearConfig(): Boolean {
            currentConfig = DeviceConfig()
            return true
        }
        override fun saveLastKnownLocation(lat: Double, lng: Double, timestamp: Long) {
            lastCoords = Pair(lat, lng)
        }
        override fun getLastKnownCoordinates(): Pair<Double, Double>? = lastCoords
    }

    class FakeLocationProvider : LocationProvider {
        var currentLocationPoint: LocationPoint? = null
        var lastKnownLocationPoint: LocationPoint? = null
        var isGpsActive: Boolean = true
        var permissionGranted: Boolean = true

        override suspend fun getCurrentLocation(timeoutMillis: Long): LocationPoint? = currentLocationPoint
        override fun getLastKnownLocation(): LocationPoint? = lastKnownLocationPoint
        override fun isLocationEnabled(): Boolean = isGpsActive
        override fun hasLocationPermission(): Boolean = permissionGranted
    }

    class FakeTelemetryGateway : TelemetryGateway {
        val sentHeartbeats = mutableListOf<TelemetryHeartbeat>()

        override suspend fun sendHeartbeat(
            heartbeat: TelemetryHeartbeat,
            serverUrl: String,
            apiKey: String
        ): HeartbeatResult {
            sentHeartbeats.add(heartbeat)
            return HeartbeatResult.Success("OK", 201)
        }

        override suspend fun verifyAdminPassword(
            serverUrl: String,
            apiKey: String,
            placa: String,
            password: String
        ): com.flotalamacarena.telemetry.domain.models.AdminAuthResult {
            return com.flotalamacarena.telemetry.domain.models.AdminAuthResult.Authorized
        }
    }

    class FakeIpResolver : IpResolver {
        var ipToReturn = "127.0.0.1"
        override fun resolveIpAddress(): String = ipToReturn
    }
}
