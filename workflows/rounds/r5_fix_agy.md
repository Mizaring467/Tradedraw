TAREA — RONDA 5-FIX · APLICAR CORRECCIONES AL JOURNAL (agy)

Repo: C:\Users\heidy\Tradedraw. Kotlin, comentarios en español. NO hagas commit ni install.
Aplica las correcciones que TÚ mismo detectaste en la auditoría del fix de TradeJournalLogger.

Archivo: app/src/main/java/com/example/tradedraw/TradeJournalLogger.kt

=== CORRECCIÓN 1 (CRÍTICA) — Evitar colisión internalDir == externalDir ===
Si el directorio interno y el externo apuntan al MISMO path, el sync se anexa a sí mismo y borra el
archivo (pérdida total). Añade guarda de canonicalPath:
- En `syncInternalToExternal(internalDir, externalDir)`: al inicio, si
  `internalDir.canonicalPath == externalDir.canonicalPath` -> return inmediato (log de warning).
- En el bloque de fallback de escritura (el catch que hace `writeRow(internalDir, ...)`): 
  condicionar con `if (dir.canonicalPath != internalDir.canonicalPath)` antes de reintentar en interno,
  para no reescribir sobre la misma ruta fallida.

=== CORRECCIÓN 2 (CRÍTICA) — delete() fallido no debe duplicar ===
En `syncInternalToExternal`: si `source.delete()` devuelve false, en vez de dejar el archivo (que se
volvería a anexar en el próximo trade duplicando registros), TRÚNCALO a 0 bytes:
```kotlin
if (source.delete()) {
    Log.i(TAG, "Registros internos sincronizados al almacenamiento externo")
} else {
    // delete() falló (p.ej. archivo abierto): truncar para no duplicar en el siguiente sync.
    try { java.io.FileWriter(source, false).use { } ; Log.w(TAG, "source no borrado: truncado a 0") }
    catch (e2: Exception) { Log.e(TAG, "No se pudo truncar el journal interno: ${e2.message}") }
}
```

=== CORRECCIÓN 3 (MEDIA) — pendingSignature seguro con finally ===
Asegura que `pendingSignature` no quede con una firma fallida: envuelve la lógica de firma para que
ante una excepción de I/O se limpie (usa try/finally o limpia en el catch). Mantén la semántica actual:
la firma `lastSignature` solo se fija TRAS persistir con éxito.

=== CORRECCIÓN 4 (DEFENSIVA) — isWritableDir con runCatching ===
Envuelve el cuerpo de `isWritableDir` en `runCatching { ... }.getOrDefault(false)` para que un
SecurityException devuelva false en vez de propagarse.

=== VERIFICACIÓN OBLIGATORIA ===
1. Balance de llaves {} correcto.
2. .\gradlew.bat testDebugUnitTest --no-daemon  (deben pasar los 164; ajusta el test si es necesario)
3. .\gradlew.bat assembleDebug --no-daemon --no-configuration-cache
4. NO commit, NO install (hay una sesión de prueba en el dispositivo).

=== ENTREGABLE (español, máx 20 líneas) ===
- Correcciones aplicadas (numeradas)
- BUILD SUCCESSFUL/FAILED + conteo de tests
- Algún riesgo residual que decidas NO corregir y por qué
