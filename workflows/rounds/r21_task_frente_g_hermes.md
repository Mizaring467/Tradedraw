# R21 — Asignación a HERMES: Frente G (Verificación de Reproducibilidad en Worktree Limpio)

**Responsable:** Hermes (`w1:p6`)  
**Orquestador:** AGY (`w1:p1`)  
**Fecha:** 2026-10-14  
**Workspace:** `w1`  
**Directorio de trabajo:** `C:\Users\heidy\Tradedraw`  
**Archivos reservados para Hermes:** `scripts/ci/verify_clean_worktree.py`, `workflows/rounds/r21_worktree_reproducibility_report.md`  
**PROHIBIDO:** Editar archivos Kotlin (reservados por AGY).

---

## 1. Contexto y Objetivo

Para garantizar la reproducibilidad científica estricta de la Ronda 21:
Antes de cerrar la misión, se debe comprobar que el repositorio compila y pasa sus tests desde un worktree temporal limpio, demostrando que no existen dependencias ocultas, archivos sin trackear necesarios o basura no reproducible.

---

## 2. Requisitos del Script / Automatización

Crear `scripts/ci/verify_clean_worktree.py` (o PowerShell si es más directo para Windows):
1. Crear un worktree temporal limpio (ej. `git worktree add ../tradedraw_repro_test HEAD`).
2. Comprobar que Gradle compila y ejecuta los tests unitarios (`gradlew.bat testDebugUnitTest`) dentro del worktree limpio.
3. Verificar el estado de git (`git status`) en el repo principal para certificar:
   - Cero archivos sensibles o temporales sin trackear necesarios.
   - `.gitignore` limpio.
4. Desmantelar y eliminar el worktree temporal (`git worktree remove --force ...`).
5. Generar reporte Markdown con la evidencia en:
   `workflows/rounds/r21_worktree_reproducibility_report.md`.

---

## 3. Instrucción de Cierre

Al terminar, reporta a AGY en Herdr con el resultado de la compilación en el worktree limpio y la confirmación de limpieza.
