TAREA — RONDA 4 · AUDITORÍA CUANTITATIVA DEL JOURNAL (agy)

Repo: C:\Users\heidy\Tradedraw. NO edites código Kotlin. Solo analiza y reporta.

=== Datos ===
Archivo: workflows/rounds/r4_journal_full.csv  (102 trades reales del dispositivo)
Columnas (CSV):
epoch,time,strategy,submode,action,conf,price,stake,balBefore,result,balAfter,dur,trend,distSupport,distResist,velocity,regime,streak,candleCount,res2,note

Resumen conocido:
- Global: W=37 L=38 TIE=27  (WR ~49.3% excluyendo empates; breakeven objetivo 54.95%)
- BUY: W=19 L=18 (13 TIE)      SELL: W=18 L=20 (14 TIE)
- YOLO: W=7 L=3 (0 TIE) -> 70% WR con N=10
- CONSERVATIVE: W=30 L=35 (27 TIE) -> 46.2% WR con N=65

=== OBJETIVO ===
1. Calcular WR por: action, submode, trend (UPTREND/DOWNTREND), candleCount, y rango de distancia S/R.
   Excluye los TIE de los cálculos de WR (repórtalos aparte).
2. Determinar qué SUBCONJUNTO de condiciones tiene WR >= 55% con N >= 8 (mínimo).
3. Comprobar la hipótesis: ¿YOLO (7W/3L) es realmente superior a CONSERVATIVE, o es ruido por N pequeño?
   Calcula el intervalo de confianza de Wilson para ambos.
4. Identificar la causa de los 27 TIEs (probablemente el HUD cubría los botones; ya se corrigió H8).
5. Recomendar 2-3 cambios concretos (archivo+línea si aplica) para SUBIR la tasa de aciertos:
   p.ej. vetar BUY/SELL en ciertas condiciones, ajustar umbral de confluencia, priorizar submode.

=== ENTREGABLE (español, máx 30 líneas) ===
- Tablas de WR por dimensión (action, submode, trend, candles)
- Wilson LCL/UCL para YOLO y CONSERVATIVE
- Subconjunto(s) con ventaja estadística (WR>=55%, N>=8)
- Veredicto: ¿qué configurar para maximizar aciertos?
- 2-3 recomendaciones accionables
