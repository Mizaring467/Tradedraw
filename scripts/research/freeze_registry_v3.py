#!/usr/bin/env python3
"""Create the R29-C v3 frozen registry after the verified Binomo quote-feed repair.

v2 remains immutable historical evidence. v3 snapshots the exact 44-source
production bundle at 293c932 and is intentionally a new version because the
conservative R29-C bundle includes market transport code.
"""
import hashlib
import json
import re
import subprocess
from pathlib import Path

from scripts.research.manifests import write_once

BASE = "293c932fbcae8c85c0ca38afb527d486e8379710"
VERSION = "r29c-frozen-293c932"
PREVIOUS = Path("loops/r29c/evidence/strategy_registry_v2.json")
OUTPUT = Path("loops/r29c/evidence/strategy_registry_v3.json")
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
        "v2->v3 must change only verified quote transport source; "
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
                supersedes="r29c-frozen-4fdb646",
                change_scope=(
                    "Operational market-data transport repair only: Binomo Web quote "
                    "socket uses as.binomo.com with Web session cookies/Origin, "
                    "reconnect_request and rics subscription. Strategy/risk/execution "
                    "decision logic unchanged."
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
            f"Current production byte drift after v3 snapshot: {p}"
        )

    print(
        "FROZEN_V3",
        len(registry),
        "modes",
        len(hashes),
        "source hashes",
        bundle,
        "changed_from_v2",
        changed,
    )


if __name__ == "__main__":
    main()
