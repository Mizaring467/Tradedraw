# HALLAZGOS DEL VALIDADOR INDEPENDIENTE — R23A ITERACIÓN 3 (MODO AUTÓNOMO · CUENTA DEMO)

**Fecha:** 2026-10-16 (UTC)
**Validador:** OPENCODE (independiente del autor del monitor)
**Commit auditado:** `bd7ef4e` (R23A iter3)
**Build:** versionCode=12, versionName=1.4-r23-auto-iter3, buildId=R23-AUTO-ITER3, gitSha=bd7ef4e
**Dispositivo:** Redmi A1 (`220733SL`), Android 12 · USB `UGU8KFUKM7E6CE8D` (cargando, 37 %)
**Ventana:** 20,0 min · intervalo 10 s · 99 muestras de `/status`

---

## 1. VEREDICTO

**KEEP (seguridad)** — Cero exposición a cuenta real. Los 3 arreglos de iter 3 (JSON de `/status`,
`taps_window`, `gitSha` dinámico) **quedan verificados**. La ventana volvió a quedar **sin trades**
por acción de los guards.

| Criterio | Resultado | Evidencia |
|---|---|---|
| `isDemoAccount == true` en cada muestra | ✅ | **99 / 99** (cuentas reales: 0) |
| Modo AUTONOMOUS sostenido | ✅ | 99/99 muestras |
| Identidad de build en runtime | ✅ | versionCode=12 / gitSha=bd7ef4e / buildId=R23-AUTO-ITER3 |
| Balance inmutable en la ventana | ✅ | 38.320.737,28 COP constante (Δ 0,00) |
| Fix `/status` JSON estricto | ✅ | `json.loads` puro OK (sin comentarios `//`) |
| Fix `taps_window` | ✅ | Reporte: "0 taps en la ventana (acumulado: 2)" |
| Fix `gitSha` dinámico | ✅ | Reporte: `gitSha="bd7ef4e"` (ya no vacío) |
| Ciclo operativo (señal→tap) | ⚠️ | **0 trades** — guards bloquearon toda operación |

---

## 2. VERIFICACIÓN DE LOS 3 ARREGLOS DE ITER 3

### 2.1 JSON de `/status` (regresión de iter 2) — RESUELTO
`json.loads(raw)` sobre `/status` **funciona sin filtro** (verificado por el validador con parser
estricto). Los comentarios `//` embebidos fueron erradicados (RFC 8259). **Confirmado** — commit
`bd7ef4e` + test de parsing estricto.

### 2.2 `taps_window` — RESUELTO
El `LIVE_REPORT.md` ahora distingue los taps **de la ventana** del acumulado histórico:
> "0 taps despachados en la ventana (acumulado histórico del proceso: **2**)."

Corrige la lectura engañosa de iter 2. **Confirmado** (`live_monitor_r23_autonomous.py:389`).

### 2.3 `gitSha` dinámico — RESUELTO
El reporte propaga `gitSha="bd7ef4e"` (extraído de `/status`), donde iter 2 lo mostraba **vacío**.
**Confirmado** (`:388`, `:397`).

---

## 3. POR QUÉ NO HUBO TRADES — Guards de nuevo al 100 %

Ventana: **0 trades**, 0 resoluciones (W/L sin cambio), balance inmutable. Los 9 eventos VETO:

| Tipo | Ocurrencias | Detalle |
|---|---|---|
| `ADAPTIVE_BLOCK` | 7 | "Anti-patrón perdedor recurrente (98% similitud en contexto S/R=0%) - inversión deshabilitada" |
| `RISK_LIMIT` | 2 | "Trailing Profit Lock: Ganancia asegurada (+392000 COP pico)" |

Fail-closed correcto, pero **tercera iteración consecutiva sin ejercitar el ciclo señal→tap→
resolución→W/L**. La validación de eficacia sigue **imposible** en este régimen de mercado.

---

## 4. PARIDAD Live ↔ Replay

