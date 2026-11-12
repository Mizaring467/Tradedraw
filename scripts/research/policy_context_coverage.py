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


def observed_context_from_status(prediction: dict[str, Any], status: dict[str, Any], captured_at_ms: int) -> dict[str, Any]:
    """Extract only fields observable from the read-only status snapshot.

    This is deliberately not returned as exact policy_context because the status
    snapshot is observed after the decision was created (though before settlement)
    and several volatile policy inputs are not exposed by /status.
    """
    feed = status.get("feed") if isinstance(status.get("feed"), dict) else {}
    analysis = status.get("analysis") if isinstance(status.get("analysis"), dict) else {}
    risk = status.get("risk") if isinstance(status.get("risk"), dict) else {}

    fields: dict[str, Any] = {}
    quality: dict[str, str] = {}

    def observed(name: str, value: Any) -> None:
        if value is not None:
            fields[name] = value
            quality[name] = "POST_DECISION_PRE_OUTCOME_OBSERVED"

    observed("mode", status.get("mode"))
    observed("autonomousSubMode", status.get("submode"))
    observed("strategy", status.get("strategy"))

    if "lastTickAgeMs" in feed:
        age = feed.get("lastTickAgeMs")
        fields["tickAgeMs"] = None if age == -1 else age
        quality["tickAgeMs"] = "POST_DECISION_PRE_OUTCOME_OBSERVED"
    observed("isFeedFresh", feed.get("isFresh"))
    observed("feedAsset", feed.get("activeAsset"))
    observed("rangoRelativo", feed.get("rangoRelativoActual"))
    observed("isPriceFrozen", feed.get("priceFrozen"))
    observed("isChoppinessDetected", analysis.get("isMarketSideways"))
    observed("currentLossStreak", risk.get("currentLossStreak"))
    observed("trend", analysis.get("trend"))
    observed("callPower", analysis.get("callPower"))

    ts = prediction.get("timestamp")
    if isinstance(ts, int) and ts >= 0:
        fields["sec"] = (ts // 1000) % 60
        fields["currentCandleEpochMinute"] = ts // 60_000
        quality["sec"] = "CAUSALLY_DERIVED_FROM_DECISION_TIMESTAMP"
        quality["currentCandleEpochMinute"] = "CAUSALLY_DERIVED_FROM_DECISION_TIMESTAMP"

    fields["currentDecisionId"] = prediction.get("decisionId")
    fields["action"] = prediction.get("action")
    fields["reasonDescription"] = prediction.get("reason")
    quality["currentDecisionId"] = "PRECOMMITTED_PREDICTION"
    quality["action"] = "PRECOMMITTED_PREDICTION"
    quality["reasonDescription"] = "PRECOMMITTED_PREDICTION"

    all_required = set(POLICY_CONTEXT_REQUIRED) | set(TOP_LEVEL_EQUIVALENTS)
    unresolved = sorted(all_required - set(fields))

    return {
        "decisionId": prediction.get("decisionId"),
        "captured_at_ms": captured_at_ms,
        "fields": fields,
        "quality": quality,
        "unresolved_fields": unresolved,
        "exact_decision_context": False,
        "status": "PARTIAL_PRE_OUTCOME_OBSERVATION",
    }


def observation_summary(observations: Iterable[dict[str, Any]]) -> dict[str, Any]:
    rows = list(observations)
    field_counts: Counter[str] = Counter()
    unresolved_counts: Counter[str] = Counter()
    for row in rows:
        fields = row.get("fields")
        if isinstance(fields, dict):
            field_counts.update(fields.keys())
        unresolved = row.get("unresolved_fields")
        if isinstance(unresolved, list):
            unresolved_counts.update(str(field) for field in unresolved)
    return {
        "observations": len(rows),
        "observed_field_counts": dict(sorted(field_counts.items())),
        "unresolved_field_counts": dict(sorted(unresolved_counts.items())),
        "exact_decision_context_observations": sum(
            1 for row in rows if row.get("exact_decision_context") is True
        ),
    }
