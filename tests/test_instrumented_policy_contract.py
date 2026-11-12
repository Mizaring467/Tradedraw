import re
from pathlib import Path
import unittest

from scripts.research.policy_context_coverage import (
    EXACT_POLICY_CONTEXT_QUALITY,
    EXACT_POLICY_CONTEXT_VERSION,
    POLICY_CONTEXT_REQUIRED,
)


class InstrumentedPolicyContractTest(unittest.TestCase):
    def test_kotlin_exact_schema_matches_python_research_contract(self):
        source = Path(
            "app/src/main/java/com/example/tradedraw/"
            "ExecutionPolicyEvidenceCapture.kt"
        ).read_text(encoding="utf-8")

        match = re.search(
            r"private val REQUIRED_FIELDS = setOf\((.*?)\n\s*\)",
            source,
            re.S,
        )
        self.assertIsNotNone(match, "Kotlin REQUIRED_FIELDS block missing")
        kotlin_fields = tuple(re.findall(r'"([^"]+)"', match.group(1)))

        self.assertEqual(
            set(POLICY_CONTEXT_REQUIRED),
            set(kotlin_fields),
            "Kotlin producer and Python research consumer disagree on exact fields",
        )
        self.assertEqual(len(POLICY_CONTEXT_REQUIRED), len(kotlin_fields))
        self.assertEqual(27, len(kotlin_fields))

    def test_quality_and_version_tokens_match_python_consumer(self):
        source = Path(
            "app/src/main/java/com/example/tradedraw/"
            "ExecutionPolicyEvidenceCapture.kt"
        ).read_text(encoding="utf-8")

        quality = re.search(r'const val QUALITY = "([^"]+)"', source)
        version = re.search(r"const val VERSION = (\d+)", source)
        self.assertIsNotNone(quality)
        self.assertIsNotNone(version)
        self.assertEqual(EXACT_POLICY_CONTEXT_QUALITY, quality.group(1))
        self.assertEqual(EXACT_POLICY_CONTEXT_VERSION, int(version.group(1)))


if __name__ == "__main__":
    unittest.main()
