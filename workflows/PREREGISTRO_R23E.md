# PREREGISTRO R23E — AUDITORÍA CONTRAFACTUAL DE ADAPTIVE_BLOCK + LOOP DE MEJORA

**Fecha de Preregistro:** 2026-10-16  
**Orquestador:** AGY (`w1:p1`)  
**Implementador:** HERMES (`w1:p4`)  
**Validador Live Independiente:** OPENCODE (`w1:p7`)  
**Rama Base:** `r10-night` | **Commit Base:** `6d02ad1`  
**Regla Absoluta de Seguridad:** 100% CUENTA DEMO (`isDemoAccount == true`). Aborto fail-closed inmediato si deja de cumplirse.

---

## 1. CONTEXTO Y BASELINE DE PARTIDA (R23D)

Tras la campaña de 10 ventanas de validación en R23D (~195 minutos de trading autónomo en cuenta DEMO en Xiaomi Redmi A1):
- **Trades Confirmados:** $N = 13$ trades ($W = 6, L = 7 \to WR = 46.15\%$).
- **Contratos APK:** $9\text{ W} / 8\text{ L} \to WR = 52.94\%$.
- **Payout Promedio:** $b = 0.82$ ($82.0\%$).
- **Breakeven Dinámico:** $WR_{BE} = \frac{1}{1 + b} = 54.95\%$.
- **Variación Neta de Capital:** $\Delta = -22,999.04\text{ COP}$ ($-0.060\%$).
- **Drawdown Máximo Global:** $0.87\%$.
- **Vetos Registrados por ADAPTIVE_BLOCK:** **$848$ de $876$ decisiones evaluadas ($96.8\%$ de tasa de veto)**.

**Conclusión Fundamental:**
El guard `ADAPTIVE_BLOCK` gobierna y estrangula prácticamente toda la frecuencia operativa del bot. Sin embargo, queda estrictamente prohibido relajarlo o desactivarlo a ciegas sin medir cuantitativamente qué habría ocurrido con las señales que bloqueó.

---

## 2. PRINCIPIO METODOLÓGICO CONTRAFACTUAL

No preguntamos: *"¿Qué pasa si apagamos o relajamos ADAPTIVE_BLOCK?"*.  
Respondemos rigurosamente: *"¿Qué habría pasado con las señales que ADAPTIVE_BLOCK vetó, si se hubieran ejecutado?"*.  
**Cero taps despachados al broker, cero fondos expuestos, cero impacto en saldo DEMO.** Es un seguimiento puramente estadístico y pasivo.

---

## 3. DEFINICIÓN OPERATIVA DEL GUARD ADAPTIVE_BLOCK

`ADAPTIVE_BLOCK` es el veto emitido por `AdaptiveLearningEngine.kt` cuando una señal candidata analizada por `TradingEngine.kt`:
1. Coincide con una firma contextual de pérdida previa (`TradeContextSignature`) con:
   - Similitud matemática: $\text{similarity} \ge 0.90$ ($90\%$).
   - Compuerta de contexto estricta (`isTightlyMatchingContext`): misma acción, misma tendencia, mismo régimen lateral y $| \Delta S/R | \le 0.10$.
2. O bien, presenta un sesgo adaptativo perdedor recurrente en continuación o reversión extrema con canal significativo ($channelWidth \ge 0.35$).

---

## 4. PARÁMETROS EXPERIMENTALES CONGELADOS

1. **Activo Único:** `Z-CRY/IDX` (Crypto IDX en Binomo).
2. **Cuenta:** 100% DEMO.
3. **Horizonte Temporal de Expiración ($T_{horizon}$):**  
   Exactamente **60 segundos** ($60,000\text{ ms}$) contados a partir del milisegundo exacto de la decisión (`decision_ts`), correspondiente al tiempo de resolución de una vela/opción binaria estándar en el broker.
4. **Payout de Referencia ($b$):**  
   $b = 0.82$ ($82.0\%$).
5. **Breakeven Teórico ($WR_{BE}$):**  
   $$WR_{BE} = \frac{1}{1 + 0.82} = 54.95\%$$

---

## 5. RESOLUCIÓN CONTRAFACTUAL (SIN LOOK-AHEAD)

La decisión queda irrevocablemente congelada en $t = \text{decision\_ts}$ con su precio de entrada $P_{entry} = \text{price\_at\_signal}$.  
En $t = \text{decision\_ts} + 60,000\text{ ms}$, se toma el precio de salida real $P_{exit}$ registrado en el feed de ticks WebSocket (`ticks_live.csv`).

### Criterio de Resolución:
- **Para Señal BUY (CALL):**
  - $\text{COUNTERFACTUAL\_WIN} \iff P_{exit} > P_{entry}$
  - $\text{COUNTERFACTUAL\_LOSS} \iff P_{exit} < P_{entry}$
  - $\text{COUNTERFACTUAL\_VOID} \iff P_{exit} == P_{entry}$
- **Para Señal SELL (PUT):**
  - $\text{COUNTERFACTUAL\_WIN} \iff P_{exit} < P_{entry}$
  - $\text{COUNTERFACTUAL\_LOSS} \iff P_{exit} > P_{entry}$
  - $\text{COUNTERFACTUAL\_VOID} \iff P_{exit} == P_{entry}$

