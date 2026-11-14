# REPORTE DE VALIDACIÓN LIVE — R23B ITERACIÓN 2 (MODO YOLO · CUENTA DEMO)

**Fecha:** 2026-10-16 (UTC)
**Validador:** OPENCODE (`w1:pT`)
**Orquestador:** AGY (`w1:p1`)
**Dispositivo:** Xiaomi Redmi A1 (`220733SL`) · enlace **WiFi** `[fd10:d508:34cf:10:e8c5:80ff:fef0:d5f9]:5555`
**Build:** versionCode=14, versionName=1.4-r23-yolo-iter2, buildId=R23-YOLO-ITER2, gitSha=ac079fd
**Submodo:** `YOLO` (118/118 muestras) · **Modo Global:** `AUTONOMOUS`
**Duración de la Ventana:** 20,0 min (118 muestras de telemetría)
**Veredicto:** **KEEP** (éxito técnico de telemetría) — rentabilidad NO concluyente

---

## 1. FACT (Hechos Directamente Observados)

1. **Seguridad DEMO:** Cuenta DEMO verificada **118 / 118** muestras. Cuentas reales: **0**.
2. **Submodo:** `submode = "YOLO"` en **118 / 118** muestras.
3. **Identidad de Build:** versionCode=14, buildId="R23-YOLO-ITER2", gitSha="ac079fd".
4. **Ejecución Autónoma:** **10 taps** en la ventana · **5 operaciones DEMO** registradas.
5. **Dinámica de Balance:** 39.033.735,68 → **38.716.736,00 COP** (**Δ −316.999,68 COP**).
6. **Resoluciones:** **W = 1, L = 4** (Win Rate = **20,0 %**).
7. **Motor:** `signalsGenerated` 0 → 63 · `evaluatedCount` 12 → 245 · supresión por confluencia.
8. **Decisiones:** 63 (`decision_id` determinista). Vetos: `RISK_LIMIT` (operación abierta) 40, `ADAPTIVE_BLOCK` 18.

---

## 2. CRITERIO ESPECÍFICO DE ÉXITO DE LA ITERACIÓN 2 — ✅ CUMPLIDO

| Criterio (spec §3.B) | Resultado | Evidencia |
|---|---|---|
| `action = UNKNOWN` == 0 en `trades.csv` | ✅ **0** | acciones = {BUY: 3, SELL: 2} |
| Sin eventos `END root (tap)` como trades | ✅ **0** | detalle = "CONFIRMADA BUY/SELL saldo movido" |
| Firma `"CONFIRMADA BUY/SELL saldo movido"` | ✅ | 5/5 filas con esa firma |
| Estado de reposo limpio | ✅ | `ORDER_STATE: NINGUNA BUY sin operacion en curso` |

**La telemetría determinística de dirección (commit `ac079fd`) funciona: el `action=UNKNOWN` de iter 1
queda erradicado y los taps físicos `END root` ya no contaminan `trades.csv`.**

---

## 3. DECISION PIPELINE

| Métrica | Valor |
|---|---|
| Evaluaciones motor (`evaluatedCount`) | 12 → 245 |
| Señales generadas (`signalsGenerated`) | 0 → **63** |
| Decisiones registradas | 63 |
| **Taps en ventana** | **10** |
| **Operaciones DEMO** | **5** (BUY 3 / SELL 2) |
| Resoluciones | W=1, L=4 (**WR 20,0 %**) |
| Máx. racha de pérdidas | **3** |
| Máx. drawdown desde pico | **−400.000,00 COP** |

---

## 4. DISTRIBUCIÓN DE VETOS

| Motivo de veto | Ocurrencias | Nota |
|---|---|---|
| `RISK_LIMIT` — Operación abierta en curso | 40 | Benigno (anti-solape) |
| `ADAPTIVE_BLOCK` — Anti-patrón recurrente | 18 | Control negativo **intacto** ✅ |
| `RISK_LIMIT` Trailing Profit Lock | **0** | Desbloqueado (YOLO) |
| `Stop Loss Sesión / sessionFloor` | **0** | Desbloqueado (YOLO) |

---

## 5. EVIDENCE (Rutas y Archivos de Prueba)

- Telemetría periódica: `loops/r23_yolo/iter_2/live_samples.jsonl`
- Muestras de `/status`: `loops/r23_yolo/iter_2/status_samples.jsonl`
- Eventos (curl): `loops/r23_yolo/iter_2/events.jsonl` · Ticks (curl): `ticks.jsonl` · `ticks_live.csv`
- Decisiones: `loops/r23_yolo/iter_2/decisions.csv`
- Operaciones: `loops/r23_yolo/iter_2/trades.csv` (BUY/SELL determinados)

---

## 6. REGRESSION & SECURITY CHECK

- **Cuenta real:** 0 órdenes, 0 fondos comprometidos. Fail-closed activo (118/118).
- **Controles negativos:** `ADAPTIVE_BLOCK` activo (18); "Operación abierta en curso" activo (40);
  V1/V2, ventana sniper, 1-orden-por-vela intactas.

---

## 7. RECOMMENDATION

**KEEP** en lo técnico: el objetivo de la iteración 2 (telemetría determinística BUY/SELL) se cumplió
íntegramente. **Rentabilidad NO concluyente:** esta ventana perdió −316.999,68 COP (WR 20 %) frente a
la ganancia de iter 1 (+131.998,72, WR 66,7 %) — muestra pequeña (n=5) e inconclusa; ver
`VALIDATOR_FINDINGS.md` §3.
