# Tasks: Aumento de Confiabilidad de TradeDraw para Operativa Real

**Input**: Design documents from `/specs/001-trade-reliability/`
**Prerequisites**: `spec.md`, `plan.md`, `constitution.md`

## Organization & Ruflo Swarm Alignment

Los grupos de tareas han sido orquestados utilizando los roles de los enjambres de **Ruflo** (`codex`, `hermes`, `opencode`, `dsh`):

---

## Phase 1: Capital Safety & Terminal Risk Handbrake (Runtime: Codex)

- [x] **T001** [US1] Implementar parada dura terminal por racha de pérdidas en `app/src/main/java/com/example/tradedraw/RiskManager.kt`.
  - Evaluar `stopLossStreak` antes que cualquier lógica de martingala o cooldown temporal.
  - Asegurar que el estado terminal requiera reset explícito de sesión (`resetSession()`).
- [x] **T002** [US1] Añadir suelo de equity absoluto (`absoluteEquityFloor`) y trailing profit lock en `RiskManager.kt`.
  - Persistir `absoluteEquityFloor` en `SharedPreferences` con valor por defecto de 40,000,000 COP en Demo.
  - Si `currentBalance < absoluteEquityFloor`, denegar toda orden permanentemente con `Log.e`.
- [x] **T003** [US1] Soporte para resultado de trade `VOID / NO_EJECUTADO` en `RiskManager.kt`.
  - Cuando el balance no cambie tras 62s (Diff = 0.0), clasificar como `VOID`.
  - `VOID` no incrementa racha de pérdidas ni escala nivel de martingala.
  - Si ocurren >= 3 `VOID` consecutivos, entrar en parada dura por fallo de enlace con el broker.
- [x] **T004** [US1] Escribir suite exhaustiva de pruebas unitarias en `app/src/test/java/com/example/tradedraw/RiskManagerTest.kt`.
  - Test 1: Stop-loss terminal por racha bloquea trades permanentemente.
  - Test 2: Equity por debajo del suelo absoluto bloquea trades permanentemente.
  - Test 3: Un trade VOID entre pérdidas no incrementa la racha ni martingala.
  - Test 4: 3 trades VOID consecutivos disparan parada dura.

---

## Phase 2: Quantitative Geometry & Fractal S/R (Runtime: Hermes)

- [x] **T005** [US3] Reemplazar cálculo estático de caja de S/R en `app/src/main/java/com/example/tradedraw/SyntheticCandleEngine.kt`.
  - Implementar detector de fractales/pivotes (altos y bajos locales sobre ventana de 5 a 10 velas).
  - Agrupar pivotes cercanos por densidad de toques y calcular relevancia cuantitativa (>= 2 toques).
- [x] **T006** [US3] Independizar `distanceToSupportRatio` y `distanceToResistanceRatio` en `SyntheticCandleEngine.kt`.
  - Normalizar distancias por ATR(14) o rango medio verdadero en lugar de `srRange` fijo.
  - Permitir que el precio cotice por debajo de soporte roto o por encima de resistencia rota.
- [x] **T007** [US2] Optimizar y reforzar el filtro Choppiness Index (CHOP) en `SyntheticCandleEngine.kt`.
  - Mantener `cachedChoppiness` invalidado por vela para rendimiento óptimo.
  - Veto obligatorio de continuación cuando CHOP >= 61.8% o micro-rango < 0.05%.
- [x] **T008** [US3] Escribir suite de pruebas para fractales y distancias independientes en `app/src/test/java/com/example/tradedraw/SyntheticCandleEngineTest.kt`.
  - Test de independencia de distancias (ambas > 0.60 y ambas < 0.30).
  - Test de detección de fractales con niveles conocidos.
  - Test de ruptura de soporte donde el precio cotiza por debajo del nivel.

---

## Phase 3: Anti-Suicide Vetos & Execution Timing (Runtime: Hermes / Codex)

- [x] **T009** [US2] Implementar Veto de ATH/ATL y proximidad a S/R en `app/src/main/java/com/example/tradedraw/TradingEngine.kt`.
  - Veto CALL: Prohibido comprar si el precio está a menos de 15% de la resistencia o en ATH.
  - Veto PUT: Prohibido vender si el precio está a menos de 15% del soporte o en ATL.
- [x] **T010** [US2] Restringir ventana operativa sniper a :58s - :02s en `TradingEngine.kt`.
  - Alinear apertura de orden exactamente con el cambio de vela del broker móvil.
- [x] **T011** [US2] Escribir tests para vetos de sobreextensión y ventana sniper en `app/src/test/java/com/example/tradedraw/TradingEngineTest.kt`.

---

## Phase 4: Overlay Telemetry & Latency Instrumentation (Runtime: OpenCode)

- [x] **T012** [US4] Integrar feedback visual en el HUD de `OverlayService.kt`.
  - Badges informativos: `[PARADA SL]`, `[SUELO EQUITY]`, `[FILTRO CHOP]`, `[VETO S/R]`.
  - Mantener control atómico `hudUpdatePending` para garantizar 60 fps en el movimiento del HUD.
- [x] **T013** [US4] Instrumentar telemetría de latencia de disparo en `AutoTradeAccessibilityService.kt`.
  - Medir tiempo transcurrido entre generación de señal en `TradingEngine` y despacho de `dispatchGesture`.

---

## Phase 5: Replay Backtesting & Statistical Validation (Runtime: DeepSeek Harness)

- [x] **T014** [US5] Implementar replay histórico con datos reales en `loops/run_backtest.py`.
  - Cargar los 35 trades reales de `loops/journal/live/trade_journal_live.csv`.
  - Aislar la fila corrupta del timestamp 1789080600323 y clasificar los 5 trades con diff 0.0 como VOID.
  - Calcular esperanza matemática por trade (EV), Win Rate real y Drawdown máximo.
- [x] **T015** [US5] Publicar tabla de potencia estadística en `loops/scoreboard.md`.
  - Determinar número de operaciones necesarias para validar estadísticamente WR > 54.9% al 95% de confianza.

---

## Phase 6: QA, Build & Device Deployment

- [x] **T016** Ejecutar suite completa de tests unitarios: `./gradlew testDebugUnitTest --no-daemon --no-configuration-cache`.
- [x] **T017** Compilar APK de depuración: `./gradlew assembleDebug --no-daemon --no-configuration-cache`.
- [x] **T018** Instalar APK en el POCO X6 Pro vía ADB (`192.168.1.185:5555`).
- [x] **T019** Commit y push a la rama `main` en GitHub.
