# 📋 Guía de Uso, Instalación y Pruebas de QA — Agente de Telemetría Móvil
**Flota La Macarena S.A. — Ecosistema de Monitoreo de Activos y Geocercas**

Esta guía está diseñada para el **Equipo de QA, Administradores de TI e Interesados (Stakeholders)**. Describe paso a paso cómo instalar, configurar, validar y certificar el Agente Móvil de Telemetría tanto en terminales POS (**Ciontek CS30**, **Topwise T6D**) como en smartphones corporativos (**Android 11 a Android 17**).

---

## 🎯 1. Resumen Ejecutivo y Propósito del Agente

El **Agente de Telemetría** es un servicio corporativo ultra-ligero (< 3.5 MB de tamaño, < 25 MB de consumo de RAM) que opera de forma autónoma y silenciosa en los dispositivos de taquilla y venta.
* **Misión**: Transmitir periódicamente al backend central (`inventory-node`) la ubicación satelital/red y la dirección IP del activo para verificar el cumplimiento de su **Geocerca Autorizada**.
* **Protección contra Fraude**: Si un activo sale de su agencia asignada o se intenta falsificar la ubicación (*Mock GPS*), el sistema dispara alertas automáticas por **WhatsApp (Wasapi)** y **Correo Electrónico**.

---

## 📲 2. Métodos de Instalación del Archivo `.apk`

Se generan dos variantes del instalador:
* `app-debug.apk`: Para pruebas de laboratorio y desarrollo (permite servidores locales `http://192.168.x.x`).
* `app-release.apk`: Para despliegue final en producción (forzado a HTTPS).

### Método A: Instalación por Cable USB con ADB (Recomendado para QA)
1. Conecta el dispositivo al computador mediante cable USB.
2. Asegúrate de tener habilitada la **Depuración por USB**:
   * *Ajustes* -> *Acerca del teléfono* -> Toca 7 veces consecutivas sobre *Número de compilación*.
   * *Ajustes* -> *Sistema / Opciones de Desarrollador* -> Activa **Depuración por USB**.
3. Abre una terminal y verifica el enganche:
   ```bash
   adb devices
   ```
4. Instala el APK reemplazando versiones previas sin perder datos:
   ```bash
   adb install -r app-debug.apk
   ```

### Método B: Instalación Directa en el Dispositivo (Sin Computador)
1. Envía el archivo `app-debug.apk` al dispositivo vía WhatsApp Web, Telegram, correo o memoria USB.
2. Abre el explorador de archivos del celular o terminal POS y toca el archivo.
3. Si el sistema solicita permiso para *"Instalar aplicaciones de fuentes desconocidas"*, concédelo y presiona **Instalar**.

---

## 🖥️ 3. Visualización y Control en Monitor con `scrcpy` (Iteración Rápida de UI)

Para facilitar el desarrollo, los ajustes visuales de la interfaz y la realización de pruebas de QA sin necesidad de manipular físicamente el teléfono, se utiliza **`scrcpy`** (Screen Copy).

### ¿Cómo funciona?
* `scrcpy` se ejecuta **exclusivamente en tu computador**; en el teléfono **no se instala ninguna aplicación**.
* Abre una ventana en tu monitor que proyecta la pantalla de tu celular en tiempo real (60 FPS, baja latencia) y te permite operar la aplicación usando el ratón y el teclado de tu equipo.

### Paso 1: Instalación de `scrcpy` en Linux (Ubuntu)
Abre tu terminal en el computador y ejecuta:
```bash
sudo apt update && sudo apt install -y scrcpy
```
*(O vía snap si tu distribución lo prefiere: `sudo snap install scrcpy`)*.

### Paso 2: Conectar el teléfono y proyectar pantalla
1. Conecta el celular por cable USB con **Depuración por USB** activada.
2. Habilita el túnel inverso para que el celular alcance el backend local:
   ```bash
   adb reverse tcp:3000 tcp:3000
   ```
3. Lanza la proyección en tu monitor:
   ```bash
   scrcpy
   ```
   *(Opciones útiles: `scrcpy --always-on-top` para mantener la ventana visible mientras editas código, o `scrcpy -m 1024` para optimizar ancho de banda).*

