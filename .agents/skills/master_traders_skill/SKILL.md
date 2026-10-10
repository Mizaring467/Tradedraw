---
name: master_traders_skill
description: >
  Metodología cuantitativa y reglas de acción del precio pura a 60s de Master Traders para TradeDraw.
  Gobierna rigurosamente el reconocimiento de patrones de mecha de rechazo (MT_REJECTION), agotamiento
  de 3 velas (MT_3_VELAS_AGOT), choque de polaridad (MT_CHOQUE_PULLBACK), falsos rompimientos (MT_FALSE_BREAKOUT),
  filtros anti-derrotas (ventana sniper :58-:03, CHOP index > 61.8, ratio dojis >= 0.35),
  matriz de confluencia (mínimo 80 pts) y gestión de capital Quarter-Kelly (1.0% - 3.5%).
  Todas las reglas deben seguirse letra por letra sin excepciones ni relajaciones.
metadata:
  author: TradeDraw & Master Traders Team
  version: "2.0"
---

# Master Traders Skill · Metodología Cuantitativa y Acción del Precio Pura (60s)

Esta habilidad encapsula las directrices operativas, modelos matemáticos, fórmulas de microestructura y protocolos de gestión de riesgo de la metodología **Master Traders** para opciones binarias a 60 segundos sobre **Binomo (Crypto IDX, OTC), Quotex y PocketOption**.

> ⚠️ **MANDATO OBLIGATORIO ("LETRA POR LETRA"):**  
> Todo agente de desarrollo, motor algorítmico, subagente de trading o evaluador que implemente o modifique lógica en TradeDraw **DEBE obedecer todas y cada una de las reglas, umbrales numéricos, ventanas temporales y fórmulas aquí descritas de manera literal**. Queda estrictamente prohibido relajar tolerancias, ignorar vetos cronológicos o inventar heurísticas que contradigan este manual.

---

## 1. Fundamentos Cuantitativos y Ventaja Matemática (*Statistical Edge*)

Las opciones binarias de efectivo o nada (*Cash-or-Nothing*) se rigen por la función escalón de Heaviside:

$$\Pi(S_T) = \begin{cases} b \cdot I, & \text{si } S_T > K \text{ (CALL Ganador)} \\ -I, & \text{si } S_T \le K \text{ (CALL Perdedor)} \end{cases}$$

donde:
- $S_T$: Precio al vencimiento $T$ (exactamente 60 segundos tras el strike).
- $K$: Precio de ejercicio (*Strike Price*) en $t_0$.
- $I$: Capital invertido (*Investment stake*).
- $b$: Tasa de pago neto (*Broker Payout Rate*), comúnmente $0.80$ a $0.85$ ($80\% - 85\%$).

### 1.1 Punto de Equilibrio Matemático (*Breakeven Win Rate*)
La esperanza matemática $E = I \cdot [p(b + 1) - 1]$ exige una tasa de acierto mínima para no quebrar:
$$p_{\text{BE}} = \frac{1}{1 + b}$$

- **Payout 82% ($b = 0.82$):** $p_{\text{BE}} = \frac{1}{1.82} \approx 54.945\%$
- **Payout 85% ($b = 0.85$):** $p_{\text{BE}} = \frac{1}{1.85} \approx 54.054\%$

**Objetivo de la metodología:** Operar exclusivamente con ventaja estadística neta positiva para sostener una tasa de acierto del **62% al 73%**.

---

