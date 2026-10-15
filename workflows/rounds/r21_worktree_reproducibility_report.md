# R21 — REPORTE DE REPRODUCIBILIDAD EN WORKTREE LIMPIO (FRENTE G)

**Fecha:** 2026-10-15 03:12:39 UTC  
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
- **Duración total fase Gradle:** `142.68 s`

```
	at java.rmi/sun.rmi.transport.tcp.TCPTransport.handleMessages(Unknown Source)
	at java.rmi/sun.rmi.transport.tcp.TCPTransport$ConnectionHandler.run0(Unknown Source)
	at java.rmi/sun.rmi.transport.tcp.TCPTransport$ConnectionHandler.lambda$run$0(Unknown Source)
	at java.base/java.security.AccessController.doPrivileged(Unknown Source)
	at java.rmi/sun.rmi.transport.tcp.TCPTransport$ConnectionHandler.run(Unknown Source)
	... 3 more
Caused by: java.nio.file.NoSuchFileException: C:\Users\heidy\Tradedraw_repro_worktree\app\build\kotlin\compileDebugKotlin\classpath-snapshot\shrunk-classpath-snapshot.bin
	at java.base/sun.nio.fs.WindowsException.translateToIOException(Unknown Source)
	at java.base/sun.nio.fs.WindowsException.rethrowAsIOException(Unknown Source)
	at java.base/sun.nio.fs.WindowsException.rethrowAsIOException(Unknown Source)
	at java.base/sun.nio.fs.WindowsFileSystemProvider.newFileChannel(Unknown Source)
	... 29 more
Using fallback strategy: Compile without Kotlin daemon
Try ./gradlew --stop if this issue persists.

> Task :app:compileDebugKotlin FAILED

[Incubating] Problems report is available at: file:///C:/Users/heidy/Tradedraw_repro_worktree/build/reports/problems/problems-report.html

Deprecated Gradle features were used in this build, making it incompatible with Gradle 10.

You can use '--warning-mode all' to show the individual deprecation warnings and determine if they come from your own scripts or plugins.

For more on this, please refer to https://docs.gradle.org/9.5.0/userguide/command_line_interface.html#sec:command_line_warnings in the Gradle documentation.
17 actionable tasks: 17 executed
```

**Salida de error (Stderr):**
```
exception: error: source file or directory not found: C:\Users\heidy\Tradedraw_repro_worktree\app\src\main\java\com\example\tradedraw\TradeJournalLogger.kt
exception: error: source file or directory not found: C:\Users\heidy\Tradedraw_repro_worktree\app\src\main\java\com\example\tradedraw\TradeLearningEngine.kt
exception: error: source file or directory not found: C:\Users\heidy\Tradedraw_repro_worktree\app\src\main\java\com\example\tradedraw\TradingEngine.kt
exception: error: source file or directory not found: C:\Users\heidy\Tradedraw_repro_worktree\app\src\main\java\com\example\tradedraw\TradingTool.kt
exception: error: source file or directory not found: C:\Users\heidy\Tradedraw_repro_worktree\app\src\main\java\com\example\tradedraw\ui\theme\Color.kt
exception: error: source file or directory not found: C:\Users\heidy\Tradedraw_repro_worktree\app\src\main\java\com\example\tradedraw\ui\theme\Theme.kt
exception: error: source file or directory not found: C:\Users\heidy\Tradedraw_repro_worktree\app\src\main\java\com\example\tradedraw\ui\theme\Type.kt
exception: error: source file or directory not found: C:\Users\heidy\Tradedraw_repro_worktree\app\src\main\java\com\example\tradedraw\VisionAnalyzer.kt
exception: warning: classpath entry points to a non-existent location: C:\Users\heidy\Tradedraw_repro_worktree\app\build\intermediates\compile_and_runtime_not_namespaced_r_class_jar\debug\processDebugResources\R.jar

FAILURE: Build failed with an exception.

* What went wrong:
Execution failed for task ':app:compileDebugKotlin' (registered by plugin 'org.jetbrains.kotlin.android').
> A failure occurred while executing org.jetbrains.kotlin.compilerRunner.GradleCompilerRunnerWithWorkers$GradleKotlinCompilerWorkAction
   > El sistema no puede encontrar la ruta especificada

* Try:
> Run with --stacktrace option to get the stack trace.
> Run with --info or --debug option to get more log output.
> Run with --scan to get full insights from a Build Scan (powered by Develocity).
> Get more help at https://help.gradle.org.

BUILD FAILED in 2m 21s
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