#!/usr/bin/env python3
"""Classify a GitHub Actions change set conservatively.

Three gates:
- docs_only: non-empty and every changed file is Markdown.
- python_only: non-empty, at least one approved Python/tooling file, and every
  other file is Markdown or approved Python/tooling.
- heavy: empty/unknown or anything that can affect Android, Gradle, datasets,
  workflows, configuration, or other executable surfaces.

The whitelist is intentionally narrow. Unknown files always fall back to heavy.
"""

from __future__ import annotations

import argparse
from pathlib import Path


PYTHON_ONLY_ROOTS = (
    "scripts/ci/",
    "scripts/research/",
    "scripts/validation/",
    "scripts/backtest/",
    "tests/",
    "validation_engine/",
)
PYTHON_ONLY_EXACT = {
    "scripts/ci/requirements-tests.txt",
}


def normalize(path: str) -> str:
    normalized = path.strip().replace("\\", "/")
    while normalized.startswith("./"):
        normalized = normalized[2:]
    return normalized


def normalized_paths(paths: list[str]) -> list[str]:
    return [value for raw in paths if (value := normalize(raw))]


def is_markdown(path: str) -> bool:
    return Path(path).suffix.lower() == ".md"


def is_python_tooling(path: str) -> bool:
    if path in PYTHON_ONLY_EXACT:
        return True
    return path.endswith(".py") and any(path.startswith(root) for root in PYTHON_ONLY_ROOTS)


def is_markdown_only(paths: list[str]) -> bool:
    values = normalized_paths(paths)
    return bool(values) and all(is_markdown(path) for path in values)


def is_python_only(paths: list[str]) -> bool:
    values = normalized_paths(paths)
    if not values:
        return False
    has_tooling = any(is_python_tooling(path) for path in values)
    return has_tooling and all(
        is_markdown(path) or is_python_tooling(path)
        for path in values
    )


def classify(paths: list[str]) -> dict[str, str]:
    docs_only = is_markdown_only(paths)
    python_only = not docs_only and is_python_only(paths)
    # Full Android validation includes the Python suite too.
    needs_python = not docs_only
    needs_android = not docs_only and not python_only

    # Heavy remains as a compatibility alias for Android/full validation.
    heavy = needs_android
    return {
        "docs_only": "true" if docs_only else "false",
        "python_only": "true" if python_only else "false",
        "needs_python": "true" if needs_python else "false",
        "needs_android": "true" if needs_android else "false",
        "heavy": "true" if heavy else "false",
    }


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--paths-file", type=Path, required=True)
    parser.add_argument(
        "--github-output",
        type=Path,
        help="Append key=value outputs for GitHub Actions.",
    )
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    paths = args.paths_file.read_text(encoding="utf-8").splitlines()
    result = classify(paths)

    print(f"changed_files={len([p for p in paths if p.strip()])}")
    for key, value in result.items():
        print(f"{key}={value}")

    if args.github_output:
        with args.github_output.open("a", encoding="utf-8") as handle:
            for key, value in result.items():
                handle.write(f"{key}={value}\n")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
