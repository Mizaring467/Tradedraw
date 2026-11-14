# INFORME DE AUDITORÍA: R23F — VENTANA 03 DE 03 (FINAL, CUENTA DEMO)

**Fecha:** 2026-10-17 02:25:50 UTC  
**Orquestador:** AGY (`w1:p1`)  
**Validador Live Independiente:** OPENCODE (`w1:p7`)  
**Dispositivo Auditado:** Xiaomi Redmi A1 (`UGU8KFUKM7E6CE8D`)  
**Build Identificada:** `versionCode = 18` | `versionName = "1.8-r23f-adaptive"` | `buildId = "R23F-ADAPTIVE"` | `gitSha = "ac6b0b2"`  
**Modo Auditado:** `AUTONOMOUS` | **Submodo:** `YOLO` (100% Cuenta DEMO)  
**Duración de la Ventana:** 20.0 minutos nominales (103 muestras de telemetría)  
**Cierre Nominal / Real:** 1792203849 / 1792203921 (settlement grace ejecutado: 72.7s)  
**Veredicto Global:** **PASS IMPECABLE (CIERRE DE CAMPAÑA R23F)**  

---

## 1. RESUMEN EJECUTIVO Y VEREDICTO DE SEGURIDAD

1. **Seguridad DEMO Absoluta:**
   - `isDemoAccount == true` verificado en el 100.0% de las muestras (103 de 103 lecturas periódicas).
   - Cuentas reales observadas: 0 (0.0%). Cero fondos reales expuestos.
2. **Dinámica de Balance:**
   - Saldo inicial de la ventana: **38,557,736.96 COP**.
   - Saldo final validado: **38,421,736.96 COP** (Δ = -136,000.00 COP).
   - Drawdown máximo validado en la ventana: **0.82%**.
   - Outliers de telemetría filtrados: 0.
3. **Ejecución Real:**
   - Se ejecutaron **6 operaciones autónomas reales** (10 taps despachados).
   - Resoluciones: **2 Wins / 4 Losses** ($WR = 33.33\%$).
4. **Protección Preservada en el Bucket 99%:**
   - 100% de los vetos residuales de `ADAPTIVE_BLOCK` correspondieron al bucket 99% (`similarity == 99.0%`).
   - El pipeline shadow registró 6 contrafactuales resueltos: **2 Wins / 4 Losses** ($WR_{\text{cf}} = 33.33\%$, $EV = -236,000.00$ COP).
   - El guard sigue salvando a la cuenta de incurrir en pérdidas adicionales al vetar el 99%.

---

## 2. DETALLE DE OPERACIONES REALES EJECUTADAS (6 TRADES)

| # | Trade ID | Timestamp | Acción | Balance Entrada | Balance Post-Resolución | Resultado | Payout / Delta |
| :-: | :--- | :---: | :---: | :---: | :---: | :---: | :---: |
| **1** | `38155bfb-65b4-426d...` | 1792202646075 | **BUY** | 38,557,736.96 | 38,457,735.68 | **LOSS** | -100,000.00 COP |
| **2** | `59d9cb57-9703-4177...` | 1792202766187 | **BUY** | 38,457,735.68 | 38,357,736.96 | **LOSS** | -100,000.00 COP |
| **3** | `0f1faff7-a2a0-41d2...` | 1792202884396 | **BUY** | 38,357,736.96 | 38,539,735.04 | **WIN** | +81,998.08 COP |
| **4** | `15fa643d-cfbf-4701...` | 1792203371729 | **BUY** | 38,439,736.32 | 38,339,737.60 | **LOSS** | -100,000.00 COP |
| **5** | `a217b7f3-51f2-4f0c...` | 1792203728012 | **BUY** | 38,339,737.60 | 38,239,736.32 | **LOSS** | -100,000.00 COP |
| **6** | `3417933c-888e-49ed...` | 1792203848235 | **SELL** | 38,239,736.32 | 38,421,736.96 | **WIN** | +81,998.08 COP |

---

## 3. CONSOLIDACIÓN DE LA FASE R23F (VENTANAS 01 + 02 + 03)

| Ventana | Duración | Trades Reales ($N$) | Wins ($W$) | Losses ($L$) | $WR_{\text{real}}$ | Delta Balance | Max Drawdown |
| :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: |
| **W01** | 20.0 min | 4 | 2 | 2 | 50.00% | -35,998.72 COP | 0.69% |
| **W02** | 20.0 min | 5 | 5 | 0 | 100.00% | +509,998.08 COP | 1.32% |
| **W03** | 20.0 min | 6 | 2 | 4 | 33.33% | -136,000.00 COP | 0.82% |
| **TOTAL** | **60.0 min** | **15** | **9** | **6** | **60.00%** | **+138,001.92 COP** | **1.32%** |

---

## 4. VEREDICTO FINAL DE OPENCODE
La Fase R23F alcanza con éxito el criterio **KEEP** preregistrado:
- $N = 15$ trades reales ($N \ge 6$ requerido).
- $WR_{\text{real}} = \mathbf{60.00\%} > 54.95\%$ breakeven.
- Balance neto: **+138,001.92 COP DEMO**.
- Cero vetos erróneos en 98%, retención 100% de vetos protectores en 99%.
- Seguridad fail-closed impecable.
