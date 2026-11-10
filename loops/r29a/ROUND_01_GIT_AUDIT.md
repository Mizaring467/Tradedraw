# ROUND 01 — Auditoría de integridad (Git) · TradeDraw R29-A

**Fecha:** 2026-11-09T07:03–07:07Z · **Ejecutado por:** DeepInvestigator (subagente de auditoría, sesión Antigravity visible)
**Alcance:** solo lectura sobre el repo. Única escritura: este archivo. Sin commits, sin comandos destructivos, sin reversiones, sin ADB/dispositivo, sin AUTONOMOUS, sin broker.
**Revisión del supervisor (07:40Z) — CORRECCIÓN E3:** cifras por-archivo recalculadas con `git diff --numstat` (ATAS +51/−7, Bridge +4/−1, HealthSupervisor +164/−47; HealthSupervisorTest +277/−52 y OverlayService +33 correctos). Los totales +582/−109 SÍ cuadran; el error era solo por-archivo (se habían sumado adiciones+eliminaciones).

---

## 1. Estado Git: rama, HEAD, origen y posición de `main` — [VERIFICADO]

| Comprobación | Comando | Resultado |
| :--- | :--- | :--- |
| Rama | `git rev-parse --abbrev-ref HEAD` | `r10-night` |
| HEAD | `git rev-parse HEAD` | `c934835b7490caea0807c825aba664fa0b148c20` |
| HEAD == origin/r10-night | `git rev-parse origin/r10-night` | **IDÉNTICO** a HEAD (en sincronía) |
| Stash | `git stash list` | vacío |
| `main` | `git rev-parse main` | `4d433ed54bbc0c8541b64a05ef122079ca0d11b9` |
| Relación main ↔ r10-night | `git rev-list --left-right --count main...r10-night` | `0  212` — `main` es **ancestro estricto**: 0 commits por delante, `r10-night` 212 por delante. El merge-base ES `main` (`git merge-base` = `4d433ed`) |
| HEAD sin mover | `git log --oneline -1` al cierre | `c934835` — sin commits en esta ronda |

- `main` **no contiene** R21–R26 (212 commits de distancia); la línea de trabajo vive en `r10-night`. Coherente con `TRADEDRAW_CURRENT_STATE.md:24`.
- Historial reciente (últimos 15): R24→R26 (docs de cierre R25 `NO EVALUABLE por V5`, caducidad de AUTONOMOUS P1/P2, evidencia de reconexión). Nada posterior a R26 en el historial.
- Working tree al inicio de esta ronda: **9 modificados + sin-rastrear** idénticos a los inventariados en `ROUND_00_REPORT.md` §2.5 → sin edición concurrente detectada durante R0→R1. [VERIFICADO — `git status --porcelain`]

## 2. Contenido real de los 9 diffs HEAD ↔ working tree — [VERIFICADO — `git diff`]

Total: 582 inserciones / 109 eliminaciones (coincide con Ronda 0).

### 2.1 Documentación (3 archivos — solo aditivos)
| Archivo | Δ | Contenido del cambio |
| :--- | :--- | :--- |
| `AGENTS.md` | +14 | Bloque **"AUTORIZACIÓN DE PRUEBAS EN BINOMO (CUENTA DEMO)"** (2026-11-05, autorización del propietario; prevalece sobre reglas previas; mantiene el guard de cuenta REAL). Solo documental. |
| `CLAUDE.md` | +16 | Sección "Agent skills": issue tracker (`gh`, `Mizaring467/Tradedraw`), triage labels, domain docs — **referencia a los archivos sin rastrear `docs/agents/*.md`**; de sus 3 rutas referenciadas solo 1 existe hoy (`docs/agents/` ✔; `GLOSSARY.md` ✖; `docs/adr/` ✖ — aspiracional, no actual). |
| `TRADEDRAW_CURRENT_STATE.md` | +21 | Dos bloques de actualización aditivos: "Actualización 2026-11-05 (R27-H)" en `:108` y "(R27-H loops)" en `:119`. No borra contenido previo. |

