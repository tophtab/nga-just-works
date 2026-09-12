#!/usr/bin/env python3
"""Run the workflow's Bash against local Git, APK metadata, and GitHub fixtures.

Requires the workflow's Bash/Git tools and jq; no Android build or network access.
"""

import hashlib
import json
import os
import re
import shutil
import subprocess
import sys
import tempfile
import textwrap
import unittest
from pathlib import Path
from types import SimpleNamespace


REPOSITORY_ROOT = Path(__file__).resolve().parent.parent
WORKFLOW = (REPOSITORY_ROOT / ".github/workflows/build.yml").read_text(encoding="utf-8")
REF_KEY = "Derive publication ref key"
IDENTITY = "Derive release identity"
STAGING = "Verify and stage APK"
PUBLICATION = "Create GitHub Release"
CLEANUP = "Remove older channel prereleases"


def workflow_step(name: str) -> str:
    # Read literal step blocks without introducing a YAML dependency. actionlint
    # separately checks the complete workflow and its GitHub expressions.
    lines = WORKFLOW.splitlines(keepends=True)
    for start, line in enumerate(lines):
        if line.rstrip() == "      - name: " + name:
            end = start + 1
            # A following step OR job ends this step; do not execute job YAML
            # as Bash when the current step is the last one in a pre-job.
            while end < len(lines):
                if lines[end].strip() and not lines[end].startswith("        "):
                    break
                end += 1
            return "".join(lines[start + 1:end])
    raise AssertionError(f"Missing workflow step: {name}")


class ExpressionString(str):
    """The workflow's GitHub string comparisons are case-insensitive."""

    def __eq__(self, other: object) -> bool:
        if not isinstance(other, str):
            return NotImplemented
        return self.casefold() == other.casefold()

    def __ne__(self, other: object) -> bool:
        if not isinstance(other, str):
            return NotImplemented
        return self.casefold() != other.casefold()


GH_FIXTURE = r'''
import json
import os
import subprocess
import sys
from pathlib import Path

state_path = Path(os.environ["WORKFLOW_FIXTURE_STATE"])
state = json.loads(state_path.read_text())
args = sys.argv[1:]
with Path(os.environ["WORKFLOW_FIXTURE_CALLS"]).open("a") as log:
    log.write(json.dumps(args) + "\n")

payloads = []
if args[0] == "api":
    route = next(arg for arg in args if arg.startswith("repos/"))
    if "--method" in args:
        assert args[args.index("--method") + 1] == "PATCH"
        action = "patch"
    elif "/git/matching-refs/tags/" in route:
        action = "tags"
        payloads = [state["refs"]]
    elif route.endswith("/releases"):
        action = "releases"
        size = state.get("page_size", 100)
        pages = [state["releases"][i:i + size]
                 for i in range(0, len(state["releases"]), size)] or [[]]
        payloads = pages if "--paginate" in args else pages[:1]
    else:
        action = "detail"
        payloads = [next(release for release in state["releases"]
                         if str(release["id"]) == route.rsplit("/", 1)[1])]
else:
    assert args[0] == "release" and args[1] in {"create", "upload", "delete"}
    action = args[1]

if action == state.get("fail") or (
    action == "delete" and args[2] == state.get("fail_delete_tag")
):
    print(state.get("fail_output", ""), end="")
    print("Fixture failure: " + action, file=sys.stderr)
    sys.exit(42)

if action == "create":
    tag = args[2]
    assert not any(release["tag_name"] == tag for release in state["releases"])
    state["releases"].append({
        "id": max((release["id"] for release in state["releases"]), default=0) + 1,
        "tag_name": tag,
        "prerelease": "--prerelease" in args,
    })
    if not any(ref["ref"] == "refs/tags/" + tag for ref in state["refs"]):
        state["refs"].append({"ref": "refs/tags/" + tag,
                              "object": {"sha": os.environ["GITHUB_SHA"]}})
elif action == "delete":
    state["releases"] = [r for r in state["releases"] if r["tag_name"] != args[2]]
    if "--cleanup-tag" in args:
        state["refs"] = [r for r in state["refs"] if r["ref"] != "refs/tags/" + args[2]]
state_path.write_text(json.dumps(state))

if "--jq" in args:
    # Execute the actual workflow filter, including exact tag and prerelease
    # selection, instead of teaching this fixture the expected filtered answer.
    result = subprocess.run(
        ["jq", "-r", args[args.index("--jq") + 1]],
        input="\n".join(json.dumps(payload) for payload in payloads),
        text=True,
    )
    sys.exit(result.returncode)
for payload in payloads:
    print(json.dumps(payload))
'''


