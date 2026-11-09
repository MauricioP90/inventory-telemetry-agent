package com.flotalamacarena.telemetry

import android.app.Application
import android.util.Log
import com.flotalamacarena.telemetry.infrastructure.storage.SecureConfigRepository
import com.flotalamacarena.telemetry.infrastructure.work.TelemetryWorkScheduler

/**
 * Clase principal de aplicación del Agente Móvil de Telemetría.
 * Inicializa los módulos de bajo nivel y garantiza la continuidad del servicio en segundo plano.
 */
class TelemetryApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "Iniciando Agente de Telemetría Flota La Macarena...")

        // Si el equipo ya está enrolado, garantizar que el demonio WorkManager esté activo
        try {
            val configRepo = SecureConfigRepository(this)
            if (configRepo.isEnrolled()) {
                val config = configRepo.getConfig()
                val scheduler = TelemetryWorkScheduler(this)
                scheduler.schedulePeriodicTelemetry(config.syncIntervalMinutes)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error inicializando ciclo de telemetría en Application: ${e.message}")
        }
    }

    companion object {
        const val TAG = "TelemetryAgent"
    }
}
