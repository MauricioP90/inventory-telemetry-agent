# 📱 inventory-telemetry-agent
> **Agente Móvil de Telemetría y Geocercas en Tiempo Real**  
> *Flota La Macarena S.A. — Sistema Integrado de Gestión de Inventario y Activos*

---

## 📌 Descripción General

**`inventory-telemetry-agent`** es un demonio móvil corporativo y ultra-ligero desarrollado en **Kotlin** para el sistema operativo Android. Su propósito principal es reportar de forma periódica, autónoma y silenciosa la ubicación satelital/red y el estado operativo de los activos de venta y terminales POS (**Ciontek CS30**, smartphones Android 11+ a Android 17) hacia el backend central de inventario (`cross_Inventory` / `inventory-node`).

---

## 🏛️ Arquitectura y Pila Tecnológica

El agente implementa **Arquitectura Limpia (Hexagonal)** desacoplada:

* **Lenguaje:** Kotlin 2.x
* **Compatibilidad Android:** `minSdk 29/30` (Android 10/11) a `targetSdk 35/36` (Android 15-17).
* **Motor de Segundo Plano:** Android Jetpack `WorkManager` con restricciones de conectividad y reintento exponencial.
* **Arranque Autónomo:** `BroadcastReceiver` (`BOOT_COMPLETED`, `LOCKED_BOOT_COMPLETED`).
* **Proveedor de Ubicación:** Híbrido (`LocationManager` GPS + Red celular/Wi-Fi) con detector **Anti-Mock GPS**.
* **Seguridad:** Cifrado de credenciales (`EncryptedSharedPreferences` / Hardware Keystore) y pantalla de configuración protegida por **PIN Administrativo de TI**.
* **Consumo de Red y Recursos:** Huella de memoria mínima y payloads HTTP ultraligeros (~250 bytes).

---

## 📁 Estructura del Proyecto

```text
inventory-telemetry-agent/
├── app/
│   └── src/main/
│       ├── java/com/flotalamacarena/telemetry/
│       │   ├── domain/           # Entidades y contratos puros (sin android.*)
│       │   ├── application/      # Casos de uso de latido y autenticación de PIN
│       │   ├── infrastructure/   # WorkManager, GPS híbrido, cliente HTTP, Keystore
│       │   └── presentation/     # UI de diagnóstico y modal de enrolamiento
│       └── AndroidManifest.xml
├── AGENTS.md                     # Perfiles y reglas de los agentes de desarrollo
├── BITACORA.md                   # Seguimiento de hitos y pruebas de campo
├── PLAN_ARQUITECTURA.md          # Especificación técnica y diseño detallado
└── README.md
```

---

## 🔗 Integración con el Ecosistema Central

Este repositorio opera de forma independiente para mantener aislado el entorno Android/Gradle del monorepo web/backend, pero mantiene sincronización con:
* **Backend:** `POST /api/telemetry/heartbeat` autenticado con cabecera `X-Device-Api-Key`.
* **Alertas Wasapi (WhatsApp) / Correo:** Plantillas de advertencia inmediata ante desviaciones fuera de geocerca.
* **Documentación Espejo:** [`AGENTE_TELEMETRIA_MOVIL.md`](file:///home/tecnologia17/desarrollo/cross_Inventory/AGENTE_TELEMETRIA_MOVIL.md).

---

## 📄 Licencia y Confidencialidad
Propiedad exclusiva de **Flota La Macarena S.A.** — Uso corporativo e interno.
