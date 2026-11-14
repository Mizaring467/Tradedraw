# SIGUIENTE AUDITORÍA — candidatos a Loop 5+

**Fecha:** 2026-11-05 · **Base:** `c934835` + cambios de R27-H y R27-H loops (sin commit)

Auditoría de solo lectura. **Nada de esto está implementado.** Ordenado por relación
impacto/riesgo, con evidencia de por qué cada uno merece un loop.

---

## Numeración propuesta

| Identificador | Estado | Contenido |
| :--- | :--- | :--- |
| **R27** | **RESERVADO — no consumir** | Evaluación V5: ≥20 trades live. Requiere `PREREGISTRO_R27.md` commiteado **antes** de medir |
| **R27-H** | **CERRADA** | Health state machine: los tres bloqueos de R26 |
| **R27-H/L1–L4** | Esta ronda | Accesibilidad, fuente de datos, balance, regresión |
| **R27-H/L5+** | **PROPUESTA** | Los candidatos de abajo |

**No hay colisión:** `R27-H` es un sufijo del reservado, y `loops/r27_validation/` (R27-H) y
`loops/r27h_loops/` (estos loops) son carpetas distintas. Las siguientes rondas deberían usar
`R28`, `R29`… para no acercarse al nombre reservado.

---

## Candidato 1 — 25 `catch (e: Exception) {}` silenciosos

**Evidencia:** `grep -rn "catch (e: Exception) {}" app/src/main/java/` → **25 coincidencias**.

**Por qué importa:** son los puntos donde un fallo desaparece sin rastro. En R27-H ya aparecieron
dos consecuencias medibles de este patrón: el estampado de `lastBalanceReadMs` y la pérdida del
bridge. Un `catch` vacío convierte un error de diagnóstico en un estado "normal".

**Riesgo:** bajo. Sustituir `{}` por `Log.w(...)` no cambia comportamiento.

**Cuidado:** no añadir logging dentro de bucles calientes (escaneo de accesibilidad, por frame) sin
throttle, o la cura será peor.

---

## Candidato 2 — `OverlayService.kt` es CRLF con el resto del repo en LF

**Evidencia:** 2466 de 2466 líneas CRLF; `MainActivity.kt` y `RiskManager.kt` en LF; no hay
`.gitattributes`.

**Por qué importa:** `git diff --check` da falsos positivos en cada línea añadida a ese archivo, lo
que erosiona la señal de esa comprobación para todo el repo.

**Riesgo:** medio (diff masivo). Requiere un commit dedicado solo a normalizar.

---

## Candidato 3 — `TradingEngine.kt` con 2970 líneas

**Evidencia:** el archivo más grande del proyecto; contiene decisión, liquidación, journal,
métricas y settle.

**Por qué importa:** es el corazón de la lógica y concentra los guards. Extraer módulos puros
(como se hizo con `HealthSupervisor.Snapshot`) es lo que permitió, en esta ronda, probar estados que
antes eran inalcanzables en test.

**Riesgo:** **alto** si se intenta de una vez. Recomendación: extraer solo funciones puras ya
probadas, una por loop, sin mover lógica de decisión.

**Nota:** no tocar mientras no haya validación en dispositivo de los cambios actuales.

---

## Candidato 4 — Código muerto declarado: `ScreenCaptureManager`

**Evidencia:** `OverlayService.kt:87-92` lo declara deshabilitado; `ScreenCaptureManager(` tiene
**0 instanciaciones** en `app/src/main`.

**Por qué importa:** una clase completa más su test que no se ejecuta nunca, y un parámetro
`hasMediaProjection` que solo alimenta el texto de una notificación.

**Riesgo:** bajo, pero **requiere decisión de producto**: borrarlo cierra la puerta a reactivar la
visión. El propio comentario dice "cuando se confirme que la visión no volverá".

---

## Candidato 5 — Evicción del set de deduplicación (`RiskManager`)

**Evidencia:** `TRADEDRAW_CURRENT_STATE.md` §10.2 — `liquidatedTradeIds` se vacía entero al llegar a
512 claves (política `ponytail`, `RiskManager.kt:493`).

**Por qué importa:** un `tradeId` repetido tras la evicción volvería a contar. Riesgo teórico
(512 liquidaciones en una sesión) pero es el tipo de defecto que corrompe contabilidad en silencio.

**Riesgo:** alto si se toca mal (es el mecanismo que garantiza "1 evento → 1 incremento", certificado
en R26 con 10 pruebas). Cualquier cambio debe mantener esas 10 pruebas en verde.

---

## Candidato 6 — `PREREGISTRO_R27.md` (V5)

**Evidencia:** no existe; `ls workflows/PREREGISTRO_R*.md` termina en R26.

**Por qué importa:** es el único pendiente estructural de R25 y el único camino a evidencia de
rendimiento. **No es un loop de código**: es documental, y no requiere dispositivo.

**Riesgo:** **proceso**, no técnico. La lección F-2 de R26 fue que un preregistro commiteado
*después* de medir invalida la ronda.

---

## Lo que NO se recomienda como siguiente loop

| Idea | Por qué no |
| :--- | :--- |
| Reactivar la captura de pantalla | Decisión de producto pendiente; además exigiría permisos y encarece el diagnóstico |
| Unificar los umbrales de frescura (3000/10000 ms) | **Contradice el Loop 3**: son preguntas distintas y hay una prueba que lo fija |
| Bloquear `BALANCE_STALE` en el diagnóstico | No aporta seguridad (el health no decide) y rompe el contrato de R21/R23K sin autorización |
| Medir V5 ahora | Sin preregistro y sin muestra; violaría la lección F-2 |

---

## Bloqueos que afectan a cualquier loop futuro

1. **Sin validación en dispositivo** desde R27-H loops: instalar requiere autorización expresa.
2. **Bridge en IPv6 loopback** (`[::1]:8080`): `curl 127.0.0.1:8080` devuelve vacío sin error.
3. **`am force-stop` revoca la accesibilidad** en este HyperOS: nunca usarlo para reiniciar la app.
4. **Batería al 33 %** al cierre de esta ronda.
5. **`adb install` (streamed) falla** con `INSTALL_FAILED_USER_RESTRICTED`; usar `adb push` +
   `pm install -r -d`.
