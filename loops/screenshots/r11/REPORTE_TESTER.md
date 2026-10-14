# REPORTE TESTER — Ronda 11 (R11)

**Dispositivo:** ~~POCO X6 Pro~~ → **Redmi A1 `220733SL`** (Android 12 Go, `mt6761`, 4 núcleos) — serial ADB Wi-Fi `192.168.1.196:5555`.
*(Erratum R13/adenda 1: el modelo y el serial USB `UGU8KFUKM7E6CE8D` de la línea original eran incorrectos. Las cifras de rendimiento y latencia de este informe asumían un teléfono más potente del que es; ver `loops/R13_REPORTE.md`.)*
**Pantalla:** 720x1600 px, densidad 320 (360dp ancho)
**App bajo prueba:** TradeDraw (overlay HUD sobre Binomo `com.marketly.trading`)
**Fecha de sesión:** según reloj de dispositivo, franja 15:18–15:58
**Alcance:** verificación visual pasiva + puente HTTP local (`/status`, `/hud`, `/strategy`, `/submode`). Sin modificación de código Kotlin, sin commits.

## Resumen de veredictos

| Punto | Veredicto |
| :--- | :--- |
| T3 strike/moneyness | NO VERIFICABLE |
| T4 sin datos inventados | PASA (parcial — caso "feed no fresco" NO VERIFICABLE) |
| T5 micro-velocidad | PASA |
| T6a countdown "Vela [1m]" | PASA |
| T6b P&L y etiquetas de stats | PASA (parcial — total resuelto ≠ 0 NO VERIFICABLE) |
| T6c "Liquidando en vela :00s" | PASA (ausente en 28 capturas) |
| T7 coherencia S/R ↔ Plan | PASA |
| T8 layout | **FALLA** |

---

## T3 — Strike / Moneyness de operación abierta

**VEREDICTO: NO VERIFICABLE**

**Evidencia:** no hubo operación abierta en ningún momento de la sesión. El puente HTTP devolvió en todas las consultas de `/status`:

```
"activeSignal": null
"hasPendingTrade": false
"signalsGenerated": 0
"totalWins": 0, "totalLosses": 0
```

Capturas asociadas: `tester_00_setup.png` … `tester_04.png`, `status_log.txt`.

**Observaciones:** no se pudo forzar el estado de operación abierta (no existe endpoint de inyección). La línea `🤖 CALL ▲ · Strike … · ITM/OTM · P&L · Hasta exp` **no aparece** en ninguna captura, por lo que no hay evidencia visual de su formato ni de coherencia ITM/OTM ↔ signo de P&L. NO se reporta como fallo: simplemente no observable en esta sesión.

---

## T4 — Sin datos inventados (racha y termómetro)

**VEREDICTO: PASA (parcial)**

**Evidencia — texto LITERAL leído:**

| Captura | Línea de stats | Termómetro |
| :--- | :--- | :--- |
| `tester_00_setup.png` | `W: 0 \| L: 0 \| Nulas: 0 \| Total: 0 (0.0%)` | `[ 76% CALL / 24% PUT ]` |
| `tester_01.png` | `W: 0 \| L: 0 \| Nulas: 0 \| Total: 0 (0.0%)` | `[ 86% CALL / 14% PUT ]` |
| `tester_02.png` | `W: 0 \| L: 0 \| Nulas: 0 \| Total: 0 (0.0%)` | `[ 68% CALL / 32% PUT ]` |
| `tester_03.png` | `W: 0 \| L: 0 \| Nulas: 0 \| Total: 0 (0.0%)` | `[ 76% CALL / 24% PUT ]` |
| `tester_04.png` | `W: 0 \| L: 0 \| Nulas: 0 \| Total: 0 (0.0%)` | `[ 86% CALL / 14% PUT ]` |
| `tester_05_collapsed_http.png` | `W: 0 \| L: 0 \| Nulas: 0 \| Total: 0 (0.0%)` | `[ 24% CALL / 76% PUT ]` |
| `tester_12_sniper.png` | `W: 0 \| L: 0 \| Nulas: 0 \| Total: 0 (0.0%)` | `[ 76% CALL / 24% PUT ]` |

- Racha mostrada: `· TREND 🟢` (badge de cabecera). Nunca se observó un 50/50 inventado: todos los pares de porcentajes suman exactamente 100.
- `/status` coherente con pantalla: `callPower`/`putPower` (p. ej. 68/32) coinciden con el termómetro capturado en el mismo intervalo.

