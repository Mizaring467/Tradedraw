# Cuestionario de diagnóstico: por qué el agente de TradeDraw dejó de operar

**Purpose:** Identificar cuál de los vetos observados en el log de producción está apagando al bot, y fijar los parámetros exactos con los que debe volver a operar (o decidir que el bot *está* funcionando correctamente y el problema es de expectativa). La decisión que cuelga de esto: qué línea de código tocamos y con qué valores, sin volver a quemar capital en pruebas ciegas.

**From:** el operador de TradeDraw, **To:** el agente de código (DeepSeek Harness) que mantiene `TradingEngine.kt` / `AdaptiveLearningEngine.kt`, **How your answers will be used:** se convierten directamente en el parche de parámetros y en el plan de verificación antes de volver a `main`.

## Context

`TradeDraw` corre en modo **100% Headless** (WebSocket + `AccessibilityService`, sin captura de pantalla). La evidencia disponible es una corrida real del `2026-09-13` entre `18:12:55` y `18:14:13` (≈78 s, activo Crypto IDX) registrada en `logcat_app.txt`. En esa ventana **no se completó ninguna orden nueva**: el pipeline se atascó en una cascada de vetos distintos, y cada uno reporta un motivo diferente. El motor encadena señales del `SyntheticCandleEngine` → `executeHeadlessTrade()` → `RiskManager.canExecuteTrade()` → `AdaptiveLearningEngine`, y cualquiera de esas cuatro capas puede abortar la entrada. El log solo muestra la capa que habló **última**, no la que decidió primero, así que la causa raíz sigue siendo ambigua. Necesitamos desambiguarla antes de tocar umbrales.

**Cronología reconstruida del log (esto es lo que ya sabemos, no hay que volver a explicarlo):**

| Timestamp | Capa | Mensaje |
| :--- | :--- | :--- |
| 18:12:55.496 | `TradingEngine:1357` | Se ejecutó `executeHeadlessTrade` (feedback háptico) → **una orden sí se envió** |
| 18:12:55 → 18:13:57 | `RiskManager` | `Headless bloqueado por riesgo: Operación abierta en curso (0s … 62s)` durante **62 segundos** |
| 18:13:57.766 | `TradingEngine:1417` | `checkHeadlessTradeResolution` resolvió el trade |
| 18:13:58 → 18:14:05 | `TradingEngine` | `Headless bloqueado por Cooldown post-resolución (0s…7s < 8s)` |
| 18:14:06 → 18:14:12 | `AdaptiveLearningEngine` | `Anti-patrón perdedor bloqueado (99% similitud con pérdida previa en HEADLESS_WS)` — repetido **10 veces en 7 s** |

**Sospecha técnica principal (a confirmar):** `calculateSignatureSimilarity()` otorga `0.20` por misma acción + `0.20` por misma tendencia + `0.30` por proximidad S/R + `0.15` por régimen lateral + `0.15` por impulsos idénticos. En Headless con Crypto IDX, `distanceToSupportRatio` converge a `0.50` estable y la tendencia suele quedar en `SIDEWAYS`, así que **cualquier** candidato BUY en el mismo régimen suma automáticamente ~`0.65–0.80`, y basta que los impulsos coincidan para cruzar el umbral de veto `0.82`. Es decir: la firma puede ser demasiado **laxa** y bloquear sistemáticamente, o el umbral `0.82` demasiado **bajo**. Necesitamos que el operador decida cuál de las dos hipótesis es la correcta.

## How to answer

No hay prisa: respóndelo con el celular en mano y el APK reinstalado, o entre sesiones de prueba. **No hace falta una respuesta por pregunta**: las marcadas con 🔴 son las que desbloquean el parche; el resto afina los valores. Si algo no lo sabes, escribe `NO SÉ` y sigue — un "no sé" es información útil, una respuesta inventada nos cuesta otra sesión de pérdidas. Tiempo estimado: 15–20 minutos. Responde **en línea debajo de cada `>`**.

---

## 1. Contexto de la corrida (🔴 bloqueante)

### ¿En qué modo exacto estaba el bot durante la corrida del 13-sep?

_Why this matters: `AUTONOMOUS` es Headless puro; `SEMIAUTOMATIC` solo alerta y jamás llama a `executeHeadlessTrade`. Cambia por completo el diagnóstico._

