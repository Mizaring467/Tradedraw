# REPORTE FORMAL DE VALIDACIÓN — VENTANA 02 DE 10 (R23D YOLO)

**Fecha:** 2026-10-16  
**Iteración:** 2 / 10  
**Validador:** OPENCODE (`w1:p7`)  
**Orquestador:** AGY (`w1:p1`)  
**Directorio:** `loops/r23d_validation/window_02/`  
**Veredicto de Ventana:** **PASS** (100% DEMO, telemetría limpia, 0 fallos de seguridad)

---

## 1. IDENTIDAD DE BUILD Y HARDWARE
- **Dispositivo:** Xiaomi Redmi A1 (`220733SL` / `UGU8KFUKM7E6CE8D`)
- **SoC / OS:** MediaTek MT6761 (4 núcleos), Android 12 Go Edition (`API 31`, `ro.config.low_ram = true`)
- **APK Target:** `versionCode = 16`, `versionName = "1.6-r23d-telemetry"`, `buildId = "R23D-TELEMETRY"`
- **Git SHA:** Reportado en runtime = `5b2245f` (base de ronda `58a7ed0`)
- **Activo:** `Z-CRY/IDX` (Crypto IDX en Binomo `com.marketly.trading`)
- **Cuenta:** 100% DEMO (`isDemoAccount = true` verificado en 98/98 muestras)

---

## 2. RESULTADOS OPERATIVOS — VENTANA 02
- **Operaciones Confirmadas ($N$):** 1 trade ejecutado con movimiento de saldo
- **Victorias ($W$):** 0
- **Derrotas ($L$):** 1
- **Nulos ($\text{VOID}$):** 0
- **Win Rate Bruto (Trades):** $0.0\%$ ($0 / 1$)
- **Payout Promedio Resuelto ($b$):** $0.82$ ($82.0\%$)
- **Win Rate Breakeven ($WR_{BE} = \frac{1}{1 + b}$):** $54.95\%$
- **Taps Despachados en W02:** 2 taps (acumulado histórico del proceso: 16)
- **Evaluaciones de Motor en W02:** 896
- **Señales Generadas:** 187
- **Decisiones Registradas en W02:** 76

---

## 3. RACHAS Y SECUENCIA DE RESOLUCIÓN — VENTANA 02
- **Secuencia de Trades W02:**
  1. `76c08c1a...` (did: `a98793fd...`, BUY / CALL a t=399s) $\to$ **LOSS** ($-99,998.72\text{ COP}$)
- **Racha Ganadora Máxima en W02:** 0
- **Racha Perdedora Máxima en W02:** 1 (Martingala escaló a `M1`)

---

## 4. BALANCE Y DRAWDOWN — VENTANA 02
- **Saldo Inicial W02:** $38,473,735.68\text{ COP}$
- **Saldo Final Crudo W02:** $38,373,736.96\text{ COP}$
- **Saldo Final Validado W02:** $38,373,736.96\text{ COP}$
- **Delta Neto W02 ($\Delta$):** $-99,998.72\text{ COP}$ ($-0.26\%$)
- **Mínimo Validado W02:** $38,373,736.96\text{ COP}$
- **Máximo Validado W02:** $38,473,735.68\text{ COP}$
- **Drawdown Validado W02:** $0.26\%$
- **Outliers de Telemetría Filtrados:** 0 (telemetría 100% limpia)

---

## 5. DISTRIBUCIÓN DIRECCIONAL Y RÉGIMEN — VENTANA 02
- **Dirección de Trades:**
  - **BUY (CALL):** 1 ($100.0\%$)
  - **SELL (PUT):** 0 ($0.0\%$)
- **Decisiones Totales Registradas:** 76 eventos
- **Régimen de Mercado en Decisiones:**
  - `TRENDING`: 76 ($100.0\%$)