**Observaciones / límite de la verificación:** el caso "feed sin tick fresco → racha `—`" **NO VERIFICABLE**: el feed nunca dejó de estar fresco durante la sesión (`/status` reportó siempre `"connected": true`, `"isFresh": true`, `"lastTickAgeMs"` entre 2 y 500 ms, `"source": "websocket"`, `"socketTicks"` creciendo). No se pudo provocar la condición de degradación sin manipular el dispositivo.

---

## T5 — Micro-velocidad (signo ↔ flecha/texto)

**VEREDICTO: PASA**

**Evidencia — textos LITERALES leídos:**

| Captura | Texto literal |
| :--- | :--- |
| `tester_00_setup.png` (crop `crop_00b.png`) | `⚡ Micro-Velocidad: -2.03e-08 %/s ▼ Bajista` |
| `tester_01.png` | `⚡ Micro-Velocidad: -3.89e-08 %/s ▼ Bajista` |
| `tester_02.png` | `⚡ Micro-Velocidad: -2.33e-08 %/s ▼ Bajista` |
| `tester_03.png` | `⚡ Micro-Velocidad: +1.26e-0? %/s ▲ Alcista` (exponente parcialmente solapado por el reloj de Binomo `3:20 p. m.`; signo `+` y flecha `▲` legibles) |
| `tester_04.png` | `⚡ Micro-Velocidad: -1.70e-08 %/s ▼ Bajista` |
| `tester_10_closed.png` | `⚡ Micro-Velocidad: -5.81e-09 %/s ▼ Bajista` |
| `tester_11_closed.png` | `⚡ Micro-Velocidad: -1.11e-08 %/s ▼ Bajista` |
| `tester_12_sniper.png` | `⚡ Micro-Velocidad: +1.56e-08 %/s ▲ Alcista` |
| `tester_18_tf5m.png` | `⚡ Micro-Velocidad: +1.80e-08 %/s ▲ Alcista` |
| `tester_20_binomo.png` | `⚡ Micro-Velocidad: -1.56e-08 %/s ▼ Bajista` |

**Observaciones:**
- Notación científica en `%/s` presente y correcta en las 10 muestras.
- **9 casos negativos → `▼ Bajista`; 3 casos positivos → `▲ Alcista`.** Cero contradicciones signo↔texto.
- El BUG ANTIGUO (`▲ Impulso Alcista` con número negativo) **NO apareció en ninguna de las 28 capturas**. El sufijo actual es `▲ Alcista` / `▼ Bajista`.
- El valor `%/s` queda parcialmente tapado por textos del gráfico de Binomo (`3:20 p. m.`, `3:40 p. m.`) que se transparentan a través del panel. Es consecuencia del `hud_alpha` del usuario (33%), no del formato del texto.

---

## T6a — Countdown de vela en cabecera

**VEREDICTO: PASA**

**Evidencia — texto LITERAL leído:** en todas las capturas la cabecera contiene la palabra **`Vela`**:

| Captura | Cabecera literal |
| :--- | :--- |
| `tester_00_setup.png` | `[AUTO] Estrat: MT Rechazo · TREND 🟢 ⏱️ Vela [1m] :57s 👁️ 33%` |
| `tester_01.png` | `… ⏱️ Vela [1m] :37s …` |
| `tester_02.png` | `… ⏱️ Vela [1m] :11s …` |
| `tester_03.png` | `… ⏱️ Vela [1m] :44s …` |
| `tester_04.png` | `… ⏱️ Vela [1m] :18s …` |
| `tester_05_collapsed_http.png` | `… ⏱️ Vela [1m] :47s …` |
| `tester_17_moved.png` | `… ⏱️ Vela [5m] 3:47` |

**Observaciones:** el formato antiguo `⏱️ [1m] :NNs` (sin `Vela`) no reaparece. El reloj desciende correctamente entre capturas consecutivas. El HUD se adapta al timeframe de Binomo (`[1m]` → `[5m] 3:47`) cuando el timeframe cambió accidentalmente a 5m durante la sesión.

**Cruce con reloj de Binomo (comprobación de coherencia):**

