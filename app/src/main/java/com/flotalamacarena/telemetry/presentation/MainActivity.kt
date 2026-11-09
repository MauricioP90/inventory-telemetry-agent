package com.flotalamacarena.telemetry.presentation

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.flotalamacarena.telemetry.R
import com.flotalamacarena.telemetry.application.usecases.GetDeviceConfigUseCase
import com.flotalamacarena.telemetry.application.usecases.SaveDeviceConfigUseCase
import com.flotalamacarena.telemetry.application.usecases.SendHeartbeatUseCase
import com.flotalamacarena.telemetry.application.usecases.ValidateAdminPinUseCase
import com.flotalamacarena.telemetry.databinding.ActivityMainBinding
import com.flotalamacarena.telemetry.databinding.DialogAdminConfigBinding
import com.flotalamacarena.telemetry.databinding.DialogPinAuthBinding
import com.flotalamacarena.telemetry.domain.models.HeartbeatResult
import com.flotalamacarena.telemetry.infrastructure.location.HybridLocationProvider
import com.flotalamacarena.telemetry.infrastructure.network.DeviceIpResolver
import com.flotalamacarena.telemetry.infrastructure.network.HttpTelemetryGateway
import com.flotalamacarena.telemetry.infrastructure.storage.SecureConfigRepository
import com.flotalamacarena.telemetry.infrastructure.work.TelemetryWorkScheduler
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val configRepo by lazy { SecureConfigRepository(applicationContext) }
    private val locationProvider by lazy { HybridLocationProvider(applicationContext) }
    private val gateway by lazy { HttpTelemetryGateway() }
    private val ipResolver by lazy { DeviceIpResolver() }

    private val validatePinUseCase by lazy { ValidateAdminPinUseCase() }
    private val saveConfigUseCase by lazy { SaveDeviceConfigUseCase(configRepo) }
    private val getConfigUseCase by lazy { GetDeviceConfigUseCase(configRepo) }
    private val sendHeartbeatUseCase by lazy {
        SendHeartbeatUseCase(configRepo, locationProvider, gateway, ipResolver)
    }
    private val workScheduler by lazy { TelemetryWorkScheduler(applicationContext) }

    // Launcher para permisos de ubicación en primer plano
    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false

        if (fineGranted || coarseGranted) {
            checkBackgroundLocationPermission()
        } else {
            showSnackbar("Se requiere permiso de ubicación para el monitoreo de geocercas.", isError = true)
        }
    }

    // Launcher para permiso de fondo (ACCESS_BACKGROUND_LOCATION)
    private val backgroundLocationLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            checkNotificationPermission()
        } else {
            showSnackbar("Recomendado: Seleccione 'Permitir todo el tiempo' para reportes con pantalla apagada.", isError = false)
        }
    }

    // Launcher para notificaciones (Android 13+)
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        checkBatteryOptimizationExemption()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupListeners()
        checkAllPermissions()
    }

    override fun onResume() {
        super.onResume()
        refreshUiState()
    }

    private fun setupListeners() {
        binding.btnSyncNow.setOnClickListener {
            performManualSync()
        }

        binding.btnAdminConfig.setOnClickListener {
            showPinAuthDialog()
        }
    }

    private fun refreshUiState() {
        val config = getConfigUseCase.execute()

        if (config.isEnrolled) {
            binding.tvStatusBadge.text = "ENROLADO / ACTIVO"
            binding.tvStatusBadge.setBackgroundResource(R.drawable.badge_status_ok)
            binding.tvStatusBadge.setTextColor(ContextCompat.getColor(this, R.color.status_ok))

            binding.tvDevicePlaca.text = config.placa
            binding.tvServerUrl.text = config.serverUrl
        } else {
            binding.tvStatusBadge.text = "NO ENROLADO"
            binding.tvStatusBadge.setBackgroundResource(R.drawable.badge_status_warning)
            binding.tvStatusBadge.setTextColor(ContextCompat.getColor(this, R.color.status_warning))

            binding.tvDevicePlaca.text = "[ SIN ASIGNAR ]"
            binding.tvServerUrl.text = config.serverUrl
        }

        val lastCoords = configRepo.getLastKnownCoordinates()
        if (lastCoords != null) {
            binding.tvLastLocation.text = String.format(Locale.US, "%.5f, %.5f", lastCoords.first, lastCoords.second)
        } else {
            binding.tvLastLocation.text = "Aún sin coordenadas registradas"
        }
    }

    private fun performManualSync() {
        if (!configRepo.isEnrolled()) {
            showSnackbar("Primero debe enrolar el dispositivo en 'Configuración TI'.", isError = true)
            return
        }

        setLoading(true)

        lifecycleScope.launch(Dispatchers.IO) {
            val result = sendHeartbeatUseCase.execute()

            withContext(Dispatchers.Main) {
                setLoading(false)
                when (result) {
                    is HeartbeatResult.Success -> {
                        val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
                        binding.tvLastHeartbeat.text = "${sdf.format(Date())} (Exitoso)"
                        refreshUiState()
                        showSnackbar("✅ Latido recibido por el servidor exitosamente.")
                    }
                    is HeartbeatResult.Skipped -> {
                        showSnackbar("ℹ️ ${result.reason}")
                    }
                    is HeartbeatResult.Error -> {
                        showSnackbar("⚠️ ${result.reason}", isError = true)
                    }
                }
            }
        }
    }

    private fun showPinAuthDialog() {
        val dialogBinding = DialogPinAuthBinding.inflate(LayoutInflater.from(this))

        val dialog = AlertDialog.Builder(this)
            .setView(dialogBinding.root)
            .setPositiveButton("Ingresar", null)
            .setNegativeButton("Cancelar", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val enteredPin = dialogBinding.etPin.text.toString().trim()
                if (validatePinUseCase.execute(enteredPin)) {
                    dialog.dismiss()
                    showAdminConfigDialog()
                } else {
                    dialogBinding.etPin.error = "PIN incorrecto. Acceso denegado."
                    dialogBinding.etPin.setText("")
                }
            }
        }

        dialog.show()
    }

    private fun showAdminConfigDialog() {
        val currentConfig = getConfigUseCase.execute()
        val dialogBinding = DialogAdminConfigBinding.inflate(LayoutInflater.from(this))

        dialogBinding.etPlaca.setText(currentConfig.placa)
        dialogBinding.etServerUrl.setText(currentConfig.serverUrl)
        dialogBinding.etApiKey.setText(currentConfig.apiKey)
        dialogBinding.etInterval.setText(currentConfig.syncIntervalMinutes.toString())

        val dialog = AlertDialog.Builder(this)
            .setView(dialogBinding.root)
            .setPositiveButton("Guardar y Enrolar", null)
            .setNeutralButton("Desenrolar", null)
            .setNegativeButton("Cancelar", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val placa = dialogBinding.etPlaca.text.toString()
                val serverUrl = dialogBinding.etServerUrl.text.toString()
                val apiKey = dialogBinding.etApiKey.text.toString()
                val interval = dialogBinding.etInterval.text.toString().toIntOrNull() ?: 15

                val saveResult = saveConfigUseCase.execute(placa, serverUrl, apiKey, interval)

                when (saveResult) {
                    is SaveDeviceConfigUseCase.Result.Success -> {
                        workScheduler.schedulePeriodicTelemetry(interval)
                        workScheduler.scheduleImmediateTelemetry()
                        refreshUiState()
                        dialog.dismiss()
                        showSnackbar("Dispositivo enrolado exitosamente con placa $placa.")
                        checkBatteryOptimizationExemption()
                    }
                    is SaveDeviceConfigUseCase.Result.ValidationError -> {
                        when (saveResult.field) {
                            "placa" -> dialogBinding.etPlaca.error = saveResult.message
                            "serverUrl" -> dialogBinding.etServerUrl.error = saveResult.message
                            "apiKey" -> dialogBinding.etApiKey.error = saveResult.message
                        }
                    }
                    is SaveDeviceConfigUseCase.Result.StorageError -> {
                        Toast.makeText(this@MainActivity, saveResult.message, Toast.LENGTH_LONG).show()
                    }
                }
            }

            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                configRepo.clearConfig()
                workScheduler.cancelAllTelemetry()
                refreshUiState()
                dialog.dismiss()
                showSnackbar("Dispositivo desenrolado. Monitoreo detenido.")
            }
        }

        dialog.show()
    }

    private fun checkAllPermissions() {
        val fineLocationGranted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val coarseLocationGranted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!fineLocationGranted || !coarseLocationGranted) {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        } else {
            checkBackgroundLocationPermission()
        }
    }

    private fun checkBackgroundLocationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val bgGranted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

            if (!bgGranted) {
                backgroundLocationLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            } else {
                checkNotificationPermission()
            }
        } else {
            checkBatteryOptimizationExemption()
        }
    }

    private fun checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val notifGranted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

            if (!notifGranted) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                checkBatteryOptimizationExemption()
            }
        } else {
            checkBatteryOptimizationExemption()
        }
    }

    @SuppressLint("BatteryLife")
    private fun checkBatteryOptimizationExemption() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return
        val packageName = packageName

        if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
            try {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
            } catch (e: Exception) {
                // Fallback silencioso si el fabricante bloquea el intent
            }
        }
    }

    private fun setLoading(isLoading: Boolean) {
        binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        binding.btnSyncNow.isEnabled = !isLoading
    }

    private fun showSnackbar(message: String, isError: Boolean = false) {
        val snackbar = Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG)
        if (isError) {
            snackbar.setBackgroundTint(ContextCompat.getColor(this, R.color.status_error))
        } else {
            snackbar.setBackgroundTint(ContextCompat.getColor(this, R.color.macarena_green_dark))
        }
        snackbar.show()
    }
}
