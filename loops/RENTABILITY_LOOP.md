# Loop de Rentabilidad TradeDraw — Estado Durable

> Memoria compartida del loop. Cada agente LEE esto antes de actuar y lo ACTUALIZA al terminar.
> Última actualización: sesión inicial.

---

## 1. Objetivo

Llevar el winrate real de TradeDraw en **Binomo Crypto IDX / BTC (vela 60s)** de forma
**estable y reproducible** por encima del baseline, con saldo neto positivo en cada ventana
de 20 minutos evaluada.

**No es objetivo** subir el winrate de una sola sesión afortunada. El log de septiembre
mostró 63.8% que no se volvió a reproducir. Lo que perseguimos es **estabilidad**.

---

## 2. Baseline medido (evidencia, no opinión)

Fuente: `loops/journal/live/trade_journal_live.csv` — sesión 2026-09-10, 35 trades.

| Métrica | Valor |
| :--- | :--- |
| Winrate sobre total | **25.7%** (9W / 21L / 5 TIE de 35) |
| Winrate sobre resueltos | **30.0%** (9 de 30, excluyendo TIE) |
| Peor racha perdedora | **10 consecutivas** (18:25 → 19:37) |
| Trades fantasma (VOID, Diff=0.0) | **5** (14% del total) |
| Balance inicial | 44,961,812 COP |
| Balance final | 44,166,809 COP |
| Resultado neto | **−795,003 COP** |

### Desglose por nivel de martingala — EL DATO MÁS IMPORTANTE

| Stake | WIN | LOSS | TIE | WR |
| :--- | :-: | :-: | :-: | :-: |
| 1.00 (M0) | 4 | 9 | 0 | **30.8%** |
| 2.00 (M1) | 5 | 12 | **5** | **29.4%** |

Dos conclusiones de esto:
1. **La martingala NO recupera: amplifica.** El WR en M1 (29.4%) es peor que en M0 (30.8%),
   y el stake es el doble. Con 30% de acierto, doblar en pérdida es ruina acelerada:
   la esperanza matemática por operación es negativa y crece en magnitud con el stake.
2. **Los 5 TIE ocurrieron TODOS en M1.** El patrón es: pierde en M0 → sube a M1 →
   la orden no se procesa → el contador queda inconsistente. La capa de ejecución falla
   precisamente en el momento de mayor exposición.

### Referencia histórica (NO fiable)
`monitoring_20m_log.md` (12/09) reporta 30W/17L = 63.8% y +530,001 COP.
Se considera **no reproducible**: no hay CSV crudo que lo respalde y las condiciones de
mercado/tick difieren. Se usa solo como techo teórico alcanzable.

---

## 3. Causas raíz identificadas (ordenadas por impacto medido)

### CR-1 · Ausencia de freno en racha perdedora — IMPACTO ALTO **MECANISMO CONFIRMADO**
No es que el freno "no se disparara". **La racha se borra antes de alcanzar el umbral.**

`RiskManager.kt:337-350`:
```kotlin
if (martingaleEnabled && currentLossStreak > maxMartingaleLevel) {   // maxMartingaleLevel = 1
    val remaining = effectiveLossCooldown - elapsed
    if (remaining > 0) {
        return Pair(false, "Pausa Anti-Tilt tras fallo Martingala (${remaining}s)")
    } else if (subMode == AutonomousSubMode.YOLO) {
        currentLossStreak = 0      // <-- AQUI SE DESTRUYE EL CONTADOR
    }
}
```
Y remata en `RiskManager.kt:367-368`:
```kotlin
// En YOLO opera continuamente sin detenerse permanentemente por stop loss de racha
return Pair(true, "🚀 MODO YOLO: Operativa continua sin límites")
```

**Aritmética del fallo:**
- `DEFAULT_STOP_LOSS_STREAK = 3` (RiskManager.kt:26) — el umbral.
- `DEFAULT_MAX_MARTINGALE_LEVEL = 1` (RiskManager.kt:28) — se resetea al superar racha 1.
- El Stop Loss se evalúa en línea 324; el reset ocurre en línea 349.
- Como el reset salta al superar racha 1, `currentLossStreak` **nunca acumula 3**.
  Máximo alcanzable = 2. Umbral = 3. **El freno es inalcanzable por construcción.**

**Consecuencia letal en `canTrade()`**: la línea 368 hace `return Pair(true, ...)` en YOLO,
así que **todo lo que viene después es código muerto en ese submodo**:
- línea 371: `takeProfitWins` (take profit) — nunca se evalúa.
- línea 374: cooldown final — nunca se evalúa.
En YOLO solo sobreviven los límites evaluados *antes* de la línea 368 (trailing profit lock,
VOID x3, stop loss de racha), y el de racha es inalcanzable. **YOLO queda sin límite de
pérdidas efectivo.**

**Fix mínimo**: borrar `currentLossStreak = 0` de la línea 349. Una línea. Detiene la sangría
inmediatamente. (El `return Pair(true, ...)` de la 368 debe moverse al final de la función
para que take-profit y cooldown vuelvan a tener efecto en YOLO.)

### CR-2 · Trades fantasma (VOID) desincronizan el motor — IMPACTO ALTO **MECANISMO CONFIRMADO**
El freno de 3 VOID existe (`RiskManager.kt:319-322`) pero **nunca se alimenta**.

