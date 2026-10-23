# R24 — B2 + B3 + B4: resultado

Rama `r10-night`. Estrategia intacta: no se tocaron señales, umbrales, vetos ni stakes.

| Tarea | Commit | SHA |
| :--- | :--- | :--- |
| B2 — Columnas S/R en `decisions.csv` | `feat(r24): B2 columnas S/R en decisions.csv` | `7ec019ac62aa5ead2c84da3cc363f3c5e25cbb5d` |
| B3 — Endpoint `adaptive_state` | `feat(r24): B3 endpoint adaptive_state` | `0a728e5759d8b485412642d690f4602761420938` |
| B4 — Diagnóstico `NO_PROCESADA` | `feat(r24): B4 diagnostico NO_PROCESADA` | `69046d2c41242a464168624d73e1c219ad1834e8` |

---

## B2 — Columnas S/R en `decisions.csv`

### Archivos

- `scripts/validation/live_monitor_r23_autonomous.py` — writer real de `decisions.csv`.
  **No** es `TradeJournalLogger`: ese escribe `trade_journal.csv` y `shadow_journal.csv`. El
  `decisions.csv` de las ventanas lo produce el monitor de validación.
- `app/src/main/java/com/example/tradedraw/TradeDrawHttpBridge.kt` — publica las dos ratios en el
  bloque `analysis` de `/status`.

### Cambio

- `fieldnames` de `decisions.csv`: las 14 columnas históricas intactas, más
  `distanceToSupportRatio` y `distanceToResistanceRatio` **al final**.
- Los valores son los REALES del instante: `syntheticCandleEngine.distanceToSupportRatio` /
  `distanceToResistanceRatio`, los mismos que `AdaptiveLearningEngine` consume como
  `distSup`/`distRes` al construir `TradeContextSignature`. No se recalcula nada en el monitor.
- Celda vacía cuando no hay medición. El bridge emite `-1` como centinela si no hay velas
  cerradas (el campo del motor vale `0.5` por defecto y eso **no** es una medición); el monitor
  traduce `-1`, `None` y no numéricos a `""`. **Nunca `0.0`**, que sí es una medición legítima.

### Tests

- `scripts/validation/test_decisions_sr_columns.py` — 8 casos, **8/8 PASS**:
  las 14 columnas previas coinciden campo a campo entre un CSV viejo y uno nuevo; la cabecera
  nueva es la previa + 2 al final; un lector viejo ignora las nuevas; un lector nuevo sobre CSV
  viejo no lanza; centinela/ausente/no numérico → celda vacía; `0.0` real se conserva.
- `scripts/validation/live_monitor_r23_autonomous.py --test` — **PASS (4/4)**, sin regresión.

---

## B3 — Endpoint `GET /adaptive_state`

### Archivos

- `app/src/main/java/com/example/tradedraw/TradeDrawHttpBridge.kt` — ruta nueva +
  `buildAdaptiveStateJson`.

### Cambio

- Devuelve el contenido de `filesDir/adaptive_learning_state.json`.
- **Solo lectura.** No llama a `saveState` (eso reescribiría el archivo vivo y reiniciaría su TTL),
  no modifica el archivo, no toca el motor.
- Contrato: `success`, `exists`, `parseable`, `firmas` (= longitud de `lossSignatures`), `path`,
  `state`. Archivo ausente → 200 con `exists:false`. Archivo corrupto → 200 con
  `parseable:false` y el texto escapado en `stateRaw`, para que la respuesta HTTP nunca deje de ser
  JSON válido por culpa del dato que transporta.

### Tests

- `app/src/test/java/com/example/tradedraw/AdaptiveStateEndpointTest.kt` — 5 casos, **5/5 PASS**
  (verificado en `TEST-com.example.tradedraw.AdaptiveStateEndpointTest.xml`, `failures=0 errors=0`).

---

## B4 — Diagnóstico de `NO_PROCESADA`

### Análisis de W17

Detalle completo en `workflows/PREREGISTRO_R24.md` §6.1. Resumen:

- `decisions.csv`: 72 filas — 69 `VETO`, 2 `TAP`, 1 `ORDER_STATE`. Los dos `TAP` con `exit=0`.
- `status_samples.jsonl`: 118 muestras. Secuencia `NINGUNA → CONFIRMADA → NINGUNA → ENVIADA →
  NO_PROCESADA → ENVIADA → NO_PROCESADA`.
- Duración observada: **≥ 590 s** y **≥ 540 s**. Son cotas inferiores (muestreo cada ~10 s y la
  ventana termina antes del cambio de estado).
