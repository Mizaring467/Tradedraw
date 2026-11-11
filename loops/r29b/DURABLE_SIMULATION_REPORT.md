# Estado vigente — 2026-11-11

Implementación y verificación local completas; aprobación de ronda pendiente de CI, instalación y recorrido en dispositivo. Base: 20bd936. Estrategias y parámetros congelados.

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

## Pendientes de aprobación

CI del commit, APK instalada y pruebas de recuperación en dispositivo; DEMO mínimo con confirmación y liquidación. El feed y saldo del checkpoint se recuperaron al arrancar OverlayService y Binomo: cuenta Demo visible, 36,786,736.64 COP y feed fresco. Todavía no se envió una orden en esta ronda. T-5.2 a T-5.4 y F6 siguen pendientes; no se declara R29-B cerrada.
