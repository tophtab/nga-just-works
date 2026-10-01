# Comprehensive reduction design

## Selection model
Inventory methods plus loops/parameter rows and test setup cost. Classify each check by observable outcome, primary owner, risk and distinctness:
- Keep: unique high-impact invariants, representative main user flows, supported migration/data recovery boundaries and distinct reproduced regressions.
- Consolidate coverage: select one existing test owner; delete copies at other layers. Retain actual integration contracts such as session propagation or persistence only where the lower-level test does not establish them.
- Remove: equivalent inputs following the same branch, trivial getters/default spelling/layout copy, obsolete implementation assumptions and behavior already covered with the same failure signal.
- Make cheaper: retain important outcome while removing unrelated timers, shared queue cooldowns, repeated process launches or server startup. Do not weaken timeout/cancellation synchronization.

A single happy-path test is insufficient for a security or data-loss boundary. Conversely, labeling a test security-related does not justify every malformed-input combination. Select distinct equivalence classes and boundary transitions, not arbitrary sample counts.

## Domain boundaries
| Domain | Baseline JVM methods | Main review focus |
| --- | ---: | --- |
| AI/settings | 215 | Model/configuration/store duplication, parser-vs-client error matrices, summary/controller/loader lifecycle overlap, full server fixtures for pure parsing |
| Author location | 92 | Repository/page/store/session overlap, duplicated replay/cache/expiry cases, equivalent malformed inputs and cooldown states |
| Reader/navigation/cache | 127 | Repeated page ownership and stale callback assertions across presenter/session/cache; parser/row projection examples sharing branches |
| Images/emoticons/editor | 77 | Host-format variants, mapping/default/ordering overcoverage, preserve real encoding/host safety and source-preserving edits |
| UI/configuration/release/other | 107 | Low-risk resource inventories and state/editor defaults, remaining snapshots, keep unique gesture and release boundary behavior |
| Python | 37 methods | Count loop scenarios, separate pure validator logic from CLI smoke coverage, reduce repeated Git/setup/subprocess matrices while retaining destructive cleanup safety |
| Device | 3 methods | Keep only distinct platform-backed Keystore/AtomicFile guarantees; check overlap with JVM fakes without assuming JVM proves Android platform behavior |

## Execution ownership
Main session owns artifacts, spec synchronization, baseline/final evidence and integration. Two implementers can own disjoint batches: (A) AI and author-location tests, (B) reader/UI/editor/common and release/device tests. Assign exact paths before dispatch and separate shared fixtures. No concurrent Gradle builds. Use one independent reviewer for unique coverage loss and fragile timing checks.

## Validation selection
During development run the narrow module/class checks affected by a completed batch, with downstream consumers when a shared contract changes. For dependency/configuration/shared-base edits include affected modules and downstream callers. Python checks run when their scripts/workflows/fixtures change. Before final delivery run the retained repository Debug suite and lint once, plus Python because this revision audits/changes its tests. Device cases are reviewed statically and compiled only if changed and relevant; execution remains opt-in.

Avoid building an elaborate change-selection framework. Document explicit commands and dependency rules first. Do not replace the full final gate with a hand-maintained tiny smoke list or change production publication behavior.

## Measurement and rollback
Keep the prior two-pass state as the starting point; all work is currently uncommitted. Capture path-scoped baseline snapshots outside product sources before further edits, or use approved commits later. Never use a repository-wide reset to undo a batch. Preserve other untracked tasks.

Record warm compilation separately from actual test execution. For a controlled JVM sample, rerun Test tasks only via a temporary external Gradle init script (no production-task --rerun-tasks) so test cases execute while compilation cache policy stays equal. Run Python serially with Gradle. Use three comparable samples before/after only for the dedicated benchmark; compare medians. Stop measurement on an external blocker rather than inventing speed gains.

Maintain a deletion ledger: file/method or scenario, outcome, reason, remaining owner or explicit low-risk coverage loss. Review tests whose assertions were weakened or whose setup changed for accidental passes.
