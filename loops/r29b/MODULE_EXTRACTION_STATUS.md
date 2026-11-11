# Estado vigente — 2026-11-11 · Extracción incremental en curso

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
