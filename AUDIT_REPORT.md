# 🔍 INFORME DE AUDITORÍA TÉCNICA, ESTADÍSTICA Y METODOLÓGICA — TRADEDRAW

**Autor:** Antigravity (Orquestador Técnico Principal)  
**Fecha de Auditoría:** 3 de Octubre de 2026  
**Proyecto:** TradeDraw (Android Headless / Quant Trading Engine)  
**Repositorio:** `/root/.gemini/antigravity/scratch/Tradedraw`  
**Estado:** FASE 1 COMPLETADA — AUDITORÍA Y LEVANTAMIENTO DE EVIDENCIA (SIN MODIFICACIONES DE CÓDIGO OPERATIVO)

---

## RESUMEN EJECUTIVO

Se completó una auditoría exhaustiva e independiente sobre la totalidad de los componentes algorítmicos, modelos matemáticos, scripts de investigación cuantitativa, datasets y telemetría histórica del proyecto TradeDraw.

### Conclusión Principal:
La discrepancia entre los resultados cuantitativos hiper-optimistas documentados en `loops/CRYPTO_IDX_QUANT_RESEARCH.md` (e.g. **91.67% Win Rate en Reversión**, **83.43% en Continuación**, **Sharpe Anualizado de 157.07**) y el rendimiento real en producción del dispositivo (**Win Rate 41.5% – 42.0%**, **Esperanza Matemática −0.24 COP/trade**) **NO se debe a variación aleatoria del mercado ni a desfase temporal**.

Se debe a la confluencia de **cuatro fallas estructurales críticas**:
1. **Sesgo Circular en el Generador Sintético (Tautología Matemática):** El modelo en Python (`scripts/quant_crypto_idx_model.py`) generó micro-ticks inyectando determinísticamente un proceso Ornstein-Uhlenbeck que fuerza la reversión a la media hacia el centro del canal ($drift = -0.018 \cdot (P - \text{anchor})$) cada vez que el precio toca S/R. Luego se evaluó si la estrategia de reversión funcionaba sobre esos datos artificiales.
2. **Bug Crítico de Implementación en Producción (`SyntheticCandleEngine.kt`):** El cálculo de `deltaP` para el indicador de flujo de ticks (`tickFlowImbalance`, TFI) se realiza después de sobrescribir `lastTickPrice = tick.price`. Como consecuencia matemática, $\Delta P = 0$ siempre, $TFI \equiv 0.0$ de forma permanente, y las estrategias cuantitativas de microestructura **nunca se ejecutaron en el dispositivo**.
3. **Muestras Estadísticamente Nulas en Walk-Forward:** Las ventanas Out-Of-Sample (OOS) del análisis cuantitativo contenían entre 2 y 9 operaciones por fold ($N_{total} = 27$ trades en todo el OOS), con cálculo incorrecto de promedios no ponderados y sin ajuste por comparaciones múltiples.
4. **Bypass de Gestión de Riesgo y Glitches de Saldo:** En `RiskManager.kt`, el modo YOLO evade completamente el límite de `takeProfitWins`, mientras que el log de telemetría del dispositivo contiene lecturas corruptas de balance ($1.00$ COP) y trades sin movimiento clasificados arbitrariamente como pérdidas.

---

## 1. FLUJO REAL DE DATOS: ARQUITECTURA Y DESCONEXIONES

```
[WebSocket Binomo] (Tick JSON)
       │
       ▼
[BinomoWebSocketClient]
       │
       ▼ MarketTick(price, timestamp)
[TradingEngine.onMarketTick]
       │
       ├──► [SyntheticCandleEngine.onNewTick] ───► [BUG CRÍTICO: deltaP=0, TFI=0.0f]
       │             │
       │             ├──► Velas 60s (closedCandles)
       │             ├──► Fractales S/R (takeLast(40))
       │             └──► evaluateSniperOpportunity (:58s-:05s)
       │                         │
       │                         └──► onSignalGenerated(action, reason)
       │                                     │
       ├──► [MarketRegimeClassifier]         ▼
       ├──► [AdaptiveLearningEngine] ──► [TradingEngine.executeHeadlessTrade]
       │                                     │
       │                                     ├──► RiskManager.canExecuteTrade (Bypass YOLO)
       │                                     ├──► AutoTradeAccessibilityService.performClick
       │                                     └──► RiskManager.recordTradeSent
       │                                                 │
       ▼                                                 ▼
[Telemetría / Journal] ◄────────────────────── [checkHeadlessTradeResolution]
(trade_journal.csv)                            (Espera 60s -> diff saldo real)
```

