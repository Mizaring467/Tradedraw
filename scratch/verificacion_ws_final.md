# Verificación independiente — Suscripción WebSocket Binomo (TradeDraw)

- **Fecha:** 2026-09-17
- **Verificador:** subagente independiente (skill `probador`). No participó en el arreglo y no modificó código.
- **Dispositivo:** `192.168.1.185:5555` — `2311DRK48G` (duchamp_global)
- **Bridge:** `adb forward tcp:18080 tcp:8080`
- **Veredicto: PASA**

---

## Resumen por afirmación

| # | Afirmación | Veredicto |
|---|---|---|
| 1 | Causa raíz: el formato viejo `data:[{ric}]` era descartado en silencio; el correcto es `rics` plural | **NO VERIFICABLE (parcialmente CONFIRMADA)** |
| 2 | El socket WebSocket **nativo** recibe ticks reales del broker | **CONFIRMADA** |
| 3 | Código: `rics` plural, propiedades públicas intactas, guard de frescura presente | **CONFIRMADA** |
| 4 | Tests ≥ 90 y 0 fallos | **CONFIRMADA** |

---

## A) Evidencia en vivo del dispositivo (logcat)

Tras `am force-stop` + relanzamiento, el handshake y los frames quedan registrados:

```
09-17 21:50:03.110 15366 18869 D BinomoWebSocketClient: WebSocket conectado exitosamente: wss://ws.binomo.com/?v=2&vsn=2.0.0
09-17 21:50:03.114 15366 18869 D BinomoWebSocketClient: Suscripción enviada para activo: Z-CRY/IDX (rics + phoenix, ref=2)
09-17 21:50:07.083 15366 18869 D BinomoWebSocketClient: Frame WS crudo #1: {"data":[{"field":"Z-CRY/IDX","action":"subscribe"}],"success":true,"errors":[]}
09-17 21:50:07.457 15366 18869 D BinomoWebSocketClient: Frame WS crudo #2: {"data":[{"assets":[{"sent_at":"2026-09-17T05:17:05.298935Z","provider_time":"...","rate":641.867391475,"precision":10,"repeat":0,"ask":641.86739149,"bid":641.86739146,"ric":"Z-CRY/IDX",...
```

Observaciones:

- **Frame #1 = ACK del subscribe con `"success":true`** (no un error ni cierre de socket). Presente.
- **Frames #2..#N = ticks reales** con `"rate":<número>` (641.867391475, 641.867391415, 641.867391355…) y `"ric":"Z-CRY/IDX"`. Ritmo ≈ 2.5 ticks/s. Presentes.
- Los frames salen **truncados a ~4 KB** por el límite de payload del ring buffer de logcat (`max payload 4068 B`), pero el campo `"ric":"Z-CRY/IDX"` es visible en todos los frames capturados.
- La sesión reconectó entre endpoints (`ws.binomo.com` y `as.binomo.com`), ambos sirviendo el mismo feed correctamente.

## B) Sondeo del bridge (3 sondeos separados 10 s)

### Serie 1 — sesión inicial del agente
```
SONDA 1: lastTickAgeMs=66  | isFresh=true | tickSource="socket"   | rawSocketMessages=21  | socketTicks=20
SONDA 2: lastTickAgeMs=62  | isFresh=true | tickSource="socket"   | rawSocketMessages=46  | socketTicks=45
SONDA 3: lastTickAgeMs=227 | isFresh=true | tickSource="socket"   | rawSocketMessages=70  | socketTicks=69
```

### Serie 2 — tras reinicio limpio de la app
```
sondeo 1: lastTickAgeMs=109 | tickSource="socket" | socketTicks=45
sondeo 2: lastTickAgeMs=10  | tickSource="socket" | socketTicks=68
sondeo 3: lastTickAgeMs=105 | tickSource="socket" | socketTicks=92
```

### Serie 3 — 6 muestras, 8 s de separación
```
1: lastTickAgeMs=91  | tickSource="socket"   | socketTicks=160
2: lastTickAgeMs=258 | tickSource="socket"   | socketTicks=179
3: lastTickAgeMs=386 | tickSource="socket"   | socketTicks=199
4: lastTickAgeMs=97  | tickSource="socket"   | socketTicks=219
5: lastTickAgeMs=194 | tickSource="headless" | socketTicks=238
6: lastTickAgeMs=306 | tickSource="socket"   | socketTicks=258
```

**`socketTicks` crece de forma monótona en las tres series** (20→45→69; 45→68→92; 160→179→199→219→238→258), a un ritmo estable de ~19 ticks / 8 s (~2.4 ticks/s), coherente con el ritmo observado en los frames de logcat. `lastTickAgeMs` siempre pequeño (10–389 ms, nunca `-1` una vez establecido) e `isFresh:true` constante.

## C) Verificación del código

**`BinomoWebSocketClient.kt`**

