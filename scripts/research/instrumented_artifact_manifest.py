#!/usr/bin/env python3
"""Generate a deterministic manifest for an instrumented TradeDraw APK."""

from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
import re
import subprocess

DEFAULT_VERSION = "r29c-instrumented-policy-v1"
BASE_VERSION = "r29c-frozen-4fdb646"


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def git_head(repo: Path) -> str:
    return subprocess.check_output(
        ["git", "rev-parse", "--short=7", "HEAD"], cwd=repo, text=True
    ).strip()


def build_config_sha(path: Path) -> str:
    match = re.search(r'GIT_SHA\s*=\s*"([0-9a-f]+)"', path.read_text(encoding="utf-8"))
    if not match:
        raise ValueError("GIT_SHA_NOT_FOUND")
    return match.group(1)


def create_manifest(repo: Path, apk: Path, build_config: Path) -> dict:
    head = git_head(repo)
    embedded = build_config_sha(build_config)
    if embedded != head:
        raise ValueError(f"BUILD_SHA_MISMATCH embedded={embedded} head={head}")
    return {
        "schema_version": 1,
        "artifact": str(apk.relative_to(repo)),
        "apk_sha256": sha256(apk),
        "git_sha": embedded,
        "strategy_version": DEFAULT_VERSION,
        "base_strategy_version": BASE_VERSION,
        "policy_context_quality": "EXACT_PRE_OUTCOME_EXECUTION_POLICY_INPUTS",
        "policy_context_version": 1,
        "runtime_probe_expected_git_sha": embedded,
        "allowed_runtime_mode": "SHADOW",
        "real_money_authorized": False,
    }


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--repo", default=".")
    parser.add_argument(
        "--apk", default="app/build/outputs/apk/debug/app-debug.apk"
    )
    parser.add_argument(
        "--build-config",
        default=(
            "app/build/generated/source/buildConfig/debug/"
            "com/example/tradedraw/BuildConfig.java"
        ),
    )
    parser.add_argument(
        "--out", default="build/instrumented-artifact-manifest.json"
    )
    args = parser.parse_args()

    repo = Path(args.repo).resolve()
    apk = (repo / args.apk).resolve()
    config = (repo / args.build_config).resolve()
    out = (repo / args.out).resolve()
    for path in (apk, config):
        if not path.is_file():
            raise FileNotFoundError(path)

    manifest = create_manifest(repo, apk, config)
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(json.dumps(manifest, indent=2, sort_keys=True) + "\n")
    print(json.dumps(manifest, indent=2, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
