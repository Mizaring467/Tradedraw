# TRADEDRAW_CURRENT_STATE

**Reconstruido por:** DSH (orquestador, sesión visible) · **Fecha:** 2026-10-27 22:55 (America/Bogota)
**Rama:** `r10-night` · **HEAD:** `75f005e` (sincronizado con `origin/r10-night`) · **`main`:** `4d433ed` (210 commits por detrás)
**Método:** git + código + tests ejecutados + artefactos + auditorías independientes.
**Regla aplicada:** ninguna afirmación de agente se aceptó como evidencia única.

Auditorías de pares integradas (copias versionadas en [`loops/r26_validation/auditorias/`](loops/r26_validation/auditorias/)):

| Auditoría | Ejecutor | Alcance | Informe |
| :--- | :--- | :--- | :--- |
| A | DSH | estado git/loops | `AUDIT_A_ESTADO.md` |
| B | **AGY** (panel herdr) | idempotencia del contador + test real | `AUDIT_B_IDEMPOTENCIA.md` |
| C | **OpenCode** (panel herdr) | evidencia R26 y coherencia temporal | `AUDIT_C_R26.md` |
| D | **Hermes** (panel herdr) | consistencia documental | `AUDIT_D_DOCS.md` |

---

## 1. Estado actual (una frase por dimensión)

- **Código:** `75f005e` compila; APK v20 `1.10-r26-autonomous-expiry` construida e instalada; suite Kotlin **513/513**.
  - ⚠️ **Corrección (R29-A, 2026-11-09):** este claim describe la reconstrucción R26 y quedó desfasado. Verificado hoy: el working tree contiene trabajo R27/R28 **sin commit** con `versionCode=21` / `versionName "1.11-r27h-activeinstance-atomic"` (`app/build.gradle.kts`), mientras la APK **instalada** sigue siendo v20 (no contiene esos cambios; `loops/r28a_verification/R28A_CIERRE.md` §7.2). La cifra de tests vigente es **565** (detalle en la sección "Actualización R29-A" del final). El texto original se conserva.
- **Instrumentación:** R25 certificó V1–V4; R26 certificó la caducidad de `AUTONOMOUS` (P1/P2).
- **Edge/estrategia:** **no demostrado**. R25 cerró `NO EVALUABLE (por V5)` y R26 no midió trading.
- **Repo:** limpio, `r10-night` empujado; `main` **no** contiene R25/R26.
  - ⚠️ **Corrección (R29-A, 2026-11-09):** **"Repo: limpio" es FALSO hoy** (era cierto en la fecha de la reconstrucción R26). `git status` verificado hoy: **9 archivos modificados (+582/−109) y 13 rutas sin rastrear** (30 archivos) — todo el trabajo R27/R27-H/R28-A/R28-B vive **solo en el working tree, sin commit** (`loops/r29a/ROUND_01_GIT_AUDIT.md` §2–3, §8). El texto original se conserva.
- **Dispositivo:** **inalcanzable por ADB desde las 22:50** (`No route to host`). Última lectura verificada: 22:25:58, `mode=DISABLED`.

---

## 2. Lo realmente completado

| Ronda | Veredicto verificado | Evidencia primaria |
| :--- | :--- | :--- |
| R21 | PASS (pipeline reproducible) | `loops/R21_REPORTE.md:10`, commit `e33f8cb` |
| R22 | PASS (ADB + runtime, capital preservado) | `loops/R22_REPORTE.md:11`, commit `300a06a` |
| R23J | PASS operativo y contable; paridad/desempeño NO CONCLUYENTE | `loops/R23J_PROGRESO.md:20` |
| R24 Fase B | PASS-AUTOR (4 fixes: idempotencia, S/R, estado adaptativo, NO_PROCESADA) | `6fc51f4`, `7be78e8`, `7ec019a`, `543996a`, `0a728e5`, `69046d2` |
| R25 | **CERRADO**: V1–V4 PASS en 3 ventanas; V5 NO EVALUABLE; veredicto formal `NO EVALUABLE (por V5)` | `loops/r25_validation/R25_CIERRE.md`, commit `48509ca` |
| R26 | **CERTIFICADO**: anomalía 7→9 corregida y probada; caducidad de `AUTONOMOUS` P1/P2 PASS | `loops/r26_validation/R26_REPORTE.md`, commits `f97abfa`, `f491ca4`, `75f005e` |
| R26 (auditoría) | Propiedad «1 evento → 1 incremento» **CUMPLE** | `auditorias/AUDIT_B_IDEMPOTENCIA.md` §7 |