### 2.2 Build (1 archivo)
| Archivo | Δ | Contenido |
| :--- | :--- | :--- |
| `app/build.gradle.kts` | 2/2 | `versionCode 20→21`, `versionName "1.10-r26-autonomous-expiry" → "1.11-r27h-activeinstance-atomic"`. Bump **sin justificación documentada** (detectado y registrado por R28A §7; el sufijo mezcla numeración R27-H/R28). No afecta comportamiento en runtime. |

### 2.3 Código Kotlin (5 archivos — diagnóstico y ciclo de vida, NO estrategia)
| Archivo | Δ | Contenido del cambio |
| :--- | :--- | :--- |
| `AutoTradeAccessibilityService.kt` | +51/−7 | **R27-H/L1 + R27/C.1**: (a) el `var instance` estático se sustituye por `ActiveInstance` con `attach`/`detach` (liberación por identidad atómica); `onUnbind` ahora libera la referencia y `onDestroy` usa `detach` (evita que un destroy tardío borre una instancia nueva). (b) La ruta pasiva de balance (`onAccessibilityEvent`) ahora estampa `lastBalanceReadMs`, que antes no se estampaba → causaba `BALANCE_NOT_FRESH` permanente espurio. |
| `OverlayService.kt` | +33 | **R27-H/L2**: campo autoritativo `dataSource` + `setDataSource()`; en `onStartCommand` declara explícitamente `WEBSOCKET_PURO` (la captura de pantalla está deshabilitada por producto). Solo alimenta el diagnóstico. |
| `TradeDrawHttpBridge.kt` | +4/−1 | **R27-H/L2**: pasa `dataSource = overlayService.dataSource` explícitamente al evaluar health en `GET /status`. |
| `health/HealthSupervisor.kt` | +164/−47 | **R27/A+C**: nuevo `enum DataSource`, `Snapshot` inmutable y `evaluateFromSnapshot` **pura** (testeable sin Android); `SCREEN_FEED_MISMATCH` pasa a `notEvaluable` cuando no hay fuente de pantalla (WebSocket puro) en vez de bloquear estructuralmente; balance distingue `AUSENTE`/`INVALIDO`/`STALE`; `BALANCE_NOT_FRESH` → `BALANCE_STALE` con ventana `BALANCE_FRESH_WINDOW_MS` (holgura de medición sobre cadencia de 5000 ms). El propio diff declara que el guard real de trading (`TradingEngine.isBalanceFresh`, 3000 ms) **no se toca**. `Report.toJson()` añade `dataSource` y `notEvaluable`. |
| `health/HealthSupervisorTest.kt` | +277/−52 | Reescrito sobre `Snapshot` (20 testcases en XML on-disk; 26→20 @Test respecto a HEAD: accesibilidad, screen/feed por modo, balance ausente/inválido/obsoleto/duplicación, recuperación, JSON, `sinceMs`). |

