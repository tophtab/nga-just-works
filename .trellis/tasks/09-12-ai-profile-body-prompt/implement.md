# Execution plan: profile topic bodies and prompt simplification

## Entry gate

- [x] Task creation/planning was authorized; use the existing task and worktree.
- [x] R4 is resolved: first 1,200 characters of each topic and reply body.
- [x] PRD, design, and source research capture scope, identity, failure, and size
  behavior; implementation/check context manifests contain real entries.
- [x] Final summary was presented and the maintainer approved proceeding on
  2026-09-12, explicitly requesting commit, finish-work, then push.

Worktree: `/home/toph/nga-just-works-ai-summary`.
Branch: `feature/ai-summary`.
Task: `.trellis/tasks/09-12-ai-profile-body-prompt`.

## 1. Activate and load context

- [x] After final-plan approval, run `task.py start` for this task in the AI
  worktree and load Phase 2.1 for the Codex agent workflow.
- [x] Dispatch `trellis-implement` with `Active task:` as the first prompt line
  and the exact worktree. The worker owns the AI profile collector/input/preset
  production files and their relevant unit tests. It is not alone in the
  repository and must preserve other edits. The main agent owns task/spec
  records and coordination.
- [x] In both implementation and review dispatches, require direct reads of
  relevant complete sections from `ai-summary-contract.md` and
  `android-quality-guidelines.md`: task validation reports that both exceed
  the 32 KiB per-file injection limit. A truncated injected excerpt is not
  sufficient context; the complete files remain available in the worktree.

## 2. Implement the bounded topic-body path

- [x] Retain validated topic IDs privately during first-page list parsing.
  Share list validation and preserve accepted-entry ordering/limits.
- [x] Extend `NgaProfilePageSource` with sequential original-post reads using
  the existing client/session and `THREAD.PAGE` wire shape. A package-private
  helper in the summary package is allowed if it keeps parsing or request
  ownership clear.
- [x] Parse and verify page/thread/original-row identity before projecting
  body text. Implement explicit unavailable-body handling and safe failures.
  Keep normalization local, bounded, and outside quoted strings.
- [x] Preserve exactly-once terminal callbacks and cancellation through list,
  enrichment, and subsequent reply phases, including synchronous fake calls.
- [x] Keep `ProfileSummaryLoader`'s category interface and existing controller
  behavior; update related source comments that incorrectly imply only two
  transport reads.

## 3. Update input composition and presets

- [x] In `ProfileSummaryInput`, use one 1,200-character body limit for topics
  and replies, append the appropriate body label, and preserve immutable
  samples, accurate counts, existing metadata bounds, and quote markers.
- [x] Replace the title-only/800-character framing and delete the entire
  rejected fixed paragraph.
- [x] In `AiProfilePrompt`, remove the overlapping mandatory identifier/quote
  clause while preserving each preset's voice, structure, output-length
  instructions, and saved custom text semantics.

## 4. Add and update meaningful behavior coverage

- [x] `NgaProfilePageSourceTest`: request shape/session snapshot; topic TID
  validation; actual original-body inclusion; misleading first row; other
  authors/floors/TIDs; unavailable bodies; malformed envelopes; known wrapper
  handling; response bounds; cancellation and late/synchronous callbacks.
- [x] Update network topic fixtures to supply valid TIDs and matching detail
  responses; do not leave MockWebServer tests waiting for unscripted requests.
  Check at most one active request and the application-scheduled read limit.
- [x] `SummaryInputTest`: replace obsolete topic-body exclusion assertions,
  test 1,199/1,200/1,201-character bodies and surrogate boundaries for both
  categories, and retain count/order/quote isolation checks. The integrated
  maximum-sample/preset checks are in `AiSummaryClientTest`.
- [x] Retain existing `ProfileSummaryLoaderTest`, `SummaryControllerTest`,
  `AiProfilePromptTest`, configuration snapshot, and custom-text coverage.
  Add an integrated custom-overflow regression only if existing client tests
  do not cover the no-request error boundary sufficiently.
- [x] Review the complete composed prompt for each style; do not add tests
  that merely duplicate the new prompt prose.

Focused command:

```bash
./gradlew :nga_phone_base_3.0:testDebugUnitTest --tests 'sp.phone.ai.*' --tests 'sp.phone.ai.summary.*' --console=plain
```

## 5. Check, synchronize contracts, and hand off

- [x] Dispatch `trellis-check` for the complete feature diff after the worker
  finishes; give it the same task/worktree and preserve-edit instruction.
  Resolve verified findings before handoff.
- [x] Run the device-independent Android quality gate and inspect lint report
  contents for `Error`/`Fatal`, not just process exit status:

```bash
./gradlew :nga_phone_base_3.0:assembleDebug
./gradlew :nga_phone_base_3.0:testDebugUnitTest
./gradlew :nga_phone_base_3.0:lintDebug
./gradlew lintDebug --continue --rerun-tasks --console=plain
./gradlew testDebugUnitTest --continue
```

- [x] Record actual test counts/results and distinguish any verified baseline
  failure from this change. Do not claim live service/device verification.
- [x] Main agent: update `.trellis/spec/backend/ai-summary-contract.md` for
  topic enrichment, the 1,200-character limit, simplified prompts, request
  bounds, and tests. Update the `THREAD.PAGE` current-fork delta in the
  operation registry if needed to document this additional consumer.
- [x] Run `git diff --check`, verify the changed-file set and task manifests,
  update acceptance checkboxes with evidence, and load the finish/commit step
  guidance. The maintainer authorized the work commit, finish-work archive and
  journal, then push to `feature/ai-summary`, in that order. Device operations
  and live service calls remain outside the task.

## Completion evidence

Implementation and the final full-scope `trellis-check` review are complete.
The reviewer found no task-scoped code defects or remaining contract drift and
made no production/test edits. The full app suite reports 368 passing tests;
all 13 Android lint reports contain zero Error/Fatal issues. The repository-wide
test diagnostic reproduced only the two documented library example compilation
failures. [validation.md](validation.md) records commands, counts, and limits.

The work commit includes the cohesive AI source/test changes, updated backend
contracts, and this task's planning/validation records. Finish-work then archives
this task and records the work commit in the developer journal; the final step
pushes `feature/ai-summary`, as authorized by the maintainer.

## Rollback and stop conditions

Revert only this task's cohesive profile-code/test/spec delta if rollback is
needed. No data migration or provider reconfiguration needs reversing.
Malformed or mismatched source input must produce the agreed safe error, not
broader scraping, account switching, permissive execution, or an automatic
fallback to unrelated reader APIs. Scope changes return to planning.
