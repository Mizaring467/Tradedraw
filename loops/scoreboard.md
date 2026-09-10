# 📊 Scoreboard — Loop de rentabilidad TradeDraw (DEMO)

- **Baseline (equity actual):** 45.4M COP (arranque del experimento: 50.3M COP → −4.9M / −9.7%)
- **Meta de parada:** ≥ 50.3M COP verificado por ADB + ≥ 50 trades + WR ≥ 60% + DD ≤ 5% +
  racha máx pérdidas ≤ 4 + **sesión de confirmación de ≥ 20 trades sin devolver lo ganado**
- **Stop-loss del experimento:** 43.0M COP → `force-stop`, revertir y escalar
- **Duración:** sin límite de tiempo ni de intentos. Se para por resultado (§11 del loop).

📈 **Progreso:** 45.4M ▸ `<equity actual>` ▸ meta 50.3M — recuperado `<p>%` de los 4.9M

---

## Intentos

| Intento | Hipótesis / cambio | Build | Backtest (trades / WR / profit / DD / racha) | Vivo ADB (equity ini → fin / trades / WR) | Veredicto |
|---|---|---|---|---|---|
| 0 (baseline) | Código actual en `main` | ⬜ | ⬜ | ⬜ | BASELINE |
| 0.1 | Instrumentación: journal CSV en app | ⬜ | N/A | N/A | ⬜ |
| 0.2 | Instrumentación: backtester + fixtures | ⬜ | ⬜ | N/A | ⬜ |
| 0.3 | Sesión de observación del baseline (cómo pierde) | N/A | N/A | ⬜ | ⬜ |
| 1 | | | | | |

Veredictos: `ACEPTADO` · `RECHAZADO` · `SOSPECHOSO` (pocos trades / posible overfit)

---

## Curva de equity (sesiones en vivo por ADB)

Cada fila exige su screenshot de evidencia. Cifra sin frame = cifra inválida.

| Fecha | Sesión / intento | Cuenta DEMO ✅ | Equity inicial | Equity final | Δ | Trades | WR | DD | Racha máx | Frame (ruta) | Notas |
|---|---|---|---|---|---|---|---|---|---|---|---|
| | | | | | | | | | | | |

---

## Incidentes (bot detenido, comportamiento peligroso, ADB caído)

| Fecha | Qué pasó | Acción tomada | Evidencia |
|---|---|---|---|
| | | | |

---

## Aprendizajes (qué NO volver a intentar y por qué)

- …
