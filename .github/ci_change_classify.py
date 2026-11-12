#!/usr/bin/env python3
"""Classify a GitHub Actions change set conservatively.

Only all-Markdown changes are eligible for the lightweight CI path.
Everything else, including empty/unknown change sets, requires the full gate.
"""

from __future__ import annotations

import argparse
from pathlib import Path


def normalize(path: str) -> str:
    return path.strip().replace("\\", "/").lstrip("./")


def is_markdown_only(paths: list[str]) -> bool:
    normalized = [normalize(path) for path in paths if normalize(path)]
    return bool(normalized) and all(Path(path).suffix.lower() == ".md" for path in normalized)


def classify(paths: list[str]) -> dict[str, str]:
    docs_only = is_markdown_only(paths)
    return {
        "docs_only": "true" if docs_only else "false",
        "heavy": "false" if docs_only else "true",
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
