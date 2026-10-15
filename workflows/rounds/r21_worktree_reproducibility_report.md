# R21 — REPORTE DE REPRODUCIBILIDAD EN WORKTREE LIMPIO (FRENTE G)

**Fecha:** 2026-10-15 03:07:26 UTC  
**Herramienta:** `scripts/ci/verify_clean_worktree.py`  
**Commit / Ref Verificado:** `HEAD` (`4d433ed54bbc0c8541b64a05ef122079ca0d11b9`)  
**Ruta Worktree Temporal:** `C:\Users\heidy\Tradedraw_repro_worktree`  
**Veredicto Final:** **FAIL (NO REPRODUCIBLE)**  

---

## 1. Resumen Ejecutivo de Reproducibilidad

| Métrica / Fase | Resultado | Criterio de Aceptación |
|---|---|---|
| **Creación Worktree Limpio** | `PASS` | Worktree aislado creado sin basura no trackeada |
| **Compilación Gradle** | `FAIL` | `compileDebugKotlin` / `compileDebugJavaWithJavac` exitosos |
| **Suite de Tests Unitarios** | **`0 tests`** (`0 fallos, 0 errores`) | 100% tests verdes sin dependencias ocultas |
| **Clases de Test Ejecutadas** | `0 clases` | Suite completa ejecutada en JVM limpia |
| **Tiempo de Ejecución Tests** | `0.0 s` | Rendimiento de suite unitaria |
| **Limpieza y Desmantelamiento** | `PASS` | Eliminación segura de directorio y `git worktree prune` |

---

## 2. Detalle de Ejecución de Gradle (`testDebugUnitTest`)

- **Comando ejecutado:** `"C:\Users\heidy\Tradedraw_repro_worktree\gradlew.bat" testDebugUnitTest --no-daemon`
- **Código de salida:** `1`
- **Duración total fase Gradle:** `94.38 s`

```
> Task :app:generateDebugResources
> Task :app:packageDebugResources
> Task :app:mergeDebugResources
> Task :app:createDebugCompatibleScreenManifests
> Task :app:extractDeepLinksDebug
> Task :app:parseDebugLocalResources
> Task :app:processDebugMainManifest
> Task :app:processDebugManifest
> Task :app:preDebugUnitTestBuild UP-TO-DATE
> Task :app:javaPreCompileDebug
> Task :app:javaPreCompileDebugUnitTest
> Task :app:processDebugManifestForPackage
> Task :app:processDebugResources
> Task :app:compileDebugKotlin

> Task :app:compileDebugKotlin FAILED

[Incubating] Problems report is available at: file:///C:/Users/heidy/Tradedraw_repro_worktree/build/reports/problems/problems-report.html

Deprecated Gradle features were used in this build, making it incompatible with Gradle 10.

You can use '--warning-mode all' to show the individual deprecation warnings and determine if they come from your own scripts or plugins.

For more on this, please refer to https://docs.gradle.org/9.5.0/userguide/command_line_interface.html#sec:command_line_warnings in the Gradle documentation.
17 actionable tasks: 17 executed
```

**Salida de error (Stderr):**
```
Warning: SDK processing. This version only understands SDK XML versions up to 3 but an SDK XML file of version 4 was encountered. This can happen if you use versions of Android Studio and the command-line tools that were released at different times.
e: file:///C:/Users/heidy/Tradedraw_repro_worktree/app/src/main/java/com/example/tradedraw/OverlayService.kt:114:49 Unresolved reference: health
e: file:///C:/Users/heidy/Tradedraw_repro_worktree/app/src/main/java/com/example/tradedraw/OverlayService.kt:114:97 Unresolved reference: health

FAILURE: Build failed with an exception.

* What went wrong:
Execution failed for task ':app:compileDebugKotlin' (registered by plugin 'org.jetbrains.kotlin.android').
> A failure occurred while executing org.jetbrains.kotlin.compilerRunner.GradleCompilerRunnerWithWorkers$GradleKotlinCompilerWorkAction
   > Compilation error. See log for more details

* Try:
> Run with --stacktrace option to get the stack trace.
> Run with --info or --debug option to get more log output.
> Run with --scan to get full insights from a Build Scan (powered by Develocity).
> Get more help at https://help.gradle.org.

BUILD FAILED in 1m 33s
```

---

## 3. Auditoría de Higiene del Repositorio Principal

- **Archivos modificados (en curso por otros frentes):** `9`
- **Archivos no trackeados (artefactos / temporales):** `55`
- **Reglas .gitignore activas para artefactos:** `OK (build y .gradle ignorados)`

---

## 4. Criterio de Aceptación Frente G

- [x] **G.1:** Script que clona/crea un git worktree limpio temporal para el SHA a validar.
- [x] **G.2:** Ejecuta compilación y tests unitarios dentro del worktree aislado.
- [ ] **G.3:** Verifica que la compilación es reproducible y pasa la suite limpia.
- [x] **G.4:** Limpieza automática y segura del worktree temporal sin afectar el espacio de trabajo activo.