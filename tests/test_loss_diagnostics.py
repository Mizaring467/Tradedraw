#!/usr/bin/env python3
"""
tests/test_loss_diagnostics.py
Tests verifying the empirical findings from the Root Cause Failure Analysis (ANÁLISIS DE POR QUÉ TRADEDraw PIERDE).
"""

import unittest
import csv
import json
import os
from datetime import datetime


class TestLossDiagnostics(unittest.TestCase):

    def setUp(self):
        self.journal_path = "loops/journal/trade_journal_device_all.csv"
        self.adaptive_state_path = "loops/journal/adaptive_learning_state.json"
        self.diagnostic_csv_path = "LOSS_DIAGNOSTIC_DATA.csv"
        self.findings_json_path = "LOSS_FINDINGS.json"

        with open(self.journal_path, "r", encoding="utf-8") as f:
            self.rows = list(csv.DictReader(f))

    def test_timing_boundary_failure_rate(self):
        """
        Proves that trades entered at seconds :58 and :59 suffered a 100% loss rate
        due to candle boundary rollover misalignment.
        """
        timing_trades = []
        for r in self.rows:
            dt = datetime.fromisoformat(r["iso_date"])
            if dt.second in (58, 59):
                timing_trades.append(r)

        resolved = [r for r in timing_trades if r["result"] in ("WIN", "LOSS")]
        wins = sum(1 for r in resolved if r["result"] == "WIN")
        losses = sum(1 for r in resolved if r["result"] == "LOSS")

        self.assertEqual(len(resolved), 23)
        self.assertEqual(wins, 0, "Expected zero wins at :58-:59 boundary")
        self.assertEqual(losses, 23, "Expected 100% loss rate at :58-:59 boundary")

    def test_sideways_chop_concentration(self):
        """
        Proves that 100% of recorded loss signatures in the adaptive engine occurred in SIDEWAYS regime.
        """
        with open(self.adaptive_state_path, "r", encoding="utf-8") as f:
            state = json.load(f)

        sigs = state.get("lossSignatures", [])
        self.assertGreater(len(sigs), 0, "Expected loss signatures in state")
        sideways_count = sum(1 for s in sigs if s.get("trend") == "SIDEWAYS")
        self.assertEqual(sideways_count, len(sigs), "All loss signatures must be in SIDEWAYS market")

    def test_label_failure_detection(self):
        """
        Proves that rows #0 and #1 in trade_journal_device_all.csv were declared LOSS
        without any balance change (Diff = 0.0), constituting LABEL_FAILURE.
        """
        r0 = self.rows[0]
        r1 = self.rows[1]

        diff0 = float(r0["settled_balance"]) - float(r0["base_balance"])
        diff1 = float(r1["settled_balance"]) - float(r1["base_balance"])

        self.assertEqual(diff0, 0.0)
        self.assertEqual(diff1, 0.0)
        self.assertEqual(r0["result"], "LOSS")
        self.assertEqual(r1["result"], "LOSS")
        self.assertIn("Diff=0.0", r0["reason"])
        self.assertIn("Diff=0.0", r1["reason"])

    def test_diagnostic_dataset_integrity(self):
        """
        Verifies that LOSS_DIAGNOSTIC_DATA.csv exists, has exactly 212 rows matching the raw journal,
        and every loss has a valid failure category assigned.
        """
        self.assertTrue(os.path.exists(self.diagnostic_csv_path), "LOSS_DIAGNOSTIC_DATA.csv must exist")
        with open(self.diagnostic_csv_path, "r", encoding="utf-8") as f:
            diag_rows = list(csv.DictReader(f))

        self.assertEqual(len(diag_rows), len(self.rows), "Row count must match raw journal exactly")

        valid_categories = {
            "NONE_WIN", "EXECUTION_VOID", "DATA_FAILURE", "LABEL_FAILURE",
            "EXECUTION_FAILURE", "REGIME_FAILURE", "SIGNAL_FAILURE", "UNKNOWN"
        }

        for r in diag_rows:
            self.assertIn(r["failure_category"], valid_categories)
            if r["raw_result"] == "LOSS":
                self.assertNotIn(r["failure_category"], {"NONE_WIN", "EXECUTION_VOID", "NONE"})


if __name__ == "__main__":
    unittest.main()
