#!/usr/bin/env python3
"""Detect cross-agent/worktree edit collisions before integration.

Examples:
  python scripts/ci/worktree_overlap.py
  python scripts/ci/worktree_overlap.py --strict-scope
  python scripts/ci/worktree_overlap.py --claim research --claim loops/r29c
  python scripts/ci/worktree_overlap.py --json

The tool compares both committed divergence (from the merge-base) and current
working-tree/untracked changes across every registered git worktree. Optional
claims make the guard preventive: an agent can declare the module/scope/path it
plans to edit before touching files and fail fast if another worktree already
owns that logical area.
"""

from __future__ import annotations

import argparse
import json
from pathlib import Path
import subprocess
import sys
from dataclasses import dataclass, asdict
from typing import Iterable


GLOBAL_FILES = {
    "settings.gradle.kts",
    "build.gradle.kts",
    "gradle.properties",
    "gradle/libs.versions.toml",
}
SCOPED_ROOTS = {"scripts", "loops", "data", "datasets", "experiments", ".github", "tests"}


@dataclass(frozen=True)
class WorktreeInfo:
    path: str
    head: str
    branch: str | None


@dataclass(frozen=True)
class OverlapResult:
    current: str
    other: str
    other_branch: str | None
    exact_paths: tuple[str, ...]
    shared_scopes: tuple[str, ...]
    claimed_conflicts: tuple[str, ...]
    current_changes: tuple[str, ...]
    other_changes: tuple[str, ...]


def git(cwd: Path, *args: str, check: bool = True) -> str:
    proc = subprocess.run(
        ["git", *args],
        cwd=cwd,
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        check=False,
    )
    if check and proc.returncode != 0:
        raise RuntimeError(
            f"git {' '.join(args)} failed ({proc.returncode}): {proc.stderr.strip()}"
        )
    return proc.stdout


def parse_worktree_porcelain(text: str) -> list[WorktreeInfo]:
    items: list[WorktreeInfo] = []
    current: dict[str, str | None] = {}
    for line in text.splitlines() + [""]:
        if not line:
            if current.get("path") and current.get("head"):
                items.append(
                    WorktreeInfo(
                        path=str(current["path"]),
                        head=str(current["head"]),
                        branch=current.get("branch"),
                    )
                )
            current = {}
            continue
        key, _, value = line.partition(" ")
        if key == "worktree":
            current["path"] = value
        elif key == "HEAD":
            current["head"] = value
        elif key == "branch":
            current["branch"] = value.removeprefix("refs/heads/")
        elif key == "detached":
            current["branch"] = None
    return items


def list_worktrees(repo: Path) -> list[WorktreeInfo]:
    return parse_worktree_porcelain(git(repo, "worktree", "list", "--porcelain"))


def working_changes(worktree: Path) -> set[str]:
    paths: set[str] = set()
    for args in (
        ("diff", "--name-only", "--no-renames", "-z", "HEAD"),
        ("diff", "--cached", "--name-only", "--no-renames", "-z"),
        ("ls-files", "--others", "--exclude-standard", "-z"),
    ):
        output = git(worktree, *args)
        paths.update(path for path in output.split("\0") if path)
    return paths


def committed_changes_since(
    repo: Path, merge_base: str, head: str
) -> set[str]:
    output = git(repo, "diff", "--name-only", "--no-renames", "-z", f"{merge_base}..{head}")
    return {path for path in output.split("\0") if path}


def pair_changes(
    current_repo: Path,
    current: WorktreeInfo,
    other: WorktreeInfo,
) -> tuple[set[str], set[str]]:
    base = git(current_repo, "merge-base", current.head, other.head).strip()
    current_committed = committed_changes_since(current_repo, base, current.head)
    other_committed = committed_changes_since(current_repo, base, other.head)
    current_all = current_committed | working_changes(Path(current.path))
    other_all = other_committed | working_changes(Path(other.path))
    return current_all, other_all