### Hallazgos de Conectividad:
* **Desconexión entre Investigación y Producción:** El script de investigación `scripts/quant_crypto_idx_model.py` **nunca consumió datos de `trade_journal.csv` ni del WebSocket**. Operó de forma 100% aislada sobre arrays de NumPy autogenerados.
* **Desfase de Esquema en Telemetría:** El código de `TradeJournalLogger.kt` declara 21 columnas (incluyendo contexto cuantitativo: `trend, dist_support, dist_resistance, tick_velocity, impulse, market_regime, candle_second, adaptive_status`). Sin embargo, **todos los CSVs históricos en disco poseen únicamente 13 columnas**. El APK que generó el historial no tenía compilada la versión con métricas contextuales.

---

## 2. AUDITORÍA DETALLADA DE BUGS Y CÓDIGO (KOTLIN)

### 2.1 Bug Crítico: Desactivación Permanente del Indicador TFI en `SyntheticCandleEngine.kt`
* **Archivo:** `app/src/main/java/com/example/tradedraw/SyntheticCandleEngine.kt`
* **Líneas:** 147 y 167–174
* **Sospecha Inicial:** Confirmada plenamente.
* **Evidencia en Código:**
  ```kotlin
  // Línea 130-147:
  if (lastTickPrice > 0.0) {
      val delta = tick.price - lastTickPrice
      ...
  }
  lastTickPrice = tick.price  // <--- LÍNEA 147: SE ACTUALIZA lastTickPrice AL PRECIO ACTUAL

  // Línea 160-174:
  val sec = tick.candleSecond
  ...
  if (sec in 40..59 && lastTickPrice > 0.0) {
      val deltaP = tick.price - lastTickPrice  // <--- LÍNEA 168: tick.price - tick.price == 0.0 SIEMPRE
      val eps = lastTickPrice * 0.0000005
      if (deltaP > eps) windowUpTicksCount++   // NUNCA OCURRE (0.0 no es > eps)
      else if (deltaP < -eps) windowDownTicksCount++ // NUNCA OCURRE (0.0 no es < -eps)
      val totalTicks = windowUpTicksCount + windowDownTicksCount
      tickFlowImbalance = if (totalTicks >= 4) (windowUpTicksCount - windowDownTicksCount).toFloat() / totalTicks else 0.0f
  }
  ```
* **Impacto Operativo:**
  1. `deltaP` es matemáticamente idéntico a `0.0` en cada llamada.
  2. `windowUpTicksCount` y `windowDownTicksCount` permanecen en 0.
  3. `tickFlowImbalance` permanece en `0.0f` indefinidamente.
  4. En `evaluateSniperOpportunity`:
     - Setup 0A (Reversión Clímax Venta): requiere `tfi >= 0.30f` (o `0.40f`). **Nunca se disparó**.
     - Setup 0B (Reversión Clímax Compra): requiere `tfi <= -0.30f` (o `-0.40f`). **Nunca se disparó**.
     - Setup 0C (Continuación Momentum): requiere `|tfi| >= 0.40f`. **Nunca se disparó**.
  5. Las supuestas estrategias cuantitativas Rentech documentadas como el pilar del sistema estuvieron **completamente inertes en el dispositivo real**.

---

