TAREA — RONDA 10 · PASO 5 SUPERVISIÓN HORARIA (hermes)

Repo: C:\Users\heidy\Tradedraw (rama r10-night). Dispositivo: UGU8KFUKM7E6CE8D.

=== Objetivo ===
Mantener viva la recolección y reportar progreso SIN revelar métricas de la confirmación.

=== Cada ~60 min hasta ~07:40 ===
1. Verifica el heartbeat: `Get-Content data/ticks/heartbeat.txt`. Si NO avanzó en >3 min, reinicia el
   supervisor:
   `Start-Process powershell -ArgumentList '-NoProfile','-ExecutionPolicy','Bypass','-File','scripts\ticks\supervisor.ps1','-Minutes','300' -WorkingDirectory 'C:\Users\heidy\Tradedraw' -NoNewWindow`
2. Cuenta ticks totales y post-T0 (T0=1791791190823) por activo:
   Para ETH/BTC/SOL: lee data/ticks/<RIC>_2026-10-12.csv y cuenta filas con ts > T0.
3. Comprueba que NO haya subido un hueco nuevo grande (usa scripts/backtest/data_quality.py).
4. AÑADE una línea (máx 3) al final de workflows/rounds/r10_progress.md con:
   `HH:MM | heartbeat OK/REINICIADO | ETH=<postT0>/BTC=<postT0>/SOL=<postT0> ticks post-T0 | huecos nuevos: <n> | N proyectado ETH ≈ <postT0*0.003434>`
   **NUNCA escribas WR, EV ni PnL de la confirmación.** Solo conteos.
5. Repite cada hora. No edites código. No hagas commit.

=== ENTREGABLE ===
Un reporte final en español (máx 12 líneas): hora de inicio/fin, nº de reinicios del supervisor,
ticks post-T0 finales por activo, N proyectado final, y cualquier hueco nuevo detectado.
