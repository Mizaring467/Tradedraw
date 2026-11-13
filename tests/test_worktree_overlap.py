import contextlib
import importlib.util
import io
import json
from pathlib import Path
import sys
import subprocess
import tempfile
import unittest


MODULE_PATH = Path(__file__).resolve().parents[1] / "scripts" / "ci" / "worktree_overlap.py"
SPEC = importlib.util.spec_from_file_location("worktree_overlap", MODULE_PATH)
worktree_overlap = importlib.util.module_from_spec(SPEC)
assert SPEC and SPEC.loader
sys.modules[SPEC.name] = worktree_overlap
SPEC.loader.exec_module(worktree_overlap)


class WorktreeOverlapTest(unittest.TestCase):
    def test_parse_worktree_porcelain(self):
        raw = """worktree /repo/main
HEAD aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
branch refs/heads/main

worktree /repo/agent
HEAD bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb
branch refs/heads/agent/test

worktree /repo/detached
HEAD cccccccccccccccccccccccccccccccccccccccc
detached

"""
        items = worktree_overlap.parse_worktree_porcelain(raw)
        self.assertEqual(3, len(items))
        self.assertEqual("main", items[0].branch)
        self.assertEqual("agent/test", items[1].branch)
        self.assertIsNone(items[2].branch)

    def test_scope_for_modules(self):
        modules = {"app", "core", "strategy"}
        self.assertEqual("strategy", worktree_overlap.scope_for("strategy/src/Foo.kt", modules))
        self.assertEqual("app", worktree_overlap.scope_for("app/src/Main.kt", modules))

    def test_test_files_are_scoped_per_file_for_parallel_agents(self):
        modules = {"app"}
        self.assertEqual(
            "tests/test_alpha.py",
            worktree_overlap.scope_for("tests/test_alpha.py", modules),
        )
        self.assertNotEqual(
            worktree_overlap.scope_for("tests/test_alpha.py", modules),
            worktree_overlap.scope_for("tests/test_beta.py", modules),
        )

    def test_scope_for_shared_roots_uses_two_components(self):
        modules = {"app"}
        self.assertEqual("scripts/ci", worktree_overlap.scope_for("scripts/ci/x.py", modules))
        self.assertEqual("scripts/research", worktree_overlap.scope_for("scripts/research/y.py", modules))
        self.assertEqual("loops/r29c", worktree_overlap.scope_for("loops/r29c/STATE.md", modules))

    def test_global_scope(self):
        modules = set()
        self.assertEqual("GLOBAL", worktree_overlap.scope_for("settings.gradle.kts", modules))
        self.assertEqual("GLOBAL", worktree_overlap.scope_for("gradle/libs.versions.toml", modules))

    def test_normalize_claims_accepts_module_scope_and_path(self):
        modules = {"app", "research"}
        claims = worktree_overlap.normalize_claims(
            ["research", "loops/r29c/STATE.md", "scripts/ci"],
            modules,
        )
        self.assertEqual({"research", "loops/r29c/STATE.md", "scripts/ci"}, claims)

    def test_claim_conflict_matches_other_scope(self):
        modules = {"app", "research"}
        conflicts = worktree_overlap.claimed_conflicts_for(
            {"research", "scripts/ci"},
            {"research/src/Test.kt", "app/src/Main.kt"},
            modules,
        )
        self.assertEqual({"research"}, conflicts)

    def test_global_claim_conflicts_with_any_other_change(self):
        modules = {"app"}
        conflicts = worktree_overlap.claimed_conflicts_for(
            {"GLOBAL"},
            {"app/src/Main.kt"},
            modules,
        )
        self.assertEqual({"GLOBAL"}, conflicts)

    def test_claim_mode_ignores_unrelated_existing_overlap_for_exit(self):
        result = worktree_overlap.OverlapResult(
            current="/current",
            other="/other",
            other_branch="agent",
            exact_paths=("research/Foo.kt",),
            shared_scopes=("research",),
            claimed_conflicts=(),
            current_changes=("research/Foo.kt",),
            other_changes=("research/Foo.kt",),
        )
        self.assertEqual(
            0,
            worktree_overlap.exit_code(
                [result],
                strict_scope=False,
                claims_present=True,
            ),
        )

    def test_claim_mode_fails_on_claim_conflict(self):
        result = worktree_overlap.OverlapResult(
            current="/current",
            other="/other",
            other_branch="agent",
            exact_paths=(),
            shared_scopes=(),
            claimed_conflicts=("scripts/ci",),
            current_changes=(),
            other_changes=("scripts/ci/x.py",),
        )
        self.assertEqual(
            4,
            worktree_overlap.exit_code(
                [result],
                strict_scope=False,
                claims_present=True,
            ),
        )

    def test_claim_human_output_suppresses_unrelated_history(self):
        result = worktree_overlap.OverlapResult(
            current="/current",
            other="/other",
            other_branch="agent",
            exact_paths=("research/Foo.kt",),
            shared_scopes=("research",),
            claimed_conflicts=(),
            current_changes=("research/Foo.kt",),
            other_changes=("research/Foo.kt",),
        )
        out = io.StringIO()
        with contextlib.redirect_stdout(out):
            worktree_overlap.print_human([result], claims_present=True)
        rendered = out.getvalue()
        self.assertIn("Claim clear across 1 other worktree(s).", rendered)
        self.assertNotIn("research/Foo.kt", rendered)
        self.assertNotIn("DIRECT FILE COLLISIONS", rendered)

    def test_claim_human_output_only_shows_claim_conflicts(self):
        safe = worktree_overlap.OverlapResult(
            current="/current",
            other="/safe",
            other_branch="safe-agent",
            exact_paths=("research/Foo.kt",),
            shared_scopes=("research",),
            claimed_conflicts=(),
            current_changes=(),
            other_changes=("research/Foo.kt",),
        )
        conflict = worktree_overlap.OverlapResult(
            current="/current",
            other="/busy",
            other_branch="busy-agent",
            exact_paths=(),
            shared_scopes=(),
            claimed_conflicts=("scripts/ci",),
            current_changes=(),
            other_changes=("scripts/ci/tool.py",),
        )
        out = io.StringIO()
        with contextlib.redirect_stdout(out):
            worktree_overlap.print_human(
                [safe, conflict],
                claims_present=True,
            )
        rendered = out.getvalue()
        self.assertIn("/busy [busy-agent]", rendered)
        self.assertIn("CLAIM CONFLICTS: scripts/ci", rendered)
        self.assertNotIn("/safe", rendered)
        self.assertNotIn("research/Foo.kt", rendered)

    def test_global_scope_is_project_wide_risk(self):
        modules = {"app"}
        current = {"settings.gradle.kts"}
        other = {"app/src/Main.kt"}
        current_scopes = worktree_overlap.scopes(current, modules)
        other_scopes = worktree_overlap.scopes(other, modules)
        shared = current_scopes & other_scopes
        if (("GLOBAL" in current_scopes and other) or
                ("GLOBAL" in other_scopes and current)):
            shared.add("GLOBAL")
        self.assertIn("GLOBAL", shared)


class WorktreeClaimIntegrationTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        root = Path(self.temp.name)
        self.repo = root / "main"
        self.repo.mkdir()
        self.run_git(self.repo, "init", "-q")
        self.run_git(self.repo, "config", "user.email", "test@example.invalid")
        self.run_git(self.repo, "config", "user.name", "Overlap test")
        for name in ("settings.gradle.kts", "app/build.gradle.kts",
                     "app/src/A.kt", "app/src/B.kt", "scripts/ci/a.py",
                     "scripts/ci/b.py", "loops/r29c/STATE.md"):
            self.write(self.repo, name)
        self.run_git(self.repo, "add", ".")
        self.run_git(self.repo, "commit", "-qm", "fixture")
        self.other = root / "other"
        self.run_git(self.repo, "worktree", "add", "-qb", "agent", str(self.other))

    def run_git(self, repo, *args):
        return subprocess.run(["git", *args], cwd=repo, check=True,
                              capture_output=True, text=True)

    def write(self, repo, name):
        path = repo / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text("fixture change\n")

    def cli(self, *args):
        return subprocess.run([sys.executable, str(MODULE_PATH), "--repo",
                               str(self.repo), *args], capture_output=True, text=True)

    def assert_claim(self, claim, expected):
        proc = self.cli("--claim", claim, "--json")
        self.assertEqual(expected, proc.returncode, proc.stderr + proc.stdout)
        result = json.loads(proc.stdout)[0]
        self.assertEqual(bool(expected), bool(result["claimed_conflicts"]))
        return result

    def test_siblings_exact_directory_module_and_untracked(self):
        (self.other / "app/src/A.kt").write_text("modified\n")
        self.assert_claim("app/src/B.kt", 0)
        self.assert_claim("app/src/A.kt", 4)
        self.assert_claim("app/src", 4)
        self.assert_claim("app", 4)
        self.assert_claim("app/src/A", 0)
        self.write(self.other, "scripts/ci/new file é.py")
        self.assert_claim("scripts/ci/new file é.py", 4)
        self.assert_claim("scripts/ci/other.py", 0)
        self.assert_claim("scripts/ci", 4)
        self.assert_claim("scripts", 4)
        self.assert_claim("scripts/c", 0)

    def test_normalization_and_invalid_claims(self):
        (self.other / "app/src/A.kt").write_text("modified\n")
        for claim in ("./app//src/./A.kt", "app\\src\\A.kt",
                      str(self.repo / "app/src/A.kt")):
            self.assert_claim(claim, 4)
        for claim in ("", " ", "../app", "app/../app/src/A.kt",
                      "/outside/app/src/A.kt", "C:\\app\\src\\A.kt"):
            with self.subTest(claim=claim):
                proc = self.cli("--claim", claim)
                self.assertEqual(1, proc.returncode, proc.stdout + proc.stderr)
                self.assertIn("Invalid claim:", proc.stderr)
        (self.repo / "alias").symlink_to(self.other, target_is_directory=True)
        proc = self.cli("--claim", "alias/app/src/A.kt")
        self.assertEqual(1, proc.returncode)

    def test_renames_and_unusual_names_keep_both_paths(self):
        self.assert_claim("GLOBAL", 0)
        self.run_git(self.other, "mv", "app/src/A.kt", "app/src/Renamed.kt")
        self.assert_claim("app/src/A.kt", 4)
        self.assert_claim("app/src/Renamed.kt", 4)
        self.run_git(self.other, "commit", "-qm", "rename")
        self.assert_claim("app/src/A.kt", 4)
        for name in ("scripts/ci/line\nbreak.py", "scripts/ci/trailing.py "):
            self.write(self.other, name)
            self.assert_claim(name, 4)
            self.run_git(self.other, "add", name)
            self.run_git(self.other, "commit", "-qm", "unusual filename")
            self.assert_claim(name, 4)

    def test_global_in_both_directions(self):
        self.write(self.other, "new.txt")
        for claim in ("GLOBAL", ".", "gradle", "gradle/wrapper/new",
                      "settings.gradle.kts"):
            self.assert_claim(claim, 4)
        (self.other / "settings.gradle.kts").write_text("modified\n")
        self.assert_claim("unrelated/new.py", 4)

    def test_strict_scope_and_exit_codes_stay_compatible(self):
        (self.repo / "app/src/B.kt").write_text("current\n")
        (self.other / "app/src/A.kt").write_text("other\n")
        self.assertEqual(0, self.cli().returncode)
        self.assertEqual(3, self.cli("--strict-scope").returncode)
        # Prospective claims still ignore unrelated historical logical overlap.
        self.assertEqual(0, self.cli("--strict-scope", "--claim", "scripts/ci").returncode)
        (self.other / "app/src/B.kt").write_text("other\n")
        self.assertEqual(2, self.cli().returncode)
        self.assertEqual(2, self.cli("--strict-scope").returncode)
        self.assert_claim("app/src/B.kt", 4)

    def test_committed_staged_deleted_and_parent_replacement(self):
        (self.other / "app/src/A.kt").write_text("committed\n")
        self.run_git(self.other, "add", ".")
        self.run_git(self.other, "commit", "-qm", "agent edit")
        self.assert_claim("app/src/A.kt", 4)
        self.assert_claim("app/src/B.kt", 0)
        (self.other / "scripts/ci/a.py").unlink()
        self.run_git(self.other, "add", "scripts/ci/a.py")
        self.assert_claim("scripts/ci/a.py", 4)
        self.assert_claim("scripts/ci/a.py/child", 4)
        self.write(self.other, "loops/r29c/extra.md")
        self.assert_claim("loops/r29c/STATE.md", 0)
        self.assert_claim("loops/r29c", 4)


if __name__ == "__main__":
    unittest.main()