### 2.2 Bypass de Límites en Modo YOLO en `RiskManager.kt`
* **Archivo:** `app/src/main/java/com/example/tradedraw/RiskManager.kt`
* **Líneas:** 371–385 vs Línea 426
* **Sospecha Inicial:** Confirmada plenamente.
* **Evidencia en Código:**
  ```kotlin
  // Líneas 371-385:
  if (subMode == AutonomousSubMode.YOLO) {
      val remaining = getRemainingCooldown(subMode)
      if (remaining > 0) {
          return Pair(false, "Pausa de Cooldown YOLO: ${remaining}s")
      }
      ...
      // En YOLO opera continuamente sin detenerse permanentemente por stop loss de racha
      return Pair(true, "🚀 MODO YOLO: Operativa continua sin límites") // <--- RETORNO INCONDICIONAL
  }

  ...
  // Línea 426 (CÓDIGO INALCANZABLE EN MODO YOLO):
  if (takeProfitWins > 0 && currentWins >= takeProfitWins) {
      return Pair(false, "Take Profit alcanzado ($takeProfitWins victorias)")
  }
  ```
* **Impacto Operativo:**
  1. Cuando el bot opera en submodo YOLO (el modo más frecuente en telemetría: 206 de 212 trades), la función `canExecuteTrade` retorna `true` en la línea 384.
  2. La validación de `takeProfitWins` (línea 426) jamás se evalúa. El bot continúa operando indefinidamente hasta incurrir en drawdown destructivo.
  3. Inconsistencia de firma: `canExecuteTrade` recibe `subMode: AutonomousSubMode? = null` pero no resuelve `subMode ?: currentSubMode` en la raíz del método, provocando que llamadas sin parámetros ignoren la configuración interna de la instancia.

---

### 2.3 Probabilidades Falsas / Scores Heurísticos en `TradingEngine.kt`
* **Archivo:** `app/src/main/java/com/example/tradedraw/TradingEngine.kt`
* **Líneas:** 170–176, 263–287, 1143
* **Evidencia en Código:**
  ```kotlin
  // Línea 170-176:
  data class EngineReasoning(
      val patternName: String,
      val probabilityPct: Int, // <--- NOMENCLATURA FALAZ: NO ES PROBABILIDAD
      val srFilterStatus: String,
      val actionPlan: String,
      val isFavorable: Boolean
  )

  // Líneas 263-280:
  // Cálculo dinámico de probabilidades para termómetro del HUD y telemetría
  var callPower = 50
  var putPower = 50
  when (trend) {
      TrendDirection.UPTREND -> {
          callPower = 68
          putPower = 32
          if (distSup <= 0.25f) callPower += 10
          if (rsi in 40.0..68.0) callPower += 8
          if (tick.isBullishImpulse) callPower += 6
      }
      ...
  ```
* **Diagnóstico Estadístico:**
  Estos valores (e.g. $68\%$, $+10\%$, $+8\%$, $+6\%$) son **adiciones aritméticas arbitrarias** inventadas por el autor original. No provienen de regresiones logísticas, calibraciones de Platt, ni frecuencias relativas observadas. Presentar `callPower` como "probabilidad" en el HUD y en la telemetría falsea la percepción de riesgo.
* **Corrección requerida:** Renombrar de inmediato a `heuristic_score`, `signal_strength` o `confluence_score`.

---

### 2.4 Destrucción de Señales por Chasing Noise en `AdaptiveLearningEngine.kt`
* **Archivo:** `app/src/main/java/com/example/tradedraw/AdaptiveLearningEngine.kt`
* **Líneas:** 253–277 y `loops/journal/adaptive_learning_state.json`
* **Evidencia:**
  En `adaptive_learning_state.json` se registran **2,558 anti-patrones invertidos**.
  ```kotlin
  if (highestSimilarity >= 0.82f && matchingLossSig != null) {
      ...
      val inverted = if (candidateAction == TradeAction.BUY) TradeAction.SELL else TradeAction.BUY
      totalInvertedAntiPatterns++
      return AdaptiveDecision.Invert(invertedAction = inverted, ...)
  }
  ```
* **Diagnóstico:**
  En un activo como opciones binarias con payout $83\%$ y ruido microestructural dominante, una pérdida individual tiene probabilidad $\ge 50\%$ de ocurrir por simple varianza estocástica. Invertir una señal sistemáticamente tras una pérdida previa en contexto similar es la clásica **falacia del apostador**: convierte señales con edge marginal o nulo en pérdidas consecutivas amplificadas por whipsaw.

---

