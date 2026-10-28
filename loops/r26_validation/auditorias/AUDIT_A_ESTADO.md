# AUDIT-A · Estado de loops y git (verificación propia del orquestador)

**Ejecutado por:** DSH (sesión visible) · **Fecha:** 2026-10-27 22:45 (America/Bogota)
**Método:** comandos de git y lectura de documentos. Sin delegación para esta parte (ya cubierta por el orquestador).

## 1. Ramas y HEAD

| Ref | SHA | Nota |
| :--- | :--- | :--- |
| `HEAD` / `r10-night` / `origin/r10-night` | `75f005e` | sincronizados (0/0) |
| `main` / `origin/main` | `4d433ed` | **210 commits por detrás**, ancestro directo de `r10-night` |
| `feature/jules-refactor-audit-…` | `657e1cc` | rama paralela antigua, no fusionada |

Tags: `baseline-r7`, `r9-measure`. No hay tag por ronda.

`git status --porcelain`: **limpio** (solo `backups/` ignorado tras la regla añadida en `75f005e`).

## 2. Tabla de rondas

| Ronda | Estado verificado | Evidencia | Último cambio |
| :--- | :--- | :--- | :--- |
| R11–R15 | Ejecutadas, con anomalías de observabilidad | `loops/R1x_REPORTE.md` | commits de oct-14 |
| R16–R19 | Ejecutadas (arena) | `loops/R16-R19_RESUMEN.md` (commit `752f21e`) | oct-14 |
| R20 | Diagnóstico "el bot no operaba" + guard | `loops/R20_REPORTE.md` (commit `4d433ed`); prerrequisito en `e7763e9` | oct-14 |
| R21 | **COMPLETADA — PASS** (pipeline reproducible) | `loops/R21_REPORTE.md:10`, commit `e33f8cb` | oct-14 |
| R22 | **COMPLETADA — PASS** (ADB + runtime, capital preservado) | `loops/R22_REPORTE.md:11`, commit `300a06a` | oct-14 |
| R23A–R23J | Cerradas; R23J `PASS operativo y contable`; **paridad y desempeño NO CONCLUYENTE** | `loops/R23J_PROGRESO.md:20` | oct-18 |
| R23K | **COMPLETADA con defecto de diseño heredado**: preregistro posterior a 8/19 ventanas (punto 13 de la auditoría) | `workflows/rounds/R23K_FINAL_REPORT.md`, `loops/R23K_PROGRESO.md` (commit `190f596`) | oct-20 |
| R24 | **PARCIAL**: Fase 0/A/B `PASS-AUTOR` (firma independiente **revocada**, `91c3351`); Fases C/D **NO EVALUABLE** por caída del forward (`015a17b`) | `workflows/rounds/R24_FINAL_REPORT.md:7`, `workflows/R24_PROGRESO.md:139-149` | oct-26 |
| R25 | **CERRADA — `NO EVALUABLE (por V5)`**: V1–V4 PASS en 3 ventanas, V5 sin muestra | `loops/r25_validation/R25_CIERRE.md`, commit `48509ca` | oct-27 01:34 |
| R26 | **CERTIFICADA (P1/P2 PASS)** + anomalía del contador verificada; V5 fuera de alcance | `loops/r26_validation/R26_REPORTE.md`, commits `f97abfa`, `f491ca4`, `75f005e` | oct-27 22:26 |
| R27 | **NO INICIADA**: solo la mención prospectiva en `PREREGISTRO_R26.md:123` y `R26_REPORTE.md:188`; **no existe** `workflows/PREREGISTRO_R27.md` | `ls workflows/PREREGISTRO_R*.md` termina en R26 | — |

## 3. Demostración de "cuál es el siguiente" (sin asumir)

1. `git log` posterior al cierre de R25 (`48509ca`) = **solo tres commits**, todos de R26: `f97abfa`, `f491ca4`, `75f005e`. No hay ningún commit posterior.
2. `R25_CIERRE.md` §5 declara dos pendientes estructurales: (a) caducidad de `AUTONOMOUS`; (b) V5 con ≥ 20 trades.
3. `R26_REPORTE.md` §5–§7 y la adenda a `R25_CIERRE.md` demuestran que (a) quedó cerrado (P1/P2 con artefactos) y que (b) **sigue abierto**.
4. `PREREGISTRO_R26.md` §4 excluye V5 explícitamente y remite a un cubo propio (R27).
5. No existe `PREREGISTRO_R27.md` ni carpeta `loops/r27_*`: **R27 no ha empezado**.

⇒ El siguiente trabajo técnico legítimo es **R27: preregistro + acumulación de ≥ 20 trades live para V5**. No es una suposición: es el único pendiente que sobrevive a R26 y no tiene artefacto que lo cubra.

## 4. Contradicciones detectadas

| # | Contradicción | Evidencia |
| :--- | :--- | :--- |
| A-1 | `R24_FINAL_REPORT.md` dice C/D **"NO EJECUTADA"**, pero `R24_PROGRESO.md` (enmienda) y `PREREGISTRO_R25.md` las declaran **"NO EVALUABLE por transporte"** | `workflows/rounds/R24_FINAL_REPORT.md:15`, `workflows/R24_PROGRESO.md:139`, `015a17b` |
| A-2 | El preregistro R26 declara "Fecha de fijación 22:20", pero su mtime es **22:00:43** y P1 se armó a las **22:01:38** | `stat workflows/PREREGISTRO_R26.md`, `P1_t0.txt` |
| A-3 | El preregistro R26 se **commiteó después** de P1/P2 (`75f005e`, 22:26). La inmutabilidad previa se apoya en mtime, no en git | `git show -s --format=%ci 75f005e` |
| A-4 | `main` no tiene R25 ni R26 pese a que `AGENTS.md` §1 exige push directo a `main` | `git rev-parse main` = `4d433ed` |
| A-5 | No hay `README.md` en la raíz | `ls README*` |
