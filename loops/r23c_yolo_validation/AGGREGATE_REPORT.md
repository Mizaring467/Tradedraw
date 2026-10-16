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

- **Total Trades Confirmados ($N$):** **21** (Meta mínima: 30, meta preferida: 50)
- **Victorias ($W$):** 9
- **Derrotas ($L$):** 12
- **Empates / VOID:** 0
- **Win Rate Acumulado ($WR$):** **42.86%**
- **Payout Promedio:** 82.0%
- **Break-Even Win Rate ($WR_{BE}$):** **54.95%** (con payout al 82%)
- **Margen sobre Break-Even:** **−12.09%**
- **Delta Balance Total (COP):** **−416.000,00 COP** (desde 38.826.736,64 hasta 38.410.736,64 COP)
- **Balance Actual de la Cuenta DEMO:** **38.410.736,64 COP**
- **Máximo Drawdown Acumulado:** **−497.999,36 COP** (−1.28%)
- **Máxima Racha de Victorias ($Max_W$):** 2 (en w02, w03 y w04)
- **Máxima Racha de Derrotas ($Max_L$):** 3 (en w05: L, L, L)
- **Estado Actual de la Fase:** **INCONCLUSIVE / WEAK** (Acumulando muestra: 21 / 30 trades, 70.0% de la meta mínima).

---

## 2. REGISTRO POR VENTANAS DE 20 MINUTOS

| Ventana | Timestamp (UTC) | $N$ | $W$ | $L$ | $WR$ | Payout | $\Delta$ Balance (COP) | Balance Final (COP) | Max $W$ Streak | Max $L$ Streak | Max Drawdown | Estado |
|---|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---|
| **w01** | 2026-10-16 00:40 | 4 | 1 | 3 | 25.0% | 82.0% | −217.999,36 | 38.608.737,28 | 1 | 2 | −217.999,36 | Completada |
| **w02** | 2026-10-16 01:04 | 6 | 3 | 3 | 50.0% | 82.0% | −54.000,64 | 38.554.736,64 | 2 | 2 | −300.001,28 | Completada |
| **w03** | 2026-10-16 01:27 | 3 | 2 | 1 | 66.7% | 82.0% | +64.000,00 | 38.618.736,64 | 2 | 1 | −200.000,00 | Completada |
| **w04** | 2026-10-16 02:15 | 5 | 3 | 2 | 60.0% | 82.0% | +145.999,36 | 38.546.736,64 | 2 | 1 | −200.000,00 | Completada |
| **w05** | 2026-10-16 02:40 | 3 | 0 | 3 | 0.0% | 82.0% | −217.999,36 | 38.410.736,64 | 0 | 3 | −299.998,72 | Completada |
| **w06** | Pendiente | - | - | - | - | - | - | - | - | - | - | En cola |

---

## 3. DISTRIBUCIÓN POR DIRECCIÓN Y RÉGIMEN

### Por Dirección:
- **BUY Trades:** 8 | W: 4 | L: 4 | **WR: 50.0%** (2 L en w05 bajo DOWNTREND)
- **SELL Trades:** 13 | W: 5 | L: 8 | **WR: 38.5%** (Sub-óptimo)
- **Asimetría Direccional Detectada:** El 61.9% de los disparos han sido SELL (13/21). El WR de SELL (38.5%) continúa arrastrando el sistema hacia abajo. En w05, los BUY fallaron bajo un régimen de DOWNTREND predominante (70/117 muestras).

### Por Régimen de Mercado:
- **TRENDING (UPTREND):** 15 trades | W: 7 | L: 8 | WR: 46.7%
- **TRENDING (DOWNTREND):** 6 trades | W: 2 | L: 4 | WR: 33.3%
- **SIDEWAYS / RANGE:** 0 trades | WR: 0.0%

---

## 4. REGISTRO DE RACHAS NOTABLES ($\ge 5$ consecutivas)
*(Ninguna racha $\ge 5$ registrada en R23C. Secuencia combinada w01..w05: L, L, W, L, L, L, W, W, L, W, L, W, W, L, W, W, L, W, L, L, L)*

---

## 5. REGLAS DE PARADA Y CRITERIOS DE SALIDA
- **Meta Mínima:** $N \ge 30$ trades resueltos.
- **Progreso Actual:** 21 / 30 trades (70.0% de la meta mínima acumulada).
- **Límite de Ventanas:** 5 de 6 ventanas ejecutadas.
- **Abort Inmediato:** Si `isDemoAccount != true` o fallo de seguridad.
