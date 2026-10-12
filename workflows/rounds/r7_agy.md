TAREA — RONDA 7 · CAZA DE ACTIVO VOLÁTIL (agy)

Repo: C:\Users\heidy\Tradedraw. NO edites código. Investiga y reporta.

=== Contexto ===
El bot opera `Z-CRY/IDX` ("Crypto IDX"), que es una LÍNEA MUERTA: rango relativo ~3.7e-10 en 16s.
Sobre un feed plano NO hay edge direccional (el WR oscila ~49% y a veces 0 trades por sobre-filtrado).
Necesitamos migrar a un activo con VOLATILIDAD REAL que el WebSocket de Binomo SÍ transmita.

=== Lo que ya sabemos ===
- Al suscribirse a `Z-EUR/USD`, `Z-GBP/USD`, `Z-USD/JPY` el server responde `{"success":true}` pero
  NO emite rates (posiblemente mercado cerrado o rics incorrectos).
- `Z-CRY/IDX` SÍ emite ~2 ticks/seg.
- Sonda disponible: `node _probe_asset_volatility.js <ric1> <ric2> ...` (read-only, reutiliza la sesión).

=== TU TAREA ===
1. Descubre la LISTA real de activos de Binomo. Posibles fuentes:
   - El propio socket: al hacer join al canal de assets, ¿emite una lista? Prueba mensajes tipo
     ["1","1","assets","phx_join",{}], o mira `_probe_binomo_ws.js`.
   - HTTP público de Binomo (assets/catalog): prueba curl a endpoints tipo
     https://binomo.com/api/... o revisa `BinomoWebSocketClient.kt` por si hay una URL de catálogo.
2. Prueba un SET AMPLIO de rics candidatos con la sonda (16s cada uno). Sugerencias de naming:
   - Cripto: `BTC/USD`, `Z-BTC/USD`, `Z-CRY/BTC`, `Z-CRY/ETH`, `ETH/USD`, `SOL/USD`, `Z-CRY/SOL`
   - Forex: `EUR/USD`, `EUR/USD-OTC`, `Z-EUR/USD-OTC`, `USD/JPY-OTC`, `AUD/CAD`
   - Materias: `XAU/USD`, `Z-XAU/USD`, `GOLD`, `BRENT`
   - Índices: `US500`, `Z-US500`, `DXY`, `Z-DXY`
3. Para cada ric que EMITA rates, reporta rango_rel y ticks/16s. Ordena por volatilidad.
4. Confirma a qué hora (zona del dispositivo America/Bogota) estamos y si el mercado estaba abierto.

=== ENTREGABLE (español, máx 30 líneas) ===
- Tabla: ric | suscribe | emite rates | rango_rel | ticks/16s
- El/los ric(s) con VOLATILIDAD REAL usable ahora mismo
- Si NINGUNO emite: confírmalo con datos y di qué rics son válidos en horario de mercado
- 2-3 recomendaciones para el implementador (cómo cambiar el activo: `ws_active_asset` en SharedPrefs)
