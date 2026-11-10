# Estrategia de pruebas — TradeDraw · R29-A

**Alcance:** cómo se prueba HOY el sistema, con cifras verificadas sobre artefactos en disco (no se
re-ejecutó nada en esta ronda) y límites de lo que no puede verificarse sin dispositivo.
**Fuentes:** [R2 inventario](../loops/r29a/ROUND_02_COMPONENT_INVENTORY.md) §5,
[R3 aislamiento](../loops/r29a/ROUND_03_ISOLATION_AUDIT.md) §2.8/§3,
[R1 auditoría git](../loops/r29a/ROUND_01_GIT_AUDIT.md) §5,
matriz de R27: `loops/r27_validation/TEST_MATRIX.md`.

---

## 1. Suite JVM Kotlin (principal) — cifras reales del working tree

**Re-agregado de XML on-disk** (`app/build/test-results/testDebugUnitTest/`, parseado con Python en
R2/R3; la última corrida registrada es del 2026-11-05, lote R28):

| Métrica | Bruto en disco | Vigente (excluyendo XML obsoletos) |
| :--- | :--- | :--- |
| Archivos XML | 58 | **55** vigentes + 3 obsoletos |
| Clases de test | 58 | **55** (las fuentes actuales son 55) |
| Tests | 573 | **565** |
| Fallos / errores | 0 / 0 | 0 / 0 |
| Skipped | 2 | 2 |

> ⚠️ **Corrección (R29-A, 2026-11-09, re-verificación de la Ronda 5):** una versión previa de esta
> tabla decía "59 XML / 58 vigentes": cifra incorrecta e internamente inconsistente (58+3=61≠59).
> Re-agregado directo de `app/build/test-results/testDebugUnitTest/` hoy: **58 XML en disco en
> total** = 55 vigentes + 3 obsoletos (`AssetRicMappingTest` 3 + `HudGeometriaTest` 3 +
> `OrderStateReposoTest` 2 = 8 tests obsoletos; 573 − 8 = 565 vigentes). El claim de R2 §5
> ("58 XML / 573 tests") era el correcto para el bruto.

- **Los 2 skipped vigentes** son de `ReplayBacktestTest` (`AssumptionViolatedException`): la última
  corrida no exportó `BACKTEST_CSV`/`BACKTEST_DIR` (`ReplayBacktestTest.kt:519` y `:591`,
  `assumeTrue`; XML con timestamp 2026-11-05T23:58). Causa documental en R3 §2.8d.
- **Los 3 XML obsoletos** (`AssetRicMappingTest`, `HudGeometriaTest`, `OrderStateReposoTest`)
  corresponden a clases cuya fuente ya no existe; quedan como artefactos residuales de Gradle
  (no se borran: regla 7). La diferencia 573−565 = 8 tests viven en esos XML.
- **Historial de la cifra (no son contradictorios):** R26 certificó 513/513 en
  `loops/r26_validation/kotlin_test_summary.txt`; R27 midió 528 (TEST_MATRIX §4); R27-H L4 declaró
  566/0; R28-A certificó 573/0/2 — el claim "573/58" incluye los 3 XML obsoletos (hallazgo R2 §5).
  Claims históricos, NO re-ejecutados aquí.
- Tests nuevos del working tree sin commit (R27/R28): `ActiveInstanceTest` (20 testcases,
  concurrencia con latches), `BalanceFreshnessSeparationTest`, `HealthDataSourceTest`, reescritura
  de `HealthSupervisorTest` (20 testcases). Total añadido por el trabajo sin commit: **1.072 LOC de
  test** (277 diff + 795 sin rastrear; recalcado en R3 §2.9b).

## 2. Suite Python (43 tests) — SIN VERIFICAR por ejecución

- `tests/` (6 archivos, **43 `def test_`** — conteo grep VERIFICADO) + `validation_engine/`
  (7 módulos). El comando reproducible está en `loops/r27_validation/TEST_MATRIX.md:77`
  (`PYTHONPATH=.:validation_engine /usr/bin/python3 -m unittest discover -s tests`).
