# Publication boundary and evidence

## Change boundary

- The behavior gap lives in `.github/workflows/build.yml`: the feature branch
  is eligible to build but its distribution step uploads an expiring artifact.
- Merge `origin/main` into the feature worktree with a normal merge, then edit
  only the workflow, focused workflow verification if needed, README download
  guidance, and the owning release specification/task records.
- Keep `CI_VERSION_NAME` as `X.Y.Z-debug.N`. `build.gradle` validates that exact
  pattern; attaching a branch suffix to this value would unnecessarily change
  the Android version contract. Carry the branch in separate release/asset
  metadata instead.
- Expected feature example: title `NGA Just Works 5.6.1-debug.51 (Debug,
  feature/ai-summary)`, APK `NGA-Just-Works-5.6.1-debug.51-feature-ai-summary.apk`,
  tag `branch-feature-ai-summary-<sha12>`.
- Branch tags must not start with `debug-` or `preview-`: existing main
  workflows delete those namespaces and cannot protect a newly introduced
  sub-prefix. Use a branch-specific prefix with an exact twelve-hex SHA suffix
  when selecting old branch publications; prefix-sharing branch names must
  not collide during cleanup.
- Keep only the newest successfully published preview in the active channel.
  Cancel superseded branch runs within the existing per-ref concurrency group
  to reduce stale-run cleanup races; never cancel stable-tag runs.
- Reuse the existing prerelease creation/rerun path and its SHA/prerelease
  checks. Pass variable release titles/tags through environment variables
  rather than inserting branch text into Bash source.
- Only `main`, the existing `feature/ai-summary` allowlist, and exact stable
  tags are eligible. No `workflow_dispatch` or wildcard branch expansion.

## Validation plan

1. Verify merge ancestry and the clean import of all files in `780594bf`.
2. Parse workflow YAML, run actionlint/Bash syntax checks, and exercise the real
   workflow shell blocks with offline fixtures/mocked GitHub calls.
3. Cover main/feature/stable identities; invalid/missing stable tags; reruns;
   branch-safe APK naming; and cleanup isolation including similarly prefixed
   branches, stable/unrelated releases, and API/deletion failures.
4. Run `python3 -m unittest discover -s scripts` and `git diff --check`.
5. Update the release contract and run an independent Trellis check before
   committing. Recheck remote refs before pushing the feature branch.
6. Follow the existing handoff convention: report the push and download
   location without claiming the new APK exists until CI has been verified.

## Live evidence inspected

- GitHub API confirms main `780594bf`, feature `c52e045c`, and successful feature
  build 34036399467 (workflow run number 50).
- Existing stable release: `5.6.1`. Existing ordinary main prerelease:
  `debug-9e0b59d29933` (`5.6.0-debug.48`).
- Local `gh` is installed and authenticated with repository/workflow access;
  the earlier assistant's missing-CLI limitation does not apply here.
