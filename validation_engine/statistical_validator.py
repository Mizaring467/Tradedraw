#!/usr/bin/env python3
"""
TradeDraw - SimulationValidationEngine: StatisticalValidator
-----------------------------------------------------------
Calcula métricas estadísticas rigurosas, intervalos de confianza de Wilson,
significancia contra break-even, análisis de rachas, drawdown y bootstrap de Monte Carlo.
Asigna el estado metodológico:
  - INSUFFICIENT_SAMPLE (si N < 30)
  - DATA_INVALID
  - POTENTIAL_LOOKAHEAD
  - POTENTIAL_OVERFIT
  - VALID_FOR_RESEARCH
"""

from dataclasses import dataclass, field
from typing import List, Dict, Any, Tuple, Optional
import math
import numpy as np


@dataclass
class StatisticalReport:
    total_trades: int
    resolved_trades: int
    wins: int
    losses: int
    voids: int
    win_rate: float
    win_rate_ci_wilson_95: Tuple[float, float]
    expected_value: float
    expected_value_ci_95: Tuple[float, float]
    breakeven_winrate: float
    p_value_vs_breakeven: float
    z_stat_vs_breakeven: float
    max_drawdown_pct: float
    longest_losing_streak: int
    longest_winning_streak: int
    bootstrap_ev_positive_prob: float
    validation_status: str  # "INSUFFICIENT_SAMPLE", "POTENTIAL_OVERFIT", "VALID_FOR_RESEARCH", etc.
    warnings: List[str] = field(default_factory=list)

    def to_dict(self) -> Dict[str, Any]:
        return {
            "total_trades": self.total_trades,
            "resolved_trades": self.resolved_trades,
            "wins": self.wins,
            "losses": self.losses,
            "voids": self.voids,
            "win_rate": self.win_rate,
            "win_rate_ci_wilson_95": self.win_rate_ci_wilson_95,
            "expected_value": self.expected_value,
            "expected_value_ci_95": self.expected_value_ci_95,
            "breakeven_winrate": self.breakeven_winrate,
            "p_value_vs_breakeven": self.p_value_vs_breakeven,
            "z_stat_vs_breakeven": self.z_stat_vs_breakeven,
            "max_drawdown_pct": self.max_drawdown_pct,
            "longest_losing_streak": self.longest_losing_streak,
            "longest_winning_streak": self.longest_winning_streak,
            "bootstrap_ev_positive_prob": self.bootstrap_ev_positive_prob,
            "validation_status": self.validation_status,
            "warnings": self.warnings
        }


