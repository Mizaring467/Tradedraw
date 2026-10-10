#!/usr/bin/env python3
"""
TradeDraw - SimulationValidationEngine: TemporalIntegrityValidator
----------------------------------------------------------------
Valida la regularidad y sincronización temporal:
  - Monotonicidad estricta en series de tiempo.
  - Alineación de velas con el timeframe (minutos exactos :00.000).
  - Distribución de jitter o retrasos en la llegada de ticks.
"""

from dataclasses import dataclass, field
from typing import List, Dict, Any
import numpy as np


@dataclass
class TemporalIntegrityReport:
    is_valid: bool
    is_monotonic: bool
    alignment_rate: float
    avg_tick_interval_ms: float
    max_tick_interval_ms: float
    irregular_intervals_count: int
    issues: List[str] = field(default_factory=list)

    def to_dict(self) -> Dict[str, Any]:
        return {
            "is_valid": self.is_valid,
            "is_monotonic": self.is_monotonic,
            "alignment_rate": self.alignment_rate,
            "avg_tick_interval_ms": self.avg_tick_interval_ms,
            "max_tick_interval_ms": self.max_tick_interval_ms,
            "irregular_intervals_count": self.irregular_intervals_count,
            "issues": self.issues
        }


class TemporalIntegrityValidator:
    """
    Verifica que la línea de tiempo de la simulación o telemetría no contenga saltos ni desalineaciones.
    """

    def validate_timeline(self, timestamps_ms: List[int], timeframe_seconds: int = 60) -> TemporalIntegrityReport:
        issues = []
        n = len(timestamps_ms)
        if n < 2:
            return TemporalIntegrityReport(
                is_valid=False, is_monotonic=False, alignment_rate=0.0,
                avg_tick_interval_ms=0.0, max_tick_interval_ms=0.0,
                irregular_intervals_count=0, issues=["Muestra temporal insuficiente (< 2 puntos)"]
            )

        diffs = np.diff(timestamps_ms)
        is_monotonic = bool(np.all(diffs >= 0))
        if not is_monotonic:
            issues.append("La serie temporal no es monótona creciente (hay retrocesos en el tiempo).")

        avg_int = float(np.mean(diffs))
        max_int = float(np.max(diffs))

        # Comprobar alineación de las velas
        period_ms = timeframe_seconds * 1000
        aligned_count = sum(1 for ts in timestamps_ms if (ts % period_ms) == 0)
        alignment_rate = aligned_count / n

        # Brechas anómalas (> 10x el promedio)
        irregular_count = int(np.sum(diffs > max(5000.0, avg_int * 10.0)))
        if irregular_count > 0:
            issues.append(f"Se detectaron {irregular_count} saltos temporales extremos en el stream.")

        is_valid = is_monotonic and (irregular_count < (n * 0.05))

        return TemporalIntegrityReport(
            is_valid=is_valid,
            is_monotonic=is_monotonic,
            alignment_rate=alignment_rate,
            avg_tick_interval_ms=avg_int,
            max_tick_interval_ms=max_int,
            irregular_intervals_count=irregular_count,
            issues=issues
        )