class ReleaseWorkflowTest(unittest.TestCase):
    def setUp(self) -> None:
        temporary_directory = tempfile.TemporaryDirectory()
        self.addCleanup(temporary_directory.cleanup)
        self.root = Path(temporary_directory.name)
        self.bin = self.root / "bin"
        self.bin.mkdir()
        self.state_path = self.root / "github.json"
        self.calls_path = self.root / "github-calls.jsonl"
        self.manifest_path = self.root / "manifest.json"
        self.env = {
            **os.environ,
            "PATH": str(self.bin) + os.pathsep + os.environ["PATH"],
            "GIT_CONFIG_GLOBAL": os.devnull,
            "GIT_CONFIG_NOSYSTEM": "1",
            "GIT_AUTHOR_NAME": "Workflow Test",
            "GIT_AUTHOR_EMAIL": "workflow@example.invalid",
            "GIT_COMMITTER_NAME": "Workflow Test",
            "GIT_COMMITTER_EMAIL": "workflow@example.invalid",
            "GITHUB_EVENT_NAME": "push",
            "GITHUB_REPOSITORY": "offline/release-tests",
            "GITHUB_RUN_NUMBER": "51",
            "GITHUB_ENV": str(self.root / "github-env"),
            "GITHUB_OUTPUT": str(self.root / "github-output"),
            "GH_TOKEN": "offline-fixture",
            "ANDROID_HOME": str(self.root / "sdk"),
            "WORKFLOW_FIXTURE_STATE": str(self.state_path),
            "WORKFLOW_FIXTURE_CALLS": str(self.calls_path),
            "WORKFLOW_FIXTURE_MANIFEST": str(self.manifest_path),
        }
        self.outputs: dict[str, str] = {}
        self.executable(self.bin / "gh", GH_FIXTURE)
        self.executable(self.bin / "apkanalyzer", '''
import json
import os
import sys
from pathlib import Path
assert sys.argv[1] == "manifest"
print(json.loads(Path(os.environ["WORKFLOW_FIXTURE_MANIFEST"]).read_text())[sys.argv[2]])
''')
        # Synthetic APK bytes are never assembled or signed; only metadata and
        # signer responses are replaced while staging/checksums run unchanged.
        self.executable(self.root / "sdk/build-tools/35.0.0/apksigner", '''
import os
import sys
assert sys.argv[1:4] == ["verify", "--verbose", "--print-certs"]
sys.exit(int(os.environ.get("WORKFLOW_FIXTURE_SIGNER_EXIT", "0")))
''')
        (self.root / "scripts").symlink_to(REPOSITORY_ROOT / "scripts", target_is_directory=True)
        (self.root / "release-notes").mkdir()
        shutil.copyfile(
            REPOSITORY_ROOT / "release-notes/5.6.1.md",
            self.root / "release-notes/5.6.1.md",
        )
        self.command("git", "init", "--quiet", "--initial-branch=main", "--template=")
        self.command("git", "commit", "--quiet", "--allow-empty", "-m", "stable")
        self.command("git", "tag", "5.6.1")
        self.command("git", "commit", "--quiet", "--allow-empty", "-m", "preview")
        self.sha = self.command("git", "rev-parse", "HEAD").stdout.strip()
        self.env["GITHUB_SHA"] = self.sha
        self.seed_releases([])

    def executable(self, path: Path, source: str) -> None:
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(f"#!{sys.executable}\n" + source, encoding="utf-8")
        path.chmod(0o755)

    def command(self, *args: str) -> subprocess.CompletedProcess[str]:
        return subprocess.run(
            args, cwd=self.root, env=self.env, capture_output=True, text=True, check=True,
        )

    def run_step(self, name: str, cwd: Path | None = None) -> subprocess.CompletedProcess[str]:
        block = workflow_step(name)
        script = textwrap.dedent(block.split("        run: |\n", 1)[1])
        self.assertNotIn("${{", script, "Pass dynamic workflow values through env")
        step_env = {
            key: self.outputs[output]
            for key, output in re.findall(
                r"^          (\w+): \$\{\{ steps\.release\.outputs\.(\w+) \}\}$",
                block, flags=re.MULTILINE,
            )
        }
        return subprocess.run(
            ["bash", "--noprofile", "--norc", "-e", "-o", "pipefail", "-c", script],
            cwd=cwd or self.root, env={**self.env, **step_env}, capture_output=True, text=True,
            check=False,
        )

    def assert_success(self, result: subprocess.CompletedProcess[str] | None) -> None:
        self.assertIsNotNone(result)
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)

    @staticmethod
    def branch_prefix(branch: str, slug: str) -> str:
        digest = hashlib.sha256(branch.encode("utf-8")).hexdigest()[:12]
        return f"branch-{slug}-{digest}-"

    def derive(self, ref: str) -> subprocess.CompletedProcess[str]:
        self.env["GITHUB_REF"] = ref
        self.env["GITHUB_REF_NAME"] = ref.removeprefix("refs/heads/").removeprefix("refs/tags/")
        for key in ("GITHUB_ENV", "GITHUB_OUTPUT"):
            Path(self.env[key]).write_text("")
        result = self.run_step(IDENTITY)
        self.outputs = self.read_values("GITHUB_OUTPUT")
        self.env.update(self.read_values("GITHUB_ENV"))
        return result

    def read_values(self, name: str) -> dict[str, str]:
        return dict(line.split("=", 1) for line in Path(self.env[name]).read_text().splitlines())

    def stage(self, **manifest_overrides: str) -> subprocess.CompletedProcess[str]:
        shutil.rmtree(self.root / "dist", ignore_errors=True)
        source = self.root / "nga_phone_base_3.0/build/outputs/apk" / self.outputs["apk_dir"]
        source.mkdir(parents=True, exist_ok=True)
        (source / "app.apk").write_bytes(b"synthetic APK fixture\n")
        self.manifest_path.write_text(json.dumps({
            "application-id": "com.github.tophtab.ngajustworks",
            "version-name": self.env["CI_VERSION_NAME"],
            "version-code": self.env["CI_VERSION_CODE"],
            "min-sdk": "29",
            "target-sdk": "35",
            "debuggable": self.outputs["expected_debuggable"],
            **manifest_overrides,
        }))
        return self.run_step(STAGING)

    def seed_releases(self, releases: list[tuple[str, bool]], **options: object) -> None:
        self.state_path.write_text(json.dumps({
            "releases": [
                {"id": index, "tag_name": tag, "prerelease": prerelease}
                for index, (tag, prerelease) in enumerate(releases, start=1)
            ],
            "refs": [
                {"ref": "refs/tags/" + tag, "object": {"sha": self.sha}}
                for tag, _ in releases
            ],
            **options,
        }))
        self.calls_path.write_text("")

    def state(self) -> dict:
        return json.loads(self.state_path.read_text())

    def calls(self) -> list[list[str]]:
        return [json.loads(line) for line in self.calls_path.read_text().splitlines()]

    def evaluate(self, expression: str, outcome: str = "success") -> bool:
        # These workflow conditions use only comparisons, boolean operators and
        # startsWith. Model GitHub's case-insensitive comparisons, including
        # comparisons that used to treat Main/MAIN as the main cache owner.
        expression = expression.removeprefix("${{").removesuffix("}}").strip()
        return bool(eval(
            expression.replace("&&", " and ").replace("||", " or "),
            {
                "__builtins__": {},
                "startsWith": lambda value, prefix: value.casefold().startswith(prefix.casefold()),
            },
            {
                "github": SimpleNamespace(
                    ref=ExpressionString(self.env["GITHUB_REF"]),
                    event_name=ExpressionString(self.env["GITHUB_EVENT_NAME"]),
                ),
                "steps": SimpleNamespace(
                    publish=SimpleNamespace(outcome=ExpressionString(outcome)),
                    release=SimpleNamespace(outputs=SimpleNamespace(**{
                        key: ExpressionString(value) for key, value in self.outputs.items()
                    })),
                ),
            },
        ))

    def publish(self) -> tuple[subprocess.CompletedProcess[str], subprocess.CompletedProcess[str] | None]:
        result = self.run_step(PUBLICATION)
        condition = re.search(r"^        if: (.+)$", workflow_step(CLEANUP), re.MULTILINE)
        self.assertIsNotNone(condition)
        cleanup = None
        if self.evaluate(condition[1], "success" if result.returncode == 0 else "failure"):
            cleanup = self.run_step(CLEANUP)
        return result, cleanup

    def test_channel_identities_and_staged_assets(self) -> None:
        cases = (
            ("refs/heads/main", f"debug-{self.sha[:12]}", "5.6.1-debug.51", "50601002",
             "NGA Just Works 5.6.1-debug.51 (Debug)", "", "preview"),
            ("refs/heads/feature/ai-summary",
             self.branch_prefix("feature/ai-summary", "feature-ai-summary") + self.sha[:12],
             "5.6.1-debug.51", "50601002", "NGA Just Works 5.6.1-debug.51 (Debug, feature/ai-summary)",
             "-feature-ai-summary", "preview"),
            ("refs/heads/feature/thread-detail-compat-mode",
             self.branch_prefix("feature/thread-detail-compat-mode", "feature-thread-detail-compat-mode") + self.sha[:12],
             "5.6.1-debug.51", "50601002",
             "NGA Just Works 5.6.1-debug.51 (Debug, feature/thread-detail-compat-mode)",
             "-feature-thread-detail-compat-mode", "preview"),
            ("refs/heads/feature/thread-ip-location-loading-tips",
             self.branch_prefix("feature/thread-ip-location-loading-tips", "feature-thread-ip-location-loading-tips") + self.sha[:12],
             "5.6.1-debug.51", "50601002",
             "NGA Just Works 5.6.1-debug.51 (Debug, feature/thread-ip-location-loading-tips)",
             "-feature-thread-ip-location-loading-tips", "preview"),
            ("refs/heads/review/team/topic.v2_1",
             self.branch_prefix("review/team/topic.v2_1", "review-team-topic.v2_1") + self.sha[:12],
             "5.6.1-debug.51", "50601002", "NGA Just Works 5.6.1-debug.51 (Debug, review/team/topic.v2_1)",
             "-review-team-topic.v2_1", "preview"),
            ("refs/tags/5.6.1", "5.6.1", "5.6.1", "50601000", "NGA Just Works 5.6.1", "", "release"),
        )
        for ref, tag, version, code, title, suffix, variant in cases:
            with self.subTest(ref=ref):
                self.assert_success(self.derive(ref))
                self.assertEqual((version, code), (self.env["CI_VERSION_NAME"], self.env["CI_VERSION_CODE"]))
                self.assertEqual((tag, title), (self.outputs["tag"], self.outputs["title"]))
                self.assertEqual(variant, self.outputs["apk_dir"])
                preview = "true" if variant == "preview" else "false"
                self.assertEqual(preview, self.outputs["prerelease"])
                self.assertEqual(preview, self.outputs["expected_debuggable"])
                tasks = ":nga_phone_base_3.0:assemblePreview" if variant == "preview" else (
                    "verifyReleaseTag :nga_phone_base_3.0:assembleRelease"
                )
                self.assertEqual(tasks, self.outputs["gradle_tasks"])
                self.assert_success(self.stage())
                filename = f"NGA-Just-Works-{version}{suffix}.apk"
                digest = hashlib.sha256((self.root / "dist" / filename).read_bytes()).hexdigest()
                staged = {filename, filename + ".sha256"}
                if variant == "release":
                    staged |= {"NGA-Just-Works.apk", "NGA-Just-Works.apk.sha256"}
                self.assertEqual(staged, {p.name for p in (self.root / "dist").iterdir()})
                self.assertEqual(f"{digest}  {filename}\n", (self.root / "dist" / (filename + ".sha256")).read_text())
                if variant == "release":
                    self.assertEqual(
                        f"{digest}  NGA-Just-Works.apk\n",
                        (self.root / "dist" / "NGA-Just-Works.apk.sha256").read_text(),
                    )

    def test_only_reachable_stable_tags_set_the_preview_base(self) -> None:
        self.command("git", "commit", "--quiet", "--allow-empty", "-m", "future")
        self.command("git", "tag", "9.9.9")
        self.assert_success(self.derive("refs/heads/feature/ai-summary"))
        self.assertEqual("5.6.1-debug.51", self.env["CI_VERSION_NAME"])
        self.command("git", "tag", "5.6.2", self.sha)
        self.assert_success(self.derive("refs/heads/feature/ai-summary"))
        self.assertEqual("5.6.2-debug.51", self.env["CI_VERSION_NAME"])
        self.assertEqual("50602001", self.env["CI_VERSION_CODE"])

    def test_invalid_tags_and_non_push_or_non_publication_refs_fail_identity(self) -> None:
        for event, ref in (
            ("push", "refs/tags/v5.6.1"), ("push", "refs/tags/5.6.1-rc.1"),
            ("push", "refs/tags/5.100.0"), ("push", "refs/pull/10/merge"),
            ("workflow_dispatch", "refs/heads/main"),
            ("pull_request", "refs/heads/feature/other"),
            ("workflow_dispatch", "refs/tags/5.6.1"),
        ):
            with self.subTest(event=event, ref=ref):
                self.env["GITHUB_EVENT_NAME"] = event
                self.assertNotEqual(0, self.derive(ref).returncode)
                self.assertEqual({}, self.outputs)
                self.assertEqual({}, self.read_values("GITHUB_ENV"))

    def test_missing_stable_base_fails_identity(self) -> None:
        self.command("git", "tag", "-d", "5.6.1")
        result = self.derive("refs/heads/feature/ai-summary")
        self.assertNotEqual(0, result.returncode)
        self.assertIn("No stable X.Y.Z tag is reachable", result.stderr)

    def test_arbitrary_legal_branch_names_stay_literal_and_make_bounded_assets(self) -> None:
        cases = (
            ("feature/$(touch${IFS}substituted)", "feature---touch--IFS-substituted-"),
            ("feature/`touch${IFS}backticks`", "feature--touch--IFS-backticks-"),
            ('feature/";touch${IFS}quoted;#', "feature---touch--IFS-quoted--"),
            ("feature/Mixed_CASE.v2--ready", "feature-Mixed_CASE.v2--ready"),
            ("实验/分支", "branch"),
            ("feature/" + "long-name/" * 40 + "end", ("feature-" + "long-name-" * 8)[:80]),
        )
        for branch, slug in cases:
            with self.subTest(branch=branch):
                self.command("git", "check-ref-format", "--branch", branch)
                self.assert_success(self.derive("refs/heads/" + branch))
                self.assertEqual("-" + slug, self.outputs["asset_suffix"])
                self.assertEqual(self.branch_prefix(branch, slug), self.outputs["preview_tag_prefix"])
                self.assertEqual("", self.outputs["legacy_preview_tag_prefix"])
                self.assertEqual(
                    f"NGA Just Works 5.6.1-debug.51 (Debug, {branch})", self.outputs["title"],
                )
                self.command("git", "check-ref-format", "refs/tags/" + self.outputs["tag"])
                self.assert_success(self.stage())
                filenames = {p.name for p in (self.root / "dist").iterdir()}
                expected = f"NGA-Just-Works-5.6.1-debug.51-{slug}.apk"
                self.assertEqual({expected, expected + ".sha256"}, filenames)
                for filename in filenames:
                    self.assertLess(len(filename.encode("ascii")), 255)
                    self.assertRegex(filename, r"^[A-Za-z0-9._-]+$")
                self.seed_releases([])
                publication, cleanup = self.publish()
                self.assert_success(publication)
                self.assert_success(cleanup)
                publication, cleanup = self.publish()
                self.assert_success(publication)
                self.assert_success(cleanup)
                for sentinel in ("substituted", "backticks", "quoted"):
                    self.assertFalse((self.root / sentinel).exists())
                create = next(call for call in self.calls() if call[:2] == ["release", "create"])
                patch = next(call for call in self.calls() if "PATCH" in call)
                self.assertEqual(self.outputs["title"], create[create.index("--title") + 1])
                self.assertIn("name=" + self.outputs["title"], patch)

    def test_same_sha_and_colliding_slugs_keep_independent_releases(self) -> None:
        long_prefix = "feature/" + "long-name/" * 40
        for branches in (
            ("feature/a-b", "feature/a/b"),
            ("feature/ai-summary", "feature-ai-summary"),
            (long_prefix + "first", long_prefix + "second"),
            ("实验/一", "实验/二"),
        ):
            with self.subTest(branches=branches):
                self.seed_releases([])
                identities = []
                for branch in branches:
                    self.command("git", "check-ref-format", "--branch", branch)
                    self.assert_success(self.derive("refs/heads/" + branch))
                    identities.append(dict(self.outputs))
                    self.assert_success(self.stage())
                    publication, cleanup = self.publish()
                    self.assert_success(publication)
                    self.assert_success(cleanup)
                self.assertEqual(identities[0]["asset_suffix"], identities[1]["asset_suffix"])
                self.assertNotEqual(identities[0]["preview_tag_prefix"], identities[1]["preview_tag_prefix"])
                self.assertNotEqual(identities[0]["tag"], identities[1]["tag"])
                self.assertEqual(
                    {identity["tag"] for identity in identities},
                    {release["tag_name"] for release in self.state()["releases"]},
                )
                self.assertFalse(any(call[:2] == ["release", "delete"] for call in self.calls()))

    def test_branch_channel_remains_the_same_across_commits(self) -> None:
        branch = "review/team/topic"
        self.assert_success(self.derive("refs/heads/" + branch))
        previous = dict(self.outputs)
        self.assert_success(self.stage())
        self.assert_success(self.publish()[0])
        self.command("git", "commit", "--quiet", "--allow-empty", "-m", "next preview")
        self.env["GITHUB_SHA"] = self.command("git", "rev-parse", "HEAD").stdout.strip()
        self.env["GITHUB_RUN_NUMBER"] = "52"
        self.assert_success(self.derive("refs/heads/" + branch))
        self.assertEqual(previous["preview_tag_prefix"], self.outputs["preview_tag_prefix"])
        self.assertNotEqual(previous["tag"], self.outputs["tag"])
        self.assertEqual("5.6.1-debug.52", self.env["CI_VERSION_NAME"])
        self.assertEqual("50601003", self.env["CI_VERSION_CODE"])
        self.assert_success(self.stage())
        publication, cleanup = self.publish()
        self.assert_success(publication)
        self.assert_success(cleanup)
        self.assertEqual({self.outputs["tag"]}, {r["tag_name"] for r in self.state()["releases"]})
        self.assertEqual({"refs/tags/" + self.outputs["tag"]}, {r["ref"] for r in self.state()["refs"]})

    def test_publication_and_cleanup_preserve_other_channels(self) -> None:
        old_main = "debug-111111111111"
        old_legacy = "preview-222222222222"
        old_feature = "branch-feature-ai-summary-333333333333"
        ai_prefix = self.branch_prefix("feature/ai-summary", "feature-ai-summary")
        compat_prefix = self.branch_prefix("feature/thread-detail-compat-mode", "feature-thread-detail-compat-mode")
        nested_prefix = self.branch_prefix("review/team/topic", "review-team-topic")
        collision_prefix = self.branch_prefix("feature-ai-summary", "feature-ai-summary")
        old_ai = ai_prefix + "444444444444"
        old_compat = compat_prefix + "555555555555"
        old_nested = nested_prefix + "666666666666"
        old_collision = collision_prefix + "777777777777"
        releases = [
            (old_main, True), (old_legacy, True), (old_feature, True),
            (old_ai, True), (old_compat, True), (old_nested, True), (old_collision, True),
            ("branch-feature-other-444444444444", True),
            ("branch-feature-ai-summary-next-555555555555", True),
            ("branch-feature-ai-summary-deadbeefdead-666666666666", True),
            ("branch-feature-ai-summary-77777777777", True),
            ("branch-feature-ai-summary-7777777777777", True),
            ("branch-feature-ai-summary-zzzzzzzzzzzz", True),
            ("branch-feature-ai-summary-888888888888", False),
            ("debug-999999999999", False), ("5.6.1", False), ("unrelated-beta", True),
        ]
        for prefix in ("debug-", "preview-", ai_prefix, compat_prefix, nested_prefix, collision_prefix):
            releases.extend((prefix + suffix, True) for suffix in (
                "11111111111", "1111111111111", "ABCDEFABCDEF", "zzzzzzzzzzzz",
                "next-111111111111", "111111111111-222222222222",
            ))
            releases.append((prefix + "aaaaaaaaaaaa", False))
        for ref, removed in (
            ("refs/heads/main", {old_main, old_legacy}),
            ("refs/heads/feature/ai-summary", {old_feature, old_ai}),
            ("refs/heads/feature/thread-detail-compat-mode", {old_compat}),
            ("refs/heads/review/team/topic", {old_nested}),
            ("refs/heads/feature-ai-summary", {old_collision}),
        ):
            with self.subTest(ref=ref):
                self.assert_success(self.derive(ref))
                self.assert_success(self.stage())
                self.seed_releases(releases, page_size=2)
                publication, cleanup = self.publish()
                self.assert_success(publication)
                self.assert_success(cleanup)
                remaining = {r["tag_name"] for r in self.state()["releases"]}
                self.assertEqual(({tag for tag, _ in releases} - removed) | {self.outputs["tag"]}, remaining)
                self.assertEqual({"refs/tags/" + tag for tag in remaining}, {r["ref"] for r in self.state()["refs"]})
                creates = [call for call in self.calls() if call[:2] == ["release", "create"]]
                self.assertEqual(1, len(creates))
                self.assertEqual(self.outputs["tag"], creates[0][2])
                self.assertEqual(self.outputs["title"], creates[0][creates[0].index("--title") + 1])
                self.assertIn("--prerelease", creates[0])
                self.assertIn("--generate-notes", creates[0])
                for call in self.calls():
                    if call[:2] == ["release", "delete"]:
                        self.assertIn("--cleanup-tag", call)
                        self.assertIn("--yes", call)

    def test_only_the_original_ai_branch_migrates_its_legacy_tags(self) -> None:
        legacy = "branch-feature-ai-summary-111111111111"
        for branch in ("feature/ai-summary", "feature-ai-summary", "feature/ai/summary", "feature/ai-summary/next"):
            with self.subTest(branch=branch):
                self.assert_success(self.derive("refs/heads/" + branch))
                self.assert_success(self.stage())
                self.seed_releases([(legacy, True)])
                publication, cleanup = self.publish()
                self.assert_success(publication)
                self.assert_success(cleanup)
                should_migrate = branch == "feature/ai-summary"
                self.assertEqual(
                    "branch-feature-ai-summary-" if should_migrate else "",
                    self.outputs["legacy_preview_tag_prefix"],
                )
                self.assertEqual(
                    not should_migrate, legacy in {r["tag_name"] for r in self.state()["releases"]},
                )

    def test_same_commit_rerun_validates_then_replaces_the_same_assets(self) -> None:
        for ref in ("refs/heads/main", "refs/heads/feature/ai-summary", "refs/heads/review/team/topic"):
            with self.subTest(ref=ref):
                self.seed_releases([])
                self.assert_success(self.derive(ref))
                identity = dict(self.outputs)
                version = (self.env["CI_VERSION_NAME"], self.env["CI_VERSION_CODE"])
                self.assert_success(self.stage())
                self.assert_success(self.publish()[0])
                self.calls_path.write_text("")
                self.assert_success(self.derive(ref))
                self.assertEqual(identity, self.outputs)
                self.assertEqual(version, (self.env["CI_VERSION_NAME"], self.env["CI_VERSION_CODE"]))
                publication, cleanup = self.publish()
                self.assert_success(publication)
                self.assert_success(cleanup)
                calls = self.calls()
                self.assertFalse(any(call[:2] == ["release", "create"] for call in calls))
                patches = [call for call in calls if "PATCH" in call]
                self.assertEqual(1, len(patches))
                self.assertIn("name=" + self.outputs["title"], patches[0])
                uploads = [call for call in calls if call[:2] == ["release", "upload"]]
                self.assertEqual(1, len(uploads))
                self.assertEqual(self.outputs["tag"], uploads[0][2])
                self.assertIn("--clobber", uploads[0])
                self.assertEqual({"dist/" + p.name for p in (self.root / "dist").iterdir()}, set(uploads[0][3:5]))

    def test_wrong_sha_or_non_prerelease_blocks_replacement(self) -> None:
        for ref in ("refs/heads/main", "refs/heads/feature/ai-summary", "refs/heads/review/team/topic"):
            for conflict in ("sha", "stable"):
                with self.subTest(ref=ref, conflict=conflict):
                    self.assert_success(self.derive(ref))
                    self.assert_success(self.stage())
                    self.seed_releases([(self.outputs["tag"], conflict != "stable")])
                    if conflict == "sha":
                        state = self.state()
                        state["refs"][0]["object"]["sha"] = "f" * 40
                        self.state_path.write_text(json.dumps(state))
                    original = self.state()
                    result, cleanup = self.publish()
                    self.assertNotEqual(0, result.returncode)
                    self.assertIsNone(cleanup)
                    self.assertEqual(original, self.state())
                    self.assertFalse(any(call[0] == "release" or "PATCH" in call for call in self.calls()))

    def test_api_and_publication_failures_keep_the_previous_preview(self) -> None:
        for branch in ("feature/ai-summary", "review/team/topic"):
            self.assert_success(self.derive("refs/heads/" + branch))
            self.assert_success(self.stage())
            previous = self.outputs["preview_tag_prefix"] + "111111111111"
            legacy = "branch-feature-ai-summary-222222222222"
            for failure in ("tags", "releases", "detail", "patch", "create", "upload"):
                with self.subTest(branch=branch, failure=failure):
                    releases = [(previous, True), (legacy, True)]
                    if failure in {"detail", "patch", "upload"}:
                        releases.append((self.outputs["tag"], True))
                    # A failed API can emit usable partial JSON before exiting;
                    # pipefail must still stop publication rather than accept it.
                    partial_output = {
                        "tags": json.dumps([{"ref": "refs/tags/" + self.outputs["tag"],
                                             "object": {"sha": self.sha}}]),
                        "releases": json.dumps([{"tag_name": self.outputs["tag"], "id": 3}]),
                        "detail": "true\n",
                    }
                    self.seed_releases(releases, fail=failure, fail_output=partial_output.get(failure, ""))
                    result, cleanup = self.publish()
                    self.assertNotEqual(0, result.returncode)
                    self.assertIsNone(cleanup)
                    remaining = {r["tag_name"] for r in self.state()["releases"]}
                    self.assertTrue({previous, legacy}.issubset(remaining))
                    self.assertFalse(any(call[:2] == ["release", "delete"] for call in self.calls()))

    def test_release_title_is_passed_as_literal_data(self) -> None:
        self.assert_success(self.derive("refs/heads/feature/ai-summary"))
        self.assert_success(self.stage())
        self.outputs["title"] = 'Literal "title" $(touch substituted) `touch backticks`'
        self.assert_success(self.publish()[0])
        self.assert_success(self.publish()[0])
        self.assertFalse((self.root / "substituted").exists())
        self.assertFalse((self.root / "backticks").exists())
        create = next(call for call in self.calls() if call[:2] == ["release", "create"])
        patch = next(call for call in self.calls() if "PATCH" in call)
        self.assertEqual(self.outputs["title"], create[create.index("--title") + 1])
        self.assertIn("name=" + self.outputs["title"], patch)

    def test_stable_publication_uses_validated_notes_and_skips_cleanup(self) -> None:
        self.assert_success(self.derive("refs/tags/5.6.1"))
        self.assert_success(self.stage())
        self.seed_releases([("debug-111111111111", True)])
        result, cleanup = self.publish()
        self.assert_success(result)
        self.assertIsNone(cleanup)
        self.assertEqual(1, len(self.calls()))
        create = self.calls()[0]
        self.assertEqual(["release", "create", "5.6.1"], create[:3])
        self.assertIn("--verify-tag", create)
        self.assertEqual("release-notes/5.6.1.md", create[create.index("--notes-file") + 1])
        self.assertNotIn("--generate-notes", create)
        self.assertNotIn("--prerelease", create)
        (self.root / "release-notes/5.6.1.md").unlink()
        self.calls_path.write_text("")
        result, cleanup = self.publish()
        self.assertNotEqual(0, result.returncode)
        self.assertIsNone(cleanup)
        self.assertEqual([], self.calls())

    def test_staging_rejects_incorrect_manifest_values(self) -> None:
        self.assert_success(self.derive("refs/heads/feature/ai-summary"))
        for field, value in (
            ("application-id", "com.github.tophtab.ngajustworks.debug"),
            ("version-name", "5.6.1-debug.51-feature-ai-summary"),
            ("version-code", "1"), ("debuggable", "false"),
            ("min-sdk", "26"), ("target-sdk", "34"),
        ):
            with self.subTest(field=field):
                self.assertNotEqual(0, self.stage(**{field: value}).returncode)
                self.assertEqual([], list((self.root / "dist").glob("*.sha256")))

    def test_signature_verification_failure_stops_staging(self) -> None:
        self.assert_success(self.derive("refs/heads/review/team/topic"))
        self.env["WORKFLOW_FIXTURE_SIGNER_EXIT"] = "1"
        self.assertNotEqual(0, self.stage().returncode)
        self.assertEqual([], list((self.root / "dist").glob("*.sha256")))

    def test_publication_rejects_missing_extra_or_corrupt_assets(self) -> None:
        self.assert_success(self.derive("refs/heads/feature/ai-summary"))
        for defect in ("missing_apk", "missing_checksum", "extra", "corrupt"):
            with self.subTest(defect=defect):
                self.assert_success(self.stage())
                apk = next((self.root / "dist").glob("*.apk"))
                if defect == "missing_apk":
                    apk.unlink()
                elif defect == "missing_checksum":
                    apk.with_suffix(".apk.sha256").unlink()
                elif defect == "extra":
                    (self.root / "dist/extra.txt").write_text("unexpected")
                else:
                    apk.write_bytes(b"corrupt")
                result, cleanup = self.publish()
                self.assertNotEqual(0, result.returncode)
                self.assertIsNone(cleanup)
                self.assertEqual([], self.calls())

    def test_cleanup_lookup_failure_does_not_delete_partial_results(self) -> None:
        self.assert_success(self.derive("refs/heads/feature/ai-summary"))
        previous = "branch-feature-ai-summary-111111111111"
        self.seed_releases([(previous, True)], fail="releases", fail_output=previous + "\n")
        result = self.run_step(CLEANUP)
        self.assertNotEqual(0, result.returncode)
        self.assertEqual(1, len(self.calls()))
        self.assertEqual(previous, self.state()["releases"][0]["tag_name"])

    def test_cleanup_stops_at_a_failed_deletion(self) -> None:
        self.assert_success(self.derive("refs/heads/review/team/topic"))
        self.assert_success(self.stage())
        old_tags = [self.outputs["preview_tag_prefix"] + str(i) * 12 for i in (1, 2, 3)]
        self.seed_releases([(tag, True) for tag in old_tags], fail_delete_tag=old_tags[1])
        publication, cleanup = self.publish()
        self.assert_success(publication)
        self.assertNotEqual(0, cleanup.returncode)
        self.assertEqual(old_tags[:2], [call[2] for call in self.calls() if call[:2] == ["release", "delete"]])
        self.assertEqual({*old_tags[1:], self.outputs["tag"]}, {r["tag_name"] for r in self.state()["releases"]})

    def test_trigger_keeps_all_branch_pushes_tags_and_documentation_skips(self) -> None:
        trigger = re.search(r"(?ms)^on:\n(.*?)(?=^\S)", WORKFLOW)[1]
        self.assertEqual(["push"], re.findall(r"^  (\w+):$", trigger, re.MULTILINE))
        branches = re.search(r"^    branches: (.+)$", trigger, re.MULTILINE)[1]
        self.assertEqual(["**"], json.loads(branches), "A single * does not match nested branch names")
        tags = re.search(r"^    tags: (.+)$", trigger, re.MULTILINE)[1]
        self.assertEqual(["*.*.*"], json.loads(tags))
        ignored_paths = re.findall(r'^      - "(.+)"$', trigger, re.MULTILINE)
        self.assertEqual([".trellis/**", "**/*.md"], ignored_paths)

    def test_ref_key_hashes_the_exact_full_ref_without_a_checkout(self) -> None:
        refs = (
            "refs/heads/feature/Foo", "refs/heads/feature/foo",
            "refs/heads/main", "refs/heads/Main", "refs/heads/MAIN",
            "refs/heads/5.6.1", "refs/tags/5.6.1",
            "refs/heads/feature/$(touch${IFS}ref_key_substituted)",
            "refs/heads/实验/分支",
        )
        keys = set()
        with tempfile.TemporaryDirectory() as checkout_free:
            for ref in refs:
                with self.subTest(ref=ref):
                    self.command("git", "check-ref-format", ref)
                    self.env["GITHUB_REF"] = ref
                    expected = hashlib.sha256(ref.encode("utf-8")).hexdigest()
                    for attempt in range(2):
                        # A later commit/run of the same branch must use the same
                        # group, while case-only refs and branch/tag refs differ.
                        self.env["GITHUB_SHA"] = self.sha if attempt == 0 else "f" * 40
                        self.env["GITHUB_RUN_NUMBER"] = str(51 + attempt)
                        Path(self.env["GITHUB_OUTPUT"]).write_text("")
                        self.assert_success(self.run_step(REF_KEY, cwd=Path(checkout_free)))
                        values = self.read_values("GITHUB_OUTPUT")
                        self.assertEqual({"ref_key": expected}, values)
                        self.assertRegex(values["ref_key"], r"^[0-9a-f]{64}$")
                    keys.add(values["ref_key"].casefold())
            self.assertEqual([], list(Path(checkout_free).iterdir()))
        self.assertEqual(len(refs), len(keys), "GitHub folds group names to the same case")
        self.assertEqual([], self.calls())

    def test_ref_key_pre_job_gates_build_concurrency_without_permissions_or_secrets(self) -> None:
        workflow_header = WORKFLOW.split("jobs:\n", 1)[0]
        self.assertNotIn("concurrency:", workflow_header)
        ref_job = WORKFLOW.split("  ref-identity:\n", 1)[1].split("  build-and-publish:\n", 1)[0]
        build_header = WORKFLOW.split("  build-and-publish:\n", 1)[1].split("    steps:\n", 1)[0]
        self.assertIn("    permissions: {}", ref_job)
        self.assertNotIn("uses:", ref_job)
        self.assertNotIn("secrets.", ref_job)
        self.assertNotIn("github.token", ref_job)
        self.assertIn("      ref_key: ${{ steps.ref.outputs.ref_key }}", ref_job)
        self.assertIn("        id: ref", workflow_step(REF_KEY))
        self.assertIn("    needs: ref-identity", build_header)
        self.assertIn("    concurrency:", build_header)
        self.assertIn("      group: ${{ github.workflow }}-${{ needs.ref-identity.outputs.ref_key }}", build_header)
        self.assertNotIn("github.ref }}", build_header)
        self.assertLess(WORKFLOW.index("- name: " + IDENTITY), WORKFLOW.index("- name: Setup Gradle"))

    def test_cache_is_writable_only_for_the_exact_main_identity(self) -> None:
        cache_condition = re.search(
            r"^          cache-read-only: (.+)$", workflow_step("Setup Gradle"), re.MULTILINE,
        )[1]
        for ref, read_only in (
            ("refs/heads/main", False),
            ("refs/heads/Main", True),
            ("refs/heads/MAIN", True),
            ("refs/heads/feature/main", True),
            ("refs/heads/feature/ai-summary", True),
            ("refs/heads/5.6.1", True),
            ("refs/tags/5.6.1", True),
        ):
            with self.subTest(ref=ref):
                self.assert_success(self.derive(ref))
                self.assertEqual(read_only, self.evaluate(cache_condition))

    def test_case_only_branch_names_keep_independent_preview_releases(self) -> None:
        branches = ("feature/Foo", "feature/foo", "Main", "main", "MAIN")
        tags = set()
        for branch in branches:
            with self.subTest(branch=branch):
                self.assert_success(self.derive("refs/heads/" + branch))
                tags.add(self.outputs["tag"])
                self.assert_success(self.stage())
                publication, cleanup = self.publish()
                self.assert_success(publication)
                self.assert_success(cleanup)
                self.assertEqual(tags, {r["tag_name"] for r in self.state()["releases"]})
        self.assertEqual(len(branches), len({tag.casefold() for tag in tags}))
        self.assertFalse(any(call[:2] == ["release", "delete"] for call in self.calls()))

    def test_publication_selection_and_branch_concurrency(self) -> None:
        publication = re.search(r"^        if: (.+)$", workflow_step(PUBLICATION), re.MULTILINE)[1]
        cancellation = re.search(r"^      cancel-in-progress: (.+)$", WORKFLOW, re.MULTILINE)[1]
        self.assertLess(WORKFLOW.index("- name: " + PUBLICATION), WORKFLOW.index("- name: " + CLEANUP))
        self.assertNotIn("actions/upload-artifact", WORKFLOW)
        for event, ref, selected, cancelled in (
            ("push", "refs/heads/main", True, True),
            ("push", "refs/heads/Main", True, True),
            ("push", "refs/heads/MAIN", True, True),
            ("push", "refs/heads/feature/Foo", True, True),
            ("push", "refs/heads/feature/foo", True, True),
            ("push", "refs/heads/feature/ai-summary", True, True),
            ("push", "refs/heads/feature/thread-detail-compat-mode", True, True),
            ("push", "refs/tags/5.6.1", True, False),
            ("push", "refs/heads/feature/other", True, True),
            ("push", "refs/heads/review/team/topic", True, True),
            ("workflow_dispatch", "refs/heads/main", False, True),
            ("workflow_dispatch", "refs/tags/5.6.1", False, False),
            ("pull_request", "refs/pull/10/merge", False, False),
            ("push", "refs/pull/10/merge", False, False),
        ):
            with self.subTest(event=event, ref=ref):
                self.env["GITHUB_EVENT_NAME"] = event
                self.env["GITHUB_REF"] = ref
                self.assertEqual(selected, self.evaluate(publication))
                self.assertEqual(cancelled, self.evaluate(cancellation))


if __name__ == "__main__":
    unittest.main()
