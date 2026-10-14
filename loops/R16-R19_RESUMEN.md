# R16–R19 · Resumen final

**Misión:** ingeniería de seguridad y fiabilidad sobre TradeDraw, cuatro rondas encadenadas sin
supervisión.
**Rama:** `r10-night` · **Base:** `af71997` (== `origin/main` al inicio).
**Inicio:** `2026-10-14 13:56` · **Fin:** `2026-10-14 16:40`.
**Batería inicial:** 74 % (cargando) · **final:** 83 % (cargando).

---

## 0. Lo más importante, primero

> **En 126 min de observación pasiva, el bot se rearmó 4 veces a `AUTONOMOUS`. Las 4 por la ruta de
> UI (`showAutonomousSubModeDialog`). CERO por el vector de broadcast que `af71997` cerró.**
>
> **Y no operó ni una sola vez: 0 taps, 0 operaciones, balance idéntico, 167 vetos V2 por rango
> insuficiente.** La gobernanza `master_traders_skill` se negó a entrar en un mercado plano. Ese es
> el resultado de seguridad más valioso de la misión: **un rearme no supervisado no produjo ninguna
> operación porque los filtros aguantaron.**

---

## 1. Por fase

| fase | SHA | tests | qué se verificó | qué NO |
| :--- | :--- | :--- | :--- | :--- |
| **R16** | `8950947` | 314 / 0 fallos / 2 omitidos | fix `af71997` formalizado; UID 2000 bloqueado (dirigido e implícito); root como control positivo; 0 emisores internos; tooling sin regresión; `main` local avanzada | bloqueo de `TAP`/`SWIPE` por efecto (habría disparado taps reales); emisor de R14 |
| **R17** | `a20bb95` | **no ejecutada** (solo docs) | tabla completa de componentes `exported`; 5 hipótesis clasificadas; `/mode` analizado; parche propuesto sin aplicar | si otra app alcanza `/mode`; si el login sobrevive a `exported=false`; cifrado en reposo |
| **R18** | `85222f1` + `d8ea309` | **325** / 0 fallos / 2 omitidos | ring dedicado (500 `ASSET` no desplazan); `/events` sin cambio de forma; JSONL sobrevive al reinicio del servicio; rotación sin pérdida; UID del emisor **NO DISPONIBLE** en API 31 (medido) | rotación real de 256 KB en dispositivo; supervivencia a reinicio del **teléfono** |
| **R19** | `01618ab` + informe | no aplica (solo observación) | **126 min, 673 muestras, 0 fallos de bridge, 0 huecos**; 4 rearmes **todos por UI**, 0 por `onReceive`; **0 operaciones** con 167 vetos V2; balance idéntico | comportamiento con **mercado operable** (el activo estuvo plano todo el tiempo) |

**Suite:** línea base 314 → **325** (+11, `ModeRingDedicadoTest`). **0 fallos** en todo momento.
**Balance:** `37.974.735,36` — **idéntico** en los cierres de R16, R17, R18 y R19 → **0 operaciones**.

---

## 2. Tabla única de hipótesis y estado

