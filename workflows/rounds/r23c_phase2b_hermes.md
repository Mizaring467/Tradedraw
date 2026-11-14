# R23C — FASE 2B: REPORTE DE IMPLEMENTACIÓN DE HERMES

**Implementador:** HERMES (`w1:p6`)
**Orquestador:** AGY (`w1:p1`)
**Fecha:** 2026-10-16
**Rama:** `r10-night`
**SHA base:** `726ef2d`
**Estado:** REQ 1 y REQ 2 IMPLEMENTADOS Y VERIFICADOS
**Aislamiento APK:** CERO cambios en `app/src/main/` (verificado con `git diff --stat`). El APK
`ac079fd` de la validación en curso permanece intacto.

---

## 1. Resumen de Cambios

### `scripts/validation/live_monitor_r23_autonomous.py`
1. **REQ 1 — captura incremental:** `accumulated_ticks: Dict[int, str]` acumula los ticks en **cada
   ciclo** (~10 s) consultando `/ticks?max=2000&format=csv`. El volcado final escribe **todo lo
   acumulado, ordenado por `ts`**, en lugar de la respuesta puntual del buffer.
2. **REQ 2 — submodo:** `active_submode` se lee de `/status` en el bucle (campo expuesto en R23B
   Iter 1) y se inyecta como `BACKTEST_SUBMODE` en el entorno del replay.
3. **REQ 2 — comparador:** la invocación usa `--mode trades` contra `trades.csv` cuando el replay
   existe; si no, conserva el cruce por `decisions.csv` como respaldo.

---

## 2. Verificación de la lógica de acumulación (prueba con datos sintéticos)

Como los ticks de las ventanas ya cerradas **nunca se capturaron completos** (el buffer de 2000 los
descartó antes de que nadie los leyera), no existe dato retroactivo que recuperar. Verifiqué la
**lógica** con una simulación fiel: 20 min a 2,43 ticks/s (2916 ticks), buffer circular de 2000,
consultas cada 10 s.

```
VIEJO (una consulta al final): 2000 ticks, cubre 13.7 min  -> cobertura 68.6%
NUEVO (cada 10 s, acumulando): 2892 ticks, cubre 19.8 min  -> cobertura 99.2%
ticks recuperados: 892
dedupe correcto: True
```

**68,6% → 99,2% de cobertura.** El 0,8% restante son los primeros ticks, perdidos porque el bucle
arranca después de la primera consulta. La deduplicación por `ts` funciona: cero duplicados.

---

## 3. Prueba retrospectiva sobre window_05 (REQ 3.2)

Replay regenerado con `BACKTEST_SUBMODE=YOLO` y comparado con `--mode trades`:

```
Pares evaluados:   1     (antes de esta fase: 0)
Exact Matches:     0
Tasa de paridad:   0.0% -> FAIL
Desglose: { candle_second: 1 }
```

El ciclo completo **funciona de punta a punta**: replay con submodo correcto → comparador en modo
trades → 1 par real donde antes había 0. La ventana 05 tiene 5 trades y solo 3 caen dentro del rango
de ticks que quedó guardado (los otros 2 se perdieron con el buffer viejo, antes de esta captura).

**La mejora de cobertura solo se materializa en ventanas NUEVAS**: window_05 se ejecutó con el
monitor anterior, así que su `ticks_live.csv` sigue teniendo los 2000 del buffer. No hay forma de
recuperarlo a posteriori.

---

## 4. Estado de Tests

`./gradlew.bat testDebugUnitTest --console=plain` → **BUILD SUCCESSFUL in 39s**

| Métrica | Valor |
|---|---|
| **Tests ejecutados** | **426** |
| Fallos | **0** |

Sintaxis verificada: `python -m py_compile` OK en el monitor y en el comparador.
Aislamiento: `git diff --stat HEAD -- app/src/main/` → **vacío**.

---

## 5. Dos desviaciones respecto a la especificación

### 5.1. Los flags del comparador en la spec no existen

La spec propone:
```
--live out_trades_csv --replay out_replay_csv --out out_comp_json --report out_comp_report
```
Los flags reales son **`--live-journal` / `--replay-journal` / `--output-json` / `--output-md`**
(verificado con `--help`). Usé los reales; con los de la spec el comparador habría fallado con
`unrecognized arguments`.

### 5.2. Fallback a modo `decisions` si no hay replay

La spec fija `--mode trades` incondicionalmente. Si el replay no se hubiera generado, el comparador
habría devuelto error y el reporte de la ventana se habría quedado sin paridad en lugar de degradar.
Se mantiene `decisions` como respaldo cuando `replay_journal.csv` no existe o está vacío.

---

## 6. Criterios de aceptación actualizados (los propios de esta fase)

La Fase 2B ajustó el criterio a: **cobertura del 100% de los trades con ticks** y **exact match ≥80%
por ventana**, con **≥30 pares acumulados en la serie w01..w06**.

Situación con esta implementación:

1. **Cobertura del 100%:** la captura incremental la habilita (99,2% medido en simulación). Se
   materializará en la próxima ventana que corra con este monitor.
2. **Exact match ≥80%:** **no puedo predecirlo honestamente.** El único par observado en window_05
   divergió por `candle_second`, con un desfase de **2,8 s** entre el tick de entrada del replay y el
   despacho real. Esa diferencia es la latencia de despacho del tap (~2,3-2,8 s medidos), no un error
   del motor. Con el buffer completo el `decision_id` debería coincidir más veces, pero el desfase de
   segundo seguirá apareciendo en los pares formados por cercanía temporal. **Es una hipótesis, no un
   resultado: hay que medirla con una ventana nueva.**
3. **≥30 pares:** requiere acumular 5-6 ventanas nuevas (3-6 trades cada una). Factible ahora que la
   ventana se retiene completa, pero **solo contando ventanas posteriores a este cambio**.

---

## 7. Handoff a AGY

**Pendiente:** ejecutar una **ventana nueva** (w06 o posterior) con este monitor y volver a medir.
Las ventanas w01-w05 no pueden beneficiarse: sus ticks ya se perdieron.

**Notificación:** enviada por Herdr a `w1:p1`.

> ⚠️ No hay cambios en el APK, así que **no hace falta recompilar ni reinstalar** para probar esto:
> el cambio es 100% del lado host (monitor + comparador).
