# R24 · W02 ABORTADA — CAUSA RAÍZ DEL FALLO DE TRANSPORTE ADB

**Fecha:** 2026-10-26 23:26–23:48 (America/Bogota) · **Rama:** `r10-night` · **HEAD:** `692d6f3`
**Build medida:** versionCode 19 · `1.9-r23h-exhaustion` · gitSha `692d6f3` · buildId `R23H-BUY-EXHAUSTION`
**Dispositivo:** Redmi A1 (`220733SL`, Android 12) · serial ADB `192.168.1.196:5555`
**Artefactos:** `loops/r24_validation/window_02/` (19 muestras, 473 ticks) · monitor: `scripts/validation/live_monitor_r23_autonomous.py`

---

## 1. Resultado de la ventana

| Métrica | Valor |
| :--- | :--- |
| Duración nominal | 20 min (inicio 23:26, cierre real 23:48) |
| Muestras de `/status` válidas | **19** (solo los primeros ~4 min) |
| Ciclos del monitor sin respuesta | **100** |
| Taps despachados | 0 |
| Trades | 0 |
| Decisiones capturadas | 0 |
| Balance | 37.083.735,04 → 37.083.735,04 (Δ 0,00) |
| Cierre seguro | `DISABLED` **no se pudo ordenar**: bridge inaccesible desde el host |

Ventana **ciega durante 16 de 20 minutos**. No es evaluable para V1/V3/V4 (y sin decisiones tampoco para V2).

## 2. Causa raíz medida: cae el `adb forward`, NO la app

Discriminación por evidencia, en este orden:

1. **El proceso no murió.** `ActivityManager` registra `Start proc 26449:com.example.tradedraw` a las 23:24:53 y el siguiente evento sobre ese PID es el `Killing … stop com.example.tradedraw` de las **23:48:19**, disparado por el `am force-stop` del cierre. Entre ambas marcas no hay `am_kill`, `lowmemorykiller` ni `FATAL` de la app.
2. **El servicio siguió vivo y en primer plano.** A las 23:41 `dumpsys activity services` muestra `OverlayService` con `isForeground=true` y `AutoTradeAccessibilityService` enlazado; el HUD y el feed WS seguían pintando ticks (`WS Activo (Crypto IDX): 641.867251`).
3. **El bridge nunca dejó de escuchar.** Con el proceso 26449 vivo, `adb -s … forward --list` devolvía **vacío** a las 23:48. Tras reponer el reenvío, `curl http://127.0.0.1:8080/status` respondió **de inmediato** con el payload correcto (`versionCode 19`, `gitSha 692d6f3`, `mode DISABLED`, `isDemoAccount true`).
4. **El primer síntoma del monitor es exactamente ese.** `status_samples.jsonl` deja de crecer a las **23:30:06** y las muestras siguientes traen `"status": null, "balance_quality": "UNREADABLE"`, sin que el modo ni el balance del motor cambien.

**Conclusión:** el bridge del teléfono sigue sirviendo en loopback; lo que desaparece es el reenvío `tcp:8080` del host. Al perderse, el monitor deja de ver `/status` y la ventana se vuelve ciega. W01 documentó el mismo fallo con el mismo nombre («caída del forward»), así que es recurrente y sistémico, no un caso aislado.

**Causa del reenvío perdido:** `adbd` sobre Wi-Fi reconecta (Wi-Fi power save / reasociación). `adb forward` es estado del servidor adb del host y **no sobrevive** a esa reconexión. Ningún componente lo reponía.

## 3. Corrección aplicada (arnés, no estrategia)

`scripts/validation/adb_forward_watchdog.sh` — proceso aparte que, cada N segundos:

- comprueba `adb get-state`; si el dispositivo no está `device`, ejecuta `adb connect`;
- comprueba `adb forward --list`; si falta `tcp:8080 tcp:8080`, lo repone y lo registra en `~/forward_watchdog.log`.

**No toca la app, no cambia el modo del motor y no altera ningún criterio del preregistro.** Es transporte puro.

**Verificación:** con el watchdog activo (intervalo 3 s) se removió el reenvío a mano (`adb forward --remove tcp:8080`); el log muestra `REPUESTO forward tcp:8080 tcp:8080` **4 s después** y el listado vuelve a mostrarlo.

## 4. Consecuencia sobre el Cubo R24

- W01 (23-oct) y W02 (26-oct): ciegas por la misma causa → **2 de las 4 ventanas del tope consumidas sin datos utilizables**.
- V1, V3 y V4 ya no pueden ser `SÍ` en el cubo: el veredicto global de instrumentación queda **NO EVALUABLE**, con independencia de lo que ocurra en W03/W04.
- El modo del motor se dejó en **`DISABLED`**, `orderState=NINGUNA`, `hasPendingTrade=null` e `isDemoAccount=true` al cerrar. Cero órdenes y cero COP expuestos.

## 5. Recomendación

1. Cerrar la Fase C/D de R24 como **NO EVALUABLE por fallo de transporte** (no por resultado): el cubo no puede emitir juicio de instrumentación y forzarlo sería exactamente lo que prohíbe la regla de oro del preregistro.
2. Abrir **R25** con preregistro nuevo, build congelada y el watchdog como condición de arranque: ninguna ventana se abre sin que el watchdog esté corriendo y haya certificado el reenvío durante al menos 60 s seguidos.
3. Añadir al monitor un criterio de aborto temprano: si hay **3 muestras consecutivas** con `status=null`, la ventana se declara inválida en el acto y no consume el cupo de validación (hoy sigue 20 minutos a ciegas y aun así escribe `Veredicto: PASS` en su plantilla, lo que es engañoso y debe corregirse).
