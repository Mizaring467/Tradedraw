# 📊 Scoreboard de Validación en Vivo · TradeDraw (Profitability Loop)

## 🛡️ Kill Switch de Seguridad
- **Límite Mínimo de Equity (Kill Switch):** `Col$40,000,000.00 COP`
- **Saldo Actual Auditado:** `Col$40,968,808.96 COP`
- **Margen de Seguridad:** `+$968,808.96 COP` (Seguro · Operación permitida ✅)
- **Estado del Kill Switch:** 🟢 INACTIVO (Dentro de zona segura)

---

## 📈 Marcador Global y Rendimiento

| Métrica | Valor Actual | Objetivo / Umbral | Estado |
| :--- | :---: | :---: | :---: |
| **Operaciones Totales** | `0` | $\ge 20$ trades de muestra | 🟡 En espera |
| **Victorias (W)** | `0` | - | - |
| **Derrotas (L)** | `0` | - | - |
| **Empates / Canceladas** | `0` | - | - |
| **Win Rate (WR)** | `0.0%` | $\ge 58.0\%$ | 🟡 Sin muestra |
| **Racha Actual** | `0` | Max drawdown: 3L | 🟢 Estable |
| **Nivel Martingala** | `M0` | Max permitido: M2 | 🟢 Seguro |
| **Monto de Entrada Base** | `$1.0 / Col$100,000` | Fijo | 🟢 Calibrado |
| **Activo Auditado** | `Crypto IDX` | OTC 82% payout | 🟢 Sincronizado |
| **Modo Operativo** | `WebSocket Headless (0ms)` | Latencia < 50ms | 🟢 Óptimo |

---

## 🕒 Registro de Frecuencia y Auditoría de Trades

| # | Timestamp (ISO) | Acción | Estrategia / Patrón | Saldo Base | Saldo Cierre | Duración | Resultado | Equity Actual | Delta COP |
| :-: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: |
| - | *Esperando primera operación...* | - | - | - | - | - | - | `$40,968,808.96` | `+$0.00` |

---

## 🔍 Diagnóstico Cuantitativo del Agente Observador
1. **Verificación de Red y Flujo Headless:**
   - WebSocket conectado al feed en vivo de Binomo (`Z-CRY/IDX` @ `641.8674`).
   - Latencia de cálculo: `0ms` (procesamiento local en memoria).
2. **Supervisión de Filtros Cuantitativos:**
   - Filtro de Timing: Ventana de entrada estricta en segundos `:58` a `:03` y retroceso `:02` a `:05`. Veto en zona muerta `:15` a `:55`.
   - Filtro Anti-Chop: Detectando consolidación lateral (`Dist S: 50%`). Esperando ruptura o impulso direccional claro.
3. **Control de Frecuencia:**
   - Se auditará que el bot no sobreopere en falso impulso ni ejecute más de 1 trade por vela de 1 minuto.
