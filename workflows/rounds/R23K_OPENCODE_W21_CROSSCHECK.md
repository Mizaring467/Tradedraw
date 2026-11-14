# VALIDACIÓN CRUZADA INDEPENDIENTE (FASE 2) — VENTANA 21 (W21)
**Loop:** R23K · **Rama:** `r10-night` · **Build:** `8cdb61f` (`versionCode = 19`)  
**Validador Independiente:** OPENCODE (`w1:pB`)  
**Orquestador:** AGY (`w1:p1`)  
**Fecha:** 2026-10-20 22:15:00 UTC  
**Directorio Auditado:** `loops/r23k_validation/window_21/`  
**Dictamen Formal:** **CONSISTENT** (CONCORDANCIA ABSOLUTA CON FUENTES DE DATOS PRIMARIAS · CADENA DE SALDOS SIN SALTO INTER-VENTANA · 5 TRADES REALES EN LEDGER: 1 WIN / 4 LOSS · DELTA CANÓNICO -316,999.68 COP · DISCREPANCIA CONTABLE $0.00 COP · CUBO 1 EN 90.91% CON VEREDICTO PASS · CUBO 2 EN 51.72% NO CONCLUYENTE · ARCHIVOS RECOMPUTED ACTUALIZADOS)

---

## 1. RESUMEN EJECUTIVO DE VALIDACIÓN CRUZADA

En cumplimiento del rol de Validador Independiente (`w1:pB`), se realizó la inspección forense byte a byte y el recálculo determinista de las fuentes primarias de la Ventana 21 (`trades.csv`, `window_metadata.json`, `status_samples.jsonl`, `live_samples.jsonl`, `events.jsonl`, `decisions.csv`, `replay_comparison.json`, `LIVE_REPORT.md`), actualizando e integrando los resultados en `scripts/validation/recompute_cubes.py`, `workflows/rounds/R23K_CUBES_RECOMPUTED.md` y `workflows/rounds/R23K_CUBES_RECOMPUTED.json`.

| Dimensión Auditada | Medición Primaria OpenCode (`w1:pB`) | Estado / Certificación | Veredicto |
|---|---|---|:---:|
| **A) Seguridad de Cuenta** | 117/117 muestras periódicas con `isDemoAccount == true` | 0 operaciones reales en broker | **PASS** |
| **B) Integridad de Build** | `8cdb61f` (vCode=19, buildId="R23H-BUY-EXHAUSTION") | Conforme a preflight y commit base | **PASS** |
| **C) Saldo Inicial Certificado** | **38,593,735.68 COP DEMO** | Continuidad exacta vs saldo final de W20 | **PASS** |
| **D) Saldo Final Validado** | **38,276,736.00 COP DEMO** | Muestra 117 / Telemetría final / Metadata | **PASS** |
| **E) Delta Contable Canónico** | **-316,999.68 COP DEMO** | Suma exacta al centavo de 5 deltas reales | **PASS** |
| **F) Discrepancia Interna Ledger** | **$0.00 COP** | $38,593,735.68 - 316,999.68 = 38,276,736.00$ | **PASS** |
| **G) Discrepancia vs W20** | **$0.00 COP** | $|38,593,735.68 - 38,593,735.68| = 0.00$ COP | **PASS** |
| **H) Operaciones Confirmadas** | **5 trades reales** en `trades.csv` | 10 taps observados en hardware (2 taps/trade) | **PASS** |
| **I) Desglose Real W/L** | **1 WIN / 4 LOSS** (WR = 20.00%) | Payout 83.0% ($+83,000.32$ COP por victoria) | **PASS** |
| **J) Drawdown Máximo Medido** | **0.82%** (vs inicial) | Límite estricto $< 3.50\%$ cumplido | **PASS** |
| **K) Paridad Live ↔ Replay** | **2 exact matches de 2 pares formados (100.0%)** | Paridad perfecta en pares evaluables | **PASS** |
| **L) Cubo 1 Acumulado (W07..W21)** | **$N = 22$ pares, 20 matches, 2 divergencias (90.91%)** | Criterio preregistrado $\ge 90.0\%$ superado | **PASS** |
| **M) Cubo 2 Acumulado (W04..W21)**| **$N = 59$ trades (30 W / 28 L / 1 VOID, WR = 51.72%)** | Acumulando hacia meta $N \ge 80$ | **NO CONCLUYENTE** |

