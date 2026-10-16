# R23C — REPORTE AGREGADO ACUMULATIVO DE VALIDACIÓN ESTADÍSTICA (MODO YOLO · CUENTA DEMO)

**Fecha de Inicio:** 2026-10-16  
**Orquestador:** AGY (`w1:p1`)  
**Validador:** OPENCODE (`w1:pT`)  
**Implementador:** HERMES (`w1:p6`)  
**Regla Absoluta de Seguridad:** `isDemoAccount == true` fail-closed (0 cuentas reales).  
**Baseline Fijo Inmutable:** `ac079fd` · `versionCode = 14` · `R23-YOLO-ITER2`.  
**Regla de Congelamiento:** Prohibido modificar lógica, thresholds, scoring o stake durante la fase estadística.

---

## 1. RESUMEN ACUMULADO GLOBAL (R23C)

- **Total Trades Confirmados ($N$):** 0
- **Victorias ($W$):** 0
- **Derrotas ($L$):** 0
- **Empates / VOID:** 0
- **Win Rate Acumulado ($WR$):** 0.0%
- **Payout Promedio:** ~82.0%
- **Break-Even Win Rate ($WR_{BE}$):** **54.95%** (con payout al 82%)
- **Margen sobre Break-Even:** 0.0%
- **Delta Balance Total (COP):** $0.00\text{ COP}$
- **Máximo Drawdown Acumulado:** $0.00\text{ COP}$
- **Máxima Racha de Victorias ($Max_W$):** 0
- **Máxima Racha de Derrotas ($Max_L$):** 0
- **Estado Actual de la Fase:** **INCONCLUSIVE** (acumulando muestra, meta $N \ge 30$).

---

## 2. REGISTRO POR VENTANAS DE 20 MINUTOS

| Ventana | Timestamp (UTC) | $N$ | $W$ | $L$ | $WR$ | Payout | $\Delta$ Balance (COP) | Balance Final (COP) | Max $W$ Streak | Max $L$ Streak | Max Drawdown | Estado |
|---|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---|
| **w01** | Pendiente | - | - | - | - | - | - | - | - | - | - | En cola |
| **w02** | Pendiente | - | - | - | - | - | - | - | - | - | - | En cola |
| **w03** | Pendiente | - | - | - | - | - | - | - | - | - | - | En cola |
| **w04** | Pendiente | - | - | - | - | - | - | - | - | - | - | En cola |
| **w05** | Pendiente | - | - | - | - | - | - | - | - | - | - | En cola |
| **w06** | Pendiente | - | - | - | - | - | - | - | - | - | - | En cola |

---

## 3. DISTRIBUCIÓN POR DIRECCIÓN Y RÉGIMEN

### Por Dirección:
- **BUY Trades:** 0 | W: 0 | L: 0 | WR: 0.0%
- **SELL Trades:** 0 | W: 0 | L: 0 | WR: 0.0%

### Por Régimen de Mercado:
- **TRENDING:** 0 trades | WR: 0.0%
- **SIDEWAYS / RANGE:** 0 trades | WR: 0.0%

---

## 4. REGISTRO DE RACHAS NOTABLES ($\ge 5$ consecutivas)
*(Ninguna racha $\ge 5$ registrada aún en R23C)*

---

## 5. REGLAS DE PARADA Y CRITERIOS DE SALIDA
- **Meta Mínima:** $N \ge 30$ trades resueltos.
- **Meta Preferida:** $N \ge 50$ trades resueltos.
- **Límite de Ventanas:** Máximo 6 ventanas consecutivas sin alterar código.
- **Abort Inmediato:** Si `isDemoAccount != true` o fallo de seguridad.
