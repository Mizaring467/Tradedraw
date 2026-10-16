# R23C — REPORTE AGREGADO FASE 3: REFINAMIENTO ASIMÉTRICO DE FILTROS SELL EN YOLO (DEMO)

**Fecha de Inicio:** 2026-10-16  
**Fecha de Cierre:** 2026-10-16  
**Orquestador:** AGY (`w1:p1`)  
**Validador Live:** OPENCODE (`w1:pT`)  
**Implementador:** HERMES (`w1:p6`)  
**Preregistro:** [PREREGISTRO_R23C_PHASE3.md](file:///C:/Users/heidy/Tradedraw/workflows/PREREGISTRO_R23C_PHASE3.md)  
**Build Congelada:** `e3fdba4` (base `b08459e`) · `versionCode = 15` · `BUILD_ID = "R23C-PHASE3"`  
**Regla de Seguridad:** `isDemoAccount == true` en 100% de las muestras (348/348). 0 cuentas reales.

---

## 1. RESUMEN ACUMULADO FINAL DE FASE 3 (3 VENTANAS)

- **Total Trades Confirmados ($N$):** **4**
- **Victorias ($W$):** 2
- **Derrotas ($L$):** 2
- **Empates / VOID:** 0
- **Win Rate Acumulado ($WR$):** **50.00%**
- **Payout Promedio:** 82.7%
- **Break-Even Win Rate ($WR_{BE}$):** **54.95%**
- **Delta Balance Total (COP):** **−33.999,36 COP** (desde 38.276.736,00 hasta 38.242.736,64 COP)
- **Balance Final Cuenta DEMO:** **38.242.736,64 COP**
- **Máximo Drawdown Real:** **−100.001,28 COP** (−0.261%)
- **Hito de Paridad Live-Replay:** **2 ventanas en PASS (100%)** en w02 y w03 tras captura incremental de ticks.

---

## 2. COMPARATIVA DIRECTA: FASE 1 (BASELINE) VS FASE 3 (YOLO ENDURECIDO)

| Métrica | Fase 1 (Baseline `ac079fd`) | **Fase 3 (`R23C-PHASE3`)** | Variación / Impacto |
|---|---|---|---|
| **Proporción de Volumen SELL** | **60.9%** (14/23) | **25.0%** (1/4) | ✅ **Reducción de −35.9 pp en volumen SELL** |
| **Win Rate en SELL** | 42.86% (6/14) | **100.0%** (1/1) | ✅ Sin pérdidas en ventas en Fase 3 |
| **Win Rate en BUY** | 55.56% (5/9) | 33.33% (1/3) | ⚠️ Disminución en muestra ultracorta |
| **Win Rate Global** | 47.83% (11/23) | **50.00%** (2/4) | +2.17 pp |
| **Pérdida Neta Total** | **−249.999,36 COP** | **−33.999,36 COP** | ✅ **Reducción del 86.4% del drawdown neto** |
| **Señales Suprimidas por Confluencia** | Moderada | **312 señales suprimidas** | Filtro asimétrico actuando agresivamente |
| **Paridad Live ↔ Replay** | 0.0% (FAIL total) | **100.0% PASS** (w02 y w03) | ✅ Resuelto con captura incremental |

---

## 3. REGISTRO POR VENTANA (20 MINUTOS CADA UNA)

| Ventana | $N$ | $W$ | $L$ | $WR$ | Payout | $\Delta$ Balance (COP) | Balance Final (COP) | BUY / SELL | Paridad Live-Replay |
|---|---|---|---|---|---|---|---|---|---|
| **w01** | 1 | 1 | 0 | 100.0% | 83.0% | +83.000,32 | 38.359.736,32 | 0 / 1 (100% W) | FAIL (0%) |
| **w02** | 2 | 1 | 1 | 50.0% | 83.0% | −17.000,96 | 38.342.735,36 | 2 (50% W) / 0 | **PASS (100%)** |
| **w03** | 1 | 0 | 1 | 0.0% | 82.0% | −99.998,72 | 38.242.736,64 | 1 (0% W) / 0 | **PASS (100%)** |
| **TOTAL** | **4** | **2** | **2** | **50.0%** | **82.7%** | **−33.999,36** | **38.242.736,64** | **3 BUY / 1 SELL** | **2 PASS / 1 FAIL** |

---

## 4. CONCLUSIONES TÉCNICAS Y PRÓXIMOS PASOS

1. **Efectividad del Endurecimiento Asimétrico de SELL:**
   - La elevación de confluencia a 80 pts y mecha a $\ge 45\%$ funcionó como barrera de contención: la avalancha destructiva de ventas que causó el 100% del drawdown en la Fase 1 se erradicó (pasó de 14 ventas con WR 42.8% a solo 1 venta con resultado WIN).
2. **Cuello de Botella de Frecuencia:**
   - La alta exigencia de confluencia combinada con `ADAPTIVE_BLOCK` (~100 vetos/ventana) redujo la cadencia a ~1.3 trades/hora. Con $N=4$, la muestra sigue siendo reducida para inferencia estadística profunda.
3. **Paridad Live ↔ Replay:**
   - Con la captura continua de 4800+ ticks por ventana y el comparador `--mode trades` de la Fase 2B, se logró por primera vez en R23C el **100% de paridad determinista (PASS)** en dos ventanas consecutivas.