def module_roots(repo: Path) -> set[str]:
    roots: set[str] = set()
    settings = repo / "settings.gradle.kts"
    if not settings.exists():
        return roots
    for child in repo.iterdir():
        if child.is_dir() and (child / "build.gradle.kts").exists():
            roots.add(child.name)
    return roots


def scope_for(path: str, modules: set[str]) -> str:
    if path in GLOBAL_FILES or path.startswith("gradle/"):
        return "GLOBAL"
    parts = path.split("/")
    if not parts:
        return path
    first = parts[0]
    if first in modules:
        return first
    if first in SCOPED_ROOTS and len(parts) >= 2:
        return f"{first}/{parts[1]}"
    return first


def scopes(paths: Iterable[str], modules: set[str]) -> set[str]:
    return {scope_for(path, modules) for path in paths}




def normalize_claims(
    claims: Iterable[str], modules: set[str], repo: Path | None = None,
) -> set[str]:
    """Keep explicit path granularity; module/directory claims cover descendants.

    No existence or extension heuristic is needed: component boundaries distinguish
    siblings, including new/deleted files. Global config retains project-wide scope.
    Absolute paths require a repo and must be inside it; traversal is rejected.
    """
    normalized: set[str] = set()
    for raw in claims:
        token = raw.replace("\\", "/")
        if not token.strip() or ".." in token.split("/") or "\0" in token:
            raise ValueError(f"invalid claim: {raw!r}")
        if len(token) >= 2 and token[1] == ":":
            raise ValueError(f"drive-qualified claim is unsupported: {raw!r}")
        path = Path(token)
        if path.is_absolute():
            if repo is None:
                raise ValueError(f"absolute claim requires repository root: {raw!r}")
            try:
                token = path.relative_to(repo.resolve()).as_posix()
            except ValueError:
                raise ValueError(f"claim outside repository: {raw!r}") from None
        token = "/".join(part for part in token.split("/") if part and part != ".")
        if repo is not None:
            candidate = repo / token
            if candidate.resolve() != candidate.absolute():
                raise ValueError(f"claim contains a symlink: {raw!r}")
        if not token:
            token = "GLOBAL"  # claiming the repository root covers everything
        if token == "gradle" or scope_for(token, modules) == "GLOBAL":
            token = "GLOBAL"
        normalized.add(token)
    return normalized


def claimed_conflicts_for(
    claimed_scopes: set[str],
    other_changes: set[str],
    modules: set[str],
) -> set[str]:
    if not claimed_scopes or not other_changes:
        return set()
    conflicts = {
        claim for claim in claimed_scopes
        if any(claim == path or path.startswith(claim + "/")
               or claim.startswith(path + "/") for path in other_changes)
    }
    if "GLOBAL" in claimed_scopes or "GLOBAL" in scopes(other_changes, modules):
        conflicts.add("GLOBAL")
    return conflicts


def analyze(repo: Path, claims: Iterable[str] = ()) -> list[OverlapResult]:
    repo = repo.resolve()
    worktrees = list_worktrees(repo)
    current = next(
        (w for w in worktrees if Path(w.path).resolve() == repo),
        None,
    )
    if current is None:
        raise RuntimeError(f"current path is not a registered worktree: {repo}")

    modules = module_roots(repo)
    claimed_scopes = normalize_claims(claims, modules, repo)
    results: list[OverlapResult] = []
    for other in worktrees:
        if other.path == current.path:
            continue
        current_changes, other_changes = pair_changes(repo, current, other)
        exact = tuple(sorted(current_changes & other_changes))
        current_scopes = scopes(current_changes, modules)
        other_scopes = scopes(other_changes, modules)
        shared_set = current_scopes & other_scopes
        # Root Gradle/config changes have project-wide blast radius even when
        # the other worktree edits a different logical scope.
        if (("GLOBAL" in current_scopes and other_changes) or
                ("GLOBAL" in other_scopes and current_changes)):
            shared_set.add("GLOBAL")
        shared = tuple(sorted(shared_set))
        claimed_conflicts = tuple(sorted(
            claimed_conflicts_for(claimed_scopes, other_changes, modules)
        ))
        results.append(
            OverlapResult(
                current=current.path,
                other=other.path,
                other_branch=other.branch,
                exact_paths=exact,
                shared_scopes=shared,
                claimed_conflicts=claimed_conflicts,
                current_changes=tuple(sorted(current_changes)),
                other_changes=tuple(sorted(other_changes)),
            )
        )
    return results


