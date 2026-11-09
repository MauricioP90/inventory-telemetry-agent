package com.flotalamacarena.telemetry.infrastructure.storage

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.flotalamacarena.telemetry.domain.models.DeviceConfig
import com.flotalamacarena.telemetry.domain.ports.ConfigRepository

/**
 * Implementación de ConfigRepository que almacena de forma segura las credenciales
 * (Placa, URL del Servidor, API Key) en hardware utilizando EncryptedSharedPreferences.
 * Cuenta con fallback resiliente para terminales POS con AOSP puro donde el Keystore
 * pueda presentar limitaciones de proveedor.
 */
class SecureConfigRepository(
    private val context: Context
) : ConfigRepository {

    private val prefs: SharedPreferences by lazy {
        initPreferences()
    }

    private fun initPreferences(): SharedPreferences {
        return try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            EncryptedSharedPreferences.create(
                context,
                ENCRYPTED_PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            Log.w(TAG, "Keystore no disponible o error al iniciar EncryptedSharedPreferences. Usando almacenamiento privado de respaldo: ${e.message}")
            context.getSharedPreferences(FALLBACK_PREFS_NAME, Context.MODE_PRIVATE)
        }
    }

    override fun getConfig(): DeviceConfig {
        val placa = prefs.getString(KEY_PLACA, "") ?: ""
        val serverUrl = prefs.getString(KEY_SERVER_URL, DeviceConfig.DEFAULT_SERVER_URL) ?: DeviceConfig.DEFAULT_SERVER_URL
        val apiKey = prefs.getString(KEY_API_KEY, "") ?: ""
        val interval = prefs.getInt(KEY_INTERVAL, DeviceConfig.DEFAULT_INTERVAL_MINUTES)
        val isEnrolled = prefs.getBoolean(KEY_IS_ENROLLED, false)

        return DeviceConfig(
            placa = placa,
            serverUrl = serverUrl,
            apiKey = apiKey,
            syncIntervalMinutes = interval,
            isEnrolled = isEnrolled && placa.isNotBlank() && apiKey.isNotBlank()
        )
    }

    override fun saveConfig(config: DeviceConfig): Boolean {
        return try {
            prefs.edit()
                .putString(KEY_PLACA, config.placa)
                .putString(KEY_SERVER_URL, config.serverUrl)
                .putString(KEY_API_KEY, config.apiKey)
                .putInt(KEY_INTERVAL, config.syncIntervalMinutes)
                .putBoolean(KEY_IS_ENROLLED, config.isEnrolled)
                .apply()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error al guardar configuración cifrada: ${e.message}", e)
            false
        }
    }

    override fun isEnrolled(): Boolean {
        val enrolled = prefs.getBoolean(KEY_IS_ENROLLED, false)
        val placa = prefs.getString(KEY_PLACA, "") ?: ""
        val apiKey = prefs.getString(KEY_API_KEY, "") ?: ""
        return enrolled && placa.isNotBlank() && apiKey.isNotBlank()
    }

    override fun clearConfig(): Boolean {
        return try {
            prefs.edit().clear().apply()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error al limpiar la configuración: ${e.message}", e)
            false
        }
    }

    override fun saveLastKnownLocation(lat: Double, lng: Double, timestamp: Long) {
        prefs.edit()
            .putString(KEY_LAST_LAT, lat.toString())
            .putString(KEY_LAST_LNG, lng.toString())
            .putLong(KEY_LAST_TIME, timestamp)
            .apply()
    }

    override fun getLastKnownCoordinates(): Pair<Double, Double>? {
        val latStr = prefs.getString(KEY_LAST_LAT, null)
        val lngStr = prefs.getString(KEY_LAST_LNG, null)
        if (latStr != null && lngStr != null) {
            val lat = latStr.toDoubleOrNull()
            val lng = lngStr.toDoubleOrNull()
            if (lat != null && lng != null) {
                return Pair(lat, lng)
            }
        }
        return null
    }

    companion object {
        private const val TAG = "SecureConfigRepo"
        private const val ENCRYPTED_PREFS_NAME = "macarena_telemetry_secure_prefs"
        private const val FALLBACK_PREFS_NAME = "macarena_telemetry_fallback_prefs"

        private const val KEY_PLACA = "pref_device_placa"
        private const val KEY_SERVER_URL = "pref_server_url"
        private const val KEY_API_KEY = "pref_api_key"
        private const val KEY_INTERVAL = "pref_sync_interval"
        private const val KEY_IS_ENROLLED = "pref_is_enrolled"
        private const val KEY_LAST_LAT = "pref_last_latitude"
        private const val KEY_LAST_LNG = "pref_last_longitude"
        private const val KEY_LAST_TIME = "pref_last_timestamp"
    }
}
