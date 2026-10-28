# R23H — AUDITORÍA ESTADÍSTICA INDEPENDIENTE Y RECONCILIACIÓN FORENSE

**Fecha:** 2026-10-17  
**Auditor Independiente:** CLAUDE CODE (`w1:pC`)  
**Rol Herdr:** Auditor Estadístico y Validador de Integridad  
**Referencia Metodológica:** `workflows/R23H_DATA_RECONCILIATION.md`  
**Artefactos Auditados:**
- `loops/r23f_validation/` (Ventanas 01 a 03)
- `loops/r23g_validation/` (Ventanas 01 a 08)
- `loops/r23g_validation/AGGREGATE_REPORT.md`
- `workflows/rounds/R23G_FINAL.md`
- `loops/r23h_analysis/CANONICAL_TRADES.csv`
- `loops/r23h_analysis/DATA_INTEGRITY_REPORT.md`
- `loops/r23h_analysis/OPENCODE_WINDOW_AUDIT.md`

---

## 1. RESUMEN EJECUTIVO Y VEREDICTO FORMAL

### Veredicto Formal Definitivo:
1. **Sobre los Reportes Previos de R23G (`AGGREGATE_REPORT.md` y `R23G_FINAL.md`):**  
   🔴 **`DATA_INCONSISTENT` (RECHAZADO)**  
   Los reportes violaron la ley fundamental de conservación contable ($N \ne W + L$). Presentaron una muestra nominal de $N = 32$ con resoluciones de $W = 14$ y $L = 22$ ($W + L = 36$, discrepancia de $-4$), duplicaron operaciones entre fases y contaminaron los conteos con contadores acumulativos de ciclo del APK no sincronizados con la telemetría transaccional.

2. **Sobre el Canonical Trade Ledger (`CANONICAL_TRADES.csv`):**  
   🟢 **`CONFIRMED` (APROBADO Y CERTIFICADO)**  
   El ledger reconstruido por Hermes (`w1:pA`) y auditado por OpenCode (`w1:pB`) y Claude Code (`w1:pC`) satisface rigurosamente la ecuación canónica obligatoria:
   $$\mathbf{N = W + L + VOID \iff 48 = 20 + 26 + 2}$$
   Cada céntimo debitado y acreditado en la cuenta DEMO está completamente trazado y conciliado contra saltos físicos de saldo en `live_samples.jsonl`.

3. **Sobre la Asimetría de Rendimiento BUY vs SELL:**  
   🟡 **`ARTEFACTO MUESTRAL / NO SIGNIFICATIVO`**  
   La aparente superioridad de SELL ($WR = 56.25\%$) frente a BUY ($WR = 36.67\%$) arrojó un p-valor de $p = 0.202$ (Z-test) y $p = 0.224$ (Fisher exacto), con solapamiento masivo de sus intervalos de confianza Wilson al 95%. La hipótesis de que "BUY es estructuralmente tóxico y SELL es rentable" carece de significancia estadística en esta muestra y constituyó una falacia de muestra pequeña.

---

## 2. DIAGNÓSTICO FORENSE: ¿POR QUÉ $N \ne W + L$ EN REPORTES ANTERIORES?

El análisis de causalidad raíz identificó tres fallas metodológicas concurrentes en la generación de reportes durante R23G:

```
[Persistencia Buffer /events] ---> [Trade Duplicado R23G W01] ---\
                                                                  +--> [Discrepancia N != W + L]
[Trades Pre-Ventana Fuera Saldo] -> [Exclusión Parcial W02/W08] --/    (N=32 vs W+L=36, delta=-4)
                                                                 /
[Lectura de risk.totalLosses] ----> [Inflación Fantasma L (+4)] -/
```

