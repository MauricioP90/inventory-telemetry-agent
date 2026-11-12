# 📐 Plan Maestro, Tecnología y Arquitectura — Agente Móvil de Telemetría
**Proyecto:** `inventory-telemetry-agent` — **Flota La Macarena S.A.**

Este documento define el **plan detallado de construcción**, la **pila tecnológica**, la **arquitectura de software** y el **protocolo de sincronización** con el ecosistema central `cross_Inventory`.

---

## 🔗 1. Conexión y Sincronización con `cross_Inventory`

Este proyecto opera de manera desacoplada en su propio directorio para no contaminar el monorepo web/backend con binarios ni SDKs móviles, pero mantiene un **vínculo bidireccional obligatorio** con el repositorio central:

* **Archivo Puente en `cross_Inventory`**: [`/home/tecnologia17/desarrollo/cross_Inventory/AGENTE_TELEMETRIA_MOVIL.md`](file:///home/tecnologia17/desarrollo/cross_Inventory/AGENTE_TELEMETRIA_MOVIL.md)
* **Bitácora General en `cross_Inventory`**: [`/home/tecnologia17/desarrollo/cross_Inventory/BITACORA.md`](file:///home/tecnologia17/desarrollo/cross_Inventory/BITACORA.md)
* **Bitácora Local de este Proyecto**: [`/home/tecnologia17/desarrollo/inventory-telemetry-agent/BITACORA.md`](file:///home/tecnologia17/desarrollo/inventory-telemetry-agent/BITACORA.md)
* **Perfiles y Roles del Equipo**: [`/home/tecnologia17/desarrollo/inventory-telemetry-agent/AGENTS.md`](file:///home/tecnologia17/desarrollo/inventory-telemetry-agent/AGENTS.md)

### Protocolo de Sincronización
> [!IMPORTANT]
> Cada vez que se complete una fase, se modifique el payload JSON del latido, cambie la versión del `.apk` o se realice una prueba en dispositivo físico, se **debe actualizar simultáneamente**:
> 1. La tabla de estado en [`BITACORA.md`](file:///home/tecnologia17/desarrollo/inventory-telemetry-agent/BITACORA.md) (este proyecto).
> 2. La sección **"5. Bitácora de Evolución del Agente Móvil"** en [`/home/tecnologia17/desarrollo/cross_Inventory/AGENTE_TELEMETRIA_MOVIL.md`](file:///home/tecnologia17/desarrollo/cross_Inventory/AGENTE_TELEMETRIA_MOVIL.md).

---

## 🛠️ 2. Pila Tecnológica (Tech Stack)

El agente está diseñado como un **demonio corporativo ultra-ligero** para correr tanto en terminales POS de recursos limitados (1–2 GB RAM, Android 11+ como **Ciontek CS30** y **Topwise T6D**) como en dispositivos modernos (Android 17):

| Capa / Componente | Tecnología Seleccionada | Justificación Técnica |
| :--- | :--- | :--- |
| **Lenguaje Principal** | **Kotlin 2.x** | Nulabilidad segura, corrutinas ligeras y soporte oficial de primera clase en Android. |
| **SDK Mínimo (`minSdk`)** | **API 29 (Android 10) / API 30 (Android 11)** | Cubre el 100% de terminales POS (**Ciontek CS30**, **Topwise T6D**) y handhelds corporativos. |
| **SDK Objetivo (`targetSdk`)** | **API 35 / 36 (Android 15–17)** | Permite instalación limpia y cumplimiento de políticas de seguridad en celulares con **Android 17**. |
| **Motor de Tareas en Fondo** | **Android Jetpack `WorkManager`** | Garantiza ejecución diferida resiliente, respeta `Doze Mode`, espera conexión de red (`NetworkType.CONNECTED`) y sobrevive a reinicios. |
| **Arranque Automático** | **`BroadcastReceiver` (`LOCKED_BOOT_COMPLETED` / `BOOT_COMPLETED`)** | Despierta al agente apenas el dispositivo termina de encender, sin intervención humana. |
| **Proveedor de Ubicación** | **Ubicación Híbrida (`LocationManager` + `FusedLocation`)** | Combina satélite GPS con triangulación Wi-Fi/Celdas. Totalmente compatible con AOSP puro (sin Google Play Services) para terminales Topwise y Ciontek. |
| **Almacenamiento Seguro** | **`EncryptedSharedPreferences` (Android Keystore)** | Cifra la placa del activo, el PIN de TI y el `X-Device-Api-Key` en el hardware del dispositivo. |
| **Cliente HTTP** | **`HttpURLConnection` nativo / `OkHttp` ligero** | Cero sobrecarga de librerías pesadas; envía el JSON de ~250 bytes en milisegundos. |
| **Interfaz de Usuario (UI)** | **Android Views / Material Components ligeros** | Pantalla rápida de diagnóstico (Solo Lectura para el operario) y modal protegido por PIN para TI. |

---

## 🏛️ 3. Arquitectura del Proyecto (Clean / Hexagonal Architecture)

Manteniendo la filosofía de `inventory-node` e `inventory-angular`, el agente móvil implementa **Arquitectura Limpia (Hexagonal)** estructurada en 4 capas estrictas:

```text
inventory-telemetry-agent/
├── app/
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/flotalamacarena/telemetry/
│       │   ├── domain/                      # 1. CAPA DE DOMINIO (100% Kotlin puro, sin android.*)
│       │   │   ├── models/
│       │   │   │   ├── TelemetryHeartbeat.kt    # Placa, latitud, longitud, ipAddress, isMock, tamperingAlert
│       │   │   │   ├── DeviceConfig.kt          # Placa, serverUrl, apiKey, syncIntervalMinutes, isEnrolled
│       │   │   │   ├── LocationPoint.kt         # Coordenadas, precisión, proveedor, flag mock
│       │   │   │   ├── HeartbeatResult.kt       # Resultado del latido (Success, Skipped, Error)
│       │   │   │   └── AdminAuthResult.kt       # Resultado de autenticación (Authorized, Unauthorized, NetworkUnavailable)
│       │   │   └── ports/
│       │   │       ├── TelemetryGateway.kt      # Puerto de red hacia POST /api/telemetry/heartbeat y /admin-auth
│       │   │       ├── LocationProvider.kt      # Puerto de obtención de coordenadas y permisos
│       │   │       ├── ConfigRepository.kt      # Puerto de lectura/escritura cifrada de configuración
│       │   │       └── IpResolver.kt            # Puerto para resolución de IP de red
│       │   │
│       │   ├── application/                 # 2. CAPA DE APLICACIÓN (Casos de Uso)
│       │   │   └── usecases/
│       │   │       ├── SendHeartbeatUseCase.kt  # Orquesta captura GPS -> detección sabotaje -> envío HTTP
│       │   │       ├── SaveDeviceConfigUseCase.kt # Valida y persiste Placa, Servidor, API Key e Intervalo
│       │   │       ├── GetDeviceConfigUseCase.kt  # Consulta configuración actual del dispositivo
│       │   │       └── ValidateAdminPinUseCase.kt # Valida credencial remota (backend) + fallback local SHA-256
│       │   │
│       │   ├── infrastructure/              # 3. CAPA DE INFRAESTRUCTURA (Adaptadores Android y Red)
│       │   │   ├── network/
│       │   │   │   ├── HttpTelemetryGateway.kt  # Cliente OkHttp hacia /heartbeat y /admin-auth
│       │   │   │   └── DeviceIpResolver.kt      # Detección de IP local en interfaces Wi-Fi/4G
│       │   │   ├── location/
│       │   │   │   └── HybridLocationProvider.kt# FusedLocation + LocationManager nativo AOSP + detector Anti-Mock
│       │   │   ├── storage/
│       │   │   │   └── SecureConfigRepository.kt# EncryptedSharedPreferences (Hardware Keystore)
│       │   │   └── background/
│       │   │       ├── BootReceiver.kt          # BroadcastReceiver para arranque en frío (BOOT_COMPLETED)
│       │   │       ├── TelemetryWorker.kt       # Demonio de fondo periódico ejecutado por WorkManager
│       │   │       └── TelemetryWorkScheduler.kt# Programador de tareas periódicas y de arranque
│       │   │
│       │   └── presentation/                # 4. CAPA DE PRESENTACIÓN (UI Camuflada y Diagnóstico)
│       │       └── MainActivity.kt          # Login corporativo oscuro ("Servicios FLM") y panel técnico oculto
│       │
│       └── res/
│           ├── xml/network_security_config.xml  # Tráfico seguro HTTPS y HTTP en red local
│           └── layout/                          # activity_main.xml, dialog_admin_config.xml, dialog_forgot_password.xml
├── AGENTS.md                                # Perfiles y responsabilidades del equipo de desarrollo
├── BITACORA.md                              # Historial de cambios, ADRs y reporte de pruebas de campo
├── GUIA_QA_PRUEBAS.md                       # Protocolo de QA, proyección scrcpy y casos de prueba
├── PLAN_ARQUITECTURA.md                     # Plan maestro y especificación técnica
└── README.md
```

---

## 📋 4. Plan de Ejecución Paso a Paso (Qué y Cómo lo Haremos)

### Fase 1: Cimientos, Configuración Gradle y Permisos Multi-Versión (Android 11–17) [COMPLETADA]
* **Entregables**: Esqueleto base en Kotlin con `minSdk = 29` y `targetSdk = 35/36`, `AndroidManifest.xml` con permisos de ubicación y arranque, y `network_security_config.xml`.

### Fase 2: Dominio Puro, Casos de Uso y Persistencia Cifrada [COMPLETADA]
* **Entregables**: Modelos y puertos puros en `domain/` sin dependencias de Android, casos de uso en `application/` y almacenamiento seguro en hardware mediante `SecureConfigRepository` (`EncryptedSharedPreferences`).

### Fase 3: Proveedor de Ubicación Híbrido, Cliente HTTP y Detector Anti-Mock [COMPLETADA]
* **Entregables**: `HybridLocationProvider` con soporte dual AOSP para terminales POS (**Ciontek CS30**, **Topwise T6D**), detector de coordenadas simuladas (*Anti-Mock GPS*), `HttpTelemetryGateway` contra `POST /api/telemetry/heartbeat` y [`GUIA_QA_PRUEBAS.md`](file:///home/tecnologia17/desarrollo/inventory-telemetry-agent/GUIA_QA_PRUEBAS.md).

### Fase 4: Demonio de Arranque en Frío y Ciclo Periódico (`WorkManager`) [COMPLETADA]
* **Entregables**: `BootReceiver` (`BOOT_COMPLETED`, `LOCKED_BOOT_COMPLETED`) encolando tareas resilientes en `WorkManager` con política de reintentos exponencial y restricción `NetworkType.CONNECTED` para tolerar demoras en el enganche de la red móvil o Wi-Fi.

### Fase 5: Interfaz UI, Compilación `.apk` y Pruebas en Vivo [COMPLETADA]
* **Entregables**: Interfaz de enrolamiento y diagnóstico con ViewBinding, gestión escalonada de permisos en Android 11–17 y generación del binario instalable `app-debug.apk`.

### Fase 5.1: Inspección Visual en Vivo con `scrcpy` + Túnel Inverso `adb reverse` [COMPLETADA]
* **Entregables**: Proyección en monitor a 60 FPS con control de mouse y teclado sin instalar apps extra en el celular, permitiendo iteración visual de 3 a 5 segundos (`./gradlew installDebug`) y enlace directo al backend con `adb reverse tcp:3000 tcp:3000`.

### Fase 5.2: Camuflaje Anti-Tampering (*Security by Camouflage*) [COMPLETADA]
* **Entregables**: Rediseño de identidad visual a **`Servicios FLM`** con microbús corporativo e interfaz de login institucional oscura. Panel técnico de enrolamiento oculto tras credenciales de TI.

### Fase 5.3: Blindaje Criptográfico SHA-256 + Salt y Rate Limiting [COMPLETADA]
* **Entregables**: Llave maestra de emergencia `FLM#Admin2026` con hash SHA-256 y salt fija `FLM_TELEMETRY_SALT_2026_MACARENA`. Rate limiting activo de 3 intentos fallidos consecutivos que activa un bloqueo estricto de 300 segundos (5 minutos) con mensaje neutral.

### Fase 5.4: Perfeccionamiento de Camuflaje y Diálogo de Recuperación [COMPLETADA]
* **Entregables**: Microbús institucional estático, mensaje estándar de login (*"Usuario o contraseña incorrectos. Por favor verifique sus datos."*) y modal creíble de recuperación de contraseña hacia Mesa de Ayuda de TI.

### Fase 5.5: Autenticación Administrativa Centralizada Híbrida (`inventory-node`) [COMPLETADA]
* **Entregables**: Consulta en línea a `POST /api/telemetry/admin-auth` para delegar la autoridad de contraseñas a la base de datos de `cross_Inventory` (`telemetry_settings.admin_password`), permitiendo que el Administrador cambie la clave de toda la flota desde el portal web en segundos. Respaldo offline automático con hash local cuando el dispositivo no tiene red.

### Fase 5.6: Detección Activa de Sabotaje (Permisos Revocados & GPS Apagado) [COMPLETADA]
* **Entregables**: Supervisión continua de permisos de Android (`ACCESS_FINE_LOCATION`) y estado del GPS. Si se detecta manipulación, la app no falla silenciosamente: emite de inmediato un latido de emergencia con `tamperingAlert` (`PERMISOS_UBICACION_REVOCADOS`, `GPS_DESACTIVADO`, `MOCK_LOCATION_DETECTED`) adjuntando las últimas coordenadas registradas en memoria segura y la IP local. Reutilización de la plantilla de WhatsApp aprobada en Meta (`alerta_desviacion_inventario`).

