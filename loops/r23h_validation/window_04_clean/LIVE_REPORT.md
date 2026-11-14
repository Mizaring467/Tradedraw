# REPORTE DE VALIDACIÓN LIVE — R23 AUTÓNOMO ITERACIÓN 4 (CUENTA DEMO)

**Fecha:** 2026-10-18 20:06:35 UTC  
**Validador:** OPENCODE (`w1:pT`)  
**Orquestador:** AGY (`w1:p1`)  
**Dispositivo Auditado:** Xiaomi Redmi A1 (`UGU8KFUKM7E6CE8D`)  
**Build Identificada:** versionCode=19, buildId=R23H-BUY-EXHAUSTION, gitSha=aac1df3  
**Modo Auditado:** `AUTONOMOUS` (Cuenta DEMO)  
**Duración de la Ventana:** 20.0 minutos (100 muestras de telemetría)  
**Cierre Nominal / Real:** 1792353940 / 1792353946 (gracia de settlement: 5.3s)  
**Veredicto:** **PASS**  

---

## 1. FACT (Hechos Directamente Observados)
1. **Seguridad DEMO:** Cuenta DEMO verificada 100 veces. Cuentas reales observadas: 0 (0.0%).
2. **Preservación y Dinámica de Balance:** Saldo inicial = 38,908,736.00 COP, saldo final VALIDADO = 39,382,735.36 COP (Δ = +473,999.36 COP). Drawdown validado: 1.46%.
3. **Filtro de Outliers (REQ 2):** 0 lectura(s) de balance descartada(s) como glitch de telemetría (umbral: >1,000,000 COP o >10x stake, sin trade confirmado en la muestra). El drawdown se calcula sobre `validated_balance`, no sobre la lectura cruda.
4. **Ejecución Autónoma:** 16 taps despachados en la ventana (acumulado histórico del proceso: 16). 8 operaciones autónomas registradas.
5. **Identidad de Build:** La APK en ejecución reportó versionCode=19, buildId="R23H-BUY-EXHAUSTION", gitSha="aac1df3".
6. **Decisiones Observadas:** Se registraron 88 decisiones con `decision_id` determinista; el campo `score` es el puntaje REAL de confluencia extraído del `detail` (vacío cuando el evento nunca pasó por la matriz).
7. **Paridad Live ↔ Replay:** 1 exact matches de 1 pareadas (100.0% de paridad).

---

## 2. DECISION PIPELINE
- Evaluaciones motor: 301
- Señales generadas: 88
- Decisiones registradas: 88
- Operaciones DEMO ejecutadas: 8
- Resoluciones: W=9, L=1 (WR = 90.0%)

---

## 2B. CONTRAFACTUAL SHADOW DE `ADAPTIVE_BLOCK` (R23E)

**Naturaleza:** 100% pasivo. El bot real opero con `ADAPTIVE_BLOCK` ACTIVO; estas filas miden que
HABRIA pasado con las señales vetadas. Cero taps, cero fondos expuestos, saldo intacto.

- N_blocked_evaluated (vetos ADAPTIVE_BLOCK vistos): 16
- N_cf_deduplicated (descartados por ocupacion 60s / 1 por vela): 11
- Contrafactuales registrados: 5
- Resueltos: 5 (CF_W=2, CF_L=3, CF_VOID=0)
- **CF_WR = 40.00%** (breakeven del preregistro = 54.95%)
- **CF_EV unitario = -26,800.00 COP** | CF_EV total = -134,000.00 COP (stake 100,000 COP, payout 0.8300000000000001)
- Sin resolver (faltaba tick de entrada/salida): 0

**Bucket de similitud (preregistrado §8.3):**
  - [98,100]%: n=5 W=2 L=3 VOID=0 WR=40.0%

**Bucket de score de confluencia (preregistrado §8.4):**
  - [80,84]pts: n=1 W=0 L=1 VOID=0 WR=0.0%
  - [85,100]pts: n=4 W=2 L=2 VOID=0 WR=50.0%

---

## 3. EVIDENCE (Rutas y Archivos de Prueba)
- Telemetría periódica: `loops/r23h_validation/window_04_clean\live_samples.jsonl`
- Muestras de `/status`: `loops/r23h_validation/window_04_clean\status_samples.jsonl`
- Eventos de decisión: `loops/r23h_validation/window_04_clean\events.jsonl`
- Decisiones con `decision_id`: `loops/r23h_validation/window_04_clean\decisions.csv`
- Operaciones ejecutadas: `loops/r23h_validation/window_04_clean\trades.csv`
- Ticks capturados en crudo: `loops/r23h_validation/window_04_clean\ticks_live.csv`
- Metadata de la ventana (REQ 1/REQ 2): `loops/r23h_validation/window_04_clean\window_metadata.json`
- Contrafactuales ADAPTIVE_BLOCK (R23E): `loops/r23h_validation/window_04_clean\counterfactual_adaptive_block.csv`
- Comparación de replay: `loops/r23h_validation/window_04_clean\replay_comparison.json` y `loops/r23h_validation/window_04_clean\replay_comparison_report.md`

---

## 4. REGRESSION & SECURITY CHECK
- **Cuenta real:** 0 órdenes, 0 interacciones, 0 fondos comprometidos.
- **Fail-closed:** Guard de seguridad de cuenta DEMO activo en runtime.

---

## 5. RECOMMENDATION
**KEEP**