| Métrica | Valor |
|---|---|
| Decisiones totales | 71 |
| Pares evaluables | **1** |
| Exact matches | 0 |
| Divergencias | 1 |
| **Missing en Live** (solo en replay) | **3** |
| Missing en Replay | 67 |
| **Match rate** | **0,0 % (FAIL)** |

### 4.1 La divergencia es ESPURIA (esquema del monitor)
El único par (`e892f69e-…`) diverge en `dist_resistance` (Live=0.0, Replay=0.05). **Causa:** el
`decisions.csv` que exporta el monitor **no incluye las columnas `dist_support` ni
`dist_resistance`**; el comparador las lee como `0.0` por defecto → divergen siempre. No es una
divergencia de decisión.

### 4.2 Nuevo: `missing_live = 3`
Por primera vez hay **3 decisiones presentes en el replay y ausentes en el live** (`66fd75f2`,
`f39276e9`, `72cdf013`). El motor de replay **no reproduce el mismo conjunto de decisiones** que el
vivo: divergen en qué ticks generan señal. Refuerza el hallazgo estructural: **ambos motores no son
isomorfos** (cobertura de pares 1/71).

### 4.3 Diagnóstico estructural (persistente en las 3 iteraciones)
La cobertura de paridad es sistemáticamente muy baja (28 iter1 → 79 iter2 → 71 iter3 decisiones;
pares 4 → 2 → 1). El replay genera un número de decisiones muy inferior al vivo y con conjunto no
idéntico. **La paridad Live↔Replay no es medible de forma representativa** mientras ambos motores no
compartan exactamente (ticks, guards, pipeline).

---

## 5. DEFECTOS Y PENDIENTES

1. **`decisions.csv` sin `dist_support`/`dist_resistance`** → divergencias espurias en el comparador.
2. **Asimetría de motores** (`missing_live`/`missing_replay` masivos): el replay no modela los guards
   (ADAPTIVE_BLOCK/RISK_LIMIT) y no recorre el mismo conjunto de señales.
3. **Ciclo autónomo sin ejercitar** por 3ª iteración (guards al 100 %).
4. `decisions.csv` usa `score=85.0` fijo (constante) — no representa confluencia real.

---

## 6. ARTEFACTOS AUDITADOS (`loops/r23_autonomous/iter_3/`)

| Artefacto | Estado |
|---|---|
| `live_samples.jsonl` | ✅ 99 muestras |
| `status_samples.jsonl` | ✅ 99 volcados |
| `events.jsonl` | ✅ 200 eventos (191 ASSET, 9 VETO) |
| `decisions.csv` | ✅ 68 decisiones (`market_regime=TRENDING`) |
| `trades.csv` | ⚠️ sólo cabecera (0 trades en ventana) |
| `ticks_live.csv` | ✅ volcado |
| `replay_journal.csv` | ✅ 4 filas (con `asset`) |
| `mode_log.json` | ✅ presente |
| `LIVE_REPORT.md` | ✅ PASS/KEEP (taps_window + gitSha correctos) |
| `replay_comparison.json` / `_report.md` | ✅ 1 par / 3 missing_live / 67 missing_replay |
| `monitor_stdout.log` | ✅ 20 min completos, sin crash |

---

## 7. RECOMENDACIÓN

- **Seguridad del binario:** KEEP (guard DEMO fail-closed correcto, 0 cuentas reales).
- **Antes de R23A iter4:**
  1. **Exportar `dist_support`/`dist_resistance`** (y confluencia real) en `decisions.csv` para
     eliminar divergencias espurias.
  2. **Alinear los motores** replay↔vivo: incorporar los guards ADAPTIVE_BLOCK/RISK_LIMIT al replay y
     garantizar que ambos recorren el mismo conjunto de ticks/señales (hoy 67/71 discrepan).
  3. **Ejercitar el ciclo completo**: sin una ventana que realmente opere (tap→resolución→W/L), la
     validación de eficacia del modo autónomo sigue pendiente tras 3 iteraciones.
  4. Sustituir el `score=85.0` fijo por la confluencia real.