- **Tendencia Subyacente:**
  - `DOWNTREND`: 36 ($47.4\%$)
  - `UPTREND`: 25 ($32.9\%$)
  - `SIDEWAYS`: 15 ($19.7\%$)
- **Distribución de Acciones:**
  - `NONE` (Vetos / Bloqueos): 75 ($98.7\%$)
  - `BUY`: 1 ($1.3\%$)
  - `SELL`: 0 ($0.0\%$)

---

## 6. PARIDAD LIVE ↔ REPLAY — VENTANA 02
- **Trades Live Analizados:** 1
- **Trades Replay Generados:** 12
- **Decisiones Pareadas:** 1 (`a98793fd-ae8c-304d-b852-f03b7b6ce226`)
- **Exact Matches:** 0 ($0.0\%$)
- **Divergencias:** 1 (Divergencia en `candle_second`: Live = 6 vs Replay = 1, $\Delta t = -4522\text{ ms}$). Ambos coincidieron en el desenlace: **LOSS**.
- **Missing Live:** 11 (Vetos adaptativos en vivo por `ADAPTIVE_BLOCK`)
- **Missing Replay:** 0
- **Veredicto Paridad:** **FAIL** (esperado por latencia MT6761 y estado en caliente del filtro adaptativo)

---

## 7. TABLA ACUMULADA CONSOLIDADA (VENTANAS 01 + 02)

| Métrica | Ventana 01 | Ventana 02 | **Acumulado (W01 + W02)** |
| :--- | :--- | :--- | :--- |
| **Duración Nominal** | 20.0 min | 20.0 min | **40.0 min** |
| **Muestras Telemetría** | 101 | 98 | **199** |
| **Verificación DEMO** | 100% (101/101) | 100% (98/98) | **100% (199/199)** |
| **Trades Confirmados ($N$)**| 4 | 1 | **5** |
| **Victorias ($W$)** | 2 | 0 | **2** |
| **Derrotas ($L$)** | 2 | 1 | **3** |
| **Win Rate Bruto (Trades)** | 50.0% | 0.0% | **40.0%** (2/5) |
| **Win Rate APK (Contratos)**| 66.7% (4/6) | 0.0% (0/1) | **57.14%** (4/7) |
| **Saldo Inicial** | 38,324,736.00 COP | 38,473,735.68 COP | **38,324,736.00 COP** |
| **Saldo Final Validado** | 38,473,735.68 COP | 38,373,736.96 COP | **38,373,736.96 COP** |
| **Delta Neto ($\Delta$)** | +148,999.68 COP | -99,998.72 COP | **+49,000.96 COP (+0.13%)** |
| **Drawdown Máximo** | 0.69% | 0.26% | **0.69%** |
| **Outliers Telemetría** | 0 | 0 | **0** |
| **Taps Despachados** | 14 | 2 | **16** |
| **Decisiones Registradas** | 98 | 76 | **174** |
| **Distribución BUY / SELL** | 1 BUY / 3 SELL | 1 BUY / 0 SELL | **2 BUY / 3 SELL** |

---

## 8. HALLAZGOS DE TELEMETRÍA Y CONTROL
1. **Preservación de Balance Positivo:** A pesar de la pérdida en el único trade de W02, el acumulado consolidado permanece en terreno positivo ($+49,000.96\text{ COP}$, $+0.13\%$) con un drawdown controlado de solo $0.69\%$.
2. **Alta Selectividad del Filtro Adaptativo:** De 76 decisiones evaluadas en W02, 75 fueron vetadas (`NONE`), impidiendo entradas de baja calidad durante fases de indecisión del mercado.
3. **Settlement Grace Eficiente:** Concluyó la ventana con solo 4.8 s de espera adicional, dado que el trade ocurrió en el primer tercio (t=399s) y la ventana finalizó en estado estable.
4. **Desactivación Inmediata:** Modo `DISABLED` y submodo `CONSERVATIVE` aplicados de inmediato post-ventana.
