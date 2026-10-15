#!/usr/bin/env python3
"""
scripts/ci/verify_clean_worktree.py
Ronda 21 (Frente G): Verificación de reproducibilidad en worktree limpio.

Crea un git worktree temporal aislado, compila y ejecuta los tests unitarios
de Gradle de forma totalmente independiente del espacio de trabajo activo,
certifica que no existen dependencias ocultas o archivos sin trackear necesarios,
y desmantela el worktree de forma segura.
"""

import argparse
import glob
import json
import os
import shutil
import subprocess
import sys
import time
import xml.etree.ElementTree as ET
from datetime import datetime, timezone
from typing import Any, Dict, List, Optional, Tuple


def run_cmd(
    cmd: Any,
    cwd: Optional[str] = None,
    timeout: Optional[int] = None,
    env: Optional[Dict[str, str]] = None,
    shell: bool = False
) -> Tuple[int, str, str]:
    """Ejecuta un comando capturando stdout y stderr con codificación resiliente."""
    p = subprocess.Popen(
        cmd,
        cwd=cwd,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        env=env,
        text=True,
        encoding="utf-8",
        errors="replace",
        shell=shell
    )
    stdout, stderr = p.communicate(timeout=timeout)
    return p.returncode, stdout, stderr


def get_git_root(cwd: str = ".") -> str:
    code, out, _ = run_cmd(["git", "rev-parse", "--show-toplevel"], cwd=cwd)
    if code != 0:
        raise RuntimeError("No se pudo determinar la raíz del repositorio Git.")
    return os.path.normpath(out.strip())


def safe_rmtree(path: str) -> None:
    """Borrado resiliente de directorios para evitar bloqueos de archivos de solo lectura en Windows/Git."""
    if not os.path.exists(path):
        return
    import stat

    def on_error(func, p, exc_info):
        try:
            os.chmod(p, stat.S_IWRITE)
            func(p)
        except Exception:
            pass

    try:
        shutil.rmtree(path, onerror=on_error)
    except Exception:
        pass

    if os.path.exists(path) and os.name == "nt":
        subprocess.run(f'cmd.exe /c "rd /s /q \\"{path}\\""', shell=True, capture_output=True)


def parse_gradle_test_results(test_results_dir: str) -> Dict[str, Any]:
    """Parsea los archivos XML de resultados de testDebugUnitTest."""
    xml_pattern = os.path.join(test_results_dir, "TEST-*.xml")
    xml_files = glob.glob(xml_pattern)

    total_tests = 0
    total_failures = 0
    total_errors = 0
    total_skipped = 0
    total_time = 0.0
    classes_count = len(xml_files)
    failed_test_names = []

    for f in xml_files:
        try:
            tree = ET.parse(f)
            root = tree.getroot()
            total_tests += int(root.attrib.get("tests", 0))
            total_failures += int(root.attrib.get("failures", 0))
            total_errors += int(root.attrib.get("errors", 0))
            total_skipped += int(root.attrib.get("skipped", 0))
            total_time += float(root.attrib.get("time", 0.0))

            for tc in root.findall("testcase"):
                for fail in tc.findall("failure"):
                    name = tc.attrib.get("name", "unknown")
                    cname = tc.attrib.get("classname", "unknown")
                    msg = fail.attrib.get("message", fail.text or "")
                    failed_test_names.append(f"{cname}.{name}: {msg.strip()[:100]}")
        except Exception as e:
            print(f"Advertencia al parsear {f}: {e}")

    return {
        "classes": classes_count,
        "total_tests": total_tests,
        "failures": total_failures,
        "errors": total_errors,
        "skipped": total_skipped,
        "passed": total_tests - (total_failures + total_errors + total_skipped),
        "time_seconds": round(total_time, 2),
        "failed_tests": failed_test_names,
    }


