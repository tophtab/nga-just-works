# Comprehensive test reduction results

Implementation and independent review completed on 2026-10-01. Product behavior,
production scripts/workflows, supported migrations and device execution are
unchanged. Previous uncommitted passes and unrelated task directories were
preserved. Work remains uncommitted; no push or task archive was performed.

## Maintained surface

| Metric | Third-pass baseline | Final | Reduction |
| --- | ---: | ---: | ---: |
| JVM test methods | 618 | 472 | 146 (23.6%) |
| JVM test files | 67 | 63 | 4 |
| JVM source lines | 13,207 | 10,770 | 2,437 (18.5%) |
| Python methods | 37 | 32 | 5 |
| Python audited input rows | 141 | 86 | 55 (39.0%) |
| Python test source lines | 1,035 | 948 | 87 |
| Device methods / files / lines | 3 / 1 / 85 | 3 / 1 / 85 | 0 |

Across all three passes, the original 731 JVM methods become 472: 259 fewer
(35.4%). Third-pass totals must not be compared with original HEAD line counts.
Source inventories are `comprehensive-baseline-inventory.json` and
`comprehensive-final-inventory.json`; executed test counts agree with source.

Every domain/framework received a necessity audit. The primary-owner index is
[coverage-ownership.md](coverage-ownership.md); linked ledgers list every deleted
method and changed input matrix, remaining owner, or explicit coverage loss.
The JVM scenario total is deliberately not presented as an exact scalar:
fixture construction, output assertion loops, callback stress and true input
matrices are different units. Audited reductions include the duplicate 238-row
core emoticon decoder table, 235 legacy decoder rows, 480 redundant planner
property calls, 200 repeated selector draws and cross-layer HTTP/BOM matrices.
AI matrix counts distinguish actual nested inputs from fixture/assertion loops.
No scenarios were hidden with exclusions, ignored tests or parameterization.

## Target departure and accepted losses

The 250–350 JVM working range was not reached; final count is 472. The audit
removed 78/328 AI/profile/editor methods and 68/290 remaining JVM methods.
Keeping 122 fewer just to meet the upper target would require further risk
tradeoffs. Concrete retained-suite rationales are in both ownership ledgers:

- Cache store (8) separates disk owner/layout checks, corruption, sparse windows,
  account change during write, path/symlink safety and supported legacy ZIP scope.
- Reader session (11) and byte client (11) separate account snapshots, source
  handoff, stale anchors, retry budgets, exact origin, charset and byte limits.
- Body view ownership (21) checks distinct identity fields and resource lifetime;
  a single replacement test cannot detect omission of source/owner/generation.
- AI/profile suites retain distinct cryptographic, parser state-machine,
  cancellation, attempt identity, pacing and synchronous reentrancy transitions.
  Large suites have concrete retained-boundary lists, not a blanket security label.

Explicit low-risk losses include settings/default/grouping and board/menu copy
snapshots, workflow trigger/ignore-glob snapshot, repeated URL/status/input
spellings, repeated constructor/getter/default checks and selected redundant
happy paths. Resource/UI wiring is now reviewed when changed. Independent review
restored the COMMENT-with-numeric-raw-floor prompt integration test: its caller
could bypass the lower-layer classifier, so it was not safely redundant.

## Execution cost

Three serial samples per suite before and after; Gradle and Python never ran
concurrently. Compilation was warmed before each JVM measurement series. Only
Test tasks were forced to execute, with test-result caching disabled using an
external init script; production compilation tasks retained identical normal
incremental/cache policy. These are local warm-build samples, not cold CI claims.

| Metric | Baseline samples (s) | Final samples (s) | Median change |
| --- | --- | --- | --- |
| Sum of JVM XML suite execution | 16.289 / 15.980 / 17.887 | 11.142 / 11.059 / 12.650 | 16.289 → 11.142 (−31.6%) |
| JVM command wall time | 20.205 / 19.392 / 19.442 | 14.410 / 14.149 / 14.053 | 19.442 → 14.149 (−27.2%) |
| Python command wall time | 24.912 / 23.670 / 24.273 | 14.011 / 14.216 / 14.075 | 24.273 → 14.075 (−42.0%) |

