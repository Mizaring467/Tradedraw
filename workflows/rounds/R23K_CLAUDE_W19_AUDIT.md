# AUDITORÍA FORMAL INDEPENDIENTE — VENTANA 19 (W19)
**Loop:** R23K · **Rama:** `r10-night` · **Build:** `8cdb61f` (`versionCode = 19`)  
**Auditor:** CLAUDE CODE (`w1:pC`, Auditor Independiente)  
**Fecha:** 2026-10-19 15:15:00 -05:00 (20:15:00 UTC)  
**Directorio Auditado:** `loops/r23k_validation/window_19/`  
**Dictamen Formal:** PASS_WITH_LIMITATION (VENTANA VÁLIDA EN HARDWARE · LEDGER CERTIFICADO CON DISCREPANCIA $0.00 COP · 4 TRADES REALES: 2 WIN / 2 LOSS · SALVEDAD DE CONTADOR RUNTIME DE DOBLE RESOLUCIÓN RESUELTA · PARIDAD REPLAY 100% EN PARES EVALUADOS)

---

## 1. TABLA RESUMEN DE CONCILIACIÓN Y VERIFICACIÓN

| Ítem Auditado | Especificación / Preflight | Medición Directa W19 | Veredicto |
|---|---|---|:---:|
| **Seguridad de Cuenta** | 100% DEMO, 0 real | 92/92 muestras `isDemoAccount == true` (0 real) | **PASS** |
| **Integridad de Build** | `gitSha=8cdb61f`, `vCode=19` | `8cdb61f`, `versionCode=19`, `buildId=R23H-BUY-EXHAUSTION` | **PASS** |
| **Cierre Monitor v2** | Fin nominal + gracia $\to$ `DISABLED` | `mode_log.json` atMs 1792440497701: `AUTONOMOUS` $\to$ `DISABLED` | **PASS** |
| **Periodo de Gracia** | Gracia hasta 150 s si trade en vuelo | `grace_wait_ms: 0`, `grace_resultado: SIN_TRADE_EN_VUELO`, `orderState: NINGUNA` | **PASS** |
| **Saldo Inicial Certificado** | Cierre W18: 38,863,736.32 COP | 38,863,736.32 COP DEMO (muestra 1 / metadata) | **PASS** |
| **Saldo Final Validado** | Cierre W19: 38,827,735.04 COP | 38,827,735.04 COP DEMO (muestra 92 / metadata) | **PASS** |
| **Delta Contable Neto** | -36,001.28 COP DEMO | **-36,001.28 COP DEMO** | **PASS** |
| **Discrepancia Ledger** | $0.00 COP | **$0.00 COP** ($38,863,736.32 - 36,001.28 - 38,827,735.04) | **PASS** |
| **Discrepancia Inter-Ventana** | Cero centavos vs W18 | **$0.00 COP** ($38,863,736.32 - 38,863,736.32) | **PASS** |
| **Drawdown Máximo Real** | $< 3.50\%$ (tope estricto) | **0.51%** (vs inicial) / **0.68%** (valle vs pico máximo) | **PASS** |
| **Operaciones Confirmadas** | `trades.csv` | 4 operaciones reales (2 W / 2 L / 0 VOID, WR 50.00%) | **PASS** |
| **Consistencia Contadores** | $W_{\text{net}} + L_{\text{net}} == N_{\text{trades}}$ | Salvedad detectada: $W=3, L=2$ en `/status` vs 4 trades reales | **LIMITATION** |
| **Paridad Live ↔ Replay** | Arnés v1 `compare_live_replay.py` | 3 exact matches de 3 pares (**100.0% paridad en pares formados**) | **PASS** |

---

## 2. EVIDENCIA PRIMARIA AUDITADA

La auditoría forense se fundamentó en la inspección cruzada y determinista de los siguientes artefactos primarios en `loops/r23k_validation/window_19/`:
1. `status_samples.jsonl`: 92 muestras de telemetría completa de la APK y del broker Binomo.
2. `live_samples.jsonl`: 92 registros del arnés con series de balances crudos y validados.
3. `events.jsonl`: Registro de eventos de transición de modo, cambios de activo y vetos.
4. `trades.csv`: 4 transacciones formalmente ejecutadas y auditadas.
5. `decisions.csv`: 78 decisiones registradas con hash SHA y `decision_id`.
6. `ticks_live.csv`: Flujo continuo de ticks de websocket durante los 20 minutos de ventana.
7. `window_metadata.json`: Metadatos de liquidación, gracia y balance.
8. `mode_log.json`: Transiciones de estado del motor (`TradingEngine.kt`).
9. `counterfactual_adaptive_block.csv`: 4 eventos contrafactuales pasivos evaluados.
10. `replay_journal.csv`, `replay_comparison.json` y `replay_comparison_report.md`: Resultados deterministas del arnés v1 inmutable.