| Captura | HUD | Binomo (misma captura) | Delta |
| :--- | :--- | :--- | :--- |
| `tester_01.png` | `Vela [1m] :37s` | `Tiempo para comprar 00:06` / `Hasta expiración 00:36` | 1 s |
| `tester_02.png` | `Vela [1m] :11s` | `00:40` / `01:10` | 1 s |
| `tester_03.png` | `Vela [1m] :44s` | `00:13` / `00:43` | 1 s |
| `tester_04.png` | `Vela [1m] :18s` | `00:47` / `01:17` | 1 s |

Delta constante de 1 s frente a "Hasta expiración", sin deriva acumulada → consistente con el throttle de refresco de 400 ms (ya caracterizado por el orquestador). NO se cuenta como fallo.

---

## T6b — P&L y etiquetas de estadísticas

**VEREDICTO: PASA (parcial)**

**Evidencia — texto LITERAL leído (idéntico en las 28 capturas):**

```
W: 0 | L: 0 | Nulas: 0 | Total: 0 (0.0%)
```

**Observaciones:**
- La etiqueta **`Nulas:`** está presente y la etiqueta antigua **`T:`** NO aparece en ninguna captura (sustituida por `Total:`). Correcto.
- Límite: el contador `Total` se mantuvo en `0` toda la sesión (0 operaciones resueltas, `totalWins=0`/`totalLosses=0` en `/status`), por lo que **NO VERIFICABLE** que `Total` cuente el total *resuelto* (W+L+Nulas) y no el número de operaciones, cuando ese valor es ≠ 0. La evidencia solo prueba que la etiqueta y el formato son los nuevos.

---

## T6c — Ausencia total de "Liquidando en vela :00s"

**VEREDICTO: PASA**

**Evidencia:** la cadena literal `Liquidando en vela :00s` **no aparece en ninguna de las 28 capturas** de la sesión: `tester_00_setup.png`, `tester_01..04.png`, `tester_05_collapsed_http.png`, `tester_06_after_tap.png`, `tester_07_before_tap.png`, `tester_08_after_tap.png`, `tester_09_tap665.png`, `tester_10_closed.png`, `tester_11_closed.png`, `tester_12_sniper.png`, `tester_13_tap666.png`, `tester_14_expanded.png`, `tester_15_opacity_swipe.png`, `tester_16_slider.png`, `tester_17_moved.png`, `tester_18_tf5m.png`, `tester_19_tf_restore.png`, `tester_20_binomo.png`, `tester_21_btntry.png`, `tester_22_pre_chip.png`, `tester_23_chip_tap.png`, `tester_24_left.png`, `tester_25_locate.png`, `tester_26_tfmenu.png`, `tester_27_state.png`. La captura `tester_28_relaunch.png` se citaba en una versión previa de este informe pero **no llegó a generarse** (la sesión del probador terminó en `tester_27`); la cita se retira en lugar de dejar una referencia rota.

**Observaciones:** verificación por ausencia sobre 28 muestras temporales repartidas en ~40 minutos, incluyendo capturas tomadas en los segundos `:00s`–`:05s` de la vela (p. ej. `tester_02.png` en `:11s` con la vela previa cerrada poco antes). No se observó el texto en ninguna.

---

## T7 — Coherencia Filtro S/R ↔ Plan

**VEREDICTO: PASA**

**Evidencia — pares LITERALES leídos (filtro ↔ plan/estado en la MISMA captura):**

| Captura | 🛡️ Filtro S/R | 🎯 Plan / línea de estado | ¿Coherente? |
| :--- | :--- | :--- | :--- |
| `tester_01.png` | `Toque Directo en Soporte [CALL Óptimo]` | `Plan: Buscar retroceso leve a soporte para ejecutar CALL en la ventana :01s - :05s.` | SÍ (CALL↔CALL) |
| `tester_00_setup.png` | `Toque Directo en Resistencia [PUT Óptimo]` | `Compra vetada bajo resistencia: esperando rechazo o ruptura` | SÍ |
| `tester_02.png` | `Toque Directo en Resistencia [PUT Óptimo]` | `Compra vetada bajo resistencia: esperando rechazo o ruptura` | SÍ |
| `tester_03.png` | `Toque Directo en Resistencia [PUT Óptimo]` | `Compra vetada bajo resistencia: esperando rechazo o ruptura` | SÍ |
| `tester_12_sniper.png` | `Toque Directo en Resistencia [PUT Óptimo]` | `Compra vetada bajo resistencia: esperando rechazo o ruptura` | SÍ |
| `tester_18_tf5m.png` | `Toque Directo en Soporte [CALL Óptimo]` | `Compra vetada sobre soporte: esperando rebote o ruptura` | SÍ |
| `tester_10_closed.png` | `Toque Directo en Soporte [CALL Óptimo]` | `Compra vetada bajo resistencia: esperando rechazo o ruptura` | SÍ (veto ≠ gatillo PUT) |
| `tester_04.png` | `Toque Directo en Soporte [CALL Óptimo]` | `Compra vetada bajo resistencia: esperando rechazo o ruptura` | SÍ (veto ≠ gatillo PUT) |
| `tester_20_binomo.png` | `Toque Directo en Soporte [CALL Óptimo]` | `Compra vetada bajo resistencia: esperando rebote o ruptura` | SÍ (veto ≠ gatillo PUT) |