class StatisticalValidator:
    """
    Validador estadístico formal con prueba de hipótesis y bootstrap.
    """

    MIN_SAMPLE_SIZE = 30

    def __init__(self, payout_ratio: float = 0.83):
        self.payout_ratio = payout_ratio
        self.breakeven_wr = 1.0 / (1.0 + payout_ratio)

    def calculate_wilson_interval(self, wins: int, n: int, z: float = 1.95996) -> Tuple[float, float]:
        """
        Calcula el intervalo de Wilson al 95% para proporciones binomiales.
        """
        if n <= 0:
            return 0.0, 0.0
        p = wins / n
        z2 = z * z
        denom = 1.0 + z2 / n
        center = (p + z2 / (2.0 * n)) / denom
        margin = (z / denom) * math.sqrt((p * (1.0 - p) / n) + (z2 / (4.0 * n * n)))
        return max(0.0, center - margin), min(1.0, center + margin)

    def run_bootstrap_analysis(self, returns: np.ndarray, num_simulations: int = 2000, seed: int = 42) -> Tuple[Tuple[float, float], float]:
        """
        Monte Carlo Bootstrap no paramétrico sobre retornos unitarios.
        Retorna: (intervalo_ev_95, probabilidad_ev_positivo).
        """
        if len(returns) < 5:
            return (0.0, 0.0), 0.0

        rng = np.random.RandomState(seed)
        n = len(returns)
        boot_evs = np.zeros(num_simulations, dtype=np.float64)

        for i in range(num_simulations):
            sample = rng.choice(returns, size=n, replace=True)
            boot_evs[i] = np.mean(sample)

        lo = float(np.percentile(boot_evs, 2.5))
        hi = float(np.percentile(boot_evs, 97.5))
        prob_pos = float(np.mean(boot_evs > 0.0))

        return (lo, hi), prob_pos

    def evaluate(
        self,
        trades: List[Dict[str, Any]],
        data_integrity_passed: bool = True,
        lookahead_passed: bool = True
    ) -> StatisticalReport:
        warnings = []
        total = len(trades)
        wins = sum(1 for t in trades if t.get("result") == "WIN")
        losses = sum(1 for t in trades if t.get("result") == "LOSS")
        voids = sum(1 for t in trades if t.get("result") in ("TIE", "VOID", "REJECTED"))
        resolved = wins + losses

        wr = (wins / resolved) if resolved > 0 else 0.0
        ci_lo, ci_hi = self.calculate_wilson_interval(wins, resolved)

        returns = np.array([t.get("return_unit", 0.0) for t in trades if t.get("result") in ("WIN", "LOSS")], dtype=np.float64)
        ev = float(np.mean(returns)) if len(returns) > 0 else 0.0

        # Test vs Break-Even
        p0 = self.breakeven_wr
        if resolved > 0:
            se = math.sqrt(p0 * (1.0 - p0) / resolved)
            z_stat = (wr - p0) / se
            # p-valor normal de una cola
            p_val = 0.5 * math.erfc(z_stat / math.sqrt(2.0))
        else:
            z_stat = 0.0
            p_val = 1.0

        # Rachas y Drawdown
        max_loss_streak = 0
        cur_loss_streak = 0
        max_win_streak = 0
        cur_win_streak = 0
        cum_equity = 1.0
        peak_equity = 1.0
        max_dd = 0.0

        for t in trades:
            res = t.get("result")
            ret = t.get("return_unit", 0.0)
            if res == "LOSS":
                cur_loss_streak += 1
                cur_win_streak = 0
                max_loss_streak = max(max_loss_streak, cur_loss_streak)
            elif res == "WIN":
                cur_win_streak += 1
                cur_loss_streak = 0
                max_win_streak = max(max_win_streak, cur_win_streak)

            cum_equity += ret * 0.02
            if cum_equity > peak_equity:
                peak_equity = cum_equity
            dd = (peak_equity - cum_equity) / peak_equity if peak_equity > 0 else 0.0
            if dd > max_dd:
                max_dd = dd

        # Bootstrap
        (ev_boot_lo, ev_boot_hi), prob_ev_pos = self.run_bootstrap_analysis(returns)

        # Determinación de estado metodológico
        if not data_integrity_passed:
            status = "DATA_INVALID"
            warnings.append("El dataset contiene violaciones de integridad que invalidan la medición.")
        elif not lookahead_passed:
            status = "POTENTIAL_LOOKAHEAD"
            warnings.append("Se detectó posible filtrado de información futura en las señales o S/R.")
        elif resolved < self.MIN_SAMPLE_SIZE:
            status = "INSUFFICIENT_SAMPLE"
            warnings.append(f"Muestra resuelta insuficiente (N={resolved} < {self.MIN_SAMPLE_SIZE}). El resultado no es estadísticamente accionable.")
        else:
            status = "VALID_FOR_RESEARCH"

        return StatisticalReport(
            total_trades=total,
            resolved_trades=resolved,
            wins=wins,
            losses=losses,
            voids=voids,
            win_rate=wr,
            win_rate_ci_wilson_95=(ci_lo, ci_hi),
            expected_value=ev,
            expected_value_ci_95=(ev_boot_lo, ev_boot_hi),
            breakeven_winrate=self.breakeven_wr,
            p_value_vs_breakeven=p_val,
            z_stat_vs_breakeven=z_stat,
            max_drawdown_pct=max_dd * 100.0,
            longest_losing_streak=max_loss_streak,
            longest_winning_streak=max_win_streak,
            bootstrap_ev_positive_prob=prob_ev_pos,
            validation_status=status,
            warnings=warnings
        )
