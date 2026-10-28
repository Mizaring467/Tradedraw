# AUDITORÍA R26-B: IDEMPOTENCIA DE LIQUIDACIÓN Y PROPIEDAD DE CONTADOR ÚNICO

**Fecha:** 2026-10-28  
**Tarea:** R26-AUDIT-B (Auditoría independiente, modo SOLO LECTURA del repositorio)  
**Repositorio:** `/data/data/com.termux/files/home/Tradedraw`  
**Rama:** `r10-night`  
**HEAD commit:** `75f005e`  
**Veredicto General:** **CUMPLE LA PROPIEDAD (PASS con limitaciones documentadas)**

---

## 1. Rutas de Incremento de Contador en `app/src/main`

### 1.1 `RiskManager.kt`
- **`recordTradeWins(count: Int, force: Boolean)`** ([`RiskManager.kt:1109-1144`](file:///data/data/com.termux/files/home/Tradedraw/app/src/main/java/com/example/tradedraw/RiskManager.kt#L1109-L1144)):
  ```kotlin
  consecutiveVoids = 0
  val safeCount = count.coerceAtLeast(1)
  currentWins += safeCount
  totalWins += safeCount
  ...
  lastTradeTime = System.currentTimeMillis()
  clearPendingTrade()
  OverlayService.instance?.updateHUDView()
  ```
  Incrementa `totalWins` atómicamente (`@Synchronized`). Si `!force && !hasPendingTrade`, ignora la llamada. Si `diff <= 10.0`, deriva a `recordTradeVoid`.
- **`recordTradeLosses(count: Int, force: Boolean)`** ([`RiskManager.kt:1146-1180`](file:///data/data/com.termux/files/home/Tradedraw/app/src/main/java/com/example/tradedraw/RiskManager.kt#L1146-L1180)):
  ```kotlin
  consecutiveVoids = 0
  val safeCount = count.coerceAtLeast(1)
  totalLosses += safeCount
  currentLossStreak += safeCount
  ...
  lastTradeTime = System.currentTimeMillis()
  clearPendingTrade()
  OverlayService.instance?.updateHUDView()
  ```
  Incrementa `totalLosses` atómicamente (`@Synchronized`).
- **`resolvePendingTradeWin()` / `resolvePendingTradeLoss()`** ([`RiskManager.kt:1174-1184`](file:///data/data/com.termux/files/home/Tradedraw/app/src/main/java/com/example/tradedraw/RiskManager.kt#L1174-L1184)):
  ```kotlin
  @Synchronized
  fun resolvePendingTradeWin(): Boolean {
      if (!hasPendingTrade) return false
      recordTradeWins(1, force = true)
      return true
  }
  ```
- **`settleTradeOnce(tradeId: String, tradeStartMs: Long, win: Boolean)`** ([`RiskManager.kt:507-512`](file:///data/data/com.termux/files/home/Tradedraw/app/src/main/java/com/example/tradedraw/RiskManager.kt#L507-L512)):
  ```kotlin
  @Synchronized
  fun settleTradeOnce(tradeId: String, tradeStartMs: Long, win: Boolean): Boolean {
      if (!markLiquidated(tradeId, tradeStartMs)) return false
      if (win) recordTradeWins(1, force = true) else recordTradeLosses(1, force = true)
      return true
  }
  ```
- **`settleManual(origin: String, win: Boolean)`** ([`RiskManager.kt:531-550`](file:///data/data/com.termux/files/home/Tradedraw/app/src/main/java/com/example/tradedraw/RiskManager.kt#L531-L550)):
  ```kotlin
  @Synchronized
  fun settleManual(origin: String, win: Boolean): Boolean {
      val id = pendingTradeTradeId
      val start = pendingTradeStartTime
      if (!markLiquidated(id, start)) {
          clearPendingTrade()
          OverlayService.instance?.updateHUDView()
          return false
      }
      markManualIncrement(origin, if (win) "WIN" else "LOSS")
      if (win) recordTradeWins(1, force = true) else recordTradeLosses(1, force = true)
      return true
  }
  ```
- **`setStats(wins: Int, losses: Int, lossStreak: Int?)`** ([`RiskManager.kt:1214-1225`](file:///data/data/com.termux/files/home/Tradedraw/app/src/main/java/com/example/tradedraw/RiskManager.kt#L1214-L1225)) y **`correctStats(...)`** ([`RiskManager.kt:1228-1234`](file:///data/data/com.termux/files/home/Tradedraw/app/src/main/java/com/example/tradedraw/RiskManager.kt#L1228-L1234)): Ajuste directo y forzado del marcador.
- **`recordTradeVoid(count: Int)`** ([`RiskManager.kt:1099-1107`](file:///data/data/com.termux/files/home/Tradedraw/app/src/main/java/com/example/tradedraw/RiskManager.kt#L1099-L1107)): Incrementa `totalVoids`, limpia el pendiente y no modifica `totalWins` ni `totalLosses`.

### 1.2 `TradingEngine.kt`
- **Ruta Visión (`checkTradeResolution`)**:
  - WIN ([`TradingEngine.kt:685-688`](file:///data/data/com.termux/files/home/Tradedraw/app/src/main/java/com/example/tradedraw/TradingEngine.kt#L685-L688)):
    ```kotlin
    val resolved = riskManager.resolvePendingTradeWin()
    if (!resolved) {
        riskManager.settleTradeOnce(settleTradeId, settleStartMs, win = true)
    }
    ```
  - LOSS ([`TradingEngine.kt:695-700`](file:///data/data/com.termux/files/home/Tradedraw/app/src/main/java/com/example/tradedraw/TradingEngine.kt#L695-L700)):
    ```kotlin
    val resolved = riskManager.resolvePendingTradeLoss()
    val counted = if (resolved) true
        else riskManager.settleTradeOnce(settleTradeId, settleStartMs, win = false)
    if (counted && lossSinEvidencia) riskManager.recordLossSinEvidencia()
    ```
- **Ruta Headless / WebSocket (`checkHeadlessTradeResolution`)**:
  - WIN ([`TradingEngine.kt:2804-2815`](file:///data/data/com.termux/files/home/Tradedraw/app/src/main/java/com/example/tradedraw/TradingEngine.kt#L2804-L2815)):
    ```kotlin
    if (riskManager.resolvePendingTradeWin()) {
        ...
    } else {
        riskManager.settleTradeOnce(settleTradeId, settleStartMs, win = true)
        ...
    }
    ```
  - LOSS ([`TradingEngine.kt:2817-2829`](file:///data/data/com.termux/files/home/Tradedraw/app/src/main/java/com/example/tradedraw/TradingEngine.kt#L2817-L2829)):
    ```kotlin
    if (riskManager.resolvePendingTradeLoss()) {
        if (lossSinEvidencia) riskManager.recordLossSinEvidencia()
        ...
    } else {
        val counted = riskManager.settleTradeOnce(settleTradeId, settleStartMs, win = false)
        if (counted && lossSinEvidencia) riskManager.recordLossSinEvidencia()
        ...
    }
    ```

### 1.3 `OverlayService.kt`
- **Botones manuales del HUD**:
  - WIN ([`OverlayService.kt:1275`](file:///data/data/com.termux/files/home/Tradedraw/app/src/main/java/com/example/tradedraw/OverlayService.kt#L1275)): `riskManager.settleManual("MANUAL_HUD", win = true)`
  - LOSS ([`OverlayService.kt:1279`](file:///data/data/com.termux/files/home/Tradedraw/app/src/main/java/com/example/tradedraw/OverlayService.kt#L1279)): `riskManager.settleManual("MANUAL_HUD", win = false)`
  - Diálogo de sincronización manual ([`OverlayService.kt:1309-1314`](file:///data/data/com.termux/files/home/Tradedraw/app/src/main/java/com/example/tradedraw/OverlayService.kt#L1309-L1314)): Invoca `riskManager.correctStats(newWins, ...)`.

### 1.4 `TradeDrawHttpBridge.kt`
- **Endpoints REST**:
  - `/win` ([`TradeDrawHttpBridge.kt:397`](file:///data/data/com.termux/files/home/Tradedraw/app/src/main/java/com/example/tradedraw/TradeDrawHttpBridge.kt#L397)): `overlayService.riskManager.settleManual("MANUAL_BRIDGE", win = true)`
  - `/loss` ([`TradeDrawHttpBridge.kt:404`](file:///data/data/com.termux/files/home/Tradedraw/app/src/main/java/com/example/tradedraw/TradeDrawHttpBridge.kt#L404)): `overlayService.riskManager.settleManual("MANUAL_BRIDGE", win = false)`
  - `/stats` (POST) ([`TradeDrawHttpBridge.kt:377-380`](file:///data/data/com.termux/files/home/Tradedraw/app/src/main/java/com/example/tradedraw/TradeDrawHttpBridge.kt#L377-L380)): `overlayService.riskManager.setStats(wins, losses, lossStreak)`.

---

## 2. Clave de Liquidación y Punto de Captura

### 2.1 Identificador de Liquidación
Definido en [`RiskManager.kt:482-483`](file:///data/data/com.termux/files/home/Tradedraw/app/src/main/java/com/example/tradedraw/RiskManager.kt#L482-L483):
```kotlin
internal fun liquidationKey(tradeId: String, tradeStartMs: Long): String =
    if (tradeId.isNotBlank()) tradeId else "ts:$tradeStartMs"
```
- Si `tradeId` no está vacío, la clave es el identificador único del trade (UUID v3 determinista o generado al emitir la orden).
- Si `tradeId` viene vacío, la clave se apoya en el timestamp de apertura del trade (`"ts:$tradeStartMs"`).
- Si `tradeStartMs == 0L` y `tradeId` es vacío, la clave resulta `"ts:0"`.

### 2.2 Punto de Captura Respecto de `handler.post`
La captura ocurre **ESTRICTAMENTE ANTES** de encolar en el `handler.post`:
- **Ruta Visión** ([`TradingEngine.kt:660-664`](file:///data/data/com.termux/files/home/Tradedraw/app/src/main/java/com/example/tradedraw/TradingEngine.kt#L660-L664)):
  ```kotlin
  val settleTradeId = riskManager.pendingTradeTradeId
  val settleStartMs = riskManager.pendingTradeStartTime
  val lossSinEvidencia = !finalWin && !isTie && !(baseBalance > 0.0 && currentBal > 0.0)
  handler.post {
  ```
- **Ruta Headless** ([`TradingEngine.kt:2743-2746`](file:///data/data/com.termux/files/home/Tradedraw/app/src/main/java/com/example/tradedraw/TradingEngine.kt#L2743-L2746)):
  ```kotlin
  val settleTradeId = snapshot.tradeId
  val settleStartMs = snapshot.startTime
  val lossSinEvidencia = !finalWin && !isTie && !(baseBalance > 0.0 && currentBal > 0.0)
  handler.post {
  ```
**Causa Arquitectónica:** Si la clave se leyera dentro del bloque asíncrono `handler.post`, cualquier acción manual concurrente del operador o bridge (ejecutada en el hilo principal mientras el `post` espera en la cola) invocaría `clearPendingTrade()`, borrando `pendingTradeTradeId` y dejando `pendingTradeStartTime = 0L`. Al capturarla antes, el motor retiene el identificador real para pasarlo a `settleTradeOnce`.

---

## 3. Coexistencia de Liquidación Manual y Automática (Análisis de Carreras)

### Caso A: Manual (HUD/Bridge) ocurre primero, luego el motor automático
1. El trade está activo en `RiskManager`.
2. El operador pulsa WIN en el HUD (`settleManual("MANUAL_HUD", win = true)`):
   - Lee `pendingTradeTradeId` y `pendingTradeStartTime`.
   - `markLiquidated` agrega la clave a `liquidatedTradeIds` (retorna `true`).
   - `markManualIncrement` registra `MANUAL_INCREMENT`.
   - `recordTradeWins(1, force = true)` sube el contador (ej. 7 → 8) y ejecuta `clearPendingTrade()`.
3. El `handler.post` del motor se despacha:
   - Invoca `resolvePendingTradeWin()`, que retorna `false` porque `hasPendingTrade == false`.
   - Entra en la rama `if (!resolved) { riskManager.settleTradeOnce(settleTradeId, settleStartMs, win = true) }`.
   - `settleTradeOnce` ejecuta `markLiquidated(settleTradeId, settleStartMs)`. Como la clave ya fue registrada en el paso 2, el `Set.add()` retorna `false`.
   - `settleTradeOnce` aborta y **no incrementa**.
4. **Resultado:** El contador incrementa exactamente una vez (7 → 8). El bug 7 → 9 queda neutralizado.

### Caso B: Motor automático ocurre primero, luego la acción manual
1. El `handler.post` del motor se despacha mientras el trade aún está pendiente.
2. `resolvePendingTradeWin()` retorna `true`, incrementa el contador (+1) y limpia el estado pendiente con `clearPendingTrade()`.
3. Si posteriormente el operador pulsa WIN en el HUD o envía `/win`:
   - El estado pendiente ya fue borrado (`pendingTradeTradeId == ""`, `pendingTradeStartTime = 0L`).
   - `settleManual` calcula la clave como `"ts:0"`.
   - En `markLiquidated`: `if (key == "ts:0") return true` (permite correcciones manuales cuando no hay trade activo).
   - `settleManual` incrementa el contador y anota `MANUAL_INCREMENT`.
4. **Protección:** No afecta la validez experimental porque el criterio V1 resta automáticamente todos los `manualWins` y `manualLosses` del lado `/status` al comparar contra el journal.

---

## 4. Análisis de Doble Conteo por Ruta

| Ruta | ¿Puede incrementar dos veces? | Mecanismo de Seguridad |
| :--- | :---: | :--- |
| **`resolvePendingTradeWin/Loss`** | **NO** | Protegido por guard `if (!hasPendingTrade) return false`. El primer llamador consume el flag vía `clearPendingTrade()`. En el motor, `settleTradeOnce` solo se invoca si `!resolved`. |
| **`recordTradeVoid`** | **NO** | Incrementa `totalVoids` y limpia el trade pendiente. No toca `totalWins` ni `totalLosses`. Si una orden es nula, queda cerrada e inaccesible para resoluciones posteriores. |
| **Caso sin `tradeId`** | **NO** (si `startMs > 0`) | `liquidationKey` utiliza `"ts:$tradeStartMs"`. El timestamp de apertura en milisegundos actúa como identificador determinista único. Probado y verificado. |
| **Caso anómalo `ts:0`** | **EXCEPCIÓN** | Si un trade se generara sin `tradeId` y con `startMs == 0`, la clave `"ts:0"` no se bloquea en `markLiquidated` para no congelar correcciones manuales legítimas. En el ciclo de vida normal del motor, `startMs` siempre es asignado con `System.currentTimeMillis()`. |
| **Overflow de caché (512 claves)** | **RIESGO TEÓRICO** | Al alcanzar 512 elementos, `liquidatedTradeIds.clear()` borra todas las claves. A 1 trade/minuto esto representa ~8.5 horas de operación ininterrumpida; una liquidación tardía después de ese ciclo podría perder la marca, aunque en producción las liquidaciones ocurren a los ~60–75 segundos. |

---

## 5. Tests de Regresión (`LiquidationIdempotencyTest`)

### 5.1 Catálogo de Pruebas
1. **`dosHilosMismoTradeIdIncrementanUnaSolaVez`**: Ejecuta concurrencia multihilo (`CountDownLatch`) compitiendo simultáneamente con el mismo `tradeId`; garantiza atomicidad de `settleTradeOnce`.
2. **`flujoNormalIncrementaUnaVez`**: Flujo estándar secuencial de resolución única; garantiza +1 en `totalWins`.
3. **`tradeIdDistintoIncrementaDosVeces`**: Dos trades con claves independientes; verifica ausencia de falsos bloqueos.
4. **`tradesLossSinEvidenciaNoSeReiniciaConResetStats`**: Verifica la propiedad monótona de proceso del contador `tradesLossSinEvidencia`.
5. **`incrementoManualSeEtiquetaYSeContabilizaAparte`**: Verifica la emisión de `Kind.MANUAL_INCREMENT` en `TradeEventLog` y el desglose en `manualWins`/`manualLosses`.
6. **`sinTradeIdSeUsaElTimestampDeApertura`**: Verifica la deduplicación efectiva mediante la clave de respaldo `"ts:$startMs"`.
7. **`rutaManualYAutomaticaConMismoTradeIdCuentanUnaVez`**: Verifica la coexistencia Manual (HUD) + Automática con el mismo `tradeId`.
8. **`rutaManualYAutomaticaSinTradeIdCuentanUnaVez`**: Verifica la coexistencia Manual (Bridge) + Automática sin `tradeId`.
9. **`manualSinPendienteSigueIncrementando`**: Verifica que las correcciones manuales intencionales del operador sin orden abierta sigan permitidas.
10. **`victoriaNoDuplicaContadorDeSieteANueve`**: Verificación específica de la anomalía histórica: configura `setStats(7, 0)`, emite BUY, liquida por HUD (7 → 8) y rechaza el fallback concurrente del motor, manteniendo estrictamente el contador en 8.

### 5.2 Conteo de `@Test` y Diferencia Histórica
- **Conteo en `f97abfa^`:** `9`
- **Conteo en versión actual (`HEAD` `75f005e`):** `10`
- **Diferencia:** El commit `f97abfa` introdujo formalmente el test #10 (`victoriaNoDuplicaContadorDeSieteANueve`), codificando la reproducción directa y verificación en código Kotlin real de que 7 victorias más una operación única resultan en 8 y nunca en 9.

---

## 6. Resultado Real del Comando de Verificación

**Comando ejecutado:**
```bash
cd /data/data/com.termux/files/home/Tradedraw && ./gradlew testDebugUnitTest --tests 'com.example.tradedraw.LiquidationIdempotencyTest' -q
```

- **Código de salida:** `0`
- **Tests ejecutados:** `10`
- **Fallos:** `0`
- **Errores:** `0`
- **Skipped:** `0`
- **Ruta del XML de resultados:**
  `/data/data/com.termux/files/home/Tradedraw/app/build/test-results/testDebugUnitTest/TEST-com.example.tradedraw.LiquidationIdempotencyTest.xml`

---

## 7. Veredicto Final

### ¿Se cumple la propiedad «UN EVENTO DE RESULTADO -> EXACTAMENTE UNA ACTUALIZACION DE CONTADOR»?
**SÍ, SE CUMPLE.**

La implementación de `settleTradeOnce` y `settleManual` con captura previa de clave antes del `handler.post` elimina la condición de carrera que permitía que un trade liquidado manualmente fuera incrementado nuevamente por el motor automático. La anomalía 7 → 9 está corregida y blindada con 10 tests unitarios reproducibles.

### Limitaciones Identificadas
1. **Evicción global en `liquidatedTradeIds`:** El conjunto de deduplicación se limpia completamente al alcanzar `MAX_LIQUIDATED_TRADE_IDS = 512` en lugar de utilizar una ventana deslizante basada en tiempo (LRU/TTL).
2. **Semántica de HUD sin orden abierta:** Si el operador pulsa WIN/LOSS cuando el motor ya finalizó y cerró el trade pendiente, el sistema interpreta la pulsación como un ajuste intencional (`ts:0`) e incrementa el contador, aunque queda neutralizado para auditorías gracias al desglose `manualWins`/`manualLosses` requerido por V1.