### Valor Esperado Teórico ($EV$):
Por cada oportunidad contrafactual con stake unitario nominal ($S = 100,000\text{ COP}$):
$$EV = (CF\_W \times b \times S) - (CF\_L \times S)$$
$$CF\_EV_{unitario} = (CF\_WR \times b) - ((1 - CF\_WR - CF\_VOID) \times 1.0)$$

---

## 6. UNIDAD EXPERIMENTAL Y REGLAS DE DEDUPLICACIÓN

Dado que el motor evalúa cada tick WebSocket (hasta 3-4 ticks por segundo), un mercado bajo tendencia puede emitir docenas de vetos `ADAPTIVE_BLOCK` consecutivos sobre la misma vela para la misma oportunidad.  
Para evitar inflación artificial de muestra y sobre-representación de rachas:

1. **Ventana de Ocupación Emulada (Emulated In-Flight Window):**
   Al registrarse un contrafactual para una dirección dada (`action`), se activa una ventana de no-solapamiento de **60 segundos**. Durante esos 60 segundos, ninguna señal subsiguiente para la misma acción se contabilizará como nueva oportunidad contrafactual (emulando la restricción real de `OPERACION_EN_CURSO` que habría impedido abrir órdenes solapadas).
2. **Deduplicación por Vela:**
   Máximo 1 contrafactual por vela de 60s por dirección.
3. **Identificador Único Determinista:**
   Cada contrafactual poseerá un `counterfactual_id` determinista derivado de `decision_id` y `decision_ts`.

---

## 7. TAMAÑO DE MUESTRA Y DISEÑO DE CAMPAÑA

- **Muestra Mínima Obligatoria:** $N_{counterfactual} \ge 50$ oportunidades deduplicadas.
- **Muestra Preferida:** $N_{counterfactual} \ge 100$.
- **Límite de Ventanas Live:** Hasta 6 ventanas de 20 minutos (120 minutos de captura en vivo).
- **Operativa Real en Paralelo:** El bot real continúa operando en cuenta DEMO con `ADAPTIVE_BLOCK` **ACTIVO**, permitiendo contrastar de forma simultánea en la misma ventana:
  - Operaciones REALES aceptadas vs. Oportunidades CONTRAFACTUALES rechazadas.

---

## 8. SEGMENTACIÓN PREREGISTRADA DE CONTRAFACTUALES

Los contrafactuales se segmentarán obligatoriamente en cuatro dimensiones:

1. **Por Dirección:** `BUY` vs. `SELL`.
2. **Por Régimen de Mercado:** `UPTREND`, `DOWNTREND`, `SIDEWAYS`.
3. **Por Buckets de Similitud Preregistrados:**
   - Bucket 1: $90.0\% \le \text{sim} < 93.0\%$
   - Bucket 2: $93.0\% \le \text{sim} < 96.0\%$
   - Bucket 3: $96.0\% \le \text{sim} < 98.0\%$
   - Bucket 4: $98.0\% \le \text{sim} \le 100.0\%$
4. **Por Score de Confluencia Real:** Confluencia extraída cuantitativamente ($[75, 79]$, $[80, 84]$, $\ge 85\text{ pts}$).

---

## 9. CRITERIOS FORMALES DE DECISIÓN AL CIERRE DE R23E

### CASO A: EL GUARD ES ÚTIL (KEEP GUARD)
- **Condición:** $CF\_WR < WR_{BE}$ ($54.95\%$) de forma general y consistente a través de los buckets de similitud, con $EV < 0$.
- **Acción:** `KEEP ADAPTIVE_BLOCK`. Queda terminantemente prohibido relajarlo. El cuello de botella de frecuencia se buscará en otra etapa del pipeline (ej. generación o confluencia).

### CASO B: EL GUARD ES SELECTIVAMENTE DEMASIADO AGRESIVO (RELAJACIÓN SELECTIVA)
- **Condición:** Se identifica un sub-segmento estadísticamente robusto ($n \ge 25$) donde:
  $$CF\_WR > 56.0\% \quad \text{y} \quad EV > 0$$
  (Ejemplo hipotético: similitud $< 96\%$ con score $\ge 85$ y confluencia a favor de tendencia).
- **Acción:** AGY formulará **UNA ÚNICA hipótesis selectiva** que HERMES implementará de forma acotada. Jamás se apagará el guard por completo.

### CASO C: INCONCLUSIVE
- **Condición:** $N_{counterfactual} < 50$ o resultados agrupados dentro de la banda de ruido ($\pm 2\%$ alrededor del breakeven $54.95\%$).
- **Acción:** No modificar el código de trading. Mantener baseline y extender período de muestreo.

---

## 10. REGLA DE TRAZABILIDAD DE BUILD (FASE 0)
Secuencia estricta e inmutable:
$$\text{Código finalizado} \longrightarrow \text{Tests verdes} \longrightarrow \text{Git Commit} \longrightarrow \text{Obtener SHA} \longrightarrow \text{Compilar APK} \longrightarrow \text{Instalar} \longrightarrow \text{Verificar /status}$$
La APK en runtime debe exponer exactamente: `gitSha == commit_probado`.
