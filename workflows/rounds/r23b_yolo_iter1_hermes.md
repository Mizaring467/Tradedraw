# R23B — ITERACIÓN 1: REPORTE DE IMPLEMENTACIÓN DE HERMES

**Implementador:** HERMES (`w1:p6`)
**Orquestador:** AGY (`w1:p1`)
**Fecha:** 2026-10-15
**Rama:** `r10-night`
**SHA base:** `a40eb74` (`002cc6d` + tarea formal)
**Estado:** COMPLETADO — LISTO PARA BUILD/ADB DE AGY
**Objetivo:** Desbloquear los límites de SESIÓN en submodo YOLO y exponer `submode` en `/status`.

---

## 1. Resumen de Cambios

### A. `RiskManager.kt`
1. `canExecuteTrade`: nuevo `val effectiveSubMode = subMode ?: currentSubMode`, y **todos** los usos
   internos de `subMode` crudo unificados a `effectiveSubMode` (YOLO y SNIPER).
2. `sessionFloor` + `Trailing Profit Lock`: se omiten en YOLO (`!esYolo` en la condición del bloque).
3. `stopLossStreak`: se omite en YOLO.
4. **`currentSubMode` default: `YOLO` → `CONSERVATIVE`** (ver §2 — corrección de un fail-open latente).

### B. `TradeDrawHttpBridge.kt`
- `/status`: `"submode": "${te.autonomousSubMode.name}"` justo debajo de `"mode"`. Sin comentarios
  dentro del literal (el test de iteración 3 lo verifica).

### C. `app/build.gradle.kts`
| Campo | Antes | Después |
|---|---|---|
| `versionCode` | `12` | **`13`** |
| `versionName` | `"1.4-r23-auto-iter3"` | **`"1.4-r23-yolo-iter1"`** |
| `BUILD_ID` | `"R23-AUTO-ITER3"` | **`"R23-YOLO-ITER1"`** |

### D. Tests
- `RiskManagerTest`: +6 tests nuevos, 2 actualizados a la política nueva.
- `StatusJsonStrictSyntaxTest`: +1 test de `submode`.

---

## 2. HALLAZGO: la implementación literal del fallback creaba un fail-open

La especificación pide `val effectiveSubMode = subMode ?: currentSubMode`. Al implementarlo descubrí
que **`RiskManager.currentSubMode` tenía default `YOLO`**, mientras que
`TradingEngine.autonomousSubMode` tiene default `CONSERVATIVE`. Ese campo **no se sincroniza con el
motor en ningún punto del repo** (verificado: solo se escribe en tests).

Consecuencia si se dejaba como estaba: como `canTrade(confidence)` — que es la puerta que consume el
HUD — llama sin argumento, el fallback habría devuelto **YOLO** y, con la bifurcación nueva, toda
llamada sin submodo explícito habría quedado **sin límites de sesión**. Un desbloqueo silencioso por la
puerta trasera.

**Corregido:** default de `currentSubMode` = `CONSERVATIVE`. Sin argumento → se aplican **todos** los
guards (fail-safe). Los callers de producción ya pasan el submodo explícito
(`canExecuteTrade(autonomousSubMode)` en 5 sitios), así que el comportamiento real no cambia; lo que
cambia es que el caso sin argumento deja de ser peligroso.

Esto se detectó porque 4 tests existentes (`testBug2`, `testStopLossAndTakeProfitLimits`,
`testTrailingProfitLock`, `testSelectiveMartingaleM1`) empezaron a fallar de forma **colateral** — no
por la política nueva, sino por el fail-open. Con el default corregido vuelven a pasar sin tocarlos.

---

## 3. CONFLICTO DECLARADO: REQ 1.2 revierte una decisión previa del repo

REQ 1.2 pide que YOLO omita `stopLossStreak`. Eso **contradice directamente** dos tests que codificaban
la política contraria de forma explícita:

- `testBug1_StopLossIsTerminal` → *"Stop loss must be terminal and block trading even in YOLO"*
- `testFix1_canTradeReturnsFalseWhenLossStreakReachesStopLossInYolo` → *"canTrade() debe devolver false
  al alcanzar stopLossStreak en YOLO"*

El nombre `testBug1_...` indica que esto fue un **bug reportado y arreglado**: alguien decidió a
propósito que el stop loss de racha debía ser terminal incluso en YOLO. La especificación lo revierte.

**No lo decidí por mi cuenta: implementé la política que pediste** y **actualicé los 2 tests en lugar de
borrarlos**, afirmando ahora la conducta nueva en YOLO y la vieja en CONSERVATIVE, para que el cambio
quede fijado en ambos sentidos y no se pueda revertir por accidente.

**Lo que sigue protegiendo la cuenta en YOLO** (verificado por test):
| Guard | Estado en YOLO |
|---|---|
| Suelo de equity absoluto (20M COP) | **ACTIVO** — se evalúa antes que todo |
| Operación en curso (`hasPendingTrade`) | **ACTIVO** |
| Pausa anti-tilt tras fallo de martingala | **ACTIVO** (35 s) |
| Cooldown YOLO tras pérdida | **ACTIVO** |
| Martingala M1 selectiva (confianza ≥ 85%) | **ACTIVO** |
| Guard DEMO fail-closed (3 puertas de tap) | **ACTIVO** |
| `sessionFloor` / Trailing / `stopLossStreak` | **OMITIDOS** (el objeto de esta iteración) |

