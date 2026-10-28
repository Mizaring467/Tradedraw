# HALLAZGOS DEL VALIDADOR INDEPENDIENTE — R23 ITERACIÓN 3 (SHADOW MODE)

**Fecha:** 2026-10-15 (UTC)
**Validador:** OPENCODE (independiente del autor del monitor)
**Commit auditado:** `c08c842` (R23 iter3)
**Build:** versionCode=8, versionName=1.4-r23-iter3, buildId=R23-ITER3, gitSha=c08c842
**Dispositivo:** Redmi A1 (`220733SL`), Android 12 · serial `[fd10:d508:34cf:10:e8c5:80ff:fef0:d5f9]:5555`
**Ventana:** 20,0 min · intervalo 10 s · 116 muestras de `/status`

---

## 1. VEREDICTO

**KEEP** — Sin regresión de seguridad; el `decision_id` determinista queda demostrado.

| Criterio | Resultado | Evidencia |
|---|---|---|
| Modo SHADOW sostenido | ✅ | `mode=SHADOW` inicio y fin de ventana |
| `tapsDespachados == 0` (estricto) | ✅ | 116/116 muestras = 0 |
| Balance inmutable | ✅ | 38.054.735,36 COP (valor único en la ventana) |
| Identidad de build en runtime | ✅ | versionCode=8 / gitSha=c08c842 / buildId=R23-ITER3 |
| Salud | ✅ | `READY` continuo |
| Órdenes shadow aprobadas | 0 | 26 decisiones, todas vetadas por V2_RANGE |
| Endpoint `/shadow_journal` | ✅ | `{"success":true,"total":0,...}` (tail operativo) |
| `decision_id` deterministas | ✅ | 37/37 UUID v3 válidos y únicos (ver §3) |

---

## 2. CONTROL NEGATIVO — `volatilidadSuficiente=false` ↔ VETO V2

**Resultado: CONSISTENTE (correlación perfecta en la ventana).**

| Métrica | Valor |
|---|---|
| Muestras con `volatilidadSuficiente=false` | **116 / 116** (true = 0) |
| Umbral `rangoRelativoMinimo` | `1.00e-05` (constante) |
| Incoherencias `volatilidadSuficiente ≠ (rangoRelativoActual ≥ umbral)` | **0** |
| Eventos `VETO` V2 | **37** |
| Vetos V2 legítimos (`rango < umbral`) | **37 / 37** |
| Vetos V2 ilegítimos (`rango ≥ umbral`) | **0** |
| Rango observado en vetos | `1,64e-10 … 3,90e-10` |
| `vetoV2` (acumulado) | 0 → 26 (Δ = 26 en ventana) |
| `tapsDespachados` | 0 |

> Igual que en iter 1/2, la rama positiva del guard (volatilidad suficiente ⇒ shadow order) **queda
> sin ejercitar**: Crypto IDX nunca superó el umbral. MISSING PROOF heredado.

---

## 3. AUDITORÍA DE `decision_id` DETERMINISTAS (foco de iter 3)

El cambio de iter 3 sustituye `UUID.randomUUID()` (v4) por
`UUID.nameUUIDFromBytes("${ric}_${nonceMs}_${ACTION}")` (v3, MD5) en `TradingEngine.kt:771-777`.

### 3.1 Evidencia en vivo (empírica)

| Métrica | Valor |
|---|---|
| Eventos `VETO` con `decisionId` en `/events` | **37** |
| Versión de UUID observada | **v3 en 37 / 37** |
| Formato RFC-4122 v3 válido | **37 / 37** |
| `decisionId` únicos (sin colisión) | **37 / 37** |
| Cada id asociado a un único `timestamp` | ✅ (0 ids repetidos con distinto `atMs`) |

**Contraste con iter 2 (baseline del defecto):** en iter 2 los ids eran UUID v4 aleatorios y la clave
ingenua `asset-epochSec-action` producía **9 llaves únicas para 17 filas (8 colisiones)**. En iter 3:
**37 ids únicos para 37 decisiones**, UUID v3 → el defecto queda corregido.

### 3.2 Límite de la verificación in-situ

