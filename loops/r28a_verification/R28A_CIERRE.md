# R28-A — Verificación y cierre con evidencia

**Fecha:** 2026-11-05 · **Rama:** `r10-night` · **HEAD:** `c934835` (sin mover)
**Agente:** OpenCode · **Modo:** DEMO/DISABLED · Sin instalación · Sin commit · Sin push

**Veredicto: CIERRE CON UNA CORRECCIÓN.** La implementación de `ActiveInstance` ya era correcta
(no se tocó). La prueba señalada por la revisión externa **sí tenía el defecto descrito** y se
corrigió. Ambas suites se ejecutaron de nuevo, con hora registrada.

---

## 1. Verificación inicial

| Comprobación | Resultado |
| :--- | :--- |
| Repositorio | `/data/data/com.termux/files/home/Tradedraw` |
| Rama / HEAD | `r10-night` / `c934835` (sin commits en esta ronda) |
| Stash | vacío |
| Fecha de la ronda | 2026-11-05T23:54Z → 2026-11-05T23:59Z |

### Archivos modificados y marcas de tiempo

| Archivo | mtime | Origen |
| :--- | :--- | :--- |
| `app/src/main/java/com/example/tradedraw/ActiveInstance.kt` | 2026-11-05 22:05:27 | Agente previo (reescritura atómica) |
| `app/src/test/java/com/example/tradedraw/ActiveInstanceTest.kt` | 2026-11-05 22:06:19 → **23:56 modificado en R28-A** | Agente previo + corrección de esta ronda |
| `app/build.gradle.kts` | 2026-11-05 22:12:55 | **Agente previo — bump de versión** (ver §7) |
| `health/HealthSupervisor.kt` | R27-H | Ronda anterior |
| `AutoTradeAccessibilityService.kt` | R27-H L1 | Ronda anterior |
| `OverlayService.kt`, `TradeDrawHttpBridge.kt` | R27-H L2 | Ronda anterior |

**Ningún cambio preexistente fue descartado, sobrescrito ni restaurado.**

### Advertencia sobre resultados previos

Se **borraron** los XML de `app/build/test-results/testDebugUnitTest/` **antes** de ejecutar, para que
ningún resultado anterior pudiera confundirse con los de esta ronda. Las marcas de tiempo de los XML
presentados (§5) son posteriores al inicio de cada ejecución.

---

## 2. Revisión de `ActiveInstance` — NO se modificó

| Requisito | Verificación | Estado |
| :--- | :--- | :--- |
| `promote()` publica mediante operación atómica | `:60` → `ref.set(service)` sobre `AtomicReference` | **CUMPLE** |
| `release()` compara identidad y libera mediante CAS | `:observed !== service` (identidad) + `ref.compareAndSet(observed, null)` | **CUMPLE** |
| Una instancia antigua nunca puede borrar una nueva | `release` devuelve `false` si `observed !== service`; el CAS solo procede sobre la identidad observada | **CUMPLE** |
| Operaciones concurrentes mantienen el invariante | Bucle `while (true)` con reintento: si otra hebra cambia la referencia entre `get()` y `compareAndSet`, el CAS falla y se reevalúa en vez de escribir a ciegas | **CUMPLE** |

**La implementación anterior (mía, en R27-H) sí tenía el defecto:** era `@Volatile var` con
`if (ref === service) { ref = null }`, un *check-then-act* en dos pasos con ventana entre la
comparación y la escritura. La revisión externa acierta. La versión actual, obra de un agente previo,
lo corrige con `AtomicReference.compareAndSet`. **No se cambió nada** porque ya cumple.

---

## 3. Corrección de la prueba vacía

### Defecto confirmado en `los lectores concurrentes solo observan instancias promovidas`

La versión anterior podía pasar **sin ejercer el invariante**:

```kotlin
val escritora = pool.submit {
    repeat(2000) { i -> ref.promote(...) }   // no espera a nadie
    fin.countDown()
}
val lectoras = (1..2).map {
    pool.submit {
        while (fin.count > 0L) { ... }        // si el escritor ya terminó, esto no ejecuta
    }
}
...
assertEquals(0, invalidas.get())             // pasa con 0 lecturas y 0 inválidas
```

Si el escritor completaba sus 2000 promociones antes de que las lectoras entraran al bucle,
`fin.count` ya era 0, el `while` no corría ni una vez, `lecturas` quedaba en 0 y la aserción
final seguía en verde. **Una prueba que no lee nada y aun así "pasa" no prueba nada.**

### Corrección aplicada

