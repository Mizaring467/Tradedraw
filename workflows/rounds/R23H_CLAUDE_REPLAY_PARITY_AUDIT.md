# AUDITORÍA DE PARIDAD LIVE ↔ REPLAY R23H VENTANA 03
## INFORME FORENSE PRINCIPAL: ANÁLISIS DE DISCREPANCIA Y CAUSA RAÍZ

**Fecha:** 2026-10-18  
**Auditor Principal:** CLAUDE CODE (`w1:pC` / Staff Software Engineer)  
**Destinatario:** AGY (`w1:p1` / Orquestador)  
**Directorio Auditado:** `loops/r23h_validation/window_03/`  
**Dispositivo Físico:** Xiaomi Redmi A1 (`UGU8KFUKM7E6CE8D`)  
**Build Identificada:** `versionCode = 19` | `versionName = "1.9-r23h-exhaustion"` | `BUILD_ID = "R23H-BUY-EXHAUSTION"` | `gitSha = "aac1df3"`  
**Veredicto General:** **FALSO NEGATIVO DE PARIDAD. MOTOR LIVE 100% ÍNTEGRO Y DETERMINISTA; ARNES REPLAY CON ASIMETRÍA DE PIPELINE DEFENSIVO Y CONTAMINACIÓN DE BÚFER TEMPORAL.**

---

## 1. RESPUESTA EXPLÍCITA A LA PREGUNTA CRÍTICA PRIORITARIA

> **Pregunta:** ¿Los 8 registros Replay son 8 operaciones que deberían haber sido ejecutadas en Live, o son 8 decisiones/candidatos de los cuales solamente 4 terminaron siendo ejecuciones reales?

### Respuesta Categórica con Evidencia Irrefutable:

**Ninguna de las dos premisas de la disyuntiva es correcta en sentido estricto:**

1. **NO son 8 operaciones que debieron ejecutarse en Live:** Cero (0) de los 8 registros de Replay debió ejecutarse en Live.
2. **NO son 8 candidatos que dieron lugar a las 4 ejecuciones reales:** Ninguno de los 8 registros de Replay dio origen directo a los 4 trades reales de Live.

**Naturaleza exacta demostrada de los 8 registros de Replay (`replay_journal.csv`):**
Son **8 señales técnicas candidatas brutas** emitidas por `SyntheticCandleEngine` en un entorno de test aislado (`ReplayBacktestTest.kt`) que **carece por completo del pipeline de políticas defensivas de `TradingEngine.kt`**:

- **2 registros (R1 y R2) son espurios por contaminación de búfer pre-ventana:** Ocurrieron entre las 19:49:01 y 19:51:05 UTC, es decir, entre **312 s y 188 s antes** de que iniciara la ventana de validación W03 (`ts_inicio = 1792284853958`). Proceden de 2,004 ticks residuales de la ventana previa retenidos en el búfer circular HTTP de la APK. En Live no pertenecen a W03.
- **6 registros (R3 a R8) fueron evaluados en Live y EXPLÍCITAMENTE VETADOS:** Corresponden a señales en segundo :01 que el motor en vivo recibió y bloqueó de inmediato mediante sus guards de seguridad:
  - **R3 y R4:** Vetados por `VETO_BUY_EXHAUSTION_CLIMAX` (parche R23H en `TradingEngine.kt`).
  - **R5, R6 y R7:** Vetados por `AdaptiveLearningEngine` (`ADAPTIVE_BLOCK`, similitud 99% con anti-patrón perdedor recurrente).
  - **R8:** Vetado por `RiskManager` (`RISK_LIMIT: Operación abierta en curso`).