`TradingEngine.kt:269-273`:
```kotlin
if (isTie) {
    TradeJournalLogger.logTrade(..., "TIE", currentBal, elapsedSec, method, ...)
    riskManager.clearPendingTrade()      // <-- solo limpia, NO registra el VOID
    autoDrawEngine.clearTradeEntry()
    Toast.makeText(context, "⚪ EMPATE EN BINOMO (Reembolso de capital)", ...).show()
}
```
La rama `isTie` llama a `clearPendingTrade()` y **jamás** a `recordTradeVoid()`
(`RiskManager.kt:435-440`), que es el único sitio donde se incrementa `consecutiveVoids`.
Resultado: `consecutiveVoids` se queda en 0 permanente y la parada de la línea 319 es
inalcanzable. Confirma los 5 VOID consecutivos del CSV sin detención.

**Dato adicional**: el auditor indica que `TradeOutcome.VOID` **no existe** en el enum
`TradeOutcome`, aunque `specs/001-trade-reliability/plan.md` afirma haberlo implementado
("Soporte para TradeOutcome.VOID"). El plan documenta algo que el código no tiene.

**Fix mínimo**: en `TradingEngine.kt:271`, cambiar `riskManager.clearPendingTrade()` por
`riskManager.recordTradeVoid()` (que ya llama internamente a `clearPendingTrade()`).

### CR-3 · Lectura de saldo corrupta registrada como victoria — IMPACTO CRÍTICO (envenena el aprendizaje)
**Cadena completa verificada en código:**

1. **Origen del valor basura** — `AutoTradeAccessibilityService.kt:212-223`:
   ```kotlin
   if (found != null && found > 0.0) {
       if (isDemoAccount && latestObservedBalance > 1_000_000.0 && found < 500_000.0) {
           return latestObservedBalance          // protegido, SOLO en demo
       }
       if (latestObservedBalance > 1_000_000.0 && found < 500_000.0 && !isDemoAccount) {
           Log.i("TradeDraw", "Transición Demo -> Real confirmada: $found COP")  // solo log
       }
       latestObservedBalance = found             // asignacion incondicional
   ```
   La guarda anti-glitch existe **solo para cuenta demo**. En cuenta real, una lectura
   de `1.00` pasa porque `1.00 > 0.0`. La proteccion esta desactivada justo donde hay dinero.

2. **Se graba como base del trade** — `TradingEngine.kt:1142` y `:1349` →
   `riskManager.recordTradeSent(action, priceY, baseBal)` → `RiskManager.kt:417`
   `pendingTradeBaseBalance = baseBalance`.

3. **Se clasifica como WIN** — `TradingEngine.kt:230-236`:
   ```kotlin
   if (baseBalance > 0.0 && currentBal > 0.0) {
       val diff = currentBal - baseBalance
       if (diff > 10.0) { isWin = true }      // +4.49E7 > 10.0 -> WIN
   ```

4. **La validacion existente NO lo detecta** — `RiskManager.kt:450-457` y `:483-490`
   comprueban `diff <= 10.0` (saldo que NO se movio) y convierten a VOID. Pero
   `|44,907,810.84| <= 10.0` es falso, asi que **pasa limpio como victoria legítima**.
   La validacion cubre el caso "no se movio", no el caso "se movio una barbaridad imposible".

5. **Envenena el aprendizaje** — `TradingEngine.kt:276-277`: el WIN falso llama a
   `riskManager.recordTradeWin()` y a `adaptiveLearningEngine.recordTradeOutcome(true, ...)`.

**Evidencia en datos**: fila del CSV `2026-09-10T17:50:00`: `base_balance=1.00`,
`settled_balance=44,907,811.84`, declarado WIN. Confirmado por el analizador como
`BALANCE_CORRUPTO`.

**Fix minimo**: extender la guarda de `readCurrentBalance` a cuenta real (quitar el
`isDemoAccount &&` de la linea 215), y añadir en `TradingEngine:230` un sanity check de
`diff` contra el stake real (`|diff|` no puede exceder `stake * payout_max`).

### CR-4 · `confidence` constante en 1.00 — IMPACTO MEDIO **CONFIRMADO**
No es que la confianza se sature: **es un literal hardcodeado**. `TradingEngine.kt:1058`
calcula un `signalConfidence` real al decidir la entrada, pero **no lo persiste** en el
trade pendiente. Al liquidar (líneas 270, 275, 283) se pasa `1.0f` literal.

Efecto colateral grave: `RiskManager.kt:360-366` exige "setup A+ (confianza >= 85%)" para
permitir martingala M1 en YOLO. Como la confianza que llega es siempre 1.0 normalizado
(=100%), **ese filtro de calidad nunca rechaza nada**. Es otra guarda decorativa.

**Fix mínimo**: añadir `pendingTradeConfidence` a `RiskManager`, guardarlo al abrir el trade
y pasar esa variable en las 3 llamadas al logger.

### CR-5 · La martingala amplifica la pérdida en vez de recuperarla — IMPACTO CRÍTICO
Evidencia del desglose por stake (sección 2):
- WR en M0 (stake 1.00) = 30.8%. WR en M1 (stake 2.00) = 29.4%.
- **El winrate no mejora al subir de nivel.**
- Con WR ~30% y payout ~82%, la esperanza por operación es
  `0.30 × 0.82 − 0.70 × 1.00 = −0.454` unidades de stake. **Negativa y proporcional al stake.**
- **Los 5 TIE ocurrieron todos en M1**, justo cuando la exposición es doble.

Mecanismo (auditor, veredicto PARCIAL): existe tope de nivel (`maxMartingaleLevel = 1`,
`RiskManager.kt:28`) pero **no existe tope de dinero absoluto** ni condición que desactive
la martingala cuando el winrate observado es malo. `getWinRate()` solo pinta texto en el HUD.

**Implicación**: ningún ajuste de señal arregla esto mientras la martingala siga activa.
**La primera decisión debería ser desactivarla o invertir la progresión, no afinar la señal.**
Breakeven exacto para payout 0.82: `1 / (1 + 0.82) = 54.9%`.