**Riesgo residual, dicho claro:** con estos tres omitidos, una racha larga en YOLO ya no se detiene por
pérdida de sesión ni por racha; el **único** freno de capital que queda es el suelo absoluto de 20M COP.
En cuenta DEMO es aceptable y es lo que se busca medir. Si YOLO se habilitara alguna vez fuera de DEMO,
el guard `puedeOperarAutonomoEnDemo` lo impediría — pero esa es la única cosa que lo impide, así que
conviene tenerlo presente.

---

## 4. Estado de Tests Locales

`./gradlew.bat testDebugUnitTest --console=plain --rerun-tasks` → **BUILD SUCCESSFUL in 1m 5s**

| Métrica | Valor |
|---|---|
| Clases de test | 43 |
| **Tests ejecutados** | **421** |
| Fallos | **0** |
| Errores | **0** |

Delta: **414 → 421** (+7 netos: +8 nuevos, y 2 reescritos sin cambiar el conteo… el neto real es +7
porque `RiskManagerTest` pasó de 46 a 52 y `StatusJsonStrictSyntaxTest` de 5 a 6).
BuildConfig: `VERSION_CODE=13`, `VERSION_NAME="1.4-r23-yolo-iter1"`, `BUILD_ID="R23-YOLO-ITER1"`, `GIT_SHA="a40eb74"`.

---

## 5. Verificación con parser externo

```
json.loads OK -> claves raiz: 20
mode = P | submode = P
comentarios // en el literal: []
```

`submode` está presente y el documento sigue siendo JSON estricto (la regresión de la iteración anterior
no se reintrodujo). Auditoría adicional: **ningún** uso de `subMode` crudo queda dentro de
`canExecuteTrade` — todos pasan por `effectiveSubMode`.

---

## 6. Cobertura nueva

### `RiskManagerTest`
| Test | Qué congela |
|---|---|
| `canExecuteTrade_yoloIgnoresTrailingProfitLockAndSessionFloor` | **el caso del baseline**: pico + retroceso → YOLO permite, CONSERVATIVE bloquea |
| `canExecuteTrade_yoloIgnoresSessionFloorStopLoss` | suelo de sesión (3%) omitido en YOLO, activo en CONSERVATIVE |
| `canExecuteTrade_yoloIgnoresStopLossStreak` | racha omitida en YOLO, activa en CONSERVATIVE |
| `canExecuteTrade_yoloRespectsAbsoluteEquityFloor` | **YOLO SÍ se detiene** bajo 20M COP |
| `canExecuteTrade_yoloStillRespectsAntiTiltAndPendingTrade` | anti-tilt y operación en curso siguen mordiendo |
| `elFallbackSinSubmodoEsFailSafe` | el default es CONSERVATIVE: sin argumento no se desbloquea nada |
| `testFix1_...` / `testBug1_...` | **reescritos**: YOLO omite racha / CONSERVATIVE la aplica |

### `StatusJsonStrictSyntaxTest`
| Test | Qué congela |
|---|---|
| `elStatusExponeElSubmodoActivo` | `submode` presente, es cadena, y `mode`/`strategy` siguen ahí |

---

## 7. Comportamiento Esperado para la Validación de OpenCode

**Trazabilidad:** `/status` → `versionCode=13`, `versionName="1.4-r23-yolo-iter1"`, `buildId="R23-YOLO-ITER1"`, y **`submode`**.

**Criterio de la ventana:** `submode` debe reportar **`YOLO`** durante toda la ventana (es lo que hace
atribuible el experimento). Si reporta `CONSERVATIVE`, la ventana no mide lo que dice medir.

**Cambio de comportamiento esperado (el objeto de la iteración):** los vetos
`RISK_LIMIT Trailing Profit Lock` y `Stop Loss Sesion Alcanzado` deben **desaparecer** del desglose en
YOLO. En el baseline eran 75/101 (74,3%). La frecuencia de disparo debe subir de forma medible
(baseline: 74 señales en 11,3 min, **0 taps**).

**Controles negativos que deben SEGUIR apareciendo:**
1. `ADAPTIVE_BLOCK` (~25,7% en el baseline): es anti-patrón del motor de aprendizaje, **no** un límite de
   sesión — no se relajó. Si desaparece, algo se tocó de más.
2. `Pausa Anti-Tilt tras fallo Martingala` y `Pausa de Cooldown YOLO`: activos.
3. `Stop Equity Absoluto Alcanzado`: solo debe aparecer si el saldo baja de 20M COP.
4. `Martingala M1 requiere setup A+`: activo cuando confianza < 85%.
5. Veto V1 y V2, ventana sniper y 1-orden-por-vela: **sin tocar**.
6. `isDemoAccount == true` en 100% de las muestras y **cero cuentas reales** (fail-closed intacto).

**A vigilar durante la ventana (nuevo riesgo):** al no existir `stopLossStreak` en YOLO, una racha
perdedora larga solo la corta el suelo absoluto de 20M COP. OpenCode debería registrar la **racha máxima
de pérdidas** observada y el drawdown desde el pico de sesión, porque son los indicadores que ahora
sustituyen a esos guards.

---

## 8. Handoff a AGY

**Pendiente de AGY:** build APK, ADB install (`220733SL`), activar YOLO
(`/submode?name=YOLO`), validación live ~20 min.

**Notificación:** enviada por Herdr a `w1:p1`.

> ⚠️ `GIT_SHA` se captura en config-time. Anclas inequívocas: `versionCode=13` + `buildId="R23-YOLO-ITER1"`.
> ⚠️ El cambio REVIERTE deliberadamente la política del `testBug1_StopLossIsTerminal`. Si esa decisión
> era firme y no debía tocarse, dímelo y la revierto: está aislada en una línea.