| # | hipótesis | ronda | estado |
| :--- | :--- | :--- | :--- |
| (a) | El parche cierra el vector de rearme por broadcast a apps no privilegiadas | R16 | **SOSTENIDA** |
| (b) | El parche no rompe el tooling | R16 | **SOSTENIDA** (los 2 emisores eran inertes ya antes) |
| (c) | El parche no altera decisiones de trading | R16 | **SOSTENIDA** (0 líneas de lógica tocadas) |
| (d) | Un intent externo altera el flujo de `BinomoAuthActivity` (H-INTENT) | R17 | **FALSADA** (0 lecturas de `intent`) |
| (e) | El token sale en claro (H-LEAK) | R17 | **SOSTENIDA** — 3 `Log.d`: `:256`, `:322`, `:352` |
| (f) | La actividad acepta URLs arbitrarias (H-REDIRECT) | R17 | **FALSADA** (URL constante `:161`) |
| (g) | La `@JavascriptInterface` permite acciones privilegiadas (H-BRIDGE) | R17 | **SOSTENIDA parcial**, riesgo BAJO |
| (h) | `/mode` rearma sin autenticación (H-MODE) | R17 | **SOSTENIDA**, solo loopback |
| (i) | El ring dedicado no es desplazado por `ASSET` (H-RING) | R18 | **SOSTENIDA** |
| (j) | `/events` no cambia de forma (H-COMPAT) | R18 | **SOSTENIDA** |
| (k) | El JSONL sobrevive al reinicio del servicio (H-PERSIST) | R18 | **SOSTENIDA** (medida en dispositivo) |
| (l) | La rotación no pierde lo reciente (H-ROT) | R18 | **SOSTENIDA** |
| (m) | `getSentFromUid()` disponible en API 31 (H-UID) | R18 | **FALSADA** (`since=34`) |
| (n) | El emisor del rearme de R14 | R14→R18 | **NO ATRIBUIDA** |
| (o) | No hay rearmes espontáneos con el vector cerrado (H-CERRADO) | R19 | **SOSTENIDA** — 0 rearmes por `onReceive` en 126 min |
| (p) | Quedan rearmes por otros vectores (H-RESIDUAL) | R19 | **SOSTENIDA** — 4 rearmes, **todos por UI** |
| (q) | El bridge aguanta horas sin falsos positivos (H-BRIDGE) | R19 | **SOSTENIDA** — 673/673 muestras OK, 0 huecos |
| (r) | `AUTONOMOUS` sin supervisión produce una operación | R19 | **FALSADA** en la ventana medida — 0 taps, 0 operaciones, 167 vetos V2 |

---

## 3. Los cuatro rearmes: todos por la misma ruta

| # | hora | origen | vector |
| :--- | :--- | :--- | :--- |
| — | `14:23:34` | `overlayCommandReceiver.onReceive:161` | **prueba controlada R18.5 (root, la única permitida)** |
| 1 | `13:51:02` | `showAutonomousSubModeDialog:855` | **UI** |
| 2 | `14:26:11` | `showAutonomousSubModeDialog:869` | **UI** |
| 3 | `15:03:08` | `showAutonomousSubModeDialog:869` | **UI** |
| 4 | `15:32:23` | `showAutonomousSubModeDialog:869` | **UI** |

**Contexto medido en los cuatro** (logcat): Binomo en primer plano → dos ventanas overlay de
TradeDraw en secuencia de ~1 s → rearme. `showAutonomousSubModeDialog` (`OverlayService.kt:843-874`)
exige **dos selecciones del usuario**; **no existe ruta de código que la invoque sola**.

- Incidente 1: el instalador MIUI (uid 10080) lanzó `MainActivity`, con `onScroll` en el paquete.
- Incidente 2: `START` de Binomo con `bnds=[528,560][696,801]` desde uid 10051 + 4 `onScroll`.
- Incidentes 3 y 4: mismo patrón de dos ventanas overlay.

**En los cuatro:** `orderState=NINGUNA`, `hasPendingTrade=false`, **0 taps**, balance sin cambios →
**ninguno es incidente de trading**. **Indicio fuerte de interacción humana; NO ATRIBUIDO con
certeza** (no se descarta inyección de gestos por una app con accesibilidad).

**Valor de la instrumentación:** el incidente 2 y los siguientes quedaron **atribuidos con
precisión** por el diario de R18 — imposible en R14, donde el rearme quedó sin explicación. **La
instrumentación demostró su utilidad en el primer caso real que se le presentó.**

---

## 4. NO VERIFICADO

1. **El emisor del rearme de R14: NO ATRIBUIDO.** El vector está identificado; quién lo usó, no.
2. **El bloqueo de `TAP`/`SWIPE` por efecto** no se probó (habría disparado taps reales). Se infiere
   del mecanismo compartido.
3. **`/mode` y `/trade` alcanzables desde otra app del dispositivo**: declarado por modelo de
   plataforma, **no medido**.
4. **`root`, Magisk, Shizuku y Termux-`su` siguen pudiendo rearmar** por broadcast. Medido y
   declarado como límite, no como fallo.
