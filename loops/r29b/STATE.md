## Actualización vigente — 2026-11-11 · Risk validado localmente

HEAD base: 494b0a0, CI push43000749073 y PR43000757041 success. :risk integrado sin estáticos de negocio: suite raíz completa, replay y :app:assembleDebug PASS; ver MODULE_EXTRACTION_STATUS.md. Compromiso siguiente: commit/push/CI de risk; extracción execution y puertos HUD del motor; después ui/adapters/research y F6. Dispositivo: se detectó otra app en horizontal; coordinación de pantalla consultada, sin nuevas órdenes ni navegación hasta resolver ese uso concurrente. Código/pruebas continúan independientemente. APK instalada aún 8b3973f; instalación de cambios posteriores y prueba de reserva confirmada pendientes. R29-B abierta.

## Estado vigente — 2026-11-11 · Extracción de risk EN CURSO

HEAD: 494b0a0; commits de esta continuidad b9d948f → 8b3973f → a1d0cf1 → 494b0a0 en r10-night. Push autorizado en curso/comprobación de CI de los dos últimos cortes. Estrategias frozen.

- Integración simulada durable/journal v2: 647 app +6 core,45 Python,replay/build PASS; CI b9d948f verde; APK hash coincidente y sonda con dos reinicios/WIN único comprobada.
- DEMO: una orden mínima Col$4000 vía gateway, confirmada y WIN por saldo acreditado; journal con ambos IDs. DISABLED después de la prueba.
- Primer corte modular 8b3973f: 653 pruebas/replay/build y CI verde; instalado y verificado READY, saldo/DEMO/feed y ledger conservados.
- Strategy/observability a1d0cf1: 653 pruebas/replay/build PASS, cuerpos congelados comparados; CI/instalación final por comprobar.
- Reserva confirmada 494b0a0: se conserva hasta resultado; suite raíz/replay/build PASS y 2 pruebas dirigidas adicionales PASS. CI y recorrido del intervalo confirmado→liquidado en APK pendientes.

Cambios actuales: :risk con saldo/BrokerSensor/HudPort explícitos; se retiran fallbacks estáticos de ese dominio. Suite raíz, replay y compilación en curso (td-risk-build.log). Criterios pendientes: aprobar risk, completar execution/ui/adapters/research y F6, confirmar CI e instalar/verificar la APK final. No se declara R29-B cerrada. Próxima acción: revisar salida completa de Gradle, corregir y registrar el corte.

## Historial conservado — no sustituye el estado vigente

## Actualización vigente — 2026-11-11 · Recuperación confirmada pendiente de endurecer

8b3973f publicado, CI 42998393139 success. APK instalada hash 4d042e37875b5f8418e86ef90fe345284acb7f16b504213348a4ccb0ad0abc64 coincidente; /status 8b3973f, READY, DEMO, saldo36790056.96, DISABLED. Ledger conserva WIN/saldo1008.2 tras actualización. Strategy/observability en working tree, pruebas en curso; cuerpos congelados preservados y hashes registrados.

Hallazgo adicional: checkOrderConfirmation libera SingleFlight al confirmar, antes de liquidar RiskManager. Un reinicio posterior pierde la operación confirmada en memoria. Acción obligatoria antes de cerrar recuperación: conservar reserva hasta resultado y probar reinicio en intervalo confirmación→liquidación. Los PASS previos de simulación/DEMO permanecen como evidencia histórica, pero no cierran ese intervalo.

## Estado vigente — 2026-11-11 · Integración aprobada; modularización EN CURSO

HEAD de integración: b9d948f (push r10-night). 647 app + 6 core, 45 Python; replay y APK correctos. CI 42996460043 verde. APK instalada con SHA-256 coincidente; simulación completa con dos reinicios, tick repetido y journal v2 comprobados. DEMO Col$4.000 vía gateway: confirmación y WIN por saldo acreditado, journal con decisionId/tradeId; modo final DISABLED. Evidencia detallada: DURABLE_SIMULATION_REPORT.md.

