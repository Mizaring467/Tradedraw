#!/usr/bin/env python3
"""Impact-aware verification runner for TradeDraw.

Fast iteration:
  python scripts/ci/fast_verify.py --plan
  python scripts/ci/fast_verify.py
  python scripts/ci/fast_verify.py --base origin/r10-night

Final gate:
  python scripts/ci/fast_verify.py --full

By default, committed changes from --base..HEAD plus working-tree/untracked
changes are included. Without --base, only working-tree/untracked changes are
considered. The runner derives Gradle module dependencies from build.gradle.kts
instead of maintaining a second hand-written dependency graph.
"""

from __future__ import annotations

import argparse
import os
from pathlib import Path
import re
import shlex
import subprocess
import sys
import xml.etree.ElementTree as ET
from dataclasses import dataclass
from typing import Iterable, Sequence

PROJECT_RE = re.compile(r"""project\(\s*["']:([^"']+)["']\s*\)""")
ANDROID_PLUGIN_RE = re.compile(r"libs\.plugins\.android\.(?:application|library)")
JVM_PLUGIN_RE = re.compile(r"libs\.plugins\.kotlin\.jvm")

IGNORED_PREFIXES = (
    ".git/",
    ".gradle/",
    "graft/",
)
IGNORED_PARTS = {"/build/", "/__pycache__/"}

ROOT_GRADLE_FILES = {
    "build.gradle.kts",
    "settings.gradle.kts",
    "gradle.properties",
    "gradle/libs.versions.toml",
    "gradle/wrapper/gradle-wrapper.properties",
}

PYTHON_RELEVANT_PREFIXES = (
    "scripts/",
    "tests/",
    "validation_engine/",
)
REPLAY_RELEVANT_PREFIXES = (
    "research/",
    "strategy/",
    "scripts/backtest/",
    "scripts/validation/",
    "data/",
    "loops/r23",
)


@dataclass(frozen=True)
class VerificationPlan:
    changed_paths: tuple[str, ...]
    direct_modules: tuple[str, ...]
    affected_modules: tuple[str, ...]
    gradle_tasks: tuple[str, ...]
    run_python: bool
    run_replay: bool
    build_apk: bool
    full: bool

    @property
    def empty(self) -> bool:
        return not (
            self.gradle_tasks
            or self.run_python
            or self.run_replay
            or self.build_apk
        )


def run_git(repo: Path, *args: str) -> str:
    proc = subprocess.run(
        ["git", *args],
        cwd=repo,
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        check=False,
    )
    if proc.returncode != 0:
        raise RuntimeError(
            f"git {' '.join(args)} failed ({proc.returncode}): {proc.stderr.strip()}"
        )
    return proc.stdout


def normalize_path(raw: str) -> str:
    return raw.strip().replace("\\", "/").lstrip("./")


def is_ignored(path: str) -> bool:
    if any(path.startswith(prefix) for prefix in IGNORED_PREFIXES):
        return True
    wrapped = f"/{path}/"
    return any(part in wrapped for part in IGNORED_PARTS)


def collect_changed_paths(repo: Path, base: str | None) -> list[str]:
    paths: set[str] = set()

    if base:
        diff = run_git(repo, "diff", "--name-only", f"{base}..HEAD")
        paths.update(normalize_path(p) for p in diff.splitlines() if p.strip())

    worktree = run_git(repo, "diff", "--name-only", "HEAD")
    paths.update(normalize_path(p) for p in worktree.splitlines() if p.strip())

    staged = run_git(repo, "diff", "--cached", "--name-only")
    paths.update(normalize_path(p) for p in staged.splitlines() if p.strip())

    untracked = run_git(repo, "ls-files", "--others", "--exclude-standard")
    paths.update(normalize_path(p) for p in untracked.splitlines() if p.strip())

    return sorted(p for p in paths if p and not is_ignored(p))


def discover_modules(repo: Path) -> dict[str, Path]:
    modules: dict[str, Path] = {}
    settings = repo / "settings.gradle.kts"
    if not settings.exists():
        raise RuntimeError("settings.gradle.kts not found")

    include_re = re.compile(r"""["']:([^"']+)["']""")
    for line in settings.read_text(encoding="utf-8").splitlines():
        stripped = line.strip()
        if not stripped.startswith("include("):
            continue
        for name in include_re.findall(stripped):
            module_dir = repo / name
            if (module_dir / "build.gradle.kts").exists():
                modules[name] = module_dir
    return modules


