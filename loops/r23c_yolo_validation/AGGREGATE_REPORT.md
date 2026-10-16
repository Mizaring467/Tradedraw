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

- **Total Trades Confirmados ($N$):** **4** (Meta mínima: 30)
- **Victorias ($W$):** 1
- **Derrotas ($L$):** 3
- **Empates / VOID:** 0
- **Win Rate Acumulado ($WR$):** **25.0%**
- **Payout Promedio:** 82.0%
- **Break-Even Win Rate ($WR_{BE}$):** **54.95%** (con payout al 82%)
- **Margen sobre Break-Even:** **−29.95%**
- **Delta Balance Total (COP):** **−217.999,36 COP**
- **Máximo Drawdown Acumulado:** **−217.999,36 COP** (−0.561%)
- **Máxima Racha de Victorias ($Max_W$):** 1
- **Máxima Racha de Derrotas ($Max_L$):** 2
- **Estado Actual de la Fase:** **INCONCLUSIVE** (Muestra inicial $N=4$, ventana con varianza adversa).

---

## 2. REGISTRO POR VENTANAS DE 20 MINUTOS

| Ventana | Timestamp (UTC) | $N$ | $W$ | $L$ | $WR$ | Payout | $\Delta$ Balance (COP) | Balance Final (COP) | Max $W$ Streak | Max $L$ Streak | Max Drawdown | Estado |
|---|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---|
| **w01** | 2026-10-16 00:40 | 4 | 1 | 3 | 25.0% | 82.0% | −217.999,36 | 38.608.737,28 | 1 | 2 | −217.999,36 | Completada |
| **w02** | Pendiente | - | - | - | - | - | - | - | - | - | - | En cola |
| **w03** | Pendiente | - | - | - | - | - | - | - | - | - | - | En cola |
| **w04** | Pendiente | - | - | - | - | - | - | - | - | - | - | En cola |
| **w05** | Pendiente | - | - | - | - | - | - | - | - | - | - | En cola |
| **w06** | Pendiente | - | - | - | - | - | - | - | - | - | - | En cola |

---

## 3. DISTRIBUCIÓN POR DIRECCIÓN Y RÉGIMEN

### Por Dirección:
- **BUY Trades:** 2 | W: 0 | L: 2 | WR: 0.0%
- **SELL Trades:** 2 | W: 1 | L: 1 | WR: 50.0%

### Por Régimen de Mercado:
- **TRENDING (UPTREND):** 4 trades | W: 1 | L: 3 | WR: 25.0%
- **SIDEWAYS / RANGE:** 0 trades | WR: 0.0%

---

## 4. REGISTRO DE RACHAS NOTABLES ($\ge 5$ consecutivas)
*(Ninguna racha $\ge 5$ registrada en R23C. Secuencia de w01: L, L, W, L)*

---

## 5. REGLAS DE PARADA Y CRITERIOS DE SALIDA
- **Meta Mínima:** $N \ge 30$ trades resueltos.
- **Progreso Actual:** 4 / 30 trades (13.3% de la meta mínima acumulada).
- **Límite de Ventanas:** 1 de 6 ventanas ejecutadas.
- **Abort Inmediato:** Si `isDemoAccount != true` o fallo de seguridad.
