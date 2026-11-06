# 👥 Perfiles y Roles de Agentes — Proyecto `inventory-telemetry-agent`
**Flota La Macarena S.A. — Ecosistema de Telemetría y Geocercas**

Este documento define los **perfiles especializados, responsabilidades y reglas de colaboración** que guían el desarrollo, auditoría y pruebas del Agente Móvil de Telemetría.

---

## 🎯 1. Roles Especializados del Proyecto

### 🏛️ Rol 1: Arquitecto Mobile & Clean Architecture (`Mobile Architect`)
* **Misión**: Garantizar que el agente móvil mantenga una arquitectura limpia, modular, desacoplada y ultra-ligera.
* **Responsabilidades**:
  1. Velar por la separación estricta de las 4 capas (`domain`, `application`, `infrastructure`, `presentation`).
  2. Asegurar que la capa `domain/` sea 100% Kotlin puro sin importar clases de `android.*`.
  3. Mantener el tamaño del `.apk` final por debajo de **3.5 MB** y el consumo de memoria RAM al mínimo para no afectar la operación de venta de tiquetes en las terminales POS **Ciontek CS30**.
  4. Evitar librerías pesadas o innecesarias.

---

### 🤖 Rol 2: Especialista en Sistema Operativo Android & Daemons (`Android OS & Background Specialist`)
* **Misión**: Asegurar que el agente funcione sin bloqueos desde **Android 11+ (API 30)** en terminales POS hasta **Android 17 (API 36+)** en smartphones modernos.
* **Responsabilidades**:
  1. Implementar correctamente la captura de eventos de arranque del sistema (`ACTION_BOOT_COMPLETED` y `LOCKED_BOOT_COMPLETED`).
  2. Orquestar `WorkManager` con restricciones de conectividad (`NetworkType.CONNECTED`) y políticas de reintento (`BackoffPolicy.EXPONENTIAL`) para tolerar demoras en el enganche de la SIM Card 4G o Wi-Fi al encender el equipo.
  3. Gestionar el flujo escalonado de permisos exigido por Android 11–17:
     * Paso 1: `ACCESS_FINE_LOCATION` + `ACCESS_COARSE_LOCATION`.
     * Paso 2: `ACCESS_BACKGROUND_LOCATION` (*"Permitir todo el tiempo"*).
     * Paso 3: Exención de optimización de batería (`ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`) para evitar que `Doze Mode` congele los reportes periódicos.
  4. Implementar el proveedor de ubicación híbrido (GPS Satelital + Red/Celdas/Wi-Fi) para garantizar coordenadas válidas incluso dentro de taquillas techadas.

---

### 🔐 Rol 3: Ingeniero de Seguridad, MDM & Anti-Tampering (`Mobile Security & MDM Engineer`)
* **Misión**: Blindar la configuración del agente para que ningún operario no autorizado pueda alterar la placa, apagar el monitoreo o falsificar su ubicación.
* **Responsabilidades**:
  1. Proteger el panel de enrolamiento (Placa del Activo, URL del Backend, API Key) mediante un **PIN Administrativo de TI**.
  2. Almacenar las credenciales (`X-Device-Api-Key`) y parámetros de enrolamiento de forma segura y persistente.
  3. Implementar la detección de ubicaciones simuladas (*Anti-Mock GPS*) verificando `location.isFromMockProvider` y `location.isMock`.
  4. Configurar `network_security_config.xml` para permitir comunicación segura con el servidor y habilitar pruebas controladas en red local durante la fase de certificación.

---

### 🔌 Rol 4: Ingeniero de Integración Backend & QA de Campo (`Telemetry Integration & QA Specialist`)
* **Misión**: Certificar la comunicación de extremo a extremo entre el dispositivo móvil, el backend `inventory-node`, el portal web `inventory-angular` y las alertas de WhatsApp (Wasapi) / Correo.
* **Responsabilidades**:
  1. Mantener la fidelidad absoluta con el contrato del endpoint `POST /api/telemetry/heartbeat` definido en [`TelemetryController.ts`](file:///home/tecnologia17/desarrollo/cross_Inventory/inventory-node/src/infrastructure/http/controllers/TelemetryController.ts) y [`TelemetryRoutes.ts`](file:///home/tecnologia17/desarrollo/cross_Inventory/inventory-node/src/infrastructure/http/routes/TelemetryRoutes.ts).
  2. Guiar las pruebas reales en el celular de prueba (Android 17) y en las terminales POS (Android 11+) tanto en escenario de **Sede Autorizada** (`✅ En Geocerca`) como en escenario de **Desviación / Controversia** (`🚨 Fuera de Geocerca`).
  3. Mantener sincronizados los archivos de seguimiento entre ambos repositorios.

---

## 📜 2. Reglas de Oro para Todos los Agentes

1. **Sincronización Documental Obligatoria**:
   * Ninguna fase o cambio arquitectónico se da por terminado hasta haber actualizado tanto [`BITACORA.md`](file:///home/tecnologia17/desarrollo/inventory-telemetry-agent/BITACORA.md) en este proyecto como [`/home/tecnologia17/desarrollo/cross_Inventory/AGENTE_TELEMETRIA_MOVIL.md`](file:///home/tecnologia17/desarrollo/cross_Inventory/AGENTE_TELEMETRIA_MOVIL.md) en el proyecto principal.
2. **Cero Bloqueos de Hilo Principal (Main Thread)**:
   * Toda lectura de GPS y petición HTTP debe ejecutarse en hilos de fondo (`Dispatchers.IO` / `Worker`), asegurando que la interfaz jamás sufra un *ANR (Application Not Responding)*.
3. **Falla Silenciosa y Resiliente**:
   * Si el dispositivo arranca en una zona sin cobertura celular o el servidor está temporalmente inalcanzable, el agente **nunca debe mostrar carteles de error intrusivos al operario de taquilla**; debe registrar el intento en el log interno de diagnóstico y reprogramar el reintento automático con `WorkManager`.
4. **Compatibilidad Dual Garantizada**:
   * Antes de usar cualquier API de Android, verificar siempre la versión con `Build.VERSION.SDK_INT` cuando exista diferencia entre Android 11 (API 30) y Android 17 (API 36+).

