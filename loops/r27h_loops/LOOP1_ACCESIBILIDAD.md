# LOOP 1 — Ciclo de vida de la referencia estática de AccessibilityService

**Fecha:** 2026-11-05 · **Rama:** `r10-night` · **HEAD:** `c934835` (sin commits)
**Agente:** OpenCode · **Modo:** DEMO/DISABLED · Sin instalación · Sin reinicio de servicios

---

## 1. Objetivo y alcance

Eliminar la ventana en la que `AutoTradeAccessibilityService.instance` publica una instancia que
ya no recibe eventos del sistema, sin introducir carreras y sin poder borrar una instancia nueva.

**Archivo:** `app/src/main/java/com/example/tradedraw/AutoTradeAccessibilityService.kt`

---

## 2. Estado inicial verificado

| Comprobación | Resultado |
| :--- | :--- |
| `git status` antes de tocar | `M CLAUDE.md`, `M TRADEDRAW_CURRENT_STATE.md`, `M AutoTradeAccessibilityService.kt`, `M HealthSupervisor.kt`, `M HealthSupervisorTest.kt` + 3 `??` en `docs/agents/` + 5 `??` en `loops/r27_validation/` |
| Cambios preexistentes | **Preservados**, ninguno revertido |

---

## 3. Hallazgos con referencia de código

| # | Línea (antes) | Hallazgo |
| :--- | :--- | :--- |
| H1 | `:386` `onServiceConnected` → `instance = this` | Promueve la instancia |
| H2 | `:423` `onRebind` → `instance = this` | Vuelve a promover tras rebind |
| H3 | `:462` `onUnbind` → `return true` | **No libera la referencia.** Entre unbind y rebind, 25 puntos del código seguían consultando un servicio desenlazado |
| H4 | `:471` `onDestroy` → `if (instance == this) instance = null` | La guarda por identidad **ya era correcta**, pero no estaba cubierta por ninguna prueba |
| H5 | `:18` `var instance` | `var` público con `private set`, sin ningún invariante expresable |

**Gravedad:** los consumidores usan `instance` para `performClickAt`, `readCurrentBalance`,
`rootInActiveWindow` y comprobaciones de disponibilidad. Una instancia desenlazada no recibe
eventos de accesibilidad, así que las lecturas devuelven datos congelados y los gestos pueden no
despacharse, sin que ningún consumidor lo note.

---

## 4. Decisión técnica

Se extrae el invariante a una clase genérica **pura**, `ActiveInstance<T>`, en lugar de escribir
la lógica inline en el servicio:

1. **Testeabilidad:** el ciclo de vida real no se puede reproducir bajo las restricciones vigentes
   (exigiría desenlazar el servicio). Una clase pura permite demostrar el invariante con JVM.
2. **Un solo sitio:** la regla "una instancia vieja nunca libera una nueva" queda en un lugar
   auditable.
3. **API intacta:** `instance` sigue siendo una propiedad con `private set`, así que los 26 usos
   existentes compilan sin cambios.

### Cambios

| Archivo | Cambio |
| :--- | :--- |
| `ActiveInstance.kt` (**nuevo**) | Holder con `promote` / `release` / `current`. `release` compara por **identidad** (`===`) |
| `AutoTradeAccessibilityService.kt` | `instance` delega en `activeRef`; nuevos `attach()` / `detach()`; `onUnbind` ahora llama `detach(this)`; `onDestroy` usa `detach(this)` en vez de la comparación inline |

**Deliberadamente NO se tocó:** el registro del `commandReceiver`, los permisos del receptor
(`com.example.tradedraw.permission.INTERNAL_COMMAND`), `performClickAt`, `performSwipe`,
`readCurrentBalance`, ni el throttle de escaneo de saldo.

---

## 5. Pruebas

`app/src/test/java/com/example/tradedraw/ActiveInstanceTest.kt` — **13 pruebas, 0 fallos.**

| Escenario | Verifica |
| :--- | :--- |
| `sin promocion no hay instancia activa` | Estado inicial |
| `promover publica la instancia` | `onServiceConnected` |
| `promover dos veces la misma instancia es idempotente` | Repetición segura |
| `promover una instancia nueva sustituye la anterior` | `onRebind` con nueva instancia |
| `liberar la instancia activa la deja en null` | **Corrección de H3** |
| `liberar cuando no hay nada no rompe` | Robustez |
| `una instancia vieja no puede borrar una nueva` | **Invariante central** |
| `liberar dos veces la misma instancia solo funciona la primera` | Idempotencia de release |
| `secuencia connect - unbind - rebind deja la instancia activa` | Ciclo nominal completo |
| `secuencia connect - unbind - destroy mantiene el estado limpio` | Ciclo de cierre |
| `recreacion de servicio con destroy fuera de orden conserva la instancia viva` | Carrera real |
| `liberar una instancia nunca promovida no afecta a la activa` | Intruso |
| `el getter de instance refleja el holder` | Contrato de los 26 usos |

Comando:
```
./gradlew testDebugUnitTest --tests '*ActiveInstanceTest*' --no-daemon --no-configuration-cache
```
Resultado real: `BUILD SUCCESSFUL` · XML: `tests="13" failures="0" errors="0"`.

---

## 6. `git diff --check`

Sin avisos en este archivo. Diff: `+51 / −7` en `AutoTradeAccessibilityService.kt`.

---

## 7. Limitaciones de validación

**PARCIAL.** Lo que sí está demostrado: el invariante lógico, con 13 pruebas deterministas offline.

Lo que **no** está demostrado:

| Aspecto | Por qué | Estado |
| :--- | :--- | :--- |
| `onUnbind`/`onRebind` reales en Android | Requiere desenlazar el servicio de accesibilidad del dispositivo. Prohibido por las restricciones (§3) | **PENDIENTE** |
| Comportamiento con la instancia liberada mientras un consumidor la usa | Requiere ejecución en dispositivo | **PENDIENTE** |
| Instalación del cambio | Prohibido sin autorización expresa (§3) | **BLOQUEADO** |

**No se declara validado en vivo.** El `dumpsys` del dispositivo corresponde al APK de R27-H, que
no contiene este código.

---

## 8. Riesgo residual declarado

Liberar la referencia en `onUnbind` cambia una suposición existente: antes, `instance` sobrevivía
al unbind. Un consumidor que dependiera de "instancia disponible aunque esté desenlazada" vería
ahora `null`.

**Evaluación:** se revisaron los 26 usos. Todos comprueban `instance != null` antes de usarla o
usan `?.` con un valor por defecto (`?: 0.0`, `?: return`). El único punto que combinaba ambas
condiciones (`instance != null || isAccessibilityPermissionGranted(this)`) sigue funcionando porque
el segundo término cubre el caso. No se encontró ningún consumidor que se rompa.

Si se prefiere el comportamiento anterior, el cambio es una línea (`quitar detach(this)` de
`onUnbind`). **No se hizo porque el estado obsoleto es justo el fallo auditado.**

---

## 9. Siguiente acción recomendada

Con autorización expresa: compilar, instalar, y reproducir el ciclo en dispositivo con
`adb shell settings put secure enabled_accessibility_services` (quitando y reponiendo TradeDraw) y
observar `/status` → `accessibilityActive` y las lecturas de balance durante la ventana.