---

## 2. INSPECCIÓN DIRECTA DE ARTEFACTOS PRIMARIOS (W21)

### 2.1 `window_metadata.json`
- `initial_balance`: **38,593,735.68 COP**
- `final_balance_validated`: **38,276,736.00 COP**
- `min_validated_balance`: **38,276,736.00 COP**
- `max_validated_balance`: **38,593,735.68 COP**
- `drawdown_validated_pct`: **0.821%** ($< 3.50\%$)
- `balance_outliers_filtered`: 0
- `decisions_captured`: 64
- `trades_confirmed`: 5
- `grace_wait_ms`: 150,378 ms (150.4 s)
- `grace_resultado`: `"TIMEOUT"` (Aviso: trade en vuelo liquidó a `orderState: NINGUNA` tras agotar el ciclo de gracia).

### 2.2 `trades.csv`
Contiene exactamente 5 registros confirmados en hardware:
1. `ceaff3d0-2fbb-412a-bd0a-253f386c310b` | `ts=1792532044170` | `Z-CRY/IDX` | **BUY**  | `balance_at_trade = 38493736.96`
2. `4b3f3105-f700-4364-b8d5-2e45c44ba08e` | `ts=1792532344957` | `Z-CRY/IDX` | **BUY**  | `balance_at_trade = 38393735.68`
3. `d74c7a8a-4b16-459a-9b0e-1b2bd604cabd` | `ts=1792532644896` | `Z-CRY/IDX` | **BUY**  | `balance_at_trade = 38476736.00`
4. `796df03c-a530-40bc-be53-997c58f32ec4` | `ts=1792532944622` | `Z-CRY/IDX` | **SELL** | `balance_at_trade = 38376737.28`
5. `b24987fa-bb26-45cc-9733-b7ac373fe97b` | `ts=1792533065258` | `Z-CRY/IDX` | **SELL** | `balance_at_trade = 38276736.00`

### 2.3 `status_samples.jsonl` y `live_samples.jsonl`
- Se registraron 117 muestras periódicas.
- Todas las muestras (117/117) certifican cuenta DEMO activa (`isDemoAccount == true`).
- Transición ordenada a `DISABLED` por el monitor en +6.0s tras el corte nominal (`atMs = 1792533122326`).
- Estado final en APK al concluir la gracia: `mode = DISABLED`, `orderState = NINGUNA`, `hasPendingTrade = false`, `balance = 38276736.00`.

### 2.4 `replay_comparison.json`
- Mide paridad formal bajo arnés v1 inmutable:
  * Total live trades: 5 | Total replay trades: 3
  * Pares formales formados: **2 pares**
  * Exact Matches: **2 de 2** (**100.0% match rate**)
    - Par 1: Live SELL (`796df03c...`) $\leftrightarrow$ Replay SELL (`e9dad910...`) a las `ts=1792532944622` $\implies$ **MATCH**
    - Par 2: Live SELL (`b24987fa...`) $\leftrightarrow$ Replay SELL (`d03c1d99...`) a las `ts=1792533065258` $\implies$ **MATCH**
  * Divergencias: **0**
  * Missing en replay: 3 trades BUY que el replay offline no ejecutó por condiciones de señal local.
  * Missing en live: 1 trade BUY ejecutado en replay mientras el bot live estaba en cooldown.

---

## 3. AUDITORÍA TRADE POR TRADE Y CADENA DE SALDOS

```text
Saldo Inicial Certificado (Cierre W20): 38,593,735.68 COP DEMO
```

1. **Trade 1 (`ceaff3d0...`, BUY, ts = 1792532044170):**
   - Saldo pre-trade: **38,593,735.68 COP**
   - Apertura: Débito de stake $\to 38,493,736.96$ COP.
   - Liquidación: Cierre OTM $\implies$ **LOSS** (sin crédito).
   - Saldo post-trade: **38,493,736.96 COP**
   - $\mathbf{\Delta_1 = -99,998.72\text{ COP}}$.