def verify_repo_cleanliness(repo_root: str) -> Dict[str, Any]:
    """Verifica el estado de git status e higiene en el repositorio principal."""
    code, status_out, _ = run_cmd(["git", "status", "--porcelain"], cwd=repo_root)
    untracked_files = []
    modified_files = []

    for line in status_out.splitlines():
        if not line.strip():
            continue
        prefix = line[:2]
        path = line[3:].strip()
        if prefix == "??":
            untracked_files.append(path)
        else:
            modified_files.append(path)

    code, ign_check, _ = run_cmd(["git", "check-ignore", "build", ".gradle"], cwd=repo_root)
    gitignore_clean = (code == 0)

    return {
        "is_worktree_clean": len(untracked_files) == 0 and len(modified_files) == 0,
        "untracked_count": len(untracked_files),
        "modified_count": len(modified_files),
        "untracked_files": untracked_files[:20],
        "modified_files": modified_files[:20],
        "gitignore_clean": gitignore_clean,
    }


def format_markdown_report(report_data: Dict[str, Any]) -> str:
    s = report_data
    gr = s["gradle_results"]
    git_st = s["git_hygiene"]
    passed = s["success"]

    verdict_badge = "**PASS (REPRODUCIBLE)**" if passed else "**FAIL (NO REPRODUCIBLE)**"

    lines = [
        "# R21 — REPORTE DE REPRODUCIBILIDAD EN WORKTREE LIMPIO (FRENTE G)",
        "",
        f"**Fecha:** {datetime.now(timezone.utc).strftime('%Y-%m-%d %H:%M:%S UTC')}  ",
        "**Herramienta:** `scripts/ci/verify_clean_worktree.py`  ",
        f"**Commit / Ref Verificado:** `{s['target_ref']}` (`{s['target_sha']}`)  ",
        f"**Ruta Worktree Temporal:** `{s['worktree_dir']}`  ",
        f"**Veredicto Final:** {verdict_badge}  ",
        "",
        "---",
        "",
        "## 1. Resumen Ejecutivo de Reproducibilidad",
        "",
        "| Métrica / Fase | Resultado | Criterio de Aceptación |",
        "|---|---|---|",
        f"| **Creación Worktree Limpio** | `{'PASS' if s['worktree_created'] else 'FAIL'}` | Worktree aislado creado sin basura no trackeada |",
        f"| **Compilación Gradle** | `{'PASS' if s['gradle_exit_code'] == 0 else 'FAIL'}` | `compileDebugKotlin` / `compileDebugJavaWithJavac` exitosos |",
        f"| **Suite de Tests Unitarios** | **`{gr.get('total_tests', 0)} tests`** (`{gr.get('failures', 0)} fallos, {gr.get('errors', 0)} errores`) | 100% tests verdes sin dependencias ocultas |",
        f"| **Clases de Test Ejecutadas** | `{gr.get('classes', 0)} clases` | Suite completa ejecutada en JVM limpia |",
        f"| **Tiempo de Ejecución Tests** | `{gr.get('time_seconds', 0.0)} s` | Rendimiento de suite unitaria |",
        f"| **Limpieza y Desmantelamiento** | `{'PASS' if s['worktree_cleaned'] else 'FAIL'}` | Eliminación segura de directorio y `git worktree prune` |",
        "",
        "---",
        "",
        "## 2. Detalle de Ejecución de Gradle (`testDebugUnitTest`)",
        "",
        f"- **Comando ejecutado:** `{s['gradle_command']}`",
        f"- **Código de salida:** `{s['gradle_exit_code']}`",
        f"- **Duración total fase Gradle:** `{s['gradle_duration_sec']} s`",
        "",
        "```",
    ]

    # Incluir últimas líneas de salida de Gradle para auditoría
    out_lines = s.get("gradle_stdout", "").strip().splitlines()
    tail_out = out_lines[-25:] if len(out_lines) > 25 else out_lines
    lines.extend(tail_out)
    lines.extend([
        "```",
        "",
    ])

    if s["gradle_exit_code"] != 0 and s.get("gradle_stderr"):
        lines.append("**Salida de error (Stderr):**\n```")
        lines.extend(s.get("gradle_stderr", "").strip().splitlines()[-25:])
        lines.append("```\n")

    if gr.get("failed_tests"):
        lines.append("### Tests Fallidos:")
        for ft in gr["failed_tests"]:
            lines.append(f"- ❌ `{ft}`")
        lines.append("")

    lines.extend([
        "---",
        "",
        "## 3. Auditoría de Higiene del Repositorio Principal",
        "",
        f"- **Archivos modificados (en curso por otros frentes):** `{git_st['modified_count']}`",
        f"- **Archivos no trackeados (artefactos / temporales):** `{git_st['untracked_count']}`",
        f"- **Reglas .gitignore activas para artefactos:** `{'OK (build y .gradle ignorados)' if git_st['gitignore_clean'] else 'ADVERTENCIA'}`",
        "",
        "---",
        "",
        "## 4. Criterio de Aceptación Frente G",
        "",
        "- [x] **G.1:** Script que clona/crea un git worktree limpio temporal para el SHA a validar.",
        "- [x] **G.2:** Ejecuta compilación y tests unitarios dentro del worktree aislado.",
        f"- [{'x' if passed else ' '}] **G.3:** Verifica que la compilación es reproducible y pasa la suite limpia.",
        "- [x] **G.4:** Limpieza automática y segura del worktree temporal sin afectar el espacio de trabajo activo.",
    ])

    return "\n".join(lines)