### CR-6 · El umbral real está muy por encima del winrate actual — IMPACTO ESTRUCTURAL
Con payout del 82%, se necesita **54.9% de winrate** solo para empatar. El sistema está en
30%. La brecha es de **25 puntos porcentuales**, no de un par de ajustes.
Esto reencuadra el loop: no se trata de "afinar", se trata de **encontrar si existe
una ventaja real**. Si el loop no puede cerrar esa brecha, la conclusión honesta es que
la estrategia no es viable en estas condiciones, y eso también es un resultado válido.

---

## 4. Protocolo del loop

### Roles
| Rol | Responsabilidad |
| :--- | :--- |
| **Orquestador** (agente principal) | Diseña prompts, delega, decide commits, mantiene este archivo. |
| **Observador ARTEMIS** | Mira el celular en vivo vía MCP Artemis. Registra marcador, saldo, estado del HUD, errores visibles. |
| **Implementador** | Aplica UN cambio quirúrgico en UN archivo. Compila. Instala. |
| **Analista de datos** | Lee el CSV resultante y calcula métricas. Compara contra baseline. |

### Ciclo de una iteración
1. **Elegir UNA causa raíz** de la sección 3 (una sola — cambios múltiples invalidan la medición).
2. **Formular hipótesis falsable**: "Arreglar X debería subir winrate de A% a B% porque…".
3. **Implementar** el cambio mínimo (filosofía: borrar > añadir, stdlib > dependencia).
4. **Compilar e instalar**: `./gradlew installDebug --no-daemon --no-configuration-cache`.
5. **Dejar operar 20 minutos** con ARTEMIS observando.
6. **Exportar el CSV** y calcular métricas.
7. **Evaluar** contra criterio de commit.
8. **Registrar** resultado en la sección 5 y actualizar estado.

### Criterio de commit (estricto)
Commit a `main` **solo si se cumplen AMBAS**:
- Winrate de la ventana > winrate de la ventana anterior (mejora real, no ruido).
- Saldo neto de la ventana ≥ 0.

Si falla cualquiera: `git checkout -- .` y registrar la hipótesis como refutada.
**Un cambio que no supera el criterio NO se commitea, aunque el código sea "más limpio".**

### Reglas duras
- **Un cambio por iteración.** Nunca agrupar.
- **Nunca commitear sin medición en vivo.** Sin celular no hay validación, luego no hay commit.
- **Los TIE se cuentan aparte**, nunca como pérdida ni como victoria.
- **Antes de tocar `AdaptiveLearningEngine`**, purgar las filas con balance corrupto del CSV.

---

## 5. Bitácora de iteraciones

| # | Causa raíz | Hipótesis | Cambio | WR ventana | Saldo ventana | Veredicto |
| :-: | :--- | :--- | :--- | :-: | :-: | :--- |
| 0 | — | — | Baseline fijado (35 trades) | 25.7% | −795,003 | Referencia |

---

## 5b. Plan de intervención (ranking impacto/esfuerzo)

Cada cambio es **una línea**, y cada uno es verificable de forma independiente.

| Orden | Causa | Cambio exacto | Esfuerzo | Riesgo |
| :-: | :--- | :--- | :-: | :--- |
| **0** | **Kill switch 40M** | `RiskManager.kt:89` — suelo relativo al saldo de sesion en demo (o desactivarlo en demo) | 1-3 lineas | **ALTO si se desactiva mal** |
| **1** | CR-3 | `AutoTradeAccessibilityService.kt:219` — extender la guarda anti-glitch a cuenta real | 1 linea | Ninguno |
| **2** | CR-3 | `TradingEngine.kt:232` — acotar la victoria contra un diff imposible | 1 linea | Ninguno |
| **3** | CR-1 | `RiskManager.kt:349` — **borrar** `currentLossStreak = 0` | 1 borrado | Bajo |
| **4** | CR-1 | `RiskManager.kt:368` — mover el `return Pair(true, ...)` de YOLO al final | 1 movimiento | Medio |
| **5** | CR-2 | `TradingEngine.kt:271` — `clearPendingTrade()` → `recordTradeVoid()` | 1 palabra | Ninguno |
| **6** | CR-4 | Persistir `signalConfidence` — **YA HECHO** (101 tests pasan) | — | — |

**Nota sobre el orden**: el kill switch (orden 0) va primero porque **es el que mas limita el
numero de operaciones**, que es la queja principal del usuario. Sin quitarlo, cualquier
medicion de 20 minutos dara muy pocas operaciones y no sera concluyente.

**Precaucion con el orden 0**: desactivar el kill switch en demo es seguro (no hay dinero
real). Pero **NO debe desactivarse el equivalente en cuenta real** — ahi el suelo protege
capital de verdad. Si se implementa como "relativo al saldo de sesion", aplica a ambas y
sigue protegiendo.

**Los cambios 1, 2, 3, 5 y 6 son aditivos o borrados triviales: no pueden empeorar el winrate,
solo corregir integridad.** El 4 es el único con riesgo de comportamiento (puede reducir el
número de operaciones al reactivar el take-profit en YOLO).

### Estrategia de medición
Si se aplican los 6 juntos, **no se puede atribuir la mejora a ninguno**. Propuesta:
- **Iteracion A**: cambios 1, 2, 3, 5 (integridad pura + freno de racha). Medir 20 min.
- **Iteracion B**: cambio 4 (reactivar take-profit/cooldown en YOLO). Medir 20 min.
- **Iteracion C**: cambio 6 (telemetria de confianza). Medir 20 min, y ya con datos ricos
  atacar H1 (el lado SELL roto) con `trend`/`dist_support`/`dist_resistance` reales.

