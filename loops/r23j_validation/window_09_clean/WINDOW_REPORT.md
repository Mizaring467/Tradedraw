# INFORME DE AUDITORÍA: R23J — VENTANA 09 LIMPIA (CUENTA DEMO)
## REVALIDACIÓN CONTROLADA DE CAPTURA LIVE ↔ REPLAY Y ANÁLISIS DE PARIDAD (FASE C)

**Fecha:** 2026-10-19 04:04:30 UTC  
**Orquestador:** AGY (`w1:p1`)  
**Validador Live Independiente:** OPENCODE (`w1:pB`)  
**Dispositivo Auditado:** Xiaomi Redmi A1 (`UGU8KFUKM7E6CE8D`, Android 12 Go)  
**Build Identificada:** `versionCode = 19` | `versionName = "1.9-r23h-exhaustion"` | `BUILD_ID = "R23H-BUY-EXHAUSTION"` | `gitSha = "8cdb61f"`  
**Modo Auditado:** `AUTONOMOUS` | **Submodo:** `YOLO` (100% Cuenta DEMO)  
**Duración de la Ventana:** 20.0 minutos nominales (118 muestras periódicas, gracia settlement 1.9s)  
**Veredicto Operativo y Contable:** **PASS (ECUACIÓN CANÓNICA 100% EXACTA, DISCREPANCIA $0.00 COP, MAX DD 0.47% < 3.5%)**  
**Veredicto Paridad Replay (Preregistro R23J §2):** **NO CONCLUYENTE (N=1 PAR EN W09; ACUMULADO CUBO 1 N=6 < 10 PARES REQUERIDOS; MATCH RATE 100.0%)**

---

## 1. RESUMEN EJECUTIVO Y AUDITORÍA DE SEGURIDAD

1. **Seguridad DEMO Inviolable:**
   - `isDemoAccount == true` verificado en el 100.0% de las lecturas (118 de 118 muestras periódicas).
   - Cuentas reales observadas: 0 (0.0%). Fondos reales en riesgo: **$0.00 COP**.
2. **Dinámica de Balance y Preservación de Capital en W09_clean:**
   - Saldo inicial al arrancar W09: **39,120,737.28 COP DEMO**.
   - Saldo final validado en ventana: **39,103,736.32 COP DEMO**.
   - **Variación Neta de Capital en W09:** **-17,000.96 COP DEMO** (-0.04% en la ventana).
   - Drawdown máximo validado en la ventana: **0.47%** (pico a valle de 183,001.60 COP entre el pico post-Trade 1 de 39,203,737.60 y el mínimo de 39,020,736.00 COP; límite pre-registrado: 3.5%).
   - Outliers de telemetría descartados: 0 lecturas.
3. **Ecuación Canónica de Reconciliación ($N = W + L + VOID$):**
   - **Total de Órdenes Reales en W09 ($N$):** **2 trades**.
   - **Victorias ($W$):** **1 Win** (1 SELL).
   - **Derrotas ($L$):** **1 Loss** (1 SELL).
   - **Nulos ($VOID$):** **0 VOID**.
   - **Cumplimiento Canónico:** $2 = 1 + 1 + 0$ (**100% exacto**).
   - **Win Rate de Ventana:** **50.00%**.
4. **Desglose Direccional de Órdenes en W09:**
   - **`BUY` (Compras):** $N = 0$ (Cero órdenes BUY ejecutadas; 51 intentos fueron suprimidos por el veto preventivo de clímax).
   - **`SELL` (Ventas):** $N = 2$ ($1\text{ Win} / 1\text{ Loss} \implies \mathbf{50.00\% WR}$, $-17,000.96$ COP netos).
5. **Auditoría de Bloqueos y Gobernanza de Riesgo:**
   - 104 decisiones capturadas en `decisions.csv`: 102 bloqueos pasivos (`VETO`), 2 transiciones de estado (`ORDER_STATE`). Cero ejecuciones espurias.
   - **51 bloqueos por `VETO_BUY_EXHAUSTION_CLIMAX`:** 5 minutos de vela con mercado en `UPTREND` y `callPower >= 70%` (28 con callPower=86%, 23 con callPower=78%).
   - **23 bloqueos por `RISK_LIMIT`:** Protección de operación abierta en curso.
   - **22 bloqueos por `ADAPTIVE_BLOCK 99%`:** Contrafactual shadow a 60s registró 2 señales resueltas: 1 WIN / 1 LOSS.
   - **6 bloqueos por `COOLDOWN`:** Protección de intervalo post-resolución.

