# REPORTE FORMAL DE VALIDACIÓN — VENTANA 08 DE 10 (R23D YOLO)

**Fecha:** 2026-10-16  
**Iteración:** 8 / 10  
**Validador:** OPENCODE (`w1:p7`)  
**Orquestador:** AGY (`w1:p1`)  
**Directorio:** `loops/r23d_validation/window_08/`  
**Veredicto de Ventana:** **PASS** (100% DEMO, telemetría limpia, 0 fallos de seguridad)

---

## 1. IDENTIDAD DE BUILD Y HARDWARE
- **Dispositivo:** Xiaomi Redmi A1 (`220733SL` / `UGU8KFUKM7E6CE8D`)
- **SoC / OS:** MediaTek MT6761 (4 núcleos), Android 12 Go Edition (`API 31`, `ro.config.low_ram = true`)
- **APK Target:** `versionCode = 16`, `versionName = "1.6-r23d-telemetry"`, `buildId = "R23D-TELEMETRY"`
- **Git SHA:** Reportado en runtime = `5b2245f` (base de ronda `2f17d9b`)
- **Activo:** `Z-CRY/IDX` (Crypto IDX en Binomo `com.marketly.trading`)
- **Cuenta:** 100% DEMO (`isDemoAccount = true` verificado en 87/87 muestras)

---

## 2. RESULTADOS OPERATIVOS — VENTANA 08
- **Operaciones Confirmadas ($N$):** 2 trades ejecutados con movimiento de saldo
- **Victorias ($W$):** 1 trade
- **Derrotas ($L$):** 1 trade
- **Nulos ($\text{VOID}$):** 0
- **Win Rate Bruto (Trades W08):** $50.0\%$ ($1 / 2$)
- **Payout Promedio Resuelto ($b$):** $0.82$ ($82.0\%$)
- **Win Rate Breakeven ($WR_{BE} = \frac{1}{1 + b}$):** $54.95\%$
- **Taps Despachados en W08:** 4 taps (acumulado histórico del proceso: 40)
- **Evaluaciones de Motor en W08:** 2763
- **Señales Generadas:** 811
- **Decisiones Registradas en W08:** 100

---

## 3. RACHAS Y SECUENCIA DE RESOLUCIÓN — VENTANA 08
- **Secuencia de Trades W08:**
  1. `1d198fda-3271-4da6-9e15-039f81537873` (did: `072b5f5b...`, BUY / CALL a t=38s) $\to$ **WIN** (resuelto a t=86s, $+81,999.36\text{ COP}$)
  2. `85f3e5c2-c3f6-47f9-8bbf-e72ffc6a929c` (did: `e2af0f7d...`, BUY / CALL a t=162s) $\to$ **LOSS** (resuelto a t=254s, $-100,000.00\text{ COP}$)
- **Racha Ganadora Máxima en W08:** 1 trade
- **Racha Perdedora Máxima en W08:** 1 trade
- **Martingala post-W08:** `[M1 | $2.0]`

---

## 4. BALANCE Y DRAWDOWN — VENTANA 08
- **Saldo Inicial W08:** $38,319,736.32\text{ COP}$
- **Saldo Final Crudo W08:** $38,301,736.96\text{ COP}$
- **Saldo Final Validado W08:** $38,301,736.96\text{ COP}$
- **Delta Neto W08 ($\Delta$):** $-17,999.36\text{ COP}$ ($-0.047\%$)
- **Mínimo Validado W08:** $38,219,735.04\text{ COP}$ (Trade 1 pre-liquidación)
- **Máximo Validado W08:** $38,401,735.68\text{ COP}$ (post-Trade 1)
- **Drawdown Validado W08:** $0.47\%$
- **Outliers de Telemetría Filtrados:** 0 (telemetría 100% limpia)

---

## 5. DISTRIBUCIÓN DIRECCIONAL Y RÉGIMEN — VENTANA 08
- **Dirección de Trades:**
  - **BUY (CALL):** 2 ($100.0\%$)
  - **SELL (PUT):** 0 ($0.0\%$)
- **Decisiones Totales Registradas:** 100 eventos
- **Régimen de Mercado en Decisiones:**
  - `TRENDING`: 100 ($100.0\%$)
- **Tendencia Subyacente:**
  - `UPTREND`: 80 ($80.0\%$)
  - `DOWNTREND`: 20 ($20.0\%$)
  - `SIDEWAYS`: 0 ($0.0\%$)
- **Distribución de Acciones:**
  - `NONE` (Vetos / Bloqueos): 99 ($99.0\%$)
  - `BUY`: 1 ($1.0\%$)
  - `SELL`: 0 ($0.0\%$)

