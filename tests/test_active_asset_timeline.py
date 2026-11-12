import unittest

from scripts.research.active_asset_timeline import active_asset_at, build_timeline_manifest, join_records_to_active_asset, parse_asset_event, timeline_summary


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


    def test_causal_join_never_uses_future_observation(self):
        observations=[
            {"atMs": 2000, "ric": "B"},
        ]
        result=active_asset_at(observations, 1500, max_age_ms=5000)
        self.assertEqual("UNKNOWN_NO_PRIOR_OBSERVATION", result["status"])
        self.assertIsNone(result["ric"])

    def test_causal_join_uses_latest_prior_observation_with_explicit_age_bound(self):
        observations=[
            {"atMs": 1000, "ric": "A"},
            {"atMs": 2000, "ric": "B"},
        ]
        before_switch=active_asset_at(observations, 1900, max_age_ms=1000)
        after_switch=active_asset_at(observations, 2100, max_age_ms=1000)
        self.assertEqual(("KNOWN_CAUSAL_OBSERVATION","A",900), (before_switch["status"],before_switch["ric"],before_switch["age_ms"]))
        self.assertEqual(("KNOWN_CAUSAL_OBSERVATION","B",100), (after_switch["status"],after_switch["ric"],after_switch["age_ms"]))

    def test_causal_join_marks_stale_observation_unknown(self):
        result=active_asset_at([{"atMs":1000,"ric":"A"}], 2501, max_age_ms=1500)
        self.assertEqual("UNKNOWN_STALE_OBSERVATION", result["status"])
        self.assertIsNone(result["ric"])
        self.assertEqual(1501, result["age_ms"])

    def test_conflicting_same_timestamp_assets_are_unknown(self):
        result=active_asset_at([
            {"atMs":1000,"ric":"A"},
            {"atMs":1000,"ric":"B"},
        ], 1000, max_age_ms=0)
        self.assertEqual("UNKNOWN_CONFLICT_AT_TIMESTAMP", result["status"])
        self.assertIsNone(result["ric"])

    def test_join_keeps_unknown_records_instead_of_dropping_them(self):
        joined=join_records_to_active_asset(
            [{"ts":500},{"ts":1500},{"bad":"x"}],
            [{"atMs":1000,"ric":"A"}],
            timestamp_field="ts",
            max_age_ms=1000,
        )
        self.assertEqual("UNKNOWN_NO_PRIOR_OBSERVATION",joined[0]["active_asset_evidence"]["status"])
        self.assertEqual("KNOWN_CAUSAL_OBSERVATION",joined[1]["active_asset_evidence"]["status"])
        self.assertEqual("UNKNOWN_INVALID_RECORD_TIMESTAMP",joined[2]["active_asset_evidence"]["status"])


    def test_manifest_builder_seals_source_and_preserves_only_explicit_observations(self):
        records=[
            {"kind":"TRANSPORT","payload":{},"sha256":"1"*64},
            {"kind":"ACTIVE_ASSET_OBSERVATION","payload":{
                "atMs":2000,"ric":"B","quality":"RUNTIME_SCREEN_ASSET_OBSERVED"
            },"sha256":"2"*64},
            {"kind":"ACTIVE_ASSET_OBSERVATION","payload":{
                "atMs":1000,"ric":"A","quality":"RUNTIME_SCREEN_ASSET_OBSERVED"
            },"sha256":"3"*64},
        ]
        manifest=build_timeline_manifest(
            records,
            max_age_ms=2500,
            code_sha="deadbeef",
            collector_sha256="a"*64,
        )
        self.assertEqual(1,manifest["schema_version"])
        self.assertEqual("3"*64,manifest["source_journal_final_sha256"])
        self.assertEqual(2500,manifest["max_age_ms"])
        self.assertEqual(["A","B"],[row["ric"] for row in manifest["observations"]])

    def test_manifest_builder_rejects_unsealed_or_invalid_identity(self):
        with self.assertRaisesRegex(ValueError,"SOURCE_JOURNAL_EMPTY"):
            build_timeline_manifest([],max_age_ms=1,code_sha="x",collector_sha256="a"*64)
        with self.assertRaisesRegex(ValueError,"INVALID_COLLECTOR_SHA256"):
            build_timeline_manifest(
                [{"kind":"X","payload":{},"sha256":"b"*64}],
                max_age_ms=1,code_sha="x",collector_sha256="bad",
            )


    def test_shared_golden_contract_matches_python_lookup(self):
        import json
        from pathlib import Path
        root=Path(__file__).resolve().parents[1]
        manifest=json.loads((root/'data/fixtures/research/active_asset_timeline_v1.json').read_text())
        cases=json.loads((root/'data/fixtures/research/active_asset_timeline_v1_cases.json').read_text())
        observations=manifest["observations"]
        max_age=manifest["max_age_ms"]
        for case in cases:
            actual=active_asset_at(observations,case["timestamp_ms"],max_age_ms=max_age)
            self.assertEqual(case["status"],actual["status"],case)
            self.assertEqual(case["ric"],actual["ric"],case)
            self.assertEqual(case["observation_at_ms"],actual["observation_at_ms"],case)
            self.assertEqual(case["age_ms"],actual["age_ms"],case)


if __name__ == "__main__":
    unittest.main()