**Observaciones:**
- La combinación incoherente definida en T7 (**filtro `Soporte [CALL Óptimo]` + plan pidiendo gatillo `PUT`**, o la inversa) **NO se reprodujo en ninguna de las 28 capturas**.
- El texto `Plan:` nunca pidió la dirección opuesta a la del filtro: los dos casos con gatillo explícito fueron CALL↔CALL; el resto fueron estados de **veto** (`Compra vetada…`), que no constituyen un gatillo contrario.
- El aviso `⚠ CONFLICTO S/R↔PLAN` no apareció en mis capturas, pero el orquestador lo documentó en `clock_03.png` (filtro PUT vs anti-chop CALL) funcionando como detector correcto sobre una contradicción real del motor. Se acepta como evidencia de que el detector opera; queda anotado en RIESGOS PENDIENTES.

---

## T8 — Layout del panel HUD

**VEREDICTO: FALLA**

**Evidencia:**

**(a) Ancho del panel — correcto.** `adb shell dumpsys window windows` para la ventana del HUD:

```
mAttrs={(96,711)(624xwrap) gr=TOP START CENTER ty=APPLICATION_OVERLAY
  fl=NOT_FOCUSABLE LAYOUT_NO_LIMITS HARDWARE_ACCELERATED
mFrame=[96,761][720,1361]
```

`x=96` + ancho `624` = `720` → el panel encaja exactamente en el ancho de pantalla sin recorte de texto. El texto largo envuelve correctamente (`WS Activo (Crypto IDX): 641.867316 (353ms) | Headless` pasa a dos líneas en `tester_01.png`). Este subpunto PASA.

**(b) Botón de plegar — visible pero NO pulsable, y desaparece con cabecera larga.**

Con cabecera corta (`tester_00_setup.png`) el glifo `▼` es visible a la derecha del chip `👁️ 33%`, sobre el borde del panel (mapa de píxeles: columna `x≈663`, filas `y≈808–820`). Sin embargo **ningún tap lo activa**:

| Captura | Comando | Estado tras el tap (vía `/hud`) |
| :--- | :--- | :--- |
| `tester_13_tap666.png` | `input tap 666 813` | `{"success":true,"hudVisible":true,"hudCollapsed":false}` |
| `tester_14_expanded.png` | `input tap 663 813` | `{"success":true,"hudVisible":true,"hudCollapsed":false}` |
| `tester_21_btntry.png` | `input tap 650 892` / `input tap 651 872` | sin cambio de estado (respuesta vacía, servicio ya no respondía en ese punto) |
| `tester_22_pre_chip.png` → `tester_23_chip_tap.png` | `input tap 597 893` | sin pliegue observado |

En contraste, **el arrastre del panel SÍ funciona** (el mismo `setOnTouchListener`): tras `input swipe 400 815 400 1000 800` el `mAttrs` pasó de `(96,711)` a `(96,793)`. Por tanto el HUD es táctil y el fallo es específico del chip de plegado (target `32dp` en el extremo del `LinearLayout` horizontal), no un problema general de la ventana.

**(c) Con cabecera larga el chip se sale del borde derecho.** Con `[PAUSA FRANCOTIRADOR]` (submodo SNIPER) el chip `▼` **desaparece por completo** de la pantalla:

- `tester_12_sniper.png`: cabecera `[PAUSA FRANCOTIRADOR] Estrat: MT Rechazo · TREND 🟢 ⏱️ Vela [1m] :08s` — el chip de opacidad y el `▼` ya no se ven; la cabecera ocupa el ancho completo y el texto `Vela [1m] :08s` envuelve a una segunda línea en el borde.
- `tester_22_pre_chip.png` (crop `chip_tester_22_pre_chip.png`): solo se lee `33%` pegado al borde derecho, **sin** `👁️` y **sin** chip `▼`.
- `tester_17_moved.png` (crop `hdr_17.png`): cabecera `[PAUSA FRANCOTIRADOR] Estrat: MT Rechazo · TREND 🟢 ⏱️ Vela [5m] 3:47` — de nuevo sin chip de plegado visible.