## 3. Lo parcialmente completado

| Ronda | Estado parcial | Qué falta exactamente |
| :--- | :--- | :--- |
| R24 | Fases 0/A/B `PASS-AUTOR`; **firma independiente revocada** (`91c3351`); C/D `NO EVALUABLE` por caída del forward (`015a17b`) | Firma externa de la Fase A (punto 13 irreparable: preregistro R23K posterior a 8/19 ventanas) y repetición de C/D (absorbida por R25) |
| R25 | V1–V4 PASS; **V5 NO EVALUABLE** por muestra (2 trades live vs ≥ 20) | Acumular ≥ 20 trades live y correr el comparador congelado |
| R23K | Fases cerradas, pero preregistro posterior a 8/19 ventanas | Defecto histórico irreversible; documentado |

## 4. Lo pendiente

| # | Tarea | Dónde | Por qué no está hecha |
| :--- | :--- | :--- | :--- |
| P-1 | Restaurar accesibilidad y calibrar el dispositivo | dispositivo (`com.example.tradedraw`) | Se perdió al reinstalar la v20; requiere interacción humana (Ajustes → Accesibilidad) |
| P-2 | Preregistrar **R27** y commitearlo **antes** de medir | `workflows/PREREGISTRO_R27.md` | No existe; R26 §4 lo delega explícitamente a un cubo propio |
| P-3 | Ejecutar ventanas R27 hasta ≥ 20 trades live | `loops/r27_validation/` | Requiere P-1 y conectividad ADB (caída desde 22:50) |
| P-4 | Decidir y ejecutar `r10-night` → `main` (fast-forward) | git | Decisión de política: `tradedraw-loop.md:24` exige "avance significativo"; `AGENTS.md` §1 pide push directo |
| P-5 | `README.md` de raíz | raíz | Ausente (`ls README*` → no existe) |
| P-6 | Higiene: >430 PNG rastreados (muchos en raíz), scripts sueltos `_probe_*.js` | raíz | Nunca se limpió; riesgo de ruido en revisiones |
| P-7 | Revisar el camino TIE (`recordTradeVoid`) fuera de la capa de idempotencia | `RiskManager.kt` | Hallazgo nuevo de esta auditoría (§7, F-3) |

## 5. Evidencia por conclusión

| Conclusión | Cómo se verificó (comando/artefacto) |
| :--- | :--- |
| HEAD y rama | `git rev-parse HEAD origin/r10-night main` → `75f005e`, `75f005e`, `4d433ed` |
| main detrás | `git rev-list --count main..origin/r10-night` → **210**; `origin/r10-night..main` → **0** |
| R25 cerrado | `loops/r25_validation/R25_CIERRE.md` §3 (veredicto explícito) + commit `48509ca` |
| R26 es lo único posterior | `git log --oneline 48509ca..HEAD` → 3 commits, todos R26 |
| No hay R27 | `ls workflows/PREREGISTRO_R*.md` termina en R26; `grep -r R27` solo da menciones prospectivas |
| 513 tests | `loops/r26_validation/kotlin_test_summary.txt` (regenerado 22:50:47): 55 clases, **513 tests, 0 fallos, 2 skipped** |
| 507 antes de R26 | `git grep -c "@Test" 48509ca -- app/src/test \| suma` → **507**; `f97abfa^` → **507**; HEAD → **513** |
| 10 tests de idempotencia | `app/build/test-results/.../TEST-...LiquidationIdempotencyTest.xml` → `tests="10" failures="0"` |
| 9 antes de `f97abfa` | `git show f97abfa^:.../LiquidationIdempotencyTest.kt \| grep -c @Test` → **9** (origen de la afirmación "9 PASS" en `workflows/r24_tasks/B1_verify_result.md:31`) |
| P1 caducó | `P1_t0.txt` (armado epoch 1793156498) vs `P1_mode_log.json` ts 1793157128187 → **630,015 s** |
| P2 no caducó | `P2_beats.txt` (6 beats, 121 s) + `P2_status_final.json` → `AUTONOMOUS`, sin evento de expiry |
| Sin órdenes en R26 | `risk.tapsDespachados=0` en P0/P1/P2/P3; `signalsGenerated` 0→25→45 |
| Python | `PYTHONPATH=.:validation_engine /data/.../usr/bin/python3 -m unittest discover -s tests` → **43 tests, OK** |

