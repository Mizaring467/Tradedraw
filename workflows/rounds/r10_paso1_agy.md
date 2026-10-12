TAREA — RONDA 10 · PASO 1 REVISIÓN DE PREREGISTRO (agy)

Repo: C:\Users\heidy\Tradedraw (rama r10-night). NO edites código. Revisa y firma.

=== Contexto ===
Modo desatendido nocturno (5 h). Estrategia congelada en `r9-measure` (8ea9690). Activo primario ETHUSD-OTC,
payout real 0.80 → breakeven 55.56%. T0 = 1791791190823 (2026-10-12 02:46:30).

=== Revisa y FIRMA (o corrige) ===
1. workflows/PREREGISTRO_R10.md
2. data/T0.txt
3. .gitignore (debe ignorar data/ticks/*_2026-*.csv y data/null/)

=== Preguntas ===
A) ¿El preregistro fija la regla de decisión ANTES de medir? (H0, N>=200, Wilson LCL > breakeven, evaluación única)
B) ¿Queda claro que la confirmación NO se evalúa hasta N>=200 y que cualquier cambio de estrategia invalida la confirmación?
C) ¿El T0 es coherente (epoch ms) y coincide con el inicio de la recolección de confirmación?
D) ¿Falta alguna regla de integridad importante? (p.ej. activo primario, réplicas, huecos, demo)
E) Confirma con `git diff r9-measure -- app/src/main` que está VACÍO (regla 1).

=== ENTREGABLE (español, máx 15 líneas) ===
- VEREDICTO PASS/FAIL del preregistro
- Correcciones concretas si falta algo (puedes EDITAR el preregistro si es una omisión)
- Confirmación del punto E
