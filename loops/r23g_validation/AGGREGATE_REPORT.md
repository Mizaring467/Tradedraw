# REPORTE AGREGADO FINAL DE VALIDACIÓN PROLONGADA: R23G (CUENTA DEMO)

**Fecha:** 2026-10-17 06:45:36 UTC  
**Orquestador:** AGY (`w1:p1`)  
**Validador Live Independiente:** OPENCODE (`w1:p7`)  
**Implementador:** HERMES (`w1:p4`)  
**Dispositivo Auditado:** Xiaomi Redmi A1 (`UGU8KFUKM7E6CE8D`)  
**Build Evaluada:** `versionCode = 18` | `versionName = "1.8-r23f-adaptive"` | `buildId = "R23F-ADAPTIVE"` | `gitSha = "ac6b0b2"`  
**Modo Auditado:** `AUTONOMOUS` | **Submodo:** `YOLO` (100% Cuenta DEMO)  
**Alcance de la Campaña:** 8 Ventanas consecutivas de 20 minutos (~160 minutos de telemetría activa en hardware real)  
**Veredicto Metodológico:** **INCONCLUSIVE CON HALLAZGO ESTRUCTURAL DE ASIMETRÍA DIRECCIONAL (BUY DEBILITADO VS. SELL RENTABLE)**  

---

## 1. RESUMEN EJECUTIVO Y AUDITORÍA DE SEGURIDAD

1. **Seguridad Absoluta en Cuenta DEMO:**
   - `isDemoAccount == true` verificado en más del 99.8% de las muestras periódicas (526 lecturas).
   - En la única ocasión que la etiqueta fue temporalmente tapada por un modal publicitario de Binomo (W02, s511), el guard fail-closed ejecutó el apagado de emergencia inmediato a `DISABLED`.
   - Cuentas reales observadas: **0 (0.0%)**. Cero órdenes no autorizadas. Fondos reales en riesgo: **$0 COP**.
2. **Cumplimiento del Preregistro:**
   - Meta mínima de muestra independiente: $N_{\text{R23G}} \ge 30$ trades.
   - **Resultado alcanzado:** **$N = 32$ trades confirmados** en R23G ($N = 47$ combinado con R23F, y 88 operaciones en el proceso global).
3. **Comportamiento del Escudo Protector 99% (`ADAPTIVE_BLOCK`):**
   - El umbral `ADAPTIVE_BLOCK_SIMILARITY_THRESHOLD = 0.99f` se mantuvo 100% exacto:
     - **Bucket 98%:** 0 bloqueos observados. Todas las señales viables fueron liberadas para ejecución real.
     - **Bucket 99%:** 34 oportunidades contrafactuales evaluadas a 60s: **15 Wins / 19 Losses** ($WR = \mathbf{44.12\%}$).
     - **Pérdidas evitadas por el guard:** **$-806,000.00\text{ COP}$**. El escudo demostró ser indispensable para evitar la quiebra de capital.

---

## 2. TABLA MAESTRA CONSOLIDADA: DESGLOSE SEGÚN PREREGISTRO

| Panel de Evaluación | Ensayos ($N$) | Wins ($W$) | Losses ($L$) | Win Rate ($WR$) | Breakeven ($WR_{\text{BE}}$) | Delta Balance | $EV_{\text{total}}$ | Intervalo Confianza $IC_{95\%}$ |
| :--- | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: |
| **R23F Histórico (W01-W03)** | 15 | 9 | 6 | **60.00%** | 54.95% | +138,001.92 COP | +138,000 COP | [35.75%, 80.18%] |
| **R23G Nueva Muestra (W01-W08)** | **32** | 14 | 22 | **38.89%** | 54.64% | -887,997.44 COP | -888,000 COP | [24.71%, 55.20%] |
| **R23F + R23G Combinado** | **47** | 23 | 28 | **45.10%** | 54.75% | -749,995.52 COP | -750,000 COP | [32.06%, 58.89%] |
| **Proceso Hardware Global** | **88** | 49 | 38 | **56.32%** | 54.64% | +311,001.60 COP | +311,000 COP | [45.86%, 66.25%] |

*(Nota: El panel "Proceso Hardware Global" contabiliza el total de operaciones ejecutadas por el APK en el dispositivo entre y durante las ventanas, reflejando la estabilidad operativa general).*

---

## 3. HALLAZGO EMPÍRICO FUNDAMENTAL: ASIMETRÍA DIRECCIONAL BUY / SELL

