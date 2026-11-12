from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

from scripts.research.instrumented_artifact_manifest import (
    BASE_VERSION,
    DEFAULT_VERSION,
    build_config_sha,
    create_manifest,
)


class InstrumentedArtifactManifestTest(unittest.TestCase):
    def test_extracts_embedded_build_sha(self):
        with tempfile.TemporaryDirectory() as td:
            path = Path(td) / "BuildConfig.java"
            path.write_text('public static final String GIT_SHA = "abcdef1";\n')
            self.assertEqual("abcdef1", build_config_sha(path))

    def test_manifest_contains_runtime_identity_and_apk_hash(self):
        with tempfile.TemporaryDirectory() as td:
            repo = Path(td)
            apk = repo / "app.apk"
            apk.write_bytes(b"apk-bytes")
            config = repo / "BuildConfig.java"
            config.write_text('public static final String GIT_SHA = "abc1234";\n')

            with patch(
                "scripts.research.instrumented_artifact_manifest.git_head",
                return_value="abc1234",
            ):
                manifest = create_manifest(repo, apk, config)

            self.assertEqual("abc1234", manifest["git_sha"])
            self.assertEqual("abc1234", manifest["runtime_probe_expected_git_sha"])
            self.assertEqual(DEFAULT_VERSION, manifest["strategy_version"])
            self.assertEqual(BASE_VERSION, manifest["base_strategy_version"])
            self.assertEqual("SHADOW", manifest["allowed_runtime_mode"])
            self.assertFalse(manifest["real_money_authorized"])
            self.assertEqual(64, len(manifest["apk_sha256"]))

    def test_rejects_stale_apk_identity(self):
        with tempfile.TemporaryDirectory() as td:
            repo = Path(td)
            apk = repo / "app.apk"
            apk.write_bytes(b"apk")
            config = repo / "BuildConfig.java"
            config.write_text('public static final String GIT_SHA = "abc0001";\n')

            with patch(
                "scripts.research.instrumented_artifact_manifest.git_head",
                return_value="def0002",
            ):
                with self.assertRaisesRegex(ValueError, "BUILD_SHA_MISMATCH"):
                    create_manifest(repo, apk, config)


if __name__ == "__main__":
    unittest.main()
