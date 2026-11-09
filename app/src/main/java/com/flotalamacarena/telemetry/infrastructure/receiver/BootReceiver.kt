package com.flotalamacarena.telemetry.infrastructure.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.flotalamacarena.telemetry.infrastructure.storage.SecureConfigRepository
import com.flotalamacarena.telemetry.infrastructure.work.TelemetryWorkScheduler

/**
 * Receptor de eventos de arranque del sistema operativo.
 * Se despierta automáticamente cuando el teléfono o la terminal POS (Ciontek CS30 / Topwise T6D)
 * termina de encender o reiniciarse, sin requerir que el cajero u operario abra la app.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        Log.i(TAG, "🔔 Evento de arranque del sistema detectado: $action")

        val validActions = listOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            "android.intent.action.QUICKBOOT_POWERON",
            "com.htc.intent.action.QUICKBOOT_POWERON"
        )

        if (action in validActions) {
            val configRepo = SecureConfigRepository(context.applicationContext)

            if (configRepo.isEnrolled()) {
                val config = configRepo.getConfig()
                Log.i(TAG, "🚀 Dispositivo enrolado con placa '${config.placa}'. Reactivando demonio de telemetría...")

                val scheduler = TelemetryWorkScheduler(context.applicationContext)
                // 1. Programar el ciclo recurrente periódico
                scheduler.schedulePeriodicTelemetry(config.syncIntervalMinutes)
                // 2. Despachar un latido inmediato de notificación de encendido
                scheduler.scheduleImmediateTelemetry()
            } else {
                Log.i(TAG, "ℹ️ Dispositivo no enrolado aún. No se programan tareas de telemetría al encender.")
            }
        }
    }

    companion object {
        private const val TAG = "BootReceiver"
    }
}
