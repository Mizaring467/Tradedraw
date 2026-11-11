# Estado vigente — 2026-11-11 · Extracción incremental en curso

## Séptimo corte — adapters y cierre F6 (working tree recuperado)

HEAD base 5be6418. El corte `:adapters` existía sin commit y sin compilar; se terminó sin resetear ni reescribir el motor. `adapters/` reúne los adaptadores Android/broker: `AIClient` (implementando `AIAdvisor` de `:execution`), `AccessibilityBalanceSource`, `AndroidBrokerRuntime` (implementa `BrokerRuntimePort`), `AndroidOrderComposition`, `AutoTradeAccessibilityService` y `BrokerDetector`.

Grafo comprobado en `build.gradle.kts`, no supuesto por compilación: `adapters → core/observability/market/ui/execution` (test: `risk`); `app → adapters` y todos los demás. Ningún módulo depende de `:app` ni la tiene en classpath. `AccessibilityBalanceSource` y `BrokerDetector` son movimientos byte-idénticos; `CalibrationManager` también (`:ui`); `AIClient` sólo añade `: AIAdvisor` y `override`; `AndroidOrderComposition` perdió `createExecutionPolicy`, que permanece en app porque cita `TradingEngine`.

F6: `BrokerRuntimePort` (`:core`) = `BrokerSensor + BalanceSource` + guard/refresh/permiso. `TradingEngine` lo recibe por constructor y no consulta estáticos de accesibilidad. `AutoTradeAccessibilityService` dejó `OverlayService.instance`: 17 líneas pasaron a `boundHudPort`/`boundAssetSink`, enlazados por `OverlayService` con `bindBrokerUi`/`unbindBrokerUi`. `BrokerStateView` ganó `rawObservedAsset` para preservar el `.ifBlank` exacto del sniper.

Los tres errores de recuperación fueron de migración, no de conducta: import `java.util.Locale` perdido, `String>=` tokenizado como `>=`, y un setter `internal` ejercido desde otro módulo (prueba trasladada al módulo dueño, sin ampliar API y sin dejar test hueco).

Un cuarto caso lo detectó CI, no el build local: `ProcessDeadlineTest` usaba `awaitProcessExit`, `internal` de `:adapters`. El verde local era falso por reutilización de clases de la ruta antigua. Se trasladó la prueba y desde entonces la verificación se ejecuta sobre `clean`, que es la única forma fiable aquí de detectar este tipo de fallo.

Equivalencia verificada por tokens contra 5be6418: `evaluateStrategySignalWithReason` idéntico salvo el parámetro `brokerState`; `evaluateSniperConfluences` idéntico salvo tres sustituciones equivalentes. Estrategias congeladas intactas.

Suite/agregador: 658 pruebas, 84 clases, 0F/0E/0skips (app357, core6, simulation8, persistence0, market40, strategy92, observability29, risk71, execution10, ui33, research2, adapters10). Delta 657→658 explicado: +1 `LiveStrategyBrokerPortTest`, −1 test hueco retirado. Python 47 PASS; replay ejecutado con CSV versionado; APK PASS. CI y dispositivo del corte pendientes.

## Historial conservado — no sustituye el estado vigente

No se declara G5 ni F6 completos. Los cierres históricos se conservan; esta tabla exige módulos con consumidores y dependencias reales.

| Tarea | Implementación actual | Integración | Criterio restante |
|---|---|---|---|
| T-5.1 core | JVM puro, gateways/política/contratos | app y módulos nuevos | Cerrada previamente; revalidar suite raíz |
| T-5.2 persistence | Journal v1/v2, lector, writer idempotente y preferencias de credenciales | runtime simulado, bridge, logger y WS | TradeJournalLogger y demás wrappers aún en app |
| T-5.2 market | WS, parser de ticks y lectura de payout | overlay, motor, accesibilidad | Suite y CI del corte en curso |
| T-5.3 simulation | Ledger, codec durable, gateway, reloj, outbox | runtime Android y motor | Suite de módulo y recorrido APK final |
| T-5.3 strategy/risk/execution | Contratos core, implementaciones aún en app | En producción | Extraer sin modificar interiores congelados |
| T-5.4 observability/ui/adapters/research | Implementaciones aún en app | En producción | Cortar dependencias antes de mover; replay a research |
| F6 | Callback de activo ya conectado; se retira fallback estático de WS | Overlay conecta callback antes de start | HudPort, BrokerSensor y journals restantes; equivalencia y cero estáticos de negocio |

Grafo del corte actual: app → market/persistence/simulation/core; market → persistence/core; persistence → simulation/core; simulation → core. Ningún módulo depende de app. Compilar valida visibilidad y dependencias. simulation es JVM; persistence y market son bibliotecas Android por sus adaptadores de SharedPreferences/Handler/Context. El adaptador de journal depende del contrato de resultados simulado, sin dependencia inversa: evita un ciclo persistence↔simulation. La composición Android permanece en app.

El orden adelanta el corte de simulation de T-5.3 para ubicar el contrato que necesita el writer durable. No reduce el alcance de T-5.2..4 ni crea módulos vacíos. Los identificadores de paquetes se mantienen para conservar consumidores e historial; las fuentes se mueven físicamente a su módulo.

