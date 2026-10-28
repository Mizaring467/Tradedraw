# PREREGISTRO R23D — ROBUSTEZ DE TELEMETRÍA Y VALIDACIÓN PROLONGADA DEL CANDIDATO YOLO (DEMO)

**Fecha de fijación:** 2026-10-16  
**Rama:** `r10-night`  
**Build congelada para validación:** `versionCode = 16` | `versionName = "1.6-r23d-telemetry"` | `BUILD_ID = "R23D-TELEMETRY"`  
**Firmante:** AGY (Orquestador R23D / TradeDraw)  
**Implementador:** HERMES (`w1:p4`)  
**Validador Live:** OPENCODE (`w1:p6`)  

---

## 1. Justificación y Delimitación Epistemológica

### 1.1. Contexto Empírico Previo
1. **Fase 1 (Baseline YOLO `ac079fd`, vc=14):**
   - 6 ventanas de 20 min, $N=23$ trades.
   - $W=11, L=12, WR=47.83\%$ vs $WR_{BE} = 54.95\%$. Delta: $-114.000\text{ COP}$.
   - Sesgo direccional: $60.9\%$ operaciones en SELL con $WR=42.86\%$ ($18.2\%$ en confirmados por balance).
2. **Fase 3 (Candidato Endurecido SELL `e3fdba4`, vc=15):**
   - 3 ventanas de 20 min, $N=4$ trades.
   - $W=2, L=2, WR=50.0\%$. Delta: $-33.999,36\text{ COP}$.
   - Proporción SELL reducida exitosamente a $25.0\%$ (1/4) mediante supresión por confluencia ($\ge 80$).
   - Hito técnico: 2 ventanas consecutivas con **100% PASS** en paridad Live ↔ Replay.
   - **Veredicto Fase 3:** La estrategia alteró la distribución direccional reduciendo la pérdida neta 3×, pero $N=4$ es estadísticamente insuficiente para confirmar superioridad frente al break-even.

### 1.2. Decisión Metodológica R23D
- **NO modificar la lógica de la estrategia:** Se congela estrictamente la calibración de Fase 3 (confluencia SELL $\ge 80$, mecha contra-tendencia SELL $\ge 45\%$, MT_REJECTION PUT $\ge 40\%$, BUY preservado en 75/30%/30%).
- **Solventar 4 defectos instrumentales de medición y telemetría:**
  1. **Bug 1 (Settlement Grace Period):** Extensión automática de hasta 75s al expirar la ventana si hay un trade pendiente o en curso, asegurando que su resolución se capture in-window sin orfandad.
  2. **Bug 2 (Glitch de Balance y Drawdown Limpio):** Filtro de outliers que aísla lecturas espurias momentáneas ($> 10\times\text{stake}$ sin trade) y computa el drawdown sobre `validated_balance`.
  3. **Bug 3 (Desincronización de Cooldown en Android):** Enlazar `RiskManager.currentSubMode` con `TradingEngine.autonomousSubMode` e inyectar el submodo en `HealthSupervisor` y `/status`, eliminando la falsa alarma de 180s en submodo YOLO (35s nominal).
  4. **Bug 4 (Score de Confluencia Real en Monitor):** Extracción del puntaje cuantitativo real desde el `detail`/`reason` (`[Confluencia: NNpts]`) en lugar de fijar 85.0 hardcodeado.
- **Validación Prolongada:** Acumular una muestra suficiente de $N \ge 30$ operaciones (meta preferida $N \ge 50$) a través de hasta 10 ventanas de 20 minutos en cuenta DEMO.

---

## 2. Parámetros Congelados de la Estrategia (Inmutables en R23D)

| Parámetro | Submodo YOLO | Modos Restantes |
|---|---|---|
| **Confluencia Mínima (BUY)** | **75 pts** | 80 pts |
| **Confluencia Mínima (SELL)** | **80 pts** | 80 pts |
| **Mecha Contra-Tendencia (BUY)** | **30%** | 50% |
| **Mecha Contra-Tendencia (SELL)** | **45%** | 50% |
| **Mecha Rechazo `MT_REJECTION` (CALL/BUY)** | **30%** | 45% |
| **Mecha Rechazo `MT_REJECTION` (PUT/SELL)** | **40%** | 45% |
| **Cooldown tras Derrota en YOLO** | **35s** | 180s |

---

## 3. Regla Absoluta de Seguridad y Entorno

- Toda la validación se ejecuta **100% en CUENTA DEMO** (`isDemoAccount == true`).
- Dispositivo auditado: Xiaomi Redmi A1 (`220733SL`, serial `UGU8KFUKM7E6CE8D`) vía USB ADB.
- Activo: `Z-CRY/IDX` en Binomo (`com.marketly.trading`).
- Si en cualquier instante `isDemoAccount != true`: aborto fail-closed inmediato mediante `emergency_shutdown`.

---

## 4. Plan de Validación y Criterios Formales de Veredicto

### 4.1. Protocolo de Ejecución
- Serie de hasta 10 ventanas de 20 minutos (`loops/r23d_validation/window_01` a `window_10`).
- Si se alcanza $N \ge 30$ trades consolidados antes de la ventana 10 con significancia estadística concluyente, se podrá declarar el cierre anticipado.

### 4.2. Criterios de Decisión al Cierre
1. **KEEP (Aprobar Candidato):**
   - $N \ge 30$ operaciones confirmadas.
   - $WR \ge 55.0\%$ (superando el break-even empírico $WR_{BE} \approx 54.95\%$).
   - $\Delta \text{ Balance} > 0\text{ COP}$ DEMO.
   - Proporción SELL estabilizada y controlada ($< 45\%$).
2. **REVERT (Descartar y Revertir a Baseline o Rediseñar):**
   - $N \ge 30$ operaciones confirmadas.
   - $WR < 50.0\%$ con significancia estadística ($p < 0.05$ vs $WR_{BE}$) o drawdown no controlado.
3. **INCONCLUSIVE (Muestra Insuficiente / Varianza Alta):**
   - $N < 30$ tras completar las 10 ventanas planificadas.
   - O $WR$ dentro del intervalo neutro $[50.0\%, 54.9\%]$ sin divergencia estadística suficiente.

---

## 5. Firmas y Asignaciones
- **Orquestador y Diagnóstico:** AGY
- **Implementador Tooling & Telemetría:** HERMES (`w1:p4`)
- **Validador Live Independiente:** OPENCODE (`w1:p6`)
