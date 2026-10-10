TAREA — RONDA 2 · INVESTIGACIÓN DE ACTIVOS Y EDGE (agy)

Repo: C:\Users\heidy\Tradedraw. NO edites código Kotlin. Solo investiga y reporta.
Dispositivo: UGU8KFUKM7E6CE8D (Launch activo). Cookie/device de la sesión ya validada doc.

=== Contexto (hallazgo Ronda 1) ===
El bot opera el ric `Z-CRY/IDX` ("Crypto IDX"). La sonda `_probe_asset_volatility.js`
mide su feed real por WebSocket: rango RELATIVO ≈ 4e-10 (oscila en la 10ª decimal).
El eje del propio gráfico de Binomo está en pasos de 1e-8. Es un índice sintético casi
estático → sobre ese feed no hay edge direccional sostenible con payout 82% (breakeven 54.95%).

=== Objetivo ===
Determinar si existe un activo REAL, VOLÁTIL y streameable por Binomo para migrar la operativa,
y cuantificar su ventaja frente a Z-CRY/IDX.

=== Pasos ===
1. Descubre la lista de activos/rics disponibles. Mira si hay un endpoint de assets o si el
   socket emite una lista al conectar. Revisa `_probe_binomo_ws.js` y `_probe_asset_volatility.js`.
2. Prueba variantes de naming de rics (ejemplos): `EUR/USD`, `EURUSD`, `Z-EUR/USD`, `EUR/USD-OTC`,
   `Z-CRY/BTC`, `BTC/USD`, `Z-XAU/USD`, `CHF/JPY`, `Z-CHF/JPY`, etc.
   Usa: node _probe_asset_volatility.js <ric1> <ric2> ...
3. Para cada ric que SÍ emita `rate`, reporta: rango_rel, ticks totales en 16s, y estabilidad.
4. Compara contra Z-CRY/IDX.

=== ENTREGABLE (español, máx 30 líneas) ===
- Tabla: ric | emite rate (sí/no) | rango_rel | ticks/16s | comentario
- Veredicto: ¿se puede migrar a un activo con volatilidad real? ¿cuál?
- Si NO se puede: confirma con datos que Z-CRY/IDX es el único y que su naturaleza es
  near-static/mean-reverting.
- Recomendación técnica de 2-3 líneas para el implementador.