### 2.4 ¿Alteran estrategias, parámetros estratégicos o resultados históricos? — **NO** [VERIFICADO]
- `git diff --name-only | grep -iE 'TradingEngine|RiskManager|Strateg|Signal|Confluence|Inoperab'` → **vacío** (ningún archivo de estrategia/modo de ejecución modificado).
- El diff de `HealthSupervisor.kt` documenta explícitamente que el supervisor solo alimenta `GET /status` (`OverlayService.kt:114`, `TradeDrawHttpBridge.kt:619`) y que los guards del motor no se tocan.
- **Riesgo a documentar (regla 15):** los cambios SÍ alteran comportamiento observable en dos capas no-estratégicas: (1) ciclo de vida de la referencia de accesibilidad (onUnbind/onDestroy ahora liberan la instancia), y (2) el informe de health de `/status` (nuevos campos JSON, reclasificación de bloqueos espurios a `notEvaluable`, `BALANCE_STALE` como DEGRADED). Son coherentes con el trabajo declarado R27/R27-H y con la desviación declarada en `TEST_MATRIX.md` §3 (bloqueo→degradación del balance obsoleto, decisión documentada y no autorizada a revertir).
- **Riesgo regla-15 adicional (insumos del guard del motor):** aunque ningún archivo del motor está diffado, `TradingEngine.kt:2675-2680` consume `AutoTradeAccessibilityService.instance` y `lastBalanceReadMs` para `isBalanceFresh` en la liquidación. El diff cambia la **escritura** de `lastBalanceReadMs` (ahora también en la ruta pasiva, hunk 445 de `AutoTradeAccessibilityService.kt`) y la semántica de `instance` (detach en `onUnbind`). Impacto posible en la ruta de trading: (a) ventanas unbind→rebind operan ahora con `instance=null` (antes podían operar sobre un servicio desenlazado — la corrección es correcta, pero cambia el insumo del guard); (b) el umbral de frescura de liquidación (3000 ms) puede pasar de "nunca fresco" a "fresco" con datos del escaneo pasivo. Coherente con la intención declarada (los falsos positivos eran el bug), a tratar explícitamente en la Ronda 3.

## 3. Archivos sin rastrear: contenido y atribución — [VERIFICADO — lectura directa]

| Elemento | Contenido / atribución | Referenciado por docs |
| :--- | :--- | :--- |
| `app/src/main/java/.../ActiveInstance.kt` | Clase genérica con `AtomicReference` + `compareAndSet` (promote/release por identidad). R27-H L1; versión atómica reescrita por "agente previo" según R28A §2. | Sí: diffs de `AutoTradeAccessibilityService.kt`, `LOOP1_ACCESIBILIDAD.md`, `R28A_CIERRE.md` |
| `app/src/test/.../ActiveInstanceTest.kt` (424 líneas) | 20 pruebas (XML on-disk: 20 testcases). Incluye la prueba de concurrencia corregida en R28A §3 (latches, exige ≥1 lectura). | Sí: R28A §3-4 |
| `app/src/test/.../BalanceFreshnessSeparationTest.kt` (184 líneas) | R27-H L3: separación diagnóstico (10 s/5 s) vs guard del motor (3 s). | Sí: `LOOP3_BALANCE.md`, `TRADEDRAW_CURRENT_STATE.md:119` |
| `app/src/test/.../HealthDataSourceTest.kt` (187 líneas) | R27-H L2: fuente de datos explícita. | Sí: `LOOP2_FUENTE_DATOS.md` |
| `docs/agents/` (3 md: domain, issue-tracker, triage-labels) | Guías de agentes; referenciadas por el diff de `CLAUDE.md`. Ronda anterior a R27 (protegidas por `ITERATION_LOG.md` de R27). | Sí: `CLAUDE.md` (working tree) |
| `loops/r27_validation/` (5 archivos) | Informe R27 (`R27_REPORTE.md`: "COMPLETADO CON UNA DESVIACIÓN DECLARADA"), causas raíz, matriz de pruebas, `kotlin_test_summary.txt`, log de iteración. | Sí: `TRADEDRAW_CURRENT_STATE.md:108` |
| `loops/r27h_loops/` (6 archivos) | L1–L4 + `SIGUIENTE_AUDITORIA.md` (candidatos L5+, "Nada de esto está implementado") + log. | Sí: `TRADEDRAW_CURRENT_STATE.md:127` |
| `loops/r28a_verification/R28A_CIERRE.md` | Cierre R28-A: ActiveInstance ya correcta (no se tocó); se corrigió 1 prueba vacía; suite 573/0/2 con horas. | **NO** — `TRADEDRAW_CURRENT_STATE.md` no menciona R28 (grep sin coincidencias) |
| `loops/r28b_autonomy/R28B_BLOQUEO_AISLAMIENTO.md` | Bloqueo arquitectónico: autonomía E2E **NO demostrable** (feed real del broker + `dispatchGesture` real + sin simulador de órdenes); recomienda `OrderGateway`+`SimulatedOrderGateway` (R29). | **NO** — ídem |
| `scripts/td_supervisor.{sh,lock,log}` | Supervisor ADB del bot (Poco X6 Pro). `sh` = 293 líneas con AUTO_REARM. `lock` vacío (2026-11-07 13:19, obsoleto). `log` termina `2026-11-07T18:27:41Z "RECUPERACION: bridge arriba"`. | Solo Ronda 0 (inactivo ≥1,5 días) |
| `loops/r29a/` (5 archivos) | Workspace de esta misión (creado en Ronda 0). | `STATE.md` |

