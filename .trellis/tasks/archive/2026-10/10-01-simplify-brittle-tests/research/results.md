# Implementation and validation results

## Changes
Only the two approved test source files were edited. No product code, workflow, configuration, dependencies, exclusions or skips changed.

| File | Lines before → after | Test methods | Assertion call sites |
| --- | --- | --- | --- |
| ReleaseWorkflowContractTest.kt | 139 → 104 | 4 → 3 | 44 → 26 |
| LoadingTipCatalogTest.java | 103 → 38 | 4 → 4 | 16 → 6 |
| Total | 242 → 142 | 8 → 7 | 60 → 32 |

These are lexical assertion call sites, not runtime assertion counts. Two catalog XML helper methods were removed; the release block-extraction helpers remain in use. Net reduction is 100 source lines and 28 assertion call sites. The catalog retains all four test methods; no independent behavioral cases were merged merely to lower counts.

Release changes remove the exact git-tag command assertion, the two SDK staging-command spelling assertions, and the 15-assertion Gradle invocation snapshot method. SDK configuration, module inheritance, ABI, checkout depth/filter, application identity, signing and build-variant assertions remain. The SDK method was renamed to describe its remaining configuration scope. Behavioral owners remain the Python release suite, as mapped in scope.md.

Catalog tests now directly invoke the production eligibility predicate for valid key/destination/class, missing class, wrong key and wrong destination. AI membership has one owner in optionalCapabilityControlsAiInstructionMembership; redundant downstream membership assertions were removed from the eligibility methods. Fixed base count, uniqueness/nonzero/resource inventory, relative size/contains-all, immutability and exact lookup-count assertions were removed along with XML construction/traversal and unused imports.

## Validation
- Focused JVM: `./gradlew :nga_phone_base_3.0:testDebugUnitTest --tests 'gov.anzong.androidnga.ReleaseWorkflowContractTest' --tests 'sp.phone.view.LoadingTipCatalogTest' --console=plain` passed. XML: 7 tests, zero failures/errors/skips (3 release + 4 catalog). Evidence: focused-gradle.log and focused-results.json.
- Offline Python release behavior: `python3 -m unittest discover -s scripts -p 'test_release_workflow.py'` passed 23 tests. Evidence: python-release.log.
- Repository gate run once: `./gradlew testDebugUnitTest lintDebug --continue --console=plain` passed. XML inspection: 560 tests in 83 reports, zero failures/errors/skips; all 13 Android module lint XML reports present, zero Error/Fatal. Evidence: debug-gate.log and gate-results.json. Normal incremental outputs were used; unchanged library tests may be up-to-date.
- `git diff --check` passed. Scoped source diff reviewed. Concurrent edits to the prior simplify-tests task and unrelated untracked files were left untouched.
- Device tests: not run per project policy. No live publication or additional APK packaging.

## Intentional limitations
Exact Gradle invocation count, shell variable/environment wiring, staging/publication Gradle prohibition snapshots, fixed catalog inventory/immutability and lookup counts are no longer automatically asserted. Review workflow integration and resource inventory when changing those owners. Offline Python tests execute the identity/staging/publication shell with fixtures; they do not execute the actual Gradle build step or actions/checkout. No runtime speed improvement is claimed.
