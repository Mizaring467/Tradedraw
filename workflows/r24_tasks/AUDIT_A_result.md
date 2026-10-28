# AUDITORÍA FASE A · R24
**Auditor:** agente único, contexto limpio
**SHA auditado:** 6fc51f48be34b559cf12ddedab1f904d5e0f4fdb
**Fecha:** 2026-10-22

## Veredicto por punto

| # | Punto | Veredicto | Evidencia (comando + salida) |
| :--- | :--- | :--- | :--- |
| 1 | HEAD y estado del árbol | PASS | `git rev-parse HEAD` → `6fc51f48be34b559cf12ddedab1f904d5e0f4fdb`. `git status --short` → ` M workflows/R24_PROGRESO.md`, ` M workflows/r24_tasks/AUDIT_A_result.md`, `?? loops/r24_audit/`, `?? loops/r24_orch_verify/`, `?? workflows/r24_tasks/B_1.md`. Sin cambios en `app/`. |
| 2 | Historial reciente | PASS | `git log --oneline -8` → `6fc51f4`, `2f69563`, `82f7289`, `0737d90`, `fde4a07`, `70b1323`, `16e4f48`, `dd56136`. Coincide con lo declarado. |
| 3 | Contenido de `6fc51f4` | PASS | `git show --stat 6fc51f4` → 7 archivos, 500 inserciones / 8 borrados: `OverlayService.kt` (+4), `RiskManager.kt` (+146), `TradeDrawHttpBridge.kt` (11), `TradeEventLog.kt` (14), `TradingEngine.kt` (31), `LiquidationIdempotencyTest.kt` (+159), `workflows/r24_tasks/B1_result.md` (+143). Sí ataca la carrera: `settleTradeOnce` + `markLiquidated` por `tradeId`. |
| 4 | Idempotencia: mecanismo | PASS | `RiskManager.kt:357-374` `liquidationKey`/`markLiquidated` (set `liquidatedTradeIds`); `:382-387` `@Synchronized fun settleTradeOnce(...)` → `if (!markLiquidated(...)) return false`. Clave capturada antes del `post` en `TradingEngine.kt:624-625` y `:2678-2679`. |
| 5 | Guardia de exclusión entre rutas automáticas | PASS | `TradingEngine.kt:200` `isTradeResolving`; CAS en `:615` (visión) y `:2660` (headless), reset en `:670` y `:2766`. Una sola ruta automática liquida por frame. |
| 6 | Bloque CAUTION en informe R23K | PASS | `Get-Content workflows/rounds/R23K_FINAL_REPORT.md -TotalCount 25` → `> [!CAUTION] **El Cubo 2 es EXPLORATORIO, no confirmatorio.**` presente, con fechas `38e54e8` / `9442c1f` / `ab5ddcd`. |
| 7 | SHA-256 del comparador | PASS | `Get-FileHash scripts/validation/compare_live_replay.py` → `4CB5082E4F8C52BCD775F0C5F3D53D992A3730007D1738D463A171F2C0794B5F`. `PREREGISTRO_R24.md:10` y `:200` declaran `4cb5082e...0794b5f`. Coincide. |
| 8 | Preregistro R23K no mutilado | PASS | `git diff --numstat 190f596 HEAD -- workflows/PREREGISTRO_R23K.md` → `169	0	workflows/PREREGISTRO_R23K.md`. Cero borrados. |
| 9 | Doble conteo W/L alcanzable | **FAIL** | Ver análisis abajo. Ruta residual: incremento manual (HUD/bridge) entre la captura de la clave (`TradingEngine.kt:624`) y la ejecución del `post` (`:657-659`). `OverlayService.kt:1274`, `:1279` y `TradeDrawHttpBridge.kt:377`, `:385` llaman `recordTradeWin/Loss(force = true)` **sin** pasar por `markLiquidated`, así que limpian el pendiente y suman; luego `resolvePendingTradeLoss()` devuelve `false` y `settleTradeOnce` encuentra la clave **libre** y suma por segunda vez. |
| 10 | Rango `aac1df3..8cdb61f` en `app/src/main/` | PASS (informativo) | `git diff --name-only aac1df3 8cdb61f -- app/src/main/` → `AdaptiveLearningEngine.kt`, `TradingEngine.kt`. Dos archivos. |
| 11 | `8cdb61f..190f596` sin tocar `app/` | PASS | `git diff --stat 8cdb61f 190f596 -- app/` → salida vacía. La build bajo medición es byte-idéntica en `app/`. |
| 12 | Credenciales en `190f596..HEAD` | PASS | `git log -p 190f596..HEAD \| Select-String "authtoken\|cookie\|secret\|password\|Bearer\|sk-\|ghp_\|AKIA"` → 4 coincidencias, todas texto de documentación (la línea de instrucción del grep en `AGENT_EXECUTION_POLICY.md`, dos comentarios de `.gitignore` sobre `.trail/cache`, y una fila de tabla en `B1_result.md`). Ningún valor real. |
| 13 | Preregistro R23K vs ventanas | **FAIL** | `git log --diff-filter=A -1 --format="%h %ad %s"` → `PREREGISTRO_R23K.md` = `38e54e8 2026-10-19 01:22:57`; `window_04_clean/trades.csv` = `9442c1f 2026-10-18 20:08:26`; `window_07_clean/trades.csv` = `ab5ddcd 2026-10-18 21:46:41`. `git merge-base --is-ancestor 9442c1f 38e54e8` → exit `0`; idem `ab5ddcd 38e54e8` → exit `0`. Ambas ventanas son **ancestros** del preregistro: el preregistro es **POSTERIOR** a las dos. |
| 14 | Cabeceras de CSV | PASS | `loops/r23k_validation/window_20/trades.csv` → `trade_id,decision_id,timestamp_ms,asset,action,detail,balance_at_trade`. `decisions.csv` → `decision_id,trade_id,timestamp,asset,price_at_signal,candle_second,market_regime,trend,action,score,action_plan,kind,detail,veto_reason`. |
| 15 | Símbolos de `RiskManager.kt` | PASS | `SNIPER_MAX_LOSSES = 2` en `:63`; `SNIPER_TAKE_PROFIT_WINS = 2` en `:64`; `fun resetStats()` `:1011`; `fun setStats(...)` `:1037`; `fun correctStats(...)` `:1052`. Ninguno reinicia `tradesLossSinEvidencia` (comentarios `:1018`, `:1044`, `:1053`). |
| 16 | `PREREGISTRO_R24.md` cerrado | PASS | (a) `:221` "**Máximo 4 ventanas de validación R24**, fijado A PRIORI"; (b) `:201` "**Una sola tolerancia: `±6000 ms`**"; (c) `:199-200` SHA-256 del comparador; (d) `:230` "## 5. CAMBIOS DE CÓDIGO PERMITIDOS EN EL APK R24 (LISTA CERRADA)". |
| 17 | Sensibilidad por tolerancia (informativo) | NO VERIFICABLE como V5 | Agregado Python sobre `loops/r24_orch_verify/t*_*.json` (16 archivos por tolerancia): ver tabla en Hallazgos. **No es evidencia V5**: `PREREGISTRO_R24.md:202-203` prohíbe reportar la sensibilidad como resultado. |