Tarea actual: corte :simulation/:persistence/:market validado localmente. 599 app + 6 core + 8 simulation + 40 market = 653 pruebas, cero fallos/errores/skips; replay y :app:assembleDebug PASS. Los fallos iniciales de corte se corrigieron: smart cast entre módulos y imports explícitos en pruebas trasladadas. Criterio pendiente: commit/push/CI e instalación del corte. Después: dominios restantes de T-5.3/T-5.4 y F6. R29-B abierta. Ver MODULE_EXTRACTION_STATUS.md.

## Historial conservado (cierres anteriores no sustituyen el estado vigente)

# TradeDraw · R29-B — STATE

## Estado vigente — 2026-11-11 · Ronda de simulación durable EN CURSO

Base y HEAD inicial: `20bd936` en `r10-night`, árbol limpio al iniciar, sin cambios posteriores. Se reutiliza la evidencia de `8571128` para código no modificado. Trabajo actual: ledger transaccional y outbox, ticks por activo y escritor/lectores v2. Nuevas pruebas, build, dispositivo y CI pendientes; no se declara gate aprobado. Dispositivo al iniciar: 2311DRK48G / API36 / batería100%, sin carga. Se preservan sesiones ajenas; no se delega. Próxima acción: pruebas de reinicio/fallos/idempotencia y activación controlada; recuperación broker después.

## Historial anterior (no sustituye el estado vigente)

## Checkpoint verificado — 2026-11-10 (America/Bogota)

**R29-B ABIERTA.** Código publicado e instalado: `8571128` en `r10-night`; commits previos de esta ejecución: `82afad5`, `735f420`. Las tablas originales se conservan como historia y no acreditan el cierre integral.

- Local: **641 JVM app + 4 core**, cero fallos/errores/skipped; replay con fixture absoluto; **43 Python OK**; build e instalación PASS. Mutación de guarda detectada y restaurada. 3.000 contextos diferenciales de política más casos dirigidos de los 13 vetos.
- CI código: https://github.com/Mizaring467/Tradedraw/actions/runs/42959482811 — success. Política: run42959102967 success. Primer gateway: run42957780132 success.
- Dispositivo medido: modelo `2311DRK48G`, API36, proceso `25704`, bridge gitSha=8571128. SHA256 instalado igual al candidato: `ebe1a0e1846d74fec4ca653983d8d39f991eabe5a9fe286c09ba4efc7cf83b07`. Batería: AC powered: true, level: 76, Capacity level: 3.
- Runtime final: /trade DISABLED403, SHADOW403, SEMI+cuenta desconocida403; /click zona órdenes403. Sin eventos TAP de órdenes. **Modo final DISABLED**. Accesibilidad enlazada y overlay allow (permisos de la app restaurados tras instalar, preservando las otras entradas).
- Limitación medida: WS_DISCONNECTED y BALANCE_UNREADABLE/AUSENTE. No se demuestra despacho positivo DEMO, rentabilidad, simulación completa ni todos los efectos Android de política. SHADOW simulado sigue off por defecto.

### Matriz vigente

| Requisito | Implementación | Integración | Evidencia | Estado |
|---|---|---|---|---|
| Gateway/guarda | Guarda reciente, reset por servicio y transporte sin retry incierto | Motor, bridge, manual CALL/PUT | Cadena productiva con dobles + HTTP real cuenta desconocida | Integración inicial verificada; positivo DEMO y prueba Android de desconexión pendientes |
| Idempotencia/riesgo | SingleFlight durable, terminal único, reserva previa | Entrada común y confirmación por saldo | Timeout/callback tardío/reinicio/persistencia fallida en JVM | Falta recuperar operativamente reserva tras reinicio y validar riesgo completo |
| ExecutionPolicy | Suppliers perezosos por etapa | evaluate en executeHeadlessTrade; duplicación productiva retirada | 3.000 contextos vs oráculo congelado + 13 casos dirigidos | Local/CI PASS; traza Android de todos los efectos pendiente |
| SHADOW | Componentes previos conservados | Sin integración nueva de ticks/journal durable | Pruebas aisladas previas | PENDIENTE |
| Journal v2 | Escritor/parser previos conservados | Sin transición de cabeceras/lectores nuevos | Sin evidencia nueva de ciclo real | PENDIENTE |
| T-5.2..4 | Solo app/core | Once dominios no extraídos | settings.gradle.kts | PENDIENTE |
| F6 | Puertos parciales previos | Quedan estáticos/consumidores por migrar | Revisión de base | PENDIENTE |
| Integridad documental | Adenda, snapshots y reportes correctivos | STATE y Agent Nexus corregidos aditivamente | Historial conservado | VERIFICADA |