def parse_module_graph(
    repo: Path, modules: dict[str, Path]
) -> dict[str, set[str]]:
    graph: dict[str, set[str]] = {name: set() for name in modules}
    for name, module_dir in modules.items():
        text = (module_dir / "build.gradle.kts").read_text(encoding="utf-8")
        graph[name] = {
            dep
            for dep in PROJECT_RE.findall(text)
            if dep in modules and dep != name
        }
    return graph


def reverse_graph(graph: dict[str, set[str]]) -> dict[str, set[str]]:
    reverse: dict[str, set[str]] = {name: set() for name in graph}
    for module, deps in graph.items():
        for dep in deps:
            reverse.setdefault(dep, set()).add(module)
    return reverse


def downstream_closure(
    direct: Iterable[str], graph: dict[str, set[str]]
) -> set[str]:
    reverse = reverse_graph(graph)
    affected = set(direct)
    queue = list(affected)
    while queue:
        current = queue.pop()
        for dependent in reverse.get(current, ()):
            if dependent not in affected:
                affected.add(dependent)
                queue.append(dependent)
    return affected


def modules_for_paths(
    changed_paths: Sequence[str], modules: dict[str, Path]
) -> tuple[set[str], bool]:
    module_names = set(modules)
    direct: set[str] = set()
    force_all = False

    for path in changed_paths:
        if path in ROOT_GRADLE_FILES or path.startswith(".github/workflows/"):
            force_all = True
            continue
        first = path.split("/", 1)[0]
        if first in module_names:
            direct.add(first)
    return direct, force_all


def module_test_task(module: str, module_dir: Path) -> str:
    text = (module_dir / "build.gradle.kts").read_text(encoding="utf-8")
    if JVM_PLUGIN_RE.search(text):
        return f":{module}:test"
    if ANDROID_PLUGIN_RE.search(text):
        return f":{module}:testDebugUnitTest"
    return f":{module}:check"


def any_prefix(paths: Sequence[str], prefixes: Sequence[str]) -> bool:
    return any(path.startswith(prefix) for path in paths for prefix in prefixes)


def build_plan(
    repo: Path,
    changed_paths: Sequence[str],
    *,
    full: bool = False,
    apk: bool = False,
) -> VerificationPlan:
    modules = discover_modules(repo)
    graph = parse_module_graph(repo, modules)

    if full:
        affected = set(modules)
        direct = set(modules)
        run_python = True
        run_replay = True
        build_apk = True
    else:
        direct, force_all = modules_for_paths(changed_paths, modules)
        affected = set(modules) if force_all else downstream_closure(direct, graph)

        run_python = any_prefix(changed_paths, PYTHON_RELEVANT_PREFIXES)
        run_replay = any_prefix(changed_paths, REPLAY_RELEVANT_PREFIXES)
        build_apk = apk or "app" in affected

        if force_all:
            run_python = True
            run_replay = True
            build_apk = True

    tasks = sorted(
        {module_test_task(module, modules[module]) for module in affected}
    )

    return VerificationPlan(
        changed_paths=tuple(sorted(changed_paths)),
        direct_modules=tuple(sorted(direct)),
        affected_modules=tuple(sorted(affected)),
        gradle_tasks=tuple(tasks),
        run_python=run_python,
        run_replay=run_replay,
        build_apk=build_apk,
        full=full,
    )


def print_plan(plan: VerificationPlan) -> None:
    print("=== TradeDraw impact-aware verification plan ===")
    print(f"mode: {'FULL' if plan.full else 'FAST'}")
    print(f"changed files: {len(plan.changed_paths)}")
    for path in plan.changed_paths[:30]:
        print(f"  - {path}")
    if len(plan.changed_paths) > 30:
        print(f"  ... +{len(plan.changed_paths) - 30} more")

    print("direct modules:", ", ".join(plan.direct_modules) or "(none)")
    print("affected modules:", ", ".join(plan.affected_modules) or "(none)")
    print("gradle tasks:", " ".join(plan.gradle_tasks) or "(none)")
    print("python suite:", "YES" if plan.run_python else "no")
    print("replay smoke:", "YES" if plan.run_replay else "no")
    print("APK build:", "YES" if plan.build_apk else "no")


def canonical_replay_fixture(repo: Path) -> Path:
    return repo / "loops/r23c_phase3_validation/window_01/ticks_live.csv"


def verification_environment(repo: Path) -> dict[str, str]:
    """Use the same deterministic replay inputs locally that CI uses."""
    env = os.environ.copy()
    env["PYTHONPATH"] = os.pathsep.join(
        [str(repo), str(repo / "validation_engine")]
    )
    fixture = canonical_replay_fixture(repo)
    if fixture.exists():
        env["BACKTEST_CSV"] = str(fixture)
        env["BACKTEST_OUT"] = str(repo / "build" / "fast-verify-replay.csv")
    return env


