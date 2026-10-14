# PREREGISTRO Ronda 15-A

**Estado:** hipótesis y criterios de falsación fijados **ANTES** de escribir cualquier
instrumentación.
**Fecha:** 2026-10-14
**Árbol de partida:** `2f53b90` (`HEAD == origin/main`, árbol limpio), 309 tests, 0 fallos.
**Objetivo:** atribuir la causa del rearme espontáneo a `AUTONOMOUS` observado **dos veces** en R14.

> Este documento fija decisiones **a priori**. Si algo se cambia después, se anota aquí como
> enmienda fechada y con el motivo. `PREREGISTRO_R10.md`, `R12` y `R14` **no se tocan**; este
> fichero no modifica ningún umbral de trading.

---

## 0. Regla que gobierna la ronda

**Nada de lo que se añade en R15-A puede cambiar una decisión de trading.** El objetivo es
*observar* y *atribuir*. Concretamente:

- No se toca la ventana sniper (`:01-:05`), ni CHOP, ni DojiRatio, ni la confluencia ≥ 80, ni el
  dimensionamiento quarter-Kelly, ni los vetos V1/V2.
- No se relaja ni se endurece ningún veto. No se añade ningún filtro nuevo.
- **La instrumentación es sólo observabilidad:** un evento nuevo en el buffer de `/events` y un
  log. No se cambia el valor del modo, ni quién lo cambia, ni cuándo.
- `N` sigue siendo **0** operaciones supervisadas con estos cambios.

### 0.1 Regla de atribución (fijada ahora, para no moverla después)

Una hipótesis se declara **SOSTENIDA** sólo si se cumple **una** de estas dos cosas:

1. se observa el rearme **y** el evento `MODE_CHANGE` registra un `ORIGEN` perteneciente a esa
   hipótesis; o
2. existe una **ruta en el código** que ejecuta el rearme **sin interacción humana**, demostrada
   por lectura (no por sospecha), y el `ORIGEN` observado la señala.

Se declara **FALSADA** si el rearme se reproduce y el `ORIGEN` corresponde a una ruta que requiere
toque humano, o si la ruta postulada por esa hipótesis no existe en el código.

Se declara **NO CONCLUYENTE** si no se reproduce el rearme en la ventana de observación, o si se
reproduce sin que `MODE_CHANGE` capture el origen.

**Prohibición expresa:** no se declarará causa raíz por plausibilidad. R13 declaró una causa raíz
falsa (carrera del bypass del HUD con el temporizador de 350 ms) y hubo que corregirla en
`c83992b`. Si no se atribuye, se escribe **NO ATRIBUIDA**.

---

## 1. Hechos medidos en R14 (punto de partida, no hipótesis)

| hecho | evidencia |
| :--- | :--- |
| El modo estaba en `AUTONOMOUS` al abrir R14 | `loops/R14_REPORTE.md` §7 hallazgo 1 |
| Volvió a `AUTONOMOUS` durante R14, tras ponerlo en `DISABLED` | `loops/R14_REPORTE.md` §7 hallazgo 2 |
| Ninguna de las dos veces hubo operación | `balance` idéntico a R13, `tapsDespachados=0`, `orderState=NINGUNA`, `hasPendingTrade=false` |
| El motor sí evaluó: 290 vetos V2 | `/events` en R14, activo `Z-CRY/IDX` (rango `3,3e-10` vs umbral `1e-05`) |
| No hay autostart ni persistencia del modo | `TradingEngine.kt:49`, `mode` es sólo memoria |
| `logcat` no conservó la transición | R14 §7, causa no atribuida |

**Corrección de un supuesto de R14 que hay que verificar, no asumir:** R14 §9 y §7 dieron por
hecho que `OverlayService` "no se puede rearrancar sin conceder `MediaProjection` por toque". La
lectura de `OverlayService.onStartCommand` (`OverlayService.kt:210-231`) muestra que la captura
está **deshabilitada por decisión de producto** (`startTradeDrawForeground(hasMediaProjection =
false)`, y el `dataIntent` de MediaProjection se **ignora** explícitamente), y que el servicio
devuelve **`START_STICKY`**. Consecuencia: el sistema **puede recrear el servicio solo**, sin
toque y sin `MediaProjection`. Esto **abre** H-LIFECYCLE y **cierra** parcialmente la explicación
que R14 dio del bridge caído. Se verifica en Fase 4.

---

## 2. Hipótesis y criterio de falsación

### H-LIFECYCLE — recreación del servicio y del estado

**Enunciado:** el sistema mata y recrea `OverlayService` (`START_STICKY`), o se recrea por cambio
de configuración, y en esa recreación el modo acaba en `AUTONOMOUS`.

