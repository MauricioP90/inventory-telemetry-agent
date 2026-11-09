package com.flotalamacarena.telemetry.infrastructure.work

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * Gestor central de programación de tareas en segundo plano mediante WorkManager.
 */
class TelemetryWorkScheduler(private val context: Context) {

    private val workManager: WorkManager by lazy {
        WorkManager.getInstance(context)
    }

    /**
     * Programa el ciclo recurrente de latidos respetando la restricción de red activa.
     */
    fun schedulePeriodicTelemetry(intervalMinutes: Int) {
        val safeInterval = intervalMinutes.coerceAtLeast(MIN_WORKMANAGER_INTERVAL_MINUTES).toLong()

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val periodicWorkRequest = PeriodicWorkRequestBuilder<TelemetryWorker>(
            safeInterval, TimeUnit.MINUTES,
            5, TimeUnit.MINUTES // Flex interval para permitir agrupamiento eficiente de energía
        )
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()

        workManager.enqueueUniquePeriodicWork(
            TelemetryWorker.WORK_NAME_PERIODIC,
            ExistingPeriodicWorkPolicy.UPDATE,
            periodicWorkRequest
        )

        Log.i(TAG, "⏰ Telemetría periódica programada cada $safeInterval minutos (con restricción de conexión activa).")
    }

    /**
     * Despacha un latido inmediato (usado al encender el equipo o al tocar "Sincronizar Ahora").
     */
    fun scheduleImmediateTelemetry() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val oneTimeRequest = OneTimeWorkRequestBuilder<TelemetryWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
            .build()

        workManager.enqueueUniqueWork(
            TelemetryWorker.WORK_NAME_ONETIME,
            ExistingWorkPolicy.REPLACE,
            oneTimeRequest
        )

        Log.i(TAG, "⚡ Latido inmediato encolado para ejecución rápida.")
    }

    /**
     * Detiene todas las tareas programadas (útil al desenrolar el equipo).
     */
    fun cancelAllTelemetry() {
        workManager.cancelUniqueWork(TelemetryWorker.WORK_NAME_PERIODIC)
        workManager.cancelUniqueWork(TelemetryWorker.WORK_NAME_ONETIME)
        Log.i(TAG, "🛑 Tareas de telemetría canceladas.")
    }

    companion object {
        private const val TAG = "TelemetryWorkScheduler"
        private const val MIN_WORKMANAGER_INTERVAL_MINUTES = 15
    }
}