> Nota: la tarea pedía "14 filas"; hay **17 puntos** en el enunciado (15 numerados + 2 propios). Se reportan todos. Ninguno se omite.

## Análisis del punto 9 (crítico)

**Cerrado:** el doble conteo entre las dos rutas AUTOMÁTICAS (visión `onNewFrame` y headless `checkHeadlessTradeResolution`) ya no es alcanzable. Ambas comparten el `AtomicBoolean isTradeResolving` (`TradingEngine.kt:200`, CAS en `:615` y `:2660`), y el fallback `recordTradeWin/Loss(force = true)` fue sustituido por `settleTradeOnce(tradeId, startMs, win)` (`:648`, `:659`, `:2743`, `:2757`), que deduplica por clave natural (`RiskManager.kt:357-374`).

**Sigue abierto:** las rutas MANUALES no participan en la idempotencia.

```
OverlayService.kt:1273-1274   markManualIncrement("MANUAL_HUD","WIN"); recordTradeWin(force = true)
OverlayService.kt:1278-1279   markManualIncrement("MANUAL_HUD","LOSS"); recordTradeLoss(force = true)
TradeDrawHttpBridge.kt:376-377  markManualIncrement("MANUAL_BRIDGE","WIN"); recordTradeWin(force = true)
TradeDrawHttpBridge.kt:384-385  markManualIncrement("MANUAL_BRIDGE","LOSS"); recordTradeLoss(force = true)
```