**A favor (leído en el código):** `onStartCommand` devuelve `START_STICKY`
(`OverlayService.kt:230`); `TradingEngine` se **reconstruye** en `onCreate` (`OverlayService.kt:247`),
y un `TradingEngine` nuevo nace con `mode = DISABLED` (`TradingEngine.kt:49`). Es decir, una
recreación **no** produce `AUTONOMOUS` por sí sola — pero **sí borra** cualquier `DISABLED` que
hubiera puesto el usuario, y `onCreate` lee `TradeDraw_Config`/`saved_strategy`
(`OverlayService.kt:251-257`).

**Lo que la FALSARÍA:** que el `ORIGEN` del `MODE_CHANGE` no sea ningún camino de `onCreate` /
`onStartCommand` / `onConfigurationChanged`, o que no aparezca ningún `MODE_CHANGE` en absoluto
tras una recreación (porque entonces el modo nuevo nunca pasó por el setter).

**Riesgo declarado:** esta hipótesis tiene una trampa. Si la recreación construye un `TradingEngine`
nuevo, el campo `mode` se pone a `DISABLED` **sin pasar por el setter**, así que **no emitiría
`MODE_CHANGE`**. Un rearme por esta vía sería **invisible** para la instrumentación. Se declara de
antemano para no confundir "no lo vi" con "no ocurrió".

### H-RESUME — reanudación automática tras Stop Loss, cooldown o pausa

**Enunciado:** alguna ruta automática reanuda la operativa y pone `AUTONOMOUS` sin toque.

**Estado de la lectura (Fase 1):** `resumeAutonomousTrading` (`AutonomousAgentController.kt:98`)
**sí** pone `AUTONOMOUS` y resetea la racha, pero sus **dos** llamadores son
`AgentChatOverlay.kt:340` (comando `RESUME_TRADING`, que llega del chat: exige texto humano) y no
se encontró ningún timer que lo invoque. El bucle de auditoría (`auditRunnable`,
`AutonomousAgentController.kt:43-57`) llama a `auditFinancialSync`, `auditEngineHealth`,
`auditStrategyAdaptation` y `auditChartViewport`, y **ninguno** escribe `mode`.

**Lo que la FALSARÍA:** que el `ORIGEN` apunte a `resumeAutonomousTrading` **sin** que exista un
mensaje humano en el chat en ese instante.

### H-CHAT — el agente de chat emite una orden de modo

**Enunciado:** el chat (por texto humano o por etiqueta `[CMD:MODE_AUTONOMOUS]` que devuelve la
IA) pone `AUTONOMOUS`.

**A favor (leído):** `executeAgentCommand` (`AgentChatOverlay.kt:326-363`) ejecuta
`MODE_AUTONOMOUS` / `MODE_YOLO` / `RESUME_TRADING`, y el callback de `sendChatMessage`
(`AgentChatOverlay.kt:311-323`) ejecuta el `cmdTag` que devuelva la IA. Además, el parser de
lenguaje natural es **laxo**: basta que el texto contenga **`"conservador"`**
(`AgentChatOverlay.kt:261`) o **`"yolo"`** (`AgentChatOverlay.kt:247`) para armar el bot.

**Lo que la FALSARÍA:** que el `ORIGEN` apunte al chat **sin** que exista una llamada a
`sendChatMessage` ni un mensaje en ese instante; o que el chat no estuviera abierto.

**Riesgo declarado:** `sendChatMessage` sólo se invoca desde `AgentChatOverlay.kt:311`, que exige
que el usuario escriba. No se encontró ningún disparo automático del chat.

### H-BROADCAST — comando externo por broadcast exportado

**Enunciado:** algo fuera de la app emite el broadcast `com.example.tradedraw.CMD` con
`action=AUTO` o `action=YOLO`.

**A favor (leído, y es el hallazgo más fuerte de la Fase 1):** `OverlayService.kt:303-309`
registra `overlayCommandReceiver` para `com.example.tradedraw.CMD` con **`RECEIVER_EXPORTED`** en
Android 13+, y ese receptor pone `AUTONOMOUS` en las ramas `"AUTO"` (`:151-154`) y `"YOLO"`
(`:155-158`). **Un receptor exportado lo puede invocar cualquier app del dispositivo**, sin
permiso.

**Lo que la FALSARÍA:** que el `ORIGEN` no sea `overlayCommandReceiver` cuando el modo cambie; o
que, tras revisar el patrón de cambios, se demuestre que sólo ocurre por interacción.