## 6. Tests ejecutados en esta reconstrucción

| Suite | Comando | Resultado | Artefacto |
| :--- | :--- | :--- | :--- |
| Kotlin completa | `./gradlew testDebugUnitTest` | **513 tests, 0 fallos, 2 skipped** | `loops/r26_validation/kotlin_test_summary.txt` |
| Idempotencia (dirigida) | `./gradlew testDebugUnitTest --tests '*LiquidationIdempotencyTest'` | **10/10** | XML de Gradle |
| Watchdog (dirigida) | idem `*AutonomousWatchdogTest` | **5/5** | XML de Gradle |
| Python completa | `PYTHONPATH=.:validation_engine /usr/bin/python3 -m unittest discover -s tests` | **43 tests, OK** | salida de consola |
| Python (venv del PATH) | `python3 -m unittest discover -s tests` | 37 tests, **2 errores de import** (sin numpy) | salida de consola |

**Ningún test se ejecutó contra el broker.** Todo es JVM, Python o auditoría estática. El dispositivo
solo se usó para P1/P2 de R26 (caducidad de modo), que no coloca órdenes.

## 7. Fallos y correcciones encontrados en esta pasada

| # | Hallazgo | Gravedad | Estado |
| :--- | :--- | :--- | :--- |
| F-1 | El informe R26 rotulaba `orderState=null, hasPendingTrade=null`; los valores reales son `risk.orderState="NINGUNA"` y `risk.hasPendingTrade=false` | Media (reporte) | **Corregido** (§8 del informe) |
| F-2 | El preregistro R26 decía "fijado 22:20" pero se creó a las **22:00:43** (antes de P1) y se commiteó **después** de medir | Media (proceso) | **Corregido + limitación declarada** |
| F-3 | **El camino TIE no pasa por la capa de idempotencia**: `recordTradeVoid()` no recibe clave ni es `@Synchronized`; el motor lo llama directo en `TradingEngine.kt:678`. Una liquidación manual seguida de un TIE del motor contaría 1W + 1V para un solo evento | Media (datos V1) | **Abierto** → P-7 |
| F-4 | La corrida dirigida de AGY borró los XML de la suite completa, dejando sin evidencia en disco el 513/513 | Baja (evidencia) | **Corregido**: resumen persistido en el repo |
| F-5 | "numpy no instalado" era impreciso: falta solo en el venv del PATH; con el Python del sistema la suite pasa 43/43 | Baja (reporte) | **Corregido** |
| F-6 | Hermes (AUDIT-D) afirmó que `loops/R21_REPORTE.md` y `loops/R22_REPORTE.md` no existen | — | **Refutado**: existen (17 KB y 7 KB) |
| F-7 | `R24_FINAL_REPORT.md` dice C/D "NO EJECUTADA"; `R24_PROGRESO.md` y el preregistro R25 las declaran "NO EVALUABLE por transporte" | Baja (documental) | **Corregido**: adenda fechada al final de `R24_FINAL_REPORT.md` (§1 intacto) |
| F-8 | No hay `README.md`; >430 PNG rastreados en la raíz | Baja (higiene) | **Abierto** (P-5/P-6) |
| F-9 | `AGENTS.md` §1 exige push a `main`; `tradedraw-loop.md:24` lo condiciona a "avance significativo". `main` no tiene R25/R26 | Media (política) | **Abierto** (P-4, decisión del usuario) |