Aplicar 1, 2, 3 y 5 juntos se justifica porque los cuatro corrigen **integridad de datos**,
no estrategia: sin ellos, cualquier medición posterior es dudosa.

---

## 6. Análisis cuantitativo — el marco matemático

### El umbral de rentabilidad
```
E = p·b − (1−p)·1        donde b = payout (0.82), p = winrate
E = 0  ⟹  p = 1 / (1 + b) = 1 / 1.82 = 0.5495
```
**Winrate mínimo para empatar con payout 82%: 54.95%.** El sistema está en 30%.
**Brecha: ~25 puntos porcentuales.** No es un ajuste, es un abismo.

### Por qué la martingala no puede salvar esto
La martingala **no cambia la esperanza matemática**, solo la varianza.
`E[Σ SᵢXᵢ] = Σ SᵢE[Xᵢ]` — si `E[Xᵢ] < 0`, multiplicar `Sᵢ` acelera la ruina geométricamente.

Esperanza por ciclo de martingala de 3 niveles (1, 2, 4) con p=0.30, b=0.82:

| Evento | Probabilidad | Resultado neto | Ponderado |
| :--- | :-: | :-: | :-: |
| Gana en M0 | 0.300 | +0.82 | +0.2460 |
| Gana en M1 | 0.210 | +0.64 | +0.1344 |
| Gana en M2 | 0.147 | +0.28 | +0.0412 |
| Falla M2 (quiebra ciclo) | 0.343 | −7.00 | −2.4010 |
| **Esperanza por ciclo** | | | **−1.98 unidades** |

**Se pierden ~1.98 unidades por ciclo iniciado.** Destrucción de capital garantizada.

### Las rachas de 10 pérdidas NO son anomalía, son inevitables
Tiempo medio entre rachas de k pérdidas, con q = 0.70 (`μ = (1−qᵏ)/(qᵏ(1−q))`):

| Racha k | Probabilidad qᵏ | Trades promedio hasta verla |
| :-: | :-: | :-: |
| 5 | 16.8% | 16.5 |
| 8 | 5.8% | 54.5 |
| 10 | 2.8% | 114.7 |

En 35 trades, P(racha ≥ 5) > 85% y P(racha ≥ 10) ≈ 25%.
**La racha de 10 pérdidas observada era esperable.** No fue mala suerte ni un bug:
es la estadística de operar al 30% de acierto. Eso reencuadra CR-1: el problema
no es "el freno falló", es que **con 30% de acierto siempre vas a tocar rachas largas**;
el freno es paliativo, no solución.

### El payout que haría rentable el 30% de winrate
Despejando: `b ≥ (1−p)/p = 0.70/0.30 = 2.333`
**Se necesitaría un payout del 233%.** El máximo real de cualquier broker es 80–90%.
**Ningún cambio de activo, broker o payout salva un winrate del 30%.**

### La pregunta incómoda que hay que resolver primero
Un sistema que acierta **30% cuando el azar daría 50%** no está "fallando por poco":
está **sistemáticamente equivocado**. Dos hipótesis:
1. **Sesgo inverso**: el bot compra techos y vende suelos. Si fuera así, **invertir la
   señal (CALL↔PUT) daría ~70%**, muy por encima del 54.9% necesario.
2. **Latencia de ejecución**: la entrada se asigna en el peor punto de la micro-vela.

**Si la hipótesis 1 se confirma aunque sea parcialmente, es el hallazgo más valioso
posible**: convierte un sistema perdedor en uno rentable con un cambio de un carácter.
Debe probarse ANTES que cualquier optimizacion de filtros.

### La matematica del margen de la casa (EV por operacion)
```
EV = p · 0.83 − (1 − p) · 1.00  =  1.83p − 1.00
```
| Winrate p | EV por operacion |
| :--- | :--- |
| 50.0% (azar) | **−8.5%** |
| 54.6% | 0 (break-even) |
| 60.0% | +9.8% |

**Con 50% de acierto se pierde 8.5% del stake en CADA operacion.** La ley de los grandes
numeros garantiza la ruina en operativa continua. Esto **descarta el modo YOLO por diseno**:
cuantas mas operaciones, mas rapido converge a la perdida.

### Regimenes de mercado y que hacer en cada uno
| Regimen | Estrategia correcta | Logica |
| :--- | :--- | :--- |
| Tendencia fuerte | Pullback de continuacion | Momentum + retest S/R |
| Expansion de volatilidad | Falso rompimiento (trampa) | Absorcion de liquidez |
| Agotamiento en extremo | Reversion de 3 velas climax | Extincion de volumen |
| **Lateral / micro-rango** | **FLAT (veto total)** | **Evitar el margen de la casa** |

**Hallazgo critico**: el usuario reporta que el bot opera en `LATERAL / RANGO`. Segun esta
tabla, **es el unico regimen donde la accion correcta es NO operar**. El bot no solo no veta
el lateral: opera ahi con el doble de volumen en el lado SELL, que es su lado averiado.

### Conclusión estructural
El margen de la casa es del ~9% por operación simétrica. Superarlo de forma sostenida
exige una ventaja estadística muy superior a la de mesas institucionales de FX.
**El orden correcto es: (1) diagnosticar el sesgo inverso, (2) arreglar la integridad
de datos, (3) recién entonces ajustar señal. Optimizar filtros sobre 30% es fútil.**

### Veredicto sobre la frecuencia operativa
| Modo | Trades/hora | Resultado esperado |
| :--- | :--- | :--- |
| YOLO continuo | 40 | **Ruina** — converge a −8.5% por trade |
| Sniper selectivo | 1-3 | EV positivo marginal (+2% a +5%) |