- `events.jsonl`: 200 eventos, **los 200 `ASSET`**. Cero `TAP` y cero `ORDER_STATE`: el ring de 200
  estaba saturado y la transición no es observable por esa vía.
- Saldo inmóvil en ambas ocurrencias → no se creó orden (no es un empate).
- `WINDOW_REPORT.md` **no existe** en `window_17/`; el reporte es `LIVE_REPORT.md`.
- Causa: **NO ATRIBUIDA**. H3 (`FEED_MUDO`) queda refutada para W17 (feed fresco, 13–337 ms).

### Archivos

- `app/src/main/java/com/example/tradedraw/RiskManager.kt` — `NoProcesadaCausa` (taxonomía
  cerrada), `clasificarCausaNoProcesada` (pura), `registrarNoProcesada`, `cerrarNoProcesada`,
  `noProcesadaDuracionMaxMs`, `noProcesadaCausa`, `noProcesadaOcurrencias`,
  `noProcesadaDuracionActualMs`. `markOrderUnprocessed` acepta la evidencia y etiqueta la causa.
- `app/src/main/java/com/example/tradedraw/TradingEngine.kt` — pasa la evidencia real
  (`tapDespachado` desde `pendingTradeTapEndTs`, `feedFresco` desde `isFeedFresh()`,
  `saldoObservable` desde la lectura de saldo). Solo etiqueta: no altera la decisión.
- `app/src/main/java/com/example/tradedraw/TradeDrawHttpBridge.kt` — campos nuevos en `risk`:
  `noProcesadaDuracionMaxMs`, `noProcesadaCausa`, `noProcesadaOcurrencias`,
  `noProcesadaDuracionActualMs`.
- `workflows/PREREGISTRO_R24.md` — §6.1 con las hipótesis y su falsación.

### Tests

- `app/src/test/java/com/example/tradedraw/NoProcesadaDiagnosticsTest.kt` — 12 casos,
  **12/12 PASS** (`failures=0 errors=0`): taxonomía exacta de 5 etiquetas, orden de clasificación,
  cronómetro entrada/salida, máximo monótono, causa fuera de taxonomía → `NO ATRIBUIDA`,
  contador sobrevive a `resetStats`.

---

## LÍMITES

1. **`decisions.csv` no lo escribe Kotlin.** Lo escribe el monitor de validación en Python. B2 toca
   el writer de Python y añade el dato en `/status`; no hay ninguna ruta Kotlin que escriba ese CSV.
2. **El centinela `-1` es nuevo en `/status`.** Un consumidor que lea
   `analysis.distanceToSupportRatio` sin conocer el centinela interpretará `-1` como valor. Se
   documenta aquí; el monitor ya lo traduce a celda vacía.
3. **La duración de W17 es una cota inferior**, no la duración real: muestreo cada ~10 s y la
   ventana se cierra antes del cambio de estado. `noProcesadaDuracionMaxMs` sí medirá el valor
   exacto en R24, pero solo desde el arranque del proceso (el cronómetro vive en memoria).
4. **`noProcesadaDuracionMaxMs` se reinicia con el proceso.** Es coherente con V4 (que mide dentro
   de una ventana), pero una ventana con reinicio del APK pierde el máximo previo.
5. **La causa de las `NO_PROCESADA` de W17 sigue sin atribuir.** B4 la etiquetará en R24; no se
   retro-atribuye W17.
6. **`TAP_RECHAZADO_BROKER` no se emite nunca en el código actual.** Está en la taxonomía cerrada
   del preregistro, pero el clasificador solo puede distinguir "tap no despachado", "feed mudo",
   "saldo no observable" y "sin movimiento de saldo con tap y feed OK". Distinguir "el broker
   rechazó" de "el broker nunca vio el tap" requiere leer el estado de la UI del broker, que no está
   instrumentado. Declarado, no disimulado.
7. **`events.jsonl` de W17 no sirve para atribuir la transición**: 200/200 eventos son `ASSET`.
   El ring general está dominado por eventos de activo. B4 no lo arregla (no era su alcance).
8. **No se ejecutó la suite completa** (instrucción explícita del orquestador). Se ejecutaron las
   clases nuevas y las de regresión directa del área tocada
   (`StatusJsonStrictSyntaxTest`, `OrderConfirmationTest`, `RiskManagerTest`,
   `TradeJournalLoggerTest`).
9. **No se probó en dispositivo.** Prohibido ADB y teléfono en esta tarea.
