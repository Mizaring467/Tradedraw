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

- **Total Trades Confirmados ($N$):** **18** (Meta mínima: 30, meta preferida: 50)
- **Victorias ($W$):** 9
- **Derrotas ($L$):** 9
- **Empates / VOID:** 0
- **Win Rate Acumulado ($WR$):** **50.0%**
- **Payout Promedio:** 82.0%
- **Break-Even Win Rate ($WR_{BE}$):** **54.95%** (con payout al 82%)
- **Margen sobre Break-Even:** **−4.95%**
- **Delta Balance Total (COP):** **−62.000,64 COP** (Recuperado desde el valle de −272k)
- **Balance Actual de la Cuenta DEMO:** **38.546.736,64 COP**
- **Máximo Drawdown Acumulado:** **−300.001,28 COP** (−0.777%)
- **Máxima Racha de Victorias ($Max_W$):** 2 (en w02, w03 y w04)
- **Máxima Racha de Derrotas ($Max_L$):** 2 (en w01 y w02)
- **Estado Actual de la Fase:** **INCONCLUSIVE** (Acumulando muestra: 18 / 30 trades, 60.0% de la meta mínima).

---

## 2. REGISTRO POR VENTANAS DE 20 MINUTOS

| Ventana | Timestamp (UTC) | $N$ | $W$ | $L$ | $WR$ | Payout | $\Delta$ Balance (COP) | Balance Final (COP) | Max $W$ Streak | Max $L$ Streak | Max Drawdown | Estado |
|---|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---|
| **w01** | 2026-10-16 00:40 | 4 | 1 | 3 | 25.0% | 82.0% | −217.999,36 | 38.608.737,28 | 1 | 2 | −217.999,36 | Completada |
| **w02** | 2026-10-16 01:04 | 6 | 3 | 3 | 50.0% | 82.0% | −54.000,64 | 38.554.736,64 | 2 | 2 | −300.001,28 | Completada |
| **w03** | 2026-10-16 01:27 | 3 | 2 | 1 | 66.7% | 82.0% | +64.000,00 | 38.618.736,64 | 2 | 1 | −200.000,00 | Completada |
| **w04** | 2026-10-16 02:15 | 5 | 3 | 2 | 60.0% | 82.0% | +145.999,36 | 38.546.736,64 | 2 | 1 | −200.000,00 | Completada |
| **w05** | Pendiente | - | - | - | - | - | - | - | - | - | - | En cola |
| **w06** | Pendiente | - | - | - | - | - | - | - | - | - | - | En cola |

---

## 3. DISTRIBUCIÓN POR DIRECCIÓN Y RÉGIMEN

### Por Dirección:
- **BUY Trades:** 6 | W: 4 | L: 2 | **WR: 66.7%** (Superando el break-even)
- **SELL Trades:** 12 | W: 5 | L: 7 | **WR: 41.7%** (Sub-óptimo)
- **Asimetría Direccional Detectada:** El 66.7% de los disparos han sido SELL (12/18). Sin embargo, el WR de BUY es notablemente superior (66.7% vs 41.7%). Las últimas 4 compras consecutivas han sido ganadoras.

### Por Régimen de Mercado:
- **TRENDING (UPTREND):** 15 trades | W: 7 | L: 8 | WR: 46.7%
- **TRENDING (DOWNTREND):** 3 trades | W: 2 | L: 1 | WR: 66.7%
- **SIDEWAYS / RANGE:** 0 trades | WR: 0.0%

---

## 4. REGISTRO DE RACHAS NOTABLES ($\ge 5$ consecutivas)
*(Ninguna racha $\ge 5$ registrada en R23C. Secuencia combinada w01+w02+w03+w04: L, L, W, L, L, L, W, W, L, W, L, W, W, L, W, W, L, W)*

---

## 5. REGLAS DE PARADA Y CRITERIOS DE SALIDA
- **Meta Mínima:** $N \ge 30$ trades resueltos.
- **Progreso Actual:** 18 / 30 trades (60.0% de la meta mínima acumulada).
- **Límite de Ventanas:** 4 de 6 ventanas ejecutadas.
- **Abort Inmediato:** Si `isDemoAccount != true` o fallo de seguridad.