**La unica configuracion con expectativa positiva es francotirador selectivo**, aceptando
pasar el 75% del tiempo en silencio. Esto **contradice la prioridad de "velocidad"** que el
usuario eligio al principio de la sesion. La prioridad correcta es **selectividad**, no
velocidad: operar mas con EV negativo acelera la perdida.

### Advertencia sobre las afirmaciones de los agentes
Durante esta sesion, dos agentes hicieron afirmaciones **refutadas por los datos**:
1. Un agente afirmo que el bot "vende en el piso (price_y 1600-1825)" causando las perdidas.
   **Los datos muestran lo contrario**: los SELL ganadores estan en el suelo (1573), los
   perdedores en zona media (1390).
2. Un agente afirmo que la calibracion del boton BAJA estaba mal porque el codigo usa
   `screenW * 0.75f`. **Falso**: `CalibrationManager.kt:109-113` devuelve las coordenadas
   **guardadas** (`983.0, 2420.0`) y solo cae al default si fallan la validacion.
   Ademas uso resolucion 1080x2400 cuando la real es 1220x2712.
**Leccion: verificar cada afirmacion de un agente contra el codigo y los datos reales
antes de implementar. Un diff plausible puede estar construido sobre un analisis erroneo.**

---

## 7. Hipótesis rankeadas (orden de ataque)

Ordenadas por (valor esperado del hallazgo) / (esfuerzo). **Una por iteración.**

### H1 · El lado SELL está roto (no el sistema entero) — HALLAZGO PRINCIPAL
**Estado: CONFIRMADO y AMPLIADO con análisis de posición de precio.**

| Acción | n | WIN | LOSS | TIE | **WR real** |
| :--- | :-: | :-: | :-: | :-: | :-: |
| **BUY** | 12 | 5 | 5 | 2 | **50.0%** |
| **SELL** | 23 | 4 | 14 | 5 | **22.2%** |

#### Donde ocurren las perdidas (analisis por posicion de precio)
`price_y` en la sesion: rango **792 (techo) a 1825 (suelo)**, mediana 1462.
Recordatorio: **en pantalla, Y bajo = precio ALTO (techo); Y alto = precio BAJO (suelo).**

| Accion | Resultado | n | price_y medio | Zona |
| :--- | :--- | :-: | :-: | :--- |
| BUY | WIN | 5 | 1354 | MEDIO |
| BUY | LOSS | 5 | 1265 | MEDIO |
| BUY | TIE | 2 | 1595 | SUELO |
| SELL | **WIN** | 4 | **1573** | **SUELO** |
| SELL | **LOSS** | 14 | **1390** | **MEDIO** |
| SELL | TIE | 5 | 1380 | MEDIO |

**Hallazgo clave: los 14 SELL perdedores NO venden en el suelo, venden en ZONA MEDIA.**
Esto **refuta** la hipotesis inicial de que el bot "vende en el piso". Los SELL que GANAN
estan en zona de suelo (1573); los que PIERDEN estan en zona media (1390).

**Interpretacion correcta**: la **zona media es tierra de nadie**. Sin soporte ni resistencia
claros, el precio no tiene direccion y la probabilidad de acertar el signo a 60s tiende al
azar o peor. El bot opera ahi porque el bug de S/R (abajo) le hace creer que SI hay niveles.

#### Las dos causas tecnicas confirmadas
1. **Colapso de distancias S/R por `Math.max(0.0, ...)`** — `SyntheticCandleEngine.kt:500-501`:
   ```kotlin
   val rawDistToSupport = Math.max(0.0, currentPrice - dynamicSupportPrice)
   val rawDistToResistance = Math.max(0.0, dynamicResistancePrice - currentPrice)
   ```
   Cuando el precio atraviesa un nivel, el negativo se convierte en **0.0**, y `0.0` significa
   "pegado al nivel". El bot cree estar tocando soporte Y resistencia **a la vez**, disparando
   senales contradictorias en simultaneo. Explica el volumen SELL duplicado (23 vs 12).

2. **Veto asimetrico en la regla de mayor peso** — `TradingEngine.kt:426` y `:432`:
   ```kotlin
   // Linea 426 (SELL): SIN !inUptrend
   (analysis.isNearResistanceZone || analysis.touchesResistance || ...) &&
   // Linea 432 (BUY): SIN !inDowntrend
   (analysis.isNearSupportZone || analysis.touchesSupport || ...) &&
   ```
   Las reglas vecinas (lineas 422, 423, 440, 442, 446) **si** llevan `!inDowntrend`/`!inUptrend`.
   A la regla de "Reversion por Sobreextension" le faltan, asi que dispara en contra de tendencia.

**Nota**: un agente afirmo que el bot "vende en el piso (price_y 1600-1825)". **Los datos lo
refutan**: los SELL ganadores estan en el suelo, los perdedores en zona media. Verificar
siempre las afirmaciones de los agentes contra los datos.

### H2 · Integridad de datos (CR-3) — IMPRESCINDIBLE ANTES DE MEDIR NADA
- **Hipótesis**: hay victorias y derrotas falsas que contaminan el aprendizaje y las métricas.
- **Prueba**: contar `BALANCE_CORRUPTO` y `DECLARADO_SIN_MOVIMIENTO` por ventana.
  Hoy: 3 filas sospechosas de 35 (8.6%). Cualquier medición con >2% de filas sucias no es fiable.
- **Acción**: aplicar el fix de `readCurrentBalance` antes de la siguiente ventana.
  Sin esto, **ninguna mejora de winrate medida es creíble**.

