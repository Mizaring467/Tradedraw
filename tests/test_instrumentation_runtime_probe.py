import unittest

from scripts.research.instrumentation_runtime_probe import (
    BASE_STRATEGY_VERSION,
    INSTRUMENTED_VERSION,
    validate_runtime,
)
from scripts.research.policy_context_coverage import (
    EXACT_POLICY_CONTEXT_QUALITY,
    EXACT_POLICY_CONTEXT_VERSION,
    POLICY_CONTEXT_REQUIRED,
)


def exact_prediction(sha="3917945"):
    context = {field: "x" for field in POLICY_CONTEXT_REQUIRED}
    context["tickAgeMs"] = None
    context["adaptiveDecision"] = {"kind": "ALLOW", "action": "BUY"}
    context["capitalPolicyCanExecute"] = {"allowed": True, "reason": "ok"}
    return {
        "decisionId": "d1",
        "strategyVersion": f"AUTO_ADAPTIVE@{INSTRUMENTED_VERSION}",
        "base_strategy_version": BASE_STRATEGY_VERSION,
        "git_sha": sha,
        "policy_context_quality": EXACT_POLICY_CONTEXT_QUALITY,
        "policy_context_version": EXACT_POLICY_CONTEXT_VERSION,
        "policy_context": context,
    }


class InstrumentationRuntimeProbeTest(unittest.TestCase):
    def test_passes_get_only_preflight_without_pending_prediction(self):
        status = {"gitSha": "3917945", "mode": "SHADOW"}
        simulation = {"enabled": True, "pendingDecisions": []}
        report = validate_runtime(status, simulation, "3917945")
        self.assertEqual("PASS", report["status"])
        self.assertEqual(0, report["exact_pending_predictions"])

    def test_can_require_exact_pending_prediction(self):
        status = {"gitSha": "3917945", "mode": "SHADOW"}
        simulation = {"enabled": True, "pendingDecisions": [exact_prediction()]}
        report = validate_runtime(
            status, simulation, "3917945", require_prediction=True
        )
        self.assertEqual("PASS", report["status"])
        self.assertEqual(1, report["exact_pending_predictions"])

    def test_rejects_wrong_runtime_sha_or_non_shadow_mode(self):
        report = validate_runtime(
            {"gitSha": "wrong", "mode": "DISABLED"},
            {"enabled": True, "pendingDecisions": []},
            "3917945",
        )
        self.assertEqual("FAIL", report["status"])
        self.assertIn("RUNTIME_GIT_SHA_MISMATCH", report["errors"])
        self.assertIn("MODE_NOT_SHADOW", report["errors"])

    def test_rejects_disabled_simulation(self):
        report = validate_runtime(
            {"gitSha": "3917945", "mode": "SHADOW"},
            {"enabled": False, "pendingDecisions": []},
            "3917945",
        )
        self.assertEqual("FAIL", report["status"])
        self.assertIn("SIMULATION_NOT_ENABLED", report["errors"])

    def test_rejects_partial_context_even_if_quality_claims_exact(self):
        prediction = exact_prediction()
        prediction["policy_context"] = {"action": "BUY"}
        report = validate_runtime(
            {"gitSha": "3917945", "mode": "SHADOW"},
            {"enabled": True, "pendingDecisions": [prediction]},
            "3917945",
            require_prediction=True,
        )
        self.assertEqual("FAIL", report["status"])
        self.assertIn("INVALID_PENDING_PREDICTIONS", report["errors"])
        joined = ",".join(report["prediction_errors"][0]["errors"])
        self.assertIn("POLICY_CONTEXT_MISSING_FIELDS", joined)

    def test_rejects_frozen_strategy_label_for_instrumented_evidence(self):
        prediction = exact_prediction()
        prediction["strategyVersion"] = "AUTO_ADAPTIVE@r29c-frozen-4fdb646"
        report = validate_runtime(
            {"gitSha": "3917945", "mode": "SHADOW"},
            {"enabled": True, "pendingDecisions": [prediction]},
            "3917945",
        )
        self.assertEqual("FAIL", report["status"])
        self.assertIn(
            "WRONG_INSTRUMENTED_STRATEGY_VERSION",
            report["prediction_errors"][0]["errors"],
        )

    def test_optional_feed_gate_requires_connected_and_fresh(self):
        status = {
            "gitSha": "3917945",
            "mode": "SHADOW",
            "feed": {"connected": True, "isFresh": True},
        }
        report = validate_runtime(
            status,
            {"enabled": True, "pendingDecisions": []},
            "3917945",
            require_fresh_feed=True,
        )
        self.assertEqual("PASS", report["status"])

        status["feed"]["isFresh"] = False
        report = validate_runtime(
            status,
            {"enabled": True, "pendingDecisions": []},
            "3917945",
            require_fresh_feed=True,
        )
        self.assertEqual("FAIL", report["status"])
        self.assertIn("FEED_NOT_FRESH", report["errors"])


if __name__ == "__main__":
    unittest.main()
