# REPORTE DE VALIDACIÓN LIVE — R23 AUTÓNOMO ITERACIÓN 16 (CUENTA DEMO)

**Fecha:** 2026-10-19 09:13:59 UTC  
**Validador:** OPENCODE (`w1:pT`)  
**Orquestador:** AGY (`w1:p1`)  
**Dispositivo Auditado:** Xiaomi Redmi A1 (`UGU8KFUKM7E6CE8D`)  
**Build Identificada:** versionCode=19, buildId=R23H-BUY-EXHAUSTION, gitSha=8cdb61f  
**Modo Auditado:** `AUTONOMOUS` (Cuenta DEMO)  
**Duración de la Ventana:** 20.0 minutos (118 muestras de telemetría)  
**Cierre Nominal / Real:** 1792401052 / 1792401204 (gracia de settlement: 151.9s)  
**Veredicto:** **PASS**  

---

## 1. FACT (Hechos Directamente Observados)
1. **Seguridad DEMO:** Cuenta DEMO verificada 118 veces. Cuentas reales observadas: 0 (0.0%).
2. **Preservación y Dinámica de Balance:** Saldo inicial = 38,751,736.32 COP, saldo final VALIDADO = 38,734,735.36 COP (Δ = -17,000.96 COP). Drawdown validado: 0.52%.
3. **Filtro de Outliers (REQ 2):** 0 lectura(s) de balance descartada(s) como glitch de telemetría (umbral: >1,000,000 COP o >10x stake, sin trade confirmado en la muestra). El drawdown se calcula sobre `validated_balance`, no sobre la lectura cruda.
4. **Ejecución Autónoma:** 6 taps despachados en la ventana (acumulado histórico del proceso: 44). 3 operaciones autónomas registradas.
5. **Identidad de Build:** La APK en ejecución reportó versionCode=19, buildId="R23H-BUY-EXHAUSTION", gitSha="8cdb61f".
6. **Decisiones Observadas:** Se registraron 61 decisiones con `decision_id` determinista; el campo `score` es el puntaje REAL de confluencia extraído del `detail` (vacío cuando el evento nunca pasó por la matriz).
7. **Paridad Live ↔ Replay:** 1 exact matches de 1 pareadas (100.0% de paridad).

---

## 2. DECISION PIPELINE
- Evaluaciones motor: 2401
- Señales generadas: 701
- Decisiones registradas: 61
- Operaciones DEMO ejecutadas: 3
- Resoluciones: W=1, L=1 (WR = 50.0%)

---

## 2B. CONTRAFACTUAL SHADOW DE `ADAPTIVE_BLOCK` (R23E)

**Naturaleza:** 100% pasivo. El bot real opero con `ADAPTIVE_BLOCK` ACTIVO; estas filas miden que
HABRIA pasado con las señales vetadas. Cero taps, cero fondos expuestos, saldo intacto.

- N_blocked_evaluated (vetos ADAPTIVE_BLOCK vistos): 35
- N_cf_deduplicated (descartados por ocupacion 60s / 1 por vela): 32
- Contrafactuales registrados: 3
- Resueltos: 3 (CF_W=1, CF_L=2, CF_VOID=0)
- **CF_WR = 33.33%** (breakeven del preregistro = 54.95%)
- **CF_EV unitario = -39,000.00 COP** | CF_EV total = -117,000.00 COP (stake 100,000 COP, payout 0.8300000000000001)
- Sin resolver (faltaba tick de entrada/salida): 0

**Bucket de similitud (preregistrado §8.3):**
  - [98,100]%: n=3 W=1 L=2 VOID=0 WR=33.3%

**Bucket de score de confluencia (preregistrado §8.4):**
  - [85,100]pts: n=3 W=1 L=2 VOID=0 WR=33.3%

---

## 3. EVIDENCE (Rutas y Archivos de Prueba)
- Telemetría periódica: `loops/r23k_validation/window_16\live_samples.jsonl`
- Muestras de `/status`: `loops/r23k_validation/window_16\status_samples.jsonl`
- Eventos de decisión: `loops/r23k_validation/window_16\events.jsonl`
- Decisiones con `decision_id`: `loops/r23k_validation/window_16\decisions.csv`
- Operaciones ejecutadas: `loops/r23k_validation/window_16\trades.csv`
- Ticks capturados en crudo: `loops/r23k_validation/window_16\ticks_live.csv`
- Metadata de la ventana (REQ 1/REQ 2): `loops/r23k_validation/window_16\window_metadata.json`
- Contrafactuales ADAPTIVE_BLOCK (R23E): `loops/r23k_validation/window_16\counterfactual_adaptive_block.csv`
- Comparación de replay: `loops/r23k_validation/window_16\replay_comparison.json` y `loops/r23k_validation/window_16\replay_comparison_report.md`

---

## 4. REGRESSION & SECURITY CHECK
- **Cuenta real:** 0 órdenes, 0 interacciones, 0 fondos comprometidos.
- **Fail-closed:** Guard de seguridad de cuenta DEMO activo en runtime.

---

## 5. RECOMMENDATION
**KEEP**