---

## 2. AUDITORÍA FORENSE TRADE POR TRADE (CANONICAL LEDGER W09)

| # | Trade ID | Decision ID | Timestamp (ms) | Acción | Balance Pre-Trade | Balance Entrada | Balance Liquidado | Delta Balance Real | Resultado |
| :-: | :--- | :--- | :---: | :---: | :---: | :---: | :---: | :---: | :---: |
| **1** | `a7a7cd3a-5a3e-4701-b3bc-a26d9e1a30cf` | `48250ce1...` | 1792381443141 | **SELL** | 39,120,737.28 | 39,020,736.00 | 39,203,737.60 | **+83,000.32 COP** | **WIN** |
| **2** | `4c3c69dc-afa3-4444-a10b-1ec09fb59a4f` | `c8624349...` | 1792381682890 | **SELL** | 39,203,737.60 | 39,103,736.32 | 39,103,736.32 | **-100,001.28 COP** | **LOSS** |

### Verificación Aritmética de Balance:
$$\Delta\text{ Balance Teórico} = (+83,000.32) + (-100,001.28) = \mathbf{-17,000.96\text{ COP}}$$
$$\Delta\text{ Balance Real Medido} = 39,103,736.32 - 39,120,737.28 = \mathbf{-17,000.96\text{ COP}}$$
$$\text{Discrepancia Contable} = |\Delta\text{ Teórico} - \Delta\text{ Real}| = \mathbf{\$0.00\text{ COP (Cero absoluto, cero leaks)}}$$

---

## 3. AUDITORÍA FORENSE DE PARIDAD LIVE ↔ REPLAY (FASE C)

### 3.1 Datos Procesados:
- **Ticks Live de la ventana:** 2,898 ticks íntegros en `ticks_live.csv` (2,001 ticks pre-ventana podados al inicio).
- **Trades en Live:** 2 órdenes reales confirmadas en hardware.
- **Trades simulados en Replay:** 2 órdenes en `replay_journal.csv`.

### 3.2 Matriz de Comparación Formal (`compare_live_replay.py`, `--mode trades`, tol=6000 ms):
- **Trades en Live:** 2
- **Trades en Replay:** 2
- **Pares formalmente evaluados:** **1 par**
- **Exact Matches:** **1 par (100.0%)**
  * Live: `SELL` @ $ts=1792381682890$ ms $\to$ resultado `LOSS`
  * Replay: `SELL` @ $ts=1792381681103$ ms $\to$ resultado `LOSS` (confluencia 95 pts, vela seg 1)
  * Desfase Temporal ($\Delta t$): **-1,787 ms** (dentro del límite pre-registrado de $\pm 6,000$ ms).
- **Divergencias en el par evaluado:** **0 (Match Rate = 100.0%)**.
- **Missing en Live:** 1 (Replay simuló BUY @ $t=1792381985615$ que en Live fue bloqueado por `VETO_BUY_EXHAUSTION_CLIMAX`).
- **Missing en Replay:** 1 (Live Trade 1 @ $t=1792381443141$ omitido en Replay por timing de apertura).
- **Tasa de Paridad en W09:** **100.0% (1 / 1)**.

### 3.3 Evaluación Epistémica bajo Preregistro R23J (§2):
- W09 aporta 1 par formal con 100% de exact match.
- Acumulado Cubo 1 (W07 + W08 + W09): **6 pares evaluados** (5 Matches, 1 Divergencia $\implies$ Match Rate = **83.33%**).
- Conforme al Preregistro R23J §2 ($N_{\text{pares}} \ge 10$), el estado de paridad formal estadística se mantiene rigurosamente clasificado como:
$$\mathbf{NO\ CONCLUYENTE\ (N_{\text{pares}} = 6 < 10)}$$

---

## 4. ANÁLISIS CONTRAFACTUAL DE `VETO_BUY_EXHAUSTION_CLIMAX` EN W09

- **Activaciones totales:** 51 bloqueos concentrados en 5 minutos de vela únicos en `UPTREND` con `callPower >= 70%`:
  1. Minuto 29873008 ($t=1792380481–85$ ms): 11 vetos $\to$ A 60s: $+0.00000000$ (TIE).
  2. Minuto 29873033 ($t=1792381981–85$ ms): 13 vetos $\to$ A 60s: $+0.00000004$ (WIN marginal).
  3. Minuto 29873034 ($t=1792382043–45$ ms): 2 vetos $\to$ A 60s: $+0.00000021$ (WIN).
  4. Minuto 29873035 ($t=1792382101–05$ ms): 13 vetos $\to$ A 60s: $+0.00000067$ (WIN).
  5. Minuto 29873036 ($t=1792382161–65$ ms): 12 vetos $\to$ A 60s: $+0.00000024$ (WIN).
