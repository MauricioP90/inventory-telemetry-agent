package com.flotalamacarena.telemetry.application.usecases

import com.flotalamacarena.telemetry.domain.models.HeartbeatResult
import com.flotalamacarena.telemetry.domain.models.LocationPoint
import com.flotalamacarena.telemetry.domain.models.TelemetryHeartbeat
import com.flotalamacarena.telemetry.domain.ports.ConfigRepository
import com.flotalamacarena.telemetry.domain.ports.IpResolver
import com.flotalamacarena.telemetry.domain.ports.LocationProvider
import com.flotalamacarena.telemetry.domain.ports.TelemetryGateway

/**
 * Caso de uso principal para orquestar la emisión del latido de telemetría:
 * 1. Verifica si el equipo está enrolado con placa y credenciales válidas.
 * 2. Captura coordenadas satelitales / red (o recurre a última ubicación conocida).
 * 3. Resuelve la IP local.
 * 4. Construye el payload exacto para TelemetryController.ts.
 * 5. Envía la solicitud HTTP a inventory-node con cabecera X-Device-Api-Key.
 */
class SendHeartbeatUseCase(
    private val configRepository: ConfigRepository,
    private val locationProvider: LocationProvider,
    private val telemetryGateway: TelemetryGateway,
    private val ipResolver: IpResolver
) {

    suspend fun execute(): HeartbeatResult {
        val config = configRepository.getConfig()
        if (!config.isEnrolled || !config.isValid) {
            return HeartbeatResult.Skipped("Dispositivo no enrolado o configuración incompleta.")
        }

        // 1. Detección Activa de Sabotaje (Anti-Tampering)
        val hasPermission = locationProvider.hasLocationPermission()
        val isGpsEnabled = locationProvider.isLocationEnabled()

        var tamperingAlert: String? = when {
            !hasPermission -> "PERMISOS_UBICACION_REVOCADOS"
            !isGpsEnabled -> "GPS_DESACTIVADO"
            else -> null
        }

        // Si los permisos fueron revocados o el GPS está apagado, emite reporte inmediato con la última ubicación
        if (tamperingAlert != null) {
            val lastCoords = configRepository.getLastKnownCoordinates()
            val ipAddress = ipResolver.resolveIpAddress()

            val alertHeartbeat = TelemetryHeartbeat(
                placa = config.placa,
                latitud = lastCoords?.first ?: 0.0,
                longitud = lastCoords?.second ?: 0.0,
                ipAddress = ipAddress,
                timestamp = System.currentTimeMillis(),
                tamperingAlert = tamperingAlert
            )

            return telemetryGateway.sendHeartbeat(
                heartbeat = alertHeartbeat,
                serverUrl = config.serverUrl,
                apiKey = config.apiKey
            )
        }

        // 2. Obtener ubicación actual con timeout o recurrir a la última registrada
        val location: LocationPoint? = locationProvider.getCurrentLocation(timeoutMillis = 15000L)
            ?: locationProvider.getLastKnownLocation()
            ?: run {
                val lastCoords = configRepository.getLastKnownCoordinates()
                if (lastCoords != null) {
                    LocationPoint(
                        latitude = lastCoords.first,
                        longitude = lastCoords.second,
                        provider = "cache"
                    )
                } else null
            }

        if (location == null || !location.isValidCoordinates) {
            return HeartbeatResult.Error(
                reason = "No se pudo obtener coordenadas válidas de GPS ni Red.",
                canRetry = true,
                isNetworkError = false
            )
        }

        // 3. Detección de Suplantación de Coordenadas (Mock GPS)
        if (location.isMock) {
            tamperingAlert = "MOCK_LOCATION_DETECTED"
        }

        val ipAddress = ipResolver.resolveIpAddress()

        val heartbeat = TelemetryHeartbeat(
            placa = config.placa,
            latitud = location.latitude,
            longitud = location.longitude,
            ipAddress = ipAddress,
            timestamp = System.currentTimeMillis(),
            tamperingAlert = tamperingAlert
        )

        val result = telemetryGateway.sendHeartbeat(
            heartbeat = heartbeat,
            serverUrl = config.serverUrl,
            apiKey = config.apiKey
        )

        if (result is HeartbeatResult.Success) {
            configRepository.saveLastKnownLocation(
                lat = location.latitude,
                lng = location.longitude,
                timestamp = heartbeat.timestamp
            )
        }

        return result
    }
}
