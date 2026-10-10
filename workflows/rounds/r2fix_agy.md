TAREA — RONDA 2-FIX · AUDITORÍA DE CAMBIO H6 (agy)

Repo: C:\Users\heidy\Tradedraw. NO edites código Kotlin. Audita y reporta.

=== Cambio a auditar ===
archivo: app/src/main/java/com/example/tradedraw/AdaptiveLearningEngine.kt
1. El veto por anti-patrón pasó de `highestSimilarity >= 0.82f` a `>= 0.90f` Y `isTightlyMatchingContext(...)`.
2. Nueva función `isTightlyMatchingContext(a,b)`: exige misma acción, misma tendencia, mismo
   isMarketSideways, y |ΔdistSupport| <= 0.10 y |ΔdistResistance| <= 0.10.
3. Se mantiene H3: ya NO se invierte, se BLoquea.

=== Preguntas ===
A) ¿La compuerta estricta elimina el falso positivo del feed near-static Z-CRY/IDX sin volver
   inútil el autoaprendizaje (es decir, sigue habiendo casos reales donde bloquea)?
B) ¿0.90 + contexto estricto es coherente con `.agents/skills/master_traders_skill/SKILL.md`?
C) ¿Hay riesgo de regresión (permitir demasiado) en activos reales volátiles (Forex)?
D) ¿Algún otro guard en executeHeadlessTrade (TradingEngine.kt ~L1600-1730) seguiría vetando
   señales legítimas sobre Z-CRY/IDX? Enumera cada guard con su línea y veredicto.

ENTREGABLE (español, máx 25 líneas): veredicto PASS/FAIL de H6 + riesgos + correcciones si aplica.