5. **El token en claro sigue sin corregir** (C1 de R17 pendiente de aplicar).
6. **El parche de R17 no se aplicó** — requiere sesión supervisada.
7. **Cifrado en reposo** ausente; `allowBackup="true"` sin auditar sus reglas.
8. **`usesCleartextTraffic="true"`** sin corregir.
9. **Rotación real de 256 KB** en el diario, no ejercitada en dispositivo.
10. **`balance=0.0`** mientras Binomo no está en primer plano: es un **valor no leído**
    (`readCurrentBalance()` devuelve `null` sin la pantalla del broker abierta), **no** una
    discrepancia. Verificado leyendo el código (`TradeDrawHttpBridge.kt:359`,
    `AutoTradeAccessibilityService.kt:531`). **El balance real al cierre fue `37.974.735,36`.**
11. **El comportamiento con mercado operable: NO VERIFICADO.** El activo estuvo plano las 126 min
    (rango `~2.9e-10` vs umbral `1e-05`, 167 vetos V2). **No se sabe** si un rearme no supervisado
    despacharía con rango suficiente. **Es el hueco de evidencia más importante de la misión.**

---

## 4-bis. El resultado de seguridad más valioso

Durante **234 muestras (~39 min)** el motor estuvo en `AUTONOMOUS` **sin supervisión**, y:

| comprobación | resultado |
| :--- | :--- |
| `orderState` distinto de `NINGUNA` | **0 / 234** |
| `hasPendingTrade = true` | **0 / 234** |
| Taps despachados | **0** |
| Balance | **sin cambios** |

Bloqueado por sus propios filtros: **167 vetos V2** por rango insuficiente.

> **`AUTONOMOUS` no significa «operar»: significa «evaluar». Y la evaluación dijo que no.**
>
> Un rearme no supervisado **no produjo ninguna operación** porque `master_traders_skill` hizo su
> trabajo. Esto también explica por qué R14 no produjo operaciones a pesar de sus dos rearmes: el
> mismo mecanismo. **No es suerte; es diseño.**

**Riesgo residual real:** no es «una operación no supervisada» — eso lo bloquea la gobernanza,
medido. El riesgo es **«una orden se despacha cuando el mercado SÍ tenga rango»** en un rearme no
supervisado. Ese escenario **no ocurrió** y **no se puede descartar**.

---

## 5. Riesgos residuales

| riesgo | severidad | estado |
| :--- | :--- | :--- |
| **Root / Magisk / Shizuku / Termux-`su` pueden rearmar el bot** | ALTA | por diseño (el operador debe poder); documentado |
| **`/mode` del bridge acepta `AUTONOMOUS` sin autenticación** | MEDIA | solo loopback; token efímero propuesto (C4) |
| **`/trade` del bridge ejecuta taps reales** | ALTA | mismo alcance; R13 ya lo movió a loopback |
| **Token en claro en logcat** (3 puntos) | MEDIA-ALTA | **corregible con riesgo nulo** (C1); pendiente |
| **`BinomoAuthActivity` exportada sin necesidad** | MEDIA | propuesta C2, requiere prueba manual |
| **`usesCleartextTraffic="true"`** | BAJA | endurecimiento pendiente |
| **`logd` puede morir** y llevarse la atribución por `Permission Denial` | MEDIA | limitación declarada; no se levanta en caliente |

---

## 6. Recomendaciones R20

1. **PRIORITARIO — repetir la observación pasiva cuando el mercado TENGA rango.** Es el único modo
   de saber si un rearme no supervisado llega a despachar una orden. Sin eso, el hueco de evidencia
   persiste y **es el más grave que queda abierto**.
2. **Aplicar C1 de R17** (redactar los 3 `Log.d` del token). Riesgo nulo, hallazgo real, una sesión.
3. **Aplicar C2** (`exported="false"` en `BinomoAuthActivity`) con el protocolo manual de P1/P2.
4. **Aplicar C4** (token efímero en el bridge) junto con la migración de
   `fast_trader_cli.py` y `test_master_trader_suite.py`. Cierra `/mode` y `/trade` a otras apps.
5. **Considerar un auto-`DISABLED` por inactividad** en `AUTONOMOUS` (p. ej. N minutos sin
   confirmación explícita). Mitiga el vector de UI — el único que quedó vivo y demostrado — sin
   quitarle control al humano.
6. **Mantener el diario de R18**: demostró su valor atribuyendo 4 rearmes que en R14 habrían quedado
   sin explicación. Es la pieza de observabilidad más útil añadida en toda la misión.
