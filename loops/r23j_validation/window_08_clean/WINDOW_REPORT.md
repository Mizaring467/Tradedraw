# INFORME DE AUDITORÍA: R23J — VENTANA 08 LIMPIA (CUENTA DEMO)
## REVALIDACIÓN CONTROLADA DE CAPTURA LIVE ↔ REPLAY Y ANÁLISIS DE PARIDAD (FASE C)

**Fecha:** 2026-10-19 03:28:45 UTC  
**Orquestador:** AGY (`w1:p1`)  
**Validador Live Independiente:** OPENCODE (`w1:pB`)  
**Dispositivo Auditado:** Xiaomi Redmi A1 (`UGU8KFUKM7E6CE8D`, Android 12 Go)  
**Build Identificada:** `versionCode = 19` | `versionName = "1.9-r23h-exhaustion"` | `BUILD_ID = "R23H-BUY-EXHAUSTION"` | `gitSha = "8cdb61f"`  
**Modo Auditado:** `AUTONOMOUS` | **Submodo:** `YOLO` (100% Cuenta DEMO)  
**Duración de la Ventana:** 20.0 minutos nominales (118 muestras periódicas, gracia settlement 3.4s)  
**Veredicto Operativo y Contable:** **PASS (ECUACIÓN CANÓNICA 100% EXACTA, DISCREPANCIA $0.00 COP, MAX DD 1.02% < 3.5%)**  
**Veredicto Paridad Replay (Preregistro R23J §2):** **NO CONCLUYENTE (N=4 PARES EN W08; ACUMULADO CUBO 1 N=5 < 10 PARES REQUERIDOS; MATCH RATE 75.0%)**

---

## 1. RESUMEN EJECUTIVO Y AUDITORÍA DE SEGURIDAD

1. **Seguridad DEMO Inviolable:**
   - `isDemoAccount == true` verificado en el 100.0% de las lecturas (118 de 118 muestras periódicas).
   - Cuentas reales observadas: 0 (0.0%). Fondos reales en riesgo: **$0.00 COP**.
2. **Dinámica de Balance y Preservación de Capital en W08_clean:**
   - Saldo inicial al arrancar W08: **39,337,735.68 COP DEMO**.
   - Saldo final validado en ventana: **39,120,737.28 COP DEMO**.
   - **Variación Neta de Capital en W08:** **-216,998.40 COP DEMO** (-0.55% en la ventana).
   - Drawdown máximo validado en la ventana: **1.02%** (pico $39,337,735.68 \to 38,937,735.68$ en entrada de Trade 4; límite de parada de emergencia pre-registrado: 3.5%).
   - Outliers de telemetría descartados: 0 lecturas.
3. **Ecuación Canónica de Reconciliación ($N = W + L + VOID$):**
   - **Total de Órdenes Reales en W08 ($N$):** **4 trades**.
   - **Victorias ($W$):** **1 Win** (1 SELL).
   - **Derrotas ($L$):** **3 Losses** (3 BUY).
   - **Nulos ($VOID$):** **0 VOID**.
   - **Cumplimiento Canónico:** $4 = 1 + 3 + 0$ (**100% exacto**).
   - **Win Rate de Ventana:** **25.00%**.
4. **Desglose Direccional de Órdenes en W08:**
   - **`BUY` (Compras):** $N = 3$ ($0\text{ Wins} / 3\text{ Losses} \implies \mathbf{0.00\% WR}$, $-299,998.72$ COP netos).
   - **`SELL` (Ventas):** $N = 1$ ($1\text{ Win} / 0\text{ Losses} \implies \mathbf{100.00\% WR}$, $+83,000.32$ COP netos).
5. **Auditoría de Bloqueos y Gobernanza de Riesgo:**
   - 90 decisiones registradas en `decisions.csv`: 86 bloqueos pasivos (`VETO`), 4 transiciones de estado (`ORDER_STATE`). Cero ejecuciones espurias.
   - **53 bloqueos por `ADAPTIVE_BLOCK 99%`:** Contrafactual shadow a 60s registró 9 señales resueltas: 6 WIN / 3 LOSS ($WR = 66.67\%$).
   - **16 bloqueos por `RISK_LIMIT`:** Protección de operación abierta en curso.
   - **15 bloqueos por `VETO_BUY_EXHAUSTION_CLIMAX`:** 4 minutos de vela con ráfagas en techo (callPower=78% y 86%).
   - **2 bloqueos por `COOLDOWN`:** Protección de intervalo post-resolución.

---

## 2. AUDITORÍA FORENSE TRADE POR TRADE (CANONICAL LEDGER W08)

