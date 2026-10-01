# Comprehensive test reduction

## Goal
Reduce the total maintained test surface and normal validation cost across the repository. The maintainer explicitly requested a plan for comprehensive reduction after two conservative passes. The maintainer approved this plan and resumed implementation on 2026-10-01. Final execution evidence and target departures are recorded in research/comprehensive-results.md.

## Confirmed baseline
- Original JVM suite: 731 methods. Two completed passes leave 618 methods in 67 files; 113 methods removed.
- Current domain inventory (exclusive path-based groups): AI/settings 215; author location 92; reader/navigation/cache 127; images/emoticons/editor 77; UI/configuration/release/other 107.
- Python: 37 test methods across release workflow (26), version derivation (8), and release notes (3). Methods include loops/subtests, so method counts understate executed scenarios.
- Device: 3 real AI configuration storage cases remain; eight template cases were removed. Device execution is opt-in.
- The last full Debug gate passed all 618 JVM tests and all 13 module lint reports (zero Error/Fatal). The subsequent deterministic cancellation check passed separately. Python's unchanged 37-test suite passed previously.
- Local JVM XML suite durations summed to 45.339 s before the second pass and 23.379 s afterward. These are single local samples, not a controlled end-to-end speed comparison. Python previously took 61.130 s under concurrent Gradle load.
- Prior work, detailed decisions and logs are retained in research/test-audit.md, research/slow-tests.md and research/second-pass-results.json. Prior planning artifacts are preserved in research/previous-plan-before-comprehensive-reduction.md.

## Requirements
R1. Audit every test domain, framework and normal validation entry point, including behavioral tests rather than only examples/source snapshots.
R2. Remove cross-layer duplication, same-branch input variants, trivial implementation details, obsolete cases and low-risk overcoverage. Keep one representative happy path and distinct important failure boundaries where justified; no uniform per-class quota.
R3. Give each retained behavioral rule a primary test owner. Higher layers retain only integration behavior not established by the lower layer. Removing duplicated cases is allowed; moving scenarios into loops or marking them ignored is not reduction.
R4. Preserve a compact critical set for account isolation, secret persistence/export, data corruption and supported migrations, stale/cancelled work, network session boundaries, and release identity/signing/destructive cleanup. This protects unique outcomes, not every historical test in those categories.
R5. Reduce expensive mock-server, filesystem and subprocess repetitions. Preserve a representative end-to-end boundary check and use the narrowest existing test seam for local logic. Do not refactor product code solely to make tests easier.
R6. Make default validation proportional to changed areas and keep one full retained-suite check before delivery. Keep all-module lint with zero Error/Fatal; do not turn lint off to claim fewer tests.
R7. Update obsolete test-specific spec mandates and remove unused fixtures, helpers and test dependencies. Keep product behavior and supported compatibility unchanged.

## Acceptance criteria
- Every domain has an audited keep/remove/replace decision and a primary owner for important invariants; actual deleted cases and scenario rows are recorded.
- Planning ambition: roughly halve the current JVM surface (about 300 cases; working range 250–350). This is an audit target, not a deletion quota or guarantee. If distinct high-value coverage requires more, document exactly why; do not hide cases or silently damage coverage to reach a number.
- Python and device tests also receive a complete necessity review; their counts depend on retained distinct behavior, not a blanket exclusion.
- Report test methods, actual scenario rows where practical, test files/lines, mock-server/subprocess starts and relevant durations. Parameterization alone cannot count as simplification.
- Compare meaningful execution costs using the same commands and cache/execution conditions, including separate JVM and Python measurements. An aspirational 30% additional execution-time reduction is investigated, not promised. Record unmet targets and environmental noise honestly.
- All retained tests pass; all 13 module lint reports have zero Error/Fatal. Review finds no unaccounted loss of unique critical coverage.
- Daily focused commands and the final full validation gate are documented with changed-layer/downstream selection rules.

## Scope and limits
In scope: JVM/Python/device test sources, test fixtures/helpers/dependencies, test invocation guidance and test-specific spec requirements. Assess the existing CI entry points; no live publication or weakening production APK/signature checks.
Out of scope: production feature changes, changing supported migration paths, blanket exclusions/disabled tests, adding a coverage framework, broad harness architecture, local signed APK packaging, device operations, live API traffic, commits/pushes in this planning turn.

## Trade-off
Some low-risk formatting/default combinations, repeated happy paths and source-wiring snapshots will lose direct assertions. The deletion ledger must name such losses rather than claim unchanged coverage. Critical invariants and distinct historically reproduced failures retain focused protection.
