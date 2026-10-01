# Execution plan: comprehensive reduction

## Status
Implementation and independent review completed on 2026-10-01. All 472 retained
JVM tests pass; all 13 lint reports have zero Error/Fatal after rerunning lint
following a transient KAPT stub-analysis failure. Python has 32 passing methods.
Final measurement and consolidated results: `research/comprehensive-results.md`.
No commit or push was authorized; all prior and current task changes remain
uncommitted. Unrelated task directories were preserved.

## 1. Establish review baseline
- [x] Confirm working tree and preserve all prior edits plus unrelated task directories.
- [x] Inventory all JVM, Python and device cases, including loop/parameter scenario rows, fixture files and setup costs.
- [x] Extract distinct invariants and create research/coverage-ownership.md plus a deletion ledger.
- [x] Establish reproducible, serial baseline measurements: three JVM and three Python samples; compilation warmed separately and source-owning XML inspected. No device tests.

## 2. Remove low-value checks throughout the repository
- [x] Remove remaining source snapshots, trivial defaults/getters/layout-copy inventories and unsupported/obsolete historical assumptions.
- [x] Remove same-branch input variants using explicit equivalence classes; retain meaningful boundaries.
- [x] Do not count parameterization or renamed/ignored scenarios as deletion.
- [x] Run only affected module/class checks after a coherent batch.

## 3. Remove cross-layer and behavioral duplication
- [x] Review AI/settings and author location first (307 of 618 JVM methods).
- [x] Assign one owner for each parser, validation, cache, cancellation and persistence rule; remove redundant upper/lower-layer copies.
- [x] Review reader/navigation/cache (127), images/editor (77), and UI/configuration/release/other (107) by the same rules.
- [x] Keep compact coverage for distinct regressions and high-impact boundaries. For every retained large suite explain why its cases are distinct.
- [x] Check coverage ledger before removing a domain's final protection.

## 4. Reduce expensive infrastructure and non-JVM tests
- [x] Review Python workflow/version/notes scenarios and repeated local Git, server and subprocess creation; preserve actual Bash/CLI smoke and release cleanup/identity safety.
- [x] Remove redundant network/disk integration permutations; keep important wire/persistence boundaries and prefer existing pure/fake-time seams.
- [x] Review all three device cases for unique platform guarantees; don't replace genuine platform coverage with a host fake.
- [x] Remove orphan fixtures/helpers/imports/test dependencies; no new harness framework.
- [x] Test asynchronous changes with deterministic coordination; keep bounded waits and dedicated real timeout tests.

## 5. Simplify routine validation and synchronize specs
- [x] Document focused module/class commands plus downstream selection and Python triggers.
- [x] Retain one final combined Debug/lint gate and zero Error/Fatal report inspection.
- [x] Update domain specs from historical class-name mandates to a concise set of retained invariants and their owners.
- [x] Review CI entry points; avoid duplicate test invocation if found. Do not change actual release/signature checks.

## 6. Verify and report
- [x] Independent reviewer checks deleted unique behavior, supported migration coverage, account/secret/export boundaries and accidental test weakening.
- [x] Run `./gradlew testDebugUnitTest lintDebug --continue --console=plain`; inspect all 13 lint reports and source-owning modules' XML.
- [x] Run `python3 -m unittest discover -s scripts` serially; inspect actual tests/scenario counts.
- [x] Run `git diff --check`; no unrelated edits, blanket excludes or skipped cases.
- [x] Complete matching execution benchmarks (three comparable final samples), report medians and cache/compilation conditions.
- [x] Report old/new methods, real scenarios, files/lines, infrastructure starts and timings. Explain any departure from the 250–350 JVM working target or aspirational performance target.
- [ ] Commit/archive: deferred under the handoff’s existing no-commit authorization boundary. Reviewable diff and scope are summarized in research/comprehensive-results.md; no new approval request repeated.

## Stop/rollback criteria
A candidate that removes the only critical invariant test is revised or kept. A flaky/racy replacement is fixed before continuing. Revert only the responsible batch when checks expose unsupported deletion; do not change product behavior to make a smaller suite pass. Target counts do not override coverage decisions.