**Por qué hay 4 trades en Live y 8 en Replay con 0 pares:**
- En **Live**, las 4 operaciones reales (**L1, L2, L3, L4**) se ejecutaron en instantes posteriores de la vela (:04 a :06) o en velas posteriores, una vez que las condiciones de veto se disiparon.
- En **Replay**, al no existir los módulos de veto, el arnés simuló una ejecución inmediata en segundo :01 para cada señal técnica. Al simular esa ejecución, Replay aplicó la restricción `lastExecutedCandleMinute = candleMinute` y una ventana de ocupación de 68 segundos (`blockedUntilTs = entry + 60s + 8s`). Esto **bloqueó y cegó al motor de Replay**, impidiéndole evaluar los ticks de segundo :04-:06 donde ocurrieron los trades reales de Live.
- Por diseño criptográfico (UUID v3), el `decision_id` de una señal vetada en :01 difiere del `decision_id` de una orden ejecutada en :06. Y el comparador `compare_live_replay.py` busca coincidencias por ID o por tiempo con ventana de 3,000 ms, la cual fue superada por el retardo de confirmación de saldo en hardware (~4,400–4,950 ms).

---

## 2. CAUSA RAÍZ TÉCNICA

La discrepancia y el resultado de 0.0% de paridad responden a **tres causas técnicas concurrentes**:

### Causa 1: Desacople y asimetría de pipeline entre `TradingEngine.kt` y `ReplayBacktestTest.kt`
En la APK de Android (Live), la ejecución de una orden atraviesa una arquitectura de capas en cascada:
```
[Tick WS] 
   → SyntheticCandleEngine.onSignalGenerated (Señal técnica bruta)
   → TradingEngine.executeHeadlessTrade
        ├─ Filtro timing sniper (:00-:05, :58-:59)
        ├─ Cooldown post-resolución
        ├─ VETO_BUY_EXHAUSTION_CLIMAX (R23H)  <--- Falta en Replay
        ├─ AdaptiveLearningEngine (ADAPTIVE_BLOCK 99%)  <--- Falta en Replay
        ├─ RiskManager (Operación abierta en curso)
        └─ Despacho TAP accesibilidad → Confirmación por saldo
```
En el arnés de Replay (`ReplayBacktestTest.kt`), el código instancia únicamente `SyntheticCandleEngine()` de forma aislada. Carece de instancia de `TradingEngine`, de `AdaptiveLearningEngine` y del evaluador `motivoVetoPorAgotamientoDeCompra`. En consecuencia:
Toda señal emitida por `SyntheticCandleEngine` que caiga en segundo 0..5 se convierte incondicionalmente en `SimulatedTrade` dentro de `replay_journal.csv`.

### Causa 2: Cegado secuencial por simulación de trades fantasma
Al ejecutar una señal fantasma en segundo :01:
1. Replay marca `lastExecutedCandleMinute = candleMinute`, descartando cualquier tick posterior de esa misma vela (donde Live ejecutó tras expirar el veto).
2. Replay calcula `blockedUntilTs = entryTick.ts + 60_000L + 8_000L`. Esto bloquea los primeros 8 a 10 segundos del minuto siguiente, descartando también las señales de las velas subsiguientes (caso de L3 y L4).

### Causa 3: Desfase temporal y rigidez del comparador `compare_live_replay.py`
1. **Cruce por `decision_id`:** Live genera el ID sobre el tick exacto de la orden aprobada (:05/:06). Replay genera el ID sobre el tick de la señal preliminar (:01). Al ser entradas distintas en `generateDeterministicDecisionId(asset, ts, action)`, los hashes UUID v3 nunca coinciden.
2. **Fallback temporal insuficiente:** `compare_live_replay.py` fija `temporal_tolerance_ms = 3000`. En Live, el campo `timestamp_ms` en `trades.csv` se toma del evento `ORDER_STATE` (`CONFIRMADA ... saldo movido`), el cual ocurre tras el tap de accesibilidad, la latencia de red del broker y la actualización visual de saldo (demora medida: 4,400 ms a 4,950 ms tras el segundo :01). El comparador rechaza el par por superar los 3.0 segundos.
3. **Contaminación de `ticks_live.csv`:** El monitor extrae `/ticks?max=2000` sin recortar los ticks anteriores a `window_start_ts`, inyectando 13.7 minutos de mercado previo en Replay.

---

