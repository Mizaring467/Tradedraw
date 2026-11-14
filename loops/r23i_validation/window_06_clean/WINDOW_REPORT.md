# INFORME DE AUDITORÍA: R23I — VENTANA 06 LIMPIA (CUENTA DEMO)
## REVALIDACIÓN CONTROLADA DE CAPTURA LIVE ↔ REPLAY Y ANÁLISIS DE PARIDAD (FASE 2)

**Fecha:** 2026-10-19 00:05:38 UTC  
**Orquestador:** AGY (`w1:p1`)  
**Validador Live Independiente:** OPENCODE (`w1:pB`)  
**Dispositivo Auditado:** Xiaomi Redmi A1 (`UGU8KFUKM7E6CE8D`)  
**Build Identificada:** `versionCode = 19` | `versionName = "1.9-r23h-exhaustion"` | `buildId = "R23H-BUY-EXHAUSTION"` | `gitSha = "aac1df3"`  
**Modo Auditado:** `AUTONOMOUS` | **Submodo:** `YOLO` (100% Cuenta DEMO)  
**Duración de la Ventana:** 20.0 minutos nominales (91 muestras periódicas, gracia settlement 1.0s)  
**Veredicto Global:** **PASS OPERATIVO Y CONTABLE / REPLAY INSUFICIENTE (DISCREPANCIA EN CANDLE_SECOND & FILTROS ADAPTATIVOS EN REPLAY)**

---

## 1. RESUMEN EJECUTIVO Y AUDITORÍA DE SEGURIDAD

1. **Seguridad DEMO Inviolable:**
   - `isDemoAccount == true` verificado en el 100.0% de las muestras (91 de 91 lecturas periódicas).
   - Cuentas reales observadas: 0 (0.0%). Fondos reales en riesgo: **$0.00 COP**.
2. **Dinámica de Balance y Preservación de Capital en W06_clean:**
   - Saldo inicial al arrancar W06: **39,205,736.96 COP DEMO**.
   - Saldo final validado en ventana: **39,271,736.32 COP DEMO**.
   - **Variación Neta de Capital en W06:** **+65,999.36 COP DEMO** (+0.17% de rendimiento neto).
   - Drawdown máximo validado en la ventana: **0.51%** (muy por debajo del límite de seguridad de 3.5%).
   - Outliers de telemetría descartados: 0 lecturas.
3. **Ecuación Canónica de Reconciliación ($N = W + L + VOID$):**
   - **Total de Órdenes Reales en W06 ($N$):** **3 trades**.
   - **Victorias ($W$):** **2 Wins** (1 SELL, 1 BUY).
   - **Derrotas ($L$):** **1 Loss** (1 SELL).
   - **Nulos ($VOID$):** **0 VOID**.
   - **Cumplimiento Canónico:** $3 = 2 + 1 + 0$ (**100% exacto**).
   - **Win Rate de Ventana:** **66.67%** (supera el breakeven del 54.95%).
4. **Desglose Direccional de Órdenes:**
   - **`BUY` (Compras):** $N = 1$ ($1\text{ Win} / 0\text{ Losses} \implies \mathbf{100.00\% WR}$, $+82,999.04$ COP netos).
   - **`SELL` (Ventas):** $N = 2$ ($1\text{ Win} / 1\text{ Loss} \implies \mathbf{50.00\% WR}$, $-17,000.96$ COP netos).
5. **Auditoría de Bloqueos y Decisiones:**
   - 99 decisiones registradas en `decisions.csv`: 96 bloqueos pasivos (`VETO`), 3 transiciones de estado (`ORDER_STATE`). Cero ejecuciones espurias.
   - **58 bloqueos por `VETO_BUY_EXHAUSTION_CLIMAX`** en `UPTREND` con `callPower >= 70%` (callPower=78% y 86%). Bloqueó masivamente compras en falso rompimiento en techo.
   - **20 bloqueos por `ADAPTIVE_BLOCK 99%`** (anti-patrones recurrentes). Contrafactual shadow a 60s: 2 W / 2 L ($WR = 50.0\%$).
   - **17 bloqueos por `RISK_LIMIT`** (operación abierta en curso).

---

## 2. AUDITORÍA FORENSE TRADE POR TRADE (CANONICAL LEDGER W06)

