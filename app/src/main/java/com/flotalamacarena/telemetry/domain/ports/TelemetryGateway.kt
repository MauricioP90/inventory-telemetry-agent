package com.flotalamacarena.telemetry.domain.ports

import com.flotalamacarena.telemetry.domain.models.HeartbeatResult
import com.flotalamacarena.telemetry.domain.models.TelemetryHeartbeat

/**
 * Puerto de comunicación hacia el backend receptor de telemetría (inventory-node).
 */
interface TelemetryGateway {
    suspend fun sendHeartbeat(
        heartbeat: TelemetryHeartbeat,
        serverUrl: String,
        apiKey: String
    ): HeartbeatResult
}
