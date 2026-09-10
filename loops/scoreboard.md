# 📊 Scoreboard — Loop de rentabilidad TradeDraw (DEMO)

- **Baseline (equity actual):** 45.4M COP (arranque del experimento: 50.3M COP → −4.9M / −9.7%)
- **Meta:** ≥ 50.3M COP + WR ≥ 60% en ≥ 50 trades, DD ≤ 5%, racha máx pérdidas ≤ 4
- **Stop-loss del experimento:** 43.0M COP → parar y escalar
- **Comando de parada:** `STOP`

| Intento | Hipótesis / cambio | Build | Backtest (trades / WR / profit / DD / racha) | Demo (P&L / trades) | Veredicto |
|---|---|---|---|---|---|
| 0 (baseline) | Código actual en `main` | ⬜ | ⬜ | 45.4M (ref. usuario) | BASELINE |
| 0.1 | Instrumentación: journal CSV en app | ⬜ | N/A | N/A | ⬜ |
| 0.2 | Instrumentación: backtester + fixtures sintéticos | ⬜ | ⬜ | N/A | ⬜ |
| 1 | | | | | |