## 8. Próxima tarea técnica (y por qué es esa)

> **Actualización 2026-11-05 (R27-H):** se abrió una ronda de corrección de **diagnóstico** bajo el
> nombre R27 que el enunciado del usuario asignó. Ver `loops/r27_validation/R27_REPORTE.md`.
> Resultado: `SCREEN_FEED_MISMATCH` y `BALANCE_UNREADABLE` eran falsos positivos de la capa de
> health (que solo alimenta `/status`, no decide trading); `lastBalanceReadMs` no se estampaba en la
> ruta pasiva de balance. Corregido + 20 pruebas. `health=READY` verificado en `duchamp` (SDK 36).
>
> **La ronda V5 de trading sigue pendiente y sin consumir:** no se creó
> `workflows/PREREGISTRO_R27.md` porque esta ronda no mide rendimiento ni coloca órdenes. Cuando se
> aborde V5 necesita su propio preregistro commiteado **antes** de medir (lección F-2 de R26), y
> probablemente conviene renombrar esta a `R27-H` para no colisionar.

> **Actualización 2026-11-05 (R27-H loops):** cuatro loops cerrados sobre el mismo HEAD.
> **L1** ciclo de vida de `AccessibilityService` (nueva clase pura `ActiveInstance`, `onUnbind`
> ahora libera la referencia; 13 tests). **L2** fuente de datos explícita: `OverlayService` es el
> poseedor autoritativo y el bridge la pasa (12 tests). **L3** verificación de la separación
> diagnóstico (10 s) vs guard del motor (3 s), sin cambios de código (13 tests). **L4** regresión:
> **566 tests, 0 fallos**.
> **Sin validación en dispositivo:** instalar y reiniciar servicios están prohibidos sin
> autorización expresa, así que el APK instalado sigue siendo el de R27-H.
> Detalle en `loops/r27h_loops/`. Ver también `SIGUIENTE_AUDITORIA.md` para los candidatos a L5+.

> **R27 — preregistro + acumulación de ≥ 20 trades live para evaluar V5.**

Cadena de evidencia (no afirmación):

1. `git log --oneline 48509ca..HEAD` = **3 commits**, todos de R26. No existe trabajo posterior a R26.
2. `R25_CIERRE.md` §5 dejó **dos** pendientes: (a) caducidad de `AUTONOMOUS`; (b) V5 con ≥ 20 trades.
3. La adenda de `R25_CIERRE.md` (22:26) y `R26_REPORTE.md` §5 prueban que **(a) quedó cerrado**; el
   pendiente (b) **no tiene ningún artefacto que lo cubra** (`ls loops/r27_validation` → no existe).
4. `PREREGISTRO_R26.md` §4 excluye V5 por diseño y remite a "un cubo propio (R27) con su preregistro".
5. `ls workflows/PREREGISTRO_R*.md` termina en **R26**: R27 **no está autorizado ni iniciado**.

**Prerrequisitos antes de abrir la primera ventana de R27:**

- P-1 accesibilidad restaurada y calibración verificada (`dumpsys accessibility` + clic de prueba);
- P-2 `PREREGISTRO_R27.md` **commiteado antes** de medir (lección de F-2);
- conectividad ADB recuperada (**hoy caída**: `adb connect 192.168.1.196:5555` → `No route to host`);
- watchdog de `adb forward` de R25 activo y certificado 60 s;
- batería ≥ 25 %.

**Bloqueo actual, exacto:** el dispositivo no responde desde las 22:50 en `192.168.1.196:5555`
(`failed to connect: No route to host`). Sin red no hay ni verificación de accesibilidad ni ventanas.
La IP la asigna DHCP y puede haber cambiado; la recuperación exige cable USB o reconexión de la Wi-Fi
del teléfono, que es una acción humana.

