# TradeDraw · Ronda R29-B · Reporte de Fase F6 (Baja de Estáticos & Desacoplamiento Final)

| Campo | Valor |
| :--- | :--- |
| Fase | **F6 — Baja de Estáticos & Desacoplamiento Final** |
| Gate | **Gate G6: APROBADO** |
| Tareas cubiertas | T-6.1 (`HudPort`), T-6.2 (`BrokerStateView` / `BrokerSensor`), T-6.3 (`JournalPorts`), T-6.4 (Baja final y flags congelados) |
| Pruebas JVM | **633 tests, 0 fallos, 0 errores, 0 omitidos** (71 suites XML) |
| Pruebas Python | **43 tests OK** (0 fallos, 0 errores en 0.29s) |
| Nuevas Suites | `HudPortWiringTest` (3 tests), `BrokerStateViewParityTest` (4 tests) |
| Invariantes preservadas | CRLF en `OverlayService.kt` (2473 CRLF, 0 LF), cascada de vetos congelada, bandera `shadowSimulationEnabled = false` conservada por directiva de misión. |

---

## 1. Resumen de Implementación

1. **`HudPort` (T-6.1):**
   - Creado `core/HudPort.kt` con métodos de orquestación HUD (`updateHUDView`, `showManualFallbackAlert`, `restoreHudTouchable`, `triggerClickAnimation`, `isPointInsideHUD`, `temporarilyBypassHUD`).
   - `OverlayService` implementa `HudPort`.
   - `RiskManager` recibe `hudPort: HudPort? = null` en su constructor, eliminando las 6 llamadas estáticas a `OverlayService.instance?.updateHUDView()`.
   - `ChartViewportController` recibe `hudPort: HudPort? = null`, eliminando acceso estático a `OverlayService.instance?.drawingView`.
   - `RootTapTransport` / `DefaultOrderGateway` consulta `hudPort ?: OverlayService.instance`.
   - `BinomoWebSocketClient` desacoplado vía callback `onAssetChangedListener: (() -> Unit)? = null` cableado en `OverlayService`.

2. **`BrokerStateView` & `BrokerSensor` (T-6.2):**
   - Creado `core/BrokerStateView.kt` con modelo de datos inmutable y `BrokerSensor` interface.
   - `AutoTradeAccessibilityService` implementa `BrokerSensor` en su companion object vía `override fun getBrokerState()`.
   - `RiskManager` consume `brokerSensor: BrokerSensor? = null` para `isDemoAccount`, `observedOrderAmount`, `latestObservedAsset`, `isSyntheticOrOTC`, con fallback seguro al estático si no se provee.
   - Migrado `AssetClassification` y `AssetClassifier` a `:core` como función pura, manteniendo delegación en `AutoTradeAccessibilityService.classifyAsset`.

3. **`JournalPorts` (T-6.3):**
   - Creado `core/JournalPorts.kt` (`JournalWriter`, `JournalReader`).
   - Creado `persistence/FileJournalReader.kt` para lecturas de cola en disco desacopladas.

4. **Baja Final & Conservación de Flags (T-6.4):**
   - De acuerdo a la directiva explícita de la misión (*"Si F6/T-6.4 propone retirar esa opción o encenderla por defecto, conserva la opción y documenta el ajuste"*), la bandera `shadowSimulationEnabled` se conserva con su valor seguro por defecto `false`.
   - La bandera `executionPolicyEnabled` se mantiene en `false` por defecto con paridad demostrada al 100% en `VetoCascadeEquivalenceTest`.
   - Estrategias de trading, órdenes de vetos y esquemas CSV estrictamente congelados.

---

## 2. Evidencia de Verificación

- **`HudPortWiringTest`:** 3 pruebas verificando delegación de `RiskManager`, `ChartViewportController` y `DefaultOrderGateway`.
- **`BrokerStateViewParityTest`:** 4 pruebas verificando paridad de estado, sensor en ATAS, paridad de clasificación de activos y dimensionamiento de stake.
- **Suite completa JVM:** 633 tests ejecutados, 0 fallos, 0 errores, 0 omitidos.
- **Suite Python:** 43 tests ejecutados, 0 fallos, 0 errores.
- **APK compilado:** `assembleDebug` exitoso en 43 segundos.
