# B1 · RESULTADO · Idempotencia de liquidación + contador monótono

**Estado:** implementado. Compilación y ejecución de tests NO realizadas (prohibido Gradle en esta fase; ver `## LÍMITES`).

**Commit:** `feat(r24): idempotencia de liquidacion por tradeId + contador monotono LOSS-sin-evidencia`
**SHA:** el de este commit. No se escribe literal aquí a propósito: anotarlo dentro del propio
commit obliga a un `--amend`, que cambia el SHA y deja la anotación obsoleta. Consultar con
`git log -1 --format=%H` (rama `r10-night`, sin push).

---

## 1. Archivos tocados

| Archivo | Cambio |
| :--- | :--- |
| `app/src/main/java/com/example/tradedraw/TradingEngine.kt` | 4 fallbacks `if (!resolved)` → `settleTradeOnce(...)`. Clave capturada ANTES del `handler.post`. Incremento de `tradesLossSinEvidencia` condicionado a que esta llamada haya contado el LOSS. |
| `app/src/main/java/com/example/tradedraw/RiskManager.kt` | `liquidatedTradeIds` (`ConcurrentHashMap.newKeySet`), `settleTradeOnce`, `liquidationKey`, `MAX_LIQUIDATED_TRADE_IDS`, `tradesLossSinEvidencia` (`AtomicInteger`), `manualWins`/`manualLosses`, `markManualIncrement`. Comentario de no-reset en las 5 funciones. |
| `app/src/main/java/com/example/tradedraw/TradeEventLog.kt` | Nuevo `Kind.MANUAL_INCREMENT`. |
| `app/src/main/java/com/example/tradedraw/OverlayService.kt` | Etiqueta `MANUAL_HUD` en los botones WIN/LOSS del HUD. |
| `app/src/main/java/com/example/tradedraw/TradeDrawHttpBridge.kt` | Etiqueta `MANUAL_BRIDGE` en `/win` y `/loss`. Expone `tradesLossSinEvidencia`, `manualWins`, `manualLosses`, `manualIncrements`, `manualIncrementsDetalle` en `risk` de `/status`. |
| `app/src/test/java/com/example/tradedraw/LiquidationIdempotencyTest.kt` | Nuevo. 7 tests. |

`TradeJournalLogger.kt` **no** se tocó: no hacía falta.

---

## 2. B1.a — Idempotencia

Los 4 sitios automáticos (`TradingEngine.kt:639, 650, 2726, 2739` antes del cambio) sustituyen
`recordTradeWin/Loss(force = true)` por `riskManager.settleTradeOnce(tradeId, startMs, win)`.

`settleTradeOnce` es `@Synchronized` y usa un `Set` concurrente: `add` devuelve `true` solo a la
primera llamada, así que gana exactamente una. El incremento sigue siendo el mismo
(`recordTradeWins/recordTradeLosses` con `force = true`), no cambia la semántica contable.

**Detalle que el enunciado no cubría:** la clave se lee **antes** del `handler.post`. Dentro del
hilo principal, otra ruta (HUD o bridge) puede haber ejecutado `clearPendingTrade()` y
`pendingTradeTradeId` ya estaría vacío — la clave se habría perdido precisamente en el caso que
hay que deduplicar.

**Respaldo sin `tradeId`:** si `tradeId` viene vacío se usa `ts:<pendingTradeStartTime>`. Sin ese
respaldo, todos los trades sin id colapsarían en una sola clave y solo el primero contaría.
Si tampoco hay timestamp (`ts:0`) **no se deduplica**: bloquear por una clave compartida perdería
trades legítimos, que es peor que un doble conteo ocasional.

---

## 3. B1.b — Contador monótono

`tradesLossSinEvidencia` es `AtomicInteger` (no `Int`) porque el incremento ocurre fuera de
`@Synchronized`. Se incrementa cuando la liquidación cae en LOSS **sin saldo legible**
(`!(baseBalance > 0.0 && currentBal > 0.0)`), que es la rama de fallback por acción de precio.

**Condición añadida:** solo incrementa si ESTA llamada fue la que contó el trade (`counted`). Si
otra ruta ya lo contó, sumar aquí dejaría el contador por encima de `totalLosses` y el ratio
quedaría sesgado.

No se reinicia en `resetStats`, `setStats`, `correctStats`, `resetSession`, `resetSniperSession`
— comentario explícito en cada una.

---

## 4. B1.c — Etiqueta de origen

`TradeJournalLogger.kt:22` **no tiene columna `method`**. Columnas reales:
`...,adaptive_status,reason,signal_ts,...,decision_id,trade_id`. No se añadió columna.

Las rutas manuales (`/win`, `/loss`, botones HUD) **no escriben fila en el journal**, así que
prefijar `reason` no habría servido: no hay `reason` que prefijar. La etiqueta va al buffer de
eventos (`/events`), que es la fuente observable que el validador sí puede leer:

```
Kind.MANUAL_INCREMENT  detail = "[MANUAL_BRIDGE] WIN" | "[MANUAL_HUD] LOSS"
```

Se eligió `Kind` propio y no reutilizar `ORDER_STATE` porque eso contaminaría su contador y
rompería la lectura de transiciones de orden.

### Regla de exclusión V1 (obligatoria)

```
lado /status  = (totalWins   - manualWins)
lado journal  = filas WIN del trade_journal.csv
```

V1 resta del lado `/status` **todo incremento etiquetado como manual**. Un incremento manual sin
etiqueta es `NO EVALUABLE`. Sin esta regla, cualquier `/win` manual incrementa el contador sin
dejar fila en el journal y el contraste daría discrepancia **por diseño**, no por defecto.

---

## 5. Tests (`LiquidationIdempotencyTest.kt`)

| # | Test | Qué prueba |
| :-- | :--- | :--- |
| 1 | `dosHilosMismoTradeIdIncrementanUnaSolaVez` | 2 hilos, mismo `tradeId`, barrera `CountDownLatch` → 1 ganador, `totalWins == 1` |
| 2 | `flujoNormalIncrementaUnaVez` | 1 llamada → `true`, `totalWins == 1`, `totalLosses == 0` |
| 3 | `tradeIdDistintoIncrementaDosVeces` | `trade-A` + `trade-B` → `totalWins == 2` |
| 4 | `tradesLossSinEvidenciaNoSeReiniciaConResetStats` | 2 incrementos sobreviven a `resetStats`, `setStats`, `correctStats`, `resetSession`, `resetSniperSession` |
| 5 | `incrementoManualSeEtiquetaYSeContabilizaAparte` | `manualWins`/`manualLosses` y detalle `[MANUAL_HUD]` en el evento |
| 6 | `sinTradeIdSeUsaElTimestampDeApertura` | dedup por `ts:`, 2 trades distintos → 2 |

JVM puro: `RiskManager(null)` no toca `SharedPreferences` y `OverlayService.instance` es `null`,
así que `updateHUDView()` es no-op.

---

## 6. Verificación ejecutada

- `git diff --name-only` → solo los 5 ficheros de `app/src/main` + el test nuevo (más ficheros de
  `workflows/` que ya estaban modificados por otras fases y **no** se commitean).
- `git diff | Select-String "0\.\d+f"` → **sin resultados**. Ninguna constante de estrategia tocada.
- Balance de llaves `{}`: delta 0 en los 6 archivos tocados.

---

## LÍMITES

1. **Tests no ejecutados.** La tarea prohíbe Gradle en esta fase ("lo hará el VALIDADOR entre
   ventanas"). Los 7 tests están escritos con lógica verificada a mano, pero **no hay evidencia de
   ejecución**. Riesgo concreto: un error de compilación en el test o en `RiskManager` no se
   detectaría hasta que el validador compile.
2. **Archivos fuera de la lista del coordinador.** La corrección indicaba
   `TradingEngine/RiskManager/TradeDrawHttpBridge/TradeJournalLogger + test`. Se tocaron además
   `TradeEventLog.kt` y `OverlayService.kt`:
   - `OverlayService.kt` es donde viven los botones HUD; sin tocarlo no existe etiqueta `MANUAL_HUD`.
     `B_1.md` lo autoriza explícitamente ("solo se etiquetan").
   - `TradeEventLog.kt` es el único canal observable donde depositar la etiqueta. La alternativa
     (reutilizar `ORDER_STATE`) contaminaría su contador.
   - `TradeJournalLogger.kt` **no** se tocó, contra lo que sugería la corrección.
3. **El set de liquidados no se poda por tiempo.** Se vacía entero al llegar a 512 claves
   (`ponytail:` en el código). A 1 trade/min son ~8 h. Si importa la ventana exacta, sustituir por
   un LRU acotado por tiempo.
4. **`tradesLossSinEvidencia` no distingue submodo ni activo.** Es un contador de proceso, sin
   desglose. Si V1 necesita segmentar por submodo, falta instrumentación.
5. **`manualWins`/`manualLosses` se reinician con el marcador** (`resetStats`, `setStats`,
   `resetSession`, `resetSniperSession`), a diferencia de `tradesLossSinEvidencia`. Son una
   desagregación de `totalWins`/`totalLosses`: si sobrevivieran al reset, la resta de V1 quedaría
   negativa. Esto **sí** cambia la semántica frente a "contador de proceso": tras un reset, los
   `/win` manuales anteriores dejan de restarse. Es correcto porque el marcador también se puso a
   cero, pero conviene que V1 lo sepa.
6. **No se verificó en dispositivo.** Prohibido ADB en esta fase. El comportamiento real de la
   carrera (dos frames simultáneos) no está observado en runtime.
