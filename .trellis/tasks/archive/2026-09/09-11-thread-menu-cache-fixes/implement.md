# Execution Plan

## Planning Gate

- [x] Capture the three requested outcomes and inspect both normal/cache paths.
- [x] Trace recent notification → reply → full thread → cache write/read.
- [x] Complete the PRD convergence pass and record the design boundary.
- [x] Present the final planning summary and receive explicit approval of that
  summary in a subsequent user message. Approved with `可以` on 2026-09-11.
- [x] After approval, activate this task with `task.py start`.

## Implementation

- [x] Dispatch `trellis-implement` with this active task and curated context.
  The implementation owner handles the bounded product/test files listed in
  `design.md`; the main session owns task artifacts and spec updates. All
  agents must preserve unrelated workspace changes, including the existing
  `09-11-upstream-august-2026-review` task.
- [x] R1: Remove the four requested actions from both floor menus, and delete
  their local handlers/imports without removing shared resource IDs or the
  standalone voting controls.
- [x] R2: Apply the ordinary reader's 1-5 equal-width / 6+ scrollable rule to
  cached tabs before binding the pager. Preserve sparse page mapping.
- [x] R3: Fix cache visibility for full-thread contexts and supply missing
  metadata from the loaded page before calling the existing cache model.
- [x] Add meaningful offline cache regression tests, including read-back of
  saved description metadata and exclusion of partial/filtered views.

## Verification

- [x] Inspect the patch for scope, surviving resource references, selected-page
  identity, cache compatibility, and unchanged network/prefetch behavior.
- [x] Run the project gate (Gradle invocations may be grouped only where they
  retain equivalent coverage and flags):

```bash
./gradlew :nga_phone_base_3.0:assembleDebug
./gradlew :nga_phone_base_3.0:testDebugUnitTest
./gradlew :nga_phone_base_3.0:lintDebug
./gradlew lintDebug --continue --rerun-tasks --console=plain
./gradlew testDebugUnitTest --continue
```

- [x] Parse the generated lint reports for every Android module; require zero
  Error/Fatal findings regardless of the Gradle exit status.
- [x] Attribute any aggregate unit-test failures to the documented legacy
  fixture baseline only after examining the actual failures. The app's unit
  tests and owned regressions must pass.
- [x] Run `git diff --check` and a final `trellis-check` review covering all
  changed product files and the accepted PRD.
- [x] Do not invoke ADB, connected tests, installation, or live NGA requests.

## Completion

- [x] Record the thread-menu, cached-tab, and cache-metadata contracts in the
  appropriate existing specs; update affected spec indexes only if necessary.
- [x] Record actual commands/results and any validation limits in this task.
- [x] Obtain delivery authorization: the user explicitly requested commit,
  finish-work, and push after completion.
- [x] Commit the verified product changes and spec updates: `6203dad5`.

After the work commit, archive this task, record the developer journal with the
work-commit hash, and push `fix/thread-menu-cache` to `origin`. The archive status
and journal commits record those bookkeeping steps.

Rollback is a source revert of the owned files; no cache data removal or
migration is needed.
