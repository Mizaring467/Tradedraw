# HALLAZGOS DEL VALIDADOR INDEPENDIENTE — R23B BASELINE (MODO YOLO · CUENTA DEMO)

**Fecha:** 2026-10-16 (UTC)
**Validador:** OPENCODE (`w1:pT`)
**Commit / Build auditado:** `bd7ef4e` (sin commit propio de R23B; HEAD de tarea `002cc6d`)
**Build:** versionCode=12, versionName=1.4-r23-auto-iter3, buildId=R23-AUTO-ITER3, gitSha=bd7ef4e
**Dispositivo:** Redmi A1 (`220733SL`, `UGU8KFUKM7E6CE8D`) · **USB perdido a mitad de ventana**
**Ventana efectiva:** ≈11,3 min (58/120 muestras) ⚠️

---

## 1. VEREDICTO

**KEEP (seguridad) — VENTANA TRUNCADA, baseline NO concluyente.**

| Criterio | Resultado | Evidencia |
|---|---|---|
| `isDemoAccount == true` en cada muestra | ✅ | **58 / 58** (reales: 0) |
| Modo AUTONOMOUS sostenido | ✅ | 58/58 muestras |
| Submodo YOLO activado | ✅ | `/submode?name=YOLO` → `{"success":true,"submode":"YOLO"}` |
| Balance inmutable en ventana | ✅ | 38.320.737,28 COP (Δ 0,00) |
| **Duración de ventana** | ❌ | **58/120 muestras** (corte de enlace a 676 s) |
| `events.jsonl` | ❌ | ausente (no volcado) |
| Paridad Live↔Replay | ❌ | no evaluable (`/ticks` falló) |
| Ciclo señal→tap→resolución | ⚠️ | 0 trades (guards), 0 resoluciones |

---

## 2. DEFECTOS EN LAS INSTRUCCIONES (tarea R23B)

El documento `workflows/rounds/r23b_yolo_baseline_task_opencode.md` §2 es **inejecutable tal cual**:

1. Cita `scripts/live_monitor_r23_autonomous.py` — **no existe**; el monitor real está en
   `scripts/validation/live_monitor_r23_autonomous.py`.
2. Usa flags inexistentes: `--duration`, `--interval`, `--out-dir`. El monitor real acepta
   `--duration-min`, `--interval-sec` y **no soportaba `--out-dir`** (lo añadí, cambio aditivo,
   para cumplir el requisito de directorio `loops/r23_yolo/baseline`).
3. Ejecutar literalmente habría fallado con `error: unrecognized arguments`.

---

## 3. HALLAZGO PRINCIPAL — El offset del `Trailing Profit Lock` explica el "baseline sin trades"

En la ventana, **74 señales** se generaron pero **100 % fueron vetadas**:

| Motivo | Ocurrencias | % |
|---|---|---|
| `RISK_LIMIT` — Trailing Profit Lock | **75** | 74,3 % |
| `ADAPTIVE_BLOCK` — Anti-patrón recurrente | **26** | 25,7 % |

**Interpretación (alineada con §2.3 de `R23B_YOLO_AUDIT.md`):** el `Trailing Profit Lock` tiene el
saldo en un estado de "ganancia asegurada" (pico +392.000 COP) que **veta toda operación aunque el
submodo sea YOLO**, porque el guard se evalúa **antes** de la bifurcación `YOLO` en `RiskManager`.
Consecuencia: **YOLO no puede expresar su mayor frecuencia de disparo** — el baseline queda dominado
por un guard de sesión, no por las relajaciones de estrategia que se querían medir.

> Esto **corrobora empíricamente** el hallazgo crítico de la auditoría §2.3: mientras el Trailing
> Profit Lock esté activo, el experimento de frecuencia de YOLO es **inobservable**. **Hipótesis 1
> candidata:** mover/restringir el Trailing Profit Lock bajo YOLO (o resetear el pico) para poder
> medir la frecuencia real.

---

