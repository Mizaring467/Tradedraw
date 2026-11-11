# Estado vigente — 2026-11-11

Integración simulada y recuperación aprobadas sobre b9d948f: pruebas locales, CI e instalación verificadas; recorrido sintético con reinicios y orden DEMO con liquidación comprobados. Modularización en curso. Base: 20bd936. Estrategias y parámetros congelados.

## Integración

El motor alimenta ticks por activo incluso tras desactivar SHADOW. El ledger guarda saldo, órdenes abiertas, liquidaciones, deduplicación y outbox en un snapshot atómico con checksum. Una liquidación se confirma en el ledger antes del journal; el consumidor idempotente permite recuperar un fallo entre escritura y acuse. Reiniciar desactiva nuevas aperturas, conserva pendientes y reintenta la outbox.

Regla reproducible: primer tick aceptado del mismo activo con timestamp >= entrada + 60000 ms. Timestamps no crecientes se descartan por activo. Sin tick elegible no hay liquidación inventada. El saldo disponible reserva las apuestas abiertas. decisionId y tradeId identifican el mismo par; colisiones se rechazan.

El journal v2 tiene archivo y cabecera propios. El lector real del bridge consume v1 y v2, y el monitor Python interpreta ambos esquemas. Los archivos v1 permanecen intactos. /simulation permite activación explícita en SHADOW; las sondas sintéticas solo existen en debug y utilizan activos TEST separados del feed.

Las reservas del broker conservan contexto y recibo durable. RiskManager recupera la retención tras reiniciar. Un timeout conserva incertidumbre; no se transforma en ausencia de orden. /dispatch_recovery muestra la intervención necesaria y acepta evidencia explícita de historial, con cuenta DEMO reciente, referencia e identidad exactas. La procedencia manual queda registrada. No se infiere reconciliación por falta de saldo.

## Evidencia local

- 647 pruebas app + 6 core, cero fallos; assembleDebug correcto. Log: td-sim-recovery.log en tmp de Termux.
- 45 pruebas Python aprobadas; incluye consumidores v1/v2.
- Replay ejecutado por la suite con ticks_live.csv de window_01.
- Desconexiones controladas: retirar alimentación de ticks y retirar escritura del journal hace fallar el recorrido integrado. Ambas restauradas byte a byte; logs td-sim-mutant-ticks.log y td-sim-mutant-writer.log.
- Casos: WIN/LOSS/TIE, varias órdenes, ticks ausentes/duplicados/atrasados/fuera de orden, activos, cambios de modo, reinicios, fallos antes/después de commit y escritura antes de acuse, reservas recuperadas y reconciliación única.

## Validación en dispositivo y CI — 2026-11-11

- CI b9d948f: https://github.com/Mizaring467/Tradedraw/actions/runs/42996460043 — success.
- Modelo 2311DRK48G, Android 36, serial ADB emulator-5554. APK local e instalada: SHA-256 0b4338bb561fe1aa36046b5cb24e4146218412c014b5708f6963d818ed68b185; /status gitSha=b9d948f.
- Sonda probe-r29b1111: apertura a 100, reinicio antes de expirar, SHADOW apagado al volver, tick elegible 101, WIN, saldo 1008.2, outbox 0. Tick repetido y segundo reinicio: siguen 1 liquidación y saldo 1008.2. /shadow_journal devuelve cabecera v2 y una fila con ambos IDs.
- DEMO: mínimo medido mediante validación del broker, Col$4.000. /trade BUY requestId=r29b-demo-1111-once en segundo 01; GESTURE 148ms; ENVIADA→CONFIRMADA→NINGUNA. decisionId=8c0b0e22-ea98-4b32-ba7c-838632217425.
- Journal productivo confirma WIN por saldo acreditado en 66s, entrada 36,786,736.64 COP, salida 36,790,056.96 COP, diferencia +3320.32 COP. Riesgo 1 WIN / 0 LOSS, sin pendientes; reserva CLEAR. Estrategias intactas. Modo final DISABLED.
- Reinicios requieren reactivar solo el componente de accesibilidad de TradeDraw en este dispositivo; se preservaron los otros servicios. Feed fresco y saldo disponibles tras volver a Binomo.

## Pendientes reales

T-5.2 a T-5.4 y F6 siguen pendientes; extracción incremental en curso. No se declara R29-B cerrada. La reconciliación manual requiere evidencia aportada por el operador: su procedencia es explícita, no verificación automática del historial remoto.
