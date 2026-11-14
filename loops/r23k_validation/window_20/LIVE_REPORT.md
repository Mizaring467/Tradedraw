# REPORTE DE VALIDACIÓN LIVE — R23 AUTÓNOMO ITERACIÓN 20 (CUENTA DEMO)

**Fecha:** 2026-10-19 23:04:27 UTC  
**Validador:** OPENCODE (`w1:pT`)  
**Orquestador:** AGY (`w1:p1`)  
**Dispositivo Auditado:** Xiaomi Redmi A1 (`UGU8KFUKM7E6CE8D`)  
**Build Identificada:** versionCode=19, buildId=R23H-BUY-EXHAUSTION, gitSha=8cdb61f  
**Modo Auditado:** `AUTONOMOUS` (Cuenta DEMO)  
**Duración de la Ventana:** 20.0 minutos (55 muestras de telemetría)  
**Cierre Nominal / Real:** 1792451003 / 1792451067 (gracia de settlement: 63.9s)  
**Veredicto:** **PASS**  

---

## 1. FACT (Hechos Directamente Observados)
1. **Seguridad DEMO:** Cuenta DEMO verificada 55 veces. Cuentas reales observadas: 0 (0.0%).
2. **Preservación y Dinámica de Balance:** Saldo inicial = 38,727,736.32 COP, saldo final VALIDADO = 38,593,735.68 COP (Δ = -134,000.64 COP). Drawdown validado: 1.03%.
3. **Filtro de Outliers (REQ 2):** 0 lectura(s) de balance descartada(s) como glitch de telemetría (umbral: >1,000,000 COP o >10x stake, sin trade confirmado en la muestra). El drawdown se calcula sobre `validated_balance`, no sobre la lectura cruda.
4. **Ejecución Autónoma:** 10 taps despachados en la ventana (acumulado histórico del proceso: 30). 6 operaciones autónomas registradas.
5. **Identidad de Build:** La APK en ejecución reportó versionCode=19, buildId="R23H-BUY-EXHAUSTION", gitSha="8cdb61f".
6. **Decisiones Observadas:** Se registraron 141 decisiones con `decision_id` determinista; el campo `score` es el puntaje REAL de confluencia extraído del `detail` (vacío cuando el evento nunca pasó por la matriz).
7. **Paridad Live ↔ Replay:** 1 exact match de 1 pareada (100.0% de paridad en pares formados).

---

## 2. DECISION PIPELINE
- Evaluaciones motor: 3234
- Señales generadas: 990
- Decisiones registradas: 141
- Operaciones DEMO ejecutadas (ledger `trades.csv`, FUENTE CONTABLE): 6
- Delta de contadores de la APK (`totalWins`/`totalLosses`, TELEMETRIA de runtime): W=4, L=4 (WR = 50.0%)
- Conciliacion contadores vs ledger: AVISO: DISCREPANCIA de +2 resolucion(es) -> los cubos usan el ledger; los contadores pueden inflarse por doble resolucion concurrente en la APK (deuda R24).

---

## 2B. CONTRAFACTUAL SHADOW DE `ADAPTIVE_BLOCK` (R23E)

**Naturaleza:** 100% pasivo. El bot real opero con `ADAPTIVE_BLOCK` ACTIVO; estas filas miden que
HABRIA pasado con las señales vetadas. Cero taps, cero fondos expuestos, saldo intacto.

- N_blocked_evaluated (vetos ADAPTIVE_BLOCK vistos): 26
- N_cf_deduplicated (descartados por ocupacion 60s / 1 por vela): 17
- Contrafactuales registrados: 9
- Resueltos: 9 (CF_W=3, CF_L=6, CF_VOID=0)
- **CF_WR = 33.33%** (breakeven del preregistro = 54.95%)
- **CF_EV unitario = -39,333.33 COP** | CF_EV total = -354,000.00 COP (stake 100,000 COP, payout 0.83)
- Sin resolver (faltaba tick de entrada/salida): 0

---

## 3. EVIDENCE (Rutas y Archivos de Prueba)
- Telemetría periódica: `loops/r23k_validation/window_20\live_samples.jsonl`
- Muestras de `/status`: `loops/r23k_validation/window_20\status_samples.jsonl`
- Eventos de decisión: `loops/r23k_validation/window_20\events.jsonl`
- Decisiones con `decision_id`: `loops/r23k_validation/window_20\decisions.csv`
- Operaciones ejecutadas: `loops/r23k_validation/window_20\trades.csv`
- Ticks capturados en crudo: `loops/r23k_validation/window_20\ticks_live.csv`
- Metadata de la ventana (REQ 1/REQ 2): `loops/r23k_validation/window_20\window_metadata.json`
- Contrafactuales ADAPTIVE_BLOCK (R23E): `loops/r23k_validation/window_20\counterfactual_adaptive_block.csv`
- Comparación de replay: `loops/r23k_validation/window_20\replay_comparison.json` y `loops/r23k_validation/window_20\replay_comparison_report.md`

---

## 4. REGRESSION & SECURITY CHECK
- **Cuenta real:** 0 órdenes, 0 interacciones, 0 fondos comprometidos.
- **Fail-closed:** Guard de seguridad de cuenta DEMO activo en runtime.

---

## 5. RECOMMENDATION
**KEEP**