## 9. Agentes responsables (asignación)

| Tarea | Agente | Por qué | Estado |
| :--- | :--- | :--- | :--- |
| P-1 restauración/calibración | **AGY** (con acción humana en Ajustes) | trabajo de dispositivo, corto y focalizado | **Bloqueado**: ADB sin ruta (`No route to host`) |
| P-2 preregistro R27 | **Hermes** | redacción multi-paso con verificación documental (ya auditó consistencia) | Listo para arrancar |
| P-3 ventanas R27 | **Hermes** | arnés largo, tool-heavy, ya operó ventanas R25 | Espera P-1/P-2 |
| P-4 merge a `main` | **OpenCode** (tras autorización del usuario) | tarea de repositorio y verificación de build | Pendiente de decisión |
| P-5 README raíz | **OpenCode** | documentación de repositorio | Pendiente |
| P-6 higiene (PNG/scripts) | **OpenCode** | limpieza de árbol con criterio conservador | Pendiente |
| P-7 TIE/idempotencia | **AGY** | análisis focalizado de `RiskManager` | Pendiente |
| P-8 conflicto documental R24 | **DSH** | decisión editorial con evidencia | **Cerrado** (adenda en R24_FINAL_REPORT.md) |

Nadie está trabajando ahora mismo en P-1…P-8: los tres paneles cerraron sus auditorías y están idle.

## 10. Riesgos e inconsistencias restantes

1. **Riesgo de conteo en TIE (F-3)**: el único camino de liquidación sin clave. No afecta a W/L, sí a
   `totalVoids` y por tanto a V1 si se comparan contadores con el ledger.
2. **Evicción del set de deduplicación**: `liquidatedTradeIds` se vacía entero al llegar a 512 claves
   (`RiskManager.kt`, política `ponytail`). Un `tradeId` repetado tras la evicción volvería a contar.
   En la práctica exige 512 liquidaciones en una sesión; queda como riesgo teórico declarado.
3. **Corrección manual sin pendiente incrementa por diseño** (`ts:0`), etiquetada en `manualWins/Losses`
   para que V1 la reste. No es bug, pero un operador puede inflar el marcador visible.
4. **`main` congelada**: el APK de CI solo se produce en `main` (`.github/workflows/android-build.yml:5`),
   así que el build v20 **no tiene artefacto en Actions** hasta que se decida P-4.
5. **Dispositivo sin red**: R27 no puede empezar hasta recuperar ADB; la última lectura (`DISABLED`)
   no se pudo reconfirmar. No hay evidencia de rearme.
6. **Confusión de intérprete Python**: usar `python3` del PATH da 2 errores por numpy. El comando
   correcto está en §6.
7. ~~Dos verdades sobre R24 C/D~~ (F-7): **resuelto** con la adenda de `R24_FINAL_REPORT.md`; el cuerpo
   histórico se conserva y la adenda explicita qué prevalece.

---

## 11. Actualización R29-A (2026-11-09)

> Sección añadida de forma aditiva por la misión **R29-A** (auditoría, estabilización y preparación
> de arquitectura; identidad: Quantitative Research Platform). Preserva todo el contenido previo;
> corrige los dos claims desfasados con notas fechadas in situ (§1 de este documento). Fuente de
> verdad de la fase: [`loops/r29a/`](loops/r29a/STATE.md) — informes R0–R4 aprobados.

### 11.1 Estado real verificado del working tree (hoy)

