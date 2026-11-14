# PREREGISTRO R23G — VALIDACIÓN PROLONGADA DEL CANDIDATO R23F + LOOP DE MEJORA CONTROLADA

**Fecha de Preregistro:** 2026-10-17  
**Orquestador:** AGY (`w1:p1`)  
**Implementador:** HERMES (`w1:p4`)  
**Validador Live Independiente:** OPENCODE (`w1:p7`)  
**Rama Base:** `r10-night` | **Commit Base:** `c1f8ad7`  
**Regla Absoluta de Seguridad:** 100% CUENTA DEMO (`isDemoAccount == true`). Aborto fail-closed inmediato si deja de cumplirse.

---

## 1. CONTEXTO Y BASELINE CONGELADO (R23F)

El candidato R23F elevó el umbral de similitud de anti-patrones en `AdaptiveLearningEngine.kt` de 0.90 a 0.990 (`ADAPTIVE_BLOCK_SIMILARITY_THRESHOLD = 0.99f`), liberando las oportunidades viables del bucket 98% mientras preserva el escudo protector sobre el bucket 99%.

### Identidad de Build Congelada
- **gitSha:** `ac6b0b2`
- **versionCode:** `18`
- **versionName:** `"1.8-r23f-adaptive"`
- **BUILD_ID:** `"R23F-ADAPTIVE"`
- **adaptive_threshold:** `0.99f` (98% liberado para ejecución real, 99% bloqueado)
- **Modo Auditado:** `AUTONOMOUS` | **Submodo:** `YOLO`
- **Activo:** `Z-CRY/IDX` en Binomo (`com.marketly.trading`)

### Historial de Rendimiento R23F (Muestra Histórica de Referencia)
- $N_{\text{R23F}} = 15$ trades reales (30 taps despachados en 3 ventanas de 20 min).
- $W_{\text{R23F}} = 9$ Wins / $L_{\text{R23F}} = 6$ Losses.
- $WR_{\text{R23F}} = \mathbf{60.00\%}$ vs Breakeven dinámico $WR_{\text{BE}} \approx 54.95\%$ ($b \approx 0.820$).
- $\Delta \text{Balance DEMO} = \mathbf{+138,001.92\text{ COP}}$.
- Drawdown Máximo Global: $1.32\%$.
- Shadow Bucket 99%: $N = 16$, $8\text{ W} / 8\text{ L}$, $WR = 50.00\%$, $EV = -144,000\text{ COP}$ (evitó pérdidas).
- Distribución Direccional:
  - BUY: 11 trades ($6\text{ W} / 5\text{ L}$, $WR = 54.55\%$, breakeven).
  - SELL: 4 trades ($3\text{ W} / 1\text{ L}$, $WR = 75.00\%$, profit $+246\text{k COP}$).

---

## 2. OBJETIVO CENTRAL DE R23G

Responder de manera estadísticamente rigurosa:
> **¿El Win Rate del 60.00% y la expectativa positiva observada en R23F se mantienen estables cuando se amplía significativamente el tamaño de muestra?**

### Reglas Metodológicas de Validación Prolongada
1. **No optimización prematura:** Prohibido modificar umbrales, confluencias, mechas o lógica de trading durante la campaña inicial de validación.
2. **Campaña inicial de hasta 10 ventanas consecutivas de 20 minutos** (con settlement grace al finalizar si hay trade pendiente).
3. **Mínimo muestral acumulado en R23G:** $N_{\text{R23G}} \ge 30$ trades reales DEMO.
4. **Objetivo preferido:** $N_{\text{R23G}} \ge 50$ trades reales DEMO.
5. **No mezcla silenciosa:** Se auditarán y reportarán estrictamente tres paneles independientes:
   - `R23F historical` (N=15, 9W/6L, WR=60.0%)
   - `R23G new sample` (inicia en N=0)
   - `R23F + R23G combined` (muestra consolidada total)

---

## 3. ESPECIFICACIÓN MATEMÁTICA Y TELEMETRÍA

### A. Break-Even Dinámico
Calculado sobre el payout promedio real observado:
$$WR_{\text{BE}} = \frac{1}{1 + \text{payout}}$$
(Para payout $0.820$, $WR_{\text{BE}} = 54.945\%$).

### B. Expected Value (Valor Esperado)
- **EV Unitario (por unidad de riesgo):**
  $$EV_{\text{unit}} = WR \times \text{payout} - (1 - WR)$$
- **EV Total (en divisa base COP):**
  $$EV_{\text{total}} = W \times (\text{stake} \times \text{payout}) - L \times \text{stake}$$

### C. Intervalo de Confianza 95% (Wilson Score Interval)
Para una muestra con $N$ ensayos y proporción de éxito $p = \frac{W}{N}$, con $z = 1.96$:
$$IC_{95\%} = \frac{p + \frac{z^2}{2N} \pm z \sqrt{\frac{p(1-p)}{N} + \frac{z^2}{4N^2}}}{1 + \frac{z^2}{N}}$$

