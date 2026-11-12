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
    def test_markdown_only_is_lightweight(self):
        result = ci_change_classify.classify([
            "README.md",
            "loops/r29c/STATE.md",
        ])
        self.assertEqual("true", result["docs_only"])
        self.assertEqual("false", result["heavy"])

    def test_mixed_markdown_and_code_is_heavy(self):
        result = ci_change_classify.classify([
            "loops/r29c/STATE.md",
            "research/src/Test.kt",
        ])
        self.assertEqual("false", result["docs_only"])
        self.assertEqual("true", result["heavy"])

    def test_workflow_or_python_change_is_heavy(self):
        for path in (
            ".github/workflows/android-build.yml",
            ".github/ci_change_classify.py",
        ):
            with self.subTest(path=path):
                result = ci_change_classify.classify([path])
                self.assertEqual("false", result["docs_only"])
                self.assertEqual("true", result["heavy"])

    def test_empty_change_set_fails_conservative(self):
        result = ci_change_classify.classify([])
        self.assertEqual("false", result["docs_only"])
        self.assertEqual("true", result["heavy"])

    def test_uppercase_markdown_extension_is_supported(self):
        self.assertTrue(ci_change_classify.is_markdown_only(["REPORT.MD"]))


if __name__ == "__main__":
    unittest.main()
