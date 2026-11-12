import importlib.util
from pathlib import Path
import sys
import unittest


MODULE_PATH = Path(__file__).resolve().parents[1] / ".github" / "ci_change_classify.py"
SPEC = importlib.util.spec_from_file_location("ci_change_classify", MODULE_PATH)
ci_change_classify = importlib.util.module_from_spec(SPEC)
assert SPEC and SPEC.loader
sys.modules[SPEC.name] = ci_change_classify
SPEC.loader.exec_module(ci_change_classify)


class CiChangeClassifyTest(unittest.TestCase):
    def assert_gate(
        self,
        paths,
        *,
        docs_only,
        python_only,
        needs_python,
        needs_android,
    ):
        result = ci_change_classify.classify(paths)
        self.assertEqual(str(docs_only).lower(), result["docs_only"])
        self.assertEqual(str(python_only).lower(), result["python_only"])
        self.assertEqual(str(needs_python).lower(), result["needs_python"])
        self.assertEqual(str(needs_android).lower(), result["needs_android"])
        self.assertEqual(str(needs_android).lower(), result["heavy"])

    def test_markdown_only_is_docs_fast_path(self):
        self.assert_gate(
            ["README.md", "loops/r29c/STATE.md"],
            docs_only=True,
            python_only=False,
            needs_python=False,
            needs_android=False,
        )

    def test_python_tooling_only_runs_python_without_android(self):
        self.assert_gate(
            [
                "scripts/research/prospective.py",
                "tests/test_r29c_prospective.py",
                "docs/NOTE.md",
            ],
            docs_only=False,
            python_only=True,
            needs_python=True,
            needs_android=False,
        )

    def test_ci_python_and_requirements_are_python_only(self):
        self.assert_gate(
            [
                "scripts/ci/verify_clean_worktree.py",
                "scripts/ci/requirements-tests.txt",
                "tests/test_gradle_results.py",
            ],
            docs_only=False,
            python_only=True,
            needs_python=True,
            needs_android=False,
        )

    def test_validation_engine_python_is_python_only(self):
        self.assert_gate(
            ["validation_engine/example.py"],
            docs_only=False,
            python_only=True,
            needs_python=True,
            needs_android=False,
        )

    def test_workflow_and_classifier_changes_remain_heavy(self):
        for path in (
            ".github/workflows/android-build.yml",
            ".github/ci_change_classify.py",
        ):
            with self.subTest(path=path):
                self.assert_gate(
                    [path],
                    docs_only=False,
                    python_only=False,
                    needs_python=True,
                    needs_android=True,
                )

    def test_dataset_or_kotlin_mixed_with_python_is_heavy(self):
        for extra in (
            "data/ticks/replay.csv",
            "research/src/Test.kt",
            "config.json",
            "scripts/validation/run.sh",
        ):
            with self.subTest(extra=extra):
                self.assert_gate(
                    ["tests/test_example.py", extra],
                    docs_only=False,
                    python_only=False,
                    needs_python=True,
                    needs_android=True,
                )

    def test_empty_change_set_fails_conservative(self):
        self.assert_gate(
            [],
            docs_only=False,
            python_only=False,
            needs_python=True,
            needs_android=True,
        )

    def test_uppercase_markdown_extension_is_supported(self):
        self.assertTrue(ci_change_classify.is_markdown_only(["REPORT.MD"]))

    def test_normalize_preserves_dotfile_prefix(self):
        self.assertEqual(
            ".github/workflows/android-build.yml",
            ci_change_classify.normalize(".github/workflows/android-build.yml"),
        )
        self.assertEqual(
            ".github/workflows/android-build.yml",
            ci_change_classify.normalize("./.github/workflows/android-build.yml"),
        )


if __name__ == "__main__":
    unittest.main()