- `subscribeToAsset` (líneas 295-313) envía `put("action","subscribe")` + `put("rics", JSONArray{ put(asset) })` — clave **plural** y **array de strings**. Además manda dos frames Phoenix v2 (`asset:<RIC>` y `rates`). Cumple.
- Propiedades públicas **intactas**, con las firmas exigidas por Fase 0:
  - `val isConnected: Boolean` (línea 68, con `@get:JvmName("isConnected")` para no romper llamadas Java).
  - `val lastTickAgeMs: Long` (línea 74).
  - `fun isFeedFresh(freshnessThresholdMs: Long = DEFAULT_FRESHNESS_MS): Boolean` (línea 84). Cumple.
- Contabilidad de procedencia bien formada: `lastTickSource` (línea 104), `rawSocketMessages` (108), `socketTicks` (112). En `processIncomingMessage` (línea 504) `lastTickSource = source`, y `if (source == "socket") socketTicks++` (línea 505).

**`TradingEngine.kt`**

- `private val FEED_MAX_AGE_MS = BinomoWebSocketClient.DEFAULT_FRESHNESS_MS` (línea 161). Presente.
- Guard activo en línea 1281: `if (tickAgeMs == null || tickAgeMs >= FEED_MAX_AGE_MS || !isFeedFresh())` con log "no se degrada a otra fuente". Presente y sin degradación a captura de pantalla.

## D) Tests

```
com.example.tradedraw.BinomoWebSocketClientTest      tests=5  fail=0 err=0
com.example.tradedraw.ChartViewportControllerTest    tests=6  fail=0 err=0
com.example.tradedraw.ExampleUnitTest                tests=1  fail=0 err=0
com.example.tradedraw.RiskManagerTest                tests=24 fail=0 err=0
com.example.tradedraw.ScreenCaptureManagerTest       tests=3  fail=0 err=0
com.example.tradedraw.SyntheticCandleEngineTest      tests=23 fail=0 err=0
com.example.tradedraw.TradingEngineTest              tests=24 fail=0 err=0
com.example.tradedraw.VisualHarnessTest              tests=4  fail=0 err=0
TOTAL=90 FAIL=0 ERR=0
```

Coincide exactamente con la línea base histórica (90 / 0).

---

## Discrepancias, ambigüedades y límites de la verificación

1. **Afirmación 1 (causa raíz) — NO VERIFICABLE como refutación empírica.** La verificación se hizo por **inspección de código** (el código nuevo usa `rics` plural) y por **evidencia positiva del resultado** (el ACK `success:true` y la llegada de ticks). No se reprodujo el formato viejo contra el servidor real para confirmar que efectivamente era descartado en silencio; esa parte de la afirmación (el comportamiento del servidor ante el formato antiguo) queda como **no comprobada**. El arreglo funciona, pero la atribución causal exacta no está probada de forma independiente. Nótese además que el mensaje enviado incluye tres frames (rics + dos Phoenix), por lo que no puede aislarse cuál de ellos produce los ticks.

2. **`tickSource` es volátil y puede confundir.** Es el *último escritor*, no un contador: en la serie 3 pasó a `"headless"` en una sola muestra aunque `socketTicks` siguiera creciendo en esa misma muestra (219→238). Un observador que mire solo `tickSource` podría concluir erróneamente que el headless alimenta el feed. La prueba correcta es el crecimiento de `socketTicks`, y ese crecimiento es inequívoco.

3. **El WebView headless está activo en paralelo** (logcat: `HeadlessConsole: ... binomo.com/assets/binomo/themes/light.css`). Ambos canales inyectan ticks. Por tanto no puede afirmarse que el socket nativo sea la *única* fuente: es una fuente **demostrada e independiente**, lo que satisface la afirmación 2 tal como está redactada, pero el feed no está aislado del headless.

4. **El bridge HTTP es frágil (hallazgo no solicitado).** Durante la verificación el listener de 8080 murió mientras el proceso de la app seguía vivo (`pidof` devolvía un PID, `ss -ltn | grep 8080` vacío, `curl` → `HTTP:000`). No hubo excepción ni traza en logcat (`TradeDrawHttp` solo registró el arranque). Tras `force-stop` + relanzamiento volvió a funcionar. Además, los sondeos fallan de forma intermitente (`SIN RESPUESTA` en ráfaga) y requieren reintento. Esto **no invalida** la afirmación 2 —cuando el bridge responde, los datos del socket son coherentes y crecientes— pero significa que el bridge no es una fuente de telemetría fiable sin reintentos, y que una lectura puntual de `socketTicks:0` podría ser un falso negativo.

5. **Limitación de logcat.** Los frames se truncan a ~4068 B, así que la cola de cada frame (cierre del JSON) no es visible. El `rate` y el `ric` sí lo son. El buffer `main` se satura y rota: una primera lectura sin `logcat -c` previo devolvió **cero** líneas del tag, lo que podría interpretarse erróneamente como "no hay frames".

---

## Conclusión

La afirmación central (**el socket WebSocket nativo recibe ticks reales del broker**) queda **CONFIRMADA** por dos vías independientes y concordantes: logcat (ACK `success:true` + frames con `rate`/`ric`) y telemetría del bridge (`socketTicks` creciente 20→258 a ritmo estable, `isFresh:true`, `lastTickAgeMs` < 400 ms). El código conserva las propiedades públicas exigidas y el guard de frescura. Los tests están en 90/0, igual que la línea base.

**Veredicto: PASA.**