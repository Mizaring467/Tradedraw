# TAREA A2 Y A3: CONTRASTE INDEPENDIENTE Y ENMIENDAS FECHADAS
## Asignado a: CLAUDE CODE (`w1:pC`)
## Orquestador: AGY (`w1:p1`)
## Rama: r10-night · Repositorio: C:\Users\heidy\Tradedraw

### OBJETIVO
1. Realizar el contraste independiente (A2) entre el recálculo determinista de OpenCode (`workflows/rounds/R23K_CUBES_RECOMPUTED.md` y `.json`), la reconciliación de frontera (`workflows/rounds/R23J_W10_BORDER_TRADE_RECONCILIATION.md`) y el informe de auditoría W11 (`workflows/rounds/R23J_CLAUDE_W11_AUDIT.md`).
2. Redactar y aplicar las enmiendas fechadas (A3) al final de los documentos de R23J correspondientes, cerrando el `INCIDENTE DE AGREGACIÓN`.

### INSTRUCCIONES ESPECÍFICAS
#### 1. CONTRASTE (A2)
- Documentar en `workflows/rounds/R23K_CLAUDE_A2_A3_AUDIT.md` cada discrepancia aritmética:
  - Total de trades: W11 audit reportaba $N=38$ (25W / 13L); el recálculo de datos crudos demuestra $N=39$ (25W / 14L). La tabla individual de W11 contenía 14 pérdidas pero la suma agregada perdió la pérdida de frontera.
  - Cubo 2: W11 audit reportaba $N=27$ (18W / 9L, WR 66.67%); el recálculo demuestra $N=28$ (18W / 10L, WR 64.29%).
  - Afirmación no soportada: «31 vetos evitaron 3 pérdidas severas». En W11 hubo 31 evaluaciones de tick, colapsadas a 4 señales únicas por vela, y no existía contrafactual out-of-sample auditado en ese momento.
  - Afirmación no soportada: «+1.95% de retorno». No deductiva de la pérdida de frontera.

#### 2. ENMIENDAS FECHADAS (A3)
- REGLA DE ORO: Las enmiendas deben agregarse ESTRICTAMENTE AL FINAL del archivo, bajo encabezado fechado (ej. `## ENMIENDA FECHADA (2026-10-19 — LOOP R23K / FASE A3)`).
- PROHIBIDO REESCRIBIR O BORRAR EL CUERPO ORIGINAL de los documentos.
- Aplicar la enmienda en:
  1. `workflows/rounds/R23J_CLAUDE_W11_AUDIT.md`
  2. `loops/R23J_PROGRESO.md`
  3. `workflows/rounds/R23J_FINAL_REPORT.md`
- Contenido obligatorio de cada enmienda:
  - Registro de `INCIDENTE DE AGREGACIÓN R23J-01` (CORREGIDO).
  - Cifras corregidas certificadas por el script `recompute_cubes.py`:
    - Cubo 2: $N = 28$ ($18\text{ W} / 10\text{ L} \implies 64.29\%\text{ WR}$, Wilson 95%: [45.83%, 79.29%]).
    - Total acumulado W01–W11: $N = 39$ ($25\text{ W} / 14\text{ L} \implies 64.10\%\text{ WR}$, Wilson 95%: [48.42%, 77.26%]).
  - Retractación formal de «31 vetos evitaron 3 pérdidas severas» y «+1.95% de retorno».
  - Confirmación de veredictos: se mantienen inalterados como `NO CONCLUYENTE` (tanto en Paridad como en Desempeño).

#### 3. ENTREGABLE
- `workflows/rounds/R23K_CLAUDE_A2_A3_AUDIT.md` con el dictamen de contraste y la confirmación de enmiendas aplicadas.
