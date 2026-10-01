# Simplify brittle test assertions

## Goal
Reduce test maintenance caused by exact source spelling and unnecessary loading-tip scaffolding. The user requested continued simplification, approved task creation, and explicitly approved the final planning summary before implementation.

## Confirmed facts
- The current source inventory has 561 JVM @Test methods across 83 files; this is not an execution result.
- ReleaseWorkflowContractTest duplicates some behaviors exercised by the Python release suite, while its Gradle configuration and checkout wiring checks have no equivalent behavioral owner.
- LoadingTipCatalogTest uses test-owned XML traversal to call a production predicate; the loading-tip specification already rejects fixed pool/copy/immutability snapshots.
- Exact file/line evidence, retained owners and intentional assertion losses are centralized in research/scope.md.

## Requirements
R1. Simplify only ReleaseWorkflowContractTest and LoadingTipCatalogTest according to research/scope.md. Remove unused local helpers/imports as necessary.
R2. Preserve release identity/signing and unique configuration protection; preserve AI eligibility outcomes and membership toggling. Leave account isolation, resource lifetime, security wiring and migration compatibility coverage intact.
R3. Record actual deleted assertions/scenarios and retained owners. Do not use skips, exclusions or parameterization to claim reduction.
R4. Preserve unrelated existing working-tree edits. Product code, Gradle configuration and production workflows are unchanged.

## Acceptance criteria
- AC1 (R1): release command-spelling duplication and the Gradle invocation snapshot method are removed; loading catalog tests invoke the real predicate directly without synthetic XML traversal or fixed inventory/lookup-count assertions.
- AC2 (R2): scope.md identifies retained owners; the focused changed tests and Python release behavior suite pass.
- AC3 (R3): results report actual test/helper/assertion changes and acknowledge loss of exact invocation-count/environment-wiring snapshots, fixed catalog inventory and lookup-count checks.
- AC4 (R4): diff review confirms no product/workflow changes or unrelated edits; the final repository Debug unit-test/lint gate passes with zero test failures/errors and zero lint Error/Fatal.

## Out of scope
Broad test reduction quotas, pruning migration snapshots, rewriting security/lifecycle guards, new testing frameworks, product refactoring, device operations and additional local APK packaging.

## Key decisions and risks
This is a lightweight two-test-file maintenance task with PRD plus a concrete scope ledger. Source guards without an existing replacement stay in place. Removing source snapshots intentionally reduces automatic detection of workflow implementation changes; existing offline release tests do not build real APKs or run actions/checkout. Runtime speed improvements are not promised.

## Approved second round and release
The user requests deletion of unnecessary whole tests. research/next-deletion-plan.md defines the approved 7-method/2-file extension, its retained owners, explicit low-risk losses and acceptance gate. The first-round results remain complete. The user approved proceeding with completion, commit, push, finish-work and a new stable release. Publish the next available patch version (currently 6.2.1) through the established tag-triggered CI workflow, with validated matching release notes. Do not wait for CI under the project publication policy.
