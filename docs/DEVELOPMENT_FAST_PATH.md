# TradeDraw · Fast Development Path

Este flujo existe para reducir tiempos de iteración sin rebajar los gates finales.

## 1. Antes de editar: reclamar el scope

Cada agente concurrente debe trabajar en su propio Git worktree y declarar la zona que va a modificar:

```bash
python scripts/ci/worktree_overlap.py --claim <scope>
```

Ejemplos:

```bash
python scripts/ci/worktree_overlap.py --claim research
python scripts/ci/worktree_overlap.py --claim scripts/ci
python scripts/ci/worktree_overlap.py --claim tests/test_fast_verify.py
```

- Exit 0: el scope está libre.
- Exit 4: otro worktree ya modifica ese scope. Cambiar de frente o coordinar antes de editar.
- Los archivos dentro de `tests/` se reclaman por archivo para permitir pruebas paralelas independientes.
- Un cambio global de Gradle/configuración debe tratarse como scope de proyecto completo.

## 2. Durante la iteración: validar sólo el impacto

Ver el plan sin ejecutar:

```bash
python scripts/ci/fast_verify.py --plan
```

Ejecutar el gate rápido correspondiente a los archivos modificados:

```bash
python scripts/ci/fast_verify.py
```

El script deriva el grafo Gradle real y añade consumidores downstream. Ejemplos:

- cambio en `:research`: prueba research, sin recompilar Android completo si no es necesario;
- cambio en `:ui`: prueba ui, adapters y app;
- cambio en `:core`: alcanza todos los módulos;
- cambio sólo Python: ejecuta tests Python sin Gradle;
- cambio en workflow/Gradle global: fuerza gate completo.

## 3. Antes de publicar: gate completo

Para certificación final:

```bash
python scripts/ci/fast_verify.py --full
```

El modo full limpia outputs, usa daemon fresco, ejecuta todos los módulos, Python, replay y APK, y falla ante tests omitidos no explicados.

Un PASS rápido nunca sustituye el gate full cuando el corte va a publicarse como evidencia final.

## 4. CI

GitHub Actions clasifica el delta del evento.

- Si **todos** los archivos cambiados son Markdown, ejecuta el fast-path documental y omite JDK, Python, Gradle y APK.
- Si aparece cualquier archivo no Markdown —Kotlin, Python, JSON, YAML, dataset, Gradle, workflow, etc.— ejecuta el CI pesado completo.
- Un conjunto vacío o desconocido también cae al camino pesado.

Esto evita gastar minutos compilando una APK por cambios puramente documentales sin permitir que un cambio ejecutable escape de validación.

## 5. Disciplina de concurrencia

No usar un worktree compartido entre agentes.

Secuencia recomendada:

```text
claim scope
→ editar
→ fast_verify --plan
→ fast_verify
→ revisar diff
→ gate full cuando corresponda
→ commit
→ integrar
→ CI
```

Si un agente detecta cambios ajenos en su scope, debe preservarlos y detener la edición de esa zona hasta resolver la colisión. No usar reset/restore/clean para borrar trabajo concurrente.