### Paso 3: Ciclo de Ajuste Visual en Vivo (3 a 5 segundos)
Cuando se realicen cambios visuales en [`activity_main.xml`](file:///home/tecnologia17/desarrollo/inventory-telemetry-agent/app/src/main/res/layout/activity_main.xml), colores o tipografías:
```bash
./gradlew installDebug
```
Al ser una compilación incremental de recursos, la app se actualiza en el dispositivo en pocos segundos y verás el nuevo diseño reflejado directamente en la ventana de tu pantalla.

---

## ⚙️ 4. Protocolo de Enrolamiento y Configuración Inicial

Al abrir la aplicación por primera vez, el agente se encuentra en **Modo Solo Lectura**:

```text
┌───────────────────────────────────────────────┐
│       TELEMETRÍA FLOTA LA MACARENA            │
│       Estado: No Enrolado / Desconectado      │
│                                               │
│  Placa: [ NO REGISTRADA ]                     │
│  Servidor: http://10.0.2.2:3000               │
│  Último Latido: Nunca                         │
│                                               │
│  [ SINCRONIZAR AHORA ]  [ CONFIGURACIÓN TI ]  │
└───────────────────────────────────────────────┘
```

### Paso a Paso para Enrolar el Dispositivo:
1. Toca el botón **Configuración TI**.
2. Aparecerá un teclado numérico de seguridad: ingresa el **PIN Administrativo de TI**:
   > 🔐 **PIN Oficial de TI**: `2026`
3. Al ingresar el PIN correcto, se desbloquea el formulario de enrolamiento. Diligencia los siguientes campos:
   * **Placa / Serial del Activo**: Identificador único del equipo (ej: `POS-BOG-01`, `POS-VILLAVO-02`, `CEL-SUPERVISOR-01`).
   * **URL del Servidor Receptor**: 
     * Para pruebas locales: `http://192.168.1.100:3000` (IP de tu máquina).
     * Para producción: `https://inventario.flotalamacarena.com`
   * **API Key de Telemetría**: Cabecera `X-Device-Api-Key` configurada en el servidor.
   * **Intervalo de Latido**: Por defecto `15` minutos (mínimo `5`, máximo `120`).
4. Presiona **Guardar y Enrolar**.
5. **Permisos Críticos del Sistema Operativo** (Aparecerán automáticamente):
   * **Ubicación**: Selecciona **"Permitir todo el tiempo"** (*Allow all the time*).
   * **Optimización de Batería**: Selecciona **"Permitir"** en la exención de ahorro de batería para evitar que el sistema duerma el agente.

---

## 🧪 5. Matriz de Casos de Prueba para QA

| ID Caso | Escenario de Prueba | Procedimiento | Resultado Esperado | Criterio de Éxito |
| :---: | :--- | :--- | :--- | :---: |
| **CP-01** | **Seguridad por PIN** | Intentar ingresar al panel administrativo con PIN erróneo (`1234`, `0000`, vacío). Luego ingresar con `2026`. | Deniega el acceso en intentos fallidos. Con `2026` abre el modal de edición. | ✅ Bloqueo efectivo contra operarios de taquilla. |
| **CP-02** | **Sincronización Manual** | Presionar el botón *"Sincronizar Ahora"*. | El indicador cambia a *"Enviando latido..."* y en 1-2 segundos confirma *"Latido recibido exitosamente (200 OK)"*. | ✅ Las coordenadas se reflejan en el backend. |
| **CP-03** | **Reporte Silencioso en Fondo** | Bloquear la pantalla del dispositivo durante 15 minutos sin abrir la app. | El demonio `WorkManager` emite el latido periódicamente sin encender la pantalla ni interrumpir al operario. | ✅ Latido visible en la base de datos de telemetría. |
| **CP-04** | **Arranque en Frío tras Reinicio** | Apagar completamente el equipo y volver a encenderlo. | El `BootReceiver` intercepta `BOOT_COMPLETED` y programa las tareas de fondo de inmediato. | ✅ No se requiere abrir la aplicación tras encender la terminal. |
| **CP-05** | **Permanencia en Geocerca** | Dispositivo ubicado dentro del radio de su sede asignada (ej: Terminal Salitre). | El backend responde estado `EN_GEOCERCA`. | ✅ No se disparan alertas de controversia. |
| **CP-06** | **Desviación / Fuga de Geocerca** | Mover el dispositivo fuera del radio permitido o asignar placa asociada a otra sede. | El backend detecta la salida y detona: 1) Registro en bitácora de controversias, 2) Mensaje de WhatsApp Wasapi con mapa, 3) Correo a auditoría. | ✅ Alerta inmediata recibida por los supervisores. |
| **CP-07** | **Compatibilidad AOSP (Sin GMS)** | Instalar y probar en una terminal **Topwise T6D** o **Ciontek CS30** sin servicios de Google. | El `HybridLocationProvider` conmuta limpiamente a `LocationManager` nativo y obtiene coordenadas satelitales directas. | ✅ Cero cierres forzosos (ANR / Crash). |
| **CP-08** | **Detección de Mock GPS** | Activar una app de "Fake GPS" en el celular de prueba y simular una coordenada. | El agente detecta `location.isMock == true` y lo reporta en los metadatos de auditoría. | ✅ Identificación de intento de suplantación. |

---

## 🛠️ 6. Comandos Útiles de Diagnóstico y Auditoría

Si durante las pruebas requieres monitorear en tiempo real la actividad interna del agente:

### Ver los logs del agente en vivo:
```bash
adb logcat -s TelemetryAgent
```

### Forzar la ejecución inmediata del WorkManager (Sin esperar los 15 minutos):
```bash
adb shell cmd jobscheduler run -f com.flotalamacarena.telemetry 1
```

### Inspeccionar la base de datos de preferencias cifradas:
```bash
adb shell "run-as com.flotalamacarena.telemetry ls -la shared_prefs/"
```

---

## 👥 7. Responsables y Contacto Técnico
* **Área Responsable**: Tecnología y Sistemas de Información — Flota La Macarena S.A.
* **Documentación de Arquitectura**: [`PLAN_ARQUITECTURA.md`](file:///home/tecnologia17/desarrollo/inventory-telemetry-agent/PLAN_ARQUITECTURA.md)
* **Bitácora de Hitos**: [`BITACORA.md`](file:///home/tecnologia17/desarrollo/inventory-telemetry-agent/BITACORA.md)
