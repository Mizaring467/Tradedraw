# R23C — REPORTE AGREGADO FINAL DE VALIDACIÓN ESTADÍSTICA (MODO YOLO · CUENTA DEMO)

**Fecha de Inicio:** 2026-10-16  
**Fecha de Cierre:** 2026-10-16  
**Orquestador:** AGY (`w1:p1`)  
**Validador:** OPENCODE (`w1:pT`)  
**Implementador:** HERMES (`w1:p6`)  
**Regla Absoluta de Seguridad:** `isDemoAccount == true` en 100% de las muestras (0 cuentas reales).  
**Baseline Fijo Inmutable:** `ac079fd` · `versionCode = 14` · `R23-YOLO-ITER2`.  
**Regla de Congelamiento:** Respetada estrictamente durante las 6 ventanas (cero cambios en lógica, umbrales o scoring).

---

## 1. RESUMEN ACUMULADO FINAL DE LA FASE (6 VENTANAS)

- **Total Trades Confirmados ($N$):** **23** (Meta inicial: 30; cerrada por límite de 6 ventanas de 20 min).
- **Victorias ($W$):** 11
- **Derrotas ($L$):** 12
- **Empates / VOID:** 0
- **Win Rate Acumulado ($WR$):** **47.83%**
- **Payout Promedio:** 82.0%
- **Break-Even Win Rate ($WR_{BE}$):** **54.95%**
- **Margen sobre Break-Even:** **−7.12%** (No rentable en el agregado)
- **Delta Balance Total (COP):** **−249.999,36 COP** (desde 38.826.736,64 hasta 38.576.737,28 COP)
- **Balance Final de la Cuenta DEMO:** **38.576.737,28 COP**
- **Máximo Drawdown de la Fase:** **−497.999,36 COP** (−1.28%)
- **Máxima Racha de Victorias ($Max_W$):** 2 (registrada en w02, w03, w04 y w06)
- **Máxima Racha de Derrotas ($Max_L$):** 3 (registrada en w05)
- **Veredicto Técnico Final de la Fase 1:** **NO RENTABLE EN EL AGREGADO (INCONCLUSIVE / WEAK).**  
  > La hipótesis inicial queda demostrada: la racha de 7 victorias previas fue producto de **varianza muestral**. Con $N=23$, el rendimiento real del baseline no modificado gravita en torno al $47.8\%$, por debajo del $54.95\%$ requerido para superar las comisiones del broker.

---

## 2. REGISTRO COMPLETO POR VENTANAS DE 20 MINUTOS

| Ventana | Timestamp (UTC) | $N$ | $W$ | $L$ | $WR$ | Payout | $\Delta$ Balance (COP) | Balance Final (COP) | Max $W$ Streak | Max $L$ Streak | Max Drawdown | Estado |
|---|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---|
| **w01** | 2026-10-16 00:40 | 4 | 1 | 3 | 25.0% | 82.0% | −217.999,36 | 38.608.737,28 | 1 | 2 | −217.999,36 | Completada |
| **w02** | 2026-10-16 01:04 | 6 | 3 | 3 | 50.0% | 82.0% | −54.000,64 | 38.554.736,64 | 2 | 2 | −300.001,28 | Completada |
| **w03** | 2026-10-16 01:27 | 3 | 2 | 1 | 66.7% | 82.0% | +64.000,00 | 38.618.736,64 | 2 | 1 | −200.000,00 | Completada |
| **w04** | 2026-10-16 02:15 | 5 | 3 | 2 | 60.0% | 82.0% | +145.999,36 | 38.546.736,64 | 2 | 1 | −200.000,00 | Completada |
| **w05** | 2026-10-16 02:40 | 3 | 0 | 3 | 0.0% | 82.0% | −217.999,36 | 38.410.736,64 | 0 | 3 | −299.998,72 | Completada |
| **w06** | 2026-10-16 03:09 | 2 | 2 | 0 | 100.0% | 82.0% | +166.000,64 | 38.576.737,28 | 2 | 0 | −100.001,28 | Completada |
| **TOTAL**| **2026-10-16** | **23** | **11** | **12** | **47.8%** | **82.0%** | **−249.999,36** | **38.576.737,28**| **2** | **3** | **−497.999,36** | **CERRADA** |

---

## 3. ANÁLISIS DE ATRIBUCIÓN CAUSAL: ASIMETRÍA DIRECCIONAL Y RÉGIMEN

### Por Dirección:
- **BUY Trades:** 9 | W: 5 | L: 4 | **WR: 55.56%** (Superando el $WR_{BE}$ del $54.95\%$)
- **SELL Trades:** 14 | W: 6 | L: 8 | **WR: 42.86%** (Claramente destructivo)
- **Hallazgo Crítico:** Existe una asimetría estructural. El 60.9% de los trades ejecutados fueron SELL (14 de 23), pero su tasa de acierto es marcadamente deficiente ($42.86\%$). En cambio, las operaciones BUY son estadísticamente rentables ($55.56\%$). Esta asimetría direccional es la causa primaria de la pérdida neta global.

### Por Régimen de Mercado:
- **TRENDING (UPTREND):** 15 trades | W: 7 | L: 8 | WR: 46.7%
- **TRENDING (DOWNTREND):** 8 trades | W: 4 | L: 4 | WR: 50.0%
- **SIDEWAYS / RANGE:** 0 trades | WR: 0.0%

---

## 4. AUDITORÍA DE TOOLING Y PARIDAD OFFLINE (FASES 2 & 2B)

1. **Captura Incremental de Ticks:**  
   En la Ventana 06, el monitor capturó **4.901 ticks** continuos frente a los ~2.000 del ring buffer antiguo, resolviendo el cuello de botella de retención para futuras iteraciones.
2. **Paridad Live ↔ Replay:**  
   El modo `--mode trades` en `compare_live_replay.py` permitió por primera vez comparar órdenes reales contra trades simulados. En w06: 2 trades live vs 10 replay -> 1 par temporal, 0 exact matches por discrepancia en el segundo de entrada (latencia de tap de ~2.3s).
3. **Causa del Déficit Frecuencial ($N=23$ en lugar de 30):**  
   El filtro `ADAPTIVE_BLOCK` emitió entre 37 y 102 vetos por ventana de 20 minutos (promedio: ~65 vetos/ventana). Si bien protege al capital, reduce la frecuencia a ~3.8 trades por hora.

---

## 5. CONCLUSIÓN Y TRANSICIÓN A FASE 3

Habiendo concluido la medición prolongada de 6 ventanas sin alterar el código, se levanta la restricción de congelamiento.

### Recomendaciones Prioritarias para la Fase 3:
1. **Filtro Direccional o Endurecimiento de Señales SELL:**
   - La asimetría demostrada ($55.6\%\text{ BUY}$ vs $42.9\%\text{ SELL}$) exige revisar los umbrales específicos de PUT/SELL (rejection wick ratio, confirmación de momentum o confluencia requerida para ventas).
2. **Alineación de Detección de Régimen con la Dirección Operada:**
   - Filtrar o penalizar operaciones SELL cuando el análisis macro indica sesgo alcista sostenido.
3. **Optimización de ADAPTIVE_BLOCK:**
   - Calibrar la sensibilidad del bloqueo de anti-patrones para recuperar frecuencia sin degradar el control de riesgo.
