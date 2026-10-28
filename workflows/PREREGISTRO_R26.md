# PREREGISTRO R26 — CERTIFICACIÓN EN VIVO DE LA CADUCIDAD DE `AUTONOMOUS`

**Fecha de fijación:** 2026-10-27 22:20 (America/Bogota) — escrito **antes** de abrir cualquier ventana de R26
**Rama:** `r10-night` · **Commit de código bajo medición:** `f491ca4`
**Build:** `f491ca4` — versionCode **20**, versionName `1.10-r26-autonomous-expiry`, buildId `R26-AUTONOMOUS-EXPIRY`
**APK:** `app/build/outputs/apk/debug/app-debug.apk` · SHA-256 en el reporte de cierre (se calcula al instalar)
**Dispositivo:** Redmi A1 `220733SL`, Android 12, serial ADB `192.168.1.196:5555`
**Redactado por:** DSH (sesión visible)
**Estado:** VIGENTE

---

## 0. REGLA DE ORO

Este documento se fija antes de observar un solo dato nuevo de R26. Cambiar umbrales, predicciones o
duración después de la primera medición invalida el ciclo completo. **La estrategia no se toca**:
R26 solo certifica un mecanismo de seguridad de arranque/cierre, no evalúa edge.

**Restricción de alcance:** ninguna cifra de win rate, EV o drawdown se publica como resultado de R26.

---

## 1. OBJETIVO

> **CERTIFICAR QUE `AUTONOMOUS` ARMADO DESDE EL BRIDGE CADUCA SOLO CUANDO DEJA DE HABER CONTACTO, Y QUE NO CADUCA CUANDO SÍ LO HAY.**

Cierra la decisión 6 de R24, pendiente desde el 2026-10-22 y reabierta por el incidente W02 de R25
(`loops/r25_validation/W02_INCIDENTE_RED.md` §3.3): el motor quedó armado ~5 min sin red ni sondeo.
El Incidente 2 de R24 dejó 3 h 15 min de exposición con 38 trades fuera de ventana.

**Mecanismo bajo prueba (código `f491ca4`):**

| Pieza | Comportamiento |
| :--- | :--- |
| `AutonomousWatchdog` | Política pura. Solo caduca si `armedByBridge = true`. |
| `TradingEngine.setTradingMode` | Arma con `source.startsWith("bridge:")`; desarma al salir de `AUTONOMOUS`. |
| `TradingEngine` tick | Cada 30 s, si el silencio ≥ `AUTONOMOUS_HEARTBEAT_TIMEOUT_MS` (10 min) → `DISABLED` con `source=engine:autonomous-expiry`. |
| `TradeDrawHttpBridge` | Toda petición renueva el heartbeat. |

**Umbral congelado:** 10 min. Justificación a priori: el preregistro R25 exige sondeo de `/status` cada
≤ 2 min; 10 min son 5× esa cadencia. Un sondeo correcto no puede caducar por error, y los dos
incidentes conocidos (~5 min y 3 h 15 min) quedan cubiertos.

---

## 2. PREDICCIONES FALSABLES

### P1 · Control positivo (caducidad)

Armado por bridge, **cero peticiones** durante 12 min:

- Al final: `mode == DISABLED`.
- `/mode_log` contiene un `MODE_CHANGE` con `source=engine:autonomous-expiry` y `de=AUTONOMOUS a=DISABLED`.
- El instante del `MODE_CHANGE` menos el instante de armado está entre **10 min y 11 min** (el tick es de 30 s; se tolera 1 min de retardo de doze/handler en este dispositivo).
- `orderState == NINGUNA`, `hasPendingTrade` nulo o `false`.
- **Falsación:** si a los 12 min sigue `AUTONOMOUS` → FAIL del mecanismo. Si caduca antes de 10 min → FAIL por umbral mal aplicado.

### P2 · Control negativo (no caduca con contacto)

Armado por bridge, heartbeat cada **2 min exactos** durante **11 min** (6 peticiones):

- En la última lectura: `mode == AUTONOMOUS`.
- `/mode_log` **no** contiene ningún `engine:autonomous-expiry` en ese intervalo.
- **Falsación:** si aparece el evento, la caducidad mide tiempo desde el armado y no desde el último contacto → FAIL.

### P3 · No regresión del armado por HUD

No se prueba en vivo en R26 (requiere 12 min adicionales y no hay incidente asociado). Se cubre con
`AutonomousWatchdogTest.armadoDesdeElHudNoCaducaNunca` (JVM). Se declara como límite de la evidencia.

### P4 · Trade en curso al caducar

Si en el momento de la caducidad hay una orden pendiente, el motor **se desarma igual** (el objetivo es
cortar exposición; no se espera a la liquidación). La orden pendiente queda para liquidación manual o
automática posterior y **no** vuelve a armar el motor. Se reporta si ocurre; no invalida la ventana.

