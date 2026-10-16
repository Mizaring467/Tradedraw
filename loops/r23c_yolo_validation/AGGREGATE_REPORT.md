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

- **Total Trades Confirmados ($N$):** **10** (Meta mínima: 30, meta preferida: 50)
- **Victorias ($W$):** 4
- **Derrotas ($L$):** 6
- **Empates / VOID:** 0
- **Win Rate Acumulado ($WR$):** **40.0%**
- **Payout Promedio:** 82.0%
- **Break-Even Win Rate ($WR_{BE}$):** **54.95%** (con payout al 82%)
- **Margen sobre Break-Even:** **−14.95%**
- **Delta Balance Total (COP):** **−272.000,00 COP**
- **Balance Actual de la Cuenta DEMO:** **38.554.736,64 COP**
- **Máximo Drawdown Acumulado:** **−300.001,28 COP** (−0.777%)
- **Máxima Racha de Victorias ($Max_W$):** 2 (en w02)
- **Máxima Racha de Derrotas ($Max_L$):** 2 (en w01 y w02)
- **Estado Actual de la Fase:** **INCONCLUSIVE** (Acumulando muestra: 10 / 30 trades, 33.3% de la meta mínima).

---

## 2. REGISTRO POR VENTANAS DE 20 MINUTOS

| Ventana | Timestamp (UTC) | $N$ | $W$ | $L$ | $WR$ | Payout | $\Delta$ Balance (COP) | Balance Final (COP) | Max $W$ Streak | Max $L$ Streak | Max Drawdown | Estado |
|---|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---|
| **w01** | 2026-10-16 00:40 | 4 | 1 | 3 | 25.0% | 82.0% | −217.999,36 | 38.608.737,28 | 1 | 2 | −217.999,36 | Completada |
| **w02** | 2026-10-16 01:04 | 6 | 3 | 3 | 50.0% | 82.0% | −54.000,64 | 38.554.736,64 | 2 | 2 | −300.001,28 | Completada |
| **w03** | Pendiente | - | - | - | - | - | - | - | - | - | - | En cola |
| **w04** | Pendiente | - | - | - | - | - | - | - | - | - | - | En cola |
| **w05** | Pendiente | - | - | - | - | - | - | - | - | - | - | En cola |
| **w06** | Pendiente | - | - | - | - | - | - | - | - | - | - | En cola |

---

## 3. DISTRIBUCIÓN POR DIRECCIÓN Y RÉGIMEN

### Por Dirección:
- **BUY Trades:** 2 | W: 0 | L: 2 | WR: 0.0%
- **SELL Trades:** 8 | W: 4 | L: 4 | WR: 50.0%
- **Sesgo Direccional Observado:** El 80% de los trades ejecutados han sido **SELL** (8/10), a pesar de que el régimen de mercado detectado por el feed fue UPTREND en >80% de las muestras.

### Por Régimen de Mercado:
- **TRENDING (UPTREND):** 10 trades | W: 4 | L: 6 | WR: 40.0%
- **SIDEWAYS / RANGE:** 0 trades | WR: 0.0%

---

## 4. REGISTRO DE RACHAS NOTABLES ($\ge 5$ consecutivas)
*(Ninguna racha $\ge 5$ registrada en R23C. Secuencia combinada w01+w02: L, L, W, L, L, L, W, W, L, W)*

---

## 5. REGLAS DE PARADA Y CRITERIOS DE SALIDA
- **Meta Mínima:** $N \ge 30$ trades resueltos.
- **Progreso Actual:** 10 / 30 trades (33.3% de la meta mínima acumulada).
- **Límite de Ventanas:** 2 de 6 ventanas ejecutadas.
- **Abort Inmediato:** Si `isDemoAccount != true` o fallo de seguridad.