---

## 3. RECONCILIACIÓN DE LAS 4 OPERACIONES Y CADENA DE SALDOS

### 3.1 Detalle Trade por Trade en `trades.csv`
Saldo inicial certificado: **38,863,736.32 COP DEMO**.

| # | Trade ID | Decision ID | Timestamp (ms) | Activo | Acción | Saldo Pre | Saldo Post | Delta Medido (COP) | Resultado |
| :-: | :--- | :--- | :---: | :---: | :---: | :---: | :---: | :---: | :---: |
| **01** | `fb68806e-a1f8-4d78-a77c-bfdd00fcae52` | `8fdb2cfd-d543-3c7d-b989-a378b9ddbf77` | 1792439523856 | `Z-CRY/IDX` | **BUY** | 38,863,736.32 | 38,763,735.04 | -100,001.28 | **LOSS** |
| **02** | `21f73846-b968-4a16-9cd9-8eb681c68c08` | `9ab29488-0f4f-3dc8-97cd-0aeac0b8c9d5` | 1792439644013 | `Z-CRY/IDX` | **BUY** | 38,763,735.04 | 38,845,736.96 | +82,001.92 | **WIN** |
| **03** | `87936905-29ec-4222-80b5-5f98fbd7eac1` | `19489e09-17a5-39ac-9194-5ce72112644f` | 1792439824559 | `Z-CRY/IDX` | **BUY** | 38,845,736.96 | 38,927,736.32 | +81,999.36 | **WIN** |
| **04** | `f39264b6-3e73-4846-9c37-7ff0a926c28d` | `ac622f04-41b3-3e3c-9c2b-ac70f1e433c4` | 1792439944773 | `Z-CRY/IDX` | **SELL** | 38,927,736.32 | 38,827,735.04 | -100,001.28 | **LOSS** |

- Total de operaciones reales en la ventana: **4 trades**.
- Desglose verificado: **2 W / 2 L / 0 VOID**.
- Win Rate real de la ventana: $2 / 4 = \mathbf{50.00\%}$.
- Payout operativo medido: $82.0\%$ ($+82,000$ COP por victoria sobre stake base de 100,000 COP).
- Taps despachados en la ventana: 8 taps (4 ciclos completos de entrada/salida).

### 3.2 Conciliación Aritmética del Ledger
$$\text{Saldo Inicial Certificado (Cierre W18)} = 38,863,736.32\text{ COP}$$
$$\Delta\text{ Trade 1 (BUY LOSS)} = -100,001.28\text{ COP}$$
$$\Delta\text{ Trade 2 (BUY WIN)} = +82,001.92\text{ COP}$$
$$\Delta\text{ Trade 3 (BUY WIN)} = +81,999.36\text{ COP}$$
$$\Delta\text{ Trade 4 (SELL LOSS)} = -100,001.28\text{ COP}$$
$$\Delta\text{ Neto Medido} = -100,001.28 + 82,001.92 + 81,999.36 - 100,001.28 = \mathbf{-36,001.28\text{ COP}}$$
$$\text{Saldo Final Esperado} = 38,863,736.32 - 36,001.28 = 38,827,735.04\text{ COP}$$
$$\text{Saldo Final Medido en Telemetría} = 38,827,735.04\text{ COP}$$
$$\text{Discrepancia Contable Interna} = |38,827,735.04 - 38,827,735.04| = \mathbf{\$0.00\text{ COP}}$$
$$\text{Discrepancia Inter-Ventana W18}\to\text{W19} = |38,863,736.32 - 38,863,736.32| = \mathbf{\$0.00\text{ COP}}$$

---

## 4. ANÁLISIS FORENSE DEL QUINTO RESULTADO (DISCREPANCIA W/L vs TRADES)

### 4.1 La Paradoja Observada
En `LIVE_REPORT.md` (Sección 2), el generador reportó:
> `Resoluciones: W=3, L=2 (WR = 60.0%)` (5 resoluciones totales)  
mientras que en la Sección 1 reportó correctamente:
> `4 operaciones autónomas registradas.` y `trades.csv` contiene exactamente 4 filas.

