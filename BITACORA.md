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
* **Java Runtime en Máquina Host**: `OpenJDK 21.0.12` (`/usr/bin/java`) -> **Listo**.
* **Android Debug Bridge (ADB)**: `v1.0.41 (34.0.4)` (`/usr/lib/android-sdk/platform-tools/adb`) -> **Listo**.
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
| **Fase 5** | **Interfaz UI, Compilación `.apk` y Prueba en Vivo**: Pantalla de estado con ViewBinding, modal de enrolamiento con PIN `2026`, gestión de permisos escalonados y compilador Gradle listo. | `MainActivity.kt`, `activity_main.xml`, `dialog_admin_config.xml` | ✅ **Completado (Local)** | — |

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

---

## 🧪 4. Guía Rápida para Pruebas con el Celular (Fase 5)

Cuando lleguemos a la Fase 5, podremos probar de dos maneras desde tu celular con **Android 17**:

1. **Vía Cable USB con `adb` (Más rápido)**:
   * Activar *Depuración por USB* en el celular.
   * Ejecutar `adb devices` y `adb install -r app/build/outputs/apk/debug/app-debug.apk`.
   * Incluso podemos usar `adb reverse tcp:3000 tcp:3000` para que tu celular acceda a `http://localhost:3000` directamente por el cable USB sin importar en qué red Wi-Fi esté.
2. **Vía Wi-Fi / Descarga Directa del `.apk`**:
   * Copiar el `.apk` al celular, instalarlo y apuntar la URL del servidor a la IP local del computador (`http://192.168.x.x:3000`).

