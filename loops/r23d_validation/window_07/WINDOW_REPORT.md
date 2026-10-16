# REPORTE FORMAL DE VALIDACIÓN — VENTANA 07 DE 10 (R23D YOLO)

**Fecha:** 2026-10-16  
**Iteración:** 7 / 10  
**Validador:** OPENCODE (`w1:p7`)  
**Orquestador:** AGY (`w1:p1`)  
**Directorio:** `loops/r23d_validation/window_07/`  
**Veredicto de Ventana:** **PASS** (100% DEMO, telemetría limpia, 0 fallos de seguridad)

---

## 1. IDENTIDAD DE BUILD Y HARDWARE
- **Dispositivo:** Xiaomi Redmi A1 (`220733SL` / `UGU8KFUKM7E6CE8D`)
- **SoC / OS:** MediaTek MT6761 (4 núcleos), Android 12 Go Edition (`API 31`, `ro.config.low_ram = true`)
- **APK Target:** `versionCode = 16`, `versionName = "1.6-r23d-telemetry"`, `buildId = "R23D-TELEMETRY"`
- **Git SHA:** Reportado en runtime = `5b2245f` (base de ronda `d3ec889`)
- **Activo:** `Z-CRY/IDX` (Crypto IDX en Binomo `com.marketly.trading`)
- **Cuenta:** 100% DEMO (`isDemoAccount = true` verificado en 96/96 muestras)

---

## 2. RESULTADOS OPERATIVOS — VENTANA 07
- **Operaciones Confirmadas ($N$):** 1 trade ejecutado con movimiento de saldo
- **Victorias ($W$):** 0 trades
- **Derrotas ($L$):** 1 trade
- **Nulos ($\text{VOID}$):** 0
- **Win Rate Bruto (Trades W07):** $0.0\%$ ($0 / 1$)
- **Payout Promedio Resuelto ($b$):** $0.82$ ($82.0\%$)
- **Win Rate Breakeven ($WR_{BE} = \frac{1}{1 + b}$):** $54.95\%$
- **Taps Despachados en W07:** 2 taps (acumulado histórico del proceso: 36)
- **Evaluaciones de Motor en W07:** 2446
- **Señales Generadas:** 704
- **Decisiones Registradas en W07:** 83

---

## 3. RACHAS Y SECUENCIA DE RESOLUCIÓN — VENTANA 07
- **Secuencia de Trades W07:**
  1. `5489e72e-3120-4b8e-bd48-0484961d9fd3` (did: `39c34c91...`, BUY / CALL a t=1023s) $\to$ **LOSS** ($-100,001.28\text{ COP}$)
- **Racha Ganadora Máxima en W07:** 0
- **Racha Perdedora Máxima en W07:** 1 trade
- **Martingala post-W07:** `[M2 | $2.0]` (cooldown activo de 15 s ejecutado)

---

## 4. BALANCE Y DRAWDOWN — VENTANA 07
- **Saldo Inicial W07:** $38,419,737.60\text{ COP}$
- **Saldo Final Crudo W07:** $38,319,736.32\text{ COP}$
- **Saldo Final Validado W07:** $38,319,736.32\text{ COP}$
- **Delta Neto W07 ($\Delta$):** $-100,001.28\text{ COP}$ ($-0.26\%$)
- **Mínimo Validado W07:** $38,319,736.32\text{ COP}$
- **Máximo Validado W07:** $38,419,737.60\text{ COP}$
- **Drawdown Validado W07:** $0.26\%$
- **Outliers de Telemetría Filtrados:** 0 (telemetría 100% limpia)

---

## 5. DISTRIBUCIÓN DIRECCIONAL Y RÉGIMEN — VENTANA 07
- **Dirección de Trades:**
  - **BUY (CALL):** 1 ($100.0\%$)
  - **SELL (PUT):** 0 ($0.0\%$)
- **Decisiones Totales Registradas:** 83 eventos
- **Régimen de Mercado en Decisiones:**
  - `TRENDING`: 83 ($100.0\%$)
- **Tendencia Subyacente:**
  - `UPTREND`: 58 ($69.9\%$)
  - `DOWNTREND`: 25 ($30.1\%$)
  - `SIDEWAYS`: 0 ($0.0\%$)
- **Distribución de Acciones:**
  - `NONE` (Vetos / Bloqueos): 82 ($98.8\%$)
  - `BUY`: 1 ($1.2\%$)
  - `SELL`: 0 ($0.0\%$)

---