### 4.2 Causa Raíz Identificada en Telemetría y Código
La inspección de `status_samples.jsonl` muestra la evolución exacta de los contadores `risk.totalWins` y `risk.totalLosses` de la APK:
- Muestra 0 a 18 (Inicio W19): `totalWins = 6`, `totalLosses = 2` (Valores heredados de la sesión acumulada).
- Muestra 19: Trade 1 entra (BUY).
- Muestra 25: Trade 1 liquida como **LOSS**. `totalWins = 6`, `totalLosses = 3` ($\Delta L = +1$).
- Muestra 28: Trade 2 entra (BUY, `tradeStartMs = 1792439641823`).
- **Muestra 33**: Trade 2 está en liquidación (`orderState = CONFIRMADA`, `totalWins = 6`, `totalLosses = 3`).
- **Muestra 34**: Trade 2 liquida como **WIN**. `orderState = NINGUNA`, pero los contadores reportan:
  $$\mathbf{totalWins = 8},\quad \mathbf{totalLosses = 3} \quad (\Delta W = \mathbf{+2}\text{ ¡EN UN SOLO TRADE!})$$
- Muestra 42 a 47: Trade 3 entra y liquida como **WIN**. `totalWins = 9`, `totalLosses = 3` ($\Delta W = +1$).
- Muestra 51 a 57: Trade 4 entra y liquida como **LOSS**. `totalWins = 9`, `totalLosses = 4` ($\Delta L = +1$).

Al cierre de la ventana, el monitor autónomo calculó:
$$\text{new\_wins} = 9 - 6 = 3$$
$$\text{new\_losses} = 4 - 2 = 2$$
$$\text{total\_trades\_calculados} = 3 + 2 = 5$$

### 4.3 Evidencia de Concurrencia y Condición de Carrera en APK
¿Por qué `totalWins` saltó de 6 a 8 en el Trade 2?
1. En `status_samples.jsonl` (Muestra 34), el campo `feed` expone la evidencia concluyente:
   `"journalError": "DUPLICADO RECHAZADO: 1792439641823"`
   `"journalDuplicatesRejected": 4`
2. En `TradingEngine.kt`, existen dos rutas de liquidación de trades:
   - Ruta visual (`onNewFrame` / frame analyzer).
   - Ruta headless / websocket (`checkTradeResolutionHeadless`).
3. En ambas rutas se encuentra el siguiente patrón de código:
   ```kotlin
   if (finalWin) {
       if (riskManager.resolvePendingTradeWin()) {
           ...
       } else {
           riskManager.recordTradeWin(force = true)
       }
   }
   ```
4. Durante la resolución del Trade 2:
   - El primer hilo invocó `resolvePendingTradeWin()`, incrementando `totalWins` de 6 a 7 y limpiando `hasPendingTrade = false`. Además, registró el trade en `TradeJournalLogger` con la firma `1792439641823`.
   - El segundo hilo se ejecutó de forma casi simultánea e invocó `resolvePendingTradeWin()`. Dado que `hasPendingTrade` ya había sido desactivado por el primer hilo, la función retornó `false`.
   - El bloque `else` de fallback ejecutó `riskManager.recordTradeWin(force = true)`, el cual forzó un segundo incremento incondicional de `totalWins` de 7 a 8.
   - El segundo hilo intentó registrar el trade en `TradeJournalLogger`, pero la guarda de idempotencia de `TradeJournalLogger.kt` (línea 169) detectó la firma duplicada (`1792439641823`) y abortó la escritura en disco (`DUPLICADO RECHAZADO: 1792439641823`).
   - Sin embargo, para ese momento el contador en memoria `riskManager.totalWins` ya había sido corrompido con un incremento espurio (+2 en lugar de +1).

### 4.4 Dictamen Forense sobre el Quinto Resultado
- **No existió un 5° trade en hardware ni en broker.**
- **No provino de W18:** W18 cerró limpiamente con `orderState=NINGUNA`, `hasPendingTrade=false` y `grace_wait_ms=0`. La Muestra 0 de W19 arrancó plana durante 18 muestras consecutivas sin ninguna orden pendiente.
- **No provino de contrafactuales:** Los registros en `counterfactual_adaptive_block.csv` son pasivos y no tocan `riskManager`.
- **Causa Certificada:** Condición de carrera de doble resolución en runtime en el Trade 2, que infló el contador `totalWins` en +1.
- **Cifra Oficial de la Auditoría:** **4 trades reales (2 WIN / 2 LOSS, WR 50.00%, P&L -36,001.28 COP)**.

---

## 5. ANÁLISIS FORENSE DE LA DISCREPANCIA REPLAY (LIVE ↔ REPLAY)

