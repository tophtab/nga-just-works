# Independent workflow review

Review date: 2026-09-12. Reviewer: `trellis-check`.

**Status: approved for the public CI commit and planned rollout.** Both issues
from the initial review are fixed and independently verified. No outstanding
code, test, or specification finding remains within this task’s change scope.
Branch synchronization and delivery remain execution work for the main session.

## Scope and context

Reviewed the complete task change relative to
`dade93ac12504f4664fd6853dc44850a89f3a91d` in
`/home/toph/nga-just-works-compat-mode`, including the untracked actual-Bash
fixture script and task artifacts. This is not a review of the earlier
compatibility feature against main.

Loaded the current `prd.md`, `design.md`, `implement.md`, all `check.jsonl`
references, package discovery, the backend index, shared thinking guides,
and the full `Scenario: Signed GitHub release APK` chapter directly from
disk. The complete quality file exceeds the native context-injection size
limit. Re-read the updated design and release contract after the fixes; the
generic quality template is not an active contract.

Reviewed public files:

- `.github/workflows/build.yml`
- `scripts/test_release_workflow.py`
- `nga_phone_base_3.0/src/test/java/gov/anzong/androidnga/ReleaseWorkflowContractTest.kt`
- `README.md`
- `.trellis/spec/backend/android-quality-guidelines.md`

Task JSON/JSONL files parse successfully and every manifest reference exists.
The current task plan and metadata correctly target the three remaining
origin branches and retain the initial four-branch observation as history.

## Findings (fixed)

The original implementer applied both corrections following the independent
review; this reviewer verified the resulting code and tests without competing
product-file edits.

### 1. Case-distinct branch names shared a concurrency group

- File: `.github/workflows/build.yml`.
- Issue: the original workflow-level group combined `github.workflow` with
  raw `github.ref`. GitHub groups ignore case, so `feature/Foo` and
  `feature/foo` could cancel each other even though Release tags were isolated.
- Fix: a `ref-identity` job with `permissions: {}`, no checkout, and no secrets
  hashes the exact full `GITHUB_REF`. Its full lowercase SHA-256 output feeds
  the build job’s concurrency group through a required `needs` dependency.
  Same-ref runs keep the same key across commits and run numbers; case-only
  refs and matching branch/tag names have distinct keys. Branch cancellation
  remains enabled while stable-tag cancellation remains disabled.
- Verification: executed the actual ref-key Bash without a checkout, checked
  exact hashes and repeatability, and verified the job/output/concurrency
  wiring. The fixtures also publish independent case-only preview channels.
- Official evidence read during the initial review:
  <https://docs.github.com/en/actions/how-tos/write-workflows/choose-when-workflows-run/control-workflow-concurrency>
  — “The concurrency group name is case insensitive.”

### 2. Cache selection treated `Main` and `MAIN` as exact `main`

- File: `.github/workflows/build.yml`, `Setup Gradle`.
- Issue: GitHub’s case-insensitive comparison made
  `github.ref != 'refs/heads/main'` false for `Main` and `MAIN`, allowing those
  feature branches to write despite the main-only cache-write contract.
- Fix: read-only selection now uses controlled release-identity outputs:
  `prerelease != 'true' || asset_suffix != ''`. The existing Bash comparison
  owns exact-main classification, so only the actual main preview can write.
- Verification: executed identity derivation and evaluated the actual cache
  expression for main, Main, MAIN, ordinary features, a version-shaped branch,
  and a stable tag. The fixture models GitHub’s case-insensitive comparison
  semantics, so reverting to the previous expression fails these cases.
- Official evidence read during the initial review:
  <https://docs.github.com/en/actions/reference/workflows-and-actions/expressions>
  — “GitHub ignores case when comparing strings.”

The fixture’s step extraction now stops at the next step or job according to
indentation, preventing pre-job metadata from entering the extracted Bash.
The release spec and task design document both corrected contracts.

## Findings (not fixed)

None. No additional issue was found in the final full-scope review.

## Full-scope behavioral assessment

