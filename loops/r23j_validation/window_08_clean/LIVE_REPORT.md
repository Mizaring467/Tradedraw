# REPORTE DE VALIDACIÓN LIVE — R23 AUTÓNOMO ITERACIÓN 8 (CUENTA DEMO)

**Fecha:** 2026-10-19 03:28:34 UTC  
**Validador:** OPENCODE (`w1:pT`)  
**Orquestador:** AGY (`w1:p1`)  
**Dispositivo Auditado:** Xiaomi Redmi A1 (`UGU8KFUKM7E6CE8D`)  
**Build Identificada:** versionCode=19, buildId=R23H-BUY-EXHAUSTION, gitSha=8cdb61f  
**Modo Auditado:** `AUTONOMOUS` (Cuenta DEMO)  
**Duración de la Ventana:** 20.0 minutos (118 muestras de telemetría)  
**Cierre Nominal / Real:** 1792380491 / 1792380494 (gracia de settlement: 3.4s)  
**Veredicto:** **PASS**  

---

## 1. FACT (Hechos Directamente Observados)
1. **Seguridad DEMO:** Cuenta DEMO verificada 118 veces. Cuentas reales observadas: 0 (0.0%).
2. **Preservación y Dinámica de Balance:** Saldo inicial = 39,337,735.68 COP, saldo final VALIDADO = 39,120,737.28 COP (Δ = -216,998.40 COP). Drawdown validado: 1.02%.
3. **Filtro de Outliers (REQ 2):** 0 lectura(s) de balance descartada(s) como glitch de telemetría (umbral: >1,000,000 COP o >10x stake, sin trade confirmado en la muestra). El drawdown se calcula sobre `validated_balance`, no sobre la lectura cruda.
4. **Ejecución Autónoma:** 8 taps despachados en la ventana (acumulado histórico del proceso: 14). 4 operaciones autónomas registradas.
5. **Identidad de Build:** La APK en ejecución reportó versionCode=19, buildId="R23H-BUY-EXHAUSTION", gitSha="8cdb61f".
6. **Decisiones Observadas:** Se registraron 90 decisiones con `decision_id` determinista; el campo `score` es el puntaje REAL de confluencia extraído del `detail` (vacío cuando el evento nunca pasó por la matriz).
7. **Paridad Live ↔ Replay:** 3 exact matches de 4 pareadas (75.0% de paridad).

---

## 2. DECISION PIPELINE
- Evaluaciones motor: 1103
- Señales generadas: 298
- Decisiones registradas: 90
- Operaciones DEMO ejecutadas: 4
- Resoluciones: W=1, L=3 (WR = 25.0%)

---

## 2B. CONTRAFACTUAL SHADOW DE `ADAPTIVE_BLOCK` (R23E)

**Naturaleza:** 100% pasivo. El bot real opero con `ADAPTIVE_BLOCK` ACTIVO; estas filas miden que
HABRIA pasado con las señales vetadas. Cero taps, cero fondos expuestos, saldo intacto.

- N_blocked_evaluated (vetos ADAPTIVE_BLOCK vistos): 53
- N_cf_deduplicated (descartados por ocupacion 60s / 1 por vela): 43
- Contrafactuales registrados: 10
- Resueltos: 9 (CF_W=6, CF_L=3, CF_VOID=0)
- **CF_WR = 66.67%** (breakeven del preregistro = 54.95%)
- **CF_EV unitario = +22,000.00 COP** | CF_EV total = +198,000.00 COP (stake 100,000 COP, payout 0.8300000000000001)
- Sin resolver (faltaba tick de entrada/salida): 1

**Bucket de similitud (preregistrado §8.3):**
  - [98,100]%: n=9 W=6 L=3 VOID=0 WR=66.7%

**Bucket de score de confluencia (preregistrado §8.4):**
  - [75,79]pts: n=5 W=3 L=2 VOID=0 WR=60.0%
  - [85,100]pts: n=4 W=3 L=1 VOID=0 WR=75.0%

---

## 3. EVIDENCE (Rutas y Archivos de Prueba)
- Telemetría periódica: `loops/r23j_validation/window_08_clean\live_samples.jsonl`
- Muestras de `/status`: `loops/r23j_validation/window_08_clean\status_samples.jsonl`
- Eventos de decisión: `loops/r23j_validation/window_08_clean\events.jsonl`
- Decisiones con `decision_id`: `loops/r23j_validation/window_08_clean\decisions.csv`
- Operaciones ejecutadas: `loops/r23j_validation/window_08_clean\trades.csv`
- Ticks capturados en crudo: `loops/r23j_validation/window_08_clean\ticks_live.csv`
- Metadata de la ventana (REQ 1/REQ 2): `loops/r23j_validation/window_08_clean\window_metadata.json`
- Contrafactuales ADAPTIVE_BLOCK (R23E): `loops/r23j_validation/window_08_clean\counterfactual_adaptive_block.csv`
- Comparación de replay: `loops/r23j_validation/window_08_clean\replay_comparison.json` y `loops/r23j_validation/window_08_clean\replay_comparison_report.md`

---

## 4. REGRESSION & SECURITY CHECK
- **Cuenta real:** 0 órdenes, 0 interacciones, 0 fondos comprometidos.
- **Fail-closed:** Guard de seguridad de cuenta DEMO activo en runtime.

---

## 5. RECOMMENDATION
**KEEP**
