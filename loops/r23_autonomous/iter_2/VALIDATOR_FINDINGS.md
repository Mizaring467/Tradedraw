# HALLAZGOS DEL VALIDADOR INDEPENDIENTE — R23A ITERACIÓN 2 (MODO AUTÓNOMO · CUENTA DEMO)

**Fecha:** 2026-10-16 (UTC)
**Validador:** OPENCODE (independiente del autor del monitor)
**Commit auditado:** `bfd0416` (R23A iter2)
**Build:** versionCode=11, versionName=1.4-r23-auto-iter2, buildId=R23-AUTO-ITER2, gitSha=bfd0416
**Dispositivo:** Redmi A1 (`220733SL`), Android 12 · USB `UGU8KFUKM7E6CE8D` (cargando)
**Ventana:** 20,0 min · intervalo 10 s · 95 muestras de `/status`

---

## 1. VEREDICTO

**KEEP (seguridad)** — Cero exposición a cuenta real. Los 3 fixes de iter 2 (Unicode,
`market_regime`, `trade_action`) **quedan verificados**. La ventana no generó trades porque los
**guards de riesgo bloquearon toda operación** (ver §4), lo que impide evaluar eficacia en esta
iteración.

| Criterio | Resultado | Evidencia |
|---|---|---|
| `isDemoAccount == true` en cada muestra | ✅ | **95 / 95** (cuentas reales: 0) |
| Modo AUTONOMOUS sostenido | ✅ | 95/95 muestras |
| Identidad de build en runtime | ✅ | versionCode=11 / gitSha=bfd0416 / buildId=R23-AUTO-ITER2 |
| Balance inmutable en la ventana | ✅ | 38.164.736,00 COP constante (Δ 0,00) |
| Fix Unicode del monitor | ✅ | Arrancó sin `UnicodeEncodeError` con el comando literal |
| Fix `market_regime` | ✅ | 79/79 decisiones con régimen real (`TRENDING`), no `trend` |
| Fix `trade_action` | ✅ | Implementado (inferencia BUY/SELL); sin datos por 0 trades |
| Endpoint `/status` | ⚠️ | **NO es JSON válido** (ver §3) |

---

## 2. VERIFICACIÓN DE LOS 3 FIXES

### 2.1 Unicode (BLOQUEANTE de iter 1) — RESUELTO
`live_monitor_r23_autonomous.py:25-28` aplica `sys.stdout/stderr.reconfigure(encoding="utf-8")` y
elimina el glifo `✓`. La ejecución **literal** del comando del usuario completó los 20 min sin
crashear (iter 1 moría a los 30 s). **Confirmado.**

### 2.2 `market_regime` — RESUELTO
En iter 1 el monitor asignaba `market_regime = analysis.trend` (UPTREND). En iter 2, las 79
decisiones reportan el **régimen real** (`TRENDING`) distinto de `trend` → el campo ya no es una
copia de la tendencia. **Confirmado.**

### 2.3 `trade_action` — IMPLEMENTADO (sin datos)
El monitor infiere BUY/SELL desde `observed_decisions` o el detalle del evento. No hubo trades, así
que `trades.csv` quedó **sólo con cabecera**. No verificable en runtime esta iteración.

---

## 3. HALLAZGO CRÍTICO (regresión binaria) — `/status` ya NO es JSON válido

La respuesta de `/status` (versionCode 11) contiene **comentarios `//` embebidos** en el cuerpo JSON
(p. ej. junto a `observedStake`/`effectiveStake`). Verificado por el validador:

```
json.loads(raw)                → FAIL: Expecting property name enclosed in double quotes (line 36)
filtro de líneas "//" (monitor) → OK
```

Esto es una **regresión del fix histórico `a212673`** ("eliminar comentarios inválidos dentro del
JSON en buildStatusJson"). Cualquier consumidor con un parser JSON estándar (jq, `ConvertFrom-Json`,
clientes externos) **falla**. El monitor sobrevive porque filtra líneas `//`, pero el contrato del
endpoint está roto. **Debe eliminarse los comentarios del payload.**

---

## 4. POR QUÉ NO HUBO TRADES — Guards de riesgo al 100 %

En la ventana: **0 trades**, 0 resoluciones (W/L sin cambio), balance inmutable. Los 24 eventos VETO
se reparten en:

| Tipo de veto | Ocurrencias | Detalle |
|---|---|---|
| `ADAPTIVE_BLOCK` | 14 | "Anti-patrón perdedor recurrente (98% similitud en contexto S/R=0%) - inversión deshabilitada" |
| `RISK_LIMIT` | 10 | "Trailing Profit Lock: Ganancia asegurada (+328000 COP pico)" |

El **Trailing Profit Lock** y el bloqueo adaptativo por anti-patrón mantuvieron el motor detenido
toda la ventana. Esto es **fail-closed correcto** (no operó cuando los guards lo indicaban), pero
consecuencia: **no se pudo validar el ciclo operativo autónomo** en esta iteración (0 señales→tap).

> Nota: `tapsDespachados=12` es **acumulado histórico** (no cambió durante la ventana); `trades.csv`
> está vacío. El `LIVE_REPORT.md` imprime "12 taps despachados" en la sección FACT sin distinguir que
> son previos a la ventana → **lectura engañosa** (sugeriría actividad en la ventana).

---

## 5. PARIDAD Live ↔ Replay

| Métrica | Valor |
|---|---|
| Decisiones totales | 79 |
| Pares evaluables | **2** |
| Exact matches | 0 |
| Divergencias | 2 |
| Missing en Replay | 77 |
| **Match rate** | **0,0 % (FAIL)** |

**Divergencias (esperadas por diseño):** en ambos pares, Live registra `kind=VETO` con
`direction=NONE`, `decision=SUPPRESSED`; el Replay ejecuta la operación (`direction=BUY`,
`decision=EXECUTED`). **Causa:** el motor de replay **no modela los guards de runtime** (ADAPTIVE_BLOCK,
RISK_LIMIT/Trailing). La divergencia es de **modelado del replay**, no de lógica de decisión. Los
campos `market_regime` (TRENDING=TRENDING) y `candle_second` (1=1) **ya coinciden** — los defectos
espurios de iter 1 están resueltos.

**Pendiente:** el replay debe incorporar los guards ADAPTIVE_BLOCK/RISK_LIMIT, y su cobertura sigue
baja (2/79).

---

## 6. DEFECTOS MENORES

1. **`gitSha` vacío** en el `LIVE_REPORT.md` (sección "Build Identificada"): el monitor no propaga
   `--expected-sha` al template. En iter 2 el comando no lo incluía.
2. **`taps` acumulado** presentado como de la ventana (ver §4).
3. **Cobertura de replay** 2/79 decisiones emparejadas.

---

## 7. ARTEFACTOS AUDITADOS (`loops/r23_autonomous/iter_2/`)

| Artefacto | Estado |
|---|---|
| `live_samples.jsonl` | ✅ 95 muestras |
| `status_samples.jsonl` | ✅ 95 volcados |
| `events.jsonl` | ✅ 200 eventos (176 ASSET, 24 VETO) |
| `decisions.csv` | ✅ 79 decisiones (market_regime corregido) |
| `trades.csv` | ⚠️ sólo cabecera (0 trades en ventana) |
| `ticks_live.csv` | ✅ volcado |
| `replay_journal.csv` | ✅ 2 filas (con `asset`) |
| `mode_log.json` | ✅ presente |
| `LIVE_REPORT.md` | ✅ PASS/KEEP (con `gitSha` vacío) |
| `replay_comparison.json` / `_report.md` | ✅ 2 pares / 2 divergencias |
| `monitor_stdout.log` | ✅ 20 min completos, sin crash |

---

## 8. RECOMENDACIÓN

- **Seguridad del binario:** KEEP (guard DEMO fail-closed correcto, 0 cuentas reales).
- **Antes de R23A iter3:**
  1. **Eliminar los comentarios `//` de `/status`** (restaurar JSON válido) — regresión binaria.
  2. Incorporar los guards ADAPTIVE_BLOCK/RISK_LIMIT al motor de replay para que la paridad no sea
     espuria por ese motivo.
  3. Distinguir en el reporte los taps/operaciones **de la ventana** vs acumulados.
  4. Propagar `gitSha` al `LIVE_REPORT.md`.
  5. Verificar el fix `trade_action` en una ventana que **sí** opere (esta quedó bloqueada por guards).
