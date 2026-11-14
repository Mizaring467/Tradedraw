# ROUND 00 — Informe F0 (Línea Base y Gate G0) · TradeDraw R29-B

**Fecha:** 2026-11-10T04:56Z · **Ejecutado por:** AGY (Sesión visible obligatoria / Solo Routine)
**Rama:** `r10-night` · **HEAD base:** `0ec5369` (`origin/r10-night`)

---

## 1. Objetivos de F0
1. **T-0.1:** Registrar la resolución de P-1 / OD-6 (opción A aprobada por el propietario: baseline R27–R29-A commiteado en `7a3b008`).
2. **T-0.2:** Verificar la ejecución de CI en `r10-night` con `testDebugUnitTest` (publicado en `0ec5369`, run `42757465313` push y `42757470092` PR, ambos verdes).
3. **T-0.3:** Crear fixtures mínimos de paridad en `data/fixtures/golden/` sin credenciales ni tokens de sesión, y documentar el comparador en `docs/TESTING_STRATEGY.md`.
4. **Gate G0:** Validar línea base local y remota antes de iniciar cualquier extracción de código.

---

## 2. Evidencia de Ejecución

### 2.1 CI GitHub Actions
- **Run ID (push):** `42757465313` · Estado: `success` en 4m19s.
- **Run ID (pull_request #7):** `42757470092` · Estado: `success` en 3m22s.
- **Pasos validados en CI:** Checkout, Java 17 Temurin, `./gradlew testDebugUnitTest`, `./gradlew assembleDebug`, upload artefacto `TradeDraw-debug`.

### 2.2 Suite Local JVM (Termux)
- **Comando:** `BACKTEST_CSV=loops/r23c_phase3_validation/window_01/ticks_live.csv BACKTEST_OUT=$(mktemp) ./gradlew testDebugUnitTest --no-daemon --no-configuration-cache`
- **Resultado:** `BUILD SUCCESSFUL in 1m 21s`.
- **Parseo de XMLs (`app/build/test-results/testDebugUnitTest/TEST-*.xml`):**
  - Archivos XML: 58
  - Total tests ejecutados: 573
  - Fallos: 0
  - Errores: 0
  - Skipped: 0 (con `BACKTEST_CSV` cargado; en CI sin env-var, 2 de `ReplayBacktestTest` son esperados por `assumeTrue`).

### 2.3 Suite Python
- **Comando:** `PYTHONDONTWRITEBYTECODE=1 PYTHONPATH=.:validation_engine python -m unittest discover -s tests`
- **Resultado:** `Ran 43 tests in 0.378s` · `OK`.

### 2.4 Fixtures Canónicos Creados (`data/fixtures/golden/`)
- `trades_journal_golden.csv`: Header canónico de 29 columnas + fila de muestra.
- `shadow_journal_golden.csv`: Header v1 canónico de 11 columnas + fila de muestra.
- `status_golden.json`: Estructura estricta de `/status` sin tokens sensibles.
- `events_golden.json`: Estructura JSON de `/events`.
- `shadow_journal_golden.json`: Estructura JSON de `/shadow_journal`.
- `docs/TESTING_STRATEGY.md`: Sección 7 añadida con descripción del comparador.

---

## 3. Dictamen de Gate G0
**GATE G0: APROBADO**
- Precondiciones P-1, P-2, P-3 y P-4 registradas y autorizadas.
- CI en verde sobre `r10-night`.
- Suites JVM y Python en 100% verde local.
- Fixtures congelados.
- Listo para avanzar a **F2 (Extracción de Core Puro)** según enmienda E6 del plan de migración.