## 2. Estrategias Nucleares de Acción del Precio Pura (60s)

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                       MATRIZ DE ESTRATEGIAS MASTER TRADERS                 │
├──────────────────────┬────────────────────────┬─────────────┬───────────────┤
│ Estrategia           │ Tipo de Configuración  │ Win Rate    │ Confluencia   │
├──────────────────────┼────────────────────────┼─────────────┼───────────────┤
│ MT_REJECTION         │ Reversión por Mecha    │ 66% - 73%   │ Alta (S/R)    │
│ MT_CHOQUE_PULLBACK   │ Breakout + Retest      │ 64% - 70%   │ Media-Alta    │
│ MT_3_VELAS_AGOT      │ Agotamiento Decaimiento│ 62% - 68%   │ Media (Contra)│
│ MT_FALSE_BREAKOUT    │ Trampa Institucional   │ 70% - 76%   │ Máxima        │
└──────────────────────┴────────────────────────┴─────────────┴───────────────┘
```

---

### ESTRATEGIA 1: `MT_REJECTION` (Mechas de Rechazo en S/R Institucional)

Una mecha larga en vela M1 representa absorción violenta de liquidez por órdenes pasivas institucionales en zonas de soporte o resistencia.

#### Parámetros y Fórmulas Obligatorias:
1. **Rango Total Mínimo:**  
   $$R_{\text{total}} = H - L \ge 12\text{ px}$$  
   *(Velas con rango $< 12\text{ px}$ quedan descalificadas por micro-ruido).*
2. **Ratio de Mecha de Rechazo ($\ge 45\%$):**  
   $$\text{Ratio}_{\text{wick}} = \frac{W_{\text{rechazo}}}{R_{\text{total}}} \ge 0.45$$
   - Para CALL: Mecha inferior $W_{\text{bottom}} = \min(O, C) - L$. $\text{Ratio}_{\text{bottom}} \ge 0.45$.
   - Para PUT: Mecha superior $W_{\text{top}} = H - \max(O, C)$. $\text{Ratio}_{\text{top}} \ge 0.45$.
3. **Límite de Cuerpo Real ($\le 40\%$):**  
   $$\frac{B}{R_{\text{total}}} = \frac{|C - O|}{R_{\text{total}}} \le 0.40$$  
   *(El cuerpo no debe dominar la vela; si $B > 0.40 \cdot R_{\text{total}}$, se invalida).*
4. **Tolerancia Espacial al Nivel ($\le 4\text{ px}$):**  
   $$|\text{Extremo de Mecha} - \text{Nivel S/R o Número Redondo}| \le 4\text{ px}$$  
   Números redondos institucionales válidos: terminaciones `.000`, `.500`, `.200`, `.800`.
5. **Condición de Posición del Cuerpo:**
   - Para CALL: $L \le \text{Soporte} + 4\text{ px}$ y $\min(O, C) \ge \text{Soporte} - 4\text{ px}$ (penetración de mecha, cuerpo protegido por encima).
   - Para PUT: $H \ge \text{Resistencia} - 4\text{ px}$ y $\max(O, C) \le \text{Resistencia} + 4\text{ px}$ (penetración de mecha, cuerpo protegido por debajo).

---

### ESTRATEGIA 2: `MT_3_VELAS_AGOT` (Agotamiento Cuantitativo de 3 Velas)

Identifica el decaimiento progresivo del volumen y la fuerza de los iniciadores de tendencia contra una barrera de liquidez.

#### Parámetros y Fórmulas Obligatorias:
Sean las tres últimas velas cerradas consecutivas $C_1, C_2, C_3$ ($C_1$ más antigua, $C_3$ más reciente):
1. **Paridad Cromática Estricta:**  
   $$\text{Color}(C_1) = \text{Color}(C_2) = \text{Color}(C_3)$$  
   *(3 velas verdes consecutivas o 3 velas rojas consecutivas).*
2. **Decaimiento Monótono de Cuerpos:**  
   $$B_1 > B_2 > B_3$$
3. **Contracción Cuantitativa Estricta:**  
   $$B_3 \le 0.45 \cdot B_1 \quad \text{y} \quad B_2 \le 0.80 \cdot B_1$$
4. **Desaceleración de Ticks:**  
   La velocidad de ticks o desplazamiento del precio decae: $V_1 > V_2 > V_3$.
5. **Ubicación Espacial Clave:**  
   $C_3$ debe cerrar a una distancia $\le 10\text{ px}$ de la zona de Soporte (para 3 rojas) o Resistencia (para 3 verdes).
6. **Veto por "Cuchillo Cayendo" (*Falling Knife*):**  
   Si $B_3 \ge B_2$ o si $\sum_{i=1}^3 R_i > 3.5 \times \text{ATR}_5$ sin mecha opuesta de absorción ($\text{Wick}_{\text{opuesta}} < 0.20$), se **veta completamente** la operación.
7. **Disparo:** Operación en contra de la tendencia en la apertura de la 4ª vela ($T+1$).

---

### ESTRATEGIA 3: `MT_CHOQUE_PULLBACK` (Polaridad Breakout + Retest)

Aprovecha el principio de polaridad cuando una resistencia rota se convierte en soporte, o un soporte roto se convierte en resistencia.

#### Parámetros y Fórmulas Obligatorias:
1. **Vela de Ruptura Válida ($C_{\text{breakout}}$):**
   - Más del $50\%$ del cuerpo debe cerrar fuera del nivel:
     $$\text{Ratio}_{\text{cuerpo\_exterior}} = \frac{\text{Cuerpo fuera de S/R}}{B_{\text{ruptura}}} > 0.50$$
   - Ausencia de mecha frontal opuesta:
     $$\text{Ratio}_{\text{mecha\_frontal}} < 0.20$$
2. **Vela de Choque / Retest ($C_{\text{actual}}$):**
   - Contacto preciso con el nivel roto:
     $$|\text{Precio Actual} - \text{Nivel Roto}| \le 3\text{ px}$$
   - **Ventana Temporal de Choque Inviolable:**  
     El contacto debe ocurrir estrictamente dentro de los primeros segundos de vida de la vela:
     $$t \in [1\text{s}, 5\text{s}]$$
   - Entrada inmediata: CALL si retestea resistencia rota; PUT si retestea soporte roto.

---

### ESTRATEGIA 4: `MT_FALSE_BREAKOUT` (Trampa Institucional / Falso Rompimiento)

1. Una vela previa penetra con su mecha o cuerpo un nivel de soporte o resistencia relevante.
2. El precio cierra **reingresando al interior del canal/rango**, dejando una mecha de rechazo $\ge 0.35$.
3. Se opera la reversión a favor del reingreso institucional hacia el centro del rango.

---

## 3. Filtros Cuantitativos Anti-Derrotas (Inviolables)

El $70\%$ de las pérdidas en 60 segundos provienen de entradas fuera de tiempo o mercados caóticos.

### 3.1 Filtro de Micro-Sincronización Cronológica (Ventana Sniper :00)
- **Entrada Estándar:** Estrictamente entre el segundo **:58** (anticipación de cambio de vela) y el segundo **:03** (apertura fresca).
- **Entrada Sniper Pullback:** Entre el segundo **:01** y el segundo **:05** únicamente si el precio realiza un leve retroceso en contra de la señal (descuento de strike).
- **VETO UNIVERSAL:** **Cualquier señal generada entre el segundo :06 y el segundo :57 queda 100% VETADA.** Ninguna orden puede ejecutarse en este intervalo.

### 3.2 Filtro de Mercado Lateral Sucio (Choppiness Index y Dojis)
1. **Índice de Choppiness de O'Brien (14 periodos):**
   $$\text{CHOP} = 100 \times \frac{\log_{10}\left( \frac{\sum_{i=1}^{14} \text{TR}_i}{\max(H_{14}) - \min(L_{14})} \right)}{\log_{10}(14)}$$
   - $\text{CHOP} > 61.8 \implies$ **VETO TOTAL de señales de tendencia / continuación.**
   - $\text{CHOP} < 38.2 \implies$ Mercado direccional limpio habilitado.
2. **Filtro de Ratio de Dojis:**
   $$\text{DojiRatio}_{10} = \frac{\sum_{i=1}^{10} \mathbb{I}(B_i \le 4\text{ px})}{10} \ge 0.35 \implies \text{VETO (Baja Volatilidad / Micro-ruido)}$$
3. **Alternancia de Color (Serrucho Caótico):**
   Si la secuencia es $V \to R \to V \to R$ en las últimas 4 velas con desplazamiento neto $< 2.5\%$ del alto del gráfico: **VETO**.

---

## 4. Matriz de Confluencia y Puntuación (0 - 100)

| Factor Cuantitativo | Ponderación | Criterio de Activación |
| :--- | :---: | :--- |
| **Sincronización Reloj (:58-:03)** | **Condición Mandatoria** | Sin esta ventana, la señal se veta en $100\%$. |
| **Tendencia Mayor H1/M15** | +20 pts | EMA(20) > EMA(50) para CALL, viceversa para PUT. |
| **Nivel Institucional S/R** | +25 pts | Contacto exacto a $\le 4\text{ px}$ de soporte/resistencia. |
| **Mecha de Rechazo $\ge 45\%$** | +25 pts | Absorción de volumen en el extremo del nivel. |
| **Agotamiento Decrescente** | +20 pts | $C_3 \le 0.45 \cdot C_1$ y paridad de color previa. |
| **Falso Rompimiento Previo** | +30 pts | Trampa con cierre devuelto dentro del rango. |
| **RSI / Velocity Exhaustion** | +15 pts | RSI de ticks $\le 22$ (sobreventa) o $\ge 78$ (sobrecompra). |
| **Filtro Anti-Lateral (CHOP)** | **Veto Multiplicador** | Si $\text{CHOP} > 61.8$ o Doji Ratio $\ge 0.35$, puntaje $\times 0.50$. |

> 🎯 **UMBRAL DE DISPARO AUTOMATIZADO:**  
> Solo se despacha la orden si la puntuación final de confluencia es **$\ge 80$ PUNTOS**.

---

## 5. Gestión de Capital Óptima: Criterio de Quarter-Kelly

La fracción óptima de capital $f^*$ para pagos asimétricos es:
$$f^* = \frac{b \cdot p - q}{b} = p - \frac{1 - p}{b}$$

En opciones binarias se aplica estrictamente **Quarter-Kelly ($0.25 \times f^*$)**:

$$f_{\text{operativo}} = \min(0.25 \times f^*, \; 0.035)$$

### Reglas de Dimensionamiento de Entrada:
1. **Piso Mínimo:** Nunca arriesgar menos del $1.0\%$ del bankroll si hay señal válida.
2. **Techo Máximo:** Nunca exceder el $3.5\%$ del bankroll por operación (protección estricta contra drawdowns de cola ancha).
3. **Filtro de Ventaja Negativa:** Si la probabilidad estimada $p \le 0.55$, el stake se reduce al mínimo defensivo ($1.0\%$).

---

## 6. Mandato de Auditoría y Verificación para el Agente

Cuando audites, programes o pruebes TradeDraw:
1. **Compara siempre contra estas fórmulas exactas**: Verifica que los nombres de variables, constantes y umbrales en Kotlin reflejen fielmente esta especificación.
2. **Verifica en tiempo real por ADB**: Toda orden emitida debe validar la hora del sistema en segundos (`sec in 58..59 || sec in 0..3`) antes de emitir clic táctil.
3. **No permitas falsos positivos**: Si el score de confluencia es $< 80$, el HUD debe reportar la causa de veto o espera sin emitir señal de compra/venta.
