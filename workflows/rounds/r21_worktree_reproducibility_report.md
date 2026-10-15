# R21 — REPORTE DE REPRODUCIBILIDAD EN WORKTREE LIMPIO (FRENTE G)

**Fecha:** 2026-10-15 03:22:30 UTC  
**Herramienta:** `scripts/ci/verify_clean_worktree.py`  
**Commit / Ref Verificado:** `HEAD` (`ef2932a00e69535ca618d9c5cb57eb1710fb9228`)  
**Ruta Worktree Temporal:** `C:\Users\heidy\Tradedraw_repro_worktree`  
**Veredicto Final:** **PASS (REPRODUCIBLE)**  

---

## 1. Resumen Ejecutivo de Reproducibilidad

| Métrica / Fase | Resultado | Criterio de Aceptación |
|---|---|---|
| **Creación Worktree Limpio** | `PASS` | Worktree aislado creado sin basura no trackeada |
| **Compilación Gradle** | `PASS` | `compileDebugKotlin` / `compileDebugJavaWithJavac` exitosos |
| **Suite de Tests Unitarios** | **`366 tests`** (`0 fallos, 0 errores`) | 100% tests verdes sin dependencias ocultas |
| **Clases de Test Ejecutadas** | `37 clases` | Suite completa ejecutada en JVM limpia |
| **Tiempo de Ejecución Tests** | `1.67 s` | Rendimiento de suite unitaria |
| **Limpieza y Desmantelamiento** | `PASS` | Eliminación segura de directorio y `git worktree prune` |

---

## 2. Detalle de Ejecución de Gradle (`testDebugUnitTest`)

- **Comando ejecutado:** `cmd.exe /c C:\Users\heidy\Tradedraw_repro_worktree\gradlew.bat testDebugUnitTest --no-daemon`
- **Código de salida:** `0`
- **Duración total fase Gradle:** `166.57 s`

```
> Task :app:compileDebugJavaWithJavac NO-SOURCE
> Task :app:processDebugJavaRes
> Task :app:bundleDebugClassesToCompileJar
> Task :app:bundleDebugClassesToRuntimeJar

> Task :app:compileDebugUnitTestKotlin
w: file:///C:/Users/heidy/Tradedraw_repro_worktree/app/src/test/java/com/example/tradedraw/SyntheticCandleEngineTest.kt:142:46 Variable 'lastSignalAction' initializer is redundant
w: file:///C:/Users/heidy/Tradedraw_repro_worktree/app/src/test/java/com/example/tradedraw/SyntheticCandleEngineTest.kt:507:13 Variable 'signalEmittedReason' is assigned but never accessed
w: file:///C:/Users/heidy/Tradedraw_repro_worktree/app/src/test/java/com/example/tradedraw/VisionAnalyzerTest.kt:341:14 Variable 'supports' is never used

> Task :app:compileDebugUnitTestJavaWithJavac NO-SOURCE
> Task :app:processDebugUnitTestJavaRes
> Task :app:testDebugUnitTest

[Incubating] Problems report is available at: file:///C:/Users/heidy/Tradedraw_repro_worktree/build/reports/problems/problems-report.html

Deprecated Gradle features were used in this build, making it incompatible with Gradle 10.

You can use '--warning-mode all' to show the individual deprecation warnings and determine if they come from your own scripts or plugins.

For more on this, please refer to https://docs.gradle.org/9.5.0/userguide/command_line_interface.html#sec:command_line_warnings in the Gradle documentation.

BUILD SUCCESSFUL in 2m 46s
23 actionable tasks: 23 executed
Consider enabling configuration cache to speed up this build: https://docs.gradle.org/9.5.0/userguide/configuration_cache_enabling.html
```

---

## 3. Auditoría de Higiene del Repositorio Principal

- **Archivos modificados (en curso por otros frentes):** `0`
- **Archivos no trackeados (artefactos / temporales):** `37`
- **Reglas .gitignore activas para artefactos:** `OK (build y .gradle ignorados)`

---

## 4. Criterio de Aceptación Frente G

- [x] **G.1:** Script que clona/crea un git worktree limpio temporal para el SHA a validar.
- [x] **G.2:** Ejecuta compilación y tests unitarios dentro del worktree aislado.
- [x] **G.3:** Verifica que la compilación es reproducible y pasa la suite limpia.
- [x] **G.4:** Limpieza automática y segura del worktree temporal sin afectar el espacio de trabajo activo.