## 4. Informes R27 y R28: qué afirman y qué quedó pendiente — [VERIFICADO — lectura directa]

- **R27** (`loops/r27_validation/R27_REPORTE.md`): los 3 bloqueos de R26 eran 2 falsos positivos estructurales del diagnóstico (`SCREEN_FEED_MISMATCH` por comparar sin modo de datos; `ACCESSIBILITY_DOWN` no reproducido en `duchamp`) + 1 fallo real de instrumentación (`lastBalanceReadMs` no estampado en ruta pasiva). Verificado en dispositivo: `health READY` con `notEvaluable:["SCREEN_FEED_MISMATCH"]`. Desviación declarada: balance obsoleto = `DEGRADED/BALANCE_STALE`, no bloqueo (justificada en `TEST_MATRIX.md` §3 porque el guard real vive en `TradingEngine`, 3000 ms). Sin commit (prohibido por el encargo).
- **R27-H loops** (`loops/r27h_loops/`): L1 ciclo de vida (ActiveInstance), L2 fuente de datos explícita, L3 verificación de separación (sin cambios de código), L4 regresión **566 tests / 0 fallos** (claim). Pendiente: candidatos L5+ listados, nada implementado.
- **R28-A** (`R28A_CIERRE.md`): "CIERRE CON UNA CORRECCIÓN". La implementación atómica de `ActiveInstance` ya cumplía (no se modificó); se corrigió una prueba de concurrencia **vacua** (podía pasar con 0 lecturas) mediante latches deterministas y exigencia de ≥1 lectura. Suite completa **573 tests, 0 fallos, 2 skipped** (2 ejecuciones con hora). Hallazgos adicionales: bump de versión sin justificación (§7), CRLF íntegro en `OverlayService.kt` (2466 líneas; `git diff --check` → 66 líneas de salida: 33 líneas marcadas + 33 avisos de trailing whitespace, benignos; sin `.gitattributes`; el «34» de R28A §6 era un conteo impreciso).
- **R28-B** (`R28B_BLOQUEO_AISLAMIENTO.md`) — **leído con atención, es el documento clave para R29-B**: veredicto **NO** — TradeDraw genera decisiones autónomas (probado, SHADOW, 573 tests JVM) pero **no puede completar una orden simulada**: no existe simulador de órdenes ni abstracción (`grep` sin interfaces Broker/Executor/OrderGateway); la única vía de ejecución es `dispatchGesture` (taps reales, `TradingEngine.kt:2551-2566` → `AutoTradeAccessibilityService.kt:576`); el feed conecta a `wss://as.binomo.com` con token real de 36 caracteres; "DEMO" es una etiqueta leída del broker (`isDemoAccount`), no un sandbox. SHADOW termina en la decisión **por diseño** (no marca orden, no altera `hasPendingTrade`). AUTONOMOUS **no** se activó durante esa ronda. **Recomienda** abrir la ronda de arquitectura (OrderGateway + SimulatedOrderGateway + fuente de datos inyectable + entorno explícito vs flag del broker).
- **Pendientes declarados heredados**: (a) commit de todo el trabajo R27/R28; (b) validación en dispositivo del working tree — el APK instalado sigue siendo v20 (R28A §7.2, LOOP4:135); (c) ronda de trading V5 sin consumir (`TRADEDRAW_CURRENT_STATE.md:115-117`); (d) L5+; (e) R29-A/R29-B (OrderGateway) — esta misión.