7. **Valorar `LocalSocket` con permiso de firma** en lugar de TCP loopback para el bridge.
8. **Verificar las reglas de backup** o poner `allowBackup="false"`.
9. **Valorar `usesCleartextTraffic="false"`** tras probar los flujos.
10. **Si se quiere atribuir broadcasts aceptados**, subir `minSdk` a 33+ para usar
    `getSentFromUid()` — o mantener la dependencia de `logd`, que es frágil en este dispositivo.

---

## 7. Estado de cierre

| campo | valor |
| :--- | :--- |
| `mode` | **`DISABLED`** |
| `orderState` | **`NINGUNA`** |
| `hasPendingTrade` | **`false`** |
| `totalWins` / `totalLosses` | **`0` / `0`** |
| `tapsDespachados` | **`0`** |
| `balance` | **`37.974.735,36`** — idéntico a los cierres de R13, R14, R15, R16, R17 y R18 |
| Batería por fase | R16: 74→75 % · R17: 75→76 % · R18: 76→75 % · R19: 75→83 % (cargando) |
| Bridge | vivo en `127.0.0.1:8080` (loopback, vía `adb forward`) |
| `logd` al cierre | `running` |
| **N de operaciones supervisadas** | **0** |
| Veredicto | **PASS-AUTOR** (verificación independiente pendiente: agy/hermes) |

### Hitos de los commits

| commit | contenido |
| :--- | :--- |
| `875dbcb` | preregistro retroactivo R16 |
| `8950947` | R16: tooling, límite de root, enmienda R15, informe |
| `a20bb95` | R17: auditoría de `BinomoAuthActivity`, tabla `exported`, parche propuesto |
| `85222f1` | R18: ring dedicado + diario JSONL + tests |
| `d8ea309` | R18: informe |
| `01618ab` | R19: preregistro + incidente 2 |
| `5ddc995` | **`fix(r18)`**: los arreglos de compilación del test, que `85222f1` dejó fuera |
| _cierre_ | R19: informe, serie cruda y este resumen |

---

## 9. Lección de proceso (hallazgo tardío, no oculto)

Al auditar el árbol justo antes del push final se descubrió que **`85222f1` había quedado con el
test `ModeRingDedicadoTest.kt` roto**: `funelJsonDeEventsNoCambiaDeForma()` (falta un espacio) y
`java.io.File(tmp.root, "no\0valido")` (`Illegal escape '\0'`). Los arreglos existían en el árbol de
trabajo — de ahí el 325/0/2 medido — pero **nunca se commitearon**.

**Consecuencia:** los commits `85222f1` y `d8ea309` no compilan. Corregido en **`5ddc995`**,
verificado tras el arreglo (`BUILD SUCCESSFUL`, **325 tests, 0 fallos, 0 errores, 2 omitidos**).

**Regla 7 refinada:** «sin build verde + suite verde no se hace push» **no basta** si la verificación
se hace sobre un árbol que difiere del commit. **Hay que verificar el commit, no el directorio de
trabajo** — `git stash` o un `git clean` antes de medir. Se documenta como enmienda fechada en
`loops/R18_REPORTE.md` §9 y no se reescribe el cuerpo original.

**Los números medidos siguen siendo válidos** para el árbol que se construyó e instaló; lo que
fallaba era únicamente la reproducibilidad desde el commit.

---

## 8. SUPUESTOS adoptados (no había a quién preguntar)

1. **Tras un `adb install`, el instalador MIUI queda abierto** y puede lanzar la app. Se cierra
   explícitamente antes de toda medición pasiva. *(Adoptado tras el incidente 1.)*
2. **`balance=0.0` sin la pantalla del broker abierta = valor no leído**, no discrepancia. Se
   verifica por código, no se asume.
3. **El parche de R17 se propone fuera del árbol de producción** (`scratch/proposals/`) para
   cumplir «no aplicarlo» sin perder el trabajo.
4. **La prueba controlada de R18.5 se ejecutó con `root`** (UID 0), porque el permiso de firma
   cierra la vía no privilegiada y era la única forma de verificar el diario con un rearme real.
5. **`/mode_log` como endpoint nuevo** en lugar de tocar `/events`, para garantizar compatibilidad
   por construcción.
6. **No se levantó `logd` en caliente** en ningún momento (AGENTS.md §8.2).