- `push.branches: ["**"]` admits nested branch names while retaining stable
  tag patterns and documentation-only path exclusions. The pre-job does not
  add a manual publication entry point or need repository access.
- APK suffixes retain the AI naming convention. Unsafe UTF-8 bytes become
  hyphens; the readable component is bounded to 80 ASCII bytes with a
  `branch` fallback. Hashing the original branch name separates normalized,
  truncated, Unicode-only, and same-SHA slug collisions.
- Branch tags remain outside the main `debug-` and `preview-` namespaces.
  Cleanup accepts only the exact channel prefix followed by twelve lowercase
  hexadecimal characters, excludes the current tag, and preserves other
  channels and non-prereleases. Legacy AI migration is enabled only for the
  exact original AI branch, including protection against lookalike refs.
- Titles and dynamic tags are environment/argument data; jq receives exact
  tag values with `--arg`. Partial output from a failing API cannot continue
  publication or cleanup. Pagination, reruns, failed publication, and failed
  deletion have executable offline coverage.
- The publishing job still starts Gradle once. Stable notes, preview/release
  build variants, manifest checks, signing verification, and checksum
  validation remain in the workflow. Application identity, Gradle signing
  configuration, version algorithms, and the Wrapper workflow are unchanged
  from the review baseline.
- The Kotlin adjustments retain existing structural build contracts. New
  behavior is verified by executing the actual workflow Bash; string checks
  are limited to the workflow wiring and existing static contracts.
- README download guidance matches the implemented filenames, separate
  preview releases, shared applicationId/signature, and documented path skips.

## Verification

| Check | Final independent result |
| --- | --- |
| `python3 -m unittest discover -s scripts` | PASS: 36 tests in 156.567 s; 25 workflow, 8 version-code, and 3 release-notes tests, freshly executed after both fixes |
| `/tmp/nga-all-branches-apk-tools/actionlint .github/workflows/build.yml` | PASS: workflow schema, expressions, and shell integration; actionlint v1.7.7 |
| `bash -n` on every literal `run` block | PASS: all 7 blocks, extracted independently by indentation |
| Python syntax/AST validation of `scripts/test_release_workflow.py` | PASS; no separate Python type checker is configured for this script |
| Focused Kotlin/JVM release-contract gate | Retained PASS: 9 tests, 0 failures/errors/skips; follow-up changes do not alter JVM code or its asserted workflow sections |
| Task JSON/JSONL and referenced-file validation | PASS |
| `git diff --check` | PASS |

The implementer freshly ran the focused JVM gate successfully in 51 s before
the initial review. The reviewer then ran
`./gradlew :nga_phone_base_3.0:testDebugUnitTest --tests gov.anzong.androidnga.ReleaseWorkflowContractTest --console=plain`:
BUILD SUCCESSFUL in 8 s, all 223 tasks UP-TO-DATE. The reviewer inspected XML
confirming 9 passing tests with timestamp `2026-09-12T00:24:56`. Those results
remain applicable after the concurrency/cache-only follow-up; Gradle was not
repeated again. Python regressions and static checks were rerun on the final
workflow. Shellcheck is not installed; no shellcheck result is claimed.

Local logs:

- `/tmp/nga-all-branches-apk-independent-final-python.log` — final 36-test run.
- `/tmp/nga-all-branches-apk-independent-python.log` — initial 32-test review run.
- `/tmp/nga-all-branches-apk-independent-jvm.log` — initial reviewer JVM check.

## Handoff and verification boundary

Proceed with the approved separate public CI commit, then synchronize the
three existing branches: main, AI summary, and thread-detail compatibility.
The parent reports that parallel work deleted the IP/loading-tips branch;
this review does not require recreating it. The main session owns rechecking
current refs/WIP, preserving concurrent changes, applying the isolated public
patches, verifying final branch content, committing records, finish-work,
and non-force pushes. This approval does not claim those later operations or
remote APK builds have already completed.

No local APK packaging, credential inspection, device/ADB operation, real
NGA traffic, live Release mutation, other-worktree modification, or remote CI
polling occurred in either review iteration. APK packaging and signing remain
GitHub Actions checks after the authorized pushes.