| # | Trade ID | Decision ID | Timestamp (ms) | Acción | Balance Pre-Trade | Balance Entrada | Balance Liquidado | Delta Balance Real | Resultado |
| :-: | :--- | :--- | :---: | :---: | :---: | :---: | :---: | :---: | :---: |
| **1** | `e00abd2a-fa9e-4be3-8f3f-85db9aa57fda` | `bbc9a96a...` | 1792379763204 | **BUY** | 39,337,735.68 | 39,237,736.96 | 39,237,736.96 | **-99,998.72 COP** | **LOSS** |
| **2** | `8a87086c-c941-47ef-94b1-bafec9335c3a` | `0f644510...` | 1792379884756 | **BUY** | 39,237,736.96 | 39,137,735.68 | 39,137,735.68 | **-100,001.28 COP** | **LOSS** |
| **3** | `69efc7bc-4097-4384-b611-b562c2df5fa8` | `fc7bbff7...` | 1792380003704 | **BUY** | 39,137,735.68 | 39,037,736.96 | 39,037,736.96 | **-99,998.72 COP** | **LOSS** |
| **4** | `8c9cc568-f89d-41ad-842b-b37a0466b384` | `1e8d3be5...` | 1792380123171 | **SELL** | 39,037,736.96 | 38,937,735.68 | 39,120,737.28 | **+83,000.32 COP** | **WIN** |

### Verificación Aritmética de Balance:
$$\Delta\text{ Balance Teórico} = (-99,998.72) + (-100,001.28) + (-99,998.72) + (+83,000.32) = \mathbf{-216,998.40\text{ COP}}$$
$$\Delta\text{ Balance Real Medido} = 39,120,737.28 - 39,337,735.68 = \mathbf{-216,998.40\text{ COP}}$$
$$\text{Discrepancia Contable} = |\Delta\text{ Teórico} - \Delta\text{ Real}| = \mathbf{\$0.00\text{ COP (Cero absoluto, cero leaks)}}$$

---

## 3. AUDITORÍA FORENSE DE PARIDAD LIVE ↔ REPLAY (FASE C)

### 3.1 Datos Procesados:
- **Ticks Live de la ventana:** 2,889 ticks íntegros en `ticks_live.csv` (2,001 ticks pre-ventana podados al inicio).
- **Trades en Live:** 4 órdenes reales confirmadas en hardware.
- **Trades simulados en Replay:** 8 órdenes en `replay_journal.csv`.

### 3.2 Matriz de Comparación Formal (`compare_live_replay.py`, `--mode trades`, tol=6000 ms):
- **Trades en Live:** 4
- **Trades en Replay:** 8
- **Pares formalmente evaluados:** **4 pares**
- **Exact Matches:** **3 pares (75.0%)**
  1. Live `BUY` @ $1792379763204$ vs Replay `BUY` @ $1792379761139$ ($\Delta t = -2,065$ ms) $\implies$ **MATCH** (ambos LOSS).
  2. Live `BUY` @ $1792379884756$ vs Replay `BUY` @ $1792379881108$ ($\Delta t = -3,648$ ms) $\implies$ **MATCH** (ambos LOSS).
  3. Live `SELL` @ $1792380123171$ vs Replay `SELL` @ $1792380121131$ ($\Delta t = -2,040$ ms) $\implies$ **MATCH** (ambos WIN).
- **Divergencias:** **1 par (25.0%)**
  - Live `BUY` @ $1792380003704$ vs Replay `SELL` @ $1792380001067$ ($\Delta t = -2,637$ ms) $\implies$ **DIVERGENCIA DE ACCIÓN (`action`)**. Causa raíz: En segundo :01 el replay detectó rechazo en resistencia para SELL, mientras que el motor en vivo evaluó en segundo :03 con impulso alcista hacia soporte generando BUY.
- **Missing en Live:** 4 (Replay simuló trades a $t=1792379522$, $1792379642$, $1792380241$ y $1792380421$ que en Live fueron suprimidos por cooldown/adaptive block).
- **Missing en Replay:** 0 (el 100% de los trades Live tuvieron contraparte evaluable en Replay).
- **Tasa de Paridad en W08:** **75.0% (3 / 4)**.

### 3.3 Evaluación Epistémica bajo Preregistro R23J (§2):
- W08 aporta 4 pares formales.
- Acumulado Cubo 1 (W07 + W08): **5 pares evaluados** (4 Matches, 1 Divergencia $\implies$ Match Rate = **80.0%**).
- Conforme al Preregistro R23J §2 ($N_{\text{pares}} \ge 10$), el estado de paridad formal estadística se mantiene rigurosamente clasificado como:
$$\mathbf{NO\ CONCLUYENTE\ (N_{\text{pares}} = 5 < 10)}$$

---

## 4. ANÁLISIS CONTRAFACTUAL DE `VETO_BUY_EXHAUSTION_CLIMAX` EN W08

