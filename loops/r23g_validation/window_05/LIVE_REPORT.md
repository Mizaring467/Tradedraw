# REPORTE DE VALIDACIÓN LIVE — R23 AUTÓNOMO ITERACIÓN 5 (CUENTA DEMO)

**Fecha:** 2026-10-17 05:25:46 UTC  
**Validador:** OPENCODE (`w1:pT`)  
**Orquestador:** AGY (`w1:p1`)  
**Dispositivo Auditado:** Xiaomi Redmi A1 (`UGU8KFUKM7E6CE8D`)  
**Build Identificada:** versionCode=18, buildId=R23F-ADAPTIVE, gitSha=ac6b0b2  
**Modo Auditado:** `AUTONOMOUS` (Cuenta DEMO)  
**Duración de la Ventana:** 20.0 minutos (111 muestras de telemetría)  
**Cierre Nominal / Real:** 1792214640 / 1792214721 (gracia de settlement: 81.5s)  
**Veredicto:** **PASS**  

---

## 1. FACT (Hechos Directamente Observados)
1. **Seguridad DEMO:** Cuenta DEMO verificada 111 veces. Cuentas reales observadas: 0 (0.0%).
2. **Preservación y Dinámica de Balance:** Saldo inicial = 38,383,736.32 COP, saldo final VALIDADO = 38,432,737.28 COP (Δ = +49,000.96 COP). Drawdown validado: 0.69%.
3. **Filtro de Outliers (REQ 2):** 0 lectura(s) de balance descartada(s) como glitch de telemetría (umbral: >1,000,000 COP o >10x stake, sin trade confirmado en la muestra). El drawdown se calcula sobre `validated_balance`, no sobre la lectura cruda.
4. **Ejecución Autónoma:** 18 taps despachados en la ventana (acumulado histórico del proceso: 100). 5 operaciones autónomas registradas.
5. **Identidad de Build:** La APK en ejecución reportó versionCode=18, buildId="R23F-ADAPTIVE", gitSha="ac6b0b2".
6. **Decisiones Observadas:** Se registraron 125 decisiones con `decision_id` determinista; el campo `score` es el puntaje REAL de confluencia extraído del `detail` (vacío cuando el evento nunca pasó por la matriz).
7. **Paridad Live ↔ Replay:** 0 exact matches de 5 pareadas (0.0% de paridad).

---

## 2. DECISION PIPELINE
- Evaluaciones motor: 3227
- Señales generadas: 935
- Decisiones registradas: 125
- Operaciones DEMO ejecutadas: 5
- Resoluciones: W=4, L=2 (WR = 66.7%)

---

## 2B. CONTRAFACTUAL SHADOW DE `ADAPTIVE_BLOCK` (R23E)

**Naturaleza:** 100% pasivo. El bot real opero con `ADAPTIVE_BLOCK` ACTIVO; estas filas miden que
HABRIA pasado con las señales vetadas. Cero taps, cero fondos expuestos, saldo intacto.

- N_blocked_evaluated (vetos ADAPTIVE_BLOCK vistos): 35
- N_cf_deduplicated (descartados por ocupacion 60s / 1 por vela): 30
- Contrafactuales registrados: 5
- Resueltos: 5 (CF_W=1, CF_L=4, CF_VOID=0)
- **CF_WR = 20.00%** (breakeven del preregistro = 54.95%)
- **CF_EV unitario = -63,400.00 COP** | CF_EV total = -317,000.00 COP (stake 100,000 COP, payout 0.8300000000000001)
- Sin resolver (faltaba tick de entrada/salida): 0

**Bucket de similitud (preregistrado §8.3):**
  - [98,100]%: n=5 W=1 L=4 VOID=0 WR=20.0%

**Bucket de score de confluencia (preregistrado §8.4):**
  - [75,79]pts: n=1 W=0 L=1 VOID=0 WR=0.0%
  - [85,100]pts: n=4 W=1 L=3 VOID=0 WR=25.0%

---

## 3. EVIDENCE (Rutas y Archivos de Prueba)
- Telemetría periódica: `loops/r23g_validation/window_05\live_samples.jsonl`
- Muestras de `/status`: `loops/r23g_validation/window_05\status_samples.jsonl`
- Eventos de decisión: `loops/r23g_validation/window_05\events.jsonl`
- Decisiones con `decision_id`: `loops/r23g_validation/window_05\decisions.csv`
- Operaciones ejecutadas: `loops/r23g_validation/window_05\trades.csv`
- Ticks capturados en crudo: `loops/r23g_validation/window_05\ticks_live.csv`
- Metadata de la ventana (REQ 1/REQ 2): `loops/r23g_validation/window_05\window_metadata.json`
- Contrafactuales ADAPTIVE_BLOCK (R23E): `loops/r23g_validation/window_05\counterfactual_adaptive_block.csv`
- Comparación de replay: `loops/r23g_validation/window_05\replay_comparison.json` y `loops/r23g_validation/window_05\replay_comparison_report.md`

---

## 4. REGRESSION & SECURITY CHECK
- **Cuenta real:** 0 órdenes, 0 interacciones, 0 fondos comprometidos.
- **Fail-closed:** Guard de seguridad de cuenta DEMO activo en runtime.

---

## 5. RECOMMENDATION
**KEEP**
