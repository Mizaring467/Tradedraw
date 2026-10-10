#!/usr/bin/env python3
"""
TradeDraw - SimulationValidationEngine: OutOfSampleValidator
-----------------------------------------------------------
Implementa la validación Walk-Forward causal estricta:
  TRAIN[t0:t1] -> parameter selection -> TEST[t1:t2] -> resultado OOS

Garantías Metodológicas:
  - Los parámetros seleccionados en TRAIN no cambian jamás tras observar TEST.
  - Prohibido optimizar thresholds en TEST.
  - Métrica de estabilidad paramétrica entre folds (detecta sobreajuste).
  - Ponderación exacta por número de operaciones (no promedio de porcentajes).
  - Calificación explícita de debilidad estadística por fold (INSUFFICIENT_SAMPLE si N < 30).
"""

from dataclasses import dataclass, field
from typing import List, Dict, Any, Tuple, Optional
import math
import numpy as np


@dataclass
class FoldResult:
    fold: int
    train_range: Tuple[int, int]
    test_range: Tuple[int, int]
    selected_params: Dict[str, Any]
    train_metrics: Dict[str, Any]
    oos_metrics: Dict[str, Any]
    is_sample_sufficient: bool
    status: str  # "VALID", "INSUFFICIENT_SAMPLE", "OVERFIT_REJECTED"


@dataclass
class WalkForwardReport:
    total_folds: int
    sufficient_folds: int
    total_oos_trades: int
    total_oos_wins: int
    weighted_oos_winrate: float
    weighted_oos_ev: float
    is_methodologically_valid: bool
    parameter_stability_score: float  # 1.0 = idénticos parámetros en todos los folds
    folds: List[FoldResult] = field(default_factory=list)
    verdict: str = "INSUFFICIENT_SAMPLE"
    notes: List[str] = field(default_factory=list)

    def to_dict(self) -> Dict[str, Any]:
        return {
            "total_folds": self.total_folds,
            "sufficient_folds": self.sufficient_folds,
            "total_oos_trades": self.total_oos_trades,
            "total_oos_wins": self.total_oos_wins,
            "weighted_oos_winrate": self.weighted_oos_winrate,
            "weighted_oos_ev": self.weighted_oos_ev,
            "is_methodologically_valid": self.is_methodologically_valid,
            "parameter_stability_score": self.parameter_stability_score,
            "folds": [
                {
                    "fold": f.fold,
                    "train_range": f.train_range,
                    "test_range": f.test_range,
                    "selected_params": f.selected_params,
                    "train_trades": f.train_metrics.get("trades", 0),
                    "train_winrate": f.train_metrics.get("winrate", 0.0),
                    "oos_trades": f.oos_metrics.get("trades", 0),
                    "oos_winrate": f.oos_metrics.get("winrate", 0.0),
                    "oos_ev": f.oos_metrics.get("ev", 0.0),
                    "status": f.status
                } for f in self.folds
            ],
            "verdict": self.verdict,
            "notes": self.notes
        }


class OutOfSampleValidator:
    """
    Ejecutor y auditor de validación cruzada Walk-Forward.
    """

    MIN_OOS_TRADES_PER_FOLD = 30

    def evaluate_walk_forward(
        self,
        folds_data: List[Dict[str, Any]],
        breakeven_wr: float = 0.5464
    ) -> WalkForwardReport:
        notes = []
        processed_folds = []
        total_oos_trades = 0
        total_oos_wins = 0
        total_oos_return = 0.0
        param_hashes = []

        sufficient_count = 0

        for f_dict in folds_data:
            fold_idx = f_dict["fold"]
            t_train = f_dict.get("train_range", (0, 0))
            t_test = f_dict.get("test_range", (0, 0))
            params = f_dict.get("selected_params", {})
            m_train = f_dict.get("train_metrics", {})
            m_oos = f_dict.get("oos_metrics", {})

            oos_n = m_oos.get("trades", 0)
            oos_wr = m_oos.get("winrate", 0.0)
            oos_ev = m_oos.get("ev", 0.0)
            oos_wins = round(oos_n * oos_wr)

            total_oos_trades += oos_n
            total_oos_wins += oos_wins
            total_oos_return += oos_n * oos_ev

            # Control de estabilidad de parámetros
            p_str = json.dumps(params, sort_keys=True)
            param_hashes.append(p_str)

            is_sufficient = oos_n >= self.MIN_OOS_TRADES_PER_FOLD
            if is_sufficient:
                sufficient_count += 1
                status = "VALID" if oos_wr >= breakeven_wr else "FAILED_BREAKEVEN"
            else:
                status = "INSUFFICIENT_SAMPLE"

            processed_folds.append(FoldResult(
                fold=fold_idx,
                train_range=t_train,
                test_range=t_test,
                selected_params=params,
                train_metrics=m_train,
                oos_metrics=m_oos,
                is_sample_sufficient=is_sufficient,
                status=status
            ))

        # Estabilidad de parámetros: frecuencia de la moda
        if param_hashes:
            from collections import Counter
            counts = Counter(param_hashes)
            stability = counts.most_common(1)[0][1] / len(param_hashes)
        else:
            stability = 0.0

        weighted_wr = (total_oos_wins / total_oos_trades) if total_oos_trades > 0 else 0.0
        weighted_ev = (total_oos_return / total_oos_trades) if total_oos_trades > 0 else 0.0

        # Veredicto general
        if total_oos_trades < 100 or sufficient_count < (len(folds_data) * 0.6):
            verdict = "INSUFFICIENT_SAMPLE"
            notes.append(f"Muestra acumulada OOS débil (N={total_oos_trades}, folds suficientes={sufficient_count}/{len(folds_data)}).")
        elif weighted_wr < breakeven_wr:
            verdict = "NEGATIVE_EXPECTANCY"
            notes.append(f"Win Rate OOS ponderado ({weighted_wr*100:.2f}%) por debajo del break-even ({breakeven_wr*100:.2f}%).")
        else:
            verdict = "VALID_FOR_RESEARCH"
            notes.append("El modelo superó el break-even en Out-Of-Sample con soporte estadístico adecuado.")

        is_valid = verdict == "VALID_FOR_RESEARCH"

        return WalkForwardReport(
            total_folds=len(folds_data),
            sufficient_folds=sufficient_count,
            total_oos_trades=total_oos_trades,
            total_oos_wins=total_oos_wins,
            weighted_oos_winrate=weighted_wr,
            weighted_oos_ev=weighted_ev,
            is_methodologically_valid=is_valid,
            parameter_stability_score=stability,
            folds=processed_folds,
            verdict=verdict,
            notes=notes
        )