## 3. AUDITORÍA DEL MODELO CUANTITATIVO Y SESGOS METODOLÓGICOS (PYTHON)

### 3.1 Sesgo Circular y Tautología en `FastCryptoIdxSimulator`
* **Archivo:** `scripts/quant_crypto_idx_model.py`
* **Líneas:** 175–217
* **Evidencia Matemática:**
  ```python
  # Línea 177: Régimen de rango asignado 55% del tiempo
  block_regimes = np.random.choice(["range", "trend", "chop"], size=num_blocks, p=[0.55, 0.30, 0.15])
  ...
  # Líneas 197-198: Soportes y resistencias definidos en torno a anchor
  current_support = anchor - half_channel
  current_resistance = anchor + half_channel
  ...
  # Líneas 202-205: DERIVA DETERMINISTA FORZADA
  if regime == "range":
      # Mean reversion toward anchor when approaching S/R boundaries
      drift = -0.018 * (prev_p - anchor) * (dt / 60.0)
  elif regime == "trend":
      drift = trend_drift
  ```
* **Análisis Crítico:**
  1. Cuando el precio se acerca a `current_resistance`, $prev_p > anchor$, por lo que $drift$ es **negativo**, empujando artificialmente el precio hacia abajo.
  2. Cuando el precio se acerca a `current_support`, $prev_p < anchor$, por lo que $drift$ es **positivo**, empujando artificialmente el precio hacia arriba.
  3. El evaluador de señales de `quant_crypto_idx_model.py` vende en resistencia y compra en soporte contra este mismo dataset sintético.
  4. **El supuesto 91.67% de Win Rate es un artefacto directo de la ecuación de difusión generadora**. La estrategia simplemente midió la constante `-0.018` que el programador escribió en el generador.

---

### 3.2 Look-Ahead / Oracle Bias en Niveles S/R
* **Archivo:** `scripts/quant_crypto_idx_model.py`
* **Líneas:** 213–216, 263–274
* **Evidencia:**
  En el simulador sintético:
  ```python
  dynamic_supports[c_idx] = current_support
  dynamic_resistances[c_idx] = current_resistance
  ```
  El script no calcula fractales históricos ni bandas ATR móviles sobre las velas pasadas como hace `SyntheticCandleEngine.kt`. Utiliza los valores teóricos predeterminados por el generador de números aleatorios para todo el bloque.

---

### 3.3 Colapso de Tamaño de Muestra y Pseudo Walk-Forward
* **Archivo:** `scripts/quant_crypto_idx_model.py` y `loops/journal/quant_research_summary.json`
* **Evidencia de Folds OOS:**
  | Fold | N (OOS) | Victorias | Derrotas | Win Rate Reportado | Calificación Metodológica |
  | :---: | :---: | :---: | :---: | :---: | :---: |
  | **Fold 1** | 3 | 3 | 0 | 100.0% | **TOTALMENTE INVÁLIDO ($N=3$)** |
  | **Fold 2** | 2 | 2 | 0 | 100.0% | **TOTALMENTE INVÁLIDO ($N=2$)** |
  | **Fold 3** | 6 | 6 | 0 | 100.0% | **TOTALMENTE INVÁLIDO ($N=6$)** |
  | **Fold 4** | 9 | 4 | 5 | 44.4% | **INVÁLIDO ($N=9$)** |
  | **Fold 5** | 7 | 4 | 3 | 57.1% | **INVÁLIDO ($N=7$)** |
  | **Total** | **27** | **19** | **8** | **Promedio Reportado: 80.32%** | **SIN SIGNIFICANCIA ESTADÍSTICA** |

* **Errores Metodológicos Graves:**
  1. **N insignificante:** En Fold 2, una sola operación fallida habría cambiado el Win Rate de 100% a 50%. Ninguna conclusión científica puede derivarse de muestras de 2 a 9 casos.
  2. **Promedio de Porcentajes:** El script calculó el Win Rate promedio OOS haciendo `mean([1.0, 1.0, 1.0, 0.444, 0.571]) = 80.32%`. Si se calcula el Win Rate ponderado real sobre los 27 trades: $19 / 27 = 70.37\%$.
  3. **Sharpe Anualizado Absurdo (157.07):** El cálculo de Sharpe anualizado en la línea 87 asume $\sqrt{15000}$ trades independientes al año con $N=12$ trades totales. Representa una extrapolación matemática sin sustento empírico.

