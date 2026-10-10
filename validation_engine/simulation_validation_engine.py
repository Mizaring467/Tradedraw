#!/usr/bin/env python3
"""
TradeDraw - SimulationValidationEngine (Orquestador Principal)
-------------------------------------------------------------
Motor de validación metodológica, trazabilidad y reproducibilidad para TradeDraw.

Arquitectura desacoplada:
  DATASET
     │
     ▼
  DataIntegrityValidator
     │
     ▼
  TemporalIntegrityValidator
     │
     ▼
  NoLookAheadValidator
     │
     ▼
  ExecutionModel (latencia, slippage, expiración)
     │
     ▼
  Simulation / Signal Evaluation
     │
     ▼
  StatisticalValidator (Wilson CI, Bootstrap, streaks, drawdown)
     │
     ▼
  OutOfSampleValidator (Walk-Forward con N>=30 y estabilidad de parámetros)
     │
     ▼
  SimulationReport & Comparador SIMULATION vs OBSERVED
"""

import json
import time
from dataclasses import dataclass, field, asdict
from enum import Enum
from typing import List, Dict, Any, Optional

from .data_integrity_validator import DataIntegrityValidator, DataIntegrityReport
from .temporal_integrity_validator import TemporalIntegrityValidator, TemporalIntegrityReport
from .no_look_ahead_validator import NoLookAheadValidator, LookAheadAuditReport
from .execution_model import ExecutionModel, SimulatedTradeResult
from .statistical_validator import StatisticalValidator, StatisticalReport
from .out_of_sample_validator import OutOfSampleValidator, WalkForwardReport


class DataSource(Enum):
    SYNTHETIC = "SYNTHETIC"
    OBSERVED = "OBSERVED"
    HISTORICAL = "HISTORICAL"
    DEMO = "DEMO"
    UNKNOWN = "UNKNOWN"


@dataclass
class ExperimentMetadata:
    data_source: str
    dataset_id: str
    start_time: str
    end_time: str
    sample_count: int
    strategy_version: str
    parameters: Dict[str, Any]
    execution_model: str
    seed: int

    def to_dict(self) -> Dict[str, Any]:
        return asdict(self)


@dataclass
class ValidationPipelineResult:
    metadata: ExperimentMetadata
    data_integrity: DataIntegrityReport
    temporal_integrity: TemporalIntegrityReport
    lookahead_audit: LookAheadAuditReport
    statistical_report: StatisticalReport
    walk_forward_report: Optional[WalkForwardReport] = None
    overall_status: str = "PENDING"
    divergence_warning: Optional[str] = None

    def to_dict(self) -> Dict[str, Any]:
        return {
            "metadata": self.metadata.to_dict(),
            "data_integrity": self.data_integrity.to_dict(),
            "temporal_integrity": self.temporal_integrity.to_dict(),
            "lookahead_audit": self.lookahead_audit.to_dict(),
            "statistical_report": self.statistical_report.to_dict(),
            "walk_forward_report": self.walk_forward_report.to_dict() if self.walk_forward_report else None,
            "overall_status": self.overall_status,
            "divergence_warning": self.divergence_warning
        }