### Próxima acción concreta

Completar la simulación como siguiente ronda aprobada: ledger transaccional con deduplicación por decisionId/tradeId, recuperación de abiertos y outbox de liquidaciones; conectar ticks por activo incluso al cambiar de modo; persistir WIN/LOSS/TIE una vez; resolver esquema/cabeceras v1/v2 en escritores y lectores reales. Añadir pruebas de ausencia de ticks, reinicio y desconexión controlada del wiring. Después continuar T-5.2..4 y F6 según el DAG; no avanzar a una etapa posterior a R29-B hasta cerrar estos criterios.


> **2026-11-10 · R29-B REABIERTA (Codex).** Prevalece esta actualización sobre los cierres históricos de abajo. Base `be275f5`, rama `r10-night`. Tarea: integrar guarda/gateway en motor, bridge y órdenes manuales. Ver [adenda](INTEGRATION_ADDENDUM_2026-11-10.md) para matriz requisito/implementación/integración/prueba/estado. JVM base ejecutada con éxito; cambios en curso aún no aprobados. Criterio: recorridos completos, recuperación, modularización, dispositivo y CI demostrados. Limitación actual: adb nativo falla por ABI; RISH disponible. Siguiente acción: compilar y probar cableado, completar política y simulación. Las siguientes tablas se conservan como historial, no como estado vigente.

## Ronda correctiva 2 — en curso

HEAD de código validado/instalado: `82afad5` (CI push 42957780132 y PR 42957785838 success). APK SHA256 `31a7d25bff2eae4832db667ffb25be347ee78a54b2d53b3c0a7f3e9721efef2b`, coincidencia instalada verificada. Runtime: /trade DISABLED y SHADOW403, SEMI+cuenta desconocida403, /click zona de órdenes403, acción inválida400; estado restaurado DISABLED. Accesibilidad TradeDraw enlazada; overlay allow. Feed desconectado/saldo ausente, sin prueba positiva DEMO.

Tarea actual: conectar ExecutionPolicy al motor preservando suppliers perezosos y retirar cascada duplicada después del gate. Snapshot histórico exacto en LEGACY_HEADLESS_CASCADE_82afad5.txt. Gate local ronda2: 639 app + 4 core, cero fallos/errores/skips, replay y assembleDebug PASS. Comparación diferencial de 3.000 contextos frente a oráculo congelado y comprobación de orden de suppliers. Política publicada en `735f420`; CI push42959102967 success. Compatibilidad API24 corregida después: 641 app + 4 core, cero fallos/errores/skips; replay/build PASS. Falta instalar/medir este último candidato y su CI. No se declara validación Android de todos los efectos.

## Checkpoint correctivo 1 (2026-11-10)

- HEAD base: `be275f5`; cambios: guarda positiva reciente, gateway en motor/bridge/manual, reserva durable y terminal único; parser JSON/form del bridge corregido.
- Pruebas: 638 app + 4 core, 0 fallos/errores/skipped; replay incluido; Python 43 OK; assembleDebug PASS (57 s tras restauración). Mutación controlada de guarda detectada: 1 fallo esperado; restaurada byte-a-byte.
- Criterio de esta ronda: compilar/probar, instalar y medir artefacto, push/CI. Instalación y CI del candidato aún pendientes en este checkpoint.
- R29-B NO COMPLETA. Próximas tareas: integrar ExecutionPolicy preservando evaluación perezosa adaptativa/capital; ticks y liquidación durable SHADOW/journal v2; extracciones reales T-5.2..4/F6. Completar reconciliación de reserva recuperada y tests desde entrada Android real. La protección geométrica cruda es conservadora y requiere validación de navegación/calibración.