- **Evaluación:** Aunque las 4 oportunidades en la rampa alcista resultaron positivas a 60s, la regla operó estrictamente según su diseño determinista (umbral inmutable `callPower >= 70%` en `UPTREND`), impidiendo cualquier intervención subjetiva post-hoc.

---

## 5. CONSOLIDACIÓN DE LOS 3 CUBOS PRE-REGISTRADOS (W01 A W09)

### 5.1 Cubo 1 — Integridad y Paridad Post-Congelado (W07 + W08 + W09):
- **Pares formales evaluados:** **6 pares** (1 en W07, 4 en W08, 1 en W09).
- **Exact Matches:** **5 pares** (83.33%).
- **Divergencias:** **1 par** (action).
- **Intervalo Wilson al 95%:** **[43.65%, 96.99%]**.
- **Veredicto Preregistro §2:** **`NO CONCLUYENTE (N=6 < 10)`**.

### 5.2 Cubo 2 — Desempeño Homogéneo con Veto (W04 a W09):
- **Total Órdenes Reales:** **26 trades**.
- **Victorias / Derrotas:** **17 Wins / 9 Losses**.
- **Win Rate:** **65.38%** (supera el breakeven de 54.64% para payout del 83%).
- **Intervalo Wilson al 95%:** **[46.22%, 80.59%]**.
- **Veredicto Preregistro §3:** **`NO CONCLUYENTE (N=26 < 40; Límite inferior 46.22% < 54.64%)`**.

### 5.3 Cubo 3 — Histórico Heterogéneo Pre-Veto (W01 + W02 + W03):
- **Total Órdenes Reales:** **11 trades** (7 Wins / 4 Losses, $WR = \mathbf{63.64\%}$).

### 5.4 Consolidado Total Acumulado (Cubo 2 + Cubo 3, W01 a W09):
- **Total Operaciones Confirmadas ($N$):** **37 trades reales**.
- **Victorias / Derrotas:** **24 Wins / 13 Losses** (0 VOID).
- **Win Rate Global:** **64.86%** ($WR_{\text{BE}} = 54.80\%$).
- **Intervalo Wilson al 95%:** **[48.76%, 78.17%]**.
- **Variación Neta de Capital:** **+678,000.64 COP DEMO** (+1.74% de ganancia neta sobre capital inicial).
- **Desglose Direccional Acumulado:**
  * **`BUY` (Compras):** $N = 19$ ($12\text{ Wins} / 7\text{ Losses} \implies \mathbf{63.16\% WR}$).
  * **`SELL` (Ventas):** $N = 18$ ($12\text{ Wins} / 6\text{ Losses} \implies \mathbf{66.67\% WR}$).

---

## 6. CONCLUSIÓN Y RECOMENDACIÓN OPERATIVA PARA AGY

1. **Gobernanza de Riesgo y Contabilidad:** Impecable. Discrepancia $0.00 COP, drawdown 0.47% muy inferior al límite de 3.5%, seguridad DEMO fail-closed 100% verificada.
2. **Desempeño W09:** Ventana de baja frecuencia ($N=2$ trades, 1 W / 1 L, $WR = 50.0\%$, delta $-17,000.96$ COP). El balance acumulado global se mantiene sólidamente en positivo (**+678,000.64 COP** en 37 operaciones).
3. **Avance en Paridad:** W09 aportó **1 par con 100% match rate**, elevando Cubo 1 a **6 pares evaluados** (83.33% match rate acumulado).
4. **Recomendación para el Loop:** Mantener la disciplina metodológica inquebrantable (sin modificar la estrategia ante muestras cortas). Se debe **continuar la recolección en ventanas homogéneas idénticas** (Ventana 10) para alcanzar los 10 pares requeridos en Cubo 1 y avanzar hacia los 40 trades en Cubo 2.

```text
======================================================================
DICTAMEN FINAL FASE C (VENTANA 09):
OPERATIVA Y CONTABILIDAD = PASS (DISCREPANCIA $0.00 COP, DD 0.47% < 3.5%)
REPLAY PARITY ESTADÍSTICA = NO CONCLUYENTE (CUBO 1: N=6 < 10 PARES)
CUBO 2 HOMOGÉNEO = NO CONCLUYENTE (N=26 < 40 TRADES, WR 65.38%)
======================================================================
```