## 4. INCIDENCIA DE INFRAESTRUCTURA — Enlace USB caído

- A los **676 s (11,3 / 20 min)** el enlace USB cayó: `UGU8KFUKM7E6CE8D offline` → luego `unauthorized`.
- Efectos: ventana truncada (58 muestras), `events.jsonl` no escrito, `/ticks` → `WinError 10061`,
  y el `LIVE_REPORT.md` autogenerado con `Evaluaciones motor: 0` (falso; el real fue 1303).
- El WiFi (`[fd10:...]:5555`) siguió `device`; el bridge se recuperó por WiFi.
- **Acción del usuario:** re-aceptar el diálogo de **Depuración USB** en el teléfono.
- El dispositivo seguía con **batería 39 %** y `USB powered: true`.

---

## 5. COMPARACIÓN CONTRA `AUTONOMOUS CONSERVATIVE` (R23A)

| Métrica | R23A (CONSERVATIVE) | R23B (YOLO, ventana truncada) |
|---|---|---|
| Duración | 20 min | **11,3 min** ⚠️ |
| Muestras | 95-117 | 58 |
| Señales | 225-405 | 74 (en 11,3 min → ritmo comparable) |
| Trades | 0-2 | 0 |
| Balance | cambió (R23A it1: Δ−17.999; it3: Δ0) | Δ0 |
| Veto dominante | ADAPTIVE_BLOCK / RISK_LIMIT | **RISK_LIMIT 74 %** |

**No hay base para comparar frecuencia YOLO vs CONSERVATIVE:** con el Trailing Profit Lock vetando el
100 %, ambos modos quedan bloqueados en este estado de saldo.

---

## 6. DEFECTOS SECUNDARIOS

1. **El submode (`YOLO`/`CONSERVATIVE`) NO se expone en `/status`** → no es auditable por telemetría;
   sólo se conoce por el ACK del endpoint `/submode`. Debería publicarse en `/status`.
2. **`LIVE_REPORT.md` autogenerado con métricas corruptas** tras el corte de conexión
   (`Evaluaciones motor: 0`, `Señales generadas: 0`): el monitor no distingue "status no leído" de
   "cero real". Debe marcarse como dato ausente, no como 0.
3. **`decisions.csv` usa `score=85.0` fijo** y **no incluye `dist_support`/`dist_resistance`** (ya
   reportado en R23A it3) → divergencias espurias si se compara.

---

## 7. ARTEFACTOS AUDITADOS (`loops/r23_yolo/baseline/`)

| Artefacto | Estado |
|---|---|
| `live_samples.jsonl` | ⚠️ 58 muestras (truncado) |
| `status_samples.jsonl` | ⚠️ 58 volcados |
| `decisions.csv` | ✅ 101 decisiones |
| `trades.csv` | ⚠️ sólo cabecera |
| `events.jsonl` | ❌ ausente |
| `ticks_live.csv` | ❌ ausente |
| `replay_comparison.json` / `_report.md` | ⚠️ 0 pares (sin replay) |
| `LIVE_REPORT.md` | ✅ **regenerado por el validador** (corrige las métricas del monitor) |
| `monitor_stdout.log` | ✅ traza (hasta 676 s) |

---

## 8. RECOMENDACIÓN

1. **Repetir el baseline** con enlace estable (recomendado **WiFi** `[fd10:...]:5555`, que sobrevivió)
   o tras re-autorizar el USB, para obtener los 20 min completos y `events.jsonl`.
2. **Hipótesis 1 (candidata):** el `Trailing Profit Lock` (evaluado antes de la bifurcación YOLO)
   anula la ventaja de frecuencia de YOLO; medir su impacto es requisito previo a cualquier cambio.
3. **Publicar el submode en `/status`** para auditabilidad.
4. Corregir el monitor para marcar datos ausentes en vez de `0` cuando la conexión cae.
5. Exportar `dist_support`/`dist_resistance` y confluencia real en `decisions.csv`.
