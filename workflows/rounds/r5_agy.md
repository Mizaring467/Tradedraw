TAREA — RONDA 5 · AUDITORÍA DEL FIX DE JOURNAL (agy)

Repo: C:\Users\heidy\Tradedraw. NO edites código Kotlin. Audita y reporta.

=== Cambio a auditar (implementado por DeepSeek Harness / DSH) ===
Archivo: app/src/main/java/com/example/tradedraw/TradeJournalLogger.kt
Problema raíz: al reinstalar la app, Android asigna un UID nuevo (u0_a179). El directorio externo
/sdcard/Android/data/<pkg>/files/TradeDraw_Audits/ retiene el UID viejo (u0_a158) y FileWriter
lanza Permission denied -> el journal dejó de registrar desde 2026-10-10.

Solución DSH:
1. `isWritableDir(dir)`: comprueba existir + isDirectory + canWrite() (o mkdirs+canWrite).
2. Si el externo NO es escribible -> usa `internalDir = File(context.filesDir, "TradeDraw_Audits")`.
3. `try { writeRow(externo) } catch (IOException|SecurityException) { writeRow(interno) }`.
4. `syncInternalToExternal()`: cuando el externo vuelve a ser escribible, anexa lo interno al
   externo y borra el interno SOLO tras confirmar la copia.
5. Refactor: `buildLine()`, `writeRow()`. La firma de idempotencia se fija SOLO tras persistir.

=== PREGUNTAS ===
A) ¿El fallback cubre TODOS los caminos de fallo? (canWrite false, mkdirs false, IOException en
   FileWriter, SecurityException). ¿Algún agujero?
B) ¿Es correcto el `syncInternalToExternal`? ¿Puede duplicar registros o perder la cabecera?
C) ¿La cabecera (HEADER) se maneja bien en el archivo interno nuevo?
D) ¿La firma de idempotencia queda consistente si ocurre el fallback?
E) ¿Algún riesgo de bucle si externo e interno son el MISMO directorio en algún dispositivo?
F) ¿La rotación (rotateIfNeeded) sigue funcionando en el fallback interno?

=== ENTREGABLE (español, máx 25 líneas) ===
- Veredicto PASS/FAIL del fix
- Riesgos/agujeros concretos (con línea si aplica)
- Correcciones recomendadas si hay alguna