The aspirational 30% execution reduction is met for JVM suite execution and
Python, but JVM command wall-time improvement is 27.2%. JVM startup/order and
local load still vary; do not claim 30% faster cold builds or lint.

Expected loopback starts per passing suite, expanded from setup and test loops:
MockWebServer 61→48; raw ServerSocket 3→3; total 64→51. Expected physical requests
228–229→122–123 (disconnect-at-start fixture creates the one-request range).
These are source-audited expected counts, not wire-traced measurements.

Separately instrumented passing Python runs measured direct `Popen` starts:
447→304 (Git 165→141, Bash 269→159, Python 13→4). Bash/Git/stub descendant
processes are excluded; instrumentation timings are not used in the benchmark.
The notes validator retains eight invalid inputs through its existing function
entry point plus real CLI success/missing-file failures. No new product harness.

Raw evidence: `baseline-benchmark.json`, `final-jvm-benchmark.json`,
`final-python-benchmark.json`, per-sample logs, and `*-process-count.json`.
The first 471-test benchmark was superseded after restoring the review case;
final evidence contains all 472 tests in each sample.

## Verification and operational guidance

- `./gradlew testDebugUnitTest lintDebug --continue --console=plain` completed.
  XML inspection correctly rejected 21 application `LintError` entries caused
  by a KAPT unit-test stub disappearing during concurrent generation/analysis.
  Other 12 module reports were clean. This was not counted as a passing lint gate.
- After the restored regression, `./gradlew testDebugUnitTest --console=plain`
  passed all 472 cases. With stubs stable, reran app `lintDebug`, forcing only
  lint analysis/report tasks out of their up-to-date/cache state. All 13 reports
  now contain zero Error/Fatal. No suppression or product/configuration fix.
- Final three JVM runs: 472 tests/63 suites, zero failures/errors/skips each.
  Python: 32 tests passed in all three timed samples and process-count run.
- Independent full-scope review covered both prior passes and third-pass
  deletion ownership. Its one substantive restoration is complete. Diff/import
  checks pass; production sources/scripts/workflow changes: none.
- Device tests: not run per project policy. The three unchanged platform-backed
  tests are retained; no device operation or extra APK assembly is required.
- Remaining dependencies are used: JUnit in four JVM-owning modules,
  MockWebServer in the app, AndroidX runner/JUnit for the retained device tests.
  Removed template-only dependencies from earlier passes remain removed.
- CI inspection found one packaging Gradle invocation and separate wrapper
  validation; no duplicated unit-test gate to remove. Actual release/signature
  checks and CI publication behavior remain unchanged.

Daily focused class/module/Python commands and downstream selection rules are
now in `.trellis/spec/backend/android-quality-guidelines.md`. Common image or
emoticon changes include common/core/app consumers; core changes include app;
Compose gesture changes include Compose/app. Shared configuration changes require
dependency-aware selection. Keep one final repository Debug/lint gate, inspect
all XML reports, and rerun only affected checks after a subsequent change/finding.
Domain specs now name primary invariant owners instead of obsolete test counts,
class inventories and exhaustive same-branch matrices.

For measurement reproduction, run `benchmark.py` from the repository root after
copying `force-test-execution.gradle` to `/tmp/simplify-tests-force.gradle`:
`python3 .trellis/tasks/10-01-simplify-tests/research/benchmark.py <phase> <jvm|python>`.
The baseline sources are preserved in `/tmp/simplify-tests-baseline.tar.gz` for
this environment; do not restore it over unrelated working-tree changes.
`count-python-processes.py` uses that baseline snapshot for its baseline mode
and current test sources for final mode. Final verification detail is preserved
in `comprehensive-final-gate.json`, `review-fix-tests.log`, `lint-recovery.log`
and `comprehensive-review.md`.

## Reviewable scope / deferred commit

One coherent test-maintenance change consists of JVM/Python test deletions and
matrix reductions, prior template-test dependency cleanup, test-specific spec
updates and this task's audit/measurement artifacts. It contains no product
feature changes. A suitable future work commit is
`test: reduce redundant regression coverage and validation cost`.
Unrelated `09-30-upstream-*` task directories and archived reader research must
remain outside that commit. Existing handoff did not authorize committing;
commit, archive and journal auto-commits are deferred, without repeating the
previously declined commit question.
