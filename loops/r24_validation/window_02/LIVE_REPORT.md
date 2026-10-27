# REPORTE DE VALIDACIÓN LIVE — R23 AUTÓNOMO ITERACIÓN 1 (CUENTA DEMO)

**Fecha:** 2026-10-27 04:48:11 UTC  
**Validador:** OPENCODE (`w1:pT`)  
**Orquestador:** AGY (`w1:p1`)  
**Dispositivo Auditado:** Xiaomi Redmi A1 (`UGU8KFUKM7E6CE8D`)  
**Build Identificada:** versionCode=19, buildId=R23H-BUY-EXHAUSTION, gitSha=692d6f3  
**Modo Auditado:** `AUTONOMOUS` (Cuenta DEMO)  
**Duración de la Ventana:** 20.0 minutos (19 muestras de telemetría)  
**Cierre Nominal / Real:** 1793076410 / 1793076418 (gracia de settlement: 8.2s)  
**Veredicto:** **PASS**  

---

## 1. FACT (Hechos Directamente Observados)
1. **Seguridad DEMO:** Cuenta DEMO verificada 19 veces. Cuentas reales observadas: 0 (0.0%).
2. **Preservación y Dinámica de Balance:** Saldo inicial = 37,083,735.04 COP, saldo final VALIDADO = 37,083,735.04 COP (Δ = +0.00 COP). Drawdown validado: 0.00%.
3. **Filtro de Outliers (REQ 2):** 0 lectura(s) de balance descartada(s) como glitch de telemetría (umbral: >1,000,000 COP o >10x stake, sin trade confirmado en la muestra). El drawdown se calcula sobre `validated_balance`, no sobre la lectura cruda.
4. **Ejecución Autónoma:** 0 taps despachados en la ventana (acumulado histórico del proceso: 0). 0 operaciones autónomas registradas.
5. **Identidad de Build:** La APK en ejecución reportó versionCode=19, buildId="R23H-BUY-EXHAUSTION", gitSha="692d6f3".
6. **Decisiones Observadas:** Se registraron 0 decisiones con `decision_id` determinista; el campo `score` es el puntaje REAL de confluencia extraído del `detail` (vacío cuando el evento nunca pasó por la matriz).
7. **Paridad Live ↔ Replay:** NO CONCLUYENTE

---

## 2. DECISION PIPELINE
- Evaluaciones motor: 0
- Señales generadas: 0
- Decisiones registradas: 0
- Operaciones DEMO ejecutadas (ledger `trades.csv`, FUENTE CONTABLE): 0
- Delta de contadores de la APK (`totalWins`/`totalLosses`, TELEMETRIA de runtime): W=0, L=0 (WR = 0.0%)
- Conciliacion contadores vs ledger: OK (coinciden)

---

## 2B. CONTRAFACTUAL SHADOW DE `ADAPTIVE_BLOCK` (R23E)

**Naturaleza:** 100% pasivo. El bot real opero con `ADAPTIVE_BLOCK` ACTIVO; estas filas miden que
HABRIA pasado con las señales vetadas. Cero taps, cero fondos expuestos, saldo intacto.

- N_blocked_evaluated (vetos ADAPTIVE_BLOCK vistos): 0
- N_cf_deduplicated (descartados por ocupacion 60s / 1 por vela): 0
- Contrafactuales registrados: 0
- Resueltos: 0 (CF_W=0, CF_L=0, CF_VOID=0)
- **CF_WR = 0.00%** (breakeven del preregistro = 54.95%)
- **CF_EV unitario = +0.00 COP** | CF_EV total = +0.00 COP (stake 100,000 COP, payout 0.8300000000000001)
- Sin resolver (faltaba tick de entrada/salida): 0

**Bucket de similitud (preregistrado §8.3):**
  (sin contrafactuales resueltos con este campo)

**Bucket de score de confluencia (preregistrado §8.4):**
  (sin contrafactuales resueltos con este campo)

---

## 3. EVIDENCE (Rutas y Archivos de Prueba)
- Telemetría periódica: `loops/r24_validation/window_02/live_samples.jsonl`
- Muestras de `/status`: `loops/r24_validation/window_02/status_samples.jsonl`
- Eventos de decisión: `loops/r24_validation/window_02/events.jsonl`
- Decisiones con `decision_id`: `loops/r24_validation/window_02/decisions.csv`
- Operaciones ejecutadas: `loops/r24_validation/window_02/trades.csv`
- Ticks capturados en crudo: `loops/r24_validation/window_02/ticks_live.csv`
- Metadata de la ventana (REQ 1/REQ 2): `loops/r24_validation/window_02/window_metadata.json`
- Contrafactuales ADAPTIVE_BLOCK (R23E): `loops/r24_validation/window_02/counterfactual_adaptive_block.csv`
- Comparación de replay: `loops/r24_validation/window_02/replay_comparison.json` y `loops/r24_validation/window_02/replay_comparison_report.md`

---

## 4. REGRESSION & SECURITY CHECK
- **Cuenta real:** 0 órdenes, 0 interacciones, 0 fondos comprometidos.
- **Fail-closed:** Guard de seguridad de cuenta DEMO activo en runtime.

---

## 5. RECOMMENDATION
**KEEP**