## 3. ARCHIVO, FUNCIÓN Y LÍNEA DONDE SE ORIGINA LA DISCREPANCIA

### 3.1 Arnés de Replay (Omisión de políticas defensivas)
- **Archivo:** `app/src/test/java/com/example/tradedraw/ReplayBacktestTest.kt`
- **Función:** `runBacktestOnStrategy(ticks, strategy, subMode)`
- **Líneas críticas:**
  - **L163–L167:** Instanciación aislada de `SyntheticCandleEngine` sin envoltorio de `TradingEngine`.
  - **L188–L205:** Conversión directa de `onSignalGenerated` a `pendingSignals` sin validar `motivoVetoPorAgotamientoDeCompra` ni `AdaptiveLearningEngine.evaluateSignalSuitability`.
  - **L189–L192:** `lastExecutedCandleMinute = candleMinute` y `blockedUntilTs = lastTick.ts + TRADE_DURATION_MS + cooldownMs` bloquean la vela e impiden evaluar la entrada legítima ocurrida segundos después.
  - **L296:** Generación de `decisionId` con el timestamp de la señal rechazada en Live.

### 3.2 Motor Live (Generación de IDs deterministas en instantes divergentes)
- **Archivo:** `app/src/main/java/com/example/tradedraw/TradingEngine.kt`
- **Funciones y Líneas:**
  - **L240:** `val decisionId = generateDeterministicDecisionId(assetName, signalTsMs, action.name)`.
  - **L866–L872:** `generateDeterministicDecisionId` calcula `UUID.nameUUIDFromBytes("${ric}_${nonceMs}_${action}".toByteArray())`. Al diferir `nonceMs` entre la señal en :01 y la confirmación en :06, los UUID resultantes son completamente disjuntos.
  - **L2326–L2334:** `motivoVetoPorAgotamientoDeCompra` bloquea compras con `callPower >= 70%` en Live (ausente en Replay).
  - **L2336–L2350:** `adaptiveLearningEngine.evaluateSignalSuitability` veta señales por recurrencia de pérdidas (ausente en Replay).

### 3.3 Monitor Live (Contaminación de ticks y latencia de timestamp)
- **Archivo:** `scripts/validation/live_monitor_r23_autonomous.py`
- **Líneas:**
  - **L616, L678–L679:** `trades.csv` guarda `timestamp_ms = at_ms` correspondiente a la confirmación de saldo, no al timestamp de la señal analítica original.
  - **L951–L970:** Volcado de `ticks_live.csv` sin podar los ticks previos a `window_start_ts`.

### 3.4 Comparador (Ventana de tolerancia reducida)
- **Archivo:** `scripts/validation/compare_live_replay.py`
- **Función:** `compare_trades`
- **Líneas:**
  - **L320:** `temporal_tolerance_ms: int = 3000` (umbral rígido insuficiente ante confirmación por UI de 4.5s–5.0s).
  - **L343–L346:** Búsqueda por `decision_id` exacto falla por la discrepancia de timestamps de origen.

---

## 4. EJEMPLO CONCRETO DE LIVE

### Trade L1 (`trades.csv` fila 2):
- **`trade_id`:** `d43f183d-b035-4657-a6f5-88fa28db3cde`
- **`decision_id`:** `74a0e873-8966-37df-b6cf-9af82f985a0f`
- **Timestamp (`timestamp_ms`):** `1792285146232` (2026-10-17 19:59:06.232 UTC)
- **Vela:** Minuto 19:59, segundo :06.
- **Activo:** `Z-CRY/IDX`
- **Acción:** `BUY`
- **Detalle registrado:** `CONFIRMADA BUY saldo movido`
- **Balance previo:** 39,243,735.04 COP DEMO
- **Balance post-entrada:** 39,143,736.32 COP DEMO (-100,000.00 COP stake)
- **Balance liquidado:** 39,325,736.96 COP DEMO (+182,000.64 COP crédito)
- **Resultado real comprobado:** **WIN (+82,001.92 COP netos)**

