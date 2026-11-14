# R30_LIVE_SESSION_001 — Observador de operaciones (plantilla de registro)

> **Uso:** una fila por operación observada. Llenar SOLO con valores leídos
> de `/status` (127.0.0.1:18080), eventos del engine, capturas de pantalla y
> `/dispatch_recovery`. Distinguir siempre OBSERVADO vs INFERIDO.
> **LIVE_BUILD_ID fijo de la sesión:** `R30-LIVE-FUSION` — commit `c9e52e9`,
> branch `r10-night`, apk sha256 `46b756201b1ac719708827ecb4f08b0e018bd591dae7db3676a3bba5b54870bf`,
> versionName `1.12-r30-live-fusion`, versionCode 22.

## Campos por operación

| Campo | Fuente | Nota |
|---|---|---|
| session_id | constante | R30_LIVE_SESSION_001 |
| decision_id | events engine | jamás reutilizar tras corte |
| trade_id | events engine | jamás reutilizar tras corte |
| ts_señal_utc | events engine | hora de decisión |
| ts_tap_utc | captura/log a11y | latencia señal→tap = diferencia |
| latencia_señal_tap_ms | calculado | métrica clave |
| activo | events ASSET | p.ej. Crypto IDX |
| direccion | events | CALL/PUT |
| estrategia | /status o events | versión congelada (registry v4) |
| confianza | events | según estrategia |
| precio_entrada | broker/WS | OBSERVADO |
| payout_pct | broker | OBSERVADO en la operación |
| stake | /status | configurado |
| segundo_de_vela | calculado | ts_tap mod 60 |
| resultado | broker + engine | WIN/LOSS/TIE (cruzar 2 fuentes) |
| balance_antes / balance_despues | /status | al centavo |
| broker_state | /status health.state | READY/DEGRADED/BLOCKED |
| ws_lastTickAgeMs | /status | frescura del feed |
| isDemoAccount | /status | debe ser true en TODAS |
| health_reasons | /status | si != READY |
| incidente_durante | watchdog_events | si hubo reconexión en vuelo |
| notas | — | solo hechos observados |

## Reglas de integridad

1. **DEMO:** si `isDemoAccount != true` en alguna fila → marcar la fila
   INVÁLIDA y notificar al coordinador inmediatamente (fail-closed).
2. **Sin duplicados:** decision_id/trade_id únicos por sesión; tras corte,
   reconciliar con `/dispatch_recovery` ANTES de re-despachar.
3. **Cruce de fuentes:** resultado WIN/LOSS/TIE debe coincidir en engine y
   broker (saldo); discrepancia → fila dudosa + evento auditor.
4. **Timestamps:** todos UTC ISO-8601; epochs cuadrados al ms.
5. **No inferir:** lo que no se observó queda vacío, nunca "asumido".

## Métricas de ronda (calcular al cierre de cada lote)

- n operaciones, WIN/LOSS/TIE, win rate con IC (Wilson 95%).
- EV = p(win)×payout − p(loss) (por operación, payout medio observado).
- PnL neto = Σ(balance_despues − balance_antes) — validar contra saldos.
- Drawdown máximo (sobre curva de balance).
- Rachas máx ganadoras/perdedoras.
- Latencia señal→tap: media y P95.
- Reconexiones durante la ronda y % recuperación.
