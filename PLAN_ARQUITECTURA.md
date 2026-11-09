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
│       │   ├── domain/                      # 1. CAPA DE DOMINIO (Pura, sin dependencias de Android)
│       │   │   ├── entities/
│       │   │   │   ├── TelemetryPayload.kt      # Placa, lat, lng, ip, isMock, timestamp
│       │   │   │   ├── AgentConfig.kt           # Placa asignada, serverUrl, apiKey, intervalHours
│       │   │   │   └── HeartbeatResult.kt       # Respuesta del backend (isAuthorized, distanceKm, etc.)
│       │   │   └── repositories/
│       │   │       ├── ITelemetryGateway.kt     # Puerto de envío HTTP al backend
│       │   │       ├── ILocationProvider.kt     # Puerto de obtención de coordenadas
│       │   │       └── IConfigRepository.kt     # Puerto de lectura/escritura cifrada de configuración
│       │   │
│       │   ├── application/                 # 2. CAPA DE APLICACIÓN (Casos de Uso)
│       │   │   └── usecases/
│       │   │       ├── SendHeartbeatUseCase.kt  # Orquesta lectura de config -> captura GPS -> envío HTTP
│       │   │       ├── EnrollDeviceUseCase.kt   # Valida PIN de TI y guarda Placa + URL + API Key
│       │   │       └── VerifyAdminPinUseCase.kt # Verifica el PIN administrativo para desbloquear ajustes
│       │   │
│       │   ├── infrastructure/              # 3. CAPA DE INFRAESTRUCTURA (Adaptadores Android / Red)
│       │   │   ├── http/
│       │   │   │   └── HttpTelemetryGateway.kt  # POST /api/telemetry/heartbeat con X-Device-Api-Key
│       │   │   ├── location/
│       │   │   │   └── HybridLocationProvider.kt# Captura GPS + Network fallback + detector Anti-Mock
│       │   │   ├── persistence/
│       │   │   │   └── SecureConfigRepository.kt# EncryptedSharedPreferences + estado último latido
│       │   │   └── background/
│       │   │       ├── BootCompletedReceiver.kt # Escucha el encendido del dispositivo
│       │   │       ├── TelemetryWorker.kt       # Worker ejecutado por WorkManager
│       │   │       └── WorkScheduler.kt         # Programa el trabajo de arranque y el periódico
│       │   │
│       │   └── presentation/                # 4. CAPA DE PRESENTACIÓN (UI Diagnóstico y Enrolamiento)
│       │       ├── MainActivity.kt              # Pantalla de estado (Solo Lectura) + Botón "Sincronizar Ahora"
│       │       └── EnrollmentDialog.kt          # Modal protegido por PIN para configurar Placa y Servidor
│       │
│       └── res/
│           ├── xml/network_security_config.xml  # Permite HTTPS y HTTP en red local para pruebas
│           └── layout/                          # Vistas XML limpias y ligeras
├── PLAN_ARQUITECTURA.md
├── AGENTS.md
└── BITACORA.md
```

---

## 📋 4. Plan de Ejecución Paso a Paso (Qué y Cómo lo Haremos)

### Fase 1: Cimientos, Configuración Gradle y Permisos Multi-Versión (Android 11–17)
* **Qué haremos**: Crear el esqueleto compilable del proyecto Gradle con soporte para Java 21, `minSdk = 29` y `targetSdk = 35`.
* **Cómo lo haremos**:
  1. Configurar `AndroidManifest.xml` declarando los permisos:
     * `INTERNET` y `ACCESS_NETWORK_STATE`.
     * `ACCESS_COARSE_LOCATION`, `ACCESS_FINE_LOCATION` y `ACCESS_BACKGROUND_LOCATION`.
     * `RECEIVE_BOOT_COMPLETED`.
     * `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`.
  2. Configurar `network_security_config.xml` para permitir pruebas contra la IP local del computador de desarrollo (`http://192.168.x.x:3000`) sin que Android 17 bloquee la petición por no ser HTTPS.