`recordTradeWins` (`RiskManager.kt:924-957`) y `recordTradeLosses` (`:960-988`) con `force = true` incrementan y llaman `clearPendingTrade()` **sin** llamar `markLiquidated`. Secuencia que produce doble conteo:

1. `TradingEngine.kt:624-625` captura `settleTradeId` / `settleStartMs` con el pendiente aún vivo.
2. El operador pulsa WIN/LOSS en el HUD, o llega `/win` o `/loss` al bridge: suma 1 y limpia el pendiente.
3. Se ejecuta el `handler.post`: `resolvePendingTradeLoss()` (`RiskManager.kt:1004-1008`) devuelve `false` porque `hasPendingTrade` ya es `false`.
4. `settleTradeOnce(settleTradeId, settleStartMs, win)` → `markLiquidated` encuentra la clave **libre** (nadie la marcó) → devuelve `true` → suma **otra vez**.

Resultado: `totalWins`/`totalLosses` suben 2 por un solo trade. Consecuencia operativa real: `SNIPER_MAX_LOSSES = 2` (`RiskManager.kt:63`) se alcanza con **una** pérdida, y `checkSniperSessionLimits` (`:712`, `:716`) corta la sesión antes de tiempo.

Mitigación parcial ya presente: `markManualIncrement` (`RiskManager.kt:332-333`) etiqueta el incremento y `/status` publica `manualWins`/`manualLosses` (`TradeDrawHttpBridge.kt:646-648`), de modo que V1 puede restarlos. Eso **corrige la medición, no el contador de riesgo**. El defecto de comportamiento sigue vivo.

Veredicto punto 9: **FAIL** (alcanzable, ventana estrecha, ruta manual).

## Análisis del punto 13 (crítico)

| Artefacto | Commit de alta | Fecha |
| :--- | :--- | :--- |
| `loops/r23h_validation/window_04_clean/trades.csv` | `9442c1f` | 2026-10-18 20:08:26 |
| `loops/r23j_validation/window_07_clean/trades.csv` | `ab5ddcd` | 2026-10-18 21:46:41 |
| `workflows/PREREGISTRO_R23K.md` | `38e54e8` | 2026-10-19 01:22:57 |

`git merge-base --is-ancestor 9442c1f 38e54e8` → exit `0`. `git merge-base --is-ancestor ab5ddcd 38e54e8` → exit `0`.

El preregistro es **POSTERIOR** a las dos ventanas que dice gobernar. Su propia regla (`PREREGISTRO_R23K.md`, línea 16: cualquier modificación posterior a la primera ventana medida invalida el ciclo) lo descalifica como preregistro de W04 y W07.

Atenuante verificada: `fde4a07` añadió el bloque `> [!CAUTION]` que declara el Cubo 2 **EXPLORATORIO** (punto 6, PASS). La consecuencia está publicada, no oculta; pero el hecho medido no cambia.

Veredicto punto 13: **FAIL** (preregistro tardío respecto a W04 y W07).

## Discrepancias

| Archivo:línea | Dice | Debería decir | Fuente |
| :--- | :--- | :--- | :--- |
| `RiskManager.kt:368` | `if (key == "ts:0") return true` — sin identificador fiable no deduplica | Debería deduplicar también en ese caso, o rechazar la liquidación y registrarla como no verificable | `Get-Content` directo; contradice el propósito declarado en `:361-364` |
| `OverlayService.kt:1274`, `:1279`; `TradeDrawHttpBridge.kt:377`, `:385` | `recordTradeWin/Loss(force = true)` sin `markLiquidated` | Deberían marcar la clave del trade pendiente antes de incrementar, para que el fallback automático no vuelva a contar | Contraste con `TradingEngine.kt:648`, `:659` |
| `workflows/PREREGISTRO_R24.md:207` | Exige `>= 20` trades Live para V5(i) | El máximo observado en `loops/r24_orch_verify` es 22 `paired_decisions` sobre 16 ventanas; el margen es de 2 trades, no un colchón | Agregado Python del punto 17 |
| `workflows/r24_tasks/B1_result.md` | Presenta B1 como cierre de la carrera de doble liquidación | Cierra la carrera **automática**; la manual sigue abierta | `git show 6fc51f4` + lectura de las 4 rutas manuales |

## Hallazgos propios

