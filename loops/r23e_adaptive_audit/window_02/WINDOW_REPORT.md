# INFORME DE AUDITORÍA OPENCODE: R23E — VENTANA 02 DE 06 (CUENTA DEMO)

**Fecha:** 2026-10-16 23:15:00 UTC  
**Auditor Independiente:** OPENCODE (`w1:p7`)  
**Orquestador:** AGY (`w1:p1`)  
**Dispositivo Auditado:** Xiaomi Redmi A1 (`UGU8KFUKM7E6CE8D`)  
**Build Identificada:** `versionCode = 17` | `versionName = "1.7-r23e-shadow"` | `buildId = "R23E-SHADOW"` | `gitSha = "08073c3"`  
**Modo Auditado:** `AUTONOMOUS` | **Submodo:** `YOLO` (100% Cuenta DEMO)  
**Duración de la Ventana:** 20.0 minutos nominales (85 muestras de telemetría a ~10s)  
**Cierre Nominal / Real:** 1792192264 / 1792192274 (settlement grace ejecutado: 9.9s)  
**Veredicto Global:** **PASS IMPECABLE (100% DE CRITERIOS AUDITADOS CUMPLIDOS)**

---

## 1. RESUMEN EJECUTIVO Y VEREDICTO DE SEGURIDAD

1. **Seguridad DEMO Absoluta:**
   - `isDemoAccount == true` verificado en el 100.0% de las muestras (85 de 85 lecturas periódicas).
   - Cuentas reales observadas: 0 (0.0%). Cero fondos reales expuestos.
2. **Dinámica de Balance e Inmunidad:**
   - Saldo inicial: **38,283,735.04 COP**.
   - Saldo final validado: **38,283,735.04 COP** (Δ = +0.00 COP).
   - Drawdown máximo validado: **0.00%**.
   - Outliers de telemetría filtrados: 0.
3. **Ejecución y Aislamiento de Taps:**
   - Taps despachados durante la ventana: **0 taps** (acumulado histórico del proceso congelado en 2 taps procedentes de W01).
   - Operaciones reales ejecutadas: 0 trades (`trades.csv` con 0 filas de datos).
4. **Validación del Pipeline Contrafactual Shadow:**
   - El fix de extracción de acción (`action`) implementado en `957ce13` funcionó de manera óptima: se evaluaron 89 vetos `ADAPTIVE_BLOCK`, deduplicando 81 ráfagas y registrando **8 operaciones contrafactuales shadow independientes**.
   - 7 operaciones contrafactuales fueron resueltas contra los 4,494 ticks de mercado en vivo (`ticks_live.csv`).
   - 1 operación quedó pendiente por proximidad al cierre de la ventana.

---

## 2. TABLA COMPARATIVA: TRADES REALES VS. CONTRAFACTUALES VETADOS (VENTANA 02)

| Métrica | Órdenes Reales Aceptadas | Pipeline Contrafactual Shadow (`ADAPTIVE_BLOCK`) |
| :--- | :---: | :---: |
| **Población Total ($N$)** | 0 | 89 vetos evaluados / **8 contrafactuales únicos** |
| **Taps Despachados** | **0** | **0 (Cero taps absolutos)** |
| **Impacto en Saldo** | **+0.00 COP** | **0.00 COP (Cero impacto en balance)** |
| **Wins ($W$)** | 0 | **4** (CF_WIN) |
| **Losses ($L$)** | 0 | **3** (CF_LOSS) |
| **Sin Resolver / Pendientes** | 0 | **1** (al cierre de ventana) |
| **Win Rate ($WR$)** | **N/A** | **57.14%** (Breakeven ref: 54.95%) |
| **EV Teórico ($EV$)** | **+0.00 COP** | **+28,000.00 COP** (unitario: +4,000.00 COP) |
| **Estado en `trades.csv`** | 0 registros | **0 registros (Aislamiento verificado)** |
| **Almacenamiento** | `trades.csv` | `counterfactual_adaptive_block.csv` |

---

## 3. AUDITORÍA CRÍTICA CONTRA LOS 6 CRITERIOS OBLIGATORIOS