### 5.1 El Hallazgo en `replay_comparison.json`
- Total Live trades: 4
- Total Replay trades: 4
- Pares formados: 3 (3 exact matches, 0 divergencias $\implies$ **100.0% de paridad en pares evaluables**).
- Trades sin pareja: 1 en Live (`MISSING_REPLAY`) y 1 en Replay (`MISSING_LIVE`).

### 5.2 Mapeo y Trazabilidad de los Trades Desacoplados
1. **El Trade Faltante en Live (`MISSING_LIVE`):**
   - Replay ejecutó una orden **SELL** a las `t=1792439763495` con `decision_id = 118074c3-22ee-36ac-84c0-ec2e48093833` (Impulso Bajista Temprano, confluencia 95 pts).
   - ¿Qué ocurrió en Live en ese instante?
     En `decisions.csv`, esa misma decisión `118074c3-22ee-36ac-84c0-ec2e48093833` (t=1792439763507) fue vetada por `ADAPTIVE_BLOCK`:
     `kind: VETO | detail: ADAPTIVE_BLOCK [sim:99%] [Confluencia: 95pts] Anti-patron perdedor recurrente (99% similitud en contexto S/R=75%) - inversion deshabilitada`.
   - Live respetó el veto adaptativo y **no entró al mercado**, quedando en espera. Replay (que corre offline en arnés v1 sin el buffer adaptativo en caliente de la sesión) ejecutó la orden SELL.

2. **El Trade Faltante en Replay (`MISSING_REPLAY`):**
   - Live ejecutó el Trade 3 (**BUY**) a las `t=1792439824559` (`trade_id = 87936905...`, `decision_id = 19489e09...`), resultando en **WIN** (+81,999.36 COP).
   - ¿Por qué Replay no lo ejecutó?
     Replay estaba ocupado en la orden SELL que había tomado a las `t=1792439763495` (duración 60s $\to$ expiraba a las `t=1792439823495` + tiempo de resolución y cooldown). Al llegar la señal BUY a las `1792439824559`, Replay aún se encontraba bloqueado por la operación anterior. Live, estando desocupado gracias al veto de `ADAPTIVE_BLOCK`, tomó la operación limpia.

3. **Resincronización en Trade 4:**
   - Una vez liquidadas ambas operaciones desfasadas, tanto Live como Replay quedaron libres y resincronizaron perfectamente en el Trade 4:
     `Live SELL (f39264b6...)` $\leftrightarrow$ `Replay SELL (f39264b6...)` a las `t=1792439944773` $\implies$ **EXACT MATCH** (MATCH por `decision_id`).

---

## 6. ANÁLISIS DE DRAWDOWN MÁXIMO

- Saldo inicial de la ventana: 38,863,736.32 COP.
- Pico máximo alcanzado (tras Trade 3, Muestra 47): **38,927,736.32 COP**.
- Valle mínimo alcanzado (durante ejecución de Trade 2, Muestras 28–32): **38,663,736.32 COP**.
- **Drawdown respecto al pico máximo:**
  $$\text{Drawdown}_{\text{peak}} = \frac{38,927,736.32 - 38,663,736.32}{38,927,736.32} = \frac{264,000.00}{38,927,736.32} = \mathbf{0.6782\% \approx 0.68\%} \ll 3.50\%$$
- **Drawdown respecto al saldo inicial:**
  $$\text{Drawdown}_{\text{initial}} = \frac{38,863,736.32 - 38,663,736.32}{38,863,736.32} = \frac{200,000.00}{38,863,736.32} = \mathbf{0.5146\% \approx 0.51\%} \ll 3.50\%$$
- Veredicto: El riesgo patrimonial se mantuvo holgadamente por debajo del límite estricto de seguridad del 3.50%.

---

## 7. IMPACTO EN LOS CUBOS EPISTÉMICOS

### Cubo 1 (Paridad Replay bajo arnés v1 inmutable)
- Previo acumulado (W07 a W18): $N = 16$ pares evaluables (14 Matches / 2 Divergencias, 87.50% match rate).
- Aporte W19: 3 pares evaluables (3 Matches / 0 Divergencias, 100.0% de paridad en W19).
- **Nuevo acumulado Cubo 1 (W07 a W19):**
  * Pares formales totales: **$N = 19$ pares** ($N \ge 10 \implies$ muestra suficiente).
  * Exact Matches: **17 de 19**.
  * Divergencias: **2 de 19**.
  * **Tasa de Match global:** $17 / 19 = \mathbf{89.47\%}$.
  * **Intervalo de Wilson al 95%:** $[68.61\%, 97.06\%]$.
  * Criterio pre-registrado de paridad: $\ge 90.0\%$.
  * **Veredicto Cubo 1:** Se mantiene el dictamen formal de preregistro §2: **FAIL** ($89.47\% < 90.0\%$).