**(d) Opacidad del fondo.** El drawable `bg_hud_panel` es `#E6070A14` (alfa 0xE6 ≈ 90%) con borde `#3338BDF8`, pero la vista tiene aplicado `hud_alpha` del usuario = **0.33** (`👁️ 33%`, confirmado en `hud_txt_opacity_value`). A esa opacidad el gráfico se lee **a través** del panel y contamina el texto: en `tester_00_setup.png` y `tester_10_closed.png` las etiquetas `3:20 p. m.` / `3:40 p. m.` / `4:00 p. m.` de Binomo se superponen literalmente sobre la línea `⚡ Micro-Velocidad: … %/s` y sobre `W: 0 | L: 0 | … | Total: 0`. Legible con esfuerzo, pero no "suficientemente opaco" a la configuración actual. El fondo base del drawable sí es opaco (90%); el problema es el multiplicador global de alpha del panel, que atenúa también el relleno.

**Observaciones adicionales (consecuencia del fallo (b)):** los taps sobre la zona de cabecera **atraviesan hacia Binomo** en lugar de consumirse en el HUD, produciendo efectos no deseados:

- `tester_08_after_tap.png`: un tap en `(665,813)` abrió el panel **`🤖 Agente TradeDraw AI`** (diálogo "¡Hola! Soy tu Agente Autónomo de TradeDraw…"), que hubo que cerrar con `✕` y `input keyevent 4`.
- `tester_13_tap666.png`: un tap en `(666,813)` abrió el **menú de timeframe de Binomo** (`1 Minuto (1m)` / `5 Minutos (5m)`), y el timeframe quedó cambiado a **5m** (`tester_18_tf5m.png`, `tester_17_moved.png`: `⏱️ Vela [5m] …`).
- `tester_19_tf_restore.png`: otro tap sobre la zona del HUD abrió la actividad **`MainActivity` de TradeDraw** (pantalla `INICIAR TRADE PRO (WEBSOCKET 0MS)`, `ACCESIBILIDAD: ACTIVA`, `INICIAR OVERLAY CON VISIÓN IA`).

---

## FALLOS DETECTADOS

1. **T8 — El botón de plegar del HUD (`hud_btn_collapse`) no responde a taps.** Reproducido **≥4 veces** con coordenadas distintas sobre el glifo (`666,813`; `663,813`; `658,813`; `672,813`; `650,892`; `597,893`): `/hud` siguió devolviendo `"hudCollapsed":false`. Evidencia: `tester_13_tap666.png`, `tester_14_expanded.png`, `btn_tester_07_before_tap.png`, `btn_tester_09.png`, `chip_tester_22_pre_chip.png`. Referencia de contraste: el **arrastre** del mismo panel sí actualiza `mAttrs` (`(96,711)` → `(96,793)`), luego el problema es específico del chip (32dp, extremo del `LinearLayout` horizontal), no de la ventana.

2. **T8 — Con cabecera larga (`[PAUSA FRANCOTIRADOR]`, submodo SNIPER) el chip de plegado desaparece de la pantalla.** Evidencia: `tester_12_sniper.png`, `tester_22_pre_chip.png`, `hdr_17.png`. La cabecera de línea 1 (`hud_header_line1`, `layout_width="wrap_content"`) desborda y empuja el chip fuera del borde derecho, dejándolo inalcanzable incluso si el listener funcionase.

3. **T8 — Los taps sobre la banda de cabecera del HUD se filtran al broker/app subyacente.** Consecuencias observadas: apertura del panel Agente IA (`tester_08_after_tap.png`), apertura del selector de timeframe de Binomo con cambio efectivo a 5m (`tester_13_tap666.png` → `tester_18_tf5m.png`), y apertura de `MainActivity` de TradeDraw (`tester_19_tf_restore.png`). Además de ser un fallo de layout/consumo de eventos, **altera el estado del entorno de trading** (timeframe del gráfico) durante una sesión, lo que puede invalidar el análisis de velas.

*(T5, T6a, T6b, T6c, T7 y el subpunto de ancho de T8 no generaron fallos.)*

