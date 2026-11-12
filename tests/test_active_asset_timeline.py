import unittest

from scripts.research.active_asset_timeline import parse_asset_event, timeline_summary


class ActiveAssetTimelineTest(unittest.TestCase):
    def test_parses_runtime_asset_event_without_inference(self):
        row = parse_asset_event({
            "atMs": 1234,
            "kind": "ASSET",
            "detail": 'visible="Crypto IDX" ric="Z-CRY/IDX"',
        })
        self.assertEqual("Crypto IDX", row["visibleAsset"])
        self.assertEqual("Z-CRY/IDX", row["ric"])
        self.assertEqual("RUNTIME_SCREEN_ASSET_OBSERVED", row["quality"])
        self.assertFalse(row["exact_interval_end"])

    def test_rejects_malformed_or_non_asset_events(self):
        self.assertIsNone(parse_asset_event({"atMs": 1, "kind": "VETO", "detail": "x"}))
        self.assertIsNone(parse_asset_event({"atMs": 1, "kind": "ASSET", "detail": "activo=X"}))
        self.assertIsNone(parse_asset_event({"atMs": -1, "kind": "ASSET", "detail": 'visible="A" ric="B"'}))

    def test_summary_never_claims_mixed_asset_replay_support(self):
        summary = timeline_summary([
            {"atMs": 10, "ric": "A"},
            {"atMs": 20, "ric": "B"},
        ])
        self.assertEqual(["A", "B"], summary["assets"])
        self.assertEqual(2, summary["observations"])
        self.assertFalse(summary["supports_mixed_asset_replay"])

    def test_summary_detects_timestamp_regression_in_observed_order(self):
        summary = timeline_summary([
            {"atMs": 20, "ric": "A"},
            {"atMs": 10, "ric": "A"},
        ])
        self.assertEqual(1, summary["timestamp_regressions"])
        self.assertEqual(10, summary["first_at_ms"])
        self.assertEqual(20, summary["last_at_ms"])


if __name__ == "__main__":
    unittest.main()