No es posible recomputar el id desde `events.jsonl` por sí solo: el `nonceMs` de la clave es el
**timestamp del tick** que disparó la señal, no el `atMs` del evento de veto. Un intento de recompute
con `atMs ± tolerancia` y acción `BUY/SELL/NONE` dio **0 coincidencias** — **esperado**, no un defecto.
La verificación fuerte del determinismo es la prueba pura (mismo input → mismo output), cubierta por
el test unitario:

- `app/src/test/java/com/example/tradedraw/DeterministicDecisionIdTest.kt` (8 casos): estabilidad,
  colapso visible↔RIC, idempotencia de RIC, convergencia seg↔ms, no-colisión por tick, distinción
  id/acción, normalización de acción, y **versión==3** (anti-reintroducción de random).
- **Ejecutado por el validador: 8/8 PASS (`tests=8 failures=0 errors=0 skipped=0`)** —
  log `loops/r23/iter_3/gradle_test_deterministic.log` (`BUILD SUCCESSFUL`) y reporte XML
  `app/build/test-results/testDebugUnitTest/TEST-com.example.tradedraw.DeterministicDecisionIdTest.xml`.
  *(Nota: el wrapper devolvió exit 1 por un falso positivo de PowerShell al leer stderr de Gradle —
  advertencias de deprecación—; el veredicto real es BUILD SUCCESSFUL y 0 fallos.)*

### 3.3 Límite conocido y aceptado (del propio código)

Dos decisiones del mismo activo, mismo milisegundo y misma acción comparten id. A 1-2 ticks/s es
improbable y el comparador agrupa por id (una colisión pierde una fila, no corrompe el pareo). La
ausencia de colisiones en las 37 observaciones respalda el supuesto.

---

## 4. PARIDAD Live ↔ Replay (estado)

- El comparador se invocó con los argumentos correctos: **26 decisiones, 0 pares, 26 MISSING_REPLAY, 0,0 %**.
- `LIVE_REPORT.md` reporta "NO EVALUABLE (0 pares evaluados, 26 decisiones sin replay correspondiente)".
- **Avance de iter 3 vs iter 2:** el motor (vivo *y* replay) ya genera ids deterministas y por tanto
  *emparejables*. **Lo que aún falta** es que el pipeline de validación **ejecute el replay offline**
  y produzca un journal con esos ids para alimentar `--replay-journal`. Sin ese paso, la paridad
  sigue sin poder medirse.

---

## 4-bis. DEFECTOS COSMÉTICOS DEL `LIVE_REPORT.md`

1. **Sección 4 desactualizada:** afirma "26 decisiones con `decision_id` **UUID v4**", pero en iter 3
   los ids son **v3 deterministas** (37/37 verificados). El texto del template no se actualizó al
   cambio de iteración → afirmación falsa sobre la naturaleza del id. (No afecta al veredicto, pero
   un auditor que lea sólo el reporte queda mal informado.)
2. La duplicación de texto de la sección 7 ("Paridad Live ↔ Replay: Paridad Live ↔ Replay: …")
   reportada en iter 2 **ya fue corregida** (commit `fd74445`).

---

## 5. ARTEFACTOS AUDITADOS (`loops/r23/iter_3/`)

| Artefacto | Estado |
|---|---|
| `live_samples.jsonl` | ✅ 116 muestras |
| `status_samples.jsonl` | ✅ 116 volcados |
| `events.jsonl` | ✅ 200 eventos (163 ASSET, 37 VETO) |
| `decisions.csv` | ✅ 27 líneas (1 cabecera + 26 decisiones) |
| `mode_log.json` | ✅ presente |
| `shadow_journal.json` | ✅ `total: 0` |
| `LIVE_REPORT.md` | ✅ veredicto PASS/KEEP |
| `replay_comparison.json` / `_report.md` | ✅ generados (0 pares) |
| `monitor_stdout.log` | ✅ traza completa |
| `gradle_test_deterministic.log` | ✅ evidencia de test unitario |

---

## 6. RECOMENDACIÓN

- **Binario:** KEEP.
- **Iteración 4:** (a) que el monitor **ejecute el replay offline** para generar el journal pareado y
  medir por fin la paridad (≥ 80 %); (b) ejercitar la rama positiva del guard en activo volátil para
  obtener el primer `SHADOW_DECISION`; (c) corregir la duplicación del texto en la sección 7 del
  `LIVE_REPORT.md` (detectada en iter 2, aún presente).
