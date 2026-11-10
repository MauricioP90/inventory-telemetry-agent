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
* **Instalador Compilado (`.apk`)**: `app/build/outputs/apk/debug/app-debug.apk` -> **Generado con Éxito**.
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
* **Decisión**: Reemplazar la identidad visible de la aplicación por **`Servicios FLM`** con un diseño de login corporativo oscuro y la ilustración oficial del microbús de Flota La Macarena. Cualquier intento de inicio de sesión no autorizado emite un mensaje neutral: *"Acceso restringido: Contacte al Administrador de TI."* El panel técnico de telemetría y enrolamiento queda completamente oculto y solo se desbloquea con el PIN de TI (`2026`) o un gesto administrativo discreto.

---

## 🧪 4. Guía Rápida para Pruebas y Ajustes Visuales con el Celular (Fase 5)

Podemos probar y ajustar la interfaz desde tu celular con **Android 17**:

1. **Vía Cable USB con `scrcpy` y `adb` (Flujo Recomendado de Alta Velocidad)**:
   * Activar *Depuración por USB* en el celular y conectar por cable.
   * Instalar `scrcpy` en la máquina: `sudo apt update && sudo apt install -y scrcpy`.
   * Habilitar el túnel inverso al backend local: `adb reverse tcp:3000 tcp:3000`.
   * Proyectar la pantalla en vivo: `scrcpy`.
   * Instalar el APK compilado: `adb install -r app/build/outputs/apk/debug/app-debug.apk`.
   * Cada ajuste visual en XML o Kotlin se refleja en ~4 segundos con `./gradlew installDebug`.
2. **Vía Wi-Fi / Descarga Directa del `.apk`**:
   * Copiar el `.apk` al celular, instalarlo y apuntar la URL del servidor a la IP local del computador (`http://192.168.x.x:3000`).

