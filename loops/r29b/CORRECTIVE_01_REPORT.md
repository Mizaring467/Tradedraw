# R29-B — Corrección 1: frontera de despacho

Fecha: 2026-11-10 (America/Bogota). Responsable: Codex en sesión visible; sin delegación. Código: `82afad5`, publicado en `r10-night`.

## Integración demostrada

Motor headless y visión, `/trade`, TEST/TEST_SELL y prueba manual CALL/PUT llaman a `dispatchBrokerOrder`. La instancia productiva usa `SingleFlightOrderGateway(DefaultOrderGateway(guarda compartida), almacenamiento durable)`.

La guarda inicia UNKNOWN y se invalida al perder o reemplazar el servicio activo. La observación recorre el árbol completo del broker: REAL prevalece ante ambigüedad; ausencia de etiqueta o ventana ajena no confirma DEMO. Caduca en 10 s y las primitivas de órdenes vuelven a leer antes de despachar. La autorización REAL permanece falsa.

No se reintenta otro transporte tras resultado incierto. La reserva se persiste antes del transporte y sobrevive al proceso. Los callbacks tardíos no generan otro resultado. La confirmación positiva por saldo reconcilia la reserva; una reserva recuperada sin evidencia conserva el bloqueo. Falta completar la recuperación operativa de esa reserva y probar sus efectos desde el motor Android.

`/trade` espera resultado de despacho, responde 202 si continúa pendiente y admite requestId. Se corrigió JSON/form para impedir que un JSON SELL fuera interpretado como BUY por defecto. Acciones inválidas son 400. `/click` espera resultado y rechaza la zona conservadora de órdenes; navegación fuera de la zona conserva su ruta. Swipe que cruza esa zona se rechaza. Queda pendiente clasificar semánticamente navegación/calibración dentro de la zona.

## Pruebas y mutación

- JVM: 638 app (74 XML) + 4 core (2 XML), 0 fallos/errores/omitidos. Replay incluido con fixture absoluto `loops/r23c_phase3_validation/window_01/ticks_live.csv` y BACKTEST_OUT temporal.
- Python: 43 OK. APK assembleDebug PASS; regeneración tras commit PASS.
- Guarda desconectada temporalmente con `if (false)`: `OrderEntryIntegrationTest.accountTransitionsAndModesGuardTheActualTransport` falla (2 tests, 1 fallo). Original restaurado byte a byte en finally, suite completa posterior verde.
- Las pruebas usan la cadena productiva de gateways con dobles de transporte/almacenamiento. No sustituyen una integración Android completa ni demuestran aún desconexión del gateway en la entrada del motor o de ticks en simulación.
- CI push: https://github.com/Mizaring467/Tradedraw/actions/runs/42957780132 — success. PR preexistente: run 42957785838 — success. No se creó PR.

## Dispositivo actual

ADB nativo: emulator-5554; propiedades leídas: modelo 2311DRK48G, serial físico 5PPFAACU6H7XHEY9, API36, 1220×2712. No se infiere identidad de la enumeración de ADB en Debian.

`adb install -r` SUCCESS. SHA256 del APK candidato y base.apk instalada: `31a7d25bff2eae4832db667ffb25be347ee78a54b2d53b3c0a7f3e9721efef2b`. Coincidencia byte a byte. Proceso 10256; `/status.gitSha=82afad5`.

Tras instalar, HyperOS dejó overlay=ignore y accesibilidad TradeDraw deshabilitada. Se restauró overlay=allow y se añadió solo el componente de TradeDraw, preservando las demás entradas. Servicio enlazado; arranque desde botón medido por UIAutomator. UI inspeccionada por captura y jerarquía; la captura posterior mostró contenido incompleto aunque la jerarquía contenía los controles: no se declara validación visual integral.

Resultados HTTP actuales:

| Flujo | Resultado |
|---|---|
| `/trade` JSON SELL, DISABLED | 403; evento action=SELL confirma parseo |
| `/trade` SEMIAUTOMATIC, cuenta desconocida | 403, SEGURIDAD_CLICK_NO_DEMO_NOT_CONFIRMED |
| `/trade` SHADOW | 403 |
| `/click` (220,2440), zona órdenes | 403, ORDER_REQUIRES_GATEWAY |
| `/trade` acción inválida | 400 |
| Estado final | DISABLED; sin eventos TAP de órdenes en la ventana |

Batería: 27 % al primer sondeo, 57 % al último de esta ronda; AC powered=true. Feed WS_DISCONNECTED y BALANCE_UNREADABLE/AUSENTE: recorrido positivo Binomo DEMO no demostrado. No se enviaron órdenes al broker.

## Continuidad

R29-B sigue abierta: política productiva/equivalencia, simulación durable y journal v2 por lectores reales, T-5.2..4/F6, navegación/calibración, recuperación de reserva y pruebas Android de desconexión. Agent Nexus actualizado y releído con la adenda que revoca el cierre histórico.

Entorno: prefijar ADB/AAPT2/Gradle con `LD_LIBRARY_PATH=/data/data/com.termux/files/usr/lib`; el valor heredado de Codex cargaba C++ incompatible. Curl loopback con `--noproxy '*'`. No se modificaron instalaciones de herramientas. Ejecuciones Gradle posteriores serializadas; colisión inicial de salidas/caché diagnosticada y recuperada regenerando solo build.
