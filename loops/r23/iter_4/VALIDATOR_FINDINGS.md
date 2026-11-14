# HALLAZGOS DEL VALIDADOR INDEPENDIENTE — R23 ITERACIÓN 4 (SHADOW MODE)

**Fecha:** 2026-10-15 (UTC)
**Validador:** OPENCODE (independiente del autor del monitor)
**Commit auditado:** `def14f8` (R23 iter4)
**Build:** versionCode=9, versionName=1.4-r23-iter4, buildId=R23-ITER4, gitSha=def14f8
**Dispositivo:** Redmi A1 (`220733SL`), Android 12 · serial `[fd10:d508:34cf:10:e8c5:80ff:fef0:d5f9]:5555`
**Ventana:** 20,0 min · intervalo 10 s · 118 muestras de `/status`

---

## 1. VEREDICTO

**KEEP** — Sin regresión de seguridad. El `/ticks` y el replay offline operan; el determinismo de
`decision_id` queda **probado end-to-end** (id emparejado entre los dos motores).

| Criterio | Resultado | Evidencia |
|---|---|---|
| Modo SHADOW sostenido | ✅ | `mode=SHADOW` inicio y fin |
| `tapsDespachados == 0` (estricto) | ✅ | 118/118 muestras = 0 |
| Balance inmutable | ✅ | 38.054.735,36 COP (valor único) |
| Identidad de build en runtime | ✅ | versionCode=9 / gitSha=def14f8 / buildId=R23-ITER4 |
| Salud | ✅ | `READY` continuo |
| Endpoint `/ticks` (JSON y CSV) | ✅ | 2000 filas, ts monótono, precio sin redondeo |
| Replay offline automático | ✅ | `ReplayBacktestTest` exit 0 → `replay_journal.csv` (3 filas) |
| `decision_id` determinista v3 | ✅ | 48/48 únicos y válidos; **3/3 emparejados Live↔Replay** |
| Test `TicksEndpointTest` | ✅ | **10/10 PASS** (`tests=10 failures=0 errors=0`) |

---

## 2. CONTROL NEGATIVO — `volatilidadSuficiente=false` ↔ VETO V2

**CONSISTENTE.**

| Métrica | Valor |
|---|---|
| Muestras con `volatilidadSuficiente=false` | **118 / 118** (true = 0) |
| Incoherencias `vs ≠ (rango ≥ umbral)` | **0** |
| Eventos `VETO` V2 | **48** |
| Vetos V2 legítimos (`rango < umbral`) | **48 / 48** |
| Vetos V2 ilegítimos | **0** |
| Rango en vetos | `1,71e-10 … 5,30e-10` |

---

## 3. ENDPOINT `/ticks` (foco de iter 4)

| Propiedad | Verificado |
|---|---|
| JSON: `{"success":true,"total":N,"totalEnBuffer":M,"ticks":[{ts,ric,price}]}` | ✅ |
| CSV: cabecera `ts,ric,price` | ✅ |
| `?max=N` acotado y en orden cronológico ascendente | ✅ (test) |
| Precio **sin redondeo** (p. ej. `641.867304055`) | ✅ |
| Buffer circular (descarta lo más viejo) | ✅ (test) |
| `ts` monótono no decreciente sobre 2000 filas | ✅ |
| `ric` único en ventana | ✅ (`Z-CRY/IDX`) |

**Test unitario `TicksEndpointTest`: 10/10 PASS** (`BUILD SUCCESSFUL`, 0 fallos) — log
`loops/r23/iter_4/gradle_test_ticks.log` y XML
`app/build/test-results/testDebugUnitTest/TEST-com.example.tradedraw.TicksEndpointTest.xml`.
*(El wrapper devolvió exit 1 por falso positivo de PowerShell al leer stderr de Gradle —
deprecations—; el veredicto real es BUILD SUCCESSFUL.)*

---

## 4. REPLAY OFFLINE Y PARIDAD Live ↔ Replay

### 4.1 Avance histórico
Por primera vez el pipeline **ejecutó el replay offline** (no existía en iter 1-3):

- `live_ticks.csv` (2000 ticks) → `ReplayBacktestTest` (exit 0) → `replay_journal.csv` (3 filas).
- El comparador recibió `--replay-journal` y **emparejó 3 decisiones por `decision_id`**.