---

## 4. AUDITORÍA DE DATOS EMPÍRICOS (CSV JOURNALS)

Se auditaron sistemáticamente `trade_journal_device_all.csv` (212 filas) y `trade_journal_live.csv` (17 filas) utilizando scripts dedicados.

### 4.1 Resumen Estadístico Real

| Métrica | Historial Dispositivo Completo | Baseline Sesión Live |
| :--- | :---: | :---: |
| **Total de Registros** | 212 | 17 |
| **Trades Resueltos (WIN + LOSS)** | 193 | 12 |
| **Victorias (WIN)** | 81 | 6 |
| **Derrotas (LOSS)** | 112 | 6 |
| **TIE / VOID (Sin movimiento)** | 19 (9.0%) | 5 (29.4%) |
| **Win Rate Observado (Resueltos)** | **42.0%** | **50.0%** |
| **Intervalo Wilson 95%** | **[35.2%, 49.0%]** | [25.4%, 74.6%] |
| **Umbral Break-Even (Payout ~81%)**| **55.3%** | 55.3% |
| **Brecha contra Break-Even** | **−13.3 pp (Inferior a −20.0 pp)** | −5.3 pp |
| **Veredicto Estadístico** | **PÉRDIDA SISTEMÁTICA CONFIRMADA** | **MUESTRA NO SIGNIFICATIVA** |

### 4.2 Anomalías y Registros Corruptos Detectados en el Journal

1. **Glitches de Saldo $1.00 COP (5 filas):**
   * `2026-09-10T17:50:00`: base = `1.00`, settled = `44,907,811.84` (Delta falso de $+44.9M$ COP).
   * `2026-09-10T22:30:02`: base = `44,463,810.56`, settled = `1.00` (Delta falso de $-44.5M$ COP).
   * `2026-09-12T12:11:03`: base = `1.00`, settled = `1.00`.
   * `2026-09-12T12:14:03`: base = `1.00`, settled = `43,246,812.16`.
   * `2026-09-12T12:18:02`: base = `1.00`, settled = `43,329,812.48`.
   * **Causa raíz:** `findBalanceInNode` en `AutoTradeAccessibilityService.kt` leyó nodos de UI que contenían "1.00" (e.g. multiplicador de payout o duración) antes de que cargara el balance real.
2. **Pérdidas declaradas sin cambio de saldo (2 filas):**
   * Filas 0 y 1: `base_balance = 44,961,812.48`, `settled_balance = 44,961,812.48`, razón: `"SIN ACREDITACIÓN TRAS 62s (Diff=0.0 -> Pérdida)"`.
   * Si el saldo no varió, el trade fue un TIE/VOID o una orden fantasma no aceptada por el broker. Clasificarlo como LOSS falsea la racha inicial.
3. **Ausencia de Columnas de Contexto Cuantitativo:**
   * Las 8 columnas necesarias para auditar la relación entre TFI, S/R, ATR y resultado no existen en el archivo físico.

---

## 5. AUDITORÍA DE EJECUCIÓN: DISCREPANCIA MODELO VS REALIDAD

| Factor de Ejecución | Simulación Cuantitativa (`quant_crypto_idx_model.py`) | Realidad Observada en Producción (`TradeJournal`) |
| :--- | :--- | :--- |
| **Precio de Entrada** | Tick exacto `:59.99` ($c_{end}-1$) | Precio tras latencia de accesibilidad + red (~300–800ms) |
| **Precio de Liquidación** | Tick exacto `:59.99` de vela siguiente | Tick del broker al expirar (54s a 72s después) |
| **Duración del Trade** | Estrictamente 60 segundos | Variable: min 54s, max 72s (promedio 60.1s) |
| **Slippage y Rechazos** | 0% slippage, 100% fill rate | Órdenes bloqueadas o con slippage en OTC |
| **Manejo de Empates (TIE)**| Delta == 0 cuenta neutro | Broker reembolsa stake, pero el bot a veces registra LOSS |

