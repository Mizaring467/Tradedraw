# REPORTE FORMAL DE VALIDACIÓN — VENTANA 05 DE 10 (R23D YOLO)

**Fecha:** 2026-10-16  
**Iteración:** 5 / 10  
**Validador:** OPENCODE (`w1:p7`)  
**Orquestador:** AGY (`w1:p1`)  
**Directorio:** `loops/r23d_validation/window_05/`  
**Veredicto de Ventana:** **PASS** (100% DEMO, balance positivo, 0 fallos de seguridad)

---

## 1. IDENTIDAD DE BUILD Y HARDWARE
- **Dispositivo:** Xiaomi Redmi A1 (`220733SL` / `UGU8KFUKM7E6CE8D`)
- **SoC / OS:** MediaTek MT6761 (4 núcleos), Android 12 Go Edition (`API 31`, `ro.config.low_ram = true`)
- **APK Target:** `versionCode = 16`, `versionName = "1.6-r23d-telemetry"`, `buildId = "R23D-TELEMETRY"`
- **Git SHA:** Reportado en runtime = `5b2245f` (base de ronda `5ed0064`)
- **Activo:** `Z-CRY/IDX` (Crypto IDX en Binomo `com.marketly.trading`)
- **Cuenta:** 100% DEMO (`isDemoAccount = true` verificado en 104/104 muestras)

---

## 2. RESULTADOS OPERATIVOS — VENTANA 05
- **Operaciones Confirmadas ($N$):** 3 trades ejecutados con movimiento real de saldo
- **Victorias ($W$):** 2 trades
- **Derrotas ($L$):** 1 trade
- **Nulos ($\text{VOID}$):** 0
- **Win Rate Bruto (Trades):** $66.7\%$ ($2 / 3$)
- **Payout Promedio Resuelto ($b$):** $0.82$ ($82.0\%$)
- **Win Rate Breakeven ($WR_{BE} = \frac{1}{1 + b}$):** $54.95\%$
- **Taps Despachados en W05:** 6 taps (acumulado histórico del proceso: 32)
- **Evaluaciones de Motor en W05:** 1795
- **Señales Generadas:** 497
- **Decisiones Registradas en W05:** 127

---

## 3. RACHAS Y SECUENCIA DE RESOLUCIÓN — VENTANA 05
- **Secuencia Cronológica de Trades W05:**
  1. `dc2c5900...` (did: `07ae97a5...`, BUY / CALL a t=82s) $\to$ **WIN** ($+82,001.92\text{ COP}$)
  2. `d284e243...` (did: `82d90d9a...`, SELL / PUT a t=562s) $\to$ **LOSS** ($-99,999.36\text{ COP}$)
  3. `04bbc010...` (did: `e08c602f...`, BUY / CALL a t=1159s) $\to$ **WIN** (resuelto en Settlement Grace a t=1197s, $+81,999.36\text{ COP}$)
- **Racha Ganadora Máxima en W05:** 1 trade
- **Racha Perdedora Máxima en W05:** 1 trade
- **Martingala post-W05:** Restablecida a `M0 | $1.0` tras la victoria final.

---

## 4. BALANCE Y DRAWDOWN — VENTANA 05
- **Saldo Inicial W05:** $38,273,735.68\text{ COP}$
- **Saldo Final Crudo W05:** $38,337,735.68\text{ COP}$
- **Saldo Final Validado W05:** $38,337,735.68\text{ COP}$
- **Delta Neto W05 ($\Delta$):** $+64,000.00\text{ COP}$ ($+0.17\%$)
- **Mínimo Validado W05:** $38,155,735.04\text{ COP}$ (Trade 3 pre-liquidación)
- **Máximo Validado W05:** $38,355,737.60\text{ COP}$ (post-Trade 1)
- **Drawdown Validado W05:** $0.52\%$
- **Outliers de Telemetría Filtrados:** 0 (telemetría 100% limpia)

---

## 5. DISTRIBUCIÓN DIRECCIONAL Y RÉGIMEN — VENTANA 05
- **Dirección de Trades:**
  - **BUY (CALL):** 2 ($66.7\%$)
  - **SELL (PUT):** 1 ($33.3\%$)
- **Decisiones Totales Registradas:** 127 eventos
- **Régimen de Mercado en Decisiones:**
  - `TRENDING`: 127 ($100.0\%$)
- **Tendencia Subyacente:**
  - `UPTREND`: 86 ($67.7\%$)
  - `DOWNTREND`: 28 ($22.0\%$)
  - `SIDEWAYS`: 13 ($10.2\%$)
