package com.flotalamacarena.telemetry

import android.app.Application
import android.util.Log

/**
 * Clase principal de aplicación del Agente Móvil de Telemetría.
 * Inicializa los módulos de bajo nivel y garantiza la inicialización silenciosa.
 */
class TelemetryApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "Iniciando Agente de Telemetría Flota La Macarena...")
    }

    companion object {
        const val TAG = "TelemetryAgent"
    }
}