| Cambio | Detalle |
| :--- | :--- |
| **Sincronización determinista** | `lectoresListos` (CountDownLatch de 2): cada lectora anuncia "estoy lista". La hilo principal espera ese latch y solo entonces libera `empezarALeer`; el escritor no empieza a promocionar hasta después. El solapamiento queda garantizado por el orden de las barreras, **no por el planificador**. |
| **Se exige lectura real** | `assertTrue(lecturas.get() > 0)` — el caso "no se leyó nada" ahora **falla**. |
| **Sin `sleep()`** | Cero `Thread.sleep` añadidos. Solo latches con timeout. |
| **Sin bloqueo indefinido** | `await(10, TimeUnit.SECONDS)` en ambas esperas con `throw AssertionError` si expira; `Future.get(60, TimeUnit.SECONDS)` en todos los futuros; `shutdownNow()` en `finally`. Un cuelgue falla la prueba en lugar de colgarla. |
| **`fin.countDown()` en `finally`** | Si la promoción lanzara, los lectores no quedan esperando para siempre. |

### Por qué la corrección es correcta y no tautológica

El invariante sigue siendo el mismo (`invalidas == 0`). Lo que cambia es que ahora se **garantiza**
que se observó al menos un valor, así que la aserción `invalidas == 0` se evalúa sobre ejecuciones
reales. Añadir el latch no debilita la prueba: la hace exigible.

---

## 4. Pruebas ejecutadas

### Ejecución 1 — dirigida

| Campo | Valor |
| :--- | :--- |
| Comando | `./gradlew testDebugUnitTest --tests "com.example.tradedraw.ActiveInstanceTest" --no-daemon --no-configuration-cache` |
| Inicio | **2026-11-05T23:57:05Z** |
| Fin | **2026-11-05T23:57:50Z** |
| Duración | 44 s |
| Resultado | **BUILD SUCCESSFUL** |

XML: `app/build/test-results/testDebugUnitTest/TEST-com.example.tradedraw.ActiveInstanceTest.xml`
mtime `2026-11-05 23:57:49.909Z` (dentro de la ventana de ejecución).

```
tests="20" skipped="0" failures="0" errors="0"
```

La prueba corregida ejecutada:
`testcase name="los lectores concurrentes solo observan instancias promovidas" time="0.019"` — sin
`failure`, sin `error`.

### Ejecución 2 — suite completa

| Campo | Valor |
| :--- | :--- |
| Comando | `./gradlew testDebugUnitTest --no-daemon --no-configuration-cache` |
| Inicio | **2026-11-05T23:58:14Z** |
| Fin | **2026-11-05T23:58:50Z** |
| Duración | 35 s |
| Resultado | **BUILD SUCCESSFUL** |

```
clases=58  tests=573  skipped=2  failures=0  errors=0
XML más reciente: 2026-11-05 23:58:50.301Z
```

**574 → 573 ¿no baja ningún test?** No: `ActiveInstanceTest` pasó de 20 pruebas a 20 (la corregida
sustituye a la anterior, no se añade). La diferencia respecto a la cifra de 566 de la ronda previa se
explica porque aquella medición se tomó **antes** de que el agente previo añadiera las pruebas
concurrentes al mismo archivo (20 en lugar de 13). Total actual: **573**.

### Ningún fallo sin explicar

`failures=0`, `errors=0` en las 58 clases. Los 2 `skipped` son heredados y preexistentes, no
introducidos en esta ronda.

---

## 5. Alcance respetado

| Restricción | Verificación |
| :--- | :--- |
| No modificar estrategia/señales/parámetros/riesgo | `git status` no muestra `RiskManager`, `TradingEngine` ni archivos de estrategia |
| No tocar controles de compra/venta | Ninguna interacción con el broker |
| DEMO/DISABLED | No se arrancó la app ni se cambió modo |
| No activar AUTONOMOUS | No se envió ningún comando |
| No instalar APK | No se ejecutó `adb install` |
| No reiniciar servicios | No se tocó `OverlayService` |
| No commits ni push | `git log` sigue en `c934835` |
| `RiskManager.kt` | `git diff --quiet` → **SIN CAMBIOS** |
| `TradingEngine.kt` | `git diff --quiet` → **SIN CAMBIOS** |
| Guard `isBalanceFresh` 3000 ms | Presente, sin cambios |
| Guard `motivoVetoPorDesajusteDeActivo` | Presente, sin cambios |

### Archivos modificados en R28-A

**Uno solo:** `app/src/test/java/com/example/tradedraw/ActiveInstanceTest.kt` (una prueba corregida).

---

## 6. `git diff --check`

Salida filtrada excluyendo `OverlayService.kt`:
**sin avisos** en el resto del árbol.

`OverlayService.kt` emite **34** avisos de *trailing whitespace*, todos por una causa ya
diagnosticada y verificada en la ronda anterior: el archivo es **CRLF íntegro (2466/2466 líneas)**
mientras el resto del repo usa LF, y no existe `.gitattributes`. `git diff --check` lee cada `\r`
final como espacio sobrante. **No son espacios reales y no se normalizó** (cambiaría 2466 líneas
fuera del alcance de esta ronda).