| Métrica de paridad | Valor |
|---|---|
| Decisiones totales (Live) | 61 |
| Pares evaluables (Live & Replay) | **3** |
| Exact matches | **0** |
| Divergencias | **3** |
| Missing en Replay | 58 |
| Missing en Live | 0 |
| No verificables | 0 |
| **Match rate** | **0,0 % (FAIL)** |

### 4.2 El id determinista FUNCIONA (hallazgo positivo)
Los **3 pares coinciden en `decision_id`** (el mismo UUID v3 generado por el motor vivo y por el
replay) y **en `candle_second` (1/1)**. Esto demuestra que el objetivo de iter 3 —hacer emparejable
la misma decisión en ambos motores— **se cumple end-to-end**.

### 4.3 La divergencia reportada es ESPURIA (defecto del comparador)
Los 3 pares divergen **sólo** en el campo `asset`:

| decision_id | asset Live | asset Replay |
|---|---|---|
| `cbf3e319-…` | `Z-CRY/IDX` | `ETHUSD-OTC` |
| `e6921d56-…` | `Z-CRY/IDX` | `ETHUSD-OTC` |
| `9aff66fc-…` | `Z-CRY/IDX` | `ETHUSD-OTC` |

**Causa raíz:** `replay_journal.csv` **no tiene columna `asset`** (su columna `source` =
`live_ticks.csv`). En `compare_live_replay.py:185-195`, al no hallar `asset`, el parser cae a un
fallback **hardcodeado que por defecto devuelve `ETHUSD-OTC`** (sólo contempla ETH/AAVE/CRY, y aquí
el valor `live_ticks.csv` no coincide con ninguno). Por tanto el replay **nunca** puede reportar
`Z-CRY/IDX`: la divergencia es un artefacto de normalización, **no una divergencia de decisión**.

**Segunda divergencia latente (enmascarada por la jerarquía):** aunque `asset` se corrigiera, el
siguiente campo de la jerarquía, `price`, también diferiría: Live=`641.8673` (de
`/status.analysis.currentPriceY`) vs Replay=`641.87` (`price_y` del journal, **redondeado a 2
decimales**). El flag "sin redondeo" de iter 4 aplica a `/ticks`, no al journal de replay.

### 4.4 Conclusión de paridad
El `match_rate 0 %` **no es una divergencia lógica de decisión**: es consecuencia de (a) fallback de
`asset` hardcodeado y (b) redondeo de `price_y` en el journal. Con las normalizaciones correctas,
los 3 pares serían **MATCH**. La paridad sigue siendo **NO CONCLUYENTE en sentido positivo**, pero
por defectos de esquema del comparador, no por comportamiento del motor.

**Pendiente estructural:** sólo 3 de 61 decisiones tienen contraparte en replay (58 `missing_replay`):
el motor de replay generó apenas 3 decisiones sobre 2000 ticks, muy por debajo del vivo (61). La
cobertura del replay necesita alinearse con la del motor vivo para medir una paridad representativa.

---

## 5. ARTEFACTOS AUDITADOS (`loops/r23/iter_4/`)

| Artefacto | Estado |
|---|---|
| `live_samples.jsonl` | ✅ 118 muestras |
| `status_samples.jsonl` | ✅ 118 volcados |
| `events.jsonl` | ✅ presente (152 ASSET, 48 VETO) |
| `decisions.csv` | ✅ 62 líneas (1 cabecera + 61 decisiones) |
| `live_ticks.csv` | ✅ 2000 ticks |
| `replay_journal.csv` | ✅ 3 filas (sin columna `asset`) |
| `mode_log.json` / `shadow_journal.json` | ✅ presentes |
| `LIVE_REPORT.md` | ✅ PASS/KEEP (+ nota del validador) |
| `replay_comparison.json` / `_report.md` | ✅ 3 pares / 3 divergencias |
| `gradle_test_ticks.log` | ✅ 10/10 PASS |

---

## 6. RECOMENDACIÓN

- **Binario:** KEEP.
- **Iteración 5:** (a) añadir la columna `asset` (RIC en crudo) al `replay_journal.csv` y ampliar el
  fallback del comparador para que reconozca el RIC real — así la paridad deja de ser espuria;
  (b) alinear la resolución de precio del journal (evitar el redondeo de `price_y` a 2 decimales) para
  que `price` no diverja tras corregir `asset`; (c) elevar la cobertura del motor de replay para que
  produzca un nº de decisiones comparable al vivo; (d) ejercitar la rama positiva del guard
  (`volatilidadSuficiente=true`) vía activo volátil para obtener el primer `SHADOW_DECISION`.