- [ ] `AUTONOMOUS`
- [ ] `SEMIAUTOMATIC`
- [ ] `DISABLED`

> 

### ¿Estaba en submodo `CONSERVATIVE` o `YOLO`?

_Why this matters: en `YOLO` el cooldown post-resolución baja a 8 s y la ventana de timing se ensancha a `:55–:12`; en `CONSERVATIVE` la ventana es `:57–:04`. El log muestra cooldown de 8 s, lo que sugiere YOLO, pero hay que confirmarlo._

- [ ] `CONSERVATIVE`
- [ ] `YOLO`

> 

### ¿La corrida del 13-sep fue la única sesión, o el bot llevaba horas sin operar antes?

_Why this matters: el archivo de estado (`SharedPreferences`) persiste hasta **50 firmas de pérdidas** entre reinicios. Si el bot llevaba horas acumulando pérdidas antes de esta ventana, el bloqueo por autoaprendizaje es un efecto acumulado, no un fallo de la corrida._

> 

### ¿Aproximadamente cuánto capital se movió en la corrida y cuál fue el resultado (W/L/Tie)?

> 

---

## 2. El veto por autoaprendizaje a 99% de similitud (🔴 bloqueante)

### Cuando el bot se bloqueó 10 veces seguidas con "99% similitud", ¿había perdido operaciones reales justo antes?

_Why this matters: si **no** hubo pérdidas previas, la firma está corrupta o el umbral es demasiado laxo y hay que subir `0.82`. Si **sí** hubo pérdidas reales y todas fueron BUY en el mismo contexto, el bloqueo es legítimo y el bug está en la estrategia, no en el filtro._

> 

### ¿Recuerdas cuántas derrotas seguidas llevaba el bot cuando empezó a bloquear?

_Why this matters: distingue "el bot aprendió de 1 pérdida y generalizó de más" de "el bot acumuló 20 pérdidas reales y se autoprotegió bien"._

> 

### ¿Tenías la Martingala activada? ¿En qué nivel (`M0`/`M1`/`M2`) estaba cuando se congeló?

_Why this matters: con `martingaleEnabled` y `currentLossStreak > maxMartingaleLevel` entra una "Pausa Anti-Tilt" que en el log **no aparece**, lo que sugiere que la racha era baja. Confirmarlo descarta esa rama._

> 

### ¿Quieres que el veto por autoaprendizaje sea bloqueante o solo un factor de penalización?

_Why this matters: hoy un anti-patrón al `≥0.82` **aborta la entrada** (`AdaptiveDecision.Block`). Convertirlo en penalización de confianza dejaría al bot operar pero con menos tamaño. Es una decisión de riesgo, no técnica._

- [ ] Mantener bloqueante (más seguro, bot puede congelarse)
- [ ] Degradar a penalización de confianza (opera siempre, arriesga más)
- [ ] Bloqueante solo si el stop-loss por racha ya está cerca

> 

---

## 3. El cooldown de 62 segundos (🔴 bloqueante)

### ¿La operación de las 18:12:55 llegó a abrirse en Binomo, o el clic no registró?

_Why this matters: el log dice `Operación abierta en curso (62s)`, o sea que `RiskManager` **creyó** que había trade. Si Binomo nunca abrió la orden, el bot quedó bloqueado 62 s esperando un fantasma — ese es un bug de settlement, no de estrategia._

- [ ] Sí se abrió y vi el trade en pantalla
- [ ] No se abrió, el clic no hizo nada
- [ ] No estoy seguro

> 

### ¿Cuánto tardó Binomo en mostrar el resultado de esa operación (Win/Loss)?

_Why this matters: `MAX_PENDING_TRADE_TIMEOUT_SEC` y el cooldown de 8 s asumen que la resolución llega a los ~60 s. Si tu broker tarda más, el bot se bloquea solo. Si tardó exactamente 60 s, el comportamiento es correcto._

> 

### ¿El bot mostró el toast de resolución (`🎉 GANADA` / `⚠️ PERDIDA` / `⚪ EMPATE`)?

_Why this matters: el log muestra la llamada a `emitHapticAndAudioFeedback` desde `checkHeadlessTradeResolution`, así que **algo** se resolvió — pero no sabemos con qué veredicto. La vibración/audio que sentiste confirma qué rama tomó._

