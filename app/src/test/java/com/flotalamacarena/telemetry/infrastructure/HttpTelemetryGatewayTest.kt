package com.flotalamacarena.telemetry.infrastructure

import com.flotalamacarena.telemetry.domain.models.TelemetryHeartbeat
import com.flotalamacarena.telemetry.infrastructure.network.DeviceIpResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HttpTelemetryGatewayTest {

    @Test
    fun `resolucion de IP debe retornar una direccion no nula`() {
        val resolver = DeviceIpResolver()
        val ip = resolver.resolveIpAddress()
        assertNotNull(ip)
        assertTrue(ip.isNotBlank())
    }

    @Test
    fun `construccion de payload de latido debe ser consistente`() {
        val heartbeat = TelemetryHeartbeat(
            placa = "POS-BOG-01",
            latitud = 4.653421,
            longitud = -74.083712,
            ipAddress = "192.168.1.50"
        )

        assertEquals("POS-BOG-01", heartbeat.placa)
        assertEquals(4.653421, heartbeat.latitud, 0.000001)
        assertEquals(-74.083712, heartbeat.longitud, 0.000001)
        assertEquals("192.168.1.50", heartbeat.ipAddress)
    }
}
