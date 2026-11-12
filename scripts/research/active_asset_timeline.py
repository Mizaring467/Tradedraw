#!/usr/bin/env python3
"""Causal active-asset observations extracted from TradeDraw runtime events.

The ASSET event is emitted by Android accessibility when an asset label is observed
on screen and the same mapped RIC is sent to the broker/feed binding. This is useful
research evidence, but it is still an observation: absence of an event must never be
filled by future information.
"""

from __future__ import annotations

import re
from typing import Any, Iterable

_ASSET_DETAIL_RE = re.compile(r'''visible=["'](?P<visible>.*?)["']\s+ric=["'](?P<ric>.*?)["']''')


def parse_asset_event(event: dict[str, Any]) -> dict[str, Any] | None:
    if event.get("kind") != "ASSET":
        return None
    at_ms = event.get("atMs")
    detail = event.get("detail")
    if not isinstance(at_ms, int) or at_ms < 0 or not isinstance(detail, str):
        return None
    match = _ASSET_DETAIL_RE.fullmatch(detail.strip())
    if not match:
        return None
    visible = match.group("visible").strip()
    ric = match.group("ric").strip()
    if not visible or not ric:
        return None
    return {
        "atMs": at_ms,
        "visibleAsset": visible,
        "ric": ric,
        "quality": "RUNTIME_SCREEN_ASSET_OBSERVED",
        "exact_interval_end": False,
    }


def timeline_summary(observations: Iterable[dict[str, Any]]) -> dict[str, Any]:
    observed_order = [
        row for row in observations if isinstance(row.get("atMs"), int)
    ]
    regressions = sum(
        1
        for previous, current in zip(observed_order, observed_order[1:])
        if current["atMs"] < previous["atMs"]
    )
    rows = sorted(observed_order, key=lambda row: row["atMs"])
    assets = sorted({str(row.get("ric", "")) for row in rows if row.get("ric")})
    return {
        "observations": len(rows),
        "assets": assets,
        "first_at_ms": rows[0]["atMs"] if rows else None,
        "last_at_ms": rows[-1]["atMs"] if rows else None,
        "timestamp_regressions": regressions,
        "supports_mixed_asset_replay": False,
        "limitation": (
            "Observed screen-asset points are causal, but gaps are not interpolated and "
            "no exact interval end is inferred. Mixed-asset replay remains fail-closed "
            "until decisions/ticks can be joined to a proven active-asset interval."
        ),
    }


def active_asset_at(
    observations: Iterable[dict[str, Any]],
    timestamp_ms: int,
    *,
    max_age_ms: int,
) -> dict[str, Any]:
    """Causally join a timestamp to the latest prior screen-asset observation.

    Future observations are never used. The caller must preregister max_age_ms;
    there is intentionally no default because acceptable staleness depends on the
    capture protocol rather than this helper.
    """
    if not isinstance(timestamp_ms, int) or timestamp_ms < 0:
        raise ValueError("INVALID_QUERY_TIMESTAMP")
    if not isinstance(max_age_ms, int) or max_age_ms < 0:
        raise ValueError("INVALID_MAX_AGE")

    by_timestamp: dict[int, set[str]] = {}
    for row in observations:
        at_ms = row.get("atMs")
        ric = row.get("ric")
        if not isinstance(at_ms, int) or at_ms < 0 or not isinstance(ric, str) or not ric.strip():
            continue
        if at_ms > timestamp_ms:
            continue
        by_timestamp.setdefault(at_ms, set()).add(ric.strip())

    if not by_timestamp:
        return {
            "status": "UNKNOWN_NO_PRIOR_OBSERVATION",
            "timestamp_ms": timestamp_ms,
            "ric": None,
            "observation_at_ms": None,
            "age_ms": None,
        }

    at_ms = max(by_timestamp)
    rics = by_timestamp[at_ms]
    age_ms = timestamp_ms - at_ms
    if len(rics) != 1:
        return {
            "status": "UNKNOWN_CONFLICT_AT_TIMESTAMP",
            "timestamp_ms": timestamp_ms,
            "ric": None,
            "observation_at_ms": at_ms,
            "age_ms": age_ms,
        }
    ric = next(iter(rics))
    if age_ms > max_age_ms:
        return {
            "status": "UNKNOWN_STALE_OBSERVATION",
            "timestamp_ms": timestamp_ms,
            "ric": None,
            "observation_at_ms": at_ms,
            "age_ms": age_ms,
        }
    return {
        "status": "KNOWN_CAUSAL_OBSERVATION",
        "timestamp_ms": timestamp_ms,
        "ric": ric,
        "observation_at_ms": at_ms,
        "age_ms": age_ms,
    }


def join_records_to_active_asset(
    records: Iterable[dict[str, Any]],
    observations: Iterable[dict[str, Any]],
    *,
    timestamp_field: str,
    max_age_ms: int,
) -> list[dict[str, Any]]:
    """Attach causal asset-join evidence without dropping unknown records."""
    obs = list(observations)
    joined: list[dict[str, Any]] = []
    for record in records:
        ts = record.get(timestamp_field)
        if not isinstance(ts, int):
            evidence = {
                "status": "UNKNOWN_INVALID_RECORD_TIMESTAMP",
                "timestamp_ms": ts,
                "ric": None,
                "observation_at_ms": None,
                "age_ms": None,
            }
        else:
            evidence = active_asset_at(obs, ts, max_age_ms=max_age_ms)
        joined.append({**record, "active_asset_evidence": evidence})
    return joined