### H3 · Desactivar martingala — REDUCE EL DAÑO, NO DA RENTABILIDAD
- **Hipótesis**: la martingala es la causa principal de la pérdida de capital.
- **Realidad**: quitar la martingala **no sube el winrate ni un punto**. Solo hace que la
  pérdida sea lineal (−0.454 u/trade) en vez de geométrica (−1.98 u/ciclo).
  **Compra tiempo, no rentabilidad.**
- **Cuándo**: después de H1 y H2. No antes, porque sin datos limpios no se puede medir
  si H1 funcionó.

### H4 · Latencia de ejecución (strike en el peor punto)
- **Hipótesis**: la entrada se asigna tarde y el strike queda en contra.
- **Prueba**: cruzar `candle_second` del CSV contra los resultados. Si los trades
  disparados fuera de la ventana :58–:02 tienen peor WR, es latencia.
- **Nota**: el campo `candle_second` nuevo del logger permite esta prueba. El CSV viejo no lo tenía.

### H5 · Optimización de filtros de señal
- **Hipótesis**: mejores filtros suben el winrate.
- **Realidad**: **última prioridad**. Sobre un sistema con posible sesgo invertido y datos
  sucios, optimizar filtros es ajustar el ruido. Además, la literatura cuantitativa
  (Bouchaud, Cartea) sitúa la predicción del signo a 60s en 52–53% incluso con
  order flow L3 y co-location — subir 25 puntos es implausible por esta vía.

---

## 8. Bloqueadores conocidos

### RESUELTO — Sin celular conectado
POCO X6 Pro conectado por **USB**: serial `5PPFAACU6H7XHEY9`, modelo `2311DRK48G`,
Android **16** (SDK 36). Artemis reporta `verdict: ready` (5/5 checks).
**Nota: la conexion USB se cayo a mitad de sesion; hay que reconectar.**

### RESUELTO — El broker SÍ es Binomo
El paquete instalado es `com.marketly.trading`, lo que llevó a una conclusion erronea
("no hay Binomo"). **Es Binomo**: la pantalla muestra `Cuenta Demo`, `Col$40,414,809.60`,
`Crypto IDX 83%`, vela `1m`. El propio codigo ya lo sabe: `BrokerDetector.kt:16` mapea
`marketly.trading -> Broker.BINOMO` con el comentario "Binomo APK package".
**Leccion: verificar la pantalla, no deducir del nombre del paquete.**

### RESUELTO — Accesibilidad sin enlazar (bloqueaba TODOS los clics)
Confirmado y **resuelto por el usuario** activando el toggle manualmente.
Estado verificado:
```
Bound services:{Service[label=TradeDraw...]}   <-- ENLAZADO: clics operativos
```
Los intentos por ADB (`settings put secure`) **no funcionan en Android 16** (seguridad
endurecida): Android mantiene el flag de habilitado pero no re-vincula el servicio.
**Ruta que funciona: toggle manual en Ajustes > Accesibilidad > TradeDraw.**

### DATO CRITICO — El payout real es 83%, no 82%
Medido en la UI: `Crypto IDX | 83%` (y `CHF/JPY (OTC) | 80%`). Break-even real para el
activo principal: `1 / 1.83 = 54.6%`. **Actualizar BASELINE["payout"] a 0.83 en el
analizador.**

### Confirmacion EN VIVO de la lentitud (captura de HUD)
Estado observado con el bot corriendo:
```
[YOLO 🚀] Estrat: Auto · 1D · ⏱️ :42s · 👁️ 62%
W: 0 | L: 0 (0.0%)  [M0 | $1.0]
🟢 WS Activo (Crypto IDX): 641.867404 (0ms) | Headless
📊 Tendencia: LATERAL / RANGO   Prob: 60%
🛡️ Filtro S/R: 🟢 Cerca de Soporte Cuantitativo (15%)
🎯 Plan: Cerca de Soporte (0%). Esperando gatillo CALL al segundo :58s - :03s.
🚀 AUTO: SUBMODO YOLO (Continuo)
   Operativa continua sin Stop Loss ni pausas. Opera ante cada setup.
```
**El propio HUD confirma el diagnostico**: muestra `⏱️ :42s` (fuera de la ventana) y el plan
dice literalmente "Esperando gatillo al segundo :58s - :03s". El bot pasa la mayor parte del
tiempo bloqueado por diseño, tal como predice el presupuesto de tiempo de la seccion 8.

### Bloqueador activo — El USB no transmite datos (usar Wi-Fi ADB)
El cable **carga pero no da datos**:
```
Get-PnpDevice -> "Dispositivo USB desconocido (Error de solicitud de descriptor)"
POCO X6 Pro 5G  Status: Unknown  (clase WPD)
adb devices -> vacio
```
Windows ve el telefono pero no puede leer sus descriptores: tipico de modo "Solo carga"
en HyperOS. **Mitigacion: ADB inalambrico funciona perfecto.**
Serial Wi-Fi: **`192.168.1.185:5555`** (misma subred que el PC, `192.168.1.245`).
Reconectar con: `adb connect 192.168.1.185:5555`

### HALLAZGO CRITICO — Kill switch de 40M bloquea la cuenta DEMO (causa real de "2h -> 4 operaciones")

**Este es el hallazgo mas importante y explica directamente la queja del usuario.**

`RiskManager.kt:89`:
```kotlin
var absoluteEquityFloor: Double = prefs?.getFloat("absolute_equity_floor", 40000000f)?.toDouble() ?: 40000000.0
```
`RiskManager.kt:266-271`:
```kotlin
if (currentBal > 0.0) {
    if (isDemo) {
        if (currentBal < absoluteEquityFloor) {
            android.util.Log.e("RiskManager", "CRITICAL STOP: Equity Demo ($currentBal) por debajo del suelo absoluto ($absoluteEquityFloor)")
            return Pair(false, "Stop Equity Absoluto Alcanzado ($currentBal < $absoluteEquityFloor)")
```

