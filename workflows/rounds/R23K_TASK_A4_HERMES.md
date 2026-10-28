# TAREA A4: SCRIPT DE ANÁLISIS CONTRAFACTUAL DEL VETO BUY_EXHAUSTION_CLIMAX
## Asignado a: HERMES (`w1:pA`)
## Orquestador: AGY (`w1:p1`)
## Rama: r10-night · Repositorio: C:\Users\heidy\Tradedraw

### OBJETIVO
Implementar y ejecutar un script determinista para auditar el contrafactual del veto `BUY_EXHAUSTION_CLIMAX` en las ventanas W04 a W11 (out-of-sample) comparado con R23G (in-sample).

### ESPECIFICACIÓN TÉCNICA
1. **Script:** `scripts/validation/counterfactual_exhaustion_climax.py`.
2. **Entrada de datos:**
   - Para cada ventana W04 a W11 (y R23G in-sample si está disponible en `loops/`):
     - `decisions.csv` (buscar filas donde el veto contenga `BUY_EXHAUSTION_CLIMAX` o `VETO_BUY_EXHAUSTION_CLIMAX`).
     - `ticks_live.csv` (o `ticks.csv`) de la ventana correspondiente para resolver el desenlace a 60 segundos del precio.
3. **Criterio de deduplicación y resolución:**
   - **Deduplicar por señal única por vela (M1):** Durante una misma vela de 60 s, los múltiples ticks que disparan veto continuo cuentan como UNA SOLA señal (tomar el primer instante o tick de la ráfaga de veto).
   - **Desenlace a 60 s:** Comparar el precio de entrada en el instante del veto contra el precio del tick más cercano a $t + 60\text{ s}$ ($\pm 4\text{ s}$).
   - **Resultado:**
     - Si `precio_60s > precio_entrada` $\implies$ WIN (el veto evitó un trade ganador).
     - Si `precio_60s < precio_entrada` $\implies$ LOSS (el veto evitó una pérdida).
     - Si `precio_60s == precio_entrada` $\implies$ TIE / EMPATE.
4. **Métricas por segmento:**
   - Segmento In-Sample (R23G).
   - Segmento Out-of-Sample (W04 a W11).
   - Para cada segmento y consolidado:
     - $N$ señales únicas vetadas.
     - Desenlaces: $W$, $L$, $TIE$.
     - $\text{Win Rate Contrafactual} = W / (W + L)$.
     - Intervalo de confianza de Wilson al 95%.
     - Comparación contra Breakeven vigente.
5. **Regla Epistémica Estricta:**
   - Prohibido concluir "efectividad demostrada" o "edge confirmado" si $N_{\text{out-of-sample}} < 20$ señales únicas. Si $N < 20$, dictamen formal obligatorio: **NO CONCLUYENTE (Muestra insuficiente, $N < 20$)**.
6. **Salida:**
   - Generar `workflows/rounds/R23K_COUNTERFACTUAL_EXHAUSTION.md` con las tablas de resultados.
   - Mostrar el resumen en consola en el panel `w1:pA`.