def run_worktree_verification(
    repo_root: str,
    target_ref: str = "HEAD",
    include_uncommitted: bool = False,
    worktree_dir: Optional[str] = None,
    skip_gradle: bool = False,
    timeout_gradle: int = 600,
) -> Dict[str, Any]:
    """Flujo completo de validación en worktree limpio con rollback garantizado."""
    repo_name = os.path.basename(repo_root)
    if not worktree_dir:
        parent_dir = os.path.dirname(repo_root)
        worktree_dir = os.path.join(parent_dir, f"{repo_name}_repro_worktree")

    worktree_dir = os.path.normpath(worktree_dir)
    print(f"[FRENTE G] Raíz del repo:   {repo_root}")
    print(f"[FRENTE G] Ref objetivo:     {target_ref}")
    print(f"[FRENTE G] Worktree destino: {worktree_dir}")

    # Obtener SHA real del ref objetivo
    code, sha_out, _ = run_cmd(["git", "rev-parse", target_ref], cwd=repo_root)
    target_sha = sha_out.strip() if code == 0 else target_ref

    commit_to_checkout = target_ref
    stash_created = None

    if include_uncommitted:
        print("[FRENTE G] Creando commit temporal de cambios en working tree...")
        code, stash_out, _ = run_cmd(["git", "stash", "create"], cwd=repo_root)
        stash_sha = stash_out.strip()
        if code == 0 and stash_sha:
            stash_created = stash_sha
            commit_to_checkout = stash_sha
            print(f"[FRENTE G] Cambios capturados en objeto stash: {stash_sha}")

    worktree_created = False
    worktree_cleaned = False
    gradle_exit = -1
    gradle_stdout = ""
    gradle_stderr = ""
    gradle_results: Dict[str, Any] = {}
    duration_gradle = 0.0

    try:
        # 1. Limpiar worktree previo si quedó huérfano
        if os.path.exists(worktree_dir):
            print(f"[FRENTE G] Limpiando worktree previo en {worktree_dir}...")
            run_cmd(["git", "worktree", "remove", "--force", worktree_dir], cwd=repo_root)
            if os.path.exists(worktree_dir):
                safe_rmtree(worktree_dir)

        # 2. Crear el worktree temporal
        print(f"[FRENTE G] Creando worktree limpio para {commit_to_checkout}...")
        add_cmd = ["git", "worktree", "add", "--detach", worktree_dir, commit_to_checkout]
        code, out, err = run_cmd(add_cmd, cwd=repo_root)
        if code != 0:
            raise RuntimeError(f"Error al crear worktree: {err}\n{out}")
        worktree_created = True
        print("[FRENTE G] Worktree limpio creado exitosamente.")

        # Copiar local.properties si existe en repo_root (requerido por Android SDK en Windows)
        local_props_src = os.path.join(repo_root, "local.properties")
        if os.path.exists(local_props_src):
            local_props_dst = os.path.join(worktree_dir, "local.properties")
            shutil.copy2(local_props_src, local_props_dst)
            print(f"[FRENTE G] local.properties copiado al worktree limpio.")

        # Si include_uncommitted está activo, propagar archivos no trackeados de código/tests
        if include_uncommitted:
            code, status_out, _ = run_cmd(["git", "status", "--porcelain"], cwd=repo_root)
            untracked_copied = 0
            for line in status_out.splitlines():
                if line.startswith("?? "):
                    rel_path = line[3:].strip()
                    src_full = os.path.join(repo_root, rel_path)
                    dst_full = os.path.join(worktree_dir, rel_path)
                    if any(rel_path.startswith(prefix) for prefix in ["app/", "scripts/", "data/"]):
                        if os.path.isdir(src_full):
                            shutil.copytree(src_full, dst_full, dirs_exist_ok=True)
                        elif os.path.isfile(src_full):
                            os.makedirs(os.path.dirname(dst_full), exist_ok=True)
                            shutil.copy2(src_full, dst_full)
                        untracked_copied += 1
            if untracked_copied > 0:
                print(f"[FRENTE G] {untracked_copied} rutas no trackeadas propagadas al worktree de prueba.")

        # 3. Ejecutar Gradle testDebugUnitTest dentro del worktree
        use_shell = False
        if os.name == "nt":
            use_shell = True
            gradle_bin = os.path.join(worktree_dir, "gradlew.bat")
            if not os.path.exists(gradle_bin):
                gradle_bin = "gradlew.bat"
            cmd_gradle = f'"{gradle_bin}" :app:parseDebugLocalResources :app:testDebugUnitTest'
            gradle_cmd_str = cmd_gradle
        else:
            cmd_gradle = ["./gradlew", ":app:parseDebugLocalResources", ":app:testDebugUnitTest"]
            gradle_cmd_str = "./gradlew :app:parseDebugLocalResources :app:testDebugUnitTest"

        if skip_gradle:
            print("[FRENTE G] Flag --skip-gradle activo. Omitiendo compilación de Gradle.")
            gradle_exit = 0
            gradle_stdout = "Gradle test skipped by user flag."
        else:
            print("[FRENTE G] Compilando y ejecutando tests unitarios en el worktree limpio...")
            t0 = time.time()
            # Pasamos variables de entorno heredadas pero limpiando PYTHONPATH de Hermes
            build_env = os.environ.copy()
            build_env.pop("PYTHONPATH", None)

            gradle_exit, gradle_stdout, gradle_stderr = run_cmd(
                cmd_gradle,
                cwd=worktree_dir,
                timeout=timeout_gradle,
                env=build_env,
                shell=use_shell
            )
            duration_gradle = round(time.time() - t0, 2)
            print(f"[FRENTE G] Gradle finalizado en {duration_gradle}s (código de salida: {gradle_exit}).")

            # Parsear resultados XML
            results_dir = os.path.join(worktree_dir, "app", "build", "test-results", "testDebugUnitTest")
            gradle_results = parse_gradle_test_results(results_dir)

    finally:
        # 4. Desmantelar y limpiar worktree garantizado
        print("[FRENTE G] Desmantelando worktree temporal...")
        run_cmd(["git", "worktree", "remove", "--force", worktree_dir], cwd=repo_root)
        run_cmd(["git", "worktree", "prune"], cwd=repo_root)
        if os.path.exists(worktree_dir):
            safe_rmtree(worktree_dir)
        worktree_cleaned = not os.path.exists(worktree_dir)
        print(f"[FRENTE G] Worktree eliminado: {worktree_cleaned}")

    # 5. Auditoría de higiene del repositorio principal
    git_hygiene = verify_repo_cleanliness(repo_root)

    success = (worktree_created and worktree_cleaned and gradle_exit == 0 and
               gradle_results.get("failures", 0) == 0 and gradle_results.get("errors", 0) == 0)

    return {
        "success": success,
        "target_ref": target_ref,
        "target_sha": target_sha,
        "worktree_dir": worktree_dir,
        "worktree_created": worktree_created,
        "worktree_cleaned": worktree_cleaned,
        "gradle_command": gradle_cmd_str,
        "gradle_exit_code": gradle_exit,
        "gradle_duration_sec": duration_gradle,
        "gradle_stdout": gradle_stdout,
        "gradle_stderr": gradle_stderr,
        "gradle_results": gradle_results,
        "git_hygiene": git_hygiene,
    }


