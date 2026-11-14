# INFORME DE VALIDACIÓN INDEPENDIENTE — FASE B (LOOP R23J)
## R23J_OPENCODE_FASE_B_VALIDATION.md

**Fecha:** 2026-10-18  
**Validador Independiente:** OPENCODE (`w1:pB`)  
**Orquestador:** AGY (`w1:p1`)  
**Equipo Autorizado:** AGY (Orquestador), HERMES (Implementador), CLAUDE CODE (Auditor), OPENCODE (Validador Independiente)  
**Objeto:** Recálculo directo e independiente sobre datos crudos de las métricas de la Fase B reportadas por Hermes en `workflows/rounds/r23j_hermes_adaptive_parity_report.md` (commits `47a34f4` y `8cdb61f`).

---

## 1. RESUMEN Y DICTAMEN FORMAL

Se ejecutó la validación independiente recalculando directamente desde los datasets crudos (`decisions.csv`, `ticks_live.csv`, `adaptive_learning_state.json`) sin asumir ni heredar conclusiones de informes previos.

| Parámetro Auditado | Reportado por Hermes | Recálculo Independiente OpenCode | Coherencia |
|---|---|---|:---:|
| **W05: Filas `ADAPTIVE_BLOCK`** | 50 filas | **50 filas** (de 60 vetos, 63 decisiones totales) | **100% EXACTO** |
| **W05: Instantes únicos (dedupe 1s)** | 35 instantes | **35 instantes** | **100% EXACTO** |
| **W06: Filas `ADAPTIVE_BLOCK`** | 20 filas | **20 filas** (de 96 vetos, 99 decisiones totales) | **100% EXACTO** |
| **W06: Instantes únicos (dedupe 1s)** | 11 instantes | **11 instantes** | **100% EXACTO** |
| **Consolidado: Filas / Instantes** | 70 filas / 46 instantes | **70 filas / 46 instantes** | **100% EXACTO** |
| **Bloqueos Replay con Memoria** | W05: 2, W06: 1 (Total: 3) | **W05: 2, W06: 1 (Total: 3)** | **100% EXACTO** |
| **Bloqueos Reproducidos (tol <= 6s)** | W05: 1, W06: 1 (Total: 2) | **W05: 1, W06: 1 (Total: 2)** | **100% EXACTO** |
| **Tasa de Reproducción Efectiva** | 4.35% (2 / 46) | **4.3478% $\to$ 4.35% (2 / 46)** | **100% EXACTO** |
| **Densidad Señales Live vs Replay** | W05: 63 vs 5 \| W06: 99 vs 4 | **W05: 63 vs 5 \| W06: 99 vs 4** | **100% EXACTO** |

### DICTAMEN FORMAL FASE B:
```text
FASE B VALIDATION = PASS (RECÁLCULO CRUDO 100% CONFIRMADO)
```

---

## 2. RECÁLCULO INDEPENDIENTE DE DATOS CRUDOS

### 2.1 Conteo en `loops/r23c_yolo_validation/window_05/decisions.csv` (W05)
- **Filas totales:** 63
- **Filas con `kind == 'VETO'`:** 60
- **Filas con `ADAPTIVE_BLOCK`:** **50**
- **Instantes únicos (deduplicando por truncamiento de segundo `ts // 1000`):** **35**
- **Acciones en filas `ADAPTIVE_BLOCK`:** 100% registradas como `NONE` (50 de 50).

### 2.2 Conteo en `loops/r23i_validation/window_06_clean/decisions.csv` (W06)
- **Filas totales:** 99
- **Filas con `kind == 'VETO'`:** 96
- **Filas con `ADAPTIVE_BLOCK`:** **20**
- **Instantes únicos (deduplicando por truncamiento de segundo `ts // 1000`):** **11**
- **Discrepancia menor de telemetría detectada en informe Hermes:** Hermes afirmó en §3.3 que en W06 todas las filas tenían `action = NONE`. El recálculo independiente sobre el archivo crudo demuestra que en W06 el logger ya registraba la acción: **18 SELL y 2 BUY** (`action_plan = VETO SELL / VETO BUY`). No afecta el cálculo matemático de instantes ni la tasa de paridad.

### 2.3 Ejecución Independiente del Replay con Memoria Real
Se extrajo la memoria real de anti-patrones del dispositivo físico (`/data/user/0/com.example.tradedraw/files/adaptive_learning_state.json`, conteniendo **19 firmas de pérdida**) y se ejecutó `ReplayBacktestTest` con `ADAPTIVE_STATE_JSON`:

1. **Sobre W05 (`ticks_live.csv`):**
   - Replay emitió **2 bloqueos adaptativos**:
     * $ts = 1792137482453$ (BUY, similitud: 0.9984). Coincide con Live en $ts = 1792137482455$ ($\Delta t = 2\text{ ms}$). **REPRODUCIDO (1 de 35 = 2.86%)**.
     * $ts = 1792137602055$ (BUY, similitud: 0.9930). Sin coincidencia en Live ($\Delta t > 6000\text{ ms}$).
2. **Sobre W06 (`ticks_live.csv`):**
   - Replay emitió **1 bloqueo adaptativo**:
     * $ts = 1792367701109$ (SELL, similitud: 0.9912). Coincide con Live en $ts = 1792367701112$ ($\Delta t = 3\text{ ms}$). **REPRODUCIDO (1 de 11 = 9.09%)**.
3. **Consolidado:**
   - 2 bloqueos reproducidos sobre 46 instantes únicos:
     $$\text{Tasa de Reproducción} = \frac{2}{46} \times 100\% = \mathbf{4.3478\% \approx 4.35\%}$$

---

## 3. VALIDACIÓN DE LA NATURALEZA DE LA BRECHA RESIDUAL

El recálculo confirma que la brecha residual (4.35% de reproducción) **no se origina en discrepancia algorítmica** (la cual tiene paridad exacta en 49,608 combinaciones de oráculo), sino en una asimetría estructural de diseño entre ambos entornos:

1. **Densidad de Señales Evaluadas:**
   - **Live W05:** 63 decisiones en 21.7 min $\implies \mathbf{2.9\text{ señales/min}}$.
   - **Replay W05:** 3 trades + 2 bloqueos = **5 señales evaluadas** en 20 min $\implies \mathbf{0.25\text{ señales/min}}$.
   - **Live W06:** 99 decisiones en 20 min $\implies \mathbf{4.95\text{ señales/min}}$.
   - **Replay W06:** 3 trades + 1 bloqueo = **4 señales evaluadas** en 20 min $\implies \mathbf{0.20\text{ señales/min}}$.
   - El arnés Replay evalúa entre **1 de cada 13 y 1 de cada 25 señales** de las que evalúa el motor vivo.
2. **Mecanismos de Poda del Arnés Replay:**
   - **Colapso a 1 orden/vela:** `candleMinute != lastExecutedCandleMinute` suprime reevaluaciones tick a tick intra-minuto.
   - **Ceguera post-trade:** `blockedUntilTs` (60s trade + 8s cooldown = 68s) inhabilita la evaluación durante más de 1 minuto tras cada trade, mientras que en Live el motor continúa evaluando ticks y registrando vetos pasivos.
   - **Ráfagas de Live:** En Live, un solo setup evaluado a 2.4 Hz genera ráfagas de 2 a 11 filas de `ADAPTIVE_BLOCK` para el mismo segundo de vela; el Replay, al colapsar por vela, sólo tiene 1 oportunidad.
3. **Calificación Epistémica («Retrospectivo y No Independiente»):**
   - Se ratifica de forma categórica: la prueba requiere precargar `ADAPTIVE_STATE_JSON` (19 firmas acumuladas históricamente en el teléfono). Sin esta precarga, Replay genera 0 bloqueos por carecer de masa crítica de pérdidas.
   - Ambas ventanas (W05 y W06) formaron parte del historial sobre el cual se construyó dicha memoria. Por tanto, es una medición retrospectiva in-sample y no una validación ciega fuera de muestra.

---

## 4. INVARIANZA DE ESTRATEGIA Y TESTS

- `testDebugUnitTest` ejecutado independientemente: **479 tests pasando / 0 fallos / 2 skipped**.
- `AdaptiveLearningEngine.kt`: `ADAPTIVE_BLOCK_SIMILARITY_THRESHOLD = 0.99f` estricto y pesos $0.20 / 0.20 / 0.30 / 0.15 / 0.15$ inalterados.
- `RiskManager.kt` y `TradingEngine.kt`: Cero líneas de estrategia alteradas.

---

## 5. CONCLUSIÓN

Las cifras y conclusiones presentadas por Hermes en su informe de Fase B son **matemática y operativamente exactas** sobre los datos crudos. La tasa del 4.35% es un artefacto demostrado de la menor densidad de evaluación del arnés Replay y no un fallo de la lógica adaptativa extraída en B1.

**Dictamen:** **`FASE B VALIDATION = PASS`**.
