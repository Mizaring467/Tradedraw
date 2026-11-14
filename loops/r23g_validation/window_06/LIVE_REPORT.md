# REPORTE DE VALIDACIÓN LIVE — R23 AUTÓNOMO ITERACIÓN 6 (CUENTA DEMO)

**Fecha:** 2026-10-17 05:52:37 UTC  
**Validador:** OPENCODE (`w1:pT`)  
**Orquestador:** AGY (`w1:p1`)  
**Dispositivo Auditado:** Xiaomi Redmi A1 (`UGU8KFUKM7E6CE8D`)  
**Build Identificada:** versionCode=18, buildId=R23F-ADAPTIVE, gitSha=ac6b0b2  
**Modo Auditado:** `AUTONOMOUS` (Cuenta DEMO)  
**Duración de la Ventana:** 20.0 minutos (111 muestras de telemetría)  
**Cierre Nominal / Real:** 1792216286 / 1792216330 (gracia de settlement: 44.6s)  
**Veredicto:** **PASS**  

---

## 1. FACT (Hechos Directamente Observados)
1. **Seguridad DEMO:** Cuenta DEMO verificada 111 veces. Cuentas reales observadas: 0 (0.0%).
2. **Preservación y Dinámica de Balance:** Saldo inicial = 38,432,737.28 COP, saldo final VALIDADO = 38,215,736.32 COP (Δ = -217,000.96 COP). Drawdown validado: 1.04%.
3. **Filtro de Outliers (REQ 2):** 0 lectura(s) de balance descartada(s) como glitch de telemetría (umbral: >1,000,000 COP o >10x stake, sin trade confirmado en la muestra). El drawdown se calcula sobre `validated_balance`, no sobre la lectura cruda.
4. **Ejecución Autónoma:** 24 taps despachados en la ventana (acumulado histórico del proceso: 132). 4 operaciones autónomas registradas.
5. **Identidad de Build:** La APK en ejecución reportó versionCode=18, buildId="R23F-ADAPTIVE", gitSha="ac6b0b2".
6. **Decisiones Observadas:** Se registraron 141 decisiones con `decision_id` determinista; el campo `score` es el puntaje REAL de confluencia extraído del `detail` (vacío cuando el evento nunca pasó por la matriz).
7. **Paridad Live ↔ Replay:** 0 exact matches de 1 pareadas (0.0% de paridad).

---

## 2. DECISION PIPELINE
- Evaluaciones motor: 3565
- Señales generadas: 1093
- Decisiones registradas: 141
- Operaciones DEMO ejecutadas: 4
- Resoluciones: W=2, L=4 (WR = 33.3%)

---

## 2B. CONTRAFACTUAL SHADOW DE `ADAPTIVE_BLOCK` (R23E)

**Naturaleza:** 100% pasivo. El bot real opero con `ADAPTIVE_BLOCK` ACTIVO; estas filas miden que
HABRIA pasado con las señales vetadas. Cero taps, cero fondos expuestos, saldo intacto.

- N_blocked_evaluated (vetos ADAPTIVE_BLOCK vistos): 47
- N_cf_deduplicated (descartados por ocupacion 60s / 1 por vela): 40
- Contrafactuales registrados: 7
- Resueltos: 7 (CF_W=3, CF_L=4, CF_VOID=0)
- **CF_WR = 42.86%** (breakeven del preregistro = 54.95%)
- **CF_EV unitario = -21,571.43 COP** | CF_EV total = -151,000.00 COP (stake 100,000 COP, payout 0.8300000000000001)
- Sin resolver (faltaba tick de entrada/salida): 0

**Bucket de similitud (preregistrado §8.3):**
  - [98,100]%: n=7 W=3 L=4 VOID=0 WR=42.9%

**Bucket de score de confluencia (preregistrado §8.4):**
  - [75,79]pts: n=2 W=1 L=1 VOID=0 WR=50.0%
  - [85,100]pts: n=5 W=2 L=3 VOID=0 WR=40.0%

---

## 3. EVIDENCE (Rutas y Archivos de Prueba)
- Telemetría periódica: `loops/r23g_validation/window_06\live_samples.jsonl`
- Muestras de `/status`: `loops/r23g_validation/window_06\status_samples.jsonl`
- Eventos de decisión: `loops/r23g_validation/window_06\events.jsonl`
- Decisiones con `decision_id`: `loops/r23g_validation/window_06\decisions.csv`
- Operaciones ejecutadas: `loops/r23g_validation/window_06\trades.csv`
- Ticks capturados en crudo: `loops/r23g_validation/window_06\ticks_live.csv`
- Metadata de la ventana (REQ 1/REQ 2): `loops/r23g_validation/window_06\window_metadata.json`
- Contrafactuales ADAPTIVE_BLOCK (R23E): `loops/r23g_validation/window_06\counterfactual_adaptive_block.csv`
- Comparación de replay: `loops/r23g_validation/window_06\replay_comparison.json` y `loops/r23g_validation/window_06\replay_comparison_report.md`

---

## 4. REGRESSION & SECURITY CHECK
- **Cuenta real:** 0 órdenes, 0 interacciones, 0 fondos comprometidos.
- **Fail-closed:** Guard de seguridad de cuenta DEMO activo en runtime.

---

## 5. RECOMMENDATION
**KEEP**