| Criterio | Requisito | Estado | Evidencia Observada en W02 |
| :--- | :--- | :---: | :--- |
| **1. Cero Taps Contrafactuales** | Los vetos nunca generan taps; `tapsDespachados` solo sube en órdenes reales. | **PASS** | `tapsDespachados = 2` constante en las 85 muestras (Δtaps = 0). Cero taps por los 89 vetos. |
| **2. Cero Impacto en Balance** | Los contrafactuales no alteran el saldo de la cuenta DEMO. | **PASS** | Saldo inicial 38,283,735.04 = Saldo final 38,283,735.04 (Δ = +0.00 COP). Drawdown = 0.00%. |
| **3. Cero Impacto en Contadores** | `totalWins` y `totalLosses` del motor no se modifican por contrafactuales. | **PASS** | Contadores del motor permanecieron en W=0, L=1 durante toda la ventana. |
| **4. Aislamiento de `trade_id`** | Los contrafactuales no aparecen en `trades.csv`. | **PASS** | `trades.csv` contiene únicamente la cabecera (0 filas de trades reales). |
| **5. Deduplicación por Vela/Dirección** | No permitir solapamientos en 60s por dirección. | **PASS** | 81 eventos solapados en ráfaga fueron correctamente deduplicados; solo 8 pasaron al CSV. |
| **6. Resolución Contrafactual Válida** | Cada contrafactual con `entry_price`, `exit_price`, `result` y `theoretical_ev`. | **PASS** | 7 de 7 resueltos con ticks reales; 4 WIN (+82,000 COP c/u), 3 LOSS (-100,000 COP c/u). |

---

## 4. DESGLOSE DETALLADO DEL PIPELINE CONTRAFACTUAL SHADOW (W02)

### A. Registro Individual de Contrafactuales (`counterfactual_adaptive_block.csv`)
1. **CF-43063040... (1792191065917):** `SELL` | Entry: 641.867301745 | Exit: 641.867301055 | **WIN** | Score: 88 pts | Sim: 98% | EV: +82,000 COP
2. **CF-d12516aa... (1792191301108):** `SELL` | Entry: 641.867301750 | Exit: 641.867301535 | **WIN** | Score: 95 pts | Sim: 99% | EV: +82,000 COP
3. **CF-aaa82e84... (1792191365103):** `BUY`  | Entry: 641.867301555 | Exit: 641.867301815 | **WIN** | Score: 75 pts | Sim: 98% | EV: +82,000 COP
4. **CF-15eb773b... (1792191425909):** `SELL` | Entry: 641.867301790 | Exit: 641.867301785 | **WIN** | Score: 90 pts | Sim: 99% | EV: +82,000 COP
5. **CF-0aa5fb27... (1792191482305):** `BUY`  | Entry: 641.867301670 | Exit: 641.867301555 | **LOSS** | Score: 83 pts | Sim: 99% | EV: -100,000 COP
6. **CF-597d5a7a... (1792191605911):** `BUY`  | Entry: 641.867301915 | Exit: 641.867301465 | **LOSS** | Score: 78 pts | Sim: 99% | EV: -100,000 COP
7. **CF-45ba654d... (1792192023504):** `BUY`  | Entry: 641.867301885 | Exit: 641.867301730 | **LOSS** | Score: 90 pts | Sim: 99% | EV: -100,000 COP
8. **CF-b41e59d5... (1792192085537):** `BUY`  | Entry: 641.867301795 | Exit: [Pendiente]   | **PEND** | Score: 90 pts | Sim: 99% | EV: N/A

### B. Distribución por Buckets de Similitud (Preregistro §8.3)
- **Bucket [98% – 100%]:** $n = 7$ evaluados (4 W, 3 L, 0 VOID) $\implies$ **WR = 57.14%**
  - Sub-análisis:
    - Similitud 98%: 2 operaciones (2 W, 0 L) $\implies$ **WR = 100.0%**
    - Similitud 99%: 5 operaciones (2 W, 3 L) $\implies$ **WR = 40.0%**

### C. Distribución por Buckets de Score de Confluencia (Preregistro §8.4)
- **Bucket [75 – 79 pts]:** $n = 2$ (1 W, 1 L) $\implies$ **WR = 50.0%**
- **Bucket [80 – 84 pts]:** $n = 1$ (0 W, 1 L) $\implies$ **WR = 0.0%**
- **Bucket [85 – 100 pts]:** $n = 4$ (3 W, 1 L) $\implies$ **WR = 75.0%**

---

## 5. TABLA COMPARATIVA CONSOLIDADA ACUMULADA (VENTANA 01 + VENTANA 02)