class SimulationValidationEngine:
    """
    Motor independiente de auditoría y validación de simulaciones cuantitativas.
    """

    def __init__(self, payout_ratio: float = 0.83):
        self.payout_ratio = payout_ratio
        self.data_validator = DataIntegrityValidator()
        self.temporal_validator = TemporalIntegrityValidator()
        self.lookahead_validator = NoLookAheadValidator()
        self.stat_validator = StatisticalValidator(payout_ratio=payout_ratio)
        self.oos_validator = OutOfSampleValidator()

    def run_pipeline(
        self,
        metadata: ExperimentMetadata,
        ticks_timestamps_ms: List[int],
        ticks_prices: List[float],
        trades: List[Dict[str, Any]],
        walk_forward_folds: Optional[List[Dict[str, Any]]] = None
    ) -> ValidationPipelineResult:
        """
        Ejecuta el pipeline de validación completo sobre un experimento.
        """
        # 1. Integridad de Datos
        data_rep = self.data_validator.validate_ticks(ticks_timestamps_ms, ticks_prices)

        # 2. Integridad Temporal
        temp_rep = self.temporal_validator.validate_timeline(ticks_timestamps_ms)

        # 3. Look-Ahead Audit
        look_rep = self.lookahead_validator.audit_simulation_pipeline(trades)

        # 4. Evaluación Estadística
        stat_rep = self.stat_validator.evaluate(
            trades=trades,
            data_integrity_passed=data_rep.is_valid,
            lookahead_passed=look_rep.passed
        )

        # 5. Out-Of-Sample / Walk-Forward (si aplica)
        wf_rep = None
        if walk_forward_folds:
            wf_rep = self.oos_validator.evaluate_walk_forward(walk_forward_folds, self.stat_validator.breakeven_wr)

        # 6. Veredicto Metodológico General
        if not data_rep.is_valid:
            status = "DATA_INVALID"
        elif not look_rep.passed:
            status = "POTENTIAL_LOOKAHEAD"
        elif stat_rep.resolved_trades < StatisticalValidator.MIN_SAMPLE_SIZE:
            status = "INSUFFICIENT_SAMPLE"
        elif wf_rep and not wf_rep.is_methodologically_valid:
            status = wf_rep.verdict
        else:
            status = "VALID_FOR_RESEARCH"

        return ValidationPipelineResult(
            metadata=metadata,
            data_integrity=data_rep,
            temporal_integrity=temp_rep,
            lookahead_audit=look_rep,
            statistical_report=stat_rep,
            walk_forward_report=wf_rep,
            overall_status=status
        )

    def compare_simulation_vs_observed(
        self,
        sim_result: ValidationPipelineResult,
        obs_result: ValidationPipelineResult
    ) -> str:
        """
        Genera una tabla comparativa estricta SIMULATION vs OBSERVED sin mezclar fuentes.
        Advierte divergencias importantes (|WR_sim - WR_obs| > 10%).
        """
        s_meta = sim_result.metadata
        o_meta = obs_result.metadata

        s_stat = sim_result.statistical_report
        o_stat = obs_result.statistical_report

        diff_wr = abs(s_stat.win_rate - o_stat.win_rate) * 100.0
        has_divergence = diff_wr > 10.0

        output = []
        output.append("=" * 82)
        output.append("📊 COMPARACIÓN METODOLÓGICA: SIMULACIÓN vs TELEMETRÍA OBSERVADA")
        output.append("=" * 82)
        output.append(f"{'Métrica':<30} | {'SIMULATION':<22} | {'OBSERVED':<22}")
        output.append("-" * 82)
        output.append(f"{'Fuente de Datos':<30} | {s_meta.data_source:<22} | {o_meta.data_source:<22}")
        output.append(f"{'Dataset ID':<30} | {s_meta.dataset_id:<22} | {o_meta.dataset_id:<22}")
        output.append(f"{'Trades Totales (N)':<30} | {s_stat.total_trades:<22} | {o_stat.total_trades:<22}")
        output.append(f"{'Trades Resueltos':<30} | {s_stat.resolved_trades:<22} | {o_stat.resolved_trades:<22}")
        output.append(f"{'Victorias / Derrotas':<30} | {f'{s_stat.wins} W / {s_stat.losses} L':<22} | {f'{o_stat.wins} W / {o_stat.losses} L':<22}")
        output.append(f"{'TIEs / Voids':<30} | {s_stat.voids:<22} | {o_stat.voids:<22}")
        output.append(f"{'Win Rate Observado':<30} | {f'{s_stat.win_rate*100:.2f}%':<22} | {f'{o_stat.win_rate*100:.2f}%':<22}")
        output.append(f"{'IC Wilson 95%':<30} | {f'[{s_stat.win_rate_ci_wilson_95[0]*100:.1f}%, {s_stat.win_rate_ci_wilson_95[1]*100:.1f}%]':<22} | {f'[{o_stat.win_rate_ci_wilson_95[0]*100:.1f}%, {o_stat.win_rate_ci_wilson_95[1]*100:.1f}%]':<22}")
        output.append(f"{'Esperanza Matemática (EV)':<30} | {f'{s_stat.expected_value:+.4f}':<22} | {f'{o_stat.expected_value:+.4f}':<22}")
        output.append(f"{'Max Drawdown':<30} | {f'{s_stat.max_drawdown_pct:.2f}%':<22} | {f'{o_stat.max_drawdown_pct:.2f}%':<22}")
        output.append(f"{'Peor Racha Pérdidas':<30} | {s_stat.longest_losing_streak:<22} | {o_stat.longest_losing_streak:<22}")
        output.append(f"{'Veredicto Metodológico':<30} | {sim_result.overall_status:<22} | {obs_result.overall_status:<22}")
        output.append("=" * 82)

        if has_divergence:
            warning_msg = (
                f"🚨 ADVERTENCIA DE DIVERGENCIA CRÍTICA: Existe una discrepancia de {diff_wr:.1f} puntos porcentuales "
                f"entre la simulación ({s_stat.win_rate*100:.1f}%) y la realidad observada ({o_stat.win_rate*100:.1f}%).\n"
                f"Causa probable: Sesgo de datos sintéticos, bug de microestructura en producción o latencia de ejecución no modelada.\n"
                f"REGLA: Prohibido manipular parámetros para 'forzar' coincidencia artificial."
            )
            output.append(warning_msg)
            sim_result.divergence_warning = warning_msg

        return "\n".join(output)