| Dimensión | Valor verificado (etiqueta) |
| :--- | :--- |
| Rama / HEAD | `r10-night` @ `c934835` = `origin/r10-night` [VERIFICADO — `git rev-parse`] |
| Working tree | **NO limpio**: 9 modificados (+582/−109) + 13 rutas sin rastrear (30 archivos) — trabajo R27/R27-H/R28-A/R28-B **sin commit** [VERIFICADO — `git status`; detalle `loops/r29a/ROUND_01_GIT_AUDIT.md`] |
| Tests Kotlin vigentes | **565 tests / 55 clases / 0 fallos / 0 errores / 2 skipped** según XML on-disk (el bruto en disco es 573/58 XML; 3 XML obsoletos de clases borradas: `AssetRicMappingTest`, `HudGeometriaTest`, `OrderStateReposoTest`) [VERIFICADO — re-agregado de XML en `loops/r29a/ROUND_02_COMPONENT_INVENTORY.md` §5] |
| APK | **Instalada v20** ≠ working tree `versionCode=21` (`1.11-r27h-activeinstance-atomic`) — el working tree no está validado en dispositivo [VERIFICADO — `loops/r28a_verification/R28A_CIERRE.md` §7.2] |
| Python | 43 `def test_` en `tests/` — **SIN VERIFICAR por ejecución** en rondas recientes (sin artefacto en disco) [PARCIALMENTE VERIFICADO] |
| CI | Solo `assembleDebug` sobre `main`; no ejecuta tests ni cubre `r10-night` [VERIFICADO — `.github/workflows/android-build.yml`] |
| Supervisor externo | `scripts/td_supervisor.sh` (AUTO_REARM=1) inactivo desde 2026-11-07T18:27:41Z; lock obsoleto conservado [VERIFICADO — R0 §2.4] |

### 11.2 Informes R28 (hoy huérfanos) y workspace R29-A

- [`loops/r28a_verification/R28A_CIERRE.md`](loops/r28a_verification/R28A_CIERRE.md): cierre R28-A
  ("CIERRE CON UNA CORRECCIÓN": prueba de concurrencia de `ActiveInstance` vacua corregida con
  latches; suite 573/0/2 con horas; bump de versión sin justificación §7). **Este documento no lo
  referenciaba** (grep "R28" sin coincidencias — inconsistencia 3 de R1 §6).
- [`loops/r28b_autonomy/R28B_BLOQUEO_AISLAMIENTO.md`](loops/r28b_autonomy/R28B_BLOQUEO_AISLAMIENTO.md):
  bloqueo arquitectónico — autonomía E2E **NO demostrable** (no existe simulador de órdenes ni
  abstracción de ejecución; la única vía es `dispatchGesture`/root-tap; "DEMO" es etiqueta del
  broker, no sandbox). Recomienda `OrderGateway`+`SimulatedOrderGateway` → motiva R29-A/R29-B.
- Workspace de la fase: [`loops/r29a/`](loops/r29a/STATE.md) con `STATE.md`, `MISSION.md`,
  `DECISIONS.md` y los informes `ROUND_00…04` aprobados.

### 11.3 Estado de la fase R29-A

- **Rondas aprobadas:** R0 (preparación), R1 (auditoría git: working tree íntegro y explicable al
  100 %; 8 inconsistencias documentales), R2 (inventario: 47 clases/19.241 LOC, 0 interfaces,
  3 vías de tap + root primaria, bridge 28 endpoints), R3 (aislamiento: 7/10 dominios nominales,
  SHADOW decision-only que consume la vela, demo≠sandbox), R4 (arquitectura objetivo: 11 dominios,
  contratos P0/P1/P2, 7 fases, enmiendas §14).
- **Ronda 5 (esta actualización):** documentación central — se crearon
  [`docs/SYSTEM_BOUNDARIES.md`](docs/SYSTEM_BOUNDARIES.md),
  [`docs/DATA_CONTRACTS.md`](docs/DATA_CONTRACTS.md),
  [`docs/SECURITY_MODEL.md`](docs/SECURITY_MODEL.md),
  [`docs/TESTING_STRATEGY.md`](docs/TESTING_STRATEGY.md) y se actualizó aditivamente
  [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) + este documento.
- **Pendientes heredados que R29-A no ejecuta:** commit del trabajo R27/R28 (regla 10), validación
  en dispositivo del working tree (APK v21), preregistro V5 (≥20 trades), L5+, y para R29-B:
  `OrderGateway`/`SimulatedOrderGateway` (recomendación R28-B; diseño R4).