### D. Métricas de Desempeño y Control por Ventana
- $N$, $W$, $L$, $\text{VOID}$
- $WR$, $\text{payout\_medio}$, $WR_{\text{BE}}$, $EV_{\text{unit}}$, $EV_{\text{total}}$
- $\Delta \text{Balance DEMO}$ (validado sin outliers de telemetría)
- $\text{Max Drawdown}$ acumulado
- $\text{Max Win Streak}$, $\text{Max Loss Streak}$
- $\text{Frecuencia: Trades por Hora}$

### E. Desgloses Segmentados Obligatorios
1. **Distribución Direccional:**
   - `BUY`: $N$, $W$, $L$, $WR$, $EV$
   - `SELL`: $N$, $W$, $L$, $WR$, $EV$
2. **Distribución por Régimen de Mercado:**
   - `BUY × UPTREND`
   - `BUY × DOWNTREND`
   - `SELL × UPTREND`
   - `SELL × DOWNTREND`
3. **Control del Bucket 98% Liberado:**
   - Marcar cada trade real con `adaptive_similarity` y `adaptive_bucket`.
   - Calcular $N_{\text{released\_98}}$, $W$, $L$, $WR$, $EV$.
   - Contrastar contra el contrafactual histórico de R23E ($66.67\%$, $10\text{ W} / 5\text{ L}$). Meta: $N_{\text{released\_98}} \ge 20$.
4. **Control del Bucket 99% en Shadow:**
   - Registrar vetos de anti-patrón residuales ($sim \ge 99.0\%$).
   - Calcular $CF\_N$, $CF\_W$, $CF\_L$, $CF\_WR$, $CF\_EV$.
   - Confirmar si el bucket 99% continúa siendo destructivo ($CF\_WR < WR_{\text{BE}}$).

### F. Calidad de Ejecución y Latencia
- Medición de marcas temporales: `decision_ts`, `dispatch_ts`, `confirmation_ts`, `resolution_ts`.
- Métricas: `decision_to_dispatch`, `dispatch_to_confirmation` (mediana, p90, máximo).
- Consistencia Live $\leftrightarrow$ Replay: objetivo acumulado $\text{paired} \ge 20$ y luego $\ge 30$, con `exact_match_rate >= 90%`.

---

## 4. CRITERIOS DE DECISIÓN DE FASE (R23G)

| Veredicto | Criterios Formales | Acción Consecuente |
| :--- | :--- | :--- |
| **STRONG_KEEP** | $N_{\text{R23G}} \ge 50$, $WR_{\text{R23G}} > WR_{\text{BE}}$, $EV > 0$, $\text{Max DD} < 3.0\%$, Bucket 99% shadow con $EV < 0$. | Candidato blindado. Proceder a Fase 2 (análisis de cuellos de botella para sizing o mejoras controladas). |
| **KEEP** | $N_{\text{R23G}} \ge 30$, $WR_{\text{R23G}} > WR_{\text{BE}}$, $EV > 0$, $\text{Max DD} \le 3.5\%$. | Candidato validado. Se ratifica la rentabilidad del threshold 0.99. |
| **INCONCLUSIVE** | $N_{\text{R23G}} < 30$ tras las ventanas programadas o $IC_{95\%}$ cruza ampliamente $WR_{\text{BE}}$ sin tendencia clara. | Mantener build congelada y extender ventanas adicionales sin tocar parámetros. |
| **REVERT** | $N_{\text{R23G}} \ge 25$, $WR_{\text{R23G}} < WR_{\text{BE}}$, $EV < 0$ y deterioro consistente entre ventanas. | Revertir relajación o volver a auditoría profunda. |

### Condiciones de Parada Inmediata (Fail-Closed / Stop Conditions)
- `isDemoAccount != true` en cualquier muestra.
- Detección de trades duplicados, solapamiento indebido o taps no autorizados.
- Corrupción de serie de balance o salto anómalo no justificado por trades.
- Loop de reconexión/crash en feed o bridge.

---

## 5. ESTRUCTURA DE ARTEFACTOS Y GESTIÓN DE ROLES

Los artefactos se organizarán en:
```text
loops/r23g_validation/
    window_01/
    window_02/
    ...
    AGGREGATE_REPORT.md
```

Cada carpeta de ventana contendrá:
- `WINDOW_REPORT.md` (Auditoría formal de OpenCode)
- `LIVE_REPORT.md` (Reporte generado por el monitor)
- `live_samples.jsonl`, `status_samples.jsonl`, `events.jsonl`
- `ticks_live.csv`, `decisions.csv`, `trades.csv`
- `counterfactual_adaptive_block.csv`
- `replay_journal.csv`, `replay_comparison.json`, `replay_comparison_report.md`
- `window_metadata.json`

### Roles del Equipo
- **AGY (Orquestador):** Control de prerregistro, supervisión de seguridad, cálculo de intervalos de confianza, consolidación agregada y veredictos.
- **HERMES (Implementador):** Congelado en standby; no altera código salvo corrección de bugs de telemetría de ser detectados.
- **OpenCode (Validador Live):** Ejecución de ventanas consecutivas de 20 minutos, recolección de muestras, auditoría de logs y verificación empírica.