**Nota:** esto **no** es una hipótesis sobre malware. Es un vector de rearme real con
independencia de quién lo use, y explica el rearme **sin toque humano en la app**.

### H-HUMAN — toque accidental en el HUD o en un diálogo

**Enunciado:** un toque real del usuario o un fantasma táctil eligió "Modo Autónomo" en el
diálogo (`OverlayService.kt:800-854`) o en el HUD (`:833-844`).

**Lo que la FALSARÍA:** un `ORIGEN` correspondiente a `setTradingMode` invocado desde los handlers
de UI **sin** que haya habido un evento táctil; o —al revés— la constatación de que el rearme
coincide con actividad táctil registrada.

**Riesgo declarado:** en R14 el usuario **no estaba** presente (ronda desatendida) y aun así el
modo se rearmó. Eso **debilita** H-HUMAN, pero no la falsa: pudo haber un fantasma táctil o una
app en primer plano. Se mantiene como hipótesis a descartar, no a asumir.

### H-BRIDGE — alguien llamó a `/mode?name=AUTONOMOUS`

**Enunciado:** una petición HTTP al bridge puso `AUTONOMOUS`.

**A favor (leído):** `/mode` (`TradeDrawHttpBridge.kt:165-175`) escribe
`overlayService.tradingEngine.mode = m` directamente.

**Lo que la FALSARÍA:** que el `ORIGEN` no sea `TradeDrawHttpBridge`. **Esta hipótesis es
fácilmente descartable en Fase 4** porque el bridge sólo escucha en loopback y hay que llegar por
`adb forward`: si nadie lanza un `curl` a `/mode`, no puede ser la causa.

---

## 3. Instrumentación comprometida (Fase 3)

1. Evento `MODE_CHANGE` en el buffer de `/events` (`TradeEventLog`) con: instante, modo anterior,
   modo nuevo y **ORIGEN** = 3-5 frames de `Thread.currentThread().stackTrace`.
2. **Centralizado en el único setter** de `TradingEngine.mode` (`TradingEngine.kt:49-57`), que ya
   existe: no se crea un setter nuevo ni se cambia la semántica.
3. La instrumentación **no decide nada**: sólo registra. El valor del modo no cambia por
   instrumentar.
4. Test unitario que verifique que el evento se emite y que el **ORIGEN no está vacío**.
5. Esquema **solo-append**: no se reordena ningún campo existente de `TradeEventLog`.

**Limitación declarada a priori:** las escrituras que **no** pasan por el setter (construcción de
un `TradingEngine` nuevo en `onCreate`) **no** emiten `MODE_CHANGE`. Se cubren parcialmente
registrando también un `MODE_CHANGE` en `onCreate` que refleje el estado inicial con
`ORIGEN = onCreate`. Si aun así no aparece ningún evento, la conclusión es **NO CONCLUYENTE**, no
"no ocurrió".

---

## 4. Medición comprometida (Fase 4)

| parámetro | valor fijado ahora |
| :--- | :--- |
| Duración de la observación | **20-30 min** con la app en reposo |
| Frecuencia de sondeo | cada **10 s** a `/status` y `/events` |
| Interacción durante la observación | **ninguna** |
| Modo de partida | `DISABLED` |
| Acción si aparece `AUTONOMOUS` | devolver a `DISABLED` de inmediato y verificar `orderState=NINGUNA`, `hasPendingTrade=false` |
| Prohibido | ejecutar taps, `/trade`, poner `AUTONOMOUS` a mano |
| Batería | medida al empezar y al terminar; **detener todo si < 20 %** |

**Umbral de decisión:** basta **un** `MODE_CHANGE` observado con `ORIGEN` legible para atribuir.
Si no se observa ninguno en 20-30 min, ninguna hipótesis queda falsada y el resultado global es
**NO CONCLUYENTE** — con la instrumentación lista para la próxima ronda.

**Nota de honestidad estadística:** R14 observó **2** rearmes en una ventana de ~1,5 h. Si la tasa
fuera de ~1,3/h, esperar ver 0 en 30 min es **perfectamente posible** (~50 % de probabilidad). Por
tanto, **no ver nada en Fase 4 no refuta ninguna hipótesis**; eso se declara por adelantado para
no convertir una ausencia de observación en una conclusión.

---

## 5. Lo que esta ronda NO hace

- **No** corrige el vector del receptor exportado aunque se confirme. Un cambio de
  comportamiento de seguridad merece su propia ronda con el usuario despierto.
- **No** modifica `master_traders_skill` ni ningún umbral.
- **No** declara causa raíz sin `ORIGEN` medido.
- **No** usa `duration_sec` como evidencia (es artefacto del método de liquidación).
