# REPORTE DE VALIDACIÓN LIVE — R23B ITERACIÓN 1 (MODO YOLO · CUENTA DEMO)

**Fecha:** 2026-10-16 (UTC)
**Validador:** OPENCODE (`w1:pT`)
**Orquestador:** AGY (`w1:p1`)
**Dispositivo:** Xiaomi Redmi A1 (`220733SL`) · enlace **WiFi** `[fd10:d508:34cf:10:e8c5:80ff:fef0:d5f9]:5555`
**Build:** versionCode=13, versionName=1.4-r23-yolo-iter1, buildId=R23-YOLO-ITER1, gitSha=ba75268
**Submodo:** `YOLO` (reportado en `/status` en el 100 % de las muestras) · **Modo Global:** `AUTONOMOUS`
**Duración de la Ventana:** 20,0 min (116 muestras de telemetría)
**Veredicto:** **KEEP**

---

## 1. FACT (Hechos Directamente Observados)

1. **Seguridad DEMO:** Cuenta DEMO verificada **116 / 116** muestras. Cuentas reales: **0**.
2. **Submodo:** `submode = "YOLO"` en **116 / 116** muestras.
3. **Identidad de Build:** versionCode=13, buildId="R23-YOLO-ITER1", gitSha="ba75268".
4. **Ejecución Autónoma:** **12 taps despachados en la ventana** · **6 operaciones DEMO** registradas.
5. **Dinámica de Balance:** inicial = 38.320.737,28 COP → final = **38.452.736,00 COP** (**Δ +131.998,72 COP**).
6. **Resoluciones:** **W = 4, L = 2** (Win Rate = **66,7 %**).
7. **Motor:** `signalsGenerated` 1 → 61 (Δ 60) · `evaluatedCount` 36 → 266 · supresión por confluencia = 51.
8. **Decisiones:** 61 (`decision_id` determinista). Vetos: **RISK_LIMIT (operación abierta)** 34, **ADAPTIVE_BLOCK** 10, **COOLDOWN** ~11.
9. **Paridad Live ↔ Replay:** 1 par, 0 exact matches (0,0 %) — ver §5.

---

## 2. DECISION PIPELINE

| Métrica | Valor |
|---|---|
| Evaluaciones motor (`evaluatedCount`) | 36 → 266 |
| Señales generadas (`signalsGenerated`) | 1 → **61** (Δ 60) |
| Supresión por confluencia | 51 |
| Decisiones registradas | 61 |
| **Taps en ventana** | **12** |
| **Operaciones DEMO** | **6** |
| Resoluciones | W=4, L=2 (**WR 66,7 %**) |
| Máx. racha de pérdidas | 2 |
| Máx. drawdown desde pico | **−300.001,28 COP** |

---

## 3. DISTRIBUCIÓN DE VETOS

| Motivo de veto | Ocurrencias | Nota |
|---|---|---|
| `RISK_LIMIT` — Operación abierta en curso | 34 | **Benigno** (no es guard de sesión) |
| `ADAPTIVE_BLOCK` — Anti-patrón recurrente | 10 | Control negativo **intacto** ✅ |
| `COOLDOWN` (YOLO, ~0,1–1,8 s) | ~11 | Pausa entre operaciones |
| **`RISK_LIMIT` Trailing Profit Lock** | **0** | **ELIMINADO en YOLO** ✅ |
| **`Stop Loss Sesión / sessionFloor`** | **0** | **ELIMINADO en YOLO** ✅ |

---

## 4. EVIDENCE (Rutas y Archivos de Prueba)

- Telemetría periódica: `loops/r23_yolo/iter_1/live_samples.jsonl`
- Muestras de `/status`: `loops/r23_yolo/iter_1/status_samples.jsonl`
- Eventos (buffer): `loops/r23_yolo/iter_1/events.jsonl`
- Ticks crudos: `loops/r23_yolo/iter_1/ticks.jsonl` y `ticks_live.csv`
- Decisiones con `decision_id`: `loops/r23_yolo/iter_1/decisions.csv`
- Operaciones ejecutadas: `loops/r23_yolo/iter_1/trades.csv`
- Comparación de replay: `loops/r23_yolo/iter_1/replay_comparison.json` y `_report.md`

---

## 5. PARIDAD LIVE ↔ REPLAY

| Métrica | Valor |
|---|---|
| Decisiones totales | 62 |
| Pares evaluables | 1 |
| Exact matches | 0 |
| Missing en Replay | 60 |
| Missing en Live | 1 |
| **Match rate** | **0,0 %** |

Cobertura baja e idéntica a iteraciones previas (motores no isomorfos). Sin cambio respecto a R23A/R23B-baseline.

---

## 6. REGRESSION & SECURITY CHECK

- **Cuenta real:** 0 órdenes, 0 interacciones, 0 fondos comprometidos.
- **Fail-closed:** Guard de cuenta DEMO activo en runtime (116/116).
- **Controles negativos preservados:** `ADAPTIVE_BLOCK` sigue activo; Veto V1/V2, ventana sniper y regla 1-orden-por-vela intactas.

---

## 7. RECOMMENDATION

**KEEP** — El desbloqueo de límites de sesión en YOLO funciona, la seguridad DEMO permanece fail-closed
y la operativa resultó observable y rentable en esta ventana DEMO.