## 5. Pruebas: estado declarado vs evidencia en disco — [PARCIALMENTE VERIFICADO]

| Fuente | Claim | Evidencia independiente |
| :--- | :--- | :--- |
| `loops/r27_validation/kotlin_test_summary.txt` | 2026-11-05T20:34Z · HEAD c934835 · clases=55 tests=528 skipped=2 failures=0 | Contenido del archivo [VERIFICADO]; snapshot histórico, no reproducible ahora sin ejecutar Gradle |
| `TEST_MATRIX.md` | 11/11 escenarios, 10 PASS + 1 PARCIAL justificado; health dirigida 20/20; Python 43 OK | Contenido leído [VERIFICADO como claim documentado]; la desviación está argumentada con código |
| `LOOP4_REGRESION.md` | 566 tests / 0 fallos / 58 clases | Claim; los XML actuales lo **superan** (573), coherente con la progresión 566→573 de R28A |
| **R28A §4** | 573 tests / 58 clases / 0 fallos / 2 skipped | **VERIFICADO en disco HOY**: re-agregado de `app/build/test-results/testDebugUnitTest/*.xml` (59 XML) → `clases=58 tests=573 skipped=2 failures=0 errors=0`; `ActiveInstanceTest` contiene 20 testcases. No re-ejecuté la suite (ronda read-only; ejecutarla regeneraría artefactos) |
| Suite Python 43 tests | Claim en R27/LOOP4 | **SIN VERIFICAR por ejecución** — artefacto localizado parcialmente: existen `tests/` (6 .py) y `validation_engine/`; el comando declarado en `TEST_MATRIX.md:77` es reproducible, pero no hay artefacto de resultados en disco |

## 6. Inconsistencias documentación ↔ código — [VERIFICADO]

1. **"Repo limpio" es falso hoy** — `TRADEDRAW_CURRENT_STATE.md:24`: *"Repo: limpio, r10-night empujado"* vs 9 modificados (+582/−109) y ~15 rutas sin rastrear. Ya detectado en Ronda 0; corrección aditiva planificada para Ronda 5.
2. **`TRADEDRAW_CURRENT_STATE.md:21` desfasado**: dice "APK v20 … suite Kotlin 513/513" mientras el working tree tiene `versionCode=21` y 573 tests. Además `LOOP4_REGRESION.md:135` e `ITERATION_LOG.md:91` documentan que el APK **instalado** es v20 y **no contiene** estos cambios.
3. **Informes R28 huérfanos**: `TRADEDRAW_CURRENT_STATE.md` no referencia ni `loops/r28a_verification/` ni `loops/r28b_autonomy/` (grep "R28" sin coincidencias) — existen solo como archivos sin rastrear.
4. **`CLAUDE.md` (working tree) referencia `docs/agents/*.md` sin rastrear**: si se commiteara solo el diff, las referencias quedarían rotas; deben commitearse juntos.
5. **`versionName "1.11-r27h-activeinstance-atomic"`** (mtime 22:12 del 2026-11-05, obra de un "agente previo" sin ronda documentada — R28A §7) mezcla numeración: el bump se documenta en R28-A. Pendiente de decisión del propietario al commitear.
6. **Actividad de AUTONOMOUS posterior a R28-B no reflejada en informes**: `scripts/td_supervisor.log` muestra arranques con `AUTO_REARM=1` y un re-arme real `{"success":true,"mode":"AUTONOMOUS"}` el **2026-11-06T08:08:55Z** (R28-B es del 2026-11-06 pero de la madrugada/tarde; el re-arme es posterior a la escritura del informe del 19:12 del 11-05). Actividad histórica (11-06/11-07, 345+33 entradas) del supervisor del propietario sobre el dispositivo demo; **no es actividad de esta misión** y no contradice el "AUTONOMOUS NO se activó" de R28-B para su ronda, pero **ningún informe de ronda documenta ese periodo**. Se registra como hueco documental.
7. **R28-B §5.1 cita texto normativo desactualizado**: R28B fundamentó parte de su bloqueo en *"No se autoriza operar en REAL ni conectar el flujo de ejecución a un broker real"*, pero el working tree ya contiene la autorización Binomo-demo del 2026-11-05 (`AGENTS.md`, diff +14) que "prevalece sobre cualquier encargo, preregistro o informe anterior". R28B (escrito el 11-06) no la mencionó. **No invalida su auditoría técnica** (la ausencia de simulador de órdenes es verificable y verificada), pero es una contradicción documental a integrar en Ronda 3/5.
8. **CLAUDE.md (working tree) referencia 3 rutas y solo 1 existe**: `docs/agents/` ✔ existe (3 md sin rastrear); `GLOSSARY.md` ✖ y `docs/adr/` ✖ no existen ("Layout de contexto único" es aspiracional). Si se commitea CLAUDE.md tal cual, 2 de 3 referencias quedarían rotas. Refuerza la inconsistencia 4.

