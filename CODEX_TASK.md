# Encargo autorizado: coordinación concurrente TradeDraw

Trabaja ahora, de principio a fin, en esta sesión VISIBLE de Codex. AGY está activo y es propietario de r10-night, loops/r30_live, loops/nights_2026-11-13 y las pruebas en el teléfono. Tu worktree exclusivo es /data/data/com.termux/files/home/Tradedraw-codex-coordination, rama assistant/codex-coordination, base 7a7b5be. No trabajes desde el checkout principal.

Objetivo: resolver el pendiente documentado de granularidad de scripts/ci/worktree_overlap.py: los claims de archivo exacto no deben convertirse injustificadamente en todo el módulo. Conservar la detección real de conflictos y la compatibilidad de claims de directorio/módulo/GLOBAL y strict-scope. Es una tarea de desarrollo completa, no solo diagnóstico.

Alcance de escritura EXCLUSIVO: scripts/ci/worktree_overlap.py, tests/test_worktree_overlap.py y CODEX_TASK.md para informe/checkpoint. No tocar fast_verify.py, Gradle, AGENTS.md, runtime Android, frontend, estrategias ni ningún otro worktree. El preflight --claim scripts/ci ya dio Claim clear across 6 other worktrees; repítelo y añade el claim del archivo de tests antes de editar. Si surge una colisión, conserva todo y registra el bloqueo concreto; no elimines ni ignores cambios ajenos.

Secuencia autónoma:
1. Lee AGENTS.md e inspecciona el script y sus tests actuales; evita releer logs enormes de R30.
2. Diseña claims explícitos y normalización robusta. Rutas hermanas no colisionan; archivo contra sí mismo sí; directorio contra descendiente sí; GLOBAL siempre según reglas actuales. No crear una vía de escape que oculte colisiones, rutas absolutas fuera del repo o traversals. Mantén el formato JSON y exit codes compatibles; explica cualquier cambio semántico.
3. Implementa corrección mínima y tests de regresión con repositorios/worktrees temporales que cubran archivos hermanos, mismo archivo, padre/hijo, módulo, GLOBAL, strict-scope y cambios untracked. Revisión adversarial propia antes de dar por terminado.
4. Ejecuta unittest dirigido y el gate Python proporcional mediante fast_verify si corresponde. Nada de Gradle, builds APK, ADB, taps, reinicios, kill de procesos ni cambios del estado del teléfono; el dispositivo pertenece a AGY durante esta tarea. Esto es coordinación de recursos, no una prohibición general de pruebas DEMO del proyecto.
5. Revisa diff/check y estado fresco de los worktrees. Commit y push solo TU rama, sin fusionar a r10-night/main ni tocar otros pendientes. Deja un resumen en CODEX_TASK.md con problemas hallados, solución, comandos/resultados reales y siguiente paso de integración.

No te limites a proponer un plan ni esperes otro prompt entre pasos. No inventes resultados; si falta cuota/autenticación, explica el bloqueo en tu panel. No ejecutes codex exec ni agentes ocultos. Mantén el panel visible y conserva todas las modificaciones ajenas.


## Informe de ejecución visible — Codex, 2026-11-13

Alcance respetado: únicamente este informe, `scripts/ci/worktree_overlap.py` y
`tests/test_worktree_overlap.py`, en `assistant/codex-coordination`, base `7a7b5be`.
Ejecución íntegra en esta sesión visible; sin agentes delegados/headless ni acceso
al dispositivo de AGY, sin Gradle ni APK. Los mensajes Gradle/lock de la suite Python
corresponden a fixtures temporales del propio test de fast_verify.

### Hallazgos y solución

- `normalize_claims` ampliaba los archivos hasta el scope lógico (por ejemplo,
  `app/src/A.kt` pasaba a `app`). Ahora conserva rutas explícitas y compara igualdad
  o relación padre/descendiente con separadores completos, sin inferir tipos por
  extensión ni existencia. Archivos hermanos quedan independientes; módulos y
  directorios siguen cubriendo sus descendientes, incluso untracked o eliminados.
- Se mantiene GLOBAL para configuración Gradle y se incluye el directorio `gradle`.
  Reclamar `.` equivale a GLOBAL. Los scopes de auditoría y la precedencia existente
  de claims sobre `--strict-scope` no cambian. JSON conserva estructura y arrays de
  strings; `claimed_conflicts` informa ahora la ruta precisa reclamada.
- Normalización de `./`, separadores repetidos y barras Windows; rutas absolutas
  únicamente dentro del repo. Claims vacíos, traversal (`..`), rutas externas,
  unidades Windows y alias symlink se rechazan con diagnóstico/exit 1, en vez de
  convertir entradas inválidas en un falso resultado libre. Symlinks se rechazan
  conservadoramente para evitar alias que oculten conflictos.
- Git usa nombres delimitados por NUL para conservar espacios, Unicode y saltos de
  línea. `--no-renames` conserva ambos extremos de un renombre como cambios: editar
  el nombre original también debe bloquearse.
- Códigos compatibles: 0 libre, 2 colisión exacta reactiva, 3 scope reactivo estricto,
  4 conflicto preventivo; sin claims sigue funcionando la auditoría original.

### Evidencia real

- Preflight inicial y repetido tras implementación:
  `python scripts/ci/worktree_overlap.py --claim scripts/ci --claim tests/test_worktree_overlap.py --claim CODEX_TASK.md`
  → exit 0, `Claim clear across 6 other worktree(s).`
- `python -m unittest discover -s tests -p test_worktree_overlap.py`
  → 19 tests, OK (14.983 s). Incluye repos Git/worktrees temporales reales y CLI JSON:
  hermanos, mismo archivo, padre/hijo, módulo, GLOBAL en ambos sentidos, strict-scope,
  commits divergentes, staged, borrados, untracked, renombres, nombres inusuales y
  entradas adversariales. Revisión adversarial propia completada.
- `python scripts/ci/fast_verify.py --plan` → tres archivos, Python YES,
  módulos/Gradle/replay/APK: ninguno.
- `git diff --check` → exit 0; estado fresco muestra exclusivamente los tres archivos
  autorizados. `git worktree list --porcelain` confirma siete worktrees, AGY en
  `r10-night`; no se ha editado ningún otro checkout.

### Integración

Integrar este corte desde `assistant/codex-coordination` mediante revisión de AGY
cuando termine su frente. Codex no fusiona ni publica en `r10-night`/`main`.

Gate final: `python scripts/ci/fast_verify.py` → exit 0, 142 tests OK
(15.984 s), sin tareas Gradle ni APK. Publicación prevista exclusivamente con
`git push origin assistant/codex-coordination`; comprobar resultado en el panel.