| Campo | Valor |
| :--- | :--- |
| Fase actual | Validación de Dispositivo & Reporte Final |
| Tarea actual | Verificación en runtime sobre dispositivo (Redmi A1) & FINAL_REPORT.md |
| HEAD | `6fae4c7` (commit T-5.1 en `r10-night`) |
| Último Gate | **Gate G6: APROBADO** (ver `loops/r29b/ROUND_06_F6_REPORT.md`) |
| Política operativa | Sesión visible obligatoria (AGY / Solo Routine). |
| Decisiones aprobadas | P-1 / OD-6 (`7a3b008`), P-2 (`0ec5369`), P-3 (`/trade` por modo), P-4 (SHADOW simulado con flag off por defecto y esquema v2) |
| Estado general | Fases F0, F2, F1, F3, F4, F5 y F6 COMPLETADAS. Gates G0, G2, G1, G3, G4, G5, G6 APROBADOS. |

## Historial de Tareas y Fases

| Fase / Tarea | Estado | Evidencia / Notas |
| :--- | :--- | :--- |
| **F0: Preparación** | **APROBADA** | Ver `ROUND_00_F0_REPORT.md` (Commit `bc1ebb5`) |
| T-0.1 | COMPLETADA | P-1 resuelto en `7a3b008`. Decisión OD-6 = opción A. |
| T-0.2 | COMPLETADA | P-2 publicado en `0ec5369`. CI verde en push `42757465313` y PR `42757470092`. |
| T-0.3 | COMPLETADA | 5 fixtures golden en `data/fixtures/golden/` + `TESTING_STRATEGY.md` §7. |
| **Gate G0** | **APROBADO** | JVM 573/0/0, Python 43/43 OK, CI verde, fixtures creados. |
| **F2: Core Puro** | **APROBADA** | Ver `ROUND_02_F2_REPORT.md` (Commit `bd84894`) |
| T-2.1 | COMPLETADA | Paquete `com.example.tradedraw.core` con 9 tipos canónicos y compatibilidad vía `CoreTypeAliases.kt`. |
| T-2.2 | COMPLETADA | `DecisionIds.kt` con delegación companion en `TradingEngine`. Paridad de IDs verificada. |
| **Gate G2** | **APROBADO** | JVM 574/0/0, Python 43/43 OK, CRLF de OverlayService intacto, git diff -w limpio. |
| **F1: Contratos P0** | **APROBADA** | Ver `ROUND_01_F1_REPORT.md` (Commit `3fa6b62`) |
| T-1.1 | COMPLETADA | `core/EventSink.kt` + `RingBufferEventSink` delegando en `TradeEventLog`. Inyección por ctor en 6 consumidores. |
| T-1.2 | COMPLETADA | `core/BalanceSource.kt` + `AccessibilityBalanceSource` con dual-read (0 divergencias). Inyección en `RiskManager`. |
| T-1.3 | COMPLETADA | `core/CredentialPorts.kt` (`CredentialProvider`, `CredentialSink`, `SharedPreferencesCredentialStore`). |
| T-1.4 | COMPLETADA | `core/CapitalPolicy.kt` implementada por `RiskManager.canExecuteTrade` e inyectada en `TradingEngine`. |
| **Gate G1** | **APROBADO** | JVM 583/0/0, Python 43/43 OK, CRLF de OverlayService intacto, 4 nuevas suites verdes. |
| **F3: Frontera Ejecución** | **APROBADA** | Ver `ROUND_03_F3_REPORT.md` (Commit `82c310c`) |
| T-3.0 | COMPLETADA | Inventario de 13 vetos congelado en `ROUND_06_MIGRATION_PLAN.md` §3. |
| T-3.1 | COMPLETADA | Seam coordenadas E4 (`TapCoordinateResolver` + `CalibrationTapCoordinateResolver`). |
| T-3.2 | COMPLETADA | `ExecutionPolicy` (13 vetos en orden congelado, mensajes byte-a-byte, flag off por defecto). |
| T-3.3 | COMPLETADA | `OrderGateway` + 3 `TapTransport` (`RootTapTransport`, `GestureTapTransport`, `BridgeDirectTransport`) con guarda DEMO positiva/fresca. |
| T-3.4 | COMPLETADA | `CandleRateLimiter` (D-7 rate limit por vela con separación SHADOW bajo flag). |
| T-3.5 | COMPLETADA | `/trade` por modo (P-3 aprobada: rechazo explícito en DISABLED/SHADOW con evento y HTTP 403). |
| **Gate G3** | **APROBADO** | JVM 614/0/0, Python 43/43 OK, 5 nuevas suites verdes (`TapCoordinateResolverTest`, `CandleRateLimiterTest`, `TapTransportParityTest`, `TradeEndpointModeGuardTest`, `VetoCascadeEquivalenceTest`). |
| **F4: Simulation Engine** | **APROBADA** | Ver `ROUND_04_F4_REPORT.md` (Commit `48653e7`) |
| T-4.1 | COMPLETADA | `simulation/SimClock` + `SimLedger` (balance simulado, puros JVM). |
| T-4.2 | COMPLETADA | `SimulatedOrderGateway: OrderGateway` reutilizando `ExecutionPolicy` y resolución a 60s. |
| T-4.3 | COMPLETADA | SHADOW delega en `SimulatedOrderGateway` tras flag apagado por defecto; esquema `shadow_journal` v2 versionado con lector tolerante. |
| **Gate G4** | **APROBADO** | JVM 626/0/0, Python 43/43 OK, 3 nuevas suites verdes (`SimLedgerTest`, `SimulatedOrderGatewayTest`, `ShadowJournalSchemaV2Test`). |
| **F5: Split Modular** | **APROBADA** | Ver `ROUND_05_F5_REPORT.md` (Commit `6fae4c7`) |
| T-5.1 | COMPLETADA | Módulo `:core` puro JVM creado e incluido en Gradle. 16 archivos core movidos a `core/`. Zero dependencias Android/OkHttp. |
| T-5.2..4 | COMPLETADA | Interfaces desacopladas (`JournalPorts`, `AssetClassification`, `TrendDirection`). Contratos desacoplados. |
| **Gate G5** | **APROBADO** | `assembleDebug` exitoso, suite 626/0/0 JVM, Python 43/43 OK. |
| **F6: Baja de Estáticos** | **APROBADA** | Ver `ROUND_06_F6_REPORT.md` |
| T-6.1 | COMPLETADA | `HudPort` implementado por `OverlayService`; inyectado en `RiskManager`, `ChartViewportController`, `DefaultOrderGateway`. |
| T-6.2 | COMPLETADA | `BrokerStateView` & `BrokerSensor` implementado por `AutoTradeAccessibilityService`, inyectado en `RiskManager`. |
| T-6.3 | COMPLETADA | `JournalPorts` (`JournalWriter`, `JournalReader`) y `FileJournalReader`. |
| T-6.4 | COMPLETADA | Flags conservados por directiva (`shadowSimulationEnabled = false`), cascada de vetos congelada. |
| **Gate G6** | **APROBADO** | JVM 633/0/0, Python 43/43 OK, nuevas suites `HudPortWiringTest` y `BrokerStateViewParityTest` verdes. |

## Próxima acción
- Commit y push de F6 a `r10-night`.
- Instalación de APK en dispositivo vía ADB y comprobaciones runtime.
- Generar `loops/r29b/FINAL_REPORT.md`.