## 7. Inventario de trabajo

| Elemento | Estado | Evidencia (comando / ruta:línea) |
| :--- | :--- | :--- |
| Ronda 0 (preparación) | TERMINADO | `loops/r29a/ROUND_00_REPORT.md` (veredicto APROBADA) |
| R27: corrección falsos positivos health + `lastBalanceReadMs` | TERMINADO (sin commit) | `git diff` HealthSupervisor/AutoTradeAccessibilityService; `kotlin_test_summary.txt`; `R27_REPORTE.md` §1 |
| R27: desviación declarada BALANCE_STALE=DEGRADED | TERMINADO (documentada) | `TEST_MATRIX.md` §3; diff de `HealthSupervisor.kt` |
| R27-H L1: ciclo de vida AccessibilityService | TERMINADO (sin commit) | `ActiveInstance.kt`; diff de `AutoTradeAccessibilityService.kt`; `LOOP1_ACCESIBILIDAD.md` |
| R27-H L2: fuente de datos explícita | TERMINADO (sin commit) | diffs OverlayService/TradeDrawHttpBridge; `LOOP2_FUENTE_DATOS.md` |
| R27-H L3: separación diagnóstico vs guard | TERMINADO (sin cambios de código) | `LOOP3_BALANCE.md`; `BalanceFreshnessSeparationTest.kt` |
| R27-H L4: regresión 566/0 | TERMINADO (claim) | `LOOP4_REGRESION.md` §1 |
| R28-A: corrección prueba vacía + cierre con evidencia | TERMINADO | `R28A_CIERRE.md` §3–4; XML on-disk 573/0/2 [VERIFICADO hoy] |
| R28-B: bloqueo por aislamiento no demostrable | TERMINADO (informe; bloqueo declarado) | `R28B_BLOQUEO_AISLAMIENTO.md` (Veredicto + §9) |
| Commit del trabajo R27/R28 | PENDIENTE | `git status` (prohibido sin autorización — regla 10) |
| Validación en dispositivo del working tree (APK v21) | PENDIENTE / NO VERIFICADO | `R28A_CIERRE.md` §7.2; `LOOP4_REGRESION.md:135` (instalado = v20) |
| V5: preregistro + ≥20 trades | PENDIENTE | `TRADEDRAW_CURRENT_STATE.md:115-117` |
| L5+ (candidatos de auditoría) | PENDIENTE | `loops/r27h_loops/SIGUIENTE_AUDITORIA.md` ("Nada de esto está implementado") |
| R29-A (esta misión) | EN CURSO | `loops/r29a/STATE.md` (ronda 1) |
| OrderGateway + SimulatedOrderGateway (recomendación R28-B) | PENDIENTE (motiva R29-B) | `R28B_BLOQUEO_AISLAMIENTO.md` §4.1–4.5, §8 |
| `versionCode=21` (bump sin ronda documentada) | SIN VERIFICAR (origen) | `R28A_CIERRE.md` §7; diff de `app/build.gradle.kts` |
| `td_supervisor.sh` (AUTO_REARM=1) | INTERRUMPIDO | `td_supervisor.lock` (vacío, 11-07 13:19); log termina 2026-11-07T18:27:41Z |
| Suite Python (43 tests) | SIN VERIFICAR | solo claims en `TEST_MATRIX.md` §4 / `LOOP4` §1; sin artefacto local |

