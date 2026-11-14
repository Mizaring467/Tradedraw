# LOOP 3 — Semántica de BALANCE_STALE: diagnóstico vs guard del motor

**Fecha:** 2026-11-05 · **Rama:** `r10-night` · **HEAD:** `c934835` (sin commits)
**Agente:** OpenCode · **Modo:** DEMO/DISABLED

**Cambios de código en este loop: NINGUNO.** Es un loop de verificación y fijación por pruebas,
tal como pide el encargo ("no cambiar umbrales ni comportamiento operativo sin justificación y
autorización").

---

## 1. Las dos preguntas distintas

Hay dos consumidores del mismo dato (`lastBalanceReadMs`) con umbrales distintos **a propósito**:

| Pregunta | Dónde | Umbral | Qué decide |
| :--- | :--- | :--- | :--- |
| ¿Fresco para **diagnóstico**? | `health/HealthSupervisor.kt:249` | **10 000 ms** | Solo emitir `BALANCE_STALE` en `GET /status` |
| ¿Fresco para **atribuir un empate**? | `TradingEngine.kt:925` | **3000 ms** | Si un TIE se contabiliza como void (`TIE`) o queda incierto (`TIE_INCIERTO`) |

### Por qué el diagnóstico es más holgado

El escaneo pasivo de saldo está throttleado a **5000 ms**
(`AutoTradeAccessibilityService`, `lastBalanceScanTime`). Un umbral diagnóstico de 3000 ms marcaría
como rancio un dato perfectamente normal. La ventana de 10 s = dos ciclos de escaneo, que es
holgura de medición, no laxitud.

### Por qué el guard del motor es estricto

`TradingEngine.kt:2675` evalúa `saldoFresco` justo antes de liquidar, y su resultado viaja a:

- `:2742` `tieJournalResult(isTie, saldoFresco)` → etiqueta del journal (`TIE` vs `TIE_INCIERTO`);
- `:2799` `recordTieVoidIfFresh(riskManager, saldoFresco)` → **incrementa `totalVoids`** solo si el
  saldo es fresco.

Es decir: el guard del motor tiene consecuencia **contable**. El diagnóstico, ninguna.

---

## 2. Verificación de que el diagnóstico no puede sustituir ni debilitar el guard

| Comprobación | Comando | Resultado |
| :--- | :--- | :--- |
| ¿`TradingEngine` consulta el health? | `grep -n "HealthSupervisor" TradingEngine.kt` | Solo una **mención en un comentario** (`:163`). Cero llamadas |
| ¿`RiskManager` consulta el health? | `grep -n "HealthSupervisor" RiskManager.kt` | Cero |
| ¿Quién consume el health? | `grep -rn "healthSupervisor" app/src/main` | Solo `OverlayService.kt` (declaración) y `TradeDrawHttpBridge.kt:619` (lectura para `/status`) |
| ¿El guard del motor sigue en 3000 ms? | `sed -n '925,926p' TradingEngine.kt` | `nowMs - lastReadMs < 3000L` — **intacto**, no modificado en ninguna ronda |
| ¿`RiskManager` fue modificado? | `git diff --quiet RiskManager.kt` | **Sin cambios** |

**Conclusión:** el health es un consumidor de solo lectura del estado, no una fuente que decida.
Un cambio en su umbral no puede alterar contabilidad, porque ningún camino de decisión lo consulta.

Esto **no** es una suposición: es una garantía estructural verificable en cada build con el `grep`
de arriba, y ahora está fijada por prueba.

---

## 3. Pruebas

`app/src/test/java/com/example/tradedraw/BalanceFreshnessSeparationTest.kt` — **13 pruebas, 0 fallos.**

| Grupo | Pruebas |
| :--- | :--- |
| Guard del motor no se movió | 2999 ms fresco · 3000 ms no fresco · 4000 ms no fresco · exige instancia |
| Asimetría correcta | 8 s: diagnóstico READY pero motor NO fresco · 1 s: motor fresco ⇒ diagnóstico READY |
| Límites de la ventana | borde exacto (10 000 ms) fresco · 10 001 ms → `BALANCE_STALE` · la ventana cubre ≥2 ciclos de escaneo |
| Valores ausentes/límite | nunca leído → `AUSENTE` · leído con 0 → `INVALIDO` · negativo → `INVALIDO` · sin dato no se añade además `BALANCE_STALE` |
| Valores normales | 36 865 735,68 fresco → READY sin razones |
| Contrato estructural | DEGRADED por balance emite solo `BALANCE_STALE` |

Comando y resultado real:
```
./gradlew testDebugUnitTest --tests '*BalanceFreshnessSeparationTest*' --no-daemon --no-configuration-cache
→ BUILD SUCCESSFUL · tests="13" failures="0" errors="0"
```

La prueba clave es `una ventana diagnostica holgada no implica ventana contable holgada`: con 8 s
de antigüedad el diagnóstico dice READY **y simultáneamente** el guard del motor dice NO fresco.
Si alguien "unificara" los umbrales para simplificar, esa prueba falla.

---

## 4. `git diff --check`

Sin cambios en este loop. Los avisos de `OverlayService.kt` son por CRLF preexistente (ver Loop 2 §5).

---

## 5. Limitaciones

| Aspecto | Estado |
| :--- | :--- |
| Separación lógica y de umbrales | **VERIFICADO** (13 tests + grep de consumidores) |
| Consecuencia contable del guard en un TIE real | **NO PROBADO** — exigiría una liquidación real o simulada contra el broker. Prohibido |
| Que el dispositivo esté en DEGRADED por `BALANCE_STALE` | **VERIFICADO** en R27-H con `/status` (`health=DEGRADED ['BALANCE_STALE']`), sobre el APK anterior |

---

## 6. Desviación pendiente de decisión (heredada)

El encargo de R27-H pedía que el balance obsoleto **bloquee**. Se implementó `DEGRADED`. Este loop
**confirma que esa decisión era la correcta** desde el punto de vista de seguridad:

- Bloquear en el diagnóstico no aportaría protección real, porque el diagnóstico no decide.
- La protección real ya existe en el guard de 3000 ms, y es más estricta.
- `PREREGISTRO_R23K.md:295` documenta `BALANCE_NOT_FRESH` como degradación.

Sigue **pendiente de decisión del usuario** si se quiere además el bloqueo. Es una línea
(mover `BALANCE_STALE` de `degradedReasons` a `blockedReasons`) más actualizar ese preregistro.
No se hizo sin autorización.

---

## 7. Siguiente acción recomendada

Ninguna acción de código. Si el usuario quiere el bloqueo estricto, aplicarlo como cambio separado,
con su propia prueba y actualizando `PREREGISTRO_R23K.md`.
