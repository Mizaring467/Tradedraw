#!/usr/bin/env python3
"""Fail-closed GET-only preflight for R29-C instrumented SHADOW capture."""

from __future__ import annotations

import argparse
import json
import urllib.request
from typing import Any

from scripts.research.policy_context_coverage import (
    EXACT_POLICY_CONTEXT_QUALITY,
    EXACT_POLICY_CONTEXT_VERSION,
    POLICY_CONTEXT_REQUIRED,
)

DEFAULT_BASE_URL = "http://127.0.0.1:8080"
INSTRUMENTED_VERSION = "r29c-instrumented-policy-v1"
BASE_STRATEGY_VERSION = "r29c-frozen-4fdb646"


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, *args, **kwargs):
        return None


def fetch_json(base_url: str, endpoint: str, timeout: float = 5.0) -> dict[str, Any]:
    if base_url != DEFAULT_BASE_URL:
        raise ValueError("BASE_URL_MUST_BE_FIXED_LOOPBACK")
    opener = urllib.request.build_opener(NoRedirect())
    request = urllib.request.Request(base_url + endpoint, method="GET")
    with opener.open(request, timeout=timeout) as response:
        if response.status != 200:
            raise RuntimeError(f"HTTP_{response.status}_{endpoint}")
        value = json.loads(response.read())
    if not isinstance(value, dict):
        raise ValueError(f"NON_OBJECT_RESPONSE_{endpoint}")
    return value


def validate_prediction(prediction: dict[str, Any], expected_sha: str) -> list[str]:
    errors: list[str] = []
    strategy_version = str(prediction.get("strategyVersion", ""))
    if not strategy_version.endswith("@" + INSTRUMENTED_VERSION):
        errors.append("WRONG_INSTRUMENTED_STRATEGY_VERSION")
    if prediction.get("base_strategy_version") != BASE_STRATEGY_VERSION:
        errors.append("WRONG_BASE_STRATEGY_VERSION")
    if prediction.get("git_sha") != expected_sha:
        errors.append("PREDICTION_BUILD_IDENTITY_MISMATCH")
    if prediction.get("policy_context_quality") != EXACT_POLICY_CONTEXT_QUALITY:
        errors.append("POLICY_CONTEXT_NOT_EXACT")
    if prediction.get("policy_context_version") != EXACT_POLICY_CONTEXT_VERSION:
        errors.append("POLICY_CONTEXT_VERSION_MISMATCH")

    context = prediction.get("policy_context")
    if not isinstance(context, dict):
        errors.append("POLICY_CONTEXT_MISSING")
    else:
        missing = [field for field in POLICY_CONTEXT_REQUIRED if field not in context]
        if missing:
            errors.append("POLICY_CONTEXT_MISSING_FIELDS:" + ",".join(missing))
    return errors


def validate_runtime(
    status: dict[str, Any],
    simulation: dict[str, Any],
    expected_sha: str,
    *,
    require_prediction: bool = False,
    require_fresh_feed: bool = False,
) -> dict[str, Any]:
    errors: list[str] = []

    if status.get("gitSha") != expected_sha:
        errors.append("RUNTIME_GIT_SHA_MISMATCH")
    if status.get("mode") != "SHADOW":
        errors.append("MODE_NOT_SHADOW")
    if simulation.get("enabled") is not True:
        errors.append("SIMULATION_NOT_ENABLED")

    if require_fresh_feed:
        feed = status.get("feed")
        if not isinstance(feed, dict) or feed.get("connected") is not True or feed.get("isFresh") is not True:
            errors.append("FEED_NOT_FRESH")

    predictions = simulation.get("pendingDecisions", [])
    if not isinstance(predictions, list):
        errors.append("PENDING_DECISIONS_NOT_LIST")
        predictions = []
    if require_prediction and not predictions:
        errors.append("NO_PENDING_PREDICTION")

    prediction_errors: list[dict[str, Any]] = []
    exact_predictions = 0
    for prediction in predictions:
        if not isinstance(prediction, dict):
            prediction_errors.append({"decisionId": None, "errors": ["PREDICTION_NOT_OBJECT"]})
            continue
        current = validate_prediction(prediction, expected_sha)
        if current:
            prediction_errors.append(
                {"decisionId": prediction.get("decisionId"), "errors": current}
            )
        else:
            exact_predictions += 1

    if prediction_errors:
        errors.append("INVALID_PENDING_PREDICTIONS")

    return {
        "status": "PASS" if not errors else "FAIL",
        "errors": errors,
        "runtime_git_sha": status.get("gitSha"),
        "mode": status.get("mode"),
        "simulation_enabled": simulation.get("enabled"),
        "pending_predictions": len(predictions),
        "exact_pending_predictions": exact_predictions,
        "prediction_errors": prediction_errors,
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--expected-git-sha", required=True)
    parser.add_argument("--base-url", default=DEFAULT_BASE_URL)
    parser.add_argument("--require-prediction", action="store_true")
    parser.add_argument("--require-fresh-feed", action="store_true")
    args = parser.parse_args()

    try:
        status = fetch_json(args.base_url, "/status")
        simulation = fetch_json(args.base_url, "/simulation")
        report = validate_runtime(
            status,
            simulation,
            args.expected_git_sha,
            require_prediction=args.require_prediction,
            require_fresh_feed=args.require_fresh_feed,
        )
    except Exception as exc:
        report = {
            "status": "FAIL",
            "errors": [f"TRANSPORT_OR_SCHEMA:{type(exc).__name__}:{exc}"],
        }

    print(json.dumps(report, indent=2, sort_keys=True))
    return 0 if report["status"] == "PASS" else 1


if __name__ == "__main__":
    raise SystemExit(main())
