# HALLAZGOS DEL VALIDADOR INDEPENDIENTE — R23A ITERACIÓN 1 (MODO AUTÓNOMO · CUENTA DEMO)

**Fecha:** 2026-10-15 (UTC)
**Validador:** OPENCODE (independiente del autor del monitor)
**Commit auditado:** `7a37032` (R23A iter1)
**Build:** versionCode=10, versionName=1.4-r23-auto-iter1, buildId=R23-AUTO-ITER1, gitSha=7a37032
**Dispositivo:** Redmi A1 (`220733SL`), Android 12 · serial `[fd10:d508:34cf:10:e8c5:80ff:fef0:d5f9]:5555`
**Ventana:** 20,0 min · intervalo 10 s · 117 muestras de `/status`

---

## 1. VEREDICTO

**KEEP (seguridad)** — Cero exposición a cuenta real; el ciclo autónomo DEMO se ejecutó de extremo a
extremo. Los defectos hallados son de **tooling de validación**, no de seguridad ni de sellado de la
cuenta real.

| Criterio | Resultado | Evidencia |
|---|---|---|
| `isDemoAccount == true` en cada muestra | ✅ | **117 / 117** (cuentas reales: 0) |
| Guard fail-closed activo | ✅ | No hubo transición a cuenta real en ninguna muestra |
| Modo AUTONOMOUS sostenido | ✅ | 117/117 muestras |
| Identidad de build en runtime | ✅ | versionCode=10 / gitSha=7a37032 / buildId=R23-AUTO-ITER1 |
| Salud | ✅ | `READY` continuo |
| Pipeline autónomo (señal→tap→trade→resolución) | ✅ | 28 señales, 4 taps, 2 trades, W=1/L=1 |
| Balance dinámico | ✅ | 38.054.735,36 → 38.036.736,00 COP (Δ **−17.999,36**) |
| Columna `asset` en replay | ✅ | `replay_journal.csv` ya la incluye (`Z-CRY/IDX` = Live) |
| Test `AutonomousSafetyAndAssetScaleTest` | ✅ | **10/10 PASS** (`tests=10 failures=0 errors=0`) |

---

## 2. HALLAZGO BLOQUEANTE (tooling) — El monitor NO arranca en Windows

`live_monitor_r23_autonomous.py:245` imprime el carácter Unicode `✓` (U+2713). Bajo la consola
cp1252 de Windows, Python aborta con:

```
UnicodeEncodeError: 'charmap' codec can't encode character '\u2713' in position 53
```

El monitor **murió a los ~30 s** en el primer intento (prioridad del defecto: no llega a recolectar la
ventana). **Workaround aplicado por el validador** (sin tocar el código auditado):
`PYTHONIOENCODING=utf-8` + `PYTHONUTF8=1` → segunda ejecución completó los 20 min.

**Impacto:** cualquier ejecución del monitor en consola Windows por defecto **falla**. Debe fijarse
`sys.stdout.reconfigure(encoding="utf-8")` o eliminar el glifo del `print`.

---

## 3. HALLAZGO CRÍTICO — Incoherencia de unidades en el dimensionamiento de capital

| Magnitud | Valor observado |
|---|---|
| `investmentAmount` declarado (`/status.risk`) | 1.0 → 2.0 (martingala `M0`→`M1`) |
| Desvío **real** del balance por operación | **≈ 100.000 COP** (pérdida) / **+82.000 COP** (ganancia) |
| Desvío mínimo del balance | **−99.998,72 COP** |
| Desvío máximo del balance | **+82.001,92 COP** |
| `observedOrderAmount` (`/nodes`) | **100.000,0** |

**El stake nominal (1-2 "USD") NO corresponde al movimiento real del balance (~100.000 COP por
operación).** El desvío neto de −17.999,36 COP ≈ (−100.000) + (+82.000) confirma **2 resoluciones**
(1 pérdida de ~100k y 1 ganancia de ~82k, payout ~82 %). En cuenta DEMO no hay riesgo financiero,
pero **invalida el modelado de capital** exigido por gobernanza (1 %–3,5 % del balance por operación:
aquí el efecto real es 100.000/38.054.735 ≈ 0,26 %, y el declarado 1,0/38 M es despreciable).
El dimensionamiento declarado y el efecto observado están **desacoplados**.

---

## 4. PARIDAD Live ↔ Replay

