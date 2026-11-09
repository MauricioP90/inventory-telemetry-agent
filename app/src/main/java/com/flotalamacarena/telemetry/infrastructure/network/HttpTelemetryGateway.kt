package com.flotalamacarena.telemetry.infrastructure.network

import android.util.Log
import com.flotalamacarena.telemetry.domain.models.HeartbeatResult
import com.flotalamacarena.telemetry.domain.models.TelemetryHeartbeat
import com.flotalamacarena.telemetry.domain.ports.TelemetryGateway
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Cliente HTTP ultra-ligero basado en OkHttp para enviar el latido de telemetría hacia inventory-node.
 * Cumple estrictamente con el contrato esperado en TelemetryController.ts.
 */
class HttpTelemetryGateway(
    private val client: OkHttpClient = defaultClient()
) : TelemetryGateway {

    override suspend fun sendHeartbeat(
        heartbeat: TelemetryHeartbeat,
        serverUrl: String,
        apiKey: String
    ): HeartbeatResult = withContext(Dispatchers.IO) {
        val endpoint = buildEndpoint(serverUrl)

        val jsonBody = JSONObject().apply {
            put("placa", heartbeat.placa)
            put("latitud", heartbeat.latitud)
            put("longitud", heartbeat.longitud)
            put("ipAddress", heartbeat.ipAddress)
        }.toString()

        val mediaType = "application/json; charset=utf-8".toMediaType()
        val requestBody = jsonBody.toRequestBody(mediaType)

        val request = Request.Builder()
            .url(endpoint)
            .addHeader(HEADER_API_KEY, apiKey)
            .addHeader("Accept", "application/json")
            .post(requestBody)
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val responseBody = response.body?.string() ?: ""
                val statusCode = response.code

                if (response.isSuccessful) {
                    Log.i(TAG, "✅ Latido enviado exitosamente ($statusCode): $responseBody")
                    HeartbeatResult.Success(
                        message = "Latido recibido correctamente por el servidor ($statusCode).",
                        statusCode = statusCode,
                        rawResponse = responseBody
                    )
                } else {
                    val errorMessage = "El servidor rechazó el latido con código HTTP $statusCode: $responseBody"
                    Log.w(TAG, "⚠️ $errorMessage")
                    HeartbeatResult.Error(
                        reason = errorMessage,
                        statusCode = statusCode,
                        canRetry = statusCode in 500..599,
                        isNetworkError = false
                    )
                }
            }
        } catch (e: IOException) {
            val netError = "Fallo de conexión o servidor inalcanzable (${e.javaClass.simpleName}): ${e.message}"
            Log.w(TAG, "📡 $netError")
            HeartbeatResult.Error(
                reason = netError,
                statusCode = null,
                canRetry = true,
                isNetworkError = true
            )
        } catch (e: Exception) {
            val generalError = "Error inesperado al enviar latido: ${e.message}"
            Log.e(TAG, "❌ $generalError", e)
            HeartbeatResult.Error(
                reason = generalError,
                statusCode = null,
                canRetry = false,
                isNetworkError = false
            )
        }
    }

    private fun buildEndpoint(baseUrl: String): String {
        val cleanBase = baseUrl.trim().removeSuffix("/")
        return if (cleanBase.endsWith(ENDPOINT_PATH)) {
            cleanBase
        } else {
            "$cleanBase$ENDPOINT_PATH"
        }
    }

    companion object {
        private const val TAG = "HttpTelemetryGateway"
        private const val ENDPOINT_PATH = "/api/telemetry/heartbeat"
        private const val HEADER_API_KEY = "X-Device-Api-Key"

        fun defaultClient(): OkHttpClient {
            return OkHttpClient.Builder()
                .connectTimeout(12, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .writeTimeout(15, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build()
        }
    }
}