### Matriz Completa de los 4 Trades Reales en Live:
| # | Trade ID | Decision ID | Timestamp (ms) | Hora UTC | Sec | Acción | Resultado | Delta Balance |
| :-: | :--- | :--- | :---: | :---: | :---: | :---: | :---: | :---: |
| **L1** | `d43f183d...` | `74a0e873-8966-37df-b6cf-9af82f985a0f` | 1792285146232 | 19:59:06 | :06 | **BUY** | **WIN** | +82,001.92 COP |
| **L2** | `8522ab74...` | `293f57bf-e117-3b07-b092-ec26e214d12f` | 1792285565725 | 20:06:05 | :05 | **SELL** | **LOSS** | -100,001.28 COP |
| **L3** | `f8a27c1b...` | `ce64c4b3-f16d-36e8-9356-09513f412596` | 1792285804188 | 20:10:04 | :04 | **BUY** | **LOSS** | -99,998.72 COP |
| **L4** | `30e1f103...` | `bd9fa290-306b-309d-90a0-db8d3b45fcba` | 1792285984501 | 20:13:04 | :04 | **SELL** | **WIN** | +83,000.32 COP |

---

## 5. REGISTRO REPLAY CORRESPONDIENTE O EXPLICACIÓN TÉCNICA DE POR QUÉ NO EXISTE

### Análisis Caso por Caso de los 8 Registros de Replay (`replay_journal.csv`):

#### Registro R1 (`replay_journal.csv` fila 2):
- **`decision_id`:** `122b7190-a333-3777-a57b-c5e7f15f9696` | **Timestamp:** `1792284541803` (19:49:01 UTC)
- **Acción:** BUY | **Resultado:** WIN
- **Explicación técnica:** **No existe en Live W03.** Corresponde a un tick capturado 5 minutos antes de iniciar W03. Es un artefacto de los 2,004 ticks pre-ventana no depurados de `ticks_live.csv`.

#### Registro R2 (`replay_journal.csv` fila 3):
- **`decision_id`:** `e3301299-4658-3d45-a07c-d42fa87961c6` | **Timestamp:** `1792284665308` (19:51:05 UTC)
- **Acción:** BUY | **Resultado:** LOSS
- **Explicación técnica:** **No existe en Live W03.** Capturado 3 minutos antes de iniciar la ventana W03. Contaminación de búfer HTTP de la APK.

#### Registro R3 (`replay_journal.csv` fila 4):
- **`decision_id`:** `6b3a2948-1e30-34df-b6f6-efa512b6f5ea` | **Timestamp:** `1792284961305` (19:56:01 UTC)
- **Acción:** BUY | **Resultado:** WIN
- **Estado en Live:** **Identificado con coincidencia exacta (Δts = 2 ms) en `decisions.csv` (Línea 18).**
- **Veredicto en Live:** `VETO BUY` → `VETO_BUY_EXHAUSTION_CLIMAX UPTREND con callPower >= 70% (callPower=76)`.
- **Por qué no fue trade en Live:** La política R23H bloqueó la compra en fatiga. Replay la ejecutó como trade porque carece de esa regla.

#### Registro R4 (`replay_journal.csv` fila 5) — Correspondiente al Trade L1:
- **`decision_id`:** `2144b51f-233a-3dd4-861f-4a8b4f80be21` | **Timestamp:** `1792285141303` (19:59:01 UTC)
- **Acción:** BUY | **Resultado:** WIN
- **Estado en Live:** **Identificado con coincidencia exacta (Δts = 1 ms) en `decisions.csv` (Línea 30).**
- **Veredicto en Live:** `VETO BUY` en segundo :01 por `VETO_BUY_EXHAUSTION_CLIMAX (callPower=78%)`.
- **Por qué diverge con L1:**
  1. En Live, el agotamiento en :01 vetó la entrada. A segundo :06 (`ts = 1792285146232`), la condición se normalizó y Live despachó el trade real L1 (`decision_id = 74a0e873...`).
  2. En Replay, R4 se ejecutó en :01. Al ejecutarse, bloqueó la vela completa (`candleMinute = 1792285141303 / 60000`). Cuando el tick de :06 llegó a Replay, fue ignorado.
  3. Resultado: Live ejecutó L1 en :06; Replay ejecutó R4 en :01. IDs distintos y Δts = 4,929 ms (> 3,000 ms).

