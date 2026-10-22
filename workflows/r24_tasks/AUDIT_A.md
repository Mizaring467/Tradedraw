# AUDITORÍA · FASE A · Contexto limpio

**Rol:** AUDITOR. No eres el autor de nada de lo que auditas. Si algo no lo puedes reproducir, NO lo firmas.
**Proyecto:** `C:\Users\heidy\Tradedraw`, rama `r10-night`.
**SHA a auditar:** `190f596` (HEAD base) + commit `1307913` (higiene) + árbol de trabajo.

## Lo que recibes (y nada más)

- SHA: `190f596` (base), `1307913` (higiene `.gitignore`).
- Build declarada: `8cdb61f`, versionCode 19.
- Preregistro: `workflows/PREREGISTRO_R23K.md` (341 líneas de cuerpo + enmiendas A2/A3 añadidas al final).
- Evidencia primaria: `loops/r24_verify/A1_cubes.json`, `loops/r24_verify/A1_stdout.txt`, `loops/r24_verify/a1_independent.py`.
- Script canónico: `scripts/validation/recompute_cubes.py`.
- Datos crudos: `loops/r23*/**/trades.csv`, `loops/r23*/**/replay_comparison.json`.
- Informe a auditar: `workflows/rounds/R23K_FINAL_REPORT.md`.
- Reconciliación: `workflows/R24_RECONCILIACION_FUERA_DE_VENTANA.md`.

## Lo que NO recibes (deliberadamente)

- Ningún resumen, conclusión ni opinión del IMPLEMENTADOR ni del ORQUESTADOR.
- Ninguna cifra "esperada". Recalcula tú.

## Encargo

1. **Recalcula desde cero** con `python scripts/validation/recompute_cubes.py --root . --out-json <tmp> --out-md <tmp>`
   y compara tu salida con `loops/r24_verify/A1_cubes.json`. Reporta cualquier diferencia numérica.
2. **Verifica el script** `recompute_cubes.py`: `git diff d39f723 190f596 -- scripts/validation/recompute_cubes.py`.
   ¿Cambió alguna fórmula (Wilson, breakeven, bootstrap, drawdown, agregación)? ¿O solo altas de ventana y cosmética?
3. **Verifica el commit de higiene** `1307913`: `git show --stat 1307913`. ¿Solo toca `.gitignore`? ¿Añade entradas sin borrar las existentes? ¿Mete algo sensible?
4. **Grep de credenciales** en todo lo añadido: `git diff 190f596 1307913 | Select-String -Pattern "token|cookie|auth|secret|password|Bearer"`. Reporta hallazgos o ausencia.
5. **Audita el informe** `workflows/rounds/R23K_FINAL_REPORT.md` contra los datos primarios: cada cifra citada, ¿coincide con el JSON? Lista discrepancias con línea.
6. **Audita las enmiendas** A2/A3 al final de `PREREGISTRO_R23K.md`:
   - ¿El cuerpo original (líneas 1–341) quedó intacto? (`git diff` sobre ese archivo)
   - ¿La inalcanzabilidad está DEMOSTRADA aritméticamente o solo afirmada?
   - ¿Se declara explícitamente que no se cambió ningún criterio retroactivamente?
   - ¿El Cubo 1 declara cobertura 27,50 % y que no se usa como evidencia de validación?
7. **Verifica la reconciliación**: `workflows/R24_RECONCILIACION_FUERA_DE_VENTANA.md`. ¿El delta −100.001,28 COP cuadra con la última fila del journal? ¿El trade está correctamente excluido de los cubos?

## Salida

Escribe `workflows/r24_tasks/AUDIT_A_result.md` con:
- Veredicto por punto: `PASS` / `FAIL` / `NO VERIFICABLE`.
- Cada discrepancia con archivo:línea y el valor correcto según tu recálculo.
- Firma final: `PASS-INDEPENDIENTE (AUDITOR, DeepInvestigator, contexto limpio)` **solo si** los 7 puntos pasan.
  Si algo falla: `FAIL` con la lista de lo que impide firmar.

Prohibido: Gradle, ADB, tocar el teléfono, modificar código o datos. Solo lectura y recálculo.
