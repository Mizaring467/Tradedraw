# 📊 Scoreboard de Rentabilidad TradeDraw — Recuperación 45.4M → 50.3M+ COP

- **Meta de Recuperación:** ≥ 50,300,000 COP en Cuenta DEMO
- **Punto de Partida (Baseline):** 44,961,812.48 COP (−5,338,187.52 COP de la meta)
- **Equity Actual Observado:** 44,653,808.64 COP (−308,003.84 COP neto en consolidación lateral)
- **Stop-Loss del Experimento:** < 43,000,000 COP (Margen de seguridad: +1,653,808 COP)
- **Dispositivos de Verificación:** Xiaomi POCO X6 Pro (`5PPFAACU6H7XHEY9`) / OPPO CPH2727

---

## 📈 Tabla de Iteraciones y Scoreboard

| Intento | Hipótesis / Cambio | Build | Backtest (Trades / WR / P&L / DD / Racha L) | Vivo ADB (Equity ini → fin / Trades / WR) | Veredicto |
| :--- | :--- | :---: | :--- | :--- | :---: |
| **0 (Baseline)** | Código anterior sin reloj sniper estricto | ✅ | 5 trades / 100% WR / +332K / 0% DD / 0L | 47.00M → 44.96M / 18 trades / 44.4% WR | **BASELINE** |
| **1** | Reloj Sniper Universal (:56-:07), Bypass HUD táctil, y descarte de falsas pérdidas | ✅ | 5 trades / 100% WR / +332K / 0% DD / 0L | 44.82M → 45.05M → 44.65M / 11 trades / 45.5% WR (5W / 6L) | **APRENDIZAJE CLAVE** |
| **2 (Candidato Activo)** | Filtro Anti-Whipsaw relativo a DPI/altura de gráfico, detector de alternancia de 4 velas y alineación estricta de tendencia en AUTO_ADAPTIVE | ✅ | 5 trades / 100% WR / +332K / 0% DD / 0L | Pendiente de validación en vivo | **COMPILADO (VERDE)** |

---

## 📉 Curva de Equity Observada (Ground Truth ADB)

| Fecha / Timestamp | Sesión / Intento | Equity Inicial | Equity Pico | Equity Final | Δ Neto | Trades | WR (%) | Frame Evidencia |
| :--- | :--- | :--- | :--- | :--- | :--- | :---: | :---: | :--- |
| 2026-09-10 11:06 UTC | Baseline Start | 50.30M COP | 50.30M COP | 44.96M COP | −5.34M COP | 18 | 44.4% | `loops/journal/live/frame-1789056361.png` |
| 2026-09-10 17:44 UTC | Intento 1 (Sesión Live) | 44.82M COP | 45.05M COP | 44.65M COP | −172,001.28 COP | 11 | 45.5% | `loops/journal/live/current_screen.png` |

---

## 🎯 Hipótesis y Diagnósticos de Rendimiento

1. **Hipótesis 1 (Sniper Window Sub-10s)**: ✅ Implementado — Bloquea órdenes entre `:12` y `:56` para evitar entrar en velas agotadas.
2. **Hipótesis 2 (Anti-Sobreoperativa / Espaciado de Vela)**: ✅ Implementado — Enfriamiento de 45s tras pérdida y 12s tras victoria para alinear con nueva vela.
3. **Hipótesis 3 (Bypass de HUD e Inyección Táctil Directa)**: ✅ Implementado — Oculta temporalmente el HUD (View.INVISIBLE) y clica en la franja Y: 89%-95%.
4. **Hipótesis 4 (Filtro de Momentum Anti-Contra Tendencia)**: ✅ Implementado — Veta CALLs en cascada bajista y PUTs en rally alcista.
5. **Hipótesis 5 (Filtro de Ruido Doji / Rango Estrecho)**: ✅ Implementado — Descarta automáticamente velas sin cuerpo o consolidaciones estrechas.
6. **Hipótesis 6 (Anti-Whipsaw Lateral & Strict Trend Alignment)**: ✅ Implementado en Candidato 2 — Umbrales relativos a la resolución de pantalla (`chartHeight * 0.025f`), veto estricto de estrategias de continuación/impulso en mercados laterales, y detector de 4 velas alternantes.