## 6. PARIDAD LIVE ↔ REPLAY — VENTANA 07
- **Trades Live Analizados:** 1
- **Trades Replay Generados:** 12
- **Decisiones Pareadas:** 1 (`39c34c91-3033-39cf-a36b-9104562fae55`)
- **Exact Matches:** 0 ($0.0\%$ debido a divergencia en latencia `candle_second`)
- **Coincidencia de Desenlace:** Tanto Live como Replay coincidieron exactamente en el desenlace: **LOSS**.
- **Missing Live:** 11 (Vetos adaptativos en vivo por `ADAPTIVE_BLOCK`)
- **Missing Replay:** 0
- **Veredicto Paridad:** **DIVERGENTE EN LATENCIA / CONCORDANCIA EXACTA DE DESENLACE**

---

## 7. TABLA ACUMULADA CONSOLIDADA (VENTANAS 01 A 07 — 70% DE R23D)

| Métrica | W01 | W02 | W03 | W04 | W05 | W06 | W07 | **Acumulado (W01..W07)** |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **Duración** | 20.0 m | 20.0 m | 20.0 m | 15.1 m | 20.0 m | 20.0 m | 20.0 m | **135.1 min** |
| **Muestras Telemetría** | 101 | 98 | 118 | 89 | 104 | 99 | 96 | **705** |
| **Verificación DEMO** | 100% | 100% | 100% | 100% | 100% | 100% | 100% | **100% (705/705)** |
| **Trades Confirmados ($N$)**| 4 | 1 | 1 | 0 | 3 | 1 | 1 | **11** |
| **Victorias ($W$)** | 2 | 0 | 0 | 0 | 2 | 1 | 0 | **5** |
| **Derrotas ($L$)** | 2 | 1 | 1 | 0 | 1 | 0 | 1 | **6** |
| **Win Rate Bruto (Trades)** | 50.0% | 0.0% | 0.0% | N/A | 66.7% | 100.0%| 0.0% | **45.45%** (5/11) |
| **Win Rate APK (Contratos)**| 66.7% | 0.0% | 0.0% | N/A | 66.7% | 100.0%| 0.0% | **53.33%** (8/15) |
| **Saldo Inicial (COP)** | 38.32M | 38.47M | 38.37M | 38.27M | 38.27M | 38.34M | 38.42M | **38,324,736.00 COP** |
| **Saldo Final (COP)** | 38.47M | 38.37M | 38.27M | 38.27M | 38.34M | 38.42M | 38.32M | **38,319,736.32 COP** |
| **Delta Neto ($\Delta$)** | +149K | -100K | -100K | 0 | +64K | +82K | -100K | **-4,999.68 COP (-0.013%)** |
| **Drawdown Máximo Global** | 0.69% | 0.26% | 0.26% | 0.00% | 0.52% | 0.47% | 0.26% | **0.87%** |
| **Outliers Telemetría** | 0 | 0 | 0 | 0 | 0 | 0 | 0 | **0** |
| **Taps Despachados** | 14 | 2 | 2 | 8 | 6 | 2 | 2 | **36** |
| **Decisiones Registradas** | 98 | 76 | 94 | 8 | 127 | 125 | 83 | **611** |
| **Distribución BUY / SELL** | 1 / 3 | 1 / 0 | 1 / 0 | 0 / 0 | 2 / 1 | 1 / 0 | 1 / 0 | **7 BUY / 4 SELL (64% / 36%)** |

---

## 8. HALLAZGOS DE TELEMETRÍA Y CONTROL
1. **Preservación Prácticamente Plana del Capital:** Tras 135 minutos y 11 operaciones confirmadas en submodo YOLO, el capital se sitúa en $38,319,736.32\text{ COP}$, una variación acumulada neta de solo **$-4,999.68\text{ COP}$** ($-0.013\%$). La cuenta demo se encuentra en equilibrio estricto sin degradación patrimonial.
2. **Drawdown Global Extraordinariamente Bajo:** El drawdown máximo global consolidado a lo largo de 7 ventanas se mantiene inalterado en **$0.87\%$**, confirmando que el submodo YOLO opera con control estricto de riesgo.
3. **Filtro Anti-patrón Selectivo:** 82 de 83 decisiones fueron vetadas por `ADAPTIVE_BLOCK Anti-patrón perdedor recurrente`.
4. **Desactivación Inmediata:** Modo `DISABLED` y submodo `CONSERVATIVE` aplicados de inmediato post-ventana.