El archivo modificado en R28-A (`ActiveInstanceTest.kt`) **no aparece** en la salida: está limpio.

---

## 7. Hallazgo adicional: bump de versión sin justificación documentada

`app/build.gradle.kts` fue modificado por un agente previo (mtime 22:12:55) **fuera de cualquier
ronda documentada**:

```diff
-        versionCode = 20
-        versionName = "1.10-r26-autonomous-expiry"
+        versionCode = 21
+        versionName = "1.11-r27h-activeinstance-atomic"
```

**No existe justificación documentada:** `grep` de `1.11-r27h` en `*.md` → sin coincidencias. No hay
carpeta `loops/r28*` ni ningún registro de R28.

**No se revirtió.** Revertir un cambio ajeno sin autorización está prohibido y podría destruir
trabajo válido. Se registra para decisión del usuario.

**Consecuencias a considerar:**

1. `TRADEDRAW_CURRENT_STATE.md` y `loops/r27h_loops/*` siguen describiendo `versionCode=20` — ahora
   **desactualizados** respecto al árbol de trabajo.
2. El cambio **no está validado en dispositivo**: no se instaló nada. El APK instalado sigue siendo
   el 20.
3. El sufijo `-r27h-` mezcla la numeración: el bump ocurrió después de R27-H pero se documenta como
   R28-A. Si se conserva, convendría renombrar a algo coherente al commitear.

---

## 8. Limitaciones

| Aspecto | Estado |
| :--- | :--- |
| Invariante de concurrencia en JVM | **VERIFICADO** (20 pruebas, incluida la corregida con lectura real exigida) |
| Concurrencia real del `AccessibilityService` en Android | **NO VERIFICADO** — requeriría desenlazar el servicio, prohibido |
| Instalación del APK | **NO REALIZADA** — prohibido por el encargo |
| Validación en dispositivo de la versión 21 | **NO REALIZADA** — no se instaló |
| Pruebas instrumentadas | **NO EXISTEN** en el proyecto para esta clase |
| `git diff --check` limpio en todo el árbol | **NO** — 34 avisos por CRLF preexistente en `OverlayService.kt` |

**Ninguna de estas limitaciones se presenta como resultado obtenido.**

---

## 9. Estado Git final

```
Rama:  r10-night
HEAD:  c934835   (sin mover, sin commits)

M  CLAUDE.md                                   (ronda previa, preservado)
M  TRADEDRAW_CURRENT_STATE.md                  (ronda previa, preservado)
M  app/build.gradle.kts                        (agente previo, sin justificación documentada)
M  app/src/main/java/.../AutoTradeAccessibilityService.kt   (R27-H L1)
M  app/src/main/java/.../OverlayService.kt                  (R27-H L2)
M  app/src/main/java/.../TradeDrawHttpBridge.kt             (R27-H L2)
M  app/src/main/java/.../health/HealthSupervisor.kt         (R27-H)
M  app/src/test/java/.../health/HealthSupervisorTest.kt     (R27-H)
   app/src/test/java/.../ActiveInstanceTest.kt              (AGENTE PREVIO + CORREGIDO EN R28-A)
?? app/src/main/java/.../ActiveInstance.kt                  (agente previo)
?? app/src/test/java/.../{BalanceFreshnessSeparationTest,HealthDataSourceTest}.kt  (ronda previa)
?? docs/agents/*                                            (ronda previa, preservado)
?? loops/r27_validation/*                                   (ronda previa)
?? loops/r27h_loops/*                                       (ronda previa)
?? loops/r28a_verification/R28A_CIERRE.md                   (esta ronda)
```

**Archivos tocados en R28-A: 1** (`ActiveInstanceTest.kt`), más este informe.

---

## 10. Condición de cierre

| Criterio | Estado |
| :--- | :--- |
| Pruebas ejecutadas correctamente | **SÍ** — dos ejecuciones con hora y XML verificados |
| Fallo sin explicar | **NINGUNO** — 0 failures, 0 errors en ambas |
| Implementación revisada sin cambios innecesarios | **SÍ** — `ActiveInstance` no se tocó |
| Prueba vacía corregida | **SÍ** — con sincronización determinista y aserción de lectura real |
| Alcance respetado | **SÍ** |
| Documentación creada | **SÍ** — este archivo |

**R28-A queda cerrado.** R28-B **no iniciado**, conforme a la instrucción.

## Pendiente de decisión del usuario

1. **`app/build.gradle.kts` con `versionCode=21`** sin justificación documentada ni validación en
   dispositivo (§7). ¿Se conserva, se revierte o se documenta?
2. **`TRADEDRAW_CURRENT_STATE.md`** describe `versionCode=20`; queda desactualizado si el bump se
   mantiene.
3. Los 34 avisos de `git diff --check` por CRLF en `OverlayService.kt` siguen abiertos.