def print_human(
    results: list[OverlapResult],
    *,
    claims_present: bool = False,
) -> None:
    print("=== TradeDraw worktree collision guard ===")
    if not results:
        print("No other worktrees registered.")
        return

    if claims_present:
        conflicts = [result for result in results if result.claimed_conflicts]
        if not conflicts:
            print(f"Claim clear across {len(results)} other worktree(s).")
            return

        for result in conflicts:
            branch = result.other_branch or "(detached)"
            print(f"\nAgainst: {result.other} [{branch}]")
            print("  CLAIM CONFLICTS:", ", ".join(result.claimed_conflicts))
        return

    for result in results:
        branch = result.other_branch or "(detached)"
        print(f"\nAgainst: {result.other} [{branch}]")
        print(f"  current changed paths: {len(result.current_changes)}")
        print(f"  other changed paths:   {len(result.other_changes)}")
        if result.exact_paths:
            print("  DIRECT FILE COLLISIONS:")
            for path in result.exact_paths:
                print(f"    ! {path}")
        else:
            print("  direct file collisions: none")

        non_global_scopes = [s for s in result.shared_scopes if s != "GLOBAL"]
        if "GLOBAL" in result.shared_scopes:
            print("  scope warning: GLOBAL build/config files changed on both sides")
        if non_global_scopes:
            print("  shared logical scopes:", ", ".join(non_global_scopes))
        elif "GLOBAL" not in result.shared_scopes:
            print("  shared logical scopes: none")
        if result.claimed_conflicts:
            print("  CLAIM CONFLICTS:", ", ".join(result.claimed_conflicts))
        else:
            print("  claim conflicts: none")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Detect file/scope overlap across registered git worktrees."
    )
    parser.add_argument("--repo", default=".", help="Current worktree root.")
    parser.add_argument(
        "--strict-scope",
        action="store_true",
        help="Exit non-zero on logical-scope overlap, not only exact files.",
    )
    parser.add_argument(
        "--claim",
        action="append",
        default=[],
        metavar="SCOPE_OR_PATH",
        help=(
            "Declare an intended module/scope/path before editing. Repeatable. "
            "Paths retain their granularity; directories/modules cover descendants. "
            "GLOBAL and Gradle config cover the project. "
            "Examples: --claim research --claim loops/r29c --claim app/src/Main.kt"
        ),
    )
    parser.add_argument("--json", action="store_true", help="Emit JSON.")
    return parser.parse_args()




def exit_code(
    results: list[OverlapResult],
    *,
    strict_scope: bool,
    claims_present: bool,
) -> int:
    """Claims are prospective: unrelated historical divergence must not block them."""
    if claims_present:
        return 4 if any(r.claimed_conflicts for r in results) else 0
    if any(r.exact_paths for r in results):
        return 2
    if strict_scope and any(r.shared_scopes for r in results):
        return 3
    return 0


def main() -> int:
    args = parse_args()
    repo = Path(args.repo).resolve()
    try:
        results = analyze(repo, args.claim)
    except ValueError as exc:
        print(f"Invalid claim: {exc}", file=sys.stderr)
        return 1

    if args.json:
        print(json.dumps([asdict(r) for r in results], indent=2))
    else:
        print_human(results, claims_present=bool(args.claim))

    return exit_code(
        results,
        strict_scope=args.strict_scope,
        claims_present=bool(args.claim),
    )


if __name__ == "__main__":
    raise SystemExit(main())
