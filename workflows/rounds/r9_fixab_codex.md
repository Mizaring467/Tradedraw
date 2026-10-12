TAREA — RONDA 9 · FIX-A + FIX-B (codex)

Repo: C:\Users\heidy\Tradedraw (rama main, HEAD 0e95d18). Kotlin, comentarios en español.
NO toques umbrales, señales, indicadores ni estrategia. NO optimices. Solo estos 2 fixes + tests.
NO hagas commit ni push ni install (los hace el orquestador tras auditoría).

=== FIX-A · price_y sin truncar en el journal ===
Archivo: app/src/main/java/com/example/tradedraw/TradeJournalLogger.kt
Problema: `buildLine` (L256-263) formatea `priceY` con `%.2f` → destruye la precisión
(ETHUSD-OTC 1049.37 se guarda como 1049.37 ok, pero Z-CRY/IDX 641.86728657 se guarda 641.87).
Existe ya `formatDynamicPrice(price: Double): String` en MarketTick.kt (L159-166).

CAMBIO MÍNIMO: en `buildLine`, reemplazar el `%.2f` de `priceY` por `%s` y pasar
`formatDynamicPrice(priceY.toDouble())`. NO toques los demás `%.2f` (stake, balance, ratios, velocity).
La cadena de formato pasa de `...%s,%.2f,%.2f,%.2f,%.2f,%s,%.2f,...` a `...%s,%s,%.2f,%.2f,%.2f,%s,%.2f,...`
(el 6º campo es priceY). Verifica que el orden de argumentos sigue alineado con el HEADER:
  timestamp,iso_date,strategy,submode,action,confidence,price_y,stake,base_balance,result,...

TEST unitario (en TradeJournalLoggerTest.kt, ya existe):
- Verificar que para priceY=1049.37f la línea contiene "1049.37" y NO "1049.37" truncado de forma distinta.
- Verificar que para priceY=641.86728657f la línea contiene "641.867287" (o el formato de formatDynamicPrice),
  y NO "641.87". Es decir: el valor de price_y en el CSV tiene >= 6 decimales significativos.

=== FIX-B · TIE incierto cuando la lectura de saldo no es fresca ===
Archivo: app/src/main/java/com/example/tradedraw/TradingEngine.kt (~L1834-1852).

Problema: si `AutoTradeAccessibilityService.instance == null` (servicio desconectado por MIUI), la lectura
cae al caché `latestObservedBalance`, que puede devolver un valor idéntico a baseBalance → se registra un
TIE falso aunque la orden SÍ se haya procesado.

CAMBIO:
1. Antes de calcular `currentBal`, determinar si la lectura es FRESCA:
   `val saldoFresco = AutoTradeAccessibilityService.instance != null && (System.currentTimeMillis() - AutoTradeAccessibilityService.lastBalanceReadMs) < 3000L`
   (Si no existe `lastBalanceReadMs`, añádelo como @Volatile en AutoTradeAccessibilityService.kt, actualizado
   cada vez que `readCurrentBalance()` lee del servicio real.)
2. Si el resultado calculado es TIE (`isTie == true`) PERO `!saldoFresco`: en vez de TIE, registrar el resultado
   como **TIE_INCIERTO** en el journal y **NO contabilizar** (no incrementar W/L, ni racha, ni autoaprendizaje,
   ni voids consecutivos de hard-stop).
3. Si el saldo SÍ es fresco, el comportamiento actual se conserva EXACTAMENTE (TIE real cuenta como antes).

Añade a `TradeJournalLogger` el resultado "TIE_INCIERTO" (es solo un string en la columna result; no requiere
cambio de esquema). En `RiskManager`: asegúrate de que NO existe un método que cuente TIE_INCIERTO; simplemente
no se llama a `recordTradeVoid()` en esa rama.

TESTS unitarios para AMBAS ramas:
- saldo fresco + diff ≈ 0 → TIE (cuenta como void).
- saldo NO fresco (instance null) + diff ≈ 0 → TIE_INCIERTO (NO cuenta, W/L sin cambios).

=== VERIFICACIÓN OBLIGATORIA ===
1. Balance de llaves {} correcto en cada .kt editado.
2. Ejecuta: .\gradlew.bat testDebugUnitTest --no-daemon
3. Ejecuta: .\gradlew.bat assembleDebug --no-daemon --no-configuration-cache
4. NO commit, NO push, NO install.

=== ENTREGABLE (español, máx 20 líneas) ===
- Archivos modificados (ruta + resumen de 1 línea)
- Salida BUILD SUCCESSFUL/FAILED + conteo de tests
- Confirmación textual de que NO cambiaste umbrales/señales/indicadores
- Bloqueos exactos si algo no compila
