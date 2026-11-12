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
    "action",
    "mode",
    "autonomousSubMode",
    "strategy",
    "currentDecisionId",
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
    "reasonDescription",
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

EXACT_POLICY_CONTEXT_QUALITY = "EXACT_PRE_OUTCOME_EXECUTION_POLICY_INPUTS"
EXACT_POLICY_CONTEXT_VERSION = 1


TOP_LEVEL_EQUIVALENTS = {
    "action": "action",
    "currentDecisionId": "decisionId",
    "reasonDescription": "reason",
}


def missing_policy_context(prediction: dict[str, Any]) -> list[str]:
    """Return fields that cannot be reconstructed from pre-outcome evidence.

    Exact nested fields take precedence. Top-level action/decisionId/reason remain
    a legacy fallback only; they may describe the final prediction after adaptive
    inversion rather than the original ExecutionContext inputs.
    """
    context = prediction.get("policy_context")
    context = context if isinstance(context, dict) else {}
    missing: list[str] = []
    for field in POLICY_CONTEXT_REQUIRED:
        # Presence matters: None is a valid exact tickAgeMs when feed is silent.
        if field in context:
            continue
        payload_field = TOP_LEVEL_EQUIVALENTS.get(field)
        if payload_field and prediction.get(payload_field) not in (None, ""):
            continue
        missing.append(field)
    return missing


def policy_context_complete(prediction: dict[str, Any]) -> bool:
    return not missing_policy_context(prediction)


def policy_context_exact(prediction: dict[str, Any]) -> bool:
    """True only for a sealed exact pre-outcome ExecutionPolicy snapshot."""
    context = prediction.get("policy_context")
    if not isinstance(context, dict):
        return False
    if prediction.get("policy_context_quality") != EXACT_POLICY_CONTEXT_QUALITY:
        return False
    if prediction.get("policy_context_version") != EXACT_POLICY_CONTEXT_VERSION:
        return False
    return all(field in context for field in POLICY_CONTEXT_REQUIRED)


def coverage_summary(predictions: Iterable[dict[str, Any]]) -> dict[str, Any]:
    rows = list(predictions)
    missing_counts: Counter[str] = Counter()
    complete = 0
    exact = 0
    for prediction in rows:
        missing = missing_policy_context(prediction)
        if missing:
            missing_counts.update(missing)
        else:
            complete += 1
        if policy_context_exact(prediction):
            exact += 1
    total = len(rows)
    if total and exact == total:
        status = "POLICY_CONTEXT_EXACT"
    elif total and complete == total:
        status = "POLICY_CONTEXT_COMPLETE_LEGACY_OR_UNSEALED"
    else:
        status = "POLICY_CONTEXT_INCOMPLETE"
    return {
        "total_predictions": total,
        "complete_predictions": complete,
        "exact_predictions": exact,
        "coverage_ratio": (complete / total) if total else None,
        "exact_coverage_ratio": (exact / total) if total else None,
        "missing_field_counts": dict(sorted(missing_counts.items())),
        "status": status,
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

    all_required = set(POLICY_CONTEXT_REQUIRED)
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
