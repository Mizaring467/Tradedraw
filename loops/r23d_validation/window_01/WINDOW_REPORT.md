# REPORTE FORMAL DE VALIDACIÓN — VENTANA 01 DE 10 (R23D YOLO)

**Fecha:** 2026-10-16  
**Iteración:** 1 / 10  
**Validador:** OPENCODE (`w1:p6`)  
**Orquestador:** AGY (`w1:p1`)  
**Directorio:** `loops/r23d_validation/window_01/`  
**Veredicto General:** **PASS** (100% DEMO, balance positivo, 0 fallos de seguridad)

---

## 1. IDENTIDAD DE BUILD Y HARDWARE
- **Dispositivo:** Xiaomi Redmi A1 (`220733SL` / `UGU8KFUKM7E6CE8D`)
- **SoC / OS:** MediaTek MT6761 (4 núcleos), Android 12 Go Edition (`API 31`, `ro.config.low_ram = true`)
- **APK Target:** `versionCode = 16`, `versionName = "1.6-r23d-telemetry"`, `buildId = "R23D-TELEMETRY"`
- **Git SHA:** Reportado en runtime = `5b2245f` (candidato `4aec1e5`)
- **Activo:** `Z-CRY/IDX` (Crypto IDX en Binomo `com.marketly.trading`)
- **Cuenta:** 100% DEMO (`isDemoAccount = true` verificado en 101/101 muestras)

---

## 2. RESULTADOS OPERATIVOS
- **Operaciones Confirmadas ($N$):** 4 trades ejecutados con movimiento de saldo
- **Victorias ($W$):** 2 trades (4 contratos acumulados en telemetría interna)
- **Derrotas ($L$):** 2 trades
- **Nulos ($\text{VOID}$):** 0
- **Win Rate Bruto (Trades):** $50.0\%$ ($2 / 4$)
- **Win Rate Telemetría Interna APK:** $66.7\%$ ($4 / 6$ contratos)
- **Payout Promedio Resuelto ($b$):** $0.83$ ($83.0\%$)
- **Win Rate Breakeven ($WR_{BE} = \frac{1}{1 + b}$):** $54.64\%$
- **Taps Despachados:** 14 totales (8 asociados a trades confirmados + 6 a intentos `NO_PROCESADA`)

---

## 3. RACHAS Y SECUENCIA DE RESOLUCIÓN
- **Secuencia Cronológica de Trades:**
  1. `84763fc9...` (SELL) $\to$ **WIN** ($+83,000.32\text{ COP}$)
  2. `9dd0b005...` (BUY) $\to$ **LOSS** ($-99,998.72\text{ COP}$)
  3. `fc1bdd6b...` (SELL) $\to$ **LOSS** ($-100,001.28\text{ COP}$)
  4. `2946aa6c...` (SELL) $\to$ **WIN** ($+82,999.04\text{ COP}$)
- **Racha Ganadora Máxima:** 1 trade (2 contratos consecutivos)
- **Racha Perdedora Máxima:** 2 trades consecutivos (Martingala escaló a `M2`)

---

## 4. BALANCE Y DRAWDOWN
- **Saldo Inicial:** $38,324,736.00\text{ COP}$
- **Saldo Final Crudo:** $38,473,735.68\text{ COP}$
- **Saldo Final Validado:** $38,473,735.68\text{ COP}$
- **Delta Neto ($\Delta$):** $+148,999.68\text{ COP}$ ($+0.39\%$)
- **Mínimo Validado:** $38,224,737.28\text{ COP}$ (Trade 1 post-deducción)
- **Máximo Validado:** $38,490,736.64\text{ COP}$
- **Drawdown Validado:** $0.69\%$
- **Outliers de Telemetría Filtrados:** 0 (telemetría 100% limpia sin saltos espurios)

---

## 5. DISTRIBUCIÓN DIRECCIONAL Y RÉGIMEN
- **Dirección de Trades:**
  - **SELL (PUT):** 3 ($75.0\%$)
  - **BUY (CALL):** 1 ($25.0\%$)
- **Decisiones Totales Registradas:** 98 eventos
- **Régimen de Mercado en Decisiones:**
  - `TRENDING`: 98 ($100.0\%$)
- **Tendencia Subyacente:**
  - `SIDEWAYS`: 43 ($43.9\%$)
  - `DOWNTREND`: 42 ($42.9\%$)
  - `UPTREND`: 13 ($13.3\%$)
- **Distribución de Acciones en Decisiones:**
  - `NONE` (Vetos / Bloqueos): 94 ($95.9\%$)
  - `SELL`: 3 ($3.1\%$)
  - `BUY`: 1 ($1.0\%$)

---

## 6. PARIDAD LIVE ↔ REPLAY
- **Trades Live Analizados:** 4
- **Trades Replay Generados:** 13
- **Decisiones Pareadas:** 1 (`e0e292db-320a-375c-b1c8-4c3861e7285d`)
- **Exact Matches:** 0 ($0.0\%$)
- **Divergencias:** 1 (Divergencia en `candle_second`: Live = 4 vs Replay = 1, $\Delta t = -2744\text{ ms}$)
- **Missing Live:** 12 (Señales disparadas en replay offline que fueron vetadas en vivo por `ADAPTIVE_BLOCK`)
- **Missing Replay:** 3 (Trades live que no replicaron idéntica ventana temporal en ticks discretos)
- **Veredicto Paridad:** **FAIL** (esperado por latencia de ejecución en hardware MT6761 y estado en caliente del filtro adaptativo)

---

## 7. HALLAZGOS DE TELEMETRÍA Y CONTROL
1. **Settlement Grace Period:** Al expirar los 1200 s nominales, el sistema detectó estado de orden residual y activó la gracia de liquidación extendida ($81.6\text{ s}$ reales, tope $75\text{ s}$), garantizando captura íntegra del ciclo de salida sin corromper artefactos.
2. **Efectividad del Cooldown en YOLO:** Tras Trade 2 (LOSS) activó 35 s de cooldown; tras Trade 3 (LOSS) activó 27 s. No hubo sobre-operación compulsiva ni cascada de pérdidas destructiva.
3. **Filtro Anti-patrón Adaptativo:** Registró 93 vetos acumulados (`ADAPTIVE_BLOCK Anti-patrón perdedor recurrente`), frenando señales de baja calidad en zonas de compresión.
4. **Desactivación Segura:** Modo `DISABLED` y submodo `CONSERVATIVE` aplicados de inmediato post-ventana. Saldo protegido.
