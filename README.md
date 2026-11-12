# 📱 inventory-telemetry-agent
> **Agente Móvil de Telemetría y Geocercas en Tiempo Real**  
> *Solución modular e independiente de monitoreo y auditoría geográfica de activos y terminales POS*

---

## 📌 Descripción General

**`inventory-telemetry-agent`** es un servicio móvil y agente de software en segundo plano ultra-ligero desarrollado en **Kotlin** para el sistema operativo Android. Su propósito principal es reportar de forma periódica, autónoma y silenciosa la ubicación satelital/red y el estado operativo de activos corporativos y terminales POS (**Ciontek CS30**, **Topwise T6D**, smartphones Android 11+ a Android 17) hacia backends de gestión de inventarios y auditoría geográfica (`cross_Inventory` / `inventory-node`).

---

## 🏛️ Arquitectura y Pila Tecnológica

El agente implementa **Arquitectura Limpia (Hexagonal)** desacoplada:

* **Lenguaje:** Kotlin 2.x
* **Compatibilidad Android:** `minSdk 29/30` (Android 10/11) a `targetSdk 35/36` (Android 15-17).
* **Motor de Segundo Plano:** Android Jetpack `WorkManager` con restricciones de conectividad y reintento exponencial.
* **Arranque Autónomo:** `BroadcastReceiver` (`BOOT_COMPLETED`, `LOCKED_BOOT_COMPLETED`).
* **Proveedor de Ubicación:** Híbrido (`LocationManager` GPS + Red celular/Wi-Fi) con detector **Anti-Mock GPS**.
* **Detección Activa de Sabotaje (Anti-Tampering):** Detección inmediata si un operario revoca permisos de ubicación o apaga el GPS, emitiendo un latido de emergencia (`tamperingAlert`) con las últimas coordenadas válidas e IP hacia el backend.
* **Camuflaje Visual Corporativo (`Servicios FLM`):** Diseño visual que emula un portal corporativo institucional, respuestas estándar de inicio de sesión (*"Usuario o contraseña incorrectos"*) y modal creíble de recuperación de credenciales hacia Mesa de Ayuda.
* **Autenticación Administrativa Híbrida:** Validación dinámica en línea contra el backend central (`POST /api/telemetry/admin-auth`) con la clave técnica activa gobernada desde el portal web de Inventarios, complementada con respaldo fuera de línea (*Offline Fallback*) mediante hash criptográfico SHA-256 + Salt (`FLM#Admin2026`) y protección contra fuerza bruta (bloqueo estricto de 300s tras 3 fallos).
* **Consumo de Red y Recursos:** Huella de memoria mínima (< 25 MB RAM), binario ultra-ligero y payloads HTTP de ~250 bytes.

---

## 🎨 Diseño Modular y Portabilidad (White-Label)

El agente está concebido bajo una arquitectura modular y desacoplada que facilita su reutilización y adaptación en múltiples organizaciones y ecosistemas:
* **Desacoplamiento Gráfico**: La estructura del proyecto separa la lógica de telemetría y seguridad de la capa de presentación.
* **Roadmap Marca Blanca**: En fases posteriores se migrarán los elementos visuales y logotipos a un esquema de tematización dinámica (*White-Label*), permitiendo compilar o parametrizar identidades corporativas genéricas o personalizadas para cualquier cliente o caso de uso sin tocar el núcleo funcional.

---

## 📁 Estructura del Proyecto

```text
inventory-telemetry-agent/
├── app/
│   └── src/main/
│       ├── java/com/flotalamacarena/telemetry/
│       │   ├── domain/           # Entidades, modelos y contratos puros (100% Kotlin sin android.*)
│       │   ├── application/      # Casos de uso de latido (SendHeartbeat) y validación híbrida (ValidateAdminPin)
│       │   ├── infrastructure/   # WorkManager, GPS híbrido, cliente HTTP, almacenamiento seguro (Keystore)
│       │   └── presentation/     # UI camuflada (Servicios FLM), diálogo de enrolamiento y recuperación
│       └── AndroidManifest.xml
├── AGENTS.md                     # Perfiles, responsabilidades y reglas de oro del equipo de desarrollo
├── BITACORA.md                   # Bitácora detallada de hitos, ADRs y pruebas de campo en vivo
├── GUIA_QA_PRUEBAS.md            # Guía para QA, comandos ADB, inspección scrcpy y matriz de pruebas
├── MANUAL_USUARIO.md             # Manual de usuario, enrolamiento, permisos y operación técnica
├── PLAN_ARQUITECTURA.md          # Especificación técnica, arquitectura hexagonal y plan de fases
└── README.md
```

---

## 🔗 Integración con el Ecosistema Central

Este repositorio opera de forma independiente para mantener aislado el entorno Android/Gradle del monorepo web/backend, pero mantiene sincronización con:
* **Backend:** `POST /api/telemetry/heartbeat` autenticado con cabecera `X-Device-Api-Key`.
* **Alertas Wasapi (WhatsApp) / Correo:** Plantillas de advertencia inmediata ante desviaciones fuera de geocerca o eventos de sabotaje.
* **Documentación Espejo:** [`AGENTE_TELEMETRIA_MOVIL.md`](file:///home/tecnologia17/desarrollo/cross_Inventory/AGENTE_TELEMETRIA_MOVIL.md).

---

## 📄 Autoría y Licencia

* **Autor:** **Mauricio Parra**
* **Tipo de Software:** Solución de software independiente y modular para telemetría móvil, auditoría de geocercas y seguridad anti-manipulación.
* Todos los derechos de autor intelectual reservados.
