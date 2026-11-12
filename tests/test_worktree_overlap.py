import contextlib
import importlib.util
import io
from pathlib import Path
import sys
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
        self.assertEqual({"research", "loops/r29c", "scripts/ci"}, claims)

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


if __name__ == "__main__":
    unittest.main()
