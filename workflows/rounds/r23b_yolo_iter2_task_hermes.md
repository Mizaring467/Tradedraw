# R23B ITERACIÓN 2 — TAREA PARA HERMES: TELEMETRÍA DETERMINÍSTICA DE ACCIÓN EN EVENTOS DE ORDEN (BUY/SELL) Y PRECISIÓN DE RESOLUCIÓN

**Destinatario:** Hermes (`w1:p6`)  
**Emisor:** AGY (`w1:p1`)  
**Fecha:** 2026-10-15  
**Fase:** R23B (Modo YOLO en Cuenta DEMO)  
**Base SHA:** `3963575` (HEAD actual de `r10-night`)  
**APK Previa:** `versionCode = 13`, `BUILD_ID = R23-YOLO-ITER1`, SHA `ba75268`  

---

## 1. CONTEXTO Y DIAGNÓSTICO DE LA ITERACIÓN 1

En la validación live de la Iteración 1 (`loops/r23_yolo/iter_1/VALIDATOR_FINDINGS.md`), OpenCode confirmó el desbloqueo exitoso de la operativa (6 trades en 20 min, WR 66,7%, delta +132k COP, que subió a 7W / 2L y +381k COP en vivo).

Sin embargo, se identificó un defecto crítico de telemetría:
1. **`action = UNKNOWN` en `trades.csv`**:
   En `RiskManager.kt:276-285`, `setOrderState` registra el evento en `TradeEventLog`:
   ```kotlin
   TradeEventLog.add(
       TradeEventLog.Kind.ORDER_STATE,
       "${nuevo.name} $motivo",
       decisionId = pendingTradeDecisionId.ifBlank { null },
       tradeId = pendingTradeTradeId.ifBlank { null }
   )
   ```
   Al ejecutarse `markOrderConfirmed()` (`setOrderState(OrderState.CONFIRMADA, "saldo movido")`), el texto resultante es `"CONFIRMADA saldo movido"`. No contiene la dirección de la orden (`BUY` o `SELL`).
2. Aunque `pendingTradeAction` existe y está disponible en `RiskManager.kt`, no se inyecta en el string del evento, obligando a los parsers externos a adivinar o asignar `UNKNOWN`.

---

## 2. ESPECIFICACIÓN DE REQUERIMIENTOS

### REQ 1: Inyección Explícita de Acción en `setOrderState` (`RiskManager.kt`)
En `RiskManager.kt`, modificar `setOrderState(nuevo: OrderState, motivo: String)` para incluir la dirección de la orden activa (`pendingTradeAction`) si está presente:
```kotlin
    internal fun setOrderState(nuevo: OrderState, motivo: String) {
        if (orderState == nuevo) return
        orderState = nuevo
        val actionPrefix = pendingTradeAction?.name?.let { "$it " } ?: ""
        TradeEventLog.add(
            TradeEventLog.Kind.ORDER_STATE,
            "${nuevo.name} $actionPrefix$motivo".trim(),
            decisionId = pendingTradeDecisionId.ifBlank { null },
            tradeId = pendingTradeTradeId.ifBlank { null }
        )
    }
```
Esto asegurará que los eventos en `/events` tengan el formato:
- `"CONFIRMADA BUY saldo movido"`
- `"CONFIRMADA SELL saldo movido"`
- `"ENVIADA BUY tap_exitoso"`
- `"EXPIRADA BUY ..."`
permitiendo que cualquier validador extraiga `BUY` o `SELL` de forma 100% determinística.

### REQ 2: Bump de Versión (`app/build.gradle.kts`)
- `versionCode = 14`
- `versionName = "1.4-r23-yolo-iter2"`
- `BUILD_ID = "R23-YOLO-ITER2"`

### REQ 3: Tests Unitarios y Verificación
1. En `RiskManagerTest.kt`:
   - Añadir test `testSetOrderStateIncludesPendingTradeAction()`:
     Asignar `pendingTradeAction = TradeAction.BUY` y llamar a `setOrderState(OrderState.CONFIRMADA, "saldo movido")`.
     Comprobar que en `TradeEventLog.snapshot()` el último evento sea de tipo `ORDER_STATE` y su detalle contenga `"CONFIRMADA BUY saldo movido"`.
   - Test análogo con `TradeAction.SELL` comprobando `"CONFIRMADA SELL saldo movido"`.
   - Test con `pendingTradeAction = null` comprobando que mantenga la compatibilidad regresiva (`"CONFIRMADA saldo movido"`).
2. Ejecutar la suite completa:
   `./gradlew.bat testDebugUnitTest --console=plain`
   Todos los tests (421+ tests) deben pasar sin ningún fallo ni regresión.

---

## 3. PROTOCOLO DE ENTREGA
Hermes debe:
1. Implementar REQ 1 y REQ 2.
2. Implementar y validar los tests de REQ 3.
3. Commitear y reportar a AGY (`w1:p1`) el SHA candidato y resumen de cambios.