## 8. Riesgos de integridad

1. **ALTO — persistencia**: el trabajo R27/R28 (+582/−109 en 9 archivos + ~800 líneas de tests + 15 rutas de informes) existe **solo en el working tree**: sin commit, sin stash, sin push. Cualquier operación destructiva lo perdería de forma permanente. Regla 8 lo mitiga; el riesgo persiste hasta que el propietario autorice commit.
2. **MEDIO — edición concurrente**: `opencode` activo (1 proceso visible en `ps` al cierre; R0 observó 2). Re-verificar `git status` antes de cualquier escritura fuera de `loops/r29a/`.
3. **MEDIO — reactivación no intencional de AUTONOMOUS**: `td_supervisor.sh` tiene `AUTO_REARM=1` por defecto documentado y evidencia de re-arme real el 11-06. Si alguien lo relanzara, re-armaría AUTONOMOUS en el dispositivo demo. Esta misión no debe ejecutarlo; lock obsoleto no se elimina (regla 7).
4. **BAJO — CRLF**: `OverlayService.kt` es CRLF íntegro (2466 líneas) sin `.gitattributes` → ruido de diff (34 avisos de trailing whitespace). No normalizar fuera de una ronda autorizada.
5. **BAJO — documentación desfasada**: `CURRENT_STATE` §1 (repo limpio / 513 tests / v20) contradice el árbol; corrección aditiva en Ronda 5.
6. **CONFIRMADO (no riesgo nuevo)**: ningún diff toca estrategias, parámetros estratégicos, guards del motor (`TradingEngine`/`RiskManager` ausentes de `git status`) ni resultados históricos [VERIFICADO — grep sobre `git diff --name-only`].

## 9. Criterios de aceptación de la ronda

- [x] Rama, HEAD, igualdad con `origin/r10-night`, stash y posición de `main` verificados con comandos.
- [x] Contenido real de los 9 diffs examinado (no solo el diffstat) y clasificado.
- [x] Ausencia de cambios en estrategias/parámetros/resultados verificados negativamente (grep sobre archivos diffados).
- [x] Archivos sin rastrear inspeccionados y atribuidos a rondas; referencias cruzadas documentales comprobadas.
- [x] Informes R27/R27-H/R28-A/R28-B leídos; claims contrastados con artefactos on-disk (XML 573/0/2 = claim de R28A).
- [x] Inventario de trabajo clasificado (TERMINADO/PENDIENTE/INTERRUMPIDO/SIN VERIFICAR) con evidencia.
- [x] 8 inconsistencias documentales enumeradas (6 del subagente + 2 incorporadas por la revisión del supervisor).
- [x] Cero commits, cero comandos destructivos, cero reversiones, cero contactos con dispositivo/broker. Única escritura: este informe.
- [x] Cada afirmación etiquetada VERIFICADO / PARCIALMENTE VERIFICADO / NO VERIFICADO.

## 10. Veredicto

**RONDA 1 COMPLETADA — APROBADA.** El working tree es íntegro y explicable al 100%: los 9 diffs y todos los elementos sin rastrear corresponden al trabajo documentado R27 / R27-H / R28-A / R28-B más el workspace R29-A; **ningún cambio toca estrategias, parámetros estratégicos ni resultados históricos**; el estado de pruebas declarado (573 tests / 0 fallos) está corroborado por los XML de resultados en disco. Los bloqueos y pendientes declarados por R28-B (ausencia de simulador de órdenes / OrderGateway) quedan correctamente heredados como motivo de R29-B. El riesgo dominante es la **persistencia**: todo vive solo en el working tree. Transición automática a **Ronda 2 (inventario técnico)**.
