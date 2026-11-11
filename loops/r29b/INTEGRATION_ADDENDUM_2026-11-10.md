# R29-B — Adenda de revalidación, 2026-11-10

Responsable: Codex, sesión visible. Rama autorizada: r10-night. Base inspeccionada: be275f551968de69f99893f342ec4fa317a7cbae. Dirección platform, prioridad all, estrategias frozen.

Esta adenda corrige el alcance de los cierres históricos sin borrar sus informes. Los gates históricos demuestran las suites declaradas, no la integración completa. R29-B se reabre hasta satisfacer los criterios del propietario.

| Requisito | Implementación en base | Integración en base | Prueba/evidencia en base | Estado revalidado |
|---|---|---|---|---|
| Gateway y guarda DEMO | DefaultOrderGateway y StandardDemoAccountGuard | Sin consumidores del gateway; motor usa performClick, /trade performClickAt | TapTransportParityTest prueba componentes | PENDIENTE integración |
| ExecutionPolicy | Evaluador de 13 vetos | executionPolicyEnabled=false, ninguna evaluate en motor | VetoCascadeEquivalenceTest prueba política aislada | PENDIENTE equivalencia de ruta productiva |
| Simulación SHADOW | Apertura y ledger | Sin ticks ni logShadowTradeV2 desde motor | SimulatedOrderGatewayTest aislado | PENDIENTE ciclo completo y recuperación |
| Esquema v2 | Escritor y parser | Lectores reales y cabecera histórica no demostrados | ShadowJournalSchemaV2Test | PENDIENTE transición de archivos |
| T-5.1 | :core puro JVM | app depende de core | settings.gradle.kts | Implementada; revalidar build |
| T-5.2..4 | Contratos parciales | Solo :app y :core frente a 11 dominios del plan | settings.gradle.kts | INCOMPLETA, cierre previo incorrecto |
| F6 HudPort/BrokerSensor | Interfaces/adaptadores | Persisten estáticos de negocio y fallback a instance | Suites de puertos no prueban eliminación | INCOMPLETA |
| F6 JournalPorts | Interfaces/FileJournalReader | Consumidores reales no acreditados | Inspección pendiente de cada lector | PENDIENTE |
| STATE / FINAL_REPORT | Ambos existen | STATE conserva HEAD 6fae4c7 y próximas acciones ya reportadas cerradas | diff documental | Contradicción confirmada; esta adenda prevalece |

## Entorno medido

- Git limpio al inicio, r10-night / be275f5; gh autenticado. Procesos AGY visibles existentes preservados; sin delegación ni mensajes enviados a otros agentes.
- graft no tenía grafo: generado localmente con graft build. No indexed callers para DefaultOrderGateway; búsqueda de llamadas confirma los huecos.
- adb nativo falla al cargar libabsl_cord.so (símbolo __hash_memory); RISH funciona con UID 2000.
- Dispositivo medido: 2311DRK48G, batería 27 %, AC powered=true. Identidad Android/artefacto/arranque pendiente de nueva medición.
- Base JVM ejecutada: ./gradlew :app:testDebugUnitTest --offline --no-daemon --no-configuration-cache, BUILD SUCCESSFUL (43 s). Recuento XML y replay con fixture pendientes; no se hereda 633 como cifra actual.
- Agent Nexus leído; contiene cierre histórico que también necesita adenda. Prompts/plan disponibles consultados: encargo .git/td-r29b-mission.md, plan R29-A, workflows/tradedraw-loop.md (histórico, no autoriza modificar estrategias congeladas).

## Criterios de cierre

Gateway conectado en rutas normales con cuenta positiva/fresca; riesgo y terminal único con reconciliación ante incertidumbre; equivalencia productiva de política; simulación durable con ticks y lectores v1/v2; T-5.2..4 y F6 acreditados; pruebas de integración y desconexión controlada; replay/Python/build/CI; instalación e identidad actuales. Hasta entonces no declarar G3–G6 completos en integración.

## Primera ronda correctiva — ejecución (en validación)

- Integrados motor headless/visión, bridge /trade y botones de prueba CALL/PUT en dispatchBrokerOrder → SingleFlightOrderGateway → DefaultOrderGateway → transporte.
- Guarda compartida UNKNOWN al inicio, inválida al desconectar/reconectar, observaciones del árbol completo de Binomo y caducidad de 10 s. REAL prevalece ante árbol ambiguo. Primitivas vuelven a leer la cuenta antes del tap.
- Reserva persistida antes del transporte, deduplicación y resultado único; sin fallback tras entrega incierta. Confirmación positiva por saldo reconcilia la reserva. Una reserva recuperada tras reinicio permanece bloqueada: falta completar la recuperación operativa; no se declara cerrado ese criterio.
- /trade responde desde el resultado de despacho; timeout HTTP = 202 pendiente, no éxito. Admite requestId para reintentos. /click deja de reportar éxito incondicional.
- Gestos/root crudos rechazan zona conservadora de órdenes (portrait ≥80 % de altura; landscape ≥80 % de ancho). CALL/PUT pasa por gateway; navegación fuera de esa zona se conserva. Queda pendiente evaluar clasificación semántica/calibración para navegación dentro de esa zona y eliminar estáticos.
- Suite final de ronda ejecutada: 635 tests app / 73 XML + 4 tests core / 2 XML; 0 fallos, 0 errores, 0 skipped. Replay con BACKTEST_CSV absoluto y BACKTEST_OUT temporal. 43 Python OK. assembleDebug SUCCESS (2m30s).
- Incidencias corregidas: dos Gradle simultáneos colisionaron en salidas de tests y caché Kotlin; se esperó su terminación y regeneraron solo salidas de build. Error de tipos Float/Int corregido antes del PASS.
- ADB/AAPT2 recuperados sin alterar instalaciones: LD_LIBRARY_PATH=/data/data/com.termux/files/usr/lib evita la biblioteca C++ del directorio de Codex. ADB nativo enumera emulator-5554 (duchamp/2311DRK48G); lectura real ro.serialno=5PPFAACU6H7XHEY9, API36, 1220×2712. La enumeración de ADB en Debian tenía metadatos contradictorios; no se usó para instalar.
- Runtime ANTES de instalar: APK gitSha=6fae4c7, proceso 7148, DISABLED, health BLOCKED/ACCESSIBILITY_DOWN; batería 42 % cargando. Captura MainActivity inspeccionada. No se hereda la validación del reporte histórico.
- CI de la base verificado por gh: be275f5, runs 42790250513 y 42790239799 success. CI del candidato pendiente de push.

- Desconexión controlada de guarda: reemplazo temporal por `if (false)`; `OrderEntryIntegrationTest.accountTransitionsAndModesGuardTheActualTransport` falla (2 tests, 1 fallo). Restauración byte-a-byte verificada en finally. Reejecución final pendiente tras restauración.

- Tras restaurar guarda y corregir JSON/form: suite final 638 app + 4 core, 0 fallos/errores/skipped; replay incluido; assembleDebug PASS (57 s). Se conserva SHADOW simulation off por defecto y estrategias intactas. La política productiva y simulación no se declaran integradas por esta ronda.
