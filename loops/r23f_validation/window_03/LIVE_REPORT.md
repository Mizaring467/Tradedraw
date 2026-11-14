# REPORTE DE VALIDACIÓN LIVE — R23 AUTÓNOMO ITERACIÓN 3 (CUENTA DEMO)

**Fecha:** 2026-10-17 02:25:50 UTC  
**Validador:** OPENCODE (`w1:pT`)  
**Orquestador:** AGY (`w1:p1`)  
**Dispositivo Auditado:** Xiaomi Redmi A1 (`UGU8KFUKM7E6CE8D`)  
**Build Identificada:** versionCode=18, buildId=R23F-ADAPTIVE, gitSha=ac6b0b2  
**Modo Auditado:** `AUTONOMOUS` (Cuenta DEMO)  
**Duración de la Ventana:** 20.0 minutos (103 muestras de telemetría)  
**Cierre Nominal / Real:** 1792203849 / 1792203921 (gracia de settlement: 72.7s)  
**Veredicto:** **PASS**  

---

## 1. FACT (Hechos Directamente Observados)
1. **Seguridad DEMO:** Cuenta DEMO verificada 103 veces. Cuentas reales observadas: 0 (0.0%).
2. **Preservación y Dinámica de Balance:** Saldo inicial = 38,557,736.96 COP, saldo final VALIDADO = 38,421,736.96 COP (Δ = -136,000.00 COP). Drawdown validado: 0.82%.
3. **Filtro de Outliers (REQ 2):** 0 lectura(s) de balance descartada(s) como glitch de telemetría (umbral: >1,000,000 COP o >10x stake, sin trade confirmado en la muestra). El drawdown se calcula sobre `validated_balance`, no sobre la lectura cruda.
4. **Ejecución Autónoma:** 10 taps despachados en la ventana (acumulado histórico del proceso: 30). 6 operaciones autónomas registradas.
5. **Identidad de Build:** La APK en ejecución reportó versionCode=18, buildId="R23F-ADAPTIVE", gitSha="ac6b0b2".
6. **Decisiones Observadas:** Se registraron 74 decisiones con `decision_id` determinista; el campo `score` es el puntaje REAL de confluencia extraído del `detail` (vacío cuando el evento nunca pasó por la matriz).
7. **Paridad Live ↔ Replay:** 0 exact matches de 2 pareadas (0.0% de paridad).

---

## 2. DECISION PIPELINE
- Evaluaciones motor: 949
- Señales generadas: 237
- Decisiones registradas: 74
- Operaciones DEMO ejecutadas: 6
- Resoluciones: W=3, L=8 (WR = 27.3%)

---

## 2B. CONTRAFACTUAL SHADOW DE `ADAPTIVE_BLOCK` (R23E)

**Naturaleza:** 100% pasivo. El bot real opero con `ADAPTIVE_BLOCK` ACTIVO; estas filas miden que
HABRIA pasado con las señales vetadas. Cero taps, cero fondos expuestos, saldo intacto.

- N_blocked_evaluated (vetos ADAPTIVE_BLOCK vistos): 42
- N_cf_deduplicated (descartados por ocupacion 60s / 1 por vela): 35
- Contrafactuales registrados: 7
- Resueltos: 6 (CF_W=2, CF_L=4, CF_VOID=0)
- **CF_WR = 33.33%** (breakeven del preregistro = 54.95%)
- **CF_EV unitario = -39,333.33 COP** | CF_EV total = -236,000.00 COP (stake 100,000 COP, payout 0.8200000000000001)
- Sin resolver (faltaba tick de entrada/salida): 1

**Bucket de similitud (preregistrado §8.3):**
  - [98,100]%: n=6 W=2 L=4 VOID=0 WR=33.3%

**Bucket de score de confluencia (preregistrado §8.4):**
  - [75,79]pts: n=1 W=0 L=1 VOID=0 WR=0.0%
  - [80,84]pts: n=1 W=1 L=0 VOID=0 WR=100.0%
  - [85,100]pts: n=4 W=1 L=3 VOID=0 WR=25.0%

---

## 3. EVIDENCE (Rutas y Archivos de Prueba)
- Telemetría periódica: `loops/r23f_validation/window_03\live_samples.jsonl`
- Muestras de `/status`: `loops/r23f_validation/window_03\status_samples.jsonl`
- Eventos de decisión: `loops/r23f_validation/window_03\events.jsonl`
- Decisiones con `decision_id`: `loops/r23f_validation/window_03\decisions.csv`
- Operaciones ejecutadas: `loops/r23f_validation/window_03\trades.csv`
- Ticks capturados en crudo: `loops/r23f_validation/window_03\ticks_live.csv`
- Metadata de la ventana (REQ 1/REQ 2): `loops/r23f_validation/window_03\window_metadata.json`
- Contrafactuales ADAPTIVE_BLOCK (R23E): `loops/r23f_validation/window_03\counterfactual_adaptive_block.csv`
- Comparación de replay: `loops/r23f_validation/window_03\replay_comparison.json` y `loops/r23f_validation/window_03\replay_comparison_report.md`

---

## 4. REGRESSION & SECURITY CHECK
- **Cuenta real:** 0 órdenes, 0 interacciones, 0 fondos comprometidos.
- **Fail-closed:** Guard de seguridad de cuenta DEMO activo en runtime.

---

## 5. RECOMMENDATION
**KEEP**