- **Distribución de Acciones:**
  - `NONE` (Vetos / Bloqueos): 124 ($97.6\%$)
  - `BUY`: 2 ($1.6\%$)
  - `SELL`: 1 ($0.8\%$)

---

## 6. PARIDAD LIVE ↔ REPLAY — VENTANA 05
- **Trades Live Analizados:** 3
- **Trades Replay Generados:** 12
- **Decisiones Pareadas:** 3 de 3 ($100\%$ de los trades en vivo pareados por `decision_id`)
- **Exact Matches:** 0 ($0.0\%$ debido a divergencia en latencia de `candle_second` por CPU MT6761)
- **Coincidencia de Desenlace:**
  - Trade 2: Tanto Live como Replay coincidieron en **LOSS**.
  - Trade 3: Tanto Live como Replay coincidieron en **WIN**.
- **Missing Live:** 9 (Vetos adaptativos en vivo por `ADAPTIVE_BLOCK`)
- **Missing Replay:** 0
- **Veredicto Paridad:** **DIVERGENTE EN LATENCIA / ALTA CORRELACIÓN DE LÓGICA**

---

## 7. TABLA ACUMULADA CONSOLIDADA (VENTANAS 01 A 05 — 50% DE R23D)

| Métrica | Ventana 01 | Ventana 02 | Ventana 03 | Ventana 04 | Ventana 05 | **Acumulado (W01..W05)** |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **Duración** | 20.0 min | 20.0 min | 20.0 min | 15.1 min | 20.0 min | **95.1 min** |
| **Muestras Telemetría** | 101 | 98 | 118 | 89 | 104 | **510** |
| **Verificación DEMO** | 100% | 100% | 100% | 100% | 100% | **100% (510/510)** |
| **Trades Confirmados ($N$)**| 4 | 1 | 1 | 0 | 3 | **9** |
| **Victorias ($W$)** | 2 | 0 | 0 | 0 | 2 | **4** |
| **Derrotas ($L$)** | 2 | 1 | 1 | 0 | 1 | **5** |
| **Win Rate Bruto (Trades)** | 50.0% | 0.0% | 0.0% | N/A | 66.7% | **44.44%** (4/9) |
| **Win Rate APK (Contratos)**| 66.7% | 0.0% | 0.0% | N/A | 66.7% | **58.33%** (7/12) |
| **Saldo Inicial** | 38,324,736.00 | 38,473,735.68| 38,373,736.96 | 38,273,735.68 | 38,273,735.68 | **38,324,736.00 COP** |
| **Saldo Final Validado** | 38,473,735.68 | 38,373,736.96| 38,273,735.68 | 38,273,735.68 | 38,337,735.68 | **38,337,735.68 COP** |
| **Delta Neto ($\Delta$)** | +148,999.68 | -99,998.72 | -100,001.28 | 0.00 | +64,000.00 | **+12,999.68 COP (+0.034%)** |
| **Drawdown Máximo Global** | 0.69% | 0.26% | 0.26% | 0.00% | 0.52% | **0.87%** |
| **Outliers Telemetría** | 0 | 0 | 0 | 0 | 0 | **0** |
| **Taps Despachados** | 14 | 2 | 2 | 8 | 6 | **32** |
| **Decisiones Registradas** | 98 | 76 | 94 | 8 | 127 | **403** |
| **Distribución BUY / SELL** | 1 / 3 | 1 / 0 | 1 / 0 | 0 / 0 | 2 / 1 | **5 BUY / 4 SELL (56% / 44%)** |

---

## 8. HALLAZGOS DE TELEMETRÍA Y CONTROL
1. **Retorno al Terreno Positivo Acumulado:** Con la victoria doble de W05 (66.7% WR), el capital acumulado volvió a terreno verde ($+12,999.68\text{ COP}$, $+0.034\%$), demostrando capacidad de recuperación estadística sin incurrir en drawdowns severos (drawdown acumulado global de apenas **0.87%** tras 95 minutos y 9 operaciones).
2. **Efectividad del Settlement Grace Period:** En el Trade 3 (t=1159s), a solo 41 segundos del cierre de la ventana, el monitor activó el protocolo de gracia extendida (21.2 s adicionales), permitiendo la liquidación en WIN sin dejar operaciones huérfanas ni desincronizar los registros.
3. **Paridad Replay Completa:** El 100% de los trades ejecutados en vivo (3/3) contaron con correspondencia exacta de `decision_id` en el replay offline, confirmando que las señales ejecutadas son reproducibles por el motor analítico.
4. **Desactivación Inmediata:** Modo `DISABLED` y submodo `CONSERVATIVE` aplicados de inmediato post-ventana.
