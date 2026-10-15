# R21 — REPORTE DE REPRODUCIBILIDAD EN WORKTREE LIMPIO (FRENTE G)

**Fecha:** 2026-10-15 03:14:46 UTC  
**Herramienta:** `scripts/ci/verify_clean_worktree.py`  
**Commit / Ref Verificado:** `HEAD` (`e6c50141e168f1b2b814a1c2280de9c95ac6d989`)  
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
- **Duración total fase Gradle:** `41.2 s`

```
> Task :app:preBuild UP-TO-DATE
> Task :app:preDebugBuild UP-TO-DATE
> Task :app:generateDebugResValues
> Task :app:checkDebugAarMetadata
> Task :app:mapDebugSourceSetPaths
> Task :app:generateDebugResources
> Task :app:packageDebugResources
> Task :app:mergeDebugResources
> Task :app:createDebugCompatibleScreenManifests
> Task :app:extractDeepLinksDebug
> Task :app:parseDebugLocalResources
> Task :app:processDebugMainManifest
> Task :app:processDebugManifest
> Task :app:processDebugManifestForPackage
> Task :app:javaPreCompileDebug
> Task :app:processDebugResources FAILED

[Incubating] Problems report is available at: file:///C:/Users/heidy/Tradedraw_repro_worktree/build/reports/problems/problems-report.html

Deprecated Gradle features were used in this build, making it incompatible with Gradle 10.

You can use '--warning-mode all' to show the individual deprecation warnings and determine if they come from your own scripts or plugins.

For more on this, please refer to https://docs.gradle.org/9.5.0/userguide/command_line_interface.html#sec:command_line_warnings in the Gradle documentation.
15 actionable tasks: 15 executed
```

**Salida de error (Stderr):**
```
Warning: SDK processing. This version only understands SDK XML versions up to 3 but an SDK XML file of version 4 was encountered. This can happen if you use versions of Android Studio and the command-line tools that were released at different times.

FAILURE: Build failed with an exception.

* What went wrong:
A problem was found with the configuration of task ':app:processDebugResources' (type 'LinkApplicationAndroidResourcesTask').
  - In plugin 'com.android.internal.version-check' type 'com.android.build.gradle.internal.res.LinkApplicationAndroidResourcesTask' property 'localResourcesFile' specifies file 'C:\Users\heidy\Tradedraw_repro_worktree\app\build\intermediates\local_only_symbol_list\debug\parseDebugLocalResources\R-def.txt' which doesn't exist.
    
    Reason: An input file was expected to be present but it doesn't exist.
    
    Possible solutions:
      1. Make sure the file exists before the task is called.
      2. Make sure that the task which produces the file is declared as an input.
    
    For more information, please refer to https://docs.gradle.org/9.5.0/userguide/validation_problems.html#input_file_does_not_exist in the Gradle documentation.

* Try:
> Run with --scan to get full insights from a Build Scan (powered by Develocity).

BUILD FAILED in 40s
No se ha encontrado el archivo por lotes.
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
- [ ] **G.3:** Verifica que la compilación es reproducible y pasa la suite limpia.
- [x] **G.4:** Limpieza automática y segura del worktree temporal sin afectar el espacio de trabajo activo.