## Evidencia del corte

Suite raíz y replay: 599 app + 6 core + 8 simulation + 40 market = 653, cero fallos/errores/skips. :app:assembleDebug PASS. Log td-market-pass.log. Se mantuvieron los 653 casos previos al moverlos a su dominio; la prueba de acceso internal vive ahora en market. No hubo cambios de estrategia. CI e instalación pendientes del commit del corte.

## Segundo corte — strategy y observability

Responsabilidades trasladadas: SyntheticCandleEngine, MarketRegimeClassifier, AdaptiveLearningEngine, MarketInoperabilityDetector, DTO de análisis, TradeEventLog/RingBufferEventSink, ModeEventJournal y CrashLogger. Ambos módulos Android → core; sin dependencia hacia app. AdaptiveReplayPort expone una fachada de lectura para el harness externo, delegando literalmente a las funciones internal congeladas. Pruebas de setters internal se ubican en strategy; el caso de reposo de RiskManager permanece como integración en app.

479 app + 6 core + 8 simulation + 40 market + 91 strategy + 29 observability = 653 pruebas, cero fallos/errores/skips; replay y build PASS (td-strategy-pass.log). Comparación de cuerpos conservados en STRATEGY_MOVE_HASHES.md. CI e instalación pendientes del commit de este corte. La extracción de risk/execution/ui/adapters/research y F6 sigue abierta.

## Tercer corte — risk

RiskManager reside en :risk (Android → core/observability), sin referencias a estáticos de accesibilidad ni OverlayService. La app inyecta BalanceSource, BrokerSensor y HudPort. Los tests de detalles internal viven con el módulo; sus observaciones son fixtures locales, con los mismos valores y expectativas anteriores. La app conserva pruebas de paridad del adaptador real. Riesgo sin adaptadores usa lectura no disponible; no accede por detrás a la app.

Suite raíz: app=410, core=6, simulation=8, market=40, strategy=91, observability=29, risk=71, total 655, cero fallos/errores/skips; replay y APK PASS (td-risk-pass.log). Se compararon también los cuatro cuerpos estratégicos con 20bd936: igualdad exacta salvo package/import/líneas vacías. CI e instalación del corte pendientes.

## Cuarto corte — execution

:execution contiene ExecutionPolicy, CandleRateLimiter, DefaultOrderGateway y los tres transportes sobre puertos. No depende de app ni busca servicios estáticos. Los predicados congelados se inyectan mediante ExecutionPolicyHooks obligatorios; la fábrica de composición llama a las implementaciones anteriores sin duplicarlas. AndroidOrderComposition conecta el guard positivo, root y el driver de gestos. El motor recibe HudPort de OverlayService y deja de buscar el servicio estático para actualizar HUD/fallback.

Se conservan orden de cascada, textos y efectos con el oráculo completo. Nuevas pruebas verifican bypass HUD350ms, resultado único ante callback repetido, ausencia de fallback tras transporte incierto, guarda REAL y desconexión de driver. Suite/replay/build PASS (td-execution-final.log): {'app': 402, 'core': 6, 'market': 40, 'persistence': 0, 'simulation': 8, 'strategy': 91, 'observability': 29, 'risk': 71, 'execution': 10}, total 657. CI e instalación de este corte pendientes. UI/adapters/research y estáticos restantes siguen abiertos.

## Quinto corte — ui

:ui contiene lienzo, figuras, herramientas, plantillas, selector de color, temas Compose, snapshot/coherencia HUD y proyección/strike/auto-dibujo. Depende de core; el rango de precios se recibe por proveedor desde el motor, sin lookup del servicio. OverlayService permanece por ahora como composición Android en app; no se da por completada su extracción ni todo T-5.4.

Suite raíz/replay/APK PASS (td-ui-online.log): {'app': 369, 'core': 6, 'market': 40, 'persistence': 0, 'simulation': 8, 'strategy': 91, 'observability': 29, 'risk': 71, 'execution': 10, 'ui': 33}, total 657. La dependencia stdlib-jdk8 inicialmente ausente se resolvió con Gradle normal; no se cambiaron versiones para ocultar el fallo de caché. La CI e instalación de este corte permanecen pendientes.

## Sexto corte — research y consumidores de evidencia

:research contiene el harness JVM ReplayBacktestTest, ejecutado como prueba local Android sin dispositivo, con dependencias core/strategy. Las fórmulas de poderes y agotamiento permanecen únicas en strategy y se comparten por puerto con producción; tests de equivalencia exhaustiva y trazabilidad siguen aprobados.

Suite raíz/replay/build: 657/0F/0E/0skips, 82 clases, confirmado mediante el agregador multimódulo real. Python47 PASS. Los dos monitores invocan :research:testDebugUnitTest; el verificador de worktree suma Android y JVM de todos los módulos, con compatibilidad para su API anterior y pruebas contra omisión de core/research. CI incorpora Python con NumPy2.4.4 (versión local validada), replay con CSV versionado y artefactos XML/replay. CI e instalación del corte pendientes del push.
