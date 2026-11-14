# HALLAZGOS DEL VALIDADOR INDEPENDIENTE — R23 ITERACIÓN 2 (SHADOW MODE)

**Fecha:** 2026-10-15 (UTC)
**Validador:** OPENCODE (independiente del autor del monitor)
**Commit auditado:** `101a183` (R23 iter2)
**Build:** versionCode=7, versionName=1.4-r23-iter2, buildId=R23-ITER2, gitSha=101a183
**Dispositivo:** Redmi A1 (`220733SL`), Android 12 · serial `[fd10:d508:34cf:10:e8c5:80ff:fef0:d5f9]:5555`
**Ventana:** 20,0 min · intervalo 10 s · 116 muestras de `/status`

---

## 1. VEREDICTO

**KEEP** — Sin regresión de seguridad; el endpoint nuevo es correcto.

| Criterio | Resultado | Evidencia |
|---|---|---|
| Modo SHADOW sostenido | ✅ | `mode=SHADOW` inicio y fin de ventana |
| `tapsDespachados == 0` (estricto) | ✅ | 116/116 muestras = 0 |
| Balance inmutable | ✅ | 38.054.735,36 COP constante (único valor en la ventana) |
| Identidad de build en runtime | ✅ | versionCode=7 / gitSha=101a183 / buildId=R23-ITER2 |
| Salud | ✅ | `READY` de principio a fin |
| Órdenes shadow aprobadas | 0 | 17 decisiones, todas vetadas por V2_RANGE |
| Endpoint `/shadow_journal` | ✅ | Responde `{"success":true,"total":0,...}` (tail `?max=N` operativo) |

---

## 2. CONTROL NEGATIVO — Correlación `volatilidadSuficiente=false` ↔ VETO V2

**Resultado: CONSISTENTE (correlación perfecta en la ventana).**

| Métrica | Valor |
|---|---|
| Muestras con `volatilidadSuficiente=false` | **116 / 116** (true = 0) |
| Umbral `rangoRelativoMinimo` observado | `1.00e-05` (constante) |
| Incoherencias `volatilidadSuficiente ≠ (rangoRelativoActual ≥ umbral)` | **0** |
| Eventos `VETO` V2 en `events.jsonl` | **9** |
| Vetos V2 legítimos (`rango < umbral`) | **9 / 9** |
| Vetos V2 ilegítimos (`rango ≥ umbral`) | **0** |
| Rango observado en vetos | `2,03e-10 … 4,36e-10` |
| `vetoV2` (acumulado) | 8 → 17 (Δ = 9 en ventana) |
| `tapsDespachados` | 0 |

**Interpretación:** no se observó ni un solo V2 con volatilidad suficiente, ni ninguna aprobación shadow
con volatilidad insuficiente. La rama de permiso (volatilidad suficiente ⇒ se permite shadow order)
**quedó sin ejercitar** porque Crypto IDX (`Z-CRY/IDX`) mantuvo rango relativo ~1e-10 ≪ 1e-05 durante
los 20 minutos completos.

> **MISSING PROOF (heredado de iter 1):** el control negativo sólo cubre una dirección. Para cerrar
> el bucle falta observar la rama positiva en un activo volátil (ETHUSD-OTC/BTCUSD-OTC) donde
> `rangoRelativoActual ≥ 1e-05` y se emita el primer `SHADOW_DECISION` aprobado.

---

## 3. PARIDAD Live ↔ Replay

- El comparador **sí se invocó** con los argumentos correctos en esta iteración (corrección de `2ef2b72`).
- Resultado real: **17 decisiones, 0 pares evaluables, 17 MISSING_REPLAY, 0,0 % de paridad**.
- `LIVE_REPORT.md` ahora reporta correctamente "NO EVALUABLE (0 pares evaluados, 17 decisiones sin
  replay correspondiente)" — sin el falso 100 % de iter 1.
- **Pendiente estructural:** falta una fuente de replay que contenga los mismos `decision_id`.
- **Cosmético detectado:** en la sección 7 del `LIVE_REPORT.md` el texto se duplica
  ("Paridad Live ↔ Replay: Paridad Live ↔ Replay: …") porque `replay_report_text` ya incluye el
  prefijo. No afecta al veredicto.

---

## 4. ARTEFACTOS AUDITADOS (`loops/r23/iter_2/`)

| Artefacto | Estado |
|---|---|
| `live_samples.jsonl` | ✅ 116 muestras |
| `status_samples.jsonl` | ✅ 116 volcados de `/status` |
| `events.jsonl` | ✅ 200 eventos (191 ASSET, 9 VETO) |
| `decisions.csv` | ✅ 18 líneas (1 cabecera + 17 decisiones) |
| `mode_log.json` | ✅ presente |
| `shadow_journal.json` | ✅ `total: 0` |
| `LIVE_REPORT.md` | ✅ veredicto PASS/KEEP |
| `replay_comparison.json` / `replay_comparison_report.md` | ✅ generados (0 pares) |
| `monitor_stdout.log` | ✅ traza completa |

---

## 5. RECOMENDACIÓN

- **Binario:** KEEP.
- **Iteración 3:** (a) ejercitar la rama positiva del guard (activo volátil) para obtener el primer
  `SHADOW_DECISION` real; (b) dotar al replay de `decision_id` para que la paridad sea medible;
  (c) corregir la duplicación de texto en la sección 7 del reporte.
