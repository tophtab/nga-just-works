# Parent Execution and Integration Plan

Status: approved for implementation on 2026-09-12 (user: “确认吧。开干吧。”).

## Plan readiness

- [x] Task creation/planning consent recorded.
- [x] Parent and two linked child task records created.
- [x] Reference userscript and pinned library investigated.
- [x] Existing profile data, normal/prefetch delivery, and metadata binding traced.
- [x] User confirmed mechanism-driven enrichment and no fixed request interval.
- [x] Request volume is modeled from delivered authors; no fixed three-page job.
- [x] Loading surfaces and nine conditional/source-verified tips researched.
- [x] PRDs rewritten against the final requirements/acceptance structure.
- [x] Parent/child designs and execution plans written.
- [x] Curated implement/check manifests validate for all three tasks.
- [x] Revised mechanism-driven summary explicitly approved on 2026-09-12: “确认吧。开干吧。” Earlier proposals are superseded.

## Execution order

1. Refresh branch/worktree state, then create
   `feature/thread-ip-location-loading-tips` from `main` in an isolated sibling
   worktree. The confirmed baseline `5bb92cf0` includes cache fix `6203dad5`.
   Carry this task tree's planning context into that worktree and run all product
   edits/checks there; preserve unrelated task directories and the AI worktree.
2. Activate the IP-location child and dispatch `trellis-implement` with its
   curated context; coordinate the network/cache and per-page delivery gates.
3. Check that child, then activate the loading-tip child and dispatch
   `trellis-implement`. Hand off shared fragment ownership explicitly.
4. Dispatch final `trellis-check` over the combined change. Verify requirements,
   request controls, account/view cleanup, passive loading copy, and AI eligibility.
5. Run the required offline Android gate and inspect every module's lint reports.
6. Update relevant specs, commit the reviewed task work, archive completed
   children/parent, and record the session according to finish-work rules.

Every dispatch starts with the active child path. Native Codex context injection
is preferred; child-side context loading is the fallback. Do not start the parent
as an implementation target solely because it owns the task tree.

Context validation reports the existing 32 KiB per-file injection limit for
`android-quality-guidelines.md` and `component-guidelines.md`. Dispatch prompts
must require child-side reads of the relevant full source sections; a truncated
injection is not sufficient. No project-wide context setting is changed.

## Execution progress

- 2026-09-12: User approved the corrected plan: “确认吧。开干吧。”
- Created the isolated branch/worktree from `main` at `5bb92cf0`, copied all
  three task directories, and configured its ignored `local.properties` to use
  the existing Android 35 SDK.
- Validated the IP child's context and activated it with `task.py start`.
- Dispatched `trellis-implement` for the IP child. Parent owns integration
  review and the build environment; loading-tip ownership follows sequentially.
- While the IP writer finishes its tests, the loading-tip implementer may own
  only the independent catalog/widget/resources and topic/notification bindings.
  `ArticleListFragment.java` stays exclusively with the IP writer/checker until
  explicit handoff; its tip binding remains sequential. This narrows concurrent
  ownership rather than changing either feature contract. Gradle work is also
  coordinated serially in this worktree.
- Both implementers completed their handoffs, including the narrow final
  article loading-widget binding. The combined `trellis-check` reviewed the
  complete parent/child scope and verified the original three request-boundary
  fixes. It added two missing request-control regressions, strengthened the
  real-client socket test, and fixed cache-only subscriptions resuming an
  expired paused queue. No unresolved in-scope finding remains.
- The final app build/unit/lint gate passed 223 tests. The final full-module
  lint rerun passed; all 13 XML reports contain zero Error/Fatal. The debug-unit
  repository diagnostic reproduced two documented example-test build failures
  in `lib_bu_statistics` and `lib_module_debug`; feature tests remain green.
- Added the author-profile-location and loading-usage-tips executable contracts,
  plus operation registry, prefetch, component, and index links. Final review
  and command evidence are in `research/combined-check-report.md` and
  `research/final-validation.md`. Commit/archive/journal follow the approved plan.

## Final integration validation

Use child-specific parser/cache/clock/selector tests first. The final gate is:

```bash
./gradlew :nga_phone_base_3.0:assembleDebug
./gradlew :nga_phone_base_3.0:testDebugUnitTest
./gradlew :nga_phone_base_3.0:lintDebug
./gradlew lintDebug --continue --rerun-tasks --console=plain
./gradlew testDebugUnitTest --continue
git diff --check
```

Inspect generated lint XML across every Android module using the quality
contract's report parser: Error/Fatal count must be zero. Re-run checks only
when subsequent changes or a finding invalidate earlier evidence.

No live NGA probes, device operations, Preview/Release builds, or release
publication are required. Preserve the two unrelated task directories present
before this task began and the sibling AI worktree.

## Acceptance and rollback gates

- Replan if profile parsing requires unsupported wire assumptions or if a
  lifecycle change would alter page prefetch/caching.
- Reject request initiation from adapter binding, a second location-prefetch
  planner/window, fixed three-page batching, or a time-based dispatch interval.
- Require location enrichment after every valid normal/prefetched page delivery,
  shared UID reuse, and no network lookup for saved-page disk-cache readers.
- Reject location updates that reload the body WebView.
- Reject tips that rotate rapidly, delay ready content, or advertise absent AI.
- Roll back each child independently; neither requires a thread-cache migration.