def run_command(
    repo: Path,
    command: Sequence[str],
    label: str,
    *,
    env: dict[str, str] | None = None,
) -> None:
    printable = " ".join(shlex.quote(part) for part in command)
    print(f"\n>>> [{label}] {printable}", flush=True)
    proc = subprocess.run(command, cwd=repo, env=env)
    if proc.returncode != 0:
        raise SystemExit(f"{label} failed with exit code {proc.returncode}")


def replay_command(repo: Path) -> list[str] | None:
    fixture = canonical_replay_fixture(repo)
    if not fixture.exists():
        print("[replay] canonical versioned fixture is missing; skipping replay smoke")
        return None
    return [
        sys.executable,
        "scripts/backtest/replay.py",
        str(fixture),
        "--solo-conteo",
    ]

def gradle_command(plan: VerificationPlan, *, cold: bool = False) -> list[str]:
    tasks = list(plan.gradle_tasks)
    if plan.build_apk and ":app:assembleDebug" not in tasks:
        tasks.append(":app:assembleDebug")
    if not tasks:
        return []

    command = ["./gradlew"]
    # Full certification must invalidate stale outputs before running the graph.
    if plan.full:
        command.append("clean")
    command.extend(tasks)
    command.append("--no-configuration-cache")
    # Fast iteration intentionally reuses the Gradle daemon. Full/cold gates
    # use a fresh daemon for reproducibility and parity with CI expectations.
    if cold or plan.full:
        command.append("--no-daemon")
    return command


def collect_gradle_test_summary(repo: Path) -> dict[str, int]:
    summary = {"tests": 0, "failures": 0, "errors": 0, "skipped": 0, "classes": 0}
    for xml_file in repo.glob("*/build/test-results/*/TEST-*.xml"):
        root = ET.parse(xml_file).getroot()
        summary["classes"] += 1
        for key in ("tests", "failures", "errors", "skipped"):
            summary[key] += int(root.attrib.get(key, 0))
    return summary


def enforce_full_test_summary(repo: Path) -> None:
    summary = collect_gradle_test_summary(repo)
    print(
        "\n=== Gradle test summary ===\n"
        f"tests={summary['tests']} classes={summary['classes']} "
        f"failures={summary['failures']} errors={summary['errors']} skipped={summary['skipped']}"
    )
    if summary["tests"] <= 0 or summary["classes"] <= 0:
        raise SystemExit("full gate produced no Gradle test XML results")
    if summary["failures"] or summary["errors"] or summary["skipped"]:
        raise SystemExit("full gate requires 0 failures, 0 errors and 0 skipped tests")


def execute_plan(
    repo: Path, plan: VerificationPlan, *, cold: bool = False
) -> None:
    if plan.empty:
        print("\nNo verification tasks selected for these changes.")
        return

    env = verification_environment(repo)

    if plan.run_python:
        run_command(
            repo,
            [sys.executable, "-m", "unittest", "discover", "-s", "tests"],
            "python",
            env=env,
        )

    gradle = gradle_command(plan, cold=cold)
    if gradle:
        run_command(repo, gradle, "gradle", env=env)
        if plan.full:
            enforce_full_test_summary(repo)

    if plan.run_replay:
        command = replay_command(repo)
        if command:
            run_command(repo, command, "replay-smoke", env=env)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Run only the verification gates impacted by current changes."
    )
    parser.add_argument(
        "--base",
        help="Include committed changes in BASE..HEAD in addition to working tree changes.",
    )
    parser.add_argument(
        "--plan",
        action="store_true",
        help="Print the plan without executing it.",
    )
    parser.add_argument(
        "--full",
        action="store_true",
        help="Run the complete multi-module/Python/replay/APK gate.",
    )
    parser.add_argument(
        "--apk",
        action="store_true",
        help="Force APK build even when app is not downstream of the change.",
    )
    parser.add_argument(
        "--cold",
        action="store_true",
        help="Disable the Gradle daemon for a cold/reproducible verification run.",
    )
    parser.add_argument(
        "--repo",
        default=".",
        help="Repository root (default: current directory).",
    )
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    repo = Path(args.repo).resolve()
    changed = collect_changed_paths(repo, args.base)
    plan = build_plan(repo, changed, full=args.full, apk=args.apk)
    print_plan(plan)

    if not args.plan:
        execute_plan(repo, plan, cold=args.cold)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