### Fase 2: Dominio, Casos de Uso y Seguridad por PIN de TI
* **Qué haremos**: Implementar las entidades puras, el almacenamiento seguro de la Placa y el bloqueo administrativo.
* **Cómo lo haremos**:
  1. Programar `SecureConfigRepository` para persistir:
     * `placa` (ej. `1528-003000`).
     * `serverUrl` (ej. `http://192.168.1.X:3000`).
     * `deviceApiKey` (`flm-telemetry-secret-key-2026`).
     * `adminPinHash` (PIN por defecto inicial: `2026`).
     * Registro del último latido exitoso (fecha, coordenada, sede detectada y si fue autorizado).
  2. Programar `SendHeartbeatUseCase` garantizando que si la placa no ha sido enrolada aún, no dispare peticiones vacías.

### Fase 3: Proveedor de Ubicación Híbrido y Cliente HTTP
* **Qué haremos**: Capturar las coordenadas reales del teléfono/POS y enviarlas a `POST /api/telemetry/heartbeat`.
* **Cómo lo haremos**:
  1. En `HybridLocationProvider`:
     * Solicitar coordenada fresca al proveedor `GPS_PROVIDER` y `NETWORK_PROVIDER` con un timeout controlado de 15 segundos; si está bajo techo denso, usar la última ubicación conocida reciente (`getLastKnownLocation`) como respaldo.
     * Evaluar `location.isFromMockProvider` (API <= 30) y `location.isMock` (API 31–36) para detectar apps de *Fake GPS*.
  2. En `HttpTelemetryGateway`:
     * Construir el JSON exacto que espera [`TelemetryController.ts`](file:///home/tecnologia17/desarrollo/cross_Inventory/inventory-node/src/infrastructure/http/controllers/TelemetryController.ts): `placa`, `latitud`, `longitud`, `ipAddress`.
     * Inyectar la cabecera `X-Device-Api-Key`.

### Fase 4: Demonio de Arranque (`BOOT_COMPLETED`) y Trabajo Periódico (`WorkManager`)
* **Qué haremos**: Automatizar el envío sin intervención humana al prender el equipo y cada $N$ horas.
* **Cómo lo haremos**:
  1. Crear `BootCompletedReceiver` que escuche `android.intent.action.BOOT_COMPLETED` y `android.intent.action.QUICKBOOT_POWERON`.
  2. Al recibir el evento, encolar un `OneTimeWorkRequest` en `WorkManager` con la condición `Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()`.
  3. Así, si la SIM Card 4G tarda 40 segundos en conectar tras encender la POS, Android esperará exactamente a que haya datos móviles y disparará el latido en ese instante.
  4. Configurar además un `PeriodicWorkRequest` (configurable, por defecto cada 8 horas) para equipos que permanecen encendidos 24/7.

### Fase 5: Interfaz de Diagnóstico, Enrolamiento y Pruebas Reales en Celular (Android 17)
* **Qué haremos**: Construir la pantalla principal y probar el `.apk` en vivo en el celular del usuario y luego en POS.
* **Cómo lo haremos**:
  1. **Modo Operario (Por defecto)**: Muestra el estado del equipo, la Placa vinculada, el estado de los permisos (GPS Todo el Tiempo + Batería), el resultado del último latido y un botón **`📡 Sincronizar Ahora`**.
  2. **Modo Administrador TI (Botón ⚙️ Configurar)**: Solicita el PIN (`2026`), y al desbloquear permite editar la Placa, la URL del servidor, la API Key y botones directos para conceder el permiso *"Permitir todo el tiempo"* y *"Excluir de ahorro de batería"*.
  3. Compilar el `.apk`, instalarlo en el celular con Android 17 vía `adb install` o descarga local, y verificar la llegada del latido al Dashboard de Angular y la alerta de WhatsApp en vivo.

