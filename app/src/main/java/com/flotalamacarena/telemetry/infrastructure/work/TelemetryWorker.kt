package com.flotalamacarena.telemetry.infrastructure.work

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.flotalamacarena.telemetry.application.usecases.SendHeartbeatUseCase
import com.flotalamacarena.telemetry.domain.models.HeartbeatResult
import com.flotalamacarena.telemetry.infrastructure.location.HybridLocationProvider
import com.flotalamacarena.telemetry.infrastructure.network.DeviceIpResolver
import com.flotalamacarena.telemetry.infrastructure.network.HttpTelemetryGateway
import com.flotalamacarena.telemetry.infrastructure.storage.SecureConfigRepository

/**
 * Worker en segundo plano ejecutado por Android Jetpack WorkManager.
 * Sobrevive a reinicios, respeta el ahorro de batería (Doze Mode) y espera
 * automáticamente a que haya conexión a Internet (4G/Wi-Fi) antes de ejecutarse.
 */
class TelemetryWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        Log.i(TAG, "📡 Ejecutando ciclo de telemetría en segundo plano (Intento #${runAttemptCount + 1})...")

        val configRepo = SecureConfigRepository(applicationContext)
        val locationProvider = HybridLocationProvider(applicationContext)
        val gateway = HttpTelemetryGateway()
        val ipResolver = DeviceIpResolver()

        val sendHeartbeatUseCase = SendHeartbeatUseCase(
            configRepository = configRepo,
            locationProvider = locationProvider,
            telemetryGateway = gateway,
            ipResolver = ipResolver
        )

        return try {
            when (val result = sendHeartbeatUseCase.execute()) {
                is HeartbeatResult.Success -> {
                    Log.i(TAG, "✅ Latido enviado con éxito desde WorkManager: ${result.message}")
                    Result.success()
                }

                is HeartbeatResult.Skipped -> {
                    Log.i(TAG, "ℹ️ Ciclo omitido: ${result.reason}")
                    Result.success()
                }

                is HeartbeatResult.Error -> {
                    Log.w(TAG, "⚠️ Error en envío de latido: ${result.reason}")
                    if (result.canRetry && runAttemptCount < MAX_RETRIES) {
                        Log.i(TAG, "🔄 Reprogramando reintento con backoff exponencial...")
                        Result.retry()
                    } else {
                        Log.w(TAG, "🛑 Se alcanzó el límite de reintentos o el error es fatal. Cerrando ciclo silenciosamente.")
                        Result.failure()
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Excepción no controlada en TelemetryWorker: ${e.message}", e)
            if (runAttemptCount < MAX_RETRIES) Result.retry() else Result.failure()
        }
    }

    companion object {
        const val TAG = "TelemetryWorker"
        const val WORK_NAME_PERIODIC = "macarena_telemetry_periodic_work"
        const val WORK_NAME_ONETIME = "macarena_telemetry_onetime_work"
        private const val MAX_RETRIES = 3
    }
}