2. **Trade 2 (`4b3f3105...`, BUY, ts = 1792532344957):**
   - Saldo pre-trade: **38,493,736.96 COP**
   - Apertura: Débito de stake $\to 38,393,735.68$ COP.
   - Liquidación: Cierre ITM $\implies$ **WIN** (crédito de $183,001.60$ COP, Payout 83.0%).
   - Saldo post-trade: **38,576,737.28 COP**
   - $\mathbf{\Delta_2 = +83,000.32\text{ COP}}$ ($38,576,737.28 - 38,493,736.96$).

3. **Trade 3 (`d74c7a8a...`, BUY, ts = 1792532644896):**
   - Saldo pre-trade: **38,576,737.28 COP**
   - Apertura: Débito de stake $\to 38,476,736.00$ COP.
   - Liquidación: Cierre OTM $\implies$ **LOSS**.
   - Saldo post-trade: **38,476,736.00 COP**
   - $\mathbf{\Delta_3 = -100,001.28\text{ COP}}$.

4. **Trade 4 (`796df03c...`, SELL, ts = 1792532944622):**
   - Saldo pre-trade: **38,476,736.00 COP**
   - Apertura: Débito de stake $\to 38,376,737.28$ COP.
   - Liquidación: Cierre OTM $\implies$ **LOSS**.
   - Saldo post-trade: **38,376,737.28 COP**
   - $\mathbf{\Delta_4 = -99,998.72\text{ COP}}$.

5. **Trade 5 (`b24987fa...`, SELL, ts = 1792533065258):**
   - Saldo pre-trade: **38,376,737.28 COP**
   - Apertura: Débito de stake $\to 38,276,736.00$ COP.
   - Liquidación: Cierre OTM en periodo de gracia $\implies$ **LOSS**.
   - Saldo post-trade: **38,276,736.00 COP**
   - $\mathbf{\Delta_5 = -100,001.28\text{ COP}}$.

### 3.1 Tabla de Conciliación de Saldos (W21)

| # | Trade ID | Acción | Saldo Pre (COP) | Saldo Post (COP) | Delta Real (COP) | Resultado |
| :-: | :--- | :---: | :---: | :---: | :---: | :---: |
| **01** | `ceaff3d0...` | **BUY**  | 38,593,735.68 | 38,493,736.96 | -99,998.72 | **LOSS** |
| **02** | `4b3f3105...` | **BUY**  | 38,493,736.96 | 38,576,737.28 | +83,000.32 | **WIN** |
| **03** | `d74c7a8a...` | **BUY**  | 38,576,737.28 | 38,476,736.00 | -100,001.28 | **LOSS** |
| **04** | `796df03c...` | **SELL** | 38,476,736.00 | 38,376,737.28 | -99,998.72 | **LOSS** |
| **05** | `b24987fa...` | **SELL** | 38,376,737.28 | 38,276,736.00 | -100,001.28 | **LOSS** |

### 3.2 Conciliación Aritmética Exacta
$$\sum_{i=1}^{5} \Delta_i = -99,998.72 + 83,000.32 - 100,001.28 - 99,998.72 - 100,001.28 = \mathbf{-316,999.68\text{ COP}}$$

$$\text{Saldo Final Esperado} = 38,593,735.68 - 316,999.68 = \mathbf{38,276,736.00\text{ COP}}$$

$$\text{Saldo Final Medido} = \mathbf{38,276,736.00\text{ COP}}$$

$$\text{Discrepancia Interna de Ledger} = |38,276,736.00 - 38,276,736.00| = \mathbf{\$0.00\text{ COP}}$$

$$\text{Discrepancia vs Cierre W20} = |38,593,735.68 - 38,593,735.68| = \mathbf{\$0.00\text{ COP}}$$

---

## 4. INTEGRACIÓN Y RECÁLCULO OFICIAL DE LOS 3 CUBOS EPISTÉMICOS

