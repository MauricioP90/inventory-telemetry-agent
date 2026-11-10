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
import android.view.inputmethod.InputMethodManager
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
import com.flotalamacarena.telemetry.domain.models.DeviceConfig
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

/**
 * Pantalla principal del Agente Móvil, camuflada como la aplicación corporativa "Servicios FLM".
 * Oculta completamente los componentes de telemetría y geocercas a operarios no autorizados,
 * requiriendo autenticación administrativa de TI para desbloquear la gestión del dispositivo.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    // Inyección de dependencias limpia de infraestructura y casos de uso
    private val configRepo by lazy { SecureConfigRepository(this) }
    private val locationProvider by lazy { HybridLocationProvider(this) }
    private val telemetryGateway by lazy { HttpTelemetryGateway() }
    private val ipResolver by lazy { DeviceIpResolver() }

    private val getConfigUseCase by lazy { GetDeviceConfigUseCase(configRepo) }
    private val saveConfigUseCase by lazy { SaveDeviceConfigUseCase(configRepo) }
    private val validatePinUseCase by lazy { ValidateAdminPinUseCase() }
    private val sendHeartbeatUseCase by lazy {
        SendHeartbeatUseCase(configRepo, locationProvider, telemetryGateway, ipResolver)
    }

    private var busTapCount = 0
    private var lastBusTapTime = 0L

    // Launcher para permisos de ubicación en primer plano
    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false

        if (fineGranted || coarseGranted) {
            checkBackgroundLocationPermission()
        }
    }

    // Launcher para permiso de fondo (ACCESS_BACKGROUND_LOCATION)
    private val backgroundLocationLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            checkNotificationPermission()
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
    }

    private fun setupListeners() {
        // Botón Ingresar del formulario de login
        binding.btnLogin.setOnClickListener {
            handleLoginAttempt()
        }

        // Enlace "¿Olvidó su contraseña?"
        binding.tvForgotPassword.setOnClickListener {
            showAccessRestrictedNotice()
        }

        // Acceso directo administrativo secreto (Toque prolongado o 5 toques rápidos en el logo del bus)
        binding.ivBusLogo.setOnLongClickListener {
            showPinAuthDialog()
            true
        }

        binding.ivBusLogo.setOnClickListener {
            val now = System.currentTimeMillis()
            if (now - lastBusTapTime < 800) {
                busTapCount++
                if (busTapCount >= 5) {
                    busTapCount = 0
                    showPinAuthDialog()
                }
            } else {
                busTapCount = 1
            }
            lastBusTapTime = now
        }
    }

    private fun handleLoginAttempt() {
        hideKeyboard()
        val username = binding.etUsername.text.toString().trim()
        val password = binding.etPassword.text.toString().trim()

        if (password.isBlank()) {
            binding.etPassword.error = "Ingrese su contraseña"
            return
        }

        setLoading(true)

        lifecycleScope.launch {
            // Simulación sutil de latencia de autenticación corporativa
            kotlinx.coroutines.delay(400)
            setLoading(false)

            when (val auth = validatePinUseCase.execute(password)) {
                is ValidateAdminPinUseCase.AuthResult.Success -> {
                    binding.etPassword.setText("")
                    binding.etUsername.setText("")
                    showAdminConfigDialog()
                }
                is ValidateAdminPinUseCase.AuthResult.LockedOut -> {
                    binding.etPassword.setText("")
                    showSnackbar("🔒 Terminal bloqueada temporalmente. Espere ${auth.remainingSeconds}s.", isError = true)
                }
                is ValidateAdminPinUseCase.AuthResult.InvalidCredentials -> {
                    binding.etPassword.setText("")
                    showAccessRestrictedNotice()
                }
            }
        }
    }

    private fun showAccessRestrictedNotice() {
        showSnackbar(getString(R.string.access_restricted), isError = true)
    }

    private fun showPinAuthDialog() {
        val input = android.widget.EditText(this).apply {
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            hint = "Contraseña de TI"
            setPadding(40, 30, 40, 30)
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.title_admin_config)
            .setMessage(R.string.pin_prompt)
            .setView(input)
            .setPositiveButton("Ingresar") { _, _ ->
                val entered = input.text.toString().trim()
                when (val auth = validatePinUseCase.execute(entered)) {
                    is ValidateAdminPinUseCase.AuthResult.Success -> {
                        showAdminConfigDialog()
                    }
                    is ValidateAdminPinUseCase.AuthResult.LockedOut -> {
                        showSnackbar("🔒 Bloqueado por seguridad. Espere ${auth.remainingSeconds}s.", isError = true)
                    }
                    is ValidateAdminPinUseCase.AuthResult.InvalidCredentials -> {
                        showSnackbar(getString(R.string.access_restricted), isError = true)
                    }
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun showAdminConfigDialog() {
        val currentConfig = getConfigUseCase.execute()
        val dialogBinding = DialogAdminConfigBinding.inflate(LayoutInflater.from(this))

        dialogBinding.etPlaca.setText(currentConfig.placa)
        dialogBinding.etServerUrl.setText(currentConfig.serverUrl)
        dialogBinding.etApiKey.setText(currentConfig.apiKey)
        dialogBinding.etInterval.setText(currentConfig.syncIntervalMinutes.toString())

        fun updateDiagnosticCard() {
            if (configRepo.isEnrolled()) {
                dialogBinding.tvDiagStatus.text = "ESTADO: ENROLADO (Activo)"
                dialogBinding.tvDiagStatus.setTextColor(ContextCompat.getColor(this, R.color.status_ok))
            } else {
                dialogBinding.tvDiagStatus.text = "ESTADO: NO ENROLADO"
                dialogBinding.tvDiagStatus.setTextColor(ContextCompat.getColor(this, R.color.status_warning))
            }

            val coords = configRepo.getLastKnownCoordinates()
            if (coords != null) {
                dialogBinding.tvDiagCoords.text = String.format(Locale.US, "Coordenadas: %.5f, %.5f", coords.first, coords.second)
            } else {
                dialogBinding.tvDiagCoords.text = "Coordenadas: Aún sin registrar"
            }
        }

        updateDiagnosticCard()

        // Botón de Sincronización Manual dentro del panel técnico
        dialogBinding.btnDiagSync.setOnClickListener {
            dialogBinding.tvDiagLastHeartbeat.text = "Enviando latido..."
            lifecycleScope.launch(Dispatchers.IO) {
                val result = sendHeartbeatUseCase.execute()
                withContext(Dispatchers.Main) {
                    when (result) {
                        is HeartbeatResult.Success -> {
                            val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
                            dialogBinding.tvDiagLastHeartbeat.text = "Último latido: ${sdf.format(Date())} (Exitoso)"
                            updateDiagnosticCard()
                            showSnackbar("✅ Latido recibido por el servidor exitosamente.")
                        }
                        is HeartbeatResult.Skipped -> {
                            dialogBinding.tvDiagLastHeartbeat.text = "Omitido: ${result.reason}"
                            showSnackbar("ℹ️ ${result.reason}")
                        }
                        is HeartbeatResult.Error -> {
                            dialogBinding.tvDiagLastHeartbeat.text = "Error: ${result.reason}"
                            showSnackbar("⚠️ ${result.reason}", isError = true)
                        }
                    }
                }
            }
        }

        val dialog = AlertDialog.Builder(this)
            .setView(dialogBinding.root)
            .setPositiveButton("Guardar y Enrolar", null)
            .setNeutralButton("Desenrolar", null)
            .setNegativeButton("Cerrar", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val placa = dialogBinding.etPlaca.text.toString().trim()
                val serverUrl = dialogBinding.etServerUrl.text.toString().trim()
                val apiKey = dialogBinding.etApiKey.text.toString().trim()
                val interval = dialogBinding.etInterval.text.toString().toIntOrNull() ?: 15

                when (val result = saveConfigUseCase.execute(placa, serverUrl, apiKey, interval)) {
                    is SaveDeviceConfigUseCase.Result.Success -> {
                        TelemetryWorkScheduler(this@MainActivity).schedulePeriodicTelemetry(interval)
                        showSnackbar("✅ Dispositivo enrolado exitosamente.")
                        dialog.dismiss()
                        checkAllPermissions()
                    }
                    is SaveDeviceConfigUseCase.Result.ValidationError -> {
                        when (result.field) {
                            "placa" -> dialogBinding.etPlaca.error = result.message
                            "serverUrl" -> dialogBinding.etServerUrl.error = result.message
                            "apiKey" -> dialogBinding.etApiKey.error = result.message
                        }
                    }
                    is SaveDeviceConfigUseCase.Result.StorageError -> {
                        showSnackbar("Error al guardar la configuración cifrada: ${result.message}", isError = true)
                    }
                }
            }

            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                AlertDialog.Builder(this)
                    .setTitle("Confirmar Desenrolamiento")
                    .setMessage("¿Desea eliminar las credenciales y desvincular este dispositivo?")
                    .setPositiveButton("Sí, Desenrolar") { _, _ ->
                        configRepo.clearConfig()
                        TelemetryWorkScheduler(this@MainActivity).cancelAllTelemetry()
                        showSnackbar("Dispositivo desenrolado correctamente.")
                        dialog.dismiss()
                    }
                    .setNegativeButton("Cancelar", null)
                    .show()
            }
        }

        dialog.show()
    }

    private fun setLoading(loading: Boolean) {
        binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        binding.btnLogin.isEnabled = !loading
    }

    private fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(binding.root.windowToken, 0)
    }

    private fun showSnackbar(message: String, isError: Boolean = false) {
        val snackbar = Snackbar.make(binding.rootLayout, message, Snackbar.LENGTH_LONG)
        if (isError) {
            snackbar.view.setBackgroundColor(ContextCompat.getColor(this, R.color.status_error))
        } else {
            snackbar.view.setBackgroundColor(ContextCompat.getColor(this, R.color.status_ok))
        }
        snackbar.show()
    }

    // ==========================================
    // GESTIÓN ESCALONADA DE PERMISOS
    // ==========================================

    private fun checkAllPermissions() {
        val hasFine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

        if (!hasFine || !hasCoarse) {
            locationPermissionLauncher.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            )
        } else {
            checkBackgroundLocationPermission()
        }
    }

    private fun checkBackgroundLocationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val hasBg = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED
            if (!hasBg) {
                backgroundLocationLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            } else {
                checkNotificationPermission()
            }
        } else {
            checkNotificationPermission()
        }
    }

    private fun checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasNotif = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            if (!hasNotif) {
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
        val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
        if (powerManager != null && !powerManager.isIgnoringBatteryOptimizations(packageName)) {
            try {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
            } catch (e: Exception) {
                // Fallback silencioso si el fabricante bloquea el intent directo
            }
        }
    }
}
