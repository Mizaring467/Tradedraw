# TAREA FASE B: REDACCIÓN DEL PREREGISTRO R23K
## Asignado a: HERMES (`w1:pA`)
## Orquestador: AGY (`w1:p1`)
## Rama: r10-night · Repositorio: C:\Users\heidy\Tradedraw

### OBJETIVO
Redactar el documento formal vinculante `workflows/PREREGISTRO_R23K.md` fijando las reglas metodológicas, criterios de parada y análisis a priori ANTES de la ejecución de cualquier ventana nueva en R23K.

### ESPECIFICACIÓN DEL PREREGISTRO R23K (B1 a B6)
1. **B1 Continuidad de Cubo 2 y Análisis Confirmatorio Único:**
   - Cubo 2 continúa con build inmutable `8cdb61f` y configuración congelada YOLO hasta $N_{\text{homogéneo}} = 80$ trades o hasta agotar el límite de tiempo de la sesión.
   - Se fija UN ÚNICO análisis confirmatorio al alcanzar $N=80$. Todos los análisis intermedios ventana por ventana son estrictamente informativos y NO autorizan decisiones ni detención anticipada.
   - Prohibido detener el bucle o parar por conveniencia de resultado (anti-p-hacking).
2. **B2 Criterio Vinculante de Edge:**
   - Solo se declarará veredicto de "Edge Sostenido" si el límite inferior del intervalo Wilson 95% > breakeven vigente ($54.64\%$) en el análisis confirmatorio ($N=80$) Y SIMULTÁNEAMENTE también en la sensibilidad "Cubo 2 sin W04".
   - Si discrepan, rige el criterio más conservador.
   - Prohibido el uso de términos «certificado», «contundente», «edge» con $N < 80$.
3. **B3 Ampliación del Tope de Ventanas:**
   - Se amplía el tope previo de R23J (8 ventanas) a «sin tope de ventanas, únicamente tope de tiempo por sesión (T0+6h30 / T0+7h)».
   - Motivo declarado a priori: potencia estadística insuficiente del límite previo para alcanzar $N=80$ o resolver el intervalo de Wilson.
4. **B4 Paridad del Arnés v1:**
   - El criterio R23J ($\ge 10$ pares formales y $\ge 90\%$ de match) se mantiene estrictamente inmutable; no se altera retroactivamente.
   - Se incorpora como métrica COMPLEMENTARIA la Cobertura:
     $$\text{Cobertura} = \frac{\text{Pares Formales}}{\text{Trades Live} \cup \text{Trades Replay}}$$
   - Se reporta de forma segregada el conteo de divergencias de ACCIÓN (Live BUY vs Replay SELL o viceversa).
5. **B5 Criterios a Priori para el Arnés v2 (R24):**
   - Unidad de comparación: Decisión tick a tick en ventana sniper.
   - (i) Reproducción de entradas $\ge 90\%$ con $\ge 20$ trades Live y misma acción dentro de $\pm 6\text{ s}$.
   - (ii) Reproducción del TIPO de bloqueo (`ADAPTIVE_BLOCK`, `VETO_BUY_EXHAUSTION_CLIMAX`, `V1`, `V2`, `RISK_LIMIT`) $\ge 90\%$ sobre $\ge 200$ decisiones comunes.
   - (iii) Cobertura $\ge 70\%$ de las decisiones Live con contraparte en Replay.
   - La reejecución retrospectiva sobre W05–W11 se etiqueta obligatoriamente como «retrospectiva y no independiente».
   - Todo nuevo congelamiento (SHA comparador v2 + SHA build nueva) abre un Cubo R24 separado; jamás se mezcla con el Cubo 2 de `8cdb61f`.
6. **B6 Imputación de Ventanas y Monitor v2:**
   - Definir qué ventanas cuentan para cada cubo:
     - W01–W03: Cubo 3 (Histórico).
     - W04–W11 + nuevas ventanas de esta sesión: Cubo 2 (Homogéneo).
     - Monitor v2 activo desde la primera ventana posterior al cierre de Fase A.

### ENTREGABLE
- `workflows/PREREGISTRO_R23K.md` redactado y commiteado.
