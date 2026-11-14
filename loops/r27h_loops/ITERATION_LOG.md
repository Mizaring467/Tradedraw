# R27-H Loops — Registro de iteraciones

**Ronda:** continuación de R27-H · **Fecha:** 2026-11-05
**Rama:** `r10-night` · **HEAD:** `c934835` · **Sin commits** (prohibido sin autorización)
**Agente:** OpenCode · **Modo:** DEMO/DISABLED en todo momento

---

## Estado inicial verificado (antes de tocar nada)

| Comprobación | Resultado |
| :--- | :--- |
| Rama / HEAD | `r10-night` / `c934835` |
| Modificados | `CLAUDE.md`, `TRADEDRAW_CURRENT_STATE.md`, `AutoTradeAccessibilityService.kt`, `health/HealthSupervisor.kt`, `health/HealthSupervisorTest.kt` |
| Sin seguimiento | `docs/agents/{domain,issue-tracker,triage-labels}.md` + `loops/r27_validation/*` (5) |
| Stash | vacío |
| ADB | real 35.0.2, `emulator-5554` |
| Accesibilidad | TradeDraw en `Bound services` |
| Batería | 37 % |

**Cambios preexistentes preservados:** ninguno revertido ni sobrescrito.

---

## Loops ejecutados

| Loop | Objeto | Cambio de código | Tests | Veredicto |
| :--- | :--- | :--- | :--- | :--- |
| **L1** | Ciclo de vida de `AccessibilityService` | `ActiveInstance.kt` (nuevo) + `AutoTradeAccessibilityService.kt` | **13** | **PARCIAL** — lógica probada, ciclo real no reproducido |
| **L2** | Fuente de datos del `HealthSupervisor` | `OverlayService.kt` + `TradeDrawHttpBridge.kt` | **12** | **PARCIAL** — lógica probada, sin validación en dispositivo |
| **L3** | Semántica de `BALANCE_STALE` | **ninguno** (verificación) | **13** | **COMPLETO** |
| **L4** | Regresión global | ninguno | suite 566 | **COMPLETO en JVM**, dispositivo BLOQUEADO |

---

## Archivos modificados

**Producción**
- `app/src/main/java/com/example/tradedraw/ActiveInstance.kt` *(nuevo)*
- `app/src/main/java/com/example/tradedraw/AutoTradeAccessibilityService.kt`
- `app/src/main/java/com/example/tradedraw/OverlayService.kt`
- `app/src/main/java/com/example/tradedraw/TradeDrawHttpBridge.kt`

**Pruebas**
- `app/src/test/java/com/example/tradedraw/ActiveInstanceTest.kt` *(nuevo, 13)*
- `app/src/test/java/com/example/tradedraw/HealthDataSourceTest.kt` *(nuevo, 12)*
- `app/src/test/java/com/example/tradedraw/BalanceFreshnessSeparationTest.kt` *(nuevo, 13)*

**Documentación**
- `loops/r27h_loops/{LOOP1_ACCESIBILIDAD,LOOP2_FUENTE_DATOS,LOOP3_BALANCE,LOOP4_REGRESION,SIGUIENTE_AUDITORIA,ITERATION_LOG}.md`

---

## Recuento final de pruebas

| Suite | Resultado real |
| :--- | :--- |
| Kotlin completa | **566 tests · 0 fallos · 2 skipped · 58 clases** |
| Python | 43 tests · OK |
| Nuevas en esta ronda | 38 (13 + 12 + 13) |

Evolución: 513 (R26) → 528 (R27-H) → **566** (esta ronda).

---

## `git diff --check`

Avisos **solo** en `OverlayService.kt`, por ser **CRLF íntegro preexistente** (2466/2466), no por
espacios reales. Verificado que `AutoTradeAccessibilityService.kt`, `TradeDrawHttpBridge.kt` y
`ActiveInstance.kt` están limpios.

---

## Protecciones verificadas

| Guard | Estado |
| :--- | :--- |
| `TradingEngine.isBalanceFresh` 3000 ms | INTACTO |
| `TradingEngine.motivoVetoPorDesajusteDeActivo` | INTACTO |
| `RiskManager.kt` | SIN CAMBIOS (`git diff --quiet`) |
| Estrategias / parámetros / ventanas | SIN CAMBIOS |
| Permisos del receptor de comandos | SIN CAMBIOS |
| `PREREGISTRO_R27.md` | NO CREADO (reservado a V5) |

---

## Estado del dispositivo al cierre

| Señal | Valor |
| :--- | :--- |
| APK instalado | `versionCode=20`, `1.10-r26-autonomous-expiry` — **es el de R27-H, no contiene estos cambios** |
| `/status` | Al inicio respondía; al cierre el bridge **no escuchaba** |
| `OverlayService` | **0** en `dumpsys activity services` (servicio caído) |
| Batería | 37 % → **33 %** |
| Órdenes ejecutadas | 0 (nunca se interactuó con el broker) |
| `AUTONOMOUS` | nunca activado |

---

## Lo que NO se hizo y por qué

| Acción | Restricción |
| :--- | :--- |
| Instalar el APK | §3: "No instalar APK ni reiniciar servicios sin autorización expresa" |
| Reiniciar `OverlayService` para recuperar el bridge | §3: "No reiniciar servicios" |
| Desenlazar la accesibilidad para probar `onUnbind` real | §3: alteraría servicios del dispositivo |
| Commit / push | §3 y §10: "No hagas commit ni push" |
| Normalizar CRLF de `OverlayService.kt` | Fuera del alcance mínimo; requeriría autorización |
| Bloquear `BALANCE_STALE` | Rompería contrato de R21/R23K sin autorización |

---

## Pendientes

1. **Validación en dispositivo de L1 y L2** — requiere autorización de instalación.
2. **`onUnbind`/`onRebind` reales** — requiere autorización para manipular accesibilidad.
3. **Decisión:** ¿bloqueo estricto de `BALANCE_STALE`?
4. **Decisión:** ¿`.gitattributes` + normalización de `OverlayService.kt`?
5. **Bridge caído al cierre** — investigar por qué el `OverlayService` dejó de estar activo (puede
   ser del sistema por batería; no verificado).
6. La ronda **R27 (V5)** sigue intacta y sin preregistro.

## Estado de Git final

```
M  CLAUDE.md                                    (preexistente, preservado)
M  TRADEDRAW_CURRENT_STATE.md                   (documentación)
M  AutoTradeAccessibilityService.kt             (L1)
M  OverlayService.kt                            (L2)
M  TradeDrawHttpBridge.kt                       (L2)
M  health/HealthSupervisor.kt                   (R27-H)
M  health/HealthSupervisorTest.kt               (R27-H)
?? ActiveInstance.kt                            (L1)
?? ActiveInstanceTest.kt                        (L1)
?? HealthDataSourceTest.kt                      (L2)
?? BalanceFreshnessSeparationTest.kt            (L3)
?? docs/agents/*                                (preexistente, preservado)
?? loops/r27_validation/*                       (R27-H)
?? loops/r27h_loops/*                           (esta ronda)
```
