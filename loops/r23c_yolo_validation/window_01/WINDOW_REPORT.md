# R23C — VENTANA 01 DE 06 · VALIDACIÓN ESTADÍSTICA YOLO

**Fecha:** 2026-10-16 (UTC)
**Validador:** OPENCODE (`w1:pT`)
**Build congelada:** `ac079fd` · versionCode=14 · buildId=R23-YOLO-ITER2
**Modo:** AUTONOMOUS · Submodo: YOLO · Cuenta DEMO
**Dispositivo:** Redmi A1 (WiFi `[fd10:...]:5555`)
**Ventana:** 20,0 min · 118 muestras de `/status`

---

## 1. RESULTADOS

| Métrica | Valor |
|---|---|
| **N** (trades confirmados con resolución W/L) | **4** |
| **W** (ganadas) | **1** |
| **L** (perdidas) | **3** |
| **VOID** (empates/anuladas) | **0** |
| **WR** (Win Rate de la ventana) | **25,0 %** |
| Payout observado | **82,0 %** |
| Break-Even WR (`1/(1+payout)`) | **54,9 %** |

> **N se contó desde los contadores canónicos del motor** (`totalWins` 6→7, `totalLosses` 6→9),
> conforme a la spec ("NO contar taps sin saldo ni eventos de reposo"). `trades.csv` contiene 6 filas,
> pero **no todas son resoluciones** (ver §5).

---

## 2. RACHAS

| Métrica | Valor |
|---|---|
| `max_win_streak` | **1** |
| `max_loss_streak` | **2** |

Secuencia de resoluciones de la ventana (cronológica): **L → L → W → L → L**.
No hubo racha ≥ 5 victorias.

---

## 3. BALANCE

| Métrica | Valor |
|---|---|
| Saldo inicial de la ventana | 38.826.736,64 COP |
| Saldo final de la ventana | 38.608.737,28 COP |
| **Δ Balance neto** | **−217.999,36 COP** |
| Máx. drawdown observado | **−217.999,36 COP** (−0,561 %) |

Balance pico = inicial (38.826.736,64); el valle (−217.999,36) se alcanzó al cierre de la ventana.
**Payout 82 % < WR requerido 54,9 %** → ventana perdedora (WR 25 %).

---

## 4. DISTRIBUCIÓN

| Métrica | Valor |
|---|---|
| BUY count | **2** |
| SELL count | **4** |
| BUY WR | 0/2 confirmados con dirección BUY ganadora (ver nota) |
| SELL WR | 1/3 estimado (única victoria fue SELL) |
| Régimen predominante | **UPTREND** (98/118 muestras; SIDEWAYS 5, DOWNTREND 15) |
| Activo operado | **Z-CRY/IDX** (118/118) |

> Nota: la atribución BUY/SELL-WR es aproximada; el único trade ganador fue un **SELL** (MT_MOMENTUM
> Impulso Bajista). Con n=4 no es concluyente.

---

## 5. SEGURIDAD Y TELEMETRÍA

| Control | Resultado |
|---|---|
| Verificación DEMO | ✅ **118 / 118** muestras `isDemoAccount==true` (0 reales) |
| Conteo vetos `ADAPTIVE_BLOCK` | **37** |
| Conteo vetos `OPERACIÓN EN CURSO` (`RISK_LIMIT`) | **28** |
| Conteo vetos `COOLDOWN` | **5** |
| `action = UNKNOWN` en `trades.csv` | ✅ **0** |
| Exclusión de `END root` de la tabla de trades | ✅ **0** presentes |

⚠️ **Anomalía detectada en `trades.csv` (6 filas ≠ 4 resoluciones):**

| decision_id | detalle | ¿Es trade válido? |
|---|---|---|
| `7ed5c2e8` | CONFIRMADA SELL saldo movido | ✅ Sí |
| `e0d9bdaa` | **NINGUNA BUY sin operacion en curso** | ❌ **Evento de reposo** (no es trade) |
| `872b047a` | CONFIRMADA SELL saldo movido | ✅ Sí |
| `4e5d58b9` | ENVIADA SELL tap despachado | ⚠️ Tap (sin confirmación de saldo) |
| `aee345be` | ENVIADA BUY tap despachado | ⚠️ Tap (sin confirmación de saldo) |
| `9298a424` | CONFIRMADA SELL saldo movido | ✅ Sí |

El filtro del monitor (`kind=="ORDER_STATE" or "saldo movido"`) **vuelve a incluir eventos de reposo
`NINGUNA ...` y taps `ENVIADA`** como trades → el conteo de `trades.csv` NO es fiable como N. Se usó
el contador del motor (N=4).

---

## 6. BUILD

| Campo | Valor |
|---|---|
| `gitSha` | `ac079fd` |
| `versionCode` | 14 |
| `buildId` | `R23-YOLO-ITER2` |
| Submodo sostenido | `YOLO` (118/118) |

---

## 7. ARTEFACTOS

- `live_samples.jsonl` (118) · `status_samples.jsonl` (118)
- `events.jsonl` (curl, 60 eventos) · `ticks.jsonl` (curl) · `ticks_live.csv`
- `decisions.csv` (76) · `trades.csv` (6 filas — ver anomalía §5)
- `replay_comparison.json` / `_report.md` · `monitor_stdout.log`
