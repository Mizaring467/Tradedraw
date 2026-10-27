# REPORTE DE VALIDACIÓN LIVE — R23 AUTÓNOMO ITERACIÓN 1 (CUENTA DEMO)

**Fecha:** 2026-10-27 05:19:25 UTC  
**Validador:** OPENCODE (`w1:pT`)  
**Orquestador:** AGY (`w1:p1`)  
**Dispositivo Auditado:** Xiaomi Redmi A1 (`UGU8KFUKM7E6CE8D`)  
**Build Identificada:** versionCode=19, buildId=R23H-BUY-EXHAUSTION, gitSha=692d6f3  
**Modo Auditado:** `AUTONOMOUS` (Cuenta DEMO)  
**Duración de la Ventana:** 20.0 minutos (113 muestras de telemetría)  
**Cierre Nominal / Real:** 1793078347 / 1793078349 (gracia de settlement: 1.8s)  
**Veredicto:** **PASS**  

---

## 1. FACT (Hechos Directamente Observados)
1. **Seguridad DEMO:** Cuenta DEMO verificada 113 veces. Cuentas reales observadas: 0 (0.0%).
2. **Preservación y Dinámica de Balance:** Saldo inicial = 37,083,735.04 COP, saldo final VALIDADO = 36,883,737.60 COP (Δ = -199,997.44 COP). Drawdown validado: 0.54%.
3. **Filtro de Outliers (REQ 2):** 0 lectura(s) de balance descartada(s) como glitch de telemetría (umbral: >1,000,000 COP o >10x stake, sin trade confirmado en la muestra). El drawdown se calcula sobre `validated_balance`, no sobre la lectura cruda.
4. **Ejecución Autónoma:** 4 taps despachados en la ventana (acumulado histórico del proceso: 4). 2 operaciones autónomas registradas.
5. **Identidad de Build:** La APK en ejecución reportó versionCode=19, buildId="R23H-BUY-EXHAUSTION", gitSha="692d6f3".
6. **Decisiones Observadas:** Se registraron 38 decisiones con `decision_id` determinista; el campo `score` es el puntaje REAL de confluencia extraído del `detail` (vacío cuando el evento nunca pasó por la matriz).
7. **Paridad Live ↔ Replay:** NO EVALUABLE (0 pares evaluados, 38 elementos sin replay correspondiente).

---

## 2. DECISION PIPELINE
- Evaluaciones motor: 341
- Señales generadas: 61
- Decisiones registradas: 38
- Operaciones DEMO ejecutadas (ledger `trades.csv`, FUENTE CONTABLE): 2
- Delta de contadores de la APK (`totalWins`/`totalLosses`, TELEMETRIA de runtime): W=0, L=2 (WR = 0.0%)
- Conciliacion contadores vs ledger: OK (coinciden)

---

## 2B. CONTRAFACTUAL SHADOW DE `ADAPTIVE_BLOCK` (R23E)

**Naturaleza:** 100% pasivo. El bot real opero con `ADAPTIVE_BLOCK` ACTIVO; estas filas miden que
HABRIA pasado con las señales vetadas. Cero taps, cero fondos expuestos, saldo intacto.

- N_blocked_evaluated (vetos ADAPTIVE_BLOCK vistos): 8
- N_cf_deduplicated (descartados por ocupacion 60s / 1 por vela): 5
- Contrafactuales registrados: 3
- Resueltos: 2 (CF_W=0, CF_L=2, CF_VOID=0)
- **CF_WR = 0.00%** (breakeven del preregistro = 54.95%)
- **CF_EV unitario = -100,000.00 COP** | CF_EV total = -200,000.00 COP (stake 100,000 COP, payout 0.8300000000000001)
- Sin resolver (faltaba tick de entrada/salida): 1

**Bucket de similitud (preregistrado §8.3):**
  - [98,100]%: n=2 W=0 L=2 VOID=0 WR=0.0%

**Bucket de score de confluencia (preregistrado §8.4):**
  - [85,100]pts: n=2 W=0 L=2 VOID=0 WR=0.0%

---

## 3. EVIDENCE (Rutas y Archivos de Prueba)
- Telemetría periódica: `loops/r25_validation/window_01/live_samples.jsonl`
- Muestras de `/status`: `loops/r25_validation/window_01/status_samples.jsonl`
- Eventos de decisión: `loops/r25_validation/window_01/events.jsonl`
- Decisiones con `decision_id`: `loops/r25_validation/window_01/decisions.csv`
- Operaciones ejecutadas: `loops/r25_validation/window_01/trades.csv`
- Ticks capturados en crudo: `loops/r25_validation/window_01/ticks_live.csv`
- Metadata de la ventana (REQ 1/REQ 2): `loops/r25_validation/window_01/window_metadata.json`
- Contrafactuales ADAPTIVE_BLOCK (R23E): `loops/r25_validation/window_01/counterfactual_adaptive_block.csv`
- Comparación de replay: `loops/r25_validation/window_01/replay_comparison.json` y `loops/r25_validation/window_01/replay_comparison_report.md`

---

## 4. REGRESSION & SECURITY CHECK
- **Cuenta real:** 0 órdenes, 0 interacciones, 0 fondos comprometidos.
- **Fail-closed:** Guard de seguridad de cuenta DEMO activo en runtime.

---

## 5. RECOMMENDATION
**KEEP**