- **Activaciones totales:** 15 bloqueos distribuidos en 4 minutos de vela únicos en `UPTREND` con `callPower >= 70%`:
  1. Minuto 29872988 ($t=1792379281–85$ ms): 5 vetos $\to$ A 60s: $+0.00000032$ (WIN).
  2. Minuto 29872989 ($t=1792379341–45$ ms): 2 vetos $\to$ A 60s: $+0.00000040$ (WIN).
  3. Minuto 29872991 ($t=1792379461–64$ ms): 4 vetos $\to$ A 60s: $-0.00000067$ (LOSS severo en giro de mercado).
  4. Minuto 29873008 ($t=1792380481–83$ ms): 4 vetos $\to$ A 60s: $+0.00000001$ (TIE / neutro).
- **Evaluación:** El veto protegió activamente del fallo abrupto del minuto 29872991 donde las compras sufrieron caída masiva (-67 pts), manteniendo una protección de riesgo asimétrica en techos de volatilidad.

---

## 5. CONSOLIDACIÓN DE LOS 3 CUBOS PRE-REGISTRADOS (W01 A W08)

### 5.1 Cubo 1 — Integridad y Paridad Post-Congelado (W07 + W08):
- **Pares formales evaluados:** **5 pares** (1 en W07 + 4 en W08).
- **Exact Matches:** **4 pares** (80.0%).
- **Divergencias:** **1 par** (action).
- **Veredicto Preregistro §2:** **`NO CONCLUYENTE (N=5 < 10)`**.

### 5.2 Cubo 2 — Desempeño Homogéneo con Veto (W04 + W05 + W06 + W07 + W08):
- **Total Órdenes Reales:** **24 trades**.
- **Victorias / Derrotas:** **16 Wins / 8 Losses**.
- **Win Rate:** **66.67%** (supera el breakeven del 54.64%).
- **Intervalo Wilson al 95%:** **[46.71%, 82.03%]**.
- **Veredicto Preregistro §3:** **`NO CONCLUYENTE (N=24 < 40; Límite inferior 46.71% < 54.64%)`**.

### 5.3 Cubo 3 — Histórico Heterogéneo Pre-Veto (W01 + W02 + W03):
- **Total Órdenes Reales:** **11 trades** (7 Wins / 4 Losses, $WR = \mathbf{63.64\%}$).

### 5.4 Consolidado Total Acumulado (Cubo 2 + Cubo 3, W01 a W08):
- **Total Operaciones Confirmadas ($N$):** **35 trades reales**.
- **Victorias / Derrotas:** **23 Wins / 12 Losses** (0 VOID).
- **Win Rate Global:** **65.71%** ($WR_{\text{BE}} = 54.80\%$).
- **Intervalo Wilson al 95%:** **[49.15%, 79.17%]**.
- **Variación Neta de Capital:** **+695,001.60 COP DEMO** (+1.79% de ganancia neta).
- **Desglose Direccional Acumulado:**
  * **`BUY` (Compras):** $N = 19$ ($12\text{ Wins} / 7\text{ Losses} \implies \mathbf{63.16\% WR}$).
  * **`SELL` (Ventas):** $N = 16$ ($11\text{ Wins} / 5\text{ Losses} \implies \mathbf{68.75\% WR}$).

---

## 6. CONCLUSIÓN Y RECOMENDACIÓN OPERATIVA PARA AGY

1. **Gobernanza de Riesgo y Contabilidad:** Impecable. Discrepancia $0.00 COP, drawdown 1.02% ampliamente inferior al límite de 3.5%, seguridad DEMO fail-closed 100% activa.
2. **Desempeño W08:** Ventana con varianza adversa en compras ($0\text{ W} / 3\text{ L}$), compensada por venta ganadora ($1\text{ W} / 0\text{ L}$). El balance acumulado se mantiene sólidamente en positivo (**+695,001.60 COP** en 35 operaciones).
3. **Avance en Paridad:** W08 aportó **4 pares evaluados** (elevando Cubo 1 a $N=5$). La tasa de exact match en W08 fue del 75.0% y acumulada del 80.0%.
4. **Recomendación para el Loop:** Conforme a las reglas de recolección homogénea sin optimización apresurada (YAGNI), se debe **continuar la recolección en ventanas homogéneas idénticas** (Ventana 09) para alcanzar los 10 pares requeridos en Cubo 1 y los 40 trades en Cubo 2, manteniendo la estrategia estrictamente congelada.

```text
======================================================================
DICTAMEN FINAL FASE C (VENTANA 08):
OPERATIVA Y CONTABILIDAD = PASS (DISCREPANCIA $0.00 COP, DD 1.02% < 3.5%)
REPLAY PARITY ESTADÍSTICA = NO CONCLUYENTE (CUBO 1: N=5 < 10 PARES)
CUBO 2 HOMOGÉNEO = NO CONCLUYENTE (N=24 < 40 TRADES, WR 66.67%)
======================================================================
```
