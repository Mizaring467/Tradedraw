#!/usr/bin/env python3
"""ExecutionPolicy context coverage for prospective research evidence.

This module does not reconstruct or invent runtime state. It only answers whether
a prospective prediction persisted enough pre-outcome evidence to later rebuild
the frozen ExecutionContext faithfully.
"""

from __future__ import annotations

from collections import Counter
from typing import Any, Iterable

# Exact ExecutionContext fields that cannot be reconstructed safely from outcome data.
# action/currentDecisionId/reasonDescription have canonical top-level equivalents in
# the existing prospective prediction payload; everything else must be persisted
# inside policy_context before the outcome is known.
POLICY_CONTEXT_REQUIRED = (
    "mode",
    "autonomousSubMode",
    "strategy",
    "tickAgeMs",
    "isFeedFresh",
    "feedMaxAgeMs",
    "observedAsset",
    "feedAsset",
    "rangoRelativo",
    "isPriceFrozen",
    "isSyntheticOrOtc",
    "sec",
    "isChoppinessDetected",
    "lastTradeResolutionTime",
    "currentLossStreak",
    "currentTimeMs",
    "trend",
    "callPower",
    "adaptiveDecision",
    "capitalPolicyCanExecute",
    "srFilterStatus",
    "actionPlan",
    "currentCandleEpochMinute",
    "isCandleAlreadyExecuted",
)

TOP_LEVEL_EQUIVALENTS = {
    "action": "action",
    "currentDecisionId": "decisionId",
    "reasonDescription": "reason",
}


def missing_policy_context(prediction: dict[str, Any]) -> list[str]:
    """Return fields that were not precommitted; never synthesize defaults."""
    missing: list[str] = []
    for context_field, payload_field in TOP_LEVEL_EQUIVALENTS.items():
        if payload_field not in prediction or prediction[payload_field] in (None, ""):
            missing.append(context_field)

    context = prediction.get("policy_context")
    if not isinstance(context, dict):
        missing.extend(POLICY_CONTEXT_REQUIRED)
        return missing

    for field in POLICY_CONTEXT_REQUIRED:
        # None is a valid observed value for tickAgeMs (FEED_MUDO), so presence
        # matters rather than truthiness.
        if field not in context:
            missing.append(field)
    return missing


def policy_context_complete(prediction: dict[str, Any]) -> bool:
    return not missing_policy_context(prediction)


def coverage_summary(predictions: Iterable[dict[str, Any]]) -> dict[str, Any]:
    rows = list(predictions)
    missing_counts: Counter[str] = Counter()
    complete = 0
    for prediction in rows:
        missing = missing_policy_context(prediction)
        if missing:
            missing_counts.update(missing)
        else:
            complete += 1
    total = len(rows)
    return {
        "total_predictions": total,
        "complete_predictions": complete,
        "coverage_ratio": (complete / total) if total else None,
        "missing_field_counts": dict(sorted(missing_counts.items())),
        "status": "POLICY_CONTEXT_COMPLETE" if total and complete == total else "POLICY_CONTEXT_INCOMPLETE",
    }
