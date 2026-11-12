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
