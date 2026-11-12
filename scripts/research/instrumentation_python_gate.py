#!/usr/bin/env python3
"""Python gate for the R29-C versioned policy instrumentation release.

The ordinary frozen-source test MUST continue to reject this worktree because
TradingEngine.kt is intentionally versioned. This gate does not hide that
failure: it requires it to be the one and only frozen-source rejection, while
all other Python tests must pass and the separate instrumentation registry must
match the current source bytes.
"""

from __future__ import annotations

import io
from pathlib import Path
import sys
import unittest

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT))

from scripts.research import instrumented_policy_registry as registry  # noqa: E402

FROZEN_TEST_ID = (
    "test_r29c_inventory.ResearchInventoryTest."
    "test_all_frozen_production_sources_match_registered_bytes"
)


def iter_tests(suite: unittest.TestSuite):
    for item in suite:
        if isinstance(item, unittest.TestSuite):
            yield from iter_tests(item)
        else:
            yield item


def load_all() -> list[unittest.TestCase]:
    discovered = unittest.defaultTestLoader.discover(str(ROOT / "tests"))
    return list(iter_tests(discovered))


def run_suite(tests: list[unittest.TestCase]) -> unittest.TestResult:
    return unittest.TextTestRunner(verbosity=1).run(unittest.TestSuite(tests))


def main() -> int:
    payload = registry.validate()
    registry.verify_manifest(payload)

    tests = load_all()
    frozen = [test for test in tests if test.id() == FROZEN_TEST_ID]
    others = [test for test in tests if test.id() != FROZEN_TEST_ID]

    if len(frozen) != 1:
        raise AssertionError(
            f"expected exactly one frozen-source guard, found {len(frozen)}"
        )

    print(f"[instrumentation] running {len(others)} ordinary Python tests")
    ordinary = run_suite(others)
    if not ordinary.wasSuccessful():
        print("[instrumentation] ordinary Python suite failed")
        return 1

    print(
        "[instrumentation] running frozen-source guard separately; "
        "one deliberate TradingEngine rejection is REQUIRED"
    )
    stream = io.StringIO()
    frozen_result = unittest.TextTestRunner(stream=stream, verbosity=2).run(
        unittest.TestSuite(frozen)
    )
    output = stream.getvalue()

    if frozen_result.errors:
        print(output)
        raise AssertionError("frozen-source guard errored instead of rejecting bytes")
    if len(frozen_result.failures) != 1:
        print(output)
        raise AssertionError(
            "frozen-source guard must have exactly one failure on instrumentation"
        )

    if payload["changed_frozen_sources"] != [
        "app/src/main/java/com/example/tradedraw/TradingEngine.kt"
    ]:
        raise AssertionError(
            f"unexpected frozen changes: {payload['changed_frozen_sources']}"
        )

    print("INSTRUMENTATION_PYTHON_GATE_PASS")
    print(f"ordinary_tests={ordinary.testsRun} frozen_guard_expected_fail=1")
    print(
        f"version={payload['strategy_version']} "
        f"base={payload['base_strategy_version']}"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