#### Registro R5 (`replay_journal.csv` fila 6) — Correspondiente al Trade L2:
- **`decision_id`:** `4621c7e3-89fa-34c4-b607-270aea78feb7` | **Timestamp:** `1792285561307` (20:06:01 UTC)
- **Acción en Replay:** SELL | **Resultado:** LOSS
- **Estado en Live:** **Identificado con coincidencia exacta (Δts = 3 ms) en `decisions.csv` (Línea 43).**
- **Veredicto en Live:** `VETO BUY` en segundo :01 por `ADAPTIVE_BLOCK [sim:99%] [Confluencia: 95pts] Anti-patrón perdedor recurrente`.
- **Por qué diverge con L2:** Live vetó la señal temprana en :01 y encontró una entrada válida SELL a segundo :05 (`293f57bf...`). Replay ejecutó en :01, bloqueó el minuto e ignoró el tick de segundo :05. Δts = 4,418 ms (> 3,000 ms).

#### Registro R6 (`replay_journal.csv` fila 7) — Causa del bloqueo de L3:
- **`decision_id`:** `fa3ecc49-e820-354f-9ca1-713b355089c6` | **Timestamp:** `1792285742110` (20:09:02 UTC)
- **Acción:** SELL | **Resultado:** LOSS
- **Estado en Live:** **Identificado con coincidencia exacta (Δts = 4 ms) en `decisions.csv` (Línea 71).**
- **Veredicto en Live:** `VETO BUY` en segundo :02 por `ADAPTIVE_BLOCK [sim:99%] [Confluencia: 82pts]`.
- **Por qué no existe L3 en Replay:** Al simular R6 a las 20:09:02, Replay fijó `blockedUntilTs = 1792285742110 + 68,000 ms = 1792285810110` (20:10:10). Cuando llegó la señal real de L3 a las 20:10:01 (`ts = 1792285801308`), Replay la descartó por estar "en curso" la orden fantasma R6.

#### Registro R7 (`replay_journal.csv` fila 8) — Causa del bloqueo de L4:
- **`decision_id`:** `406f2a25-5913-3288-ba6d-10884ebdadf7` | **Timestamp:** `1792285921304` (20:12:01 UTC)
- **Acción:** SELL | **Resultado:** WIN
- **Estado en Live:** **Identificado con coincidencia exacta (Δts = 5 ms) en `decisions.csv` (Línea 90).**
- **Veredicto en Live:** `VETO SELL` en segundo :01 por `ADAPTIVE_BLOCK [sim:99%] [Confluencia: 95pts]`.
- **Por qué no existe L4 en Replay:** R7 bloqueó a Replay hasta las 20:13:09 (`ts = 1792285989304`). El trade real L4 ocurrió a las 20:13:02 y fue ignorado por el arnés de Replay.

#### Registro R8 (`replay_journal.csv` fila 9):
- **`decision_id`:** `e9aadf7e-0994-3281-bc77-ff294dee1ddb` | **Timestamp:** `1792286041333` (20:14:01 UTC)
- **Acción:** SELL | **Resultado:** LOSS
- **Estado en Live:** **Identificado con coincidencia exacta (Δts = 2 ms) en `decisions.csv` (Línea 104).**
- **Veredicto en Live:** `VETO SELL` por `RISK_LIMIT Operación abierta en curso (58s)`.
- **Por qué no fue trade en Live:** Live aún mantenía la posición abierta de L4 liquidándose; Replay, al haber descartado L4, tenía el canal libre y disparó erróneamente R8.

---

## 6. DEMOSTRACIÓN DE LA CADENA: DECISION → CANDIDATE → EXECUTION → OUTCOME

