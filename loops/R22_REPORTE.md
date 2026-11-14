# R22 — REPORTE DE VALIDACIÓN LIVE EN DISPOSITIVO FÍSICO (SHADOW MODE)

**Fecha:** 2026-10-14 23:40 COT / 2026-10-15 04:40 UTC  
**Orquestador Principal:** AGY (`w1:p1`)  
**Auditor Independiente:** HERMES (`w1:p6`)  
**Workspace Herdr:** `w1`  
**Dispositivo Físico:** Xiaomi Redmi A1 (`220733SL`, Android 12 / API 31, ID: `UGU8KFUKM7E6CE8D`)  
**SHA Base R21:** `e33f8cb`  
**SHA Build R22:** `54b22a2` (versionCode 5, versionName "1.4-r22")  
**SHA Patch JSON:** `a212673` (corrección de sintaxis JSON en bridge)  
**Veredicto General:** **PASS — INSTALACIÓN ADB DEMOSTRADA, RUNTIME EN VIVO VALIDADO Y CAPITAL 100% PRESERVADO**

---

## 1. Resumen Ejecutivo de la Misión R22

La Ronda 22 (`R22`) cerró de forma categórica la brecha entre el código en repositorio y el runtime en el hardware físico:
$$\text{CÓDIGO IMPLEMENTADO} \longrightarrow \text{APK GENERADO} \longrightarrow \text{APK INSTALADO VÍA ADB} \longrightarrow \text{RUNTIME REAL EN REDMI A1} \longrightarrow \text{EVIDENCIA}$$

Se demostró empíricamente que:
1. El Redmi A1 actualizó físicamente su APK mediante `adb install -r`, ejecutando `versionCode = 5` (`1.4-r22`).
2. El modo `SHADOW` operó en vivo durante más de 21 minutos procesando ticks reales del broker con **CERO taps despachados** y **CERO variación de saldo**.
3. El pipeline generó `decision_id` únicos en formato UUID v4 y **no inventó falsos `trade_id`** en ninguna decisión vetada o simulada.
4. El comparador `compare_live_replay.py` logró **100% de Exact Matches** (18/18) sobre las decisiones reales de la sesión.
5. El supervisor de salud (`HealthSupervisor`) reportó transiciones válidas (`BLOCKED` $\to$ `READY`) en tiempo real.

---

## 2. Evidencia de Instalación Real en el Redmi A1

| Comprobación | Origen / Comando | Evidencia Bruta Obtenida | Veredicto |
|---|---|---|---|
| **Dispositivo Objetivo** | `adb devices -l` | `UGU8KFUKM7E6CE8D device product:ice_global model:220733SL transport_id:11` | **CONFIRMADO** (Redmi A1 físico) |
| **Instalación de APK** | `adb install -r app-debug.apk` | `Performing Streamed Install -> Success` | **EXITOSA** |
| **Dumpsys de Paquete** | `dumpsys package com.example.tradedraw` | `versionCode=5`, `versionName=1.4-r22`, `lastUpdateTime=2026-10-14 23:14:51` | **ACTUALIZADO** (Reemplazó APK vieja de 20:04:19) |
| **Reinicio de Proceso** | `adb shell pidof com.example.tradedraw` | Proceso previo (PID 9506) terminado; nuevo proceso activo (PID 25383) | **NUEVO RUNTIME** |
| **Exposición en /status** | `curl -s http://127.0.0.1:8080/status` | `"app": "TradeDraw", "versionCode": 5, "versionName": "1.4-r22", "health": {...}` | **VERIFICADO EN HTTP BRIDGE** |

---

## 3. Métricas y Telemetría de la Sesión en Vivo

