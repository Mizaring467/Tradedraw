# 📊 Scoreboard de Rentabilidad TradeDraw — Recuperación 45.4M → 50.3M+ COP

- **Meta de Recuperación:** ≥ 50,300,000 COP en Cuenta DEMO
- **Punto de Partida (Baseline):** 44,961,812.48 COP (−5,338,187.52 COP de la meta)
- **Stop-Loss del Experimento:** < 43,000,000 COP
- **Dispositivo de Verificación:** Xiaomi POCO X6 Pro (`192.168.1.185:5555`)

---

## 📈 Tabla de Iteraciones y Scoreboard

| Intento | Hipótesis / Cambio | Build | Backtest (Trades / WR / P&L / DD / Racha L) | Vivo ADB (Equity ini → fin / Trades / WR) | Veredicto |
| :--- | :--- | :---: | :--- | :--- | :---: |
| **0 (Baseline)** | Código actual en main con Sniper Clock y TradeJournalLogger | ✅ | 5 trades / 100% WR / +332K / 0% DD / 0L | 47.00M → 44.96M / 18 trades / 44.4% WR | **BASELINE** |

---

## 📉 Curva de Equity Observada (Ground Truth ADB)

| Fecha / Timestamp | Sesión / Intento | Equity Inicial | Equity Final | Δ Neto | Trades | WR (%) | Frame Evidencia |
| :--- | :--- | :--- | :--- | :--- | :---: | :---: | :--- |
| 2026-09-10 11:06 UTC | Baseline Start | 50.30M COP | 44.96M COP | −5.34M COP | 18 | 44.4% | `loops/journal/live/frame-1789056361.png` |

---

## 🎯 5 Primeras Hipótesis Priorizadas para Recuperación

1. **Hipótesis 1 (Sniper Window Sub-10s)**: Bloquear todas las órdenes entre los segundos `:12` y `:56` para evitar operar velas ya avanzadas que tienen 60% menos tiempo de vida útil.
2. **Hipótesis 2 (Anti-Sobreoperativa / Espaciado de Vela)**: Forzar enfriamiento de 45s tras una derrota y 15s tras una victoria para que el mercado dibuje una nueva vela antes de volver a entrar.
3. **Hipótesis 3 (Filtro de Momentum Anti-Contra Tendencia)**: Bloquear `PUT` si hay 3+ velas verdes consecutivas sin confirmación de resistencia institucional, y bloquear `CALL` en cascada bajista.
4. **Hipótesis 4 (Filtro de Ruido Doji / Rango Estrecho)**: Rechazar entradas si el promedio de cuerpo de las últimas 5 velas es < 12px (mercado OTC sin dirección).
5. **Hipótesis 5 (Sincronización Estricta de IA)**: Descartar respuestas de IA remota si llegan después del segundo `:08` de la vela para evitar entradas desfasadas por latencia de red.