**Sensibilidad por tolerancia** (`loops/r24_orch_verify/t{t}_*.json`, 16 archivos por tolerancia, suma de `summary.*`):

| Tolerancia | Archivos | paired_decisions | exact_matches | divergences | Tasa de coincidencia |
| :--- | ---: | ---: | ---: | ---: | ---: |
| 3000 ms | 16 | 16 | 11 | 5 | 68.75 % |
| 3500 ms | 16 | 17 | 13 | 4 | 76.47 % |
| 4000 ms | 16 | 20 | 16 | 4 | 80.00 % |
| 4500 ms | 16 | 21 | 19 | 2 | 90.48 % |
| 6000 ms | 16 | 22 | 20 | 2 | 90.91 % |

Lectura: el umbral V5(i) de `>= 90 %` **solo** se alcanza en 4500 y 6000 ms. La tolerancia congelada es 6000 ms (`PREREGISTRO_R24.md:201`), así que el criterio pasa por 0.91 puntos porcentuales de margen. Con `N = 22` un solo acierto menos lo deja en 86.36 %. El resultado es frágil y **no** debe presentarse como estable: `:202-203` prohíbe explícitamente reportar esta tabla como resultado de V5. Se incluye como diagnóstico de fragilidad, no como evidencia.

**Observación sobre `markLiquidated` y poda** (`RiskManager.kt:369-372`): el `Set` se vacía entero al llegar a `MAX_LIQUIDATED_TRADE_IDS`. Vaciar en bloque reabre la ventana de doble conteo para el trade en curso si el vaciado cae entre la captura de la clave y el `settleTradeOnce`. A 1 trade/minuto el evento es raro; queda anotado, no bloquea.

**Observación sobre `settleTradeOnce` en la rama WIN** (`TradingEngine.kt:648`): el valor de retorno se descarta. No afecta al conteo (la rama solo se ejecuta si `resolvePendingTradeWin()` devolvió `false`), pero impide distinguir en telemetría si esa llamada fue la que contó.

**Verificación no ejecutada:** no se corrió `./gradlew test` ni `assembleDebug`. `LiquidationIdempotencyTest.kt` existe (`+159` líneas en `6fc51f4`) pero su resultado no fue comprobado empíricamente en esta auditoría. La afirmación "la carrera automática está cerrada" se sostiene sobre lectura de código, no sobre ejecución de ese test.

## Re-auditoría

**Fecha:** 2026-10-22
**SHA verificados:** `6fc51f4` (HEAD auditado), `38e54e8` (preregistro R23K), `9442c1f` (W04), `ab5ddcd` (W07), `8cdb61f` (build bajo medición), `190f596` (HEAD de recálculo R23K), `aac1df3`, `fde4a07`.
**Estado del árbol:** `app/` limpio. Modificados solo `workflows/R24_PROGRESO.md` y este archivo; sin seguimiento `loops/r24_audit/`, `loops/r24_orch_verify/`, `workflows/r24_tasks/B_1.md`.

| Condición | Estado |
| :--- | :--- |
| `app/` byte-idéntico entre `8cdb61f` y `190f596` | CUMPLE (punto 11, salida vacía) |
| Preregistro R23K sin borrados | CUMPLE (punto 8, `169 0`) |
| Comparador congelado con SHA declarado | CUMPLE (punto 7) |
| Cubo 2 declarado EXPLORATORIO | CUMPLE (punto 6) |
| Preregistro R23K anterior a W04 y W07 | **NO CUMPLE** (punto 13) |
| Doble conteo W/L cerrado en todas las rutas | **NO CUMPLE** (punto 9, ruta manual) |
| Sin credenciales en el rango auditado | CUMPLE (punto 12) |
| Preregistro R24 con lista cerrada, 4 ventanas, ±6000 ms, SHA | CUMPLE (punto 16) |

## Firma

FAIL — no se firma: (1) punto 9, el doble conteo W/L sigue alcanzable por la ruta manual (HUD y bridge incrementan con `force = true` sin pasar por `markLiquidated`, y el fallback automático vuelve a contar la misma clave); (2) punto 13, el preregistro R23K (`38e54e8`, 2026-10-19 01:22:57) es posterior a W04 (`9442c1f`, 2026-10-18 20:08:26) y a W07 (`ab5ddcd`, 2026-10-18 21:46:41), ambas ancestras suyas con exit code 0.
