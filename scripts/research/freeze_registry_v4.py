#!/usr/bin/env python3
"""Create the R30 v4 frozen registry after integrating the frontend broker-agent work.

v3 remains immutable historical evidence at its own commit. v4 snapshots the
exact 44-source production bundle at the R30 merge (8bd172f): identical to v3
except the verified market transport evolution of
market/src/main/java/com/example/tradedraw/BinomoWebSocketClient.kt
(quote feed lifecycle fix from assistant/frontend-brokers-agent).
Strategy/risk/execution/decision sources are asserted byte-identical to v3.
"""
import hashlib
import json
import re
import subprocess
from pathlib import Path

from scripts.research.manifests import write_once

BASE = "8bd172f69e1e914976c70ffbf48b9348ad7362d0"
VERSION = "r30-frozen-8bd172f"
PREVIOUS = Path("loops/r29c/evidence/strategy_registry_v3.json")
OUTPUT = Path("loops/r29c/evidence/strategy_registry_v4.json")
EXPECTED_CHANGED_SOURCE = "market/src/main/java/com/example/tradedraw/BinomoWebSocketClient.kt"


def source_paths():
    return subprocess.check_output(
        [
            "git", "ls-tree", "-r", "--name-only", BASE,
            "strategy/src/main",
            "core/src/main",
            "market/src/main",
            "risk/src/main",
            "execution/src/main",
            "app/src/main/java/com/example/tradedraw/TradingEngine.kt",
        ],
        text=True,
    ).splitlines()


def main():
    paths = [p for p in source_paths() if p.endswith(".kt")]
    hashes = {
        p: hashlib.sha256(
            subprocess.check_output(["git", "show", BASE + ":" + p])
        ).hexdigest()
        for p in paths
    }
    bundle = hashlib.sha256(
        json.dumps(hashes, sort_keys=True, separators=(",", ":")).encode()
    ).hexdigest()

    previous = json.loads(PREVIOUS.read_text())
    old_hashes = previous[0]["supporting_sources"]
    changed = sorted(
        p for p in set(old_hashes) | set(hashes)
        if old_hashes.get(p) != hashes.get(p)
    )
    assert changed == [EXPECTED_CHANGED_SOURCE], (
        "v3->v4 must change only the verified quote transport source; "
        f"observed={changed}"
    )

    enum = subprocess.check_output(
        ["git", "show", BASE + ":core/src/main/kotlin/com/example/tradedraw/core/AutoTradeStrategy.kt"],
        text=True,
    )
    ids = re.findall(r"^\s+([A-Z][A-Z_0-9]+)\b", enum, re.M)

    registry = []
    for sid in ids:
        registry.append(
            dict(
                strategy_id=sid,
                name=sid,
                version=VERSION,
                git_sha=BASE,
                source_file="strategy/src/main/java/com/example/tradedraw/FrozenStrategySignals.kt",
                supporting_sources=hashes,
                parameter_hash=bundle,
                parameter_hash_scope=(
                    "SHA256 of canonical path->full-source-SHA map; conservative "
                    "superset of parameters, includes policy and engine"
                ),
                status="FROZEN",
                research_eligible=False,
                supersedes="r29c-frozen-293c932",
                change_scope=(
                    "Operational transport and UI integration only: Binomo Web quote "
                    "socket lifecycle repair (assistant/frontend-brokers-agent 451787f) "
                    "merged with R30 presence gate; broker-aware home UI and agent "
                    "connection settings added. Strategy/risk/execution decision "
                    "logic unchanged."
                ),
                limitation=(
                    "AI remote model/provider identity unpinned"
                    if sid == "AI_REMOTE"
                    else "Research data and full execution parity gates not passed"
                ),
            )
        )

    write_once(OUTPUT, registry)

    for p, expected in hashes.items():
        assert hashlib.sha256(Path(p).read_bytes()).hexdigest() == expected, (
            f"Current production byte drift after v4 snapshot: {p}"
        )

    print(
        "FROZEN_V4",
        len(registry),
        "modes",
        len(hashes),
        "source hashes",
        bundle,
        "changed_from_v3",
        changed,
    )


if __name__ == "__main__":
    main()