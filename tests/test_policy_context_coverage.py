import unittest

from scripts.research.policy_context_coverage import (
    POLICY_CONTEXT_REQUIRED,
    coverage_summary,
    missing_policy_context,
    policy_context_complete,
)


def base_prediction():
    return {
        "decisionId": "d-1",
        "timestamp": 1_000,
        "asset": "Z-CRY/IDX",
        "strategyVersion": "AUTO_ADAPTIVE@frozen",
        "action": "BUY",
        "confidence": 0.85,
        "reason": "signal",
        "regime": "UNKNOWN",
        "observed_payout": None,
        "git_sha": "abc",
        "entry_price": 100.0,
    }


class PolicyContextCoverageTest(unittest.TestCase):
    def test_current_prospective_schema_is_not_mistaken_for_policy_parity(self):
        prediction = base_prediction()
        missing = missing_policy_context(prediction)
        self.assertEqual(set(POLICY_CONTEXT_REQUIRED), set(missing))
        self.assertFalse(policy_context_complete(prediction))

    def test_complete_precommitted_context_is_recognized_without_defaults(self):
        prediction = base_prediction()
        prediction["policy_context"] = {field: "observed" for field in POLICY_CONTEXT_REQUIRED}
        prediction["policy_context"]["tickAgeMs"] = None
        self.assertEqual([], missing_policy_context(prediction))
        self.assertTrue(policy_context_complete(prediction))

    def test_missing_single_field_is_reported_exactly(self):
        prediction = base_prediction()
        prediction["policy_context"] = {field: 1 for field in POLICY_CONTEXT_REQUIRED}
        del prediction["policy_context"]["capitalPolicyCanExecute"]
        self.assertEqual(["capitalPolicyCanExecute"], missing_policy_context(prediction))

    def test_summary_counts_missing_fields_across_cohort(self):
        incomplete = base_prediction()
        complete = base_prediction()
        complete["decisionId"] = "d-2"
        complete["policy_context"] = {field: 1 for field in POLICY_CONTEXT_REQUIRED}
        summary = coverage_summary([incomplete, complete])
        self.assertEqual(2, summary["total_predictions"])
        self.assertEqual(1, summary["complete_predictions"])
        self.assertEqual(0.5, summary["coverage_ratio"])
        self.assertEqual(1, summary["missing_field_counts"]["feedAsset"])
        self.assertEqual("POLICY_CONTEXT_INCOMPLETE", summary["status"])

    def test_empty_cohort_is_not_claimed_complete(self):
        summary = coverage_summary([])
        self.assertIsNone(summary["coverage_ratio"])
        self.assertEqual("POLICY_CONTEXT_INCOMPLETE", summary["status"])


if __name__ == "__main__":
    unittest.main()