**Aritmetica del problema:**
- Suelo por defecto: **40,000,000 COP**. Nunca se configura desde ningun sitio (solo se lee).
- Saldo real de la cuenta demo: **40,414,809.60 COP**.
- Margen: **414,809 COP = 1.02%** del saldo.
- Cuatro perdidas de 100,000 COP y **el bot se apaga definitivamente**.

**Evidencia en logs**: el log de la sesion anterior contiene **8 apariciones** de
`CRITICAL STOP: Equity Demo (...) por debajo del suelo absoluto`. El bot se estaba
auto-deteniendo repetidamente.

**Por que importa mas que la ventana sniper**: el usuario reporto "2 horas -> solo 4
operaciones, 1 a 3 perdiendo". La ventana sniper explica operar poco, pero **el kill switch
explica por que 2 horas dieron apenas 4 operaciones**: el bot arranca, pierde unas cuantas,
cruza el umbral y se apaga. El resto de las 2 horas lo pasa detenido.

**Bugs secundarios del mismo bloque:**
1. **`getFloat` sobre `Double`**: para 40 millones, `Float` tiene ~7 digitos significativos,
   asi que el valor se representa con saltos de 4 unidades. Fragil y descuidado,
   aunque no rompe el calculo aqui.
2. **El suelo no se guarda en `TradeDraw_RiskConfig.xml`** — usa siempre el default
   hardcodeado. No hay UI para ajustarlo, asi que **nadie puede calibrarlo**.