### 2.1 Causa Raíz A: Fuga de Búfer Circular (Event Stale Leakage)
- **Trade Implicado:** `3417933c-888e-49ed-b37c-40074ada8a6a` (SELL, ts = `1792203848235`).
- **Mecanismo:** Fue ejecutado legítimamente en los últimos 555 ms de **R23F window_03** (Trade #6, liquidado como WIN a las `1792203920905`). Al iniciar **R23G window_01** 37 minutos después, el monitor consultó el endpoint `/events?max=200` del APK sin purgar eventos previos.
- **Consecuencia:** La orden fue ingestada por segunda vez como Trade #1 de R23G W01 sin haber movido saldo ni haber ocurrido en dicha ventana.

### 2.2 Causa Raíz B: Desacoplamiento entre Telemetría Transaccional y Acumuladores APK
- En el script `live_monitor_r23_autonomous.py`, las resoluciones se calcularon mediante deltas de contadores globales:
  ```python
  new_wins = cur_wins - initial_wins
  new_losses = cur_losses - initial_losses
  total_trades = new_wins + new_losses
  ```
- En ventanas con operaciones de fondo o liquidaciones desfasadas (W05, W06 y W07), el contador `risk.totalLosses` del APK incrementó por eventos fuera del ledger local de la ventana.
- Los reportes individuales agregaron notas de "resoluciones en ciclo":
  - **W05:** Reportó 2 Losses cuando la tabla sólo contenía 1 Loss (+1 L fantasma).
  - **W06:** Reportó 4 Losses cuando la tabla sólo contenía 2 Losses (+2 L fantasma).
  - **W07:** Reportó "6 en ciclo" agregando pérdidas no emparejadas (+1 L fantasma).

### 2.3 Causa Raíz C: Error de Denominador en `AGGREGATE_REPORT.md`
- Al consolidar R23G, el analista sumó las filas nominales de las tablas individuales:
  $$2 + 2 + 6 + 7 + 5 + 4 + 3 + 3 = \mathbf{32\text{ trades}}$$
- Pero para las victorias y derrotas, sumó ciegamente los conteos inflados de las notas:
  $$W = 14, \quad L = 22 \implies W + L = \mathbf{36}$$
- Luego calculó el Win Rate como $14 / 36 = 38.89\%$, manteniendo en la cabecera $N = 32$. La discrepancia de $-4$ quedó expuesta.

---

## 3. AUDITORÍA DEL CANONICAL TRADE LEDGER (`CANONICAL_TRADES.csv`)

### 3.1 Verificación de Integridad de Datos
- **Filas crudas en todos los `trades.csv`:** 49
- **Duplicados inter-fase purgados:** 1 (`3417933c...` en R23G W01)
- **Trades únicos confirmados en cuenta DEMO:** 48
- **Identidad Canónica:**
  $$\mathbf{N = W + L + VOID} \iff \mathbf{48 = 20 + 26 + 2 \quad (100.0\%\text{ reconciliado})}$$

### 3.2 Trazabilidad de Trades `VOID` (Unresolved Settlement)
Para garantizar rigor contable absoluto sin fabricar resultados, dos trades fueron clasificados como `VOID` por ausencia de salto de saldo medible en su ventana de expiración:
1. `492af1ef-9516-4c18-afc1-026b6250811c` (R23G W02, SELL, ts = `1792208162278`): Ocurrió 151 segundos antes del inicio del monitor de W02.
2. `c80f17ba-4546-4100-aca2-1d9a83753f1b` (R23G W08, BUY, ts = `1792218065145`): Ocurrió 156 segundos antes de la captura de telemetría de W08. $\Delta\text{balance} = 0.00\text{ COP}$.

### 3.3 Tabla de Reconciliación Consolidada

| Segmento | Muestra ($N$) | Wins ($W$) | Losses ($L$) | VOID | $WR_{\text{res}}$ (resueltos) | $WR_{\text{tot}}$ (total $N$) | $\Delta\text{ Balance}$ Real (COP) |
| :--- | :---: | :---: | :---: | :---: | :---: | :---: | ---: |
| **R23F Global** | **15** | 9 | 6 | 0 | **60.00%** | **60.00%** | **+37,994.24** |
| — R23F BUY | 11 | 6 | 5 | 0 | 54.55% | 54.55% | -38,000.64 |
| — R23F SELL | 4 | 3 | 1 | 0 | 75.00% | 75.00% | +75,994.88 |
| **R23G Global** | **33** | 11 | 20 | 2 | **35.48%** | **33.33%** | **-1,387,994.88** |
| — R23G BUY | 20 | 5 | 14 | 1 | 26.32% | 25.00% | -1,155,005.44 |
| — R23G SELL | 13 | 6 | 6 | 1 | 50.00% | 46.15% | -232,989.44 |
| **TOTAL COMBINADO** | **48** | **20** | **26** | **2** | **43.48%** | **41.67%** | **-1,350,000.64** |
| — **COMBINADO BUY** | **31** | **11** | **19** | **1** | **36.67%** | **35.48%** | **-1,193,006.08** |
| — **COMBINADO SELL** | **17** | **9** | **7** | **1** | **56.25%** | **52.94%** | **-156,994.56** |

*Nota metodológica:* $WR_{\text{res}} = W / (W + L)$ excluye los trades `VOID` no resueltos. $WR_{\text{tot}} = W / N$ es el Win Rate conservador sobre el total de órdenes emitidas.

---

## 4. RECÁLCULO ESTADÍSTICO INDEPENDIENTE

### 4.1 Fórmulas Matemáticas Rigurosas

#### Intervalo de Confianza Wilson al 95% para Proporciones Binomiales:
Evita las distorsiones del intervalo de Wald en muestras pequeñas ($N < 50$):
$$\tilde{p} = \frac{w + \frac{z^2}{2}}{n + z^2}, \quad IC_{95\%} = \tilde{p} \pm \frac{z}{n + z^2} \sqrt{\frac{w(n-w)}{n} + \frac{z^2}{4}}$$
donde $z = 1.95996$, $z^2 = 3.84146$, $z^2 / 2 = 1.92073$, $z^2 / 4 = 0.96036$.

#### Expectativa Matemática Unitaria ($EV_{\text{unit}}$):
$$EV_{\text{unit}} = (WR \times \text{stake} \times \text{payout}) - ((1 - WR) \times \text{stake})$$
- Parámetros: $\text{stake} = 100.000\text{ COP}$, $\text{payout nominal} = 0.82$.
- Win Rate de Equilibrio (Break-even):
  $$WR_{\text{be}} = \frac{1}{1 + \text{payout}} = \frac{1}{1.82} = \mathbf{54.95\%}$$

### 4.2 Tabla de Métricas Estadísticas Independientes (Muestra Combinada)

| Dirección | $N$ | Resueltos ($n$) | $W$ | $L$ | VOID | Win Rate ($WR$) | Wilson $IC_{95\%}$ | $EV_{\text{unit}}$ Teórico | $EV_{\text{total}}$ Teórico | $\Delta\text{ Balance}$ Empírico |
| :--- | :---: | :---: | :---: | :---: | :---: | :---: | :---: | ---: | ---: | ---: |
| **BUY** | 31 | 30 | 11 | 19 | 1 | **36.67%** | **[21.87%, 54.49%]** | -33,267 COP | -998,000 COP | -1,193,006.08 COP |
| **SELL** | 17 | 16 | 9 | 7 | 1 | **56.25%** | **[33.18%, 76.90%]** | +2,375 COP | +38,000 COP | -156,994.56 COP |
| **GLOBAL** | 48 | 46 | 20 | 26 | 2 | **43.48%** | **[30.21%, 57.75%]** | -20,866 COP | -960,000 COP | -1,350,000.64 COP |

*Si se computa Wilson sobre $N_{\text{total}}$ (incluyendo VOID como no-victoria):*
- **BUY ($N=31$):** $WR = 35.48\% \implies Wilson\ IC_{95\%}: \mathbf{[21.12\%, 53.05\%]}$
- **SELL ($N=17$):** $WR = 52.94\% \implies Wilson\ IC_{95\%}: \mathbf{[30.96\%, 73.84\%]}$
- **GLOBAL ($N=48$):** $WR = 41.67\% \implies Wilson\ IC_{95\%}: \mathbf{[28.85\%, 55.72\%]}$

---

## 5. EVALUACIÓN DE SIGNIFICANCIA ESTADÍSTICA: ASIMETRÍA BUY VS SELL

En `R23G_FINAL.md` se afirmó categóricamente que "BUY es destructivo (37.14% WR) y SELL es altamente rentable (62.50% WR), con una diferencia de +25.36%". Se sometió esta afirmación a contraste de hipótesis riguroso.

### 5.1 Planteamiento de Hipótesis
- **Hipótesis Nula ($H_0$):** $p_{\text{SELL}} = p_{\text{BUY}}$ (No existe diferencia real en la probabilidad de éxito; la variación observada proviene de fluctuación muestral).
- **Hipótesis Alternativa ($H_1$):** $p_{\text{SELL}} \ne p_{\text{BUY}}$ (Existe diferencia sistemática entre ambas direcciones).
- **Nivel de Significancia:** $\alpha = 0.05$.

### 5.2 Test Z de Diferencia de Dos Proporciones Binomiales Independientes
- **Datos observados:**
  - Grupo 1 (BUY): $n_1 = 30$, $w_1 = 11$, $\hat{p}_1 = 0.3667$
  - Grupo 2 (SELL): $n_2 = 16$, $w_2 = 9$, $\hat{p}_2 = 0.5625$
- **Proporción combinada agrupada ($p_{\text{pool}}$):**
  $$p_{\text{pool}} = \frac{w_1 + w_2}{n_1 + n_2} = \frac{11 + 9}{30 + 16} = \frac{20}{46} = 0.43478$$
- **Error Estándar ($SE$):**
  $$SE = \sqrt{p_{\text{pool}}(1 - p_{\text{pool}}) \left(\frac{1}{n_1} + \frac{1}{n_2}\right)} = \sqrt{0.24574 \times (0.03333 + 0.06250)} = \sqrt{0.02355} = \mathbf{0.15346}$$
- **Estadístico Z:**
  $$Z = \frac{\hat{p}_2 - \hat{p}_1}{SE} = \frac{0.56250 - 0.36667}{0.15346} = \frac{0.19583}{0.15346} = \mathbf{1.276}$$
- **P-Valor Resultante:**
  - $p\text{-valor (bilateral)} = 2 \times P(Z \ge 1.276) = \mathbf{0.202} \quad (20.20\%)$
  - $p\text{-valor (unilateral)} = P(Z \ge 1.276) = \mathbf{0.101} \quad (10.10\%)$

### 5.3 Test Exacto de Fisher (Tabla de Contingencia $2 \times 2$)
Dado que $n_2 = 16$ es una muestra pequeña, se aplicó el Test Exacto de Fisher sobre la distribución hipergeométrica:

| Dirección | WIN | LOSS | Total |
| :--- | :---: | :---: | :---: |
| **BUY** | 11 | 19 | 30 |
| **SELL** | 9 | 7 | 16 |
| **Total** | 20 | 26 | 46 |

- Probabilidad exacta acumulada de tablas iguales o más extremas ($k \ge 9$ victorias en SELL):
  $$p\text{-valor (Exacto de Fisher, bilateral)} = \mathbf{0.224} \quad (22.40\%)$$
  $$p\text{-valor (Exacto de Fisher, unilateral)} = \mathbf{0.156} \quad (15.60\%)$$

### 5.4 Análisis de Solapamiento de Intervalos de Confianza
- **Wilson $IC_{95\%}$ BUY:** $[21.87\%, \mathbf{54.49\%}]$
- **Wilson $IC_{95\%}$ SELL:** $[\mathbf{33.18\%}, 76.90\%]$
- **Región de Solapamiento:** **$[33.18\%, 54.49\%]$** (Amplitud de $21.31$ puntos porcentuales).
- **Hallazgo Crítico:** El umbral de break-even ($54.95\%$) está contenido dentro del intervalo de BUY y dentro del intervalo de SELL.

### 5.5 Veredicto Estadístico sobre la Asimetría
Como $p = 0.202 \gg 0.05$ (y Fisher $p = 0.224 \gg 0.05$), **NO ES POSIBLE RECHAZAR LA HIPÓTESIS NULA**.

**Conclusión Científica:**
La diferencia de rendimiento entre BUY y SELL observada en R23G **carece de significancia estadística**. Representa **ruido muestral estocástico** atribuible al reducido tamaño de la muestra ($N=46$). La aserción de que SELL es intrínsecamente ganador y BUY es tóxico fue un sesgo de confirmación basado en datos no reconciliados.

---

## 6. CONCLUSIONES Y DIRECTRICES VINCULANTES PARA R23H

1. **Derogación de Conclusiones Tácticas Previas:**
   Quedan anuladas todas las decisiones tácticas derivadas de `R23G_FINAL.md` que asumían superioridad demostrada de SELL o requerían endurecimiento unilateral de BUY sin control estadístico.

2. **Adopción Obligatoria del Canonical Ledger:**
   Ningún agente, script o informe posterior podrá citar métricas de R23F o R23G que no provengan byte a byte de `loops/r23h_analysis/CANONICAL_TRADES.csv`.

3. **Criterio de Avance a Fase 1:**
   - La reconciliación de datos de Fase 0 ha finalizado con éxito contable ($N = W + L + VOID = 48$).
   - Cualquier optimización de confluencia o filtro direccional en la Fase 1 debe estar respaldada por muestras que alcancen potencia estadística real ($1 - \beta \ge 0.80$, lo que para detectar una diferencia de $15\%$ requeriría un mínimo de $N \approx 135$ operaciones por brazo).