---

## 6. MATRIZ DE PROBLEMAS: CONFIRMADOS VS SOSPECHAS DESCARTADAS

| Hipótesis / Sospecha | Estado | Evidencia y Justificación Técnica |
| :--- | :---: | :--- |
| **1. Bug de TFI / deltaP en `SyntheticCandleEngine.kt`** | **CONFIRMADO** | Línea 147 actualiza `lastTickPrice` antes de línea 168. $\Delta P \equiv 0$, TFI nunca subió de 0.0f. |
| **2. Sesgo de datos sintéticos en `quant_crypto_idx_model.py`** | **CONFIRMADO** | Ecuación de difusión con mean-reversion forzada hacia anchor en rango y drift constante en tendencia. |
| **3. Sample-size inválido en OOS** | **CONFIRMADO** | Folds con $N=2, 3, 6, 7, 9$. Total OOS $N=27$. Error de agregación no ponderada. |
| **4. Bypass de `takeProfitWins` en `RiskManager.kt`** | **CONFIRMADO** | Retorno temprano incondicional en línea 384 en modo YOLO; línea 426 es inalcanzable. |
| **5. Registros corruptos en Journal ($1.00 COP)** | **CONFIRMADO** | 5 filas con base=1.0 o settled=1.0 por lectura errónea de accesibilidad en pantalla. |
| **6. Discrepancia de duración real** | **CONFIRMADO** | Duraciones reales oscilan entre 54 y 72 segundos en telemetría auditada. |
| **7. Look-Ahead por uso de velas futuras en S/R (`SyntheticCandleEngine`)** | **DESCARTADO** | El motor Kotlin usa `closedCandles.takeLast(40)`, que son velas estrictamente cerradas en el pasado. No hay fuga de velas futuras en Kotlin (el desfase en Kotlin es de retraso temporal al evaluar `:58` con vela $T-1$, no de anticipación del futuro). |
| **8. Supuesto Edge de 91.6% en Reversión** | **DESCARTADO (FALSO)** | Producido por circularidad sintética y $N=12$. En datos reales del dispositivo no existe ningún subconjunto con edge estadístico defendible (sobreviven 0 de 281 segmentos tras corrección Benjamini-Hochberg). |

---

## 7. PLAN DE ACCIÓN PARA LAS SIGUIENTES FASES

Siguiendo el protocolo maestro:

1. **FASE 2 — TESTS:**
   * Crear suite de tests unitarios que demuestren de forma determinista y reproducible:
     - Test que falle al calcular TFI con la secuencia actual de actualización de `lastTickPrice`.
     - Test que demuestre el bypass de `takeProfitWins` en `RiskManager` en modo YOLO.
     - Test que verifique el parsing y saneamiento de registros corruptos en el Journal.
2. **FASE 3 — CORRECCIONES QUIRÚRGICAS:**
   * Corregir el orden de cálculo en `SyntheticCandleEngine.kt` para que `deltaP` se calcule con el precio previo antes de actualizar `lastTickPrice`.
   * Reestructurar `canExecuteTrade` en `RiskManager.kt` para que los límites de sesión (`takeProfitWins`, `stopLossStreak`, etc.) se respeten coherentemente.
   * Renombrar métricas heurísticas en `TradingEngine.kt` (`probabilityPct` $\to$ `confluenceScore`).
3. **FASE 4 — SIMULATION VALIDATION ENGINE:**
   * Implementar `SimulationValidationEngine` en Python con arquitectura desacoplada:
     - `DataIntegrityValidator`
     - `TemporalIntegrityValidator`
     - `NoLookAheadValidator`
     - `ExecutionModel` (con latencia y slippage parametrizables)
     - `StatisticalValidator` (Wilson CI, Benjamini-Hochberg, EV, flags `INSUFFICIENT_SAMPLE`)
4. **FASE 5, 6 y 7 — RE-EVALUACIÓN, HOLDOUT E INFORME FINAL:**
   * Ejecutar la validación sobre datos limpios e independientes.
   * Emitir `VALIDATION_REPORT.md` documentando la comparativa ANTES vs DESPUÉS.
