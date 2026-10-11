TAREA — RONDA 3 · VALIDACIÓN DE COHERENCIA HUD↔BINOMO (agy)

Dispositivo: UGU8KFUKM7E6CE8D. Repo: C:\Users\heidy\Tradedraw. NO edites código Kotlin.

=== Contexto ===
Los fixes de Ronda 3 ya están aplicados y el APK instalado. El motor YA EJECUTA (root tap),
ya no hay VOID. Hay 2 LOSS confirmados en el journal del dispositivo.
Se está corriendo una sesión de monitoreo de 25 min EN PARALELO (script python).

=== TU TAREA (read-only, con evidencia viva) ===
Verificar que el HUD de TradeDraw sea FIEL a lo que muestra Binomo. Compara estas 3 fuentes:
1. HUD de TradeDraw: adb -s UGU8KFUKM7E6CE8D shell uiautomator dump /sdcard/r3_hud.xml
   y luego adb -s UGU8KFUKM7E6CE8D shell cat /sdcard/r3_hud.xml
   (busca: Tendencia, Patrón, Filtro S/R, Plan, % CALL/PUT, W: x | L: y, WS Activo precio)
2. /status del motor: curl.exe -s --max-time 6 http://127.0.0.1:8080/status
   (analysis.trend, callPower, putPower, balance, engine.*)
3. Binomo real: adb -s UGU8KFUKM7E6CE8D exec-out screencap -p > .agents/loop/r3_agy_hud.png
   y describe lo que ves (tendencia del gráfico, balance, precio).

Haz AL MENOS 5 comparaciones en momentos distintos (espera 30-60s entre cada una).
Reporta divergencias con evidencia literal.

=== ENTREGABLE (español, máx 25 líneas) ===
- Tabla: momento | HUD tendencia | /status trend | Binomo tendencia | ¿coincide?
- Balance: HUD vs Binomo vs /status
- Precio WS: HUD vs Binomo (label de precio del gráfico)
- Plan del HUD: ¿usa la ventana :01s - :05s correctamente?
- VEREDICTO: ¿el HUD es fiel a Binomo? ¿alguna divergencia pendiente?
- Ruta de la captura .agents/loop/r3_agy_hud.png
