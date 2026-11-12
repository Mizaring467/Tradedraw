import importlib.util
from pathlib import Path
import tempfile
import unittest
import sys


MODULE_PATH = Path(__file__).resolve().parents[1] / "scripts" / "ci" / "fast_verify.py"
SPEC = importlib.util.spec_from_file_location("fast_verify", MODULE_PATH)
fast_verify = importlib.util.module_from_spec(SPEC)
assert SPEC and SPEC.loader
sys.modules[SPEC.name] = fast_verify
SPEC.loader.exec_module(fast_verify)


class FastVerifyTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.repo = Path(__file__).resolve().parents[1]
        cls.modules = fast_verify.discover_modules(cls.repo)
        cls.graph = fast_verify.parse_module_graph(cls.repo, cls.modules)

    def test_discovers_current_modules(self):
        expected = {
            "app", "core", "simulation", "persistence", "market",
            "strategy", "observability", "risk", "execution",
            "ui", "research", "adapters",
        }
        self.assertEqual(expected, set(self.modules))

    def test_core_change_reaches_every_module(self):
        affected = fast_verify.downstream_closure({"core"}, self.graph)
        self.assertEqual(set(self.modules), affected)

    def test_ui_change_reaches_adapters_and_app_only_downstream(self):
        affected = fast_verify.downstream_closure({"ui"}, self.graph)
        self.assertEqual({"ui", "adapters", "app"}, affected)

    def test_research_change_does_not_expand_to_app(self):
        affected = fast_verify.downstream_closure({"research"}, self.graph)
        self.assertEqual({"research"}, affected)

    def test_strategy_change_reaches_known_consumers(self):
        affected = fast_verify.downstream_closure({"strategy"}, self.graph)
        self.assertTrue({"strategy", "execution", "adapters", "research", "app"} <= affected)

    def test_jvm_and_android_tasks_are_selected_correctly(self):
        self.assertEqual(
            ":core:test",
            fast_verify.module_test_task("core", self.modules["core"]),
        )
        self.assertEqual(
            ":simulation:test",
            fast_verify.module_test_task("simulation", self.modules["simulation"]),
        )
        self.assertEqual(
            ":strategy:testDebugUnitTest",
            fast_verify.module_test_task("strategy", self.modules["strategy"]),
        )

    def test_root_gradle_change_forces_full_gate(self):
        plan = fast_verify.build_plan(
            self.repo,
            ["settings.gradle.kts"],
            full=False,
        )
        self.assertEqual(set(self.modules), set(plan.affected_modules))
        self.assertTrue(plan.run_python)
        self.assertTrue(plan.run_replay)
        self.assertTrue(plan.build_apk)

    def test_python_only_change_avoids_gradle(self):
        plan = fast_verify.build_plan(
            self.repo,
            ["tests/test_something.py"],
            full=False,
        )
        self.assertFalse(plan.gradle_tasks)
        self.assertTrue(plan.run_python)
        self.assertFalse(plan.build_apk)

    def test_data_change_runs_replay_without_gradle(self):
        plan = fast_verify.build_plan(
            self.repo,
            ["data/ticks/replay_eth.csv"],
            full=False,
        )
        self.assertFalse(plan.gradle_tasks)
        self.assertTrue(plan.run_replay)

    def test_ignored_generated_paths(self):
        self.assertTrue(fast_verify.is_ignored("app/build/foo.xml"))
        self.assertTrue(fast_verify.is_ignored("graft/.cache/file.json"))
        self.assertFalse(fast_verify.is_ignored("app/src/main/Foo.kt"))

    def test_fast_gradle_command_reuses_daemon_and_combines_apk(self):
        plan = fast_verify.build_plan(
            self.repo,
            ["ui/src/main/java/com/example/tradedraw/CustomDrawingView.kt"],
        )
        command = fast_verify.gradle_command(plan)
        self.assertIn(":ui:testDebugUnitTest", command)
        self.assertIn(":adapters:testDebugUnitTest", command)
        self.assertIn(":app:testDebugUnitTest", command)
        self.assertIn(":app:assembleDebug", command)
        self.assertNotIn("--no-daemon", command)

    def test_full_gradle_command_is_clean_and_cold(self):
        plan = fast_verify.build_plan(self.repo, [], full=True)
        command = fast_verify.gradle_command(plan)
        self.assertIn("clean", command)
        self.assertLess(command.index("clean"), command.index(":app:testDebugUnitTest"))
        self.assertIn("--no-daemon", command)
        self.assertEqual(1, command.count(":app:assembleDebug"))

    def test_full_summary_rejects_skips(self):
        import tempfile
        from pathlib import Path
        with tempfile.TemporaryDirectory() as tmp:
            result_dir = Path(tmp) / "app/build/test-results/testDebugUnitTest"
            result_dir.mkdir(parents=True)
            (result_dir / "TEST-sample.xml").write_text(
                '<testsuite tests="2" failures="0" errors="0" skipped="1"></testsuite>'
            )
            with self.assertRaises(SystemExit):
                fast_verify.enforce_full_test_summary(Path(tmp))

    def test_full_summary_accepts_zero_skip_green_results(self):
        import tempfile
        from pathlib import Path
        with tempfile.TemporaryDirectory() as tmp:
            result_dir = Path(tmp) / "core/build/test-results/test"
            result_dir.mkdir(parents=True)
            (result_dir / "TEST-sample.xml").write_text(
                '<testsuite tests="3" failures="0" errors="0" skipped="0"></testsuite>'
            )
            fast_verify.enforce_full_test_summary(Path(tmp))


    def test_verification_environment_pins_canonical_replay_fixture(self):
        env = fast_verify.verification_environment(self.repo)
        self.assertEqual(
            str(self.repo / "loops/r23c_phase3_validation/window_01/ticks_live.csv"),
            env["BACKTEST_CSV"],
        )
        self.assertEqual(
            str(self.repo / "build/fast-verify-replay.csv"),
            env["BACKTEST_OUT"],
        )
        self.assertIn(str(self.repo / "validation_engine"), env["PYTHONPATH"])


if __name__ == "__main__":
    unittest.main()