def self_test():
    """Autochequeo interno de funciones clave del script."""
    print("=== RUNNING SELF-TEST: verify_clean_worktree ===")
    repo_root = get_git_root(".")
    assert os.path.isdir(repo_root), "Root must be a valid directory"
    hygiene = verify_repo_cleanliness(repo_root)
    assert "is_worktree_clean" in hygiene
    assert "gitignore_clean" in hygiene
    print(f"Git hygiene check passed (untracked: {hygiene['untracked_count']}, modified: {hygiene['modified_count']})")
    print("ALL SELF-TEST ASSERTIONS PASSED (OK)")


def main():
    parser = argparse.ArgumentParser(description="Verificador de Reproducibilidad en Worktree Limpio (Frente G)")
    parser.add_argument("--commit", default="HEAD", help="Ref o commit SHA a verificar (default: HEAD)")
    parser.add_argument("--include-uncommitted", action="store_true", help="Incluir cambios locales no commiteados vía stash")
    parser.add_argument("--worktree-dir", help="Directorio temporal para el worktree")
    parser.add_argument("--skip-gradle", action="store_true", help="Omitir compilación Gradle (dry-run de worktree)")
    parser.add_argument("--output-md", default="workflows/rounds/r21_worktree_reproducibility_report.md", help="Ruta para el reporte Markdown")
    parser.add_argument("--output-json", help="Ruta para guardar el resumen estructurado en JSON")
    parser.add_argument("--self-test", action="store_true", help="Ejecutar autochequeo interno")

    args = parser.parse_args()

    if args.self_test:
        self_test()
        sys.exit(0)

    repo_root = get_git_root(".")

    report_data = run_worktree_verification(
        repo_root=repo_root,
        target_ref=args.commit,
        include_uncommitted=args.include_uncommitted,
        worktree_dir=args.worktree_dir,
        skip_gradle=args.skip_gradle,
    )

    # Imprimir resumen
    s = report_data
    gr = s["gradle_results"]
    print("=" * 65)
    print("VERIFICACIÓN DE REPRODUCIBILIDAD EN WORKTREE LIMPIO")
    print("=" * 65)
    print(f"Veredicto:          {'PASS (REPRODUCIBLE)' if s['success'] else 'FAIL'}")
    print(f"Ref validado:       {s['target_ref']} ({s['target_sha'][:8]})")
    print(f"Worktree creado:    {s['worktree_created']} | Limpiado: {s['worktree_cleaned']}")
    print(f"Salida Gradle:      {s['gradle_exit_code']} (Duración: {s['gradle_duration_sec']}s)")
    if gr:
        print(f"Tests ejecutados:   {gr.get('total_tests', 0)} (Fallos: {gr.get('failures', 0)}, Errores: {gr.get('errors', 0)}, Omitidos: {gr.get('skipped', 0)})")
        print(f"Clases evaluadas:   {gr.get('classes', 0)} clases ({gr.get('time_seconds', 0)}s)")

    # Guardar reporte Markdown
    if args.output_md:
        md_path = os.path.normpath(os.path.join(repo_root, args.output_md))
        os.makedirs(os.path.dirname(md_path), exist_ok=True)
        md_content = format_markdown_report(report_data)
        with open(md_path, "w", encoding="utf-8") as f:
            f.write(md_content)
        print(f"[FRENTE G] Reporte Markdown guardado en: {md_path}")

    # Guardar JSON si se especificó
    if args.output_json:
        json_path = os.path.normpath(os.path.join(repo_root, args.output_json))
        os.makedirs(os.path.dirname(json_path), exist_ok=True)
        with open(json_path, "w", encoding="utf-8") as f:
            json.dump(report_data, f, indent=2, ensure_ascii=False)
        print(f"[FRENTE G] Salida JSON guardada en: {json_path}")

    sys.exit(0 if s["success"] else 1)


if __name__ == "__main__":
    main()
