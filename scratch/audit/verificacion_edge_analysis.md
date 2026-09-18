# Verificacion independiente - scripts/edge_analysis.py

Rol: verificado independiente (intento de refutacion). Repo: C:\Users\heidy\Tradedraw
Fecha: 2026-09-17. No se modifico nada del analisis verificado (solo se creo este script de sonda).

## Resultado

| # | Afirmacion | Veredicto |
|---|---|---|
| 1 | WR=42.0% n=193 IC[35.2,49.0] sobre control | PASA |
| 2 | Clasificacion WIN/LOSS/TIE por delta consistente con analyze_journal | PASA |
| 3 | Ningun CSV modificado | PASA |
| 4 | scripts/analyze_journal.py no fue reescrito por ESTE objetivo | PASA (tras correccion) |
| 5 | BH bien implementada | PASA |
| 6 | wald_p de una cola correcto | PASA |
| 7 | Deduplicacion no pierde ni duplica | PASA |
| 8 | 0 segmentos n>=30 sobreviven | PASA |

## Evidencia

- Comando: python scripts/edge_analysis.py -> exit 0. Colisiones timestamp = 0 (traza [2]).
- Calculo propio (csv+math, sin logica del script): FILAS=212, N_RESUELTOS=193, WIN=81,
  WR=41.97%, IC=[35.23,49.02], TIES=19. Coincide con el control.
- analyze_journal.classify vs edge_analysis.clasificar sobre 212 filas: 0 diferencias.
  Ambas usan abs(delta)<2.0 -> TIE.
- git diff -- "*.csv" = 0 lineas; los 5 CSV de scratch estan untracked; los 2 tracked sin cambios.
- git diff -- scripts/analyze_journal.py = 372 lineas / 16265 bytes. Breakpoint del "no tocar".
- BH vs referencia independiente: identico en 3000 casos aleatorios; monotonia OK; caso libro
  [0.001,0.008,0.039,0.041,0.042] -> [0.005,0.02,0.042,0.042,0.042] (correcto).
- wald_p(60,100): script 0.17103490650283154 = manual 0.5*erfc(z/sqrt2). wald_p(88,204)=0.999756.
- Dedup: 1131 filas leidas, 223 claves unicas, 908 descartadas, 1131-908=223 OK.
  218 grupos con timestamp repetido; 0 con payload distinto -> no hay trades legitimos colapsados.
- Segmentacion replicada independiente: 407 segmentos, 281 con n>=5, 86 con n>=30,
  0 supervivientes BH y 0 Bonferroni.
- Barrido de umbral (n>=1/5/10/30): 0 supervivientes en todos. El filtro n>=5 no oculta nada.
- p minimo entre n>=30: action=BUY x stake=2.00, n=31, WR=71.0%, p=3.94e-2, pBH=1.000.
  Para sobrevivir con m=281 hace falta p<1.779e-4 (z>3.57) -> WR>=87.2% con n=31. No existe.

## Detalle del fallo #4 (RESUELTO en la ronda 2)

Refutacion de mi atribucion aceptada: el diff de AJ (372 lineas) NO lo produjo este objetivo.
mtime AJ = 16/09/2026 22:41:12; mtime EA = 17/09/2026 02:48:18. Trabajo previo de otra sesion,
ya en disco antes de leer el archivo. Mi deteccion del conflicto de requisitos seguia siendo
valida: `wilson_interval` no existe en HEAD (4308d5b) y edge_analysis.py lo importaba.

Correccion aplicada (opcion (b)): edge_analysis.py es autocontenido. Verificado en ronda 2:
- AST: unicos imports sys/csv/io/os/math/itertools/collections/datetime. Cero analyze_journal.
- Copia aislada en %TEMP%\iso_check ejecutada sin scripts/ en sys.path: EXIT=0, mismos numeros.
- wilson_interval EA vs AJ: identico bit a bit en 13 pares incl. n=0, w=0, w=n, n=1.
- AJ sin tocar: hash 75f5af85 y mtime 16/09/2026 22:41:12 sin cambio tras la verificacion.

## Estado ronda 2

1. Sin import de analyze_journal (grep + AST) — PASA
2. Funciona sin AJ en el path (copia aislada, EXIT=0) — PASA
3. wilson_interval identico bit a bit en 13 casos limite — PASA
4. Numeros clave intactos: 223/204/407/281/86/0/0/0 colisiones — PASA
5. git diff CSV vacio; AJ hash y mtime sin cambio — PASA

## Descripcion original del fallo (historico)

El diff de analyze_journal.py NO es un simple cambio cosmetico: reescribe el archivo
(372 lineas de diff, funciones nuevas wilson_interval/parse_diff/filtrar_desde/drawdown_maximo/
tabla_desglose, argparse distinto, veredicto nuevo). El hash actual 75f5af85 no coincide con
el de HEAD (4308d5b). La instruccion del encargo era no modificarlo.
Ademas: el archivo versionado en HEAD NO contiene wilson_interval, y edge_analysis.py lo importa
de ahi. Si se revierte a HEAD, edge_analysis.py deja de importar y falla. Dependencia circular
de requisitos: el encargo pide a la vez "no modificar analyze_journal.py" y "reutilizar
wilson_interval de analyze_journal.py". Ambos no pueden cumplirse con el HEAD actual.

## Nota de sensibilidad (punto 7/8)

Variante SIN deduplicar: 1022 resueltos (cada trade contado 5.01x de media, 195 de 204 con
3+ copias) y ahi SI aparecen 2 supervivientes con n>=30. Es artefacto puro de inflar n con
copias identicas del mismo trade. La deduplicacion es lo correcto y su supresion seria el bug.

## Reproduccion

    cd C:\Users\heidy\Tradedraw
    python scripts/edge_analysis.py
    python scratch/audit/verif_sweep.py
    git diff --stat -- "*.csv"
    git diff -- scripts/analyze_journal.py