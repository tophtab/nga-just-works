<!-- prd.md -->

# Simplify tests

## Request
Remove unnecessary tests and speed up the testing process. The maintainer delegated task-creation judgment and requested autonomous execution.

## Acceptance
- Remove generated example tests and demonstrably low-value or redundant source-text assertions after inspecting each candidate.
- Preserve behavior regressions for parsing, state, persistence, network/session safety and release publication safety.
- Remove test-only dependencies made unused by deletions.
- Avoid duplicate local verification invocations and forced rebuilding in the normal documented gate; retain a complete Debug unit gate and zero-error lint checks.
- Record before/after counts, commands and timing when feasible; distinguish task reduction from measured speedup.
- No product behavior, release workflow behavior, device operations or live network API interactions change.

## Follow-up: deeper simplification
The maintainer challenged why over 700 cases remain. First-pass removal of only 30 JVM cases was too conservative. Inspect remaining source-string snapshots and repeated scenario coverage; keep only unique critical integration checks that cannot be covered by existing behavior tests. Do not merge methods merely to disguise the count. Investigate measured slow suites as well as total count.


<!-- design.md -->

# Design

The gap is validation cost with weak coverage: generated arithmetic/package examples, implementation-string assertions and duplicate local build/lint/test commands. Behavior lives in product code; this task changes only tests, unused test dependencies and validation guidance.

Inspect test bodies, not names, before deleting. Keep executable behavior tests, release Bash fixture tests and security-sensitive integration assertions. Pure layout/text snapshots with no meaningful behavior may be removed; mixed files should keep valuable methods. Do not introduce blanket exclusions or disable variants. Remove dependencies only where all their consumers are gone. Main session owns spec updates; implementer owns tests/build dependency edits and evidence.

Normal validation should invoke repository Debug tests once and lint once in a shared Gradle invocation. Full forced rebuilding is reserved for diagnosing stale outputs. No unrelated refactoring, APK packaging, device runs or publishing.

## Follow-up boundary
Remaining source snapshots are a second-pass deletion target; preserve actual fixture parsing and executable methods in mixed files. Source spelling alone is insufficient evidence of behavior. Retain narrow security-sensitive assertions if they are the only coverage, and document coverage loss honestly. Timing XML shows three fake-network suites account for about 40 of 45 suite-seconds; inspect redundant slow cases and test-harness waits without changing production behavior. Main owns spec synchronization.


<!-- implement.md -->

# Execution

1. Inventory and inspect deletion candidates; record counts and a baseline repository Debug test run (wall time and XML totals). If environment blocks Gradle, record the exact failure and use available static/offline verification.
2. Delete proven low-value tests, prune unused test dependencies, and document file-specific reasons in research/test-audit.md.
3. Run repository Debug tests and lint together once; inspect all generated lint reports for Error/Fatal and record retained test totals. Run Python offline tests if assessing release coverage. Avoid forced production recompilation for timing; report warm/cache effects.
4. Main session updates quality guidance to a single incremental test/lint invocation and removes obsolete example-test mandates, preserving safety coverage.
5. Independent check agent reviews deletions, retained coverage, dependency pruning and validation evidence; reruns only for a concrete concern.
6. Record results and finish. Do not push.

## Completed verification

- Removed 24 test files and 5 methods; JVM cases 731 to 701, device cases 11 to 3.
- Pruned unused test dependencies; 9 modules no longer own JVM test sources.
- Combined Debug test/lint gate passed: 701 tests, zero failures/errors/skips; all 13 lint reports zero Error/Fatal.
- Python offline suite: 37 passed. git diff --check passed.
- Independent trellis-check review passed with no findings.
- Updated Android quality, frontend component and prefetch validation guidance.
- Timing is not a comparable benchmark: cached test-only baseline 32.18s; changed combined test/lint gate 109.65s. No percentage speedup claimed.
- Implementation complete; commit/archival awaits the workflow-required commit confirmation. Pre-existing unrelated task directories remain untouched.

## Follow-up execution
1. Implementer A owns remaining source-reading tests and their audit, pruning snapshots and keeping executable mixed-file cases.
2. Implementer B owns AiModelsClientTest, AiSummaryClientTest and NgaProfilePageSourceTest, inspecting actual waits/redundancy and reducing avoidable test time. No production changes.
3. Run targeted checks for B to measure those suites, then one final combined repository gate after both finish. No Python rerun unless changed or coverage concern.
4. Review final scope and update final counts/evidence/specs.

## Second-pass results
- Removed 82 additional source-snapshot methods and one redundant network case; final 618 JVM cases.
- Kept real parsing, persistence, session, cache, lifecycle/controller and network failure regressions.
- Removed unrelated real-time production cooldowns from transport-only test fixtures, shortened artificial delays, and stabilized stream cancellation with a latch.
- Full Debug test/lint gate passed; 618 cases zero failures/errors/skips, 13 lint reports zero Error/Fatal.
- Full results retained in research/second-pass-results.json; single latest latch-case revalidation follows.
- No new Python execution required because its source/workflow and coverage evidence are unchanged.

Latest deterministic cancellation check passed independently: 1 case, 0 failures/errors/skips, 0.326 s XML time. Review accepted synchronization; no further source changes or test runs required.