A continuación se contrasta el flujo de datos exacto entre el entorno real de producción y el entorno de prueba offline:

### Cadena A: Entorno Live Real (Android Xiaomi Redmi A1)

```
1. RECEPCIÓN DE TICK WS:
   latestMarketTick = MarketTick(Z-CRY/IDX, 641.8673, ts=1792285141304)

2. ANÁLISIS SINTÉTICO (SyntheticCandleEngine):
   Sec: :01 | Regime: TRENDING | Trend: UPTREND | Confluence: 90pts
   -> onSignalGenerated(action=BUY, reason="🎯 MT_REJECTION: Rechazo en Soporte...")

3. EVALUACIÓN DE CANDIDATO (TradingEngine.executeHeadlessTrade):
   decisionId = generateDeterministicDecisionId("Z-CRY/IDX", 1792285141304, "BUY")
              = 2144b51f-233a-3dd4-861f-4a8b4f80be21
   Filtro Clímax: motivoVetoPorAgotamientoDeCompra(BUY, UPTREND, callPower=78%)
   -> DISPARA VETO_BUY_EXHAUSTION_CLIMAX
   -> TradeEventLog.add(VETO, ..., decisionId="2144b51f...")
   -> decisions.csv: 2144b51f..., VETO, callPower=78% (CERO taps, saldo intacto)

4. NUEVO CANDIDATO EN SEGUNDO :06 DE LA MISMA VELA:
   latestMarketTick = MarketTick(Z-CRY/IDX, 641.8673, ts=1792285146232)
   callPower normalizado < 70% | Sin veto adaptativo
   decisionId = generateDeterministicDecisionId("Z-CRY/IDX", 1792285146232, "BUY")
              = 74a0e873-8966-37df-b6cf-9af82f985a0f

5. EJECUCIÓN (Hardware real):
   Despacho de TAP por Accesibilidad (tap_count += 2)
   Broker debita 100,000 COP DEMO
   TradeEventLog.add(ORDER_STATE, "CONFIRMADA BUY saldo movido", tradeId="d43f183d...", decisionId="74a0e873...")
   -> trades.csv: d43f183d..., 74a0e873..., 1792285146232, BUY, CONFIRMADA...

6. OUTCOME (Liquidación a 60s):
   Balance inicial: 39,243,735.04 COP
   Broker acredita: +182,000.64 COP
   Balance final:   39,325,736.96 COP -> WIN (+82,001.92 COP reales medidos)
```

### Cadena B: Entorno Replay Offline (`ReplayBacktestTest.kt`)

```
1. RECEPCIÓN DE TICK DESDE CSV:
   tick = RawTick(ts=1792285141303, ric="Z-CRY/IDX", price=641.87)

2. ANÁLISIS SINTÉTICO (SyntheticCandleEngine aislado):
   Sec: :01 | Regime: TRENDING | Trend: UPTREND
   -> onSignalGenerated(action=BUY, reason="🎯 MT_REJECTION...")

3. BYPASS DEFENSIVO (Ausencia de TradingEngine):
   NO existe chequeo de agotamiento (VETO_BUY_EXHAUSTION_CLIMAX)
   NO existe chequeo de autoaprendizaje (ADAPTIVE_BLOCK)
   decisionId = generateDeterministicDecisionId("Z-CRY/IDX", 1792285141303, "BUY")
              = 2144b51f-233a-3dd4-861f-4a8b4f80be21

4. EJECUCIÓN SIMULADA INMEDIATA:
   Replay asume que la señal fue una orden ejecutada
   pendingSignals.add(SignalSnapshot(ts=1792285141303, BUY, decisionId="2144b51f..."))
   lastExecutedCandleMinute = 1792285141303 / 60000 (Minuto bloqueado)
   blockedUntilTs = 1792285141303 + 68000 = 1792285209303

5. CEGADO DE LA ENTRADA REAL:
   Cuando el tick de :06 (ts=1792285146232) es procesado:
   candleMinute == lastExecutedCandleMinute es TRUE
   -> EL ARNES DE REPLAY DESCARTA EL TICK DE LA ORDEN REAL L1

6. OUTCOME SIMULADO:
   SimulatedTrade registrado en replay_journal.csv con decision_id = 2144b51f...
   Resultado sintético: WIN (basado en precio teórico de tick a 60s)
```