A continuación se presenta la consolidación acumulada de las dos ventanas auditadas de R23E bajo modo `AUTONOMOUS` (submodo `YOLO`, cuenta DEMO):

| Métrica Consolidada | Ventana 01 (W01) | Ventana 02 (W02) | **Acumulado (W01 + W02)** |
| :--- | :---: | :---: | :---: |
| **Tiempo de Auditoría** | 20.0 min (91 muestras) | 20.0 min (85 muestras) | **40.0 min (176 muestras)** |
| **Ticks de Mercado Capturados** | 4,324 ticks | 4,494 ticks | **8,818 ticks** |
| **Decisiones Totales Registradas** | 81 decisiones | 114 decisiones | **195 decisiones** |
| **Órdenes Reales Aceptadas** | 1 trade | 0 trades | **1 trade** |
| **Resultado Trades Reales** | 0 W / 1 L (WR = 0.0%) | 0 W / 0 L | **0 W / 1 L (WR = 0.0%)** |
| **Taps Reales Despachados** | 2 taps | 0 taps | **2 taps** |
| **Variación Saldo Real (DEMO)** | -18,001.92 COP | +0.00 COP | **-18,001.92 COP** |
| **Drawdown Máximo Validado** | 0.47% | 0.00% | **0.47%** |
| **Vetos `ADAPTIVE_BLOCK` Vistos** | 75 vetos | 89 vetos | **164 vetos** |
| **Contrafactuales Deduplicados (N)**| 3 (post-hoc) | 8 (live) | **11 contrafactuales** |
| **Contrafactuales Resueltos (W/L)**| 2 resueltos | 7 resueltos | **9 resueltos** |
| **CF Wins ($W_{\text{cf}}$)** | 1 | 4 | **5 Wins** |
| **CF Losses ($L_{\text{cf}}$)** | 1 | 3 | **4 Losses** |
| **CF Voids / Pendientes** | 1 (VOID) | 1 (Pendiente) | **2** |
| **CF Win Rate ($WR_{\text{cf}}$)** | **50.00%** | **57.14%** | **55.56%** |
| **Breakeven de Referencia** | 54.95% | 54.95% | **54.95%** |
| **EV Teórico Contrafactual ($EV$)**| -18,000.00 COP | +28,000.00 COP | **+10,000.00 COP** |
| **Taps por Contrafactuales** | 0 taps | 0 taps | **0 taps (100% aislados)** |

---

## 6. OBSERVACIONES TÉCNICAS Y RECOMENDACIONES

1. **Eficacia del Fix de Telemetría (`957ce13`):**
   - El parser con fallback por regex y sincronización con `activeSignal` permitió que el 100% de los vetos `ADAPTIVE_BLOCK` válidos de W02 fueran procesados en tiempo real con su dirección exacta (`SELL`/`BUY`), eliminando por completo el gap detectado en W01.
2. **Desempeño Contrafactual Acumulado ($WR_{\text{cf}} = 55.56\%$):**
   - El Win Rate contrafactual combinado de 55.56% supera ligeramente el umbral de breakeven (54.95%).
   - Se observa un fuerte sesgo de direccionalidad:
     - En W02, las operaciones `SELL` contrafactuales tuvieron un **100% de Win Rate** (3 de 3), mientras que las operaciones `BUY` tuvieron un **25% de Win Rate** (1 de 4), lo cual coincide con la tendencia bajista dominante (`DOWNTREND` / `TREND 🔴`) identificada por el motor de visión.
     - En setups de alta confluencia ([85 – 100 pts]), el Win Rate contrafactual alcanzó el **75.0%**.
3. **Estado del Sistema:**
   - Modo desactivado a `DISABLED` y submodo restablecido a `CONSERVATIVE`.
   - Nivel de batería: 87% (temperatura 34.8°C, estado estable y en carga por USB).
   - APK permanece inmutable en `versionCode = 17` (`08073c3`).

---

## 7. VEREDICTO FINAL DE AUDITORÍA OPENCODE

**PASS COMPLETO Y RATIFICADO.**  
La Ventana 02 cumplió de manera estricta y sin desviaciones todos los requisitos de seguridad de cuenta DEMO, aislamiento de taps, preservación de balance y resolución cuantitativa contrafactual. El sistema queda listo y en estado seguro para continuar con la Ventana 03 de 06.
