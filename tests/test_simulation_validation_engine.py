#!/usr/bin/env python3
"""
Unit tests for SimulationValidationEngine
"""

import unittest
import numpy as np
from validation_engine import (
    SimulationValidationEngine,
    DataIntegrityValidator,
    TemporalIntegrityValidator,
    NoLookAheadValidator,
    ExecutionModel,
    StatisticalValidator,
    OutOfSampleValidator,
    ExperimentMetadata,
    DataSource
)


class TestSimulationValidationEngine(unittest.TestCase):

    def setUp(self):
        self.engine = SimulationValidationEngine(payout_ratio=0.83)

    def test_data_integrity_detects_corruptions(self):
        validator = DataIntegrityValidator()
        timestamps = [1000, 2000, 1500, 4000]  # 1500 is out of order
        prices = [100.0, 101.0, float('nan'), 102.0]  # nan

        report = validator.validate_ticks(timestamps, prices)
        self.assertFalse(report.is_valid)
        self.assertGreater(report.error_count, 0)
        self.assertTrue(any("NaN" in issue for issue in report.issues))
        self.assertTrue(any("orden" in issue for issue in report.issues))

    def test_no_look_ahead_detects_future_features(self):
        validator = NoLookAheadValidator()
        trades = [
            {"signal_time": 1000.0, "entry_time": 1200.0, "expiry_time": 61200.0, "feature_cutoff_time": 1500.0}
        ]
        report = validator.audit_simulation_pipeline(trades)
        self.assertFalse(report.passed)
        self.assertTrue(report.has_feature_leakage)

    def test_statistical_validator_flags_insufficient_sample(self):
        validator = StatisticalValidator(payout_ratio=0.83)
        # Only 10 trades with 90% WR
        trades = [{"result": "WIN", "return_unit": 0.83} for _ in range(9)]
        trades.append({"result": "LOSS", "return_unit": -1.0})

        report = validator.evaluate(trades)
        self.assertEqual(report.resolved_trades, 10)
        self.assertEqual(report.win_rate, 0.90)
        # Even with 90% WR, status MUST be INSUFFICIENT_SAMPLE because N < 30
        self.assertEqual(report.validation_status, "INSUFFICIENT_SAMPLE")

    def test_execution_model_simulates_realistic_delay(self):
        model = ExecutionModel(latency_ms=300.0, slippage_std=0.001, seed=42)
        ticks_ts = np.array([0, 100, 200, 300, 400, 500, 60000, 60400], dtype=np.float64)
        ticks_p = np.array([100.0, 100.2, 100.4, 100.6, 100.8, 101.0, 102.0, 102.5], dtype=np.float64)

        trade = model.simulate_order(
            signal_time_ms=50.0,
            signal_price=100.1,
            action="CALL",
            ticks_timestamps_ms=ticks_ts,
            ticks_prices=ticks_p
        )
        self.assertTrue(trade.is_executed)
        self.assertGreater(trade.execution_time_ms, trade.signal_time_ms)
        self.assertEqual(trade.result, "WIN")

    def test_divergence_warning_between_sim_and_observed(self):
        sim_meta = ExperimentMetadata(
            data_source=DataSource.SYNTHETIC.value,
            dataset_id="synth_10k",
            start_time="2026-09-01",
            end_time="2026-09-02",
            sample_count=100,
            strategy_version="1.0",
            parameters={"z": 2.0},
            execution_model="zero_latency",
            seed=42
        )
        obs_meta = ExperimentMetadata(
            data_source=DataSource.OBSERVED.value,
            dataset_id="device_all",
            start_time="2026-09-10",
            end_time="2026-09-17",
            sample_count=100,
            strategy_version="1.0",
            parameters={"z": 2.0},
            execution_model="broker_live",
            seed=0
        )

        # Sim with 80% WR
        sim_trades = [{"result": "WIN", "return_unit": 0.83} for _ in range(80)] + \
                     [{"result": "LOSS", "return_unit": -1.0} for _ in range(20)]
        sim_ts = list(range(0, 100000, 1000))
        sim_p = [100.0 + i*0.01 for i in range(100)]
        sim_res = self.engine.run_pipeline(sim_meta, sim_ts, sim_p, sim_trades)

        # Obs with 40% WR
        obs_trades = [{"result": "WIN", "return_unit": 0.83} for _ in range(40)] + \
                     [{"result": "LOSS", "return_unit": -1.0} for _ in range(60)]
        obs_res = self.engine.run_pipeline(obs_meta, sim_ts, sim_p, obs_trades)

        comp_text = self.engine.compare_simulation_vs_observed(sim_res, obs_res)
        self.assertIn("ADVERTENCIA DE DIVERGENCIA CRÍTICA", comp_text)
        self.assertIsNotNone(sim_res.divergence_warning)


if __name__ == "__main__":
    unittest.main()