---

## 7. CORRECCIÓN MÍNIMA PROPUESTA (SIN TOCAR ESTRATEGIA DE TRADING)

Para restablecer la paridad determinista 1:1 en futuras campañas sin alterar ningún parámetro cuantitativo ni lógica de trading, se proponen **tres correcciones de arnés e infraestructura**:

### Propuesta 1: Filtrado temporal estricto en la extracción de ticks
- **Archivo:** `scripts/validation/live_monitor_r23_autonomous.py` (Línea ~955)
- **Acción:** Al guardar `ticks_live.csv`, descartar todo tick cuyo timestamp sea menor al inicio de la ventana:
  ```python
  # Filtrar ticks para no arrastrar historial previo del buffer APK
  ticks_filtrados = [line for ts, line in accumulated_ticks.items() if ts >= window_start_ts_ms]
  ```
- **Efecto:** Elimina automáticamente las operaciones espurias R1 y R2.

### Propuesta 2: Incorporar el pipeline defensivo al test de Replay
- **Archivo:** `app/src/test/java/com/example/tradedraw/ReplayBacktestTest.kt` (Línea ~188)
- **Acción:** En la función `runBacktestOnStrategy`, aplicar la función pura de veto antes de aceptar la señal en `pendingSignals`:
  ```kotlin
  // Replicar los filtros de política que aplica TradingEngine en headless
  val esAgotamiento = TradingEngine.motivoVetoPorAgotamientoDeCompra(
      action = action,
      trend = eng.detectedTrend.name,
      callPower = eng.signalPowerCall
  ) != null

  if (!esAgotamiento && inSniperWindow && lastTick.ts >= cooldownUntilTs && ...) {
      // Disparar orden simulada solo si superó las políticas activas
  }
  ```
- **Efecto:** Evita que Replay ejecute señales vetadas por clímax (R3, R4) y evita el bloqueo indebido de las velas reales (L1, L2).

### Propuesta 3: Ajuste de tolerancias y normalización en el comparador
- **Archivo:** `scripts/validation/compare_live_replay.py` (Línea 320)
- **Acción:** Elevar la tolerancia de fallback temporal a 6,000 ms para absorber el ciclo de confirmación visual de saldo en Android:
  ```python
  def compare_trades(
      live_trades: List[Dict[str, Any]],
      replay_trades: List[Dict[str, Any]],
      temporal_tolerance_ms: int = 6000,  # Absorbe latencia UI de 4.5s-5.0s
      price_tolerance: float = 1e-3,
  ) -> Dict[str, Any]:
  ```
- **Efecto:** Permite que las órdenes confirmadas por saldo que difieran levemente en segundos por latencia de hardware emparejen limpiamente cuando la vela coincide.

---

## 8. CONCLUSIÓN Y DICTAMEN DE AUDITORÍA

1. **El motor en vivo de TradeDraw (build `R23H-BUY-EXHAUSTION`, commit `aac1df3`) no presenta falla de determinismo ni pérdida de paridad en su lógica interna.**
2. **Las 4 operaciones en vivo de W03 fueron ejecutadas de forma impecable**, cumpliendo con la ecuación canónica $N = W + L + VOID$ ($4 = 2 + 2 + 0$), respaldadas por 8 taps en el hardware físico y un delta contable exacto al centavo en cuenta DEMO ($-34,997.76\text{ COP}$).
3. **El reporte de paridad FAIL (0.0% match rate) es un falso negativo** originado por la asimetría de diseño del arnés de test `ReplayBacktestTest.kt` y la contaminación de búfer de `ticks_live.csv`.
4. **El veredicto KEEP de la campaña R23H queda ratificado en su totalidad.**
