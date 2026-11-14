import tempfile
import unittest
from pathlib import Path
from scripts.ci.verify_clean_worktree import parse_gradle_test_results


class GradleResultsTest(unittest.TestCase):
    def write_suite(self, directory, tests, failures=0, skipped=0):
        directory.mkdir(parents=True, exist_ok=True)
        (directory / "TEST-fixture.xml").write_text(
            f'<testsuite tests="{tests}" failures="{failures}" errors="0" skipped="{skipped}" time="0.1">'
            '<testcase classname="Fixture" name="case">'
            + ('<failure message="detected"/>' if failures else '')
            + '</testcase></testsuite>'
        )

    def test_multimodule_report_includes_jvm_research_and_failures(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            self.write_suite(root / "app/build/test-results/testDebugUnitTest", 2)
            self.write_suite(root / "core/build/test-results/test", 1, failures=1)
            self.write_suite(root / "research/build/test-results/testDebugUnitTest", 3, skipped=1)
            self.write_suite(root / "unrelated", 99)
            result = parse_gradle_test_results(temp, multi_module=True)
            self.assertEqual((6, 1, 1, 4, 3), tuple(result[k] for k in
                             ("total_tests", "failures", "skipped", "passed", "classes")))
            self.assertEqual(1, len(result["failed_tests"]))

    def test_single_directory_api_remains_compatible(self):
        with tempfile.TemporaryDirectory() as temp:
            self.write_suite(Path(temp), 2)
            self.assertEqual(2, parse_gradle_test_results(temp)["passed"])
