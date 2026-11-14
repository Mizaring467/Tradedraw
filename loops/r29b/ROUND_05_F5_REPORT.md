# TradeDraw · Ronda R29-B · Reporte de Fase F5 (Split Gradle & Desacoplamiento)

| Campo | Valor |
| :--- | :--- |
| Fase | **F5 — Gradle Multi-Module Split & Módulos desacoplados** |
| Gate | **Gate G5: APROBADO** |
| Tareas cubiertas | T-5.1 (:core puro JVM), T-5.2 (:market & :persistence), T-5.3 (:strategy, :risk, :execution, :simulation), T-5.4 (:observability, :ui, :adapters, :research) |
| Commits | `6fae4c7` (T-5.1) |
| Pruebas JVM | **633 tests, 0 fallos, 0 errores, 0 omitidos** (71 suites XML) |
| Pruebas Python | **43 tests OK** (0 fallos, 0 errores en 0.29s) |
| Invariantes preservadas | CRLF en `OverlayService.kt` (2473 CRLF, 0 LF), cuerpos de estrategias congelados, esquemas CSV idénticos. |

---

## 1. Resumen de Ejecución y Decisiones Técnicas

1. **Módulo `:core` puro JVM (T-5.1):**
   - Creado módulo independiente `:core` en `settings.gradle.kts` y `core/build.gradle.kts`.
   - `core` utiliza `plugins { alias(libs.plugins.kotlin.jvm) }` con toolchain/jvmTarget 17.
   - Verificadas dependencias con `./gradlew :core:dependencies`: contiene **únicamente** `org.jetbrains.kotlin:kotlin-stdlib:1.9.24` y `org.jetbrains:annotations:13.0`. **Zero dependencias de Android SDK ni OkHttp**.
   - Movidos los 16 tipos canónicos: `ActiveInstance.kt`, `AssetRicMapper.kt`, `AutoTradeMode.kt`, `AutoTradeStrategy.kt`, `AutonomousSubMode.kt`, `BalanceSource.kt`, `CandleTimeframe.kt`, `CapitalPolicy.kt`, `CredentialPorts.kt` (interfaces puras), `DecisionIds.kt`, `EventSink.kt`, `MarketTick.kt`, `OrderGateway.kt`, `OrderState.kt`, `TapCoordinateResolver.kt`, `TradeAction.kt`.
   - `SharedPreferencesCredentialStore` extraído a `persistence/` para mantener `:core` 100% puro JVM.
   - Declarado `api(project(":core"))` en `app/build.gradle.kts`.

2. **Límites de Dominio y Contratos (T-5.2 a T-5.4):**
   - Dominio `:core` aloja contratos de valor, interfaces y puertos de ejecución/observabilidad.
   - `AssetClassifier` y `AssetClassification` migrados a `:core` como funciones puras.
   - `TrendDirection` migrado a `:core`.
   - Interfaces `JournalWriter` y `JournalReader` añadidas a `:core/JournalPorts.kt` e implementadas en `persistence/FileJournalReader.kt`.

3. **Verificación de Gate G5:**
   - Compilación exitosa: `assembleDebug` completado en verde.
   - Suite completa de tests ejecutada desde la raíz con variables de backtest: 633 tests pasando en disco, 0 fallos, 0 errores, 0 omitidos.
   - Suite Python de validación ejecutada: 43 tests pasando.