---

## 3. PROTOCOLO DE INYECCIÓN DE FALLO

### 3.1 Condiciones de arranque (todas obligatorias)

| # | Condición | Comprobación |
| :--- | :--- | :--- |
| 1 | Dispositivo `device` | `adb -s 192.168.1.196:5555 get-state` |
| 2 | Reenvío `tcp:8080` presente | `adb forward --list` |
| 3 | `/status` responde con `GIT_SHA == f491ca4` y `BUILD_ID == R26-AUTONOMOUS-EXPIRY` | payload |
| 4 | `isDemoAccount == true` | payload |
| 5 | `mode == DISABLED` antes de armar | payload |
| 6 | Binomo (`com.marketly.trading`) en primer plano (los taps, si los hubiera, son DEMO) | `dumpsys window \| grep mCurrentFocus` |
| 7 | Batería ≥ 25 % | `dumpsys battery` |
| 8 | Pantalla encendida durante toda la prueba | `input keyevent WAKEUP` cada ≤ 60 s desde el host (no cambia ajustes) |

### 3.2 P1 — procedimiento

1. `GET /status` → registrar `balance`, `wins`, `losses`, `mode`, `orderState`, `hasPendingTrade`, hora local T0.
2. `GET /mode?name=AUTONOMOUS` (esta petición es el último heartbeat).
3. **Silencio total 12 min.** Prohibido cualquier `curl` al bridge; el host solo ejecuta `WAKEUP` por ADB (no toca el bridge).
4. A los 12 min: `GET /status` → registrar todo; `GET /mode_log` → buscar el evento.
5. Verificar P1 y hacer `GET /mode?name=DISABLED` de cierre.

### 3.3 P2 — procedimiento

1. `GET /mode?name=AUTONOMOUS`; registrar T0.
2. Heartbeat cada 2 min con `GET /ping` (no `/status`, para no contaminar contadores) durante 11 min → 6 peticiones en T0+2, +4, +6, +8, +10, +11.
3. A los 11 min: `GET /status` → debe seguir `AUTONOMOUS`; `GET /mode_log` → sin `engine:autonomous-expiry`.
4. `GET /mode?name=DISABLED` de cierre.

### 3.4 Artefactos

`loops/r26_validation/`:
`P1_status_samples.jsonl`, `P1_mode_log.json`, `P2_status_samples.jsonl`, `P2_mode_log.json`,
`device_metadata.json`, `R26_REPORTE.md`.

---

## 4. V5 — SIGUE FUERA DE ALCANCE

R25 cerró con **V5 NO EVALUABLE** por muestra (2 trades live; mínimo preregistrado ≥ 20). R26 **no**
acumula muestra para V5: sus ventanas son de seguridad y no buscan trades.

Aritmética declarada (no una promesa): a la tasa observada en R25 (~2 trades por ventana de 20 min),
evaluar V5 exige ≥ 10 ventanas válidas. Eso es un cubo propio (R27) con su preregistro, no una
extensión de R26. Cualquier trade que ocurra durante P1/P2 es **incidental** y queda etiquetado
`FUERA DE ALCANCE` en el reporte.

---

## 5. CRITERIO DE CIERRE

| Resultado | Condición |
| :--- | :--- |
| `PASS-CADUCIDAD` | P1 y P2 cumplen todas sus predicciones, con P1 dentro de la ventana 10–11 min |
| `FAIL-CADUCIDAD` | P1 no caduca, caduca antes de 10 min, o P2 caduca con heartbeat |
| `NO EVALUABLE` | fallo de arnés (dispositivo, bridge, batería, foco) que impida completar P1 o P2 |

Un `FAIL` **no** se corrige en R26: se documenta, se deja el modo en `DISABLED` y la corrección abre R27.

---

## 6. SEGURIDAD DURANTE LA PRUEBA

1. DEMO obligatoria; si se observa `isDemoAccount == false` → `DISABLED` y abortar.
2. La prueba se hace con Binomo en primer plano: si el motor dispara una orden, es una orden DEMO.
3. Batería < 20 % → abortar y documentar.
4. Cierre siempre con `GET /mode?name=DISABLED`; si falla, reintentar y marcarlo en el reporte.
5. Cualquier `MODE_CHANGE` con `source` distinto de `bridge(/mode)` o `engine:autonomous-expiry` se
   reporta como incidente y **sí** invalida la prueba.

---

## 7. ESTADO DE PARTIDA

`mode=DISABLED`, `orderState=NINGUNA`, `isDemoAccount=true`, balance congelado de R25
**36.865.735,68 COP**, build instalada anterior `692d6f3` (v19). La build `f491ca4` (v20) se instala
para esta prueba.
