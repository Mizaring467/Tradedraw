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