**Avance clave:** por primera vez hay **4 pares emparejados por `decision_id`** y la columna `asset`
coincide en los dos motores (`Z-CRY/IDX` = `Z-CRY/IDX`) — el defecto de iter 4 (fallback
`ETHUSD-OTC`) **queda resuelto**.

| Métrica | Valor |
|---|---|
| Decisiones totales | 28 |
| Pares evaluables | **4** |
| Exact matches | 0 |
| Divergencias | 4 |
| Missing en Replay | 24 |
| **Match rate** | **0,0 % (FAIL)** |

### Desglose de divergencias (todas **ESPURIAS**, por el monitor de validación)

| decision_id | campo divergente | Live | Replay |
|---|---|---|---|
| `1551c87f-…` | `candle_second` | 5 | 3 |
| `955fa53a-…` | `market_regime` | UPTREND | TRENDING |
| `c818605e-…` | `market_regime` | UPTREND | TRENDING |
| `02063655-…` | `market_regime` | UPTREND | TRENDING |

1. **`market_regime` (3 casos):** el monitor (`live_monitor_r23_autonomous.py:213`) asigna
   `market_regime = status.analysis.trend` (**UPTREND**) en lugar del régimen real (CHOP/TRENDING).
   El replay reporta `TRENDING`. Divergencia **artefactual del monitor**.
2. **`candle_second` (1 caso):** Live usa `(atMs//1000)%60` (segundo de pared) mientras el replay usa
   el segundo de la vela del tick. Desalineación de reloj, no de decisión.

Con ambas correcciones, los 4 pares serían candidatos a **MATCH**. La paridad sigue **NO CONCLUYENTE
en sentido positivo** por defectos del monitor, no del motor.

---

## 5. DEFECTOS SECUNDARIOS DEL MONITOR AUTÓNOMO

1. **`action=UNKNOWN`** en `trades.csv`: no extrae BUY/SELL del evento de confirmación.
2. **Taps vs trades:** `tapsDespachados=4` pero sólo **2** "CONFIRMADA saldo movido" → 2 taps sin
   confirmación de trade (posibles reintentos/sin movimiento de saldo). Sin explicación en el reporte.
3. **Cobertura de replay baja:** sólo 4 de 28 decisiones tienen contraparte (24 `missing_replay`).
4. **`mode_log.json`** recogido pero no citado en el `LIVE_REPORT.md`.
5. **El veredicto PASS sólo evalúa seguridad**, no eficacia: con W=1/L=1 no hay significancia
   estadística sobre el win-rate. El reporte no debe leerse como aval de rentabilidad.

---

## 6. ARTEFACTOS AUDITADOS (`loops/r23_autonomous/iter_1/`)

| Artefacto | Estado |
|---|---|
| `live_samples.jsonl` | ✅ 117 muestras |
| `status_samples.jsonl` | ✅ 117 volcados |
| `events.jsonl` | ✅ 200 eventos (180 ASSET, 19 VETO, 1 ORDER_STATE) |
| `decisions.csv` | ✅ 28 decisiones |
| `trades.csv` | ✅ 2 trades (con `action=UNKNOWN`) |
| `ticks_live.csv` | ✅ volcado (zona de ticks) |
| `replay_journal.csv` | ✅ 4 filas (con columna `asset`) |
| `mode_log.json` / `shadow_journal.json` | ✅ presentes |
| `LIVE_REPORT.md` | ✅ PASS/KEEP (generado por el monitor) |
| `replay_comparison.json` / `_report.md` | ✅ 4 pares / 4 divergencias |
| `gradle_test_autonomous.log` | ✅ 10/10 PASS |
| `monitor_stdout.log` | ✅ traza (2ª ejecución, con workaround) |

---

## 7. RECOMENDACIÓN

- **Seguridad del binario:** KEEP (guard DEMO fail-closed correcto, 0 cuentas reales).
- **Antes de R23A iter2:**
  1. **Corregir el `UnicodeEncodeError`** del monitor (bloqueante en Windows).
  2. Corregir `market_regime` (usar régimen real, no `trend`) y `candle_second` (reloj de vela) para
     que la paridad deje de ser espuria.
  3. Resolver la **incoherencia de unidades** del dimensionamiento (stake declarado vs efecto real
     en balance) — es el hallazgo de mayor riesgo de modelado.
  4. Poblar `action` en `trades.csv` y explicar la diferencia taps(4) vs trades(2).
  5. Elevar la cobertura del replay para medir una paridad representativa.
