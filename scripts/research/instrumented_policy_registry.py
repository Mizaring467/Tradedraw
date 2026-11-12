#!/usr/bin/env python3
"""Register and verify the R29-C exact policy-context instrumentation variant.

This intentionally does NOT overwrite the frozen r29c-frozen-4fdb646 registry.
The instrumentation release is accepted only when:
- the frozen registry is still readable and internally consistent;
- exactly one of its 44 registered production sources differs in the worktree;
- that single source is TradingEngine.kt;
- ExecutionPolicy and FrozenStrategySignals remain byte-identical to frozen;
- the approved instrumentation support files are hashed into a separate manifest.
"""

from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parents[2]
FROZEN_REGISTRY = ROOT / "loops/r29c/evidence/strategy_registry_v2.json"
OUT = ROOT / "loops/r29c/evidence/strategy_registry_instrumented_policy_v1.json"

VERSION = "r29c-instrumented-policy-v1"
BASE_VERSION = "r29c-frozen-4fdb646"
BASE_GIT_SHA = "4fdb6466d5005499789d801d64916ea9c15421a3"
PARENT_MAIN_SHA = "21b0a55"

TRADING_ENGINE = "app/src/main/java/com/example/tradedraw/TradingEngine.kt"
EXECUTION_POLICY = "execution/src/main/java/com/example/tradedraw/execution/ExecutionPolicy.kt"
FROZEN_SIGNALS = "strategy/src/main/java/com/example/tradedraw/FrozenStrategySignals.kt"

APPROVED_SUPPORT = (
    TRADING_ENGINE,
    "app/src/main/java/com/example/tradedraw/ExecutionPolicyEvidenceCapture.kt",
    "app/src/main/java/com/example/tradedraw/ProspectivePredictionExport.kt",
    "simulation/src/main/java/com/example/tradedraw/simulation/SimLedger.kt",
    "simulation/src/main/java/com/example/tradedraw/simulation/FileSimSnapshotStore.kt",
)


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def canonical_bundle(mapping: dict[str, str]) -> str:
    payload = json.dumps(mapping, sort_keys=True, separators=(",", ":")).encode()
    return hashlib.sha256(payload).hexdigest()


def load_frozen() -> tuple[list[dict[str, Any]], dict[str, str]]:
    rows = json.loads(FROZEN_REGISTRY.read_text(encoding="utf-8"))
    if len(rows) != 14:
        raise AssertionError(f"expected 14 frozen modes, got {len(rows)}")
    sources = rows[0]["supporting_sources"]
    if len(sources) != 44:
        raise AssertionError(f"expected 44 frozen sources, got {len(sources)}")
    for row in rows:
        if row["version"] != BASE_VERSION:
            raise AssertionError(f"unexpected frozen version: {row['version']}")
        if row["git_sha"] != BASE_GIT_SHA:
            raise AssertionError(f"unexpected frozen SHA: {row['git_sha']}")
        if row["supporting_sources"] != sources:
            raise AssertionError("frozen registry modes disagree on supporting_sources")
    return rows, sources


def current_registered_sources(frozen_sources: dict[str, str]) -> dict[str, str]:
    current: dict[str, str] = {}
    for rel in frozen_sources:
        path = ROOT / rel
        if not path.is_file():
            raise AssertionError(f"registered source missing: {rel}")
        current[rel] = sha256(path)
    return current


def validate() -> dict[str, Any]:
    rows, frozen_sources = load_frozen()
    current = current_registered_sources(frozen_sources)
    changed = sorted(
        rel for rel, expected in frozen_sources.items() if current[rel] != expected
    )
    if changed != [TRADING_ENGINE]:
        raise AssertionError(
            "instrumentation must alter exactly TradingEngine among frozen sources; "
            f"observed={changed}"
        )

    for critical in (EXECUTION_POLICY, FROZEN_SIGNALS):
        if current[critical] != frozen_sources[critical]:
            raise AssertionError(f"critical frozen source changed: {critical}")

    support_hashes: dict[str, str] = {}
    for rel in APPROVED_SUPPORT:
        path = ROOT / rel
        if not path.is_file():
            raise AssertionError(f"instrumentation support file missing: {rel}")
        support_hashes[rel] = sha256(path)

    current_bundle = canonical_bundle(current)
    frozen_bundle = canonical_bundle(frozen_sources)

    return {
        "schema_version": 1,
        "strategy_version": VERSION,
        "base_strategy_version": BASE_VERSION,
        "base_git_sha": BASE_GIT_SHA,
        "instrumentation_parent_main_sha": PARENT_MAIN_SHA,
        "status": "INSTRUMENTATION_ONLY",
        "research_eligible": False,
        "purpose": "exact pre-outcome ExecutionPolicy context capture for SHADOW prospective evidence",
        "policy_context_quality": "EXACT_PRE_OUTCOME_EXECUTION_POLICY_INPUTS",
        "policy_context_version": 1,
        "frozen_modes": [row["strategy_id"] for row in rows],
        "frozen_source_count": len(frozen_sources),
        "changed_frozen_sources": changed,
        "frozen_source_bundle_sha256": frozen_bundle,
        "instrumented_source_bundle_sha256": current_bundle,
        "instrumented_registered_sources": current,
        "instrumentation_support_sources": support_hashes,
        "behavioral_equivalence_required": True,
        "behavioral_equivalence_tests": [
            "com.example.tradedraw.VetoCascadeEquivalenceTest",
            "com.example.tradedraw.ExecutionPolicyEvidenceCaptureTest",
            "com.example.tradedraw.ProspectivePredictionExportTest",
            "com.example.tradedraw.FileSimSnapshotStoreV2Test",
        ],
        "limitations": [
            "not a profitability claim",
            "not eligible for real-money operation",
            "not interchangeable with r29c-frozen-4fdb646 evidence",
            "must be used only in a separately identified prospective capture epoch",
        ],
    }


def write_manifest(payload: dict[str, Any]) -> None:
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(
        json.dumps(payload, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )


def verify_manifest(payload: dict[str, Any]) -> None:
    if not OUT.is_file():
        raise AssertionError(f"instrumentation registry missing: {OUT.relative_to(ROOT)}")
    stored = json.loads(OUT.read_text(encoding="utf-8"))
    if stored != payload:
        raise AssertionError(
            "instrumentation registry is stale; rerun with --write after reviewed source changes"
        )


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--write",
        action="store_true",
        help="write/update the separate instrumentation registry",
    )
    args = parser.parse_args()

    payload = validate()
    if args.write:
        write_manifest(payload)
        print(f"WROTE {OUT.relative_to(ROOT)}")
    else:
        verify_manifest(payload)
        print("INSTRUMENTATION_REGISTRY_PASS")

    print(
        f"version={VERSION} frozen_sources={payload['frozen_source_count']} "
        f"changed={payload['changed_frozen_sources']}"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