- [ ] Sí, ganada
- [ ] Sí, perdida
- [ ] Sí, empate/cancelada
- [ ] No hubo toast

> 

### ¿Te parece correcto el cooldown de 8 s post-resolución en modo YOLO, o lo quieres en 0?

_Why this matters: en un ciclo de vela de 60 s, 8 s de cooldown significan perder la ventana `:58–:03` de la vela inmediata siguiente. Si el objetivo es operar cada vela, hay que bajarlo a 0–2 s._

> 

---

## 4. Los filtros de timing y anti-choppy que añadimos (🔴 bloqueante)

### ¿Durante la corrida viste que el bot se quedara callado en la ventana `:58–:03`?

_Why this matters: en el log **no aparece ni una sola línea** de `fuera de ventana sniper` ni de `Filtro Anti-Choppy`. Eso significa que el bot sí producía señales en la ventana correcta y el problema está aguas abajo. Si tú observaste lo contrario en pantalla, la lectura del log cambia._

> 

### ¿El activo operado era Crypto IDX (Z-CRY/IDX) y el mercado se veía plano/lateral?

_Why this matters: el filtro anti-choppy de `SyntheticCandleEngine` compara el rango reciente contra el promedio de 20 velas. En un activo sintético plano, ese filtro puede dispararse constantemente y suprimir toda señal antes de que llegue al log de veto._

- [ ] Crypto IDX, plano
- [ ] Crypto IDX, con movimiento
- [ ] Otro activo (¿cuál?)

> 

### ¿Quieres que el filtro anti-choppy siga activo, o prefieres desactivarlo y confiar solo en el timing?

_Why this matters: es el filtro que introdujimos en el commit `1f1cae9`. Si sospechas que está matando señales legítimas, se puede hacer visible (log de cada supresión) antes de decidir si se queda._

- [ ] Mantener activo (más seguro)
- [ ] Desactivar y confiar solo en timing `:58–:03`
- [ ] Mantener pero registrar cada supresión en el HUD para poder medirlo

> 

---

## 5. Cómo probamos el fix (🔴 bloqueante)

### ¿Cuál es la métrica que te hace decir "el fix funcionó"?

_Why this matters: "opera más" no es verificable. Necesitamos un número acordado **antes** de la prueba para no repetir la discusión._

- [ ] Al menos N órdenes ejecutadas en M minutos (¿N y M?)
- [ ] Cero bloqueos por "99% similitud" repetidos
- [ ] Winrate ≥ X% en una muestra de N trades
- [ ] Todas las entradas caen dentro de `:58–:03`

> 

### ¿Cuánto tiempo y con cuánto capital estás dispuesto a probar?

_Why this matters: define si hacemos una prueba de humo de 5 minutos en modo `SEMIAUTOMATIC` (cero riesgo, valida que las señales salen) o una corrida real en `AUTONOMOUS`._

> 

### ¿Autorizas que durante la prueba el bot escriba un log explícito por CADA veto (capa + motivo + segundo), para no volver a adivinar?

_Why this matters: hoy el log solo muestra la última capa que habló. Un log por capa convierte cualquier corrida futura en diagnóstico inmediato._

- [ ] Sí
- [ ] No, prefiero no llenar el log

> 

---

## 6. Restricciones y preferencias

### ¿Hay algún umbral que NO quieras que toquemos bajo ninguna circunstancia?

_Why this matters: el bot ya tiene filtros anti-suicidio (prohibido vender sobre soporte, etc.) que has pedido explícitamente en el pasado. Queremos saber cuáles son intocables para no romper tu criterio de riesgo._

> 

### ¿El modo objetivo de producción es Headless (WebSocket) o visión por pantalla?

_Why this matters: toda la evidencia disponible es Headless. Si tu objetivo real es el modo visión, estamos diagnosticando el motor equivocado._

- [ ] Headless (WebSocket)
- [ ] Visión por pantalla
- [ ] Ambos

> 

### ¿Hay algo del comportamiento en vivo que no hayamos capturado en estos logs?

> 

---

## Anything else?

¿Algo que no te hayamos preguntado y que deberíamos saber antes de tocar una sola línea de `TradingEngine.kt`? Cualquier síntoma raro, comportamiento intermitente o detalle del HUD que hayas visto durante la corrida vale más que cualquier hipótesis nuestra.

> 
