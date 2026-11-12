# 📓 Bitácora de Desarrollo — `inventory-telemetry-agent`
**Agente Móvil de Telemetría y Geocercas (Flota La Macarena S.A.)**

Este documento es la **guía central y bitácora de seguimiento** del desarrollo del Agente Móvil Android. Registra el avance por fases, las decisiones técnicas tomadas, el estado de las pruebas en dispositivos reales y su sincronización con el proyecto principal `cross_Inventory`.

> [!NOTE]
> **Documento Espejo en el Proyecto Principal:**
> Este archivo está vinculado directamente con [`/home/tecnologia17/desarrollo/cross_Inventory/AGENTE_TELEMETRIA_MOVIL.md`](file:///home/tecnologia17/desarrollo/cross_Inventory/AGENTE_TELEMETRIA_MOVIL.md). Ambos deben mantenerse sincronizados al cierre de cada hito.

---

## 📡 1. Estado Actual del Entorno y Proyecto

* **Directorio del Proyecto**: `/home/tecnologia17/desarrollo/inventory-telemetry-agent`
* **Repositorio Remoto (GitHub)**: `https://github.com/MauricioP90/inventory-telemetry-agent.git` (Rama: `main`) -> **Activo y Sincronizado**.
* **Java Runtime & Toolchain**: `OpenJDK 21.0.12` (`/usr/bin/java`) + Toolchain `JDK 17` (`~/.gradle/jdks`) -> **Listo**.
* **Android SDK Local**: `/home/tecnologia17/Android/Sdk` (`platforms;android-35`, `build-tools;35.0.0`, `build-tools;34.0.0`, `platform-tools`) -> **Instalado y Verificado**.
* **Instalador Compilado (`.apk`)**: `ServiciosFLM.apk` (18 MB en raíz y `/sdcard/Download/`) y `app/build/outputs/apk/debug/app-debug.apk` -> **Generado y Desplegado**.
* **Credencial Administrativa**: Hash SHA-256 con Salt (`FLM#Admin2026`) + Bloqueo de fuerza bruta (3 intentos, 5 min) -> **Blindado y Verificado**.
* **Android Debug Bridge (ADB)**: `v1.0.41 (34.0.4)` (`/usr/lib/android-sdk/platform-tools/adb`) -> **Listo**.
* **Visualización en Monitor (Scrcpy)**: Proyección de pantalla y control por mouse de baja latencia sin apps extra en el celular -> **Disponible para instalación (`sudo apt install scrcpy`)**.
* **Backend Receptor (`inventory-node`)**: Endpoint `POST /api/telemetry/heartbeat` con autenticación `X-Device-Api-Key` -> **Operativo y Verificado**.
* **Notificaciones (Correo + WhatsApp Wasapi)**: Plantilla Meta `alerta_desviacion_inventario` (`2bf3b48b-b426-4a2e-9442-3b38a35f606a`) con banner `monitoreo.jpeg` y botón Google Maps -> **Operativo y Verificado**.
* **Dispositivos Objetivo**:
  * 📱 **Dispositivo de Pruebas Inicial**: Smartphone del usuario con **Android 17**.
  * 📟 **Dispositivos de Producción**: Terminales POS (**Ciontek CS30**, **Topwise T6D**) y equipos corporativos con **Android 11+ (API 30+)**.

---

## 🗺️ 2. Hoja de Ruta y Checklist de Fases

| Fase | Descripción del Hito | Archivos Clave | Estado | Sincronizado con `AGENTE_TELEMETRIA_MOVIL.md` |
| :---: | :--- | :--- | :---: | :---: |
| **Fase 0** | **Planeación, Arquitectura y Roles**: Creación de `PLAN_ARQUITECTURA.md`, `AGENTS.md`, `BITACORA.md` y enlace con `cross_Inventory`. | `PLAN_ARQUITECTURA.md`, `AGENTS.md`, `BITACORA.md` | ✅ **Completado** | ✅ Sí (06/Nov/2026) |
| **Fase 1** | **Estructura Base Android & Gradle**: Configuración de `build.gradle.kts` (`minSdk 29`, `targetSdk 35`), `AndroidManifest.xml`, recursos corporativos y `network_security_config.xml`. | `build.gradle.kts`, `AndroidManifest.xml`, `network_security_config.xml` | ✅ **Completado** | ✅ Sí (09/Nov/2026) |
| **Fase 2** | **Capa de Dominio, Aplicación y Persistencia con PIN**: Entidades puras, puertos, casos de uso (`ValidateAdminPinUseCase` con PIN `2026`, `SendHeartbeatUseCase`) y persistencia cifrada (`SecureConfigRepository`). | `domain/*`, `application/*`, `SecureConfigRepository.kt` | ✅ **Completado** | ✅ Sí (09/Nov/2026) |
| **Fase 3** | **Proveedor de Ubicación Híbrido, Cliente HTTP y Guía QA**: Ubicación híbrida (Google Fused + LocationManager nativo AOSP), Anti-Mock GPS, `HttpTelemetryGateway` y [`GUIA_QA_PRUEBAS.md`](file:///home/tecnologia17/desarrollo/inventory-telemetry-agent/GUIA_QA_PRUEBAS.md). | `HybridLocationProvider.kt`, `HttpTelemetryGateway.kt`, `GUIA_QA_PRUEBAS.md` | ✅ **Completado** | ✅ Sí (09/Nov/2026) |
| **Fase 4** | **Demonio de Arranque y Ciclo Periódico**: `BootReceiver` (`BOOT_COMPLETED`, `LOCKED_BOOT_COMPLETED`) y `TelemetryWorker` (`WorkManager` con `NetworkType.CONNECTED` y backoff exponencial). | `BootReceiver.kt`, `TelemetryWorker.kt`, `TelemetryWorkScheduler.kt` | ✅ **Completado** | ✅ Sí (09/Nov/2026) |
| **Fase 5** | **Interfaz UI, Compilación `.apk` y Prueba en Vivo**: Pantalla de estado con ViewBinding, modal de enrolamiento con PIN `2026`, gestión de permisos escalonados y compilación de `app-debug.apk`. | `MainActivity.kt`, `activity_main.xml`, `app-debug.apk` | ✅ **Completado** | ✅ Sí (10/Nov/2026) |
| **Fase 5.2** | **Camuflaje Anti-Tampering & UI Corporativa `Servicios FLM`**: Rediseño estilo portal corporativo oscuro con microbús de Flota La Macarena, bloqueo total a operarios de taquilla y acceso protegido para TI. | `MainActivity.kt`, `activity_main.xml`, `dialog_admin_config.xml`, `colors.xml`, `strings.xml` | ✅ **Completado** | ✅ Sí (10/Nov/2026) |
| **Fase 5.3** | **Blindaje Criptográfico & Anti-Fuerza Bruta**: Hashing SHA-256 + Salt (`FLM#Admin2026`), rate limiting de 3 intentos / bloqueo de 300s, mensaje disuasorio neutral y suite de pruebas unitarias al 100%. | `ValidateAdminPinUseCase.kt`, `ValidateAdminPinUseCaseTest.kt`, `MainActivity.kt` | ✅ **Completado** | ✅ Sí (10/Nov/2026) |
| **Fase 5.4** | **Perfeccionamiento de Camuflaje (Login Realista & Recuperación)**: Eliminación del gesto en microbús (ilustración estática), unificación en Acceso 1 (`FLM#Admin2026`), respuestas de error estándar de login (*"Usuario o contraseña incorrectos"*) y modal creíble de recuperación de credenciales hacia Mesa de Ayuda. | `MainActivity.kt`, `dialog_forgot_password.xml`, `strings.xml`, `ServiciosFLM.apk` | ✅ **Completado** | ✅ Sí (12/Nov/2026) |
| **Fase 5.5** | **Autenticación Administrativa Híbrida (Remota Backend + Offline Fallback)**: Integración con `POST /api/telemetry/admin-auth` para delegar la autoridad de credenciales a `inventory-node` evitando secuestro de terminales, con fallback local resiliente a SHA-256 cuando no hay red. | `AdminAuthResult.kt`, `TelemetryGateway.kt`, `HttpTelemetryGateway.kt`, `ValidateAdminPinUseCase.kt` | ✅ **Completado** | ✅ Sí (12/Nov/2026) |
| **Fase 5.6** | **Detección Activa de Sabotaje (Permisos Revocados & GPS Apagado)**: Supervisión activa de permisos en Android (`ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`) y estado de GPS. Si se revocan o apagan, no detiene el agente: emite de inmediato un latido de emergencia con `tamperingAlert`, últimas coordenadas válidas e IP hacia `POST /api/telemetry/heartbeat`. | `SendHeartbeatUseCase.kt`, `LocationProvider.kt`, `HybridLocationProvider.kt`, `HttpTelemetryGateway.kt` | ✅ **Completado** | ✅ Sí (12/Nov/2026) |

---

## 🧠 3. Registro de Decisiones de Diseño (ADR)

### ADR-001: Separación de Repositorio con Documento Puente (06/Nov/2026)
* **Contexto**: Incluir el proyecto Android dentro de `cross_Inventory` añadiría archivos de compilación móvil, cachés de Gradle y configuraciones de SDK ajenas al stack Node/Angular.
* **Decisión**: Alojar la aplicación en `/home/tecnologia17/desarrollo/inventory-telemetry-agent` (siguiendo el mismo patrón exitoso de `/home/tecnologia17/desarrollo/inventory-e2e`) y mantener sincronizado `/home/tecnologia17/desarrollo/cross_Inventory/AGENTE_TELEMETRIA_MOVIL.md`.

### ADR-002: Compatibilidad Unificada Android 11+ a Android 17 mediante `WorkManager` (06/Nov/2026)
* **Contexto**: El celular de pruebas corre **Android 17** mientras que las terminales POS corren **Android 11+**. Las versiones modernas de Android restringen severamente el lanzamiento de servicios en segundo plano desde `BOOT_COMPLETED`.
* **Decisión**: Utilizar `WorkManager` con restricción `NetworkType.CONNECTED` al recibir `BOOT_COMPLETED`, combinado con el permiso `"Permitir todo el tiempo"` (`ACCESS_BACKGROUND_LOCATION`) y exención de optimización de batería otorgados una única vez durante el enrolamiento de TI.

### ADR-003: Bloqueo de Interfaz por PIN Administrativo de TI (06/Nov/2026)
* **Contexto**: El operario de una POS o custodio de un equipo no debe poder cambiar el número de placa del activo ni desactivar los reportes.
* **Decisión**: La interfaz principal abre siempre en **Modo Solo Lectura** (mostrando Placa, Estado y botón manual *"Sincronizar Ahora"*). Cualquier cambio de Placa, Servidor o API Key exige ingresar el PIN de TI.

### ADR-004: Emulación e Inspección Visual en Tiempo Real vía `scrcpy` en Dispositivo Físico (10/Nov/2026)
* **Contexto**: En máquinas corporativas con restricciones de virtualización por hardware (`/dev/kvm`), ejecutar un emulador de Android Studio consume de 4 a 6 GB de RAM y puede ralentizar el equipo. Además, se requiere un flujo rápido de iteración visual para ajustar la interfaz corporativa.
* **Decisión**: Adoptar **`scrcpy`** conectado por cable USB al celular de prueba. Permite proyectar la pantalla del dispositivo a 60 FPS en el monitor de Linux con control total por mouse/teclado, sin instalar aplicaciones adicionales en el teléfono móvil y permitiendo ciclos de compilación incremental de 3 a 5 segundos (`./gradlew installDebug`).

### ADR-005: Camuflaje de Interfaz y Ocultamiento de Telemetría (Security by Camouflage) (10/Nov/2026)
* **Contexto**: Mostrar que la aplicación es de "Telemetría", "GPS" o "Geocercas" en el menú o pantalla principal alerta a cualquier operario o custodio no autorizado, facilitando intentos de evasión, desactivación de ubicación o sabotaje del servicio.
* **Decisión**: Reemplazar la identidad visible de la aplicación por **`Servicios FLM`** con un diseño de login corporativo oscuro y la ilustración oficial del microbús de Flota La Macarena. Cualquier intento de inicio de sesión no autorizado emite un mensaje neutral: *"Acceso restringido: Contacte al Administrador de TI."* El panel técnico de telemetría y enrolamiento queda completamente oculto y solo se desbloquea con la credencial de TI o un gesto administrativo discreto.

### ADR-006: Hashing Criptográfico SHA-256 con Salt y Rate Limiting para Acceso Administrativo (10/Nov/2026)
* **Contexto**: Un PIN numérico corto de 4 dígitos (`2026`) es vulnerable a adivinación casual o deducción en taquilla. Almacenar credenciales en texto claro dentro del binario descompilable también es inseguro.
* **Decisión**:
  1. Utilizar un hash criptográfico unidireccional **SHA-256** complementado con una sal fija interna (`FLM_TELEMETRY_SALT_2026_MACARENA`).
  2. Contraseña Maestra de TI: `FLM#Admin2026` (Hash verificado: `85f7c912e02e64abe758c264adaa3140cfd57f18e20ec71ee3777c1b2770472c`).
  3. Protección activa anti-fuerza bruta en `ValidateAdminPinUseCase`: al acumular 3 intentos fallidos consecutivos, el sistema se bloquea por 300 segundos (5 minutos) rechazando cualquier intento adicional.
  4. Mensaje disuasorio: *"Acceso restringido: Contacte al Administrador de TI."*

### ADR-007: Centralización de Autenticación de Administración en el Backend (12/Nov/2026)
* **Contexto**: Permitir que la clave administrativa se cambie localmente en el dispositivo crea el riesgo de secuestro de terminal (*Device Takeover*) si alguien la descubre y bloquea al personal de TI.
* **Decisión**:
  1. No permitir cambios de contraseña aislados en el dispositivo móvil.
  2. Centralizar la autoridad de la contraseña técnica en el servidor (`POST /api/telemetry/admin-auth` en `inventory-node`), permitiendo que TI revoque o cambie la clave en el backend para toda la flota en segundos.
  3. Mantener el hash local (`FLM#Admin2026`) como mecanismo de respaldo fuera de línea (*Offline Fallback*) únicamente cuando el equipo esté en bodegas o zonas sin cobertura de red.
* **Procedimiento Operativo para Rotar la Contraseña**:
  1. El Administrador de TI edita la variable `TELEMETRY_ADMIN_PASSWORD` en el `.env` del servidor `inventory-node` (o a través del portal web en `telemetry_settings`).
  2. Al recargar el backend, todas las terminales POS y smartphones exigen la nueva contraseña de inmediato, invalidando la anterior sin tocar ningún equipo en campo.

### ADR-008: Detección Activa de Sabotaje sin Falla Silenciosa (12/Nov/2026)
* **Contexto**: Un operario podría intentar evadir el rastreo entrando a los Ajustes de Android y revocando los permisos de ubicación a la app, o apagando el interruptor de GPS en la barra de notificaciones. Si el agente simplemente espera permisos o se queda en silencio, el equipo se vuelve invisible sin que TI se entere.
* **Decisión**:
  1. Los permisos de red (`INTERNET`, `ACCESS_NETWORK_STATE`) son permisos normales de instalación que el usuario **no puede revocar** en Android.
  2. Ante la falta de permisos de GPS (`!hasLocationPermission()`) o sensor apagado (`!isLocationEnabled()`), el caso de uso `SendHeartbeatUseCase` genera inmediatamente un latido de emergencia con el campo `tamperingAlert: "PERMISOS_UBICACION_REVOCADOS"` o `"GPS_DESACTIVADO"`.
  3. Se envían las últimas coordenadas GPS válidas guardadas en almacenamiento seguro junto a la IP local de conexión.
  4. El servidor `inventory-node` clasifica este latido como `CONTROVERSIA_UBICACION` y dispara la alerta inmediata a WhatsApp vía Wasapi, reutilizando la plantilla oficial de Meta.

---

## 🧪 4. Guía Rápida para Pruebas y Ajustes Visuales con el Celular (Fase 5)

Podemos probar y ajustar la interfaz desde tu celular con **Android 17**:

1. **Vía Cable USB con `scrcpy` y `adb` (Flujo Recomendado de Alta Velocidad)**:
   * Activar *Depuración por USB* en el celular y conectar por cable.
   * Instalar `scrcpy` en la máquina: `sudo snap install scrcpy`.
   * Habilitar el túnel inverso al backend local: `adb reverse tcp:3000 tcp:3000`.
   * Proyectar la pantalla en vivo: `scrcpy`.
   * Instalar el APK compilado: `adb install -r app/build/outputs/apk/debug/app-debug.apk`.
   * Cada ajuste visual en XML o Kotlin se refleja en ~4 segundos con `./gradlew installDebug`.
2. **Vía Wi-Fi / Descarga Directa del `.apk`**:
   * Copiar el `.apk` al celular, instalarlo y apuntar la URL del servidor a la IP local del computador (`http://192.168.x.x:3000`).

---

## 🎯 5. Reporte de Certificación de Campo en Vivo (Prueba E2E Exitosa)

Se certificó el flujo completo de telemetría y geocercas en el dispositivo móvil Xiaomi con **Android 17**:

* **Dispositivo de Prueba**: Smartphone Físico conectado por USB (`adb reverse tcp:3000 tcp:3000`).
* **Activo Evaluado**: Placa `1528-007885`.
* **Sede Esperada (Asignada en BD)**: **ACACIAS** (`lat: 3.9866, lon: -73.7645`, radio permitido 200m).
* **Ubicación Real Capturada por el GPS del Celular**: Bogotá (`lat: 4.70945, lon: -74.06176`).
* **Desviación Calculada por el Backend**: **86.87 km** de distancia de la sede autorizada.
* **Resultado del Heartbeat (`POST /api/telemetry/heartbeat`)**:
  * Status: `201 Created`
  * Clasificación: `CONTROVERSIA_UBICACION` (`isAuthorized: false`)
  * Registro en PostgreSQL (`activo_telemetry_logs`): Guardado con UUID y traza completa.
  * Alerta WhatsApp Meta (Wasapi): Enviada exitosamente (`alert_sent: true`, UUID `9a54d1b8-c001-4efc-b4ea-b5e2e815b89b`) con plantilla corporativa y enlace a Google Maps.

---

## 📦 6. Ubicación de Archivos para Descarga y Distribución (`.apk`)

* **En el Computador (Linux)**:
  * 📁 Acceso directo: [`/home/tecnologia17/desarrollo/inventory-telemetry-agent/ServiciosFLM.apk`](file:///home/tecnologia17/desarrollo/inventory-telemetry-agent/ServiciosFLM.apk) *(18 MB)*
  * 📁 Salida Gradle Debug: [`/home/tecnologia17/desarrollo/inventory-telemetry-agent/app/build/outputs/apk/debug/app-debug.apk`](file:///home/tecnologia17/desarrollo/inventory-telemetry-agent/app/build/outputs/apk/debug/app-debug.apk)
* **En el Celular (Almacenamiento Interno)**:
  * 📁 Carpeta Descargas: `/sdcard/Download/ServiciosFLM.apk` *(Listo para abrir con el Administrador de Archivos)*