---

## 6. PARIDAD LIVE ↔ REPLAY — VENTANA 08
- **Trades Live Analizados:** 2
- **Trades Replay Generados:** 10
- **Decisiones Pareadas:** 1 (`072b5f5b-f8a6-359c-8fce-a58a469920d0`)
- **Exact Matches:** 0 ($0.0\%$ debido a divergencia en latencia `candle_second`)
- **Coincidencia de Desenlace:** Tanto Live como Replay coincidieron exactamente en el desenlace: **WIN**.
- **Missing Live:** 9 (Vetos adaptativos en vivo por `ADAPTIVE_BLOCK`)
- **Missing Replay:** 1
- **Veredicto Paridad:** **DIVERGENTE EN LATENCIA / CONCORDANCIA EXACTA DE DESENLACE EN PAR MATCH**

---

## 7. TABLA ACUMULADA CONSOLIDADA (VENTANAS 01 A 08 — 80% DE R23D)

| Métrica | W01 | W02 | W03 | W04 | W05 | W06 | W07 | W08 | **Acumulado (W01..W08)** |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **Duración** | 20.0 m | 20.0 m | 20.0 m | 15.1 m | 20.0 m | 20.0 m | 20.0 m | 20.0 m | **155.1 min** |
| **Muestras Telemetría** | 101 | 98 | 118 | 89 | 104 | 99 | 96 | 87 | **792** |
| **Verificación DEMO** | 100% | 100% | 100% | 100% | 100% | 100% | 100% | 100% | **100% (792/792)** |
| **Trades Confirmados ($N$)**| 4 | 1 | 1 | 0 | 3 | 1 | 1 | 2 | **13** |
| **Victorias ($W$)** | 2 | 0 | 0 | 0 | 2 | 1 | 0 | 1 | **6** |
| **Derrotas ($L$)** | 2 | 1 | 1 | 0 | 1 | 0 | 1 | 1 | **7** |
| **Win Rate Bruto (Trades)** | 50.0% | 0.0% | 0.0% | N/A | 66.7% | 100.0%| 0.0% | 50.0% | **46.15%** (6/13) |
| **Win Rate APK (Contratos)**| 66.7% | 0.0% | 0.0% | N/A | 66.7% | 100.0%| 0.0% | 50.0% | **52.94%** (9/17) |
| **Saldo Inicial (COP)** | 38.32M | 38.47M | 38.37M | 38.27M | 38.27M | 38.34M | 38.42M | 38.32M | **38,324,736.00 COP** |
| **Saldo Final (COP)** | 38.47M | 38.37M | 38.27M | 38.27M | 38.34M | 38.42M | 38.32M | 38.30M | **38,301,736.96 COP** |
| **Delta Neto ($\Delta$)** | +149K | -100K | -100K | 0 | +64K | +82K | -100K | -18K | **-22,999.04 COP (-0.060%)** |
| **Drawdown Máximo Global** | 0.69% | 0.26% | 0.26% | 0.00% | 0.52% | 0.47% | 0.26% | 0.47% | **0.87%** |
| **Outliers Telemetría** | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | **0** |
| **Taps Despachados** | 14 | 2 | 2 | 8 | 6 | 2 | 2 | 4 | **40** |
| **Decisiones Registradas** | 98 | 76 | 94 | 8 | 127 | 125 | 83 | 100 | **711** |
| **Distribución BUY / SELL** | 1 / 3 | 1 / 0 | 1 / 0 | 0 / 0 | 2 / 1 | 1 / 0 | 1 / 0 | 2 / 0 | **9 BUY / 4 SELL (69% / 31%)** |

---

## 8. HALLAZGOS DE TELEMETRÍA Y CONTROL
1. **Preservación Robusta del Capital:** A lo largo de 8 ventanas y 155 minutos de operación en submodo YOLO, la variación total de la cuenta es de apenas **$-22,999.04\text{ COP}$** ($-0.060\%$). La cuenta demo está prácticamente en punto de equilibrio estricto sin sufrir sangrado de saldo.
2. **Drawdown Global Extraordinariamente Acotado:** El drawdown máximo global consolidado tras 8 iteraciones se mantiene en **$0.87\%$**, confirmando que los filtros y vetos previenen caídas profundas de balance.
3. **Concordancia Replay en Trades Clave:** Trade 1 coincidió en WIN tanto en vivo como en el motor de replay offline.
4. **Desactivación Inmediata:** Modo `DISABLED` y submodo `CONSERVATIVE` aplicados de inmediato post-ventana.