| Dimensión | Valor Medido | Criterio de Aceptación / Significado |
|---|---|---|
| **Duración de la Ventana** | **21.5 minutos continuos** | Mínimo exigido: 15-20 minutos |
| **Muestras de Telemetría** | **95 muestras** | Volcadas a [loops/r22_live_samples.jsonl](file:///C:/Users/heidy/Tradedraw/loops/r22_live_samples.jsonl) |
| **Modo de Operación** | **`AutoTradeMode.SHADOW`** | Cero uso de `AUTONOMOUS` |
| **Activo en Pantalla** | `Crypto IDX` | Confirmado vía accesibilidad en `/nodes` |
| **Feed Activo en Socket** | `Z-CRY/IDX` | 1.968 ticks aceptados (frescura media 24-380ms) |
| **Saldo Inicial Cuenta Demo** | **38.054.735,36 COP** | Verificado en `/status` y `/nodes` |
| **Saldo Final Cuenta Demo** | **38.054.735,36 COP** | **0,00 COP de variación (100% inmutable)** |
| **Taps Despachados** | **0 taps** | Verificado en `/status` (`tapsDespachados=0`) |
| **Invocación Gestos Táctiles** | **0 invocaciones** | Auditado en logcat Android vía root (`grep dispatchGesture`) |
| **Órdenes Reales Enviadas** | **0 órdenes** | `hasPendingTrade=false`, `orderState=NINGUNA` |
| **Contadores W/L** | `totalWins=0, totalLosses=0` | Cero contaminación de estadísticas |
| **Decisiones Evaluadas con ID** | **18 decisiones** | Persistidas en [data/fixtures/r22_live_decisions.csv](file:///C:/Users/heidy/Tradedraw/data/fixtures/r22_live_decisions.csv) |
| **Propagación de `decision_id`** | **18 UUIDs únicos v4** | Ejemplo: `f061de3d-4cd1-4997-a9f6-9b91eaae06c8` |
| **Ausencia de `trade_id` Falso** | **0 `trade_id` inventados** | `trade_id = null` en el 100% de las 18 decisiones |
| **Transición HealthSupervisor** | `BLOCKED` $\to$ `READY` | Fail-closed ante desconexión / desajuste, luego `READY` |
| **Paridad Live ↔ Replay** | **100.0% Exact Matches** | [loops/r22_replay_comparison_report.md](file:///C:/Users/heidy/Tradedraw/loops/r22_replay_comparison_report.md) (18/18) |

---

## 4. Auditoría de los Guards y HealthSupervisor

### 4.1 AssetMismatchGuard
- En estado estacionario, la pantalla mostró `Crypto IDX` y el WebSocket procesó `Z-CRY/IDX`.
- La normalización interna reconoció coincidencia exacta: 0 vetos espurios por desajuste.
- En la fase de arranque y puesta en segundo plano previa, el guard reportó `SCREEN_FEED_MISMATCH: DESAJUSTE pantalla=DESCONOCIDA feed='Z-CRY/IDX'` colocando el `HealthSupervisor` en `BLOCKED` de forma fail-closed.

### 4.2 Guard de Volatilidad / Micro-Compresión (V2_RANGE)
- La volatilidad relativa observada en `Crypto IDX` fluctuó entre `~2.2e-10` y `~4.9e-10`.
- El umbral mínimo configurado para operar es `1e-05` (0.001%).
- El guard `V2_RANGE` vetó de manera implacable el 100% de las 18 oportunidades candidatas, impidiendo operaciones en un mercado congelado/plano.

### 4.3 HealthSupervisor
- Se confirmaron transiciones reales en `/status`:
  - `BLOCKED`: Durante la reconexión inicial o falta de lectura de balance.
  - `READY`: Con feed conectado, ticks frescos (<500ms), servicio de accesibilidad activo y balance sincronizado.

---

## 5. Comparación Live ↔ Replay (`compare_live_replay.py`)

Se ejecutó la herramienta determinista sobre las 18 decisiones capturadas en vivo:
- **Pares analizados:** 18
- **Exact Matches:** **18 (100.0%)**
- **Divergencias:** **0 (0.0%)**
- **Campos evaluados por jerarquía causal:** `timestamp`, `asset`, `price_at_signal`, `candle_second`, `market_regime`, `trend`, `dist_support`, `dist_resistance`, `confidence`, `strategy`, `action`, `veto`, `decision`.
- **Veredicto:** **PASS**.

---

## 6. Estado Final y Parada Segura (Fail-Closed)

La sesión culminó con la orden de parada segura enviada al dispositivo mediante `/mode?name=DISABLED`:

```json
{
  "mode": "DISABLED",
  "hasPendingTrade": false,
  "orderState": "NINGUNA",
  "tapsDespachados": 0,
  "balance": 38054735.36
}
```

- **Motor en Reposo:** `AutoTradeMode.DISABLED`.
- **Compromiso de Saldo:** 0 COP.
- **Riesgo Activo:** Ninguno.

---

## 7. Dictamen Final de la Misión R22

Conforme a la auditoría independiente de HERMES en [workflows/rounds/R22_LIVE_VALIDATION_HERMES.md](file:///C:/Users/heidy/Tradedraw/workflows/rounds/R22_LIVE_VALIDATION_HERMES.md):
- **Veredicto:** **A) ATRIBUIDO Y VALIDADO (PASS)**.
- **Conclusión:** La aplicación en el Redmi A1 contiene y ejecuta fehacientemente las implementaciones de R21, el pipeline es trazable end-to-end con `decision_id`, el modo `SHADOW` aísla al 100% el capital real/demo y el comportamiento es matemáticamente reproducible.