Se actualizó `scripts/validation/recompute_cubes.py` con W21 y se ejecutó la regeneración de los artefactos oficiales (`R23K_CUBES_RECOMPUTED.json` y `R23K_CUBES_RECOMPUTED.md`).

### 4.1 Cubo 1: Paridad Post-Congelado (W07 a W21)
- Pares formales previos (W07..W20): $N = 20$ pares (18 matches, 2 divergencias, 90.00%).
- Aporte W21: 2 pares formales evaluables (2 matches exactos, 0 divergencias).
- **Nuevo Acumulado Cubo 1:**
  * Pares formales evaluados: **$N = 22\text{ pares}$**
  * Matches exactos: **20**
  * Divergencias: **2** (en W08 y W11)
  * **Tasa de Paridad (Match Rate):** $20 / 22 = \mathbf{90.91\%}$
  * Intervalo Wilson al 95%: **[72.19%, 97.47%]**
  * Criterio Preregistro §2 ($\ge 90.0\%$ con $N \ge 10$): **PASS**

### 4.2 Cubo 2: Desempeño Homogéneo con Veto (W04 a W21 + Trade Frontera W10)
- Operaciones previas (W04..W20): $N = 54$ trades (29 W / 24 L / 1 VOID).
- Aporte W21: 5 trades reales (1 W / 4 L / 0 VOID).
- **Nuevo Acumulado Cubo 2:**
  * Total operaciones reales: **$N = 59\text{ trades}$** (30 Wins / 28 Losses / 1 Void)
  * Trades resueltos: $30 + 28 = \mathbf{58\text{ trades}}$
  * **Win Rate (sobre resueltos):** $30 / 58 = \mathbf{51.72\%}$
  * Breakeven payout 83%: $54.64\%$ ($51.72\% < 54.64\% \implies$ breakeven no alcanzado en este corte)
  * Intervalo Wilson al 95%: **[39.16%, 64.07%]**
  * EV unitario ($b = 0.83$): **-0.0553 R** ($-5,530$ COP/trade)
  * Variación de capital neta Cubo 2: **-327,001.60 COP DEMO**
  * Veredicto Preregistro §3: **NO CONCLUYENTE** ($N=59 < 80$, intervalo Wilson contiene breakeven).

### 4.3 Cubo 3: Histórico Heterogéneo Pre-Veto (W01 a W03)
- Operaciones reales: $N = 11$ trades (7 Wins / 4 Losses, WR = 63.64%, Delta = +178,001.92 COP).
- Estado: Cerrado. Referencia histórica no comparable.

### 4.4 Total Consolidado Informativo (W01 a W21)
- Total operaciones reales: **$N = 70\text{ trades}$** (37 Wins / 32 Losses / 1 Void).
- Trades resueltos: 69 trades (37 W / 32 L).
- **Win Rate global (resueltos):** $37 / 69 = \mathbf{53.62\%}$.
- Intervalo Wilson al 95%: **[41.98%, 64.89%]**.
- **Variación Neta Acumulada de Capital:** $+168,000.00 - 316,999.68 = \mathbf{-148,999.68\text{ COP DEMO}}$.
- Discrepancia contable acumulada histórica: **$0.00 COP**.

---

## 5. DICTAMEN FORMAL

$$\mathbf{DICTAMEN:\; CONSISTENT}$$

1. **Exactitud Matemática Plena:** El balance inicial (38,593,735.68 COP), la suma de deltas (-316,999.68 COP) y el balance final (38,276,736.00 COP) coinciden de manera exacta al centavo sin discrepancias contables ni saltos inter-ventana.
2. **Cubo 1 Certificado:** Alcanza $N=22$ pares con 90.91% de match rate, ratificando el veredicto **PASS**.
3. **Cubo 2 Certificado:** Alcanza $N=59$ trades con 51.72% de Win Rate, en estado **NO CONCLUYENTE** en su avance hacia el objetivo preregistrado de $N \ge 80$.
4. **Artefactos Sincronizados:** `R23K_CUBES_RECOMPUTED.md` y `R23K_CUBES_RECOMPUTED.json` actualizados y certificados bajo semilla determinista `20261019`.
