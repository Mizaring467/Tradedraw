import importlib.util
from pathlib import Path
import sys
import unittest


MODULE_PATH = Path(__file__).resolve().parents[1] / "scripts" / "ci" / "verify_clean_worktree.py"
SPEC = importlib.util.spec_from_file_location("verify_clean_worktree", MODULE_PATH)
verify = importlib.util.module_from_spec(SPEC)
assert SPEC and SPEC.loader
sys.modules[SPEC.name] = verify
SPEC.loader.exec_module(verify)


class CleanWorktreeUntrackedTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.repo = Path(__file__).resolve().parents[1]
        cls.prefixes = verify.discover_gradle_module_prefixes(str(cls.repo))

    def test_discovers_all_modularized_gradle_modules(self):
        expected = {
            "app/", "core/", "simulation/", "persistence/", "market/",
            "strategy/", "observability/", "risk/", "execution/", "ui/",
            "research/", "adapters/",
        }
        self.assertEqual(expected, set(self.prefixes))

    def test_new_module_sources_are_copied(self):
        for path in (
            "strategy/src/main/java/NewRule.kt",
            "adapters/src/test/java/NewAdapterTest.kt",
            "research/src/test/java/NewResearchTest.kt",
            "core/src/main/kotlin/NewPort.kt",
        ):
            with self.subTest(path=path):
                self.assertTrue(verify.should_copy_untracked(path, self.prefixes))

    def test_research_and_ci_inputs_are_copied(self):
        for path in (
            "scripts/research/new_audit.py",
            "tests/test_new_gate.py",
            "datasets/manifests/new.v1.json",
            "experiments/exp-1/manifest.json",
            "data/ticks/new.csv",
        ):
            with self.subTest(path=path):
                self.assertTrue(verify.should_copy_untracked(path, self.prefixes))

    def test_runtime_junk_is_not_copied(self):
        for path in (
            "scratch/build.log",
            "screen.png",
            "app/build/generated/foo.kt",
            "scripts/__pycache__/tool.pyc",
            "loops/r29c/STATE.md",
        ):
            with self.subTest(path=path):
                self.assertFalse(verify.should_copy_untracked(path, self.prefixes))

    def test_windows_separators_are_normalized(self):
        self.assertTrue(
            verify.should_copy_untracked(
                r"adapters\src\main\java\NewAdapter.kt",
                self.prefixes,
            )
        )


if __name__ == "__main__":
    unittest.main()