### Cubo 2 (Estrategia `8cdb61f` con Veto de Agotamiento)
- Previo acumulado (W04 a W18): $N = 44$ trades (25 W / 18 L / 1 VOID, WR resueltos 58.14%).
- Aporte W19 certificado: **4 trades reales (2 W / 2 L / 0 VOID)**.
- **Nuevo acumulado Cubo 2 (W04 a W19):**
  * Total operaciones reales: **$N = 48$ trades** (27 W / 20 L / 1 VOID).
  * Trades resueltos: $27 + 20 = 47\text{ trades}$.
  * **Win Rate (sobre resueltos):** $27 / 47 = \mathbf{57.45\%}$ (supera breakeven payout 83% = 54.64%, y payout 82% = 54.95%).
  * **EV unitario ($b = 0.82$):** $\mathbf{+0.0456\text{ R}}$ ($+4,560\text{ COP/trade}$).
  * **Intervalo de Wilson al 95% (resueltos):** $[43.32\%, 70.47\%]$.
  * Estado: Abierto, acumulando hacia $N = 80$.
  * **Veredicto Cubo 2:** `NO CONCLUYENTE (Acumulando N=48 < 80 y límite inf Wilson 43.32% <= BE 54.64%)`.

### Total Consolidado Histórico (W01 a W19)
- Total operaciones reales: **$N = 59$ trades** (34 W / 24 L / 1 VOID).
- Trades resueltos: 58 trades (34 W / 24 L).
- **Win Rate global (resueltos):** $34 / 58 = \mathbf{58.62\%}$.
- **Intervalo de Wilson al 95%:** $[45.82\%, 70.33\%]$.
- **P&L neto histórico acumulado:** $+438,000.64 - 36,001.28 = \mathbf{+401,999.36\text{ COP DEMO}}$.
- **Discrepancia contable acumulada:** **$0.00 COP**.

---

## 8. DICTAMEN FINAL DE AUDITORÍA

Se emite dictamen formal **PASS_WITH_LIMITATION**:
1. **Seguridad Absoluta:** 100% Cuenta DEMO verificada (92/92 muestras, 0 fondos reales expuestos).
2. **Cierre Disciplinado:** Monitor v2 ordenó parada a `DISABLED` limpiamente sin trades colgados ni necesidad de retención de gracia (`grace_wait_ms: 0`).
3. **Conciliación Contable Exacta al Centavo:** Saldo inicial $38,863,736.32 \to$ final $38,827,735.04$ COP DEMO ($\Delta = -36,001.28$ COP, discrepancia interna **$0.00 COP**, discrepancia vs W18 **$0.00 COP**).
4. **Drawdown Bajo Control:** Drawdown medido de 0.68% frente al límite de 3.50%.
5. **Paridad Replay Perfecta en Pares Evaluables:** 3 exact matches de 3 pares evaluados (100.0%).
6. **Salvedad Formal Resuelta (Limitation - Severidad ALTA en runtime APK):** La discrepancia de contadores ($W=3, L=2$ en `/status` vs 4 trades reales en ledger) fue auditada forensemente y explicada al 100% como una condición de carrera de doble resolución en el Trade 2 dentro de la APK (TradingEngine.kt:637-641/2720-2727 con reseteo de `currentLossStreak` en RiskManager.kt:832). La realidad contable certificada es de **4 trades (2 WIN / 2 LOSS, WR 50.00%)**.

---

## 9. RECOMENDACIÓN CONCRETA PARA EL CIERRE DE W19

1. **Cierre Formal de W19:** Aceptar y archivar la Ventana 19 con las cifras certificadas por la auditoría independiente: **$N=4$ trades (2 WIN / 2 LOSS, WR 50.00%, saldo final 38,827,735.04 COP DEMO, discrepancia $0.00 COP)**.
2. **Actualización de Scripts de Cubos:** Asegurar que `scripts/validation/recompute_cubes.py` compute estrictamente los 4 trades reales de `trades.csv` (evitando sumar el trade fantasma derivado de la telemetría cruda de `/status`).
3. **Deuda Técnica para R24:** Corregir en `TradingEngine.kt` el bloque de fallback que invoca `riskManager.recordTradeWin(force = true)` cuando `resolvePendingTradeWin()` retorna `false`. Si no hay trade pendiente, no se debe forzar una victoria espuria.
4. **Autorización:** Concluida la auditoría de W19, el equipo queda formalmente facultado para proceder al Preflight de la Ventana 20 (W20) con saldo base de 38,827,735.04 COP DEMO.