---

## RIESGOS PENDIENTES

1. **`⚠ CONFLICTO S/R↔PLAN` puede aparecer en producción.** Documentado por el orquestador en `clock_03.png`: filtro S/R pidiendo `PUT` mientras el filtro anti-chop pedía `CALL`. El detector T7 lo señaló correctamente, pero la contradicción subyacente del motor persiste: si el aviso no bloquea el disparo, existe riesgo de entrar en la dirección equivocada. Pendiente: confirmar que la presencia del prefijo `⚠` **veta** la orden y no solo la etiqueta.

2. **Opacidad efectiva del panel a `hud_alpha=0.33`.** El texto del HUD se mezcla con etiquetas del gráfico de Binomo (`3:20 p. m.`, `3:40 p. m.`) sobre las líneas de Micro-Velocidad y de estadísticas. No es un fallo del drawable (que es 90% opaco) sino del multiplicador global. Riesgo de lectura errónea del signo/valor de la micro-velocidad en capturas rapidas. Pendiente: ¿el `alpha` debe aplicarse solo al fondo y no al texto?

3. **T3 no verificado en absoluto.** Nunca hubo operación abierta (`activeSignal: null`, `hasPendingTrade: false`, `signalsGenerated: 0` durante ~40 min). La línea de estado con `Strike / ITM / OTM / P&L / Hasta exp` sigue **sin validación visual**. Riesgo: incoherencia ITM↔signo de P&L no detectada.

4. **T6b (semántica de `Total`) y T4 (racha `—` sin tick fresco) no verificados con datos no triviales.** Ambas verificaciones requieren un estado (operaciones resueltas / feed degradado) que no se alcanzó ni se pudo forzar desde el puente HTTP.

5. **Estabilidad del puente HTTP y de ADB.** En dos momentos de la sesión el puente dejó de responder (`curl` devolvió vacío) pese a que el proceso `com.example.tradedraw` (PID 31873) seguía vivo; se recuperó con `adb forward --remove-all` + `adb forward tcp:8080 tcp:8080`. Al final de la sesión el dispositivo se desconectó de ADB (`device 'UGU8KFUKM7E6CE8D' not found`, `adb devices` vacío). **No se pudo determinar si hubo crash de la app**: el último `logcat` filtrado por `FATAL|AndroidRuntime|TradeDraw` (800 líneas) **no devolvió ninguna coincidencia**, pero la desconexión posterior impide descartar un fallo del proceso o del servicio de overlay. Riesgo abierto, requiere re-verificación con `adb logcat` en la próxima sesión.

6. **Sin crashes observados durante la parte con dispositivo conectado.** `adb -s UGU8KFUKM7E6CE8D logcat -d -t 800 | Select-String "FATAL|AndroidRuntime|tradedraw|TradeDraw"` → **sin coincidencias**. No hay evidencia de excepción no capturada mientras la sesión fue estable.

---

## ANEXO — Artefactos de evidencia

- Capturas principales: `tester_00_setup.png`, `tester_01.png` … `tester_04.png` (serie temporal 15:19:25 → 15:20:44, ~25 s de separación).
- Capturas auxiliares: `tester_05_collapsed_http.png` (plegado por HTTP — el panel colapsa correctamente a la línea de cabecera + slider, confirmando que la lógica `applyHudCollapsed` funciona; lo que falla es solo el disparador táctil), `tester_08_after_tap.png`, `tester_12_sniper.png`, `tester_13_tap666.png`, `tester_17_moved.png`, `tester_18_tf5m.png`, `tester_19_tf_restore.png`, `tester_20_binomo.png`, `tester_22_pre_chip.png`.
- Recortes ampliados (texto literal): `crop_00.png`, `crop_00b.png`, `crop_00c.png`, `crop_00_hdr.png`, `zoom_01.png` … `zoom_04.png`, `zoom_10.png`, `zoom_tester_12_sniper.png`, `zoom_13.png`, `zoom_17.png`, `btn_*.png`, `chip_*.png`, `tmr_01.png` … `tmr_04.png`.
- Estado del motor: `status_log.txt` (`/status` volcado en cada captura de la serie).
- Nota: `tester_05_collapsed_http.png` **prueba que el plegado funciona por vía programática** (`/hud?collapse=true` → `"hudCollapsed":true` en la respuesta y panel colapsado en pantalla), lo que aísla el fallo T8-1 al disparador táctil del chip.