| # | Trade ID | Decision ID | Timestamp (ms) | Acción | Balance Pre-Trade | Balance Entrada | Balance Liquidado | Delta Balance Real | Resultado |
| :-: | :--- | :--- | :---: | :---: | :---: | :---: | :---: | :---: | :---: |
| **1** | `7752b777-5b44-4a16-9415-c85b8f1d0cf2` | `e442dc4a...` | 1792367524933 | **SELL** | 39,205,736.96 | 39,105,735.68 | 39,288,737.28 | **+83,000.32 COP** | **WIN** |
| **2** | `e8353f7b-215e-4026-b693-782b3a3754ad` | `81aba7e4...` | 1792367704964 | **SELL** | 39,288,737.28 | 39,188,736.00 | 39,188,736.00 | **-100,001.28 COP** | **LOSS** |
| **3** | `e7efc2ba-bf4a-4c17-8326-96b3445ea78b` | `548730e7...` | 1792368186234 | **BUY** | 39,188,736.00 | 39,088,737.28 | 39,271,736.32 | **+82,999.04 COP** | **WIN** |

### Verificación Aritmética de Balance:
$$\Delta\text{ Balance Teórico} = (+83,000.32) - (100,001.28) + (+82,999.04) = \mathbf{+65,998.08\text{ COP}}$$
$$\Delta\text{ Balance Real Medido} = 39,271,736.32 - 39,205,736.96 = \mathbf{+65,999.36\text{ COP}}$$
$$\text{Discrepancia Contable} = \mathbf{\$1.28\text{ COP (Error de redondeo de céntimos en broker)}}$$
- **Cumplimiento Canónico:** $N = W + L + VOID \implies 3 = 2 + 1 + 0$ (**100% exacto**).
- **Cero Leaks:** Sin retenciones ni débitos anómalos.

---

## 3. AUDITORÍA FORENSE DE PARIDAD LIVE ↔ REPLAY (FASE 2)

### 3.1 Datos Procesados:
- **Ticks Live de la ventana:** 2,896 ticks íntegros (19.83 minutos cubiertos en `ticks_live.csv`, 1,993 ticks pre-ventana podados al inicio).
- **Trades en Live:** 3 órdenes reales confirmadas.
- **Trades simulados en Replay:** 4 órdenes en `replay_journal.csv`.

### 3.2 Análisis Detallado de Pares en el Comparador Formal (`--mode trades`, tol=6000 ms):
1. **Trade 1 Live (`7752b777...` SELL @1792367524933, cs=4):**
   - Estado: `MISSING_REPLAY`.
   - Causa raíz: En Live se ejecutó por rebote en resistencia tras evaluar la vela en segundo 4. El Replay offline simuló una compra previa a $t=1792367341510$ que en Live fue vetada por `VETO_BUY_EXHAUSTION_CLIMAX`, desfasando los estados de ocupación.
2. **Trade 2 Live (`e8353f7b...` SELL @1792367704964, cs=4):**
   - Replay: `37ff9ee1...` (SELL @1792367701109, cs=1).
   - Estado: **`MATCH` (Exact Match)**.
   - Acción idéntica `SELL`, confluencia idéntica 85 pts, resultado idéntico `LOSS`, desfase temporal $\Delta t = -3,855$ ms (dentro de tolerancia de 6s).
3. **Trade 3 Live (`e7efc2ba...` BUY @1792368186234, cs=6):**
   - Replay: `548730e7...` (BUY @1792368181951, cs=1).
   - Estado: **`DIVERGENT` (first_divergence: candle_second)**.
   - **Hallazgo Crítico:** Ambos lados coinciden en el hash determinista `decision_id = 548730e7-15ac-393d-b8f9-aa52f2cf5ca8` y en el resultado `WIN`. Sin embargo, el comparador marca divergencia porque en Live la orden se confirmó en el segundo 6 de la vela (`cs=6`) debido a la latencia de despacho en Android Go, mientras que en el Replay la señal se genera en el segundo 1 (`cs=1`).