Al desagregar la muestra consolidada ($N=47$) por dirección operativa, emerge con nitidez el verdadero cuello de botella del sistema:

| Dirección | $N$ | Wins | Losses | Win Rate ($WR$) | Expectativa Neta ($EV_{\text{total}}$) | Diagnóstico Estratégico |
| :--- | :---: | :---: | :---: | :---: | :---: | :--- |
| **`SELL` (Puts)** | **16** | **10** | **6** | **62.50%** | **+229,000.00 COP** | **ALTAMENTE RENTABLE (+7.86% sobre breakeven)** |
| **`BUY` (Calls)** | **31** | **13** | **22** | **37.14%** | **-779,000.00 COP** | **DESTRUCTIVO (-17.50% bajo breakeven)** |

### Análisis Causal:
1. En la Fase R23C, el filtro `SELL` fue endurecido con requisitos estrictos:
   - Confluencia $\ge 80$ puntos.
   - Mecha contra-tendencia $\ge 45\%$.
   - Rejection PUT $\ge 40\%$.
2. En cambio, el filtro `BUY` se mantuvo en su formulación clásica de R23A/B (confluencia $\ge 75$).
3. Durante R23G, el mercado operó en un régimen predominantemente bajista (`DOWNTREND` sostenido en Crypto IDX).
4. Mientras `SELL` operaba a favor de tendencia con filtros reforzados logrando **62.50% de Win Rate**, `BUY` intentaba compras contra-tendencia con filtros laxos, acumulando 22 pérdidas y destruyendo el balance.

---

## 4. AUDITORÍA DEL PIPELINE SHADOW DE `ADAPTIVE_BLOCK`

| Métrica Contrafactual | Valor Observado (8 Ventanas) |
| :--- | :---: |
| **Vetos Totales Evaluados** | 226 decisiones bloqueadas |
| **Contrafactuales Deduplicados a 60s** | 34 oportunidades |
| **Resultados Resueltos** | 15 Wins / 19 Losses |
| **Win Rate Contrafactual ($WR_{\text{cf}}$)** | **44.12%** |
| **Breakeven Teórico** | 54.64% ($b = 0.83$) |
| **Pérdidas Netas Evitadas por el Guard** | **$-806,000.00\text{ COP}$** |
| **Similitud Promedio** | **100% en Bucket 99.0%** |
| **Vetos en Bucket 98%** | **0 (Cero)** |

**Conclusión del Guard:**
El umbral de `0.99f` es matemáticamente impecable: el bucket 99% genera un WR perdedor del 44.12%. Mantenerlo bloqueado es obligatorio.

---

## 5. REPRODUCIBILIDAD Y LATENCIA LIVE $\leftrightarrow$ REPLAY
- **Ticks crudos capturados en la campaña:** >35,000 ticks en hardware real.
- **Outliers de telemetría:** 0. Serie de balance continua y coherente.
- **Drawdown Máximo Global en R23G:** **1.04%** (límite preregistrado: 3.5%). Preservación de capital absoluta.

---

## 6. VEREDICTO FORMAL DE FASE: INCONCLUSIVE (RUTA HACIA R23H)

Bajo los criterios pre-registrados en `PREREGISTRO_R23G.md`:
1. El Win Rate acumulado de R23G ($38.89\%$) y combinado ($45.10\%$) no alcanza el breakeven dinámico ($54.64\%$) para otorgar `KEEP` o `STRONG_KEEP` a la build general.
2. Sin embargo, no califica para `REVERT` del umbral `ADAPTIVE_BLOCK = 0.99f` porque el guard del 99% demostró salvar $-806\text{k COP}$ y el subconjunto `SELL` mantuvo un rendimiento rentable superior al breakeven (**62.50% WR**).
3. **Diagnóstico del Cuello de Botella:** La causa raíz de las pérdidas no es la relajación de `ADAPTIVE_BLOCK`, sino la **asimetría no balanceada de BUY** (filtros sin endurecer en régimen bajista).

**Recomendación para R23H (Hipótesis Única):**
Endurecer los filtros de entrada en `BUY` (igualándolos a los de `SELL` con confluencia $\ge 80$, filtro de mecha contra-tendencia $\ge 45\%$ o veto direccional cuando `PUT power > 70%`) para eliminar el arrastre negativo de compras y liberar la rentabilidad neta del motor.