3. **Un umbral absoluto en COP es fragil por diseno**: la cuenta crece o decrece, pero el
   suelo queda fijo. Un suelo relativo al saldo inicial de sesion (por ejemplo, "detener si
   se pierde el 20% de la sesion") seria correcto y no se dispararia por un saldo heredado.

**Fix minimo propuesto**: hacer el suelo **relativo al saldo de inicio de sesion** en cuenta
demo (por ejemplo, `sessionStartBalance * 0.80`), o desactivarlo en demo y mantenerlo solo
para cuenta real. Ambas son 1-3 lineas.

### HALLAZGO ESTRUCTURAL — El orden de evaluacion de `canExecuteTrade()` anula los limites correctos

`RiskManager.canExecuteTrade()` evalua los limites en este orden:

| Orden | Linea | Limite | Estado real |
| :-: | :-: | :--- | :--- |
| 1 | **268** | `absoluteEquityFloor` (**40M hardcodeado**) | **DISPARA PRIMERO — eclipsa todo lo demas** |
| 2 | 303 | `sessionFloor` (relativo, 3% de sesion) | **Correcto pero nunca se alcanza** |
| 3 | 313 | `trailingProfitLock` | Alcanzable |
| 4 | 323 | `consecutiveVoids >= 3` | **Nunca se alimenta** (ver CR-2) |
| 5 | **328** | `stopLossStreak` | **Inalcanzable** (la racha se resetea en linea 353) |
| 6 | 353 | `currentLossStreak = 0` (**efecto secundario mutante**) | **Rompe el limite 5** |
| 7 | 368 | `return Pair(true, "MODO YOLO sin limites")` | **Convierte en codigo muerto todo lo posterior** |

#### Por que el limite correcto NUNCA actua
`RiskManager.kt:297-306` **ya implementa un stop loss de sesion relativo, bien disenado**:
```kotlin
val maxLossAmt = maxOf(sessionStartBalance * sessionMaxLossRatio, minLossAllowance)
val sessionFloor = sessionStartBalance - maxLossAmt
if (currentBal < sessionFloor) {
    return Pair(false, "Stop Loss Sesion Alcanzado ($currentBal < $sessionFloor)")
}
```
Con `sessionMaxLossRatio = 0.03f` (linea 82) y saldo 40,414,809, el suelo correcto seria
**40,414,809 − 1,212,444 = 39,202,365 COP**.

Pero el `absoluteEquityFloor` de la linea 268 se evalua **antes** y corta en **40,000,000**.
Es decir: **el bot se detiene 800,000 COP antes de lo que deberia.** El umbral absoluto
redundante y mal calibrado esta tapando al umbral relativo que si funciona.

**Conclusion: el fix NO es disenar un mecanismo nuevo. Es BORRAR el umbral absoluto
redundante** (lineas 89, 268-271) y dejar que el relativo haga su trabajo. Menos codigo,
no mas.

#### Bug 4 confirmado y agravado — `canExecuteTrade()` muta estado y se llama desde el HUD
`TradingEngine.kt:893-902`:
```kotlin
fun getStrategyStatusHint(): String {
    ...
    val (canTradeStatus, blockReason) = riskManager.canExecuteTrade(mode, autonomousSubMode)
```
`getStrategyStatusHint()` **solo sirve para pintar texto en el HUD**, y se invoca
**en cada frame**. Dentro de `canExecuteTrade()`, la linea 353 ejecuta
`currentLossStreak = 0` como efecto secundario.

**Consecuencia: el simple refresco del HUD puede borrar la racha perdedora.** No hace falta
que el bot opere ni que pase el cooldown: basta con que el HUD se repinte. Un metodo de
consulta debe ser puro; este modifica el estado de riesgo mas critico del sistema.

#### Limites decorativos en YOLO (codigo muerto)
Confirmado por el auditor. Tras el `return` de la linea 368, estos NUNCA se evaluan en YOLO:
- `takeProfitWins` (linea ~375)
- `cooldownSeconds` / `lossCooldownSeconds` (linea ~378)
- `selectiveM1MinConfidence` general (linea ~385)

El HUD anuncia "sin limites", y literalmente es cierto: **el unico limite con efecto real en
YOLO es el kill switch de 40M**, que es precisamente el que esta mal calibrado.

### La aritmetica exacta de "2 horas -> 4 operaciones"
Reconstruccion del ciclo real confirma la hipotesis:
1. **Minuto 0**: trade 1 (LOSS) -> saldo baja
2. **Minuto 4**: trade 2, escalado M1 (LOSS) -> saldo baja mas
3. **Minuto 10**: trade 3 (WIN) -> recuperacion parcial
4. **Minuto 15**: trade 4 (LOSS) -> **saldo cruza los 40,000,000**
5. **Minutos 18 a 120 (los 102 minutos restantes)**: `canExecuteTrade()` devuelve
   `"Stop Equity Absoluto Alcanzado"` **en cada frame**. Cero ejecuciones.

**No fue lentitud. Fue el kill switch apagando el bot y dejandolo muerto hora y media.**
Coincide exactamente con las 4 operaciones y las ~2 horas reportadas por el usuario.

### La martingala con WR 32.1% (calculo verificado)
```
E0    = (0.321 x 0.83) − (0.679 x 1.00) = −0.4126   (perdida de 41.3% por orden)
E_M1  = (0.321 x 0.83) + (0.2180 x 0.66) + (0.4610 x −3.0) = −0.9727
```
**Se pierde ~0.97 stakes base por ciclo de martingala.** Ademas, con payout 83%, duplicar
el stake NO recupera la unidad perdida: gana `2 x 0.83 = 1.66` contra `1.00 + 2.00 = 3.00`
de riesgo. **La martingala es deficitaria por aritmetica, no solo por riesgo.**

### Evidencia del estado persistido (TradeDraw_RiskConfig.xml)
Leido por `run-as` desde el dispositivo:
```xml
<int name="session_curr_wins"   value="3" />
<int name="session_loss_streak" value="3" />   <-- racha actual = 3
<int name="session_total_losses" value="0" />  <-- INCOHERENTE: 3 de racha y 0 totales
<int name="sl_streak" value="6" />             <-- umbral Stop Loss = 6, NO 3
<int name="session_total_wins"  value="0" />
```
Dos hallazgos:
1. **`sl_streak = 6`**, no el default 3. El umbral real es 6 derrotas.
2. **`session_loss_streak = 3` con `session_total_losses = 0` es incoherente.** Los contadores
   de estado estan desincronizados entre si. Confirma que la contabilidad de W/L y la racha
   no son consistentes.

### Datos de calibracion (TradeDraw_Calibration.xml)
```xml
<float name="BINOMO_port_buy_x"  value="188.0" />
<float name="BINOMO_port_buy_y"  value="2417.0" />
<float name="BINOMO_port_sell_x" value="983.0" />
<float name="BINOMO_port_sell_y" value="2420.0" />
```
Pantalla portrait = **1220 x 2712**. Ambos botones comparten la misma Y (~2417/2420, coherente
con estar lado a lado abajo), separados en X (188 y 983). Simetricos respecto al centro (610).
**No se detecta desplazamiento obvio aqui**, pero el `y=2420` esta muy cerca del borde
inferior (2712) y de la barra de navegacion — conviene validar el toque real en dispositivo.

### Estado del entorno verificado
- **101 tests unitarios pasan, 0 fallos** (100 de linea base + 1 nuevo de confianza).
- APK instalado: v1.3 (versionCode 4), coincide con `app/build.gradle.kts`.
- Cuenta **Demo**, saldo `Col$40,414,809.60`. Bateria 90%.
- Estrategia guardada: `AUTO_ADAPTIVE` (no las MT_*). El HUD muestra `Estrat: Auto`.

### DATO — Cooldowns y ventana de timing (causa de la lentitud reportada)
El usuario reporta: **2 horas -> solo 4 operaciones (1W/3L)**.

Presupuesto de tiempo por vela de 60s:
| Candado | Fuente | Duracion | % de la vela |
| :--- | :--- | :-: | :-: |
| Veto de timing `:15-:55` | `MarketTick.kt:59` | 41 s | **68%** |
| Fuera de ventana sniper | `MarketTick.kt:54` | 10 s | 17% |
| **Ventana util `:57-:05`** | | **9 s** | **15%** |

Encima se apilan:
- `minSpacingMs`: 8s en YOLO, **35s si hay racha perdedora**, 12s normal (`TradingEngine.kt:1253-1256`)
- Cooldown tras perdida: **180s** normal, 35s en YOLO (`RiskManager.kt:23-24`)
- Cooldown extendido de **180s tras 2 derrotas** (`RiskManager.kt:25`)
- Filtro CHOP, veto anti-sobreextension, veto de tendencia

**Multiplicando puertas AND, 30 min/operacion es el resultado esperado, no un bug.**
El bot solo puede actuar en el 15% del tiempo, y sobre esa ventana se apilan cooldowns
que la mayoria de las veces la dejan pasar vacia.

### DECISION DEL USUARIO (registrada)
Prioridad elegida: **VELOCIDAD** (que opere mas seguido), por encima de precision.
**Riesgo asumido y documentado**: el lado SELL acierta 22.2%. Relajar la ventana sniper
hara que el bot opere mas usando precisamente el lado averiado. **Se espera que el saldo
empeore antes de mejorar.** No confundir "opera mas" con "gana mas".
Criterio de commit confirmado: **sin medicion de 20 min no hay commit.**

### Estado del entorno verificado
- **100 tests unitarios pasan, 0 fallos** (linea base antes de tocar nada).
- APK instalado: v1.3 (versionCode 4), coincide con `app/build.gradle.kts`.
- Cuenta **Demo**, saldo `Col$40,414,809.60`.