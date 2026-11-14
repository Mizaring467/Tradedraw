# R25 · W02 — VENTANA INVALIDADA POR CAÍDA DE RED + INCIDENTE DE REARME DESATENDIDO

**Fecha:** 2026-10-27 00:25–00:50 (America/Bogota) · **Build:** `692d6f3` v19
**Dispositivo:** Redmi A1 `220733SL` · serial `192.168.1.196:5555` · batería 72 % al abrir

## 1. Qué pasó

- La ventana corrió completa: **109 muestras**, 8 taps, **2 trades**, 75 decisiones, balance 36.883.737,60 → **36.865.735,68** (DD 0,49 %), 2.838 ticks.
- A las ~00:44 el teléfono **perdió la Wi-Fi** (`adb` pasó a `No route to host`; `adb shell` colgado). No fue la app: el PID 24492 sobrevivió y al volver la red el bridge respondió con normalidad.
- El cierre seguro **no pudo ordenar `DISABLED`** ("No se pudo ordenar DISABLED al bridge") porque el host no tenía ruta al teléfono.
- Durante ~5 min el motor quedó **`AUTONOMOUS` sin supervisión**. Al reconectar: `mode=AUTONOMOUS`, `orderState=NINGUNA`, `hasPendingTrade=null`, **balance sin cambios** (0 trades en el hueco) → rearme contenido, sin impacto.

## 2. Clasificación según el preregistro

`PREREGISTRO_R25.md` §3: la ventana es **INVÁLIDA por arnés** (red/transporte ausente > 30 s) y **no consume cupo** de las 3 ventanas válidas. No se usa como dato de V1–V5.

## 3. Lección operativa (no de estrategia)

El watchdog repone el `adb forward`, pero **no puede reponer una Wi-Fi caída**, y el modo `AUTONOMOUS` **no caduca**: si el teléfono se queda sin red o sin pantalla, el motor sigue armado y fuera de observación. Es exactamente el patrón del Incidente 2 de R24 (3 h 15 min de exposición), aquí contenido por suerte y por estar en DEMO.

**Medidas para la siguiente ventana (arnés, no estrategia):**
1. Mantener la pantalla despierta durante la ventana con `input keyevent KEYCODE_WAKEUP` periódico desde el host (sin cambiar ajustes del teléfono).
2. Watchdog ampliado: detectar `adb get-state != device`, reintentar `adb connect` y **registrar el hueco** con marcas de tiempo para invalidar la ventana automáticamente.
3. Pendiente de código (decisión 6 de R24, hoy sin implementar): **caducidad de `AUTONOMOUS`** si no hay heartbeat del bridge en N minutos.

## 4. Estado final dejado

`mode=DISABLED` (ordenado por bridge a las 00:50), `orderState=NINGUNA`, `hasPendingTrade=null`, `isDemoAccount=true`, `balance=36.865.735,68`. Cero órdenes y cero COP expuestos durante el hueco.