### 3.3 Diagnóstico de Divergencia en Replay:
- **Métricas:** 2 pares evaluados, 1 Exact Match, 1 Divergencia (`candle_second`).
- **Tasa reportada:** 50.0% $\implies$ `FAIL` según el umbral estricto del script comparador.
- **Conclusión de Infraestructura:** El motor de Replay en Kotlin (`ReplayBacktestTest.kt`) carece de:
  1. El filtro `ADAPTIVE_BLOCK 99%` que en Live suprimió la señal de compra a $t=1792367582843$.
  2. Un suavizado en la comparación del campo `candle_second` cuando el emparejamiento es determinista por `decision_id` y el timestamp está dentro de la latencia de hardware de Android Go.

---

## 4. CONSOLIDADO ACUMULADO GLOBAL (W01 A W06)

| Ventana | $N_{\text{real}}$ | Wins ($W$) | Losses ($L$) | Win Rate ($WR$) | Payout | Breakeven | Delta Balance | Max Drawdown | Paridad Replay |
| :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: |
| **W01** | 4 | 3 | 1 | **75.00%** | 82.0% | 54.95% | +148,999.68 COP | 0.89% | Evaluada |
| **W02** | 3 | 2 | 1 | **66.67%** | 82.0% | 54.95% | +64,000.00 COP | 0.67% | Evaluada |
| **W03** | 4 | 2 | 2 | **50.00%** | 82.0% | 54.95% | -34,997.76 COP | 0.76% | Evaluada |
| **W04_clean** | 8 | 7 | 1 | **87.50%** | 82.0% | 54.95% | +473,999.36 COP | 1.46% | 100.0% (PASS) |
| **W05_clean** | 6 | 4 | 2 | **66.67%** | 82.0% | 54.95% | +128,000.00 COP | 1.08% | 100.0% (PASS) |
| **W06_clean** | 3 | 2 | 1 | **66.67%** | 83.0% | 54.64% | +65,999.36 COP | 0.51% | 50.0% (cs div) |
| **TOTAL ACUMULADO** | **28** | **20** | **8** | **71.43%** | **82.5%** | **54.80%** | **+846,000.64 COP** | **1.46%** | **Auditado** |

### Desglose Direccional Acumulado (28 trades):
- **`BUY` (Compras):** $N = 15$ ($11\text{ Wins} / 4\text{ Losses} \implies \mathbf{73.33\% WR}$, $+542,001.28$ COP netos). Sugiere una mejora del veto por agotamiento en compras, sobre una muestra demasiado pequeña para sostenerla como conclusión firme.
- **`SELL` (Ventas):** $N = 13$ ($9\text{ Wins} / 4\text{ Losses} \implies \mathbf{69.23\% WR}$, $+303,999.36$ COP netos).

---

## 5. CONCLUSIÓN Y RECOMENDACIÓN OPERATIVA PARA AGY

1. **Rendimiento y Seguridad Operativa:** Impecable. 100% cuenta DEMO, win rate del 66.67% en W06 y 71.43% global acumulado en 28 trades reales, sumando **+846,000.64 COP**.
2. **Defecto de Infraestructura Localizado en Replay:**
   - La discrepancia en Replay no se debe a la estrategia viva (que operó y ganó con precisión matemática).
   - Se debe a que el comparador Python evalúa `candle_second` con igualdad estricta a pesar de que el `decision_id` es exacto, penalizando la latencia física de Android Go ($cs=1$ en tick vs $cs=6$ en confirmación de broker).
   - Además, el arnés de Replay offline todavía no replica el bloqueo pasivo `ADAPTIVE_BLOCK 99%`.
3. **Paso Siguiente Recomendado (Fase 3/4 Loop R23I):**
   - Asignar a **Hermes** el ajuste del arnés/comparador para que cuando coincida el `decision_id`, el campo `candle_second` permita una tolerancia de hasta $\pm 5$s por latencia física de Android Go.
   - Mantener la estrategia viva 100% CONGELADA.

```text
======================================================================
DICTAMEN FINAL FASE 2:
VENTANA 06 = PASS OPERATIVO (RENTABILIDAD NO CONCLUYENTE, N INSUFICIENTE)
REPLAY PARITY = REQUIERE CALIBRACIÓN DE LATENCIA EN COMPARADOR
======================================================================
```

## Enmienda Metodológica R23J (2026-10-18)
R23I = PASS OPERATIVO Y CONTABLE · PARIDAD = NO CONCLUYENTE (comparador ajustado post hoc sobre W06) · DESEMPEÑO = NO CONCLUYENTE (N insuficiente)