- **Estado: SIN VERIFICAR** — no hay artefacto de resultados en disco de corridas recientes; la
  única ejecución certificada es la de R26 (43 tests OK, `TRADEDRAW_CURRENT_STATE.md` §5-6).
- Nota de entorno (R26): con el `python3` del PATH falla por numpy; usar el Python del sistema
  (`/usr/bin/python3`).

## 3. TEST_MATRIX de R27 — desviación declarada

`loops/r27_validation/TEST_MATRIX.md`: 11/11 escenarios (10 PASS + 1 PARCIAL justificado), health
dirigida 20/20, Python 43 OK (claim de esa ronda). La desviación declarada: balance obsoleto =
`DEGRADED/BALANCE_STALE` en el diagnóstico, no bloqueo — el guard real de trading vive en
`TradingEngine.isBalanceFresh` (3.000 ms, solo liquidación/TIE) y no se tocó (separación fijada por
`BalanceFreshnessSeparationTest`).

## 4. CI: qué cubre y qué no

`.github/workflows/android-build.yml` [VERIFICADO hoy — lectura del yml]:

- **Solo `assembleDebug`** — **no ejecuta `testDebugUnitTest`** (ningún paso de tests).
- Se dispara **solo sobre `main`** (push/PR); la línea de trabajo vive en `r10-night`
  (212 commits por delante de `main`, R1 §1) → **el CI actual no cubre la rama activa ni ejecuta
  la suite**: la suite JVM corre solo en máquinas locales (Termux/estación del operador).

## 5. Equivalencias planeadas (R29-B, PROPUESTA-R29-B)

El plan de migración R4 (§6) define verificaciones nuevas que AÑADEN verificación sin reemplazar
expectativas congeladas (detalle normativo en `loops/r29a/ROUND_04_TARGET_ARCHITECTURE.md`):

- **F3 — equivalencia veto-a-veto**: la cascada extraída (`ExecutionPolicy`) debe enumerar y probar
  cada uno de los 13 vetos en el orden congelado (inventario veto-por-veto pendiente para R29-B).
- **F1 — dual-read de balance**: patrón R27-H — durante 1 fase, leer por puerto nuevo Y por estático
  con log de equivalencia (`BalanceSource`, PROPUESTA-R29-B).
- **F4 — paridad de decisión simulada vs real** en fixtures r23*, con esquema de journal versionado
  si SHADOW evoluciona a ciclo simulado (punto de STOP regla-15).
- Gating general: cada fase se acepta con la suite vigente (565) + tests nuevos en verde.

## 6. Qué NO puede verificarse sin dispositivo

Heredado de R3 §3 (límites no demostrables; análisis de código solo):

1. Filtrado real de `dispatchGesture` por Binomo/MIUI (el root-tap es la vía primaria según el
   propio código, `ATAS:211-216`).
2. Fallo práctico de la heurística demo/real con cuentas reales o idiomas no españoles.
3. Distribución real TIE vs TIE_INCIERTO en liquidación con el working tree (cambio C.1 de R27).
4. Timing real unbind→rebind de MIUI y su efecto en la liquidación.
5. Paridad live↔replay ejecutada (la suite insignia corrió skipped: §1).
6. Auto-heal (`ATAS:379-390`) sobre la MIUI real del dispositivo.

Además: **ninguna prueba cubre el ciclo de órdenes simulado** (no existe simulador — bloqueo R28-B,
`loops/r28b_autonomy/R28B_BLOQUEO_AISLAMIENTO.md`) ni la vía `/trade`//`/click` del bridge sin
cascada (solo guard demo heurístico; ver `docs/SECURITY_MODEL.md` §3-4). UI pura sin cobertura JVM:
AIClient, AgentChatOverlay, ModelPickerDialog, MainActivity, vistas HUD (R2 §5).

---

*Documento creado por la Ronda 5 de R29-A. Etiquetado por claim: VERIFICADO (con ruta:línea o
artefacto) / PARCIALMENTE VERIFICADO / NO VERIFICADO / PROPUESTA-R29-B.*
