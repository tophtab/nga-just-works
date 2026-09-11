# Implementation Plan: Loading Usage Tips

Status: approved for implementation on 2026-09-12 (user: “确认吧。开干吧。”).

## Implementation boundary (2026-09-12)

- The behavior gap is discoverability during existing foreground initial loads.
  The owner is the shared legacy loading widget and its host view lifecycle;
  request scheduling and page-prefetch state are not changed.
- The independent implementation owns `LoadingLayout`, its two shared layout
  resources, a local resource catalog and selection/occasion policy, focused JVM
  tests, and lifecycle bindings in `TopicSearchFragment` and
  `RecentNotificationFragment`.
- The parent handed over the narrow `ArticleListFragment` loading-widget
  import/type/view-lifecycle binding on 2026-09-12 after reviewing the IP slice.
  The location integration and existing `onDestroyView` cleanup remain intact.
- Selection requires the loader's own visible state, a resumed host view, and
  actual window/ancestor visibility. Ancestor hiding and pause do not finish an
  occasion; hiding the loader itself does. AI eligibility reads the bundled
  settings entry and verifies its declared Fragment class, without constructing
  that screen.
- Reuse preserves existing spinner IDs, wrapper attributes, terminal visibility
  callbacks, and delay. No operation dialogs, new gestures, AI implementation,
  persistent settings, network traffic, or display timers are added. Gradle is
  coordinated with the parent after the IP implementer's initial validation.

## Ordered work

- [x] Review the approved parent/child artifacts and curated context, then
  activate this child with `task.py start`.
- [x] Load `trellis-before-dev`. Recheck whether the AI feature has joined the
  implementation baseline; preserve capability-based tip eligibility.
- [x] Add the source-verified resource catalog and a small once-per-loading
  selector/state policy. Verify empty/single/multiple pools and lifecycle reuse.
- [x] Extend shared `LoadingLayout` / `include_loading_view.xml`, adapting
  `list_loading_view.xml` without changing IDs, background, size, or margins.
- [x] Bind selection to foreground view lifecycle and existing loading visibility
  in article/topic/notification hosts. Preserve all existing completion/error
  transitions, prefetch behavior, and content-preserving refresh.
- [x] Review every tip against its source entry point and actual AI availability.
  Check semantic colors, large-font layout, and passive accessibility text.
- [x] Dispatch `trellis-check`; fix confirmed issues. Run the parent integration
  quality gate after both children are complete.
- [x] Update the component contract with the verified selection/lifecycle rule.
  Parent coordinates final commit/archive/session wrap-up.

## File ownership and dependencies

Own loading widget/resource/catalog/state code and needed bindings in
`TopicSearchFragment` / `RecentNotificationFragment`. Coordinate
`ArticleListFragment` with the IP-location child; do not overwrite its view
lifecycle or page-location subscription changes.

No dependency on merging the separate AI branch: the feature-presence check
supports the current eight-tip build and a later nine-tip build. Do not alter
the sibling worktree.

## Validation

Use executing JVM tests for selection/state policy and AI catalog eligibility.
Run the existing article refresh/request-state/prefetch and topic-title-refresh
regressions when touching those bindings. Resource compilation and lint must
preserve loader layout compatibility.

The parent runs the full offline Android gate once after final integration;
inspect all module lint reports for Error/Fatal entries. No new literal-copy
snapshot tests, device operations, or live requests are needed.

## Review and rollback

Do not activate before approval. Revisit planning if the shared loader cannot
preserve existing lifecycle/visibility semantics without broader UI changes.
Rollback the local catalog/widget additions and associated bindings; existing
request behavior and user preferences remain compatible.

## Implementation progress (2026-09-12)

- Independent slice implemented: eight base strings plus the conditional AI
  string; immutable eligible catalogs; process-level previous-choice selection;
  per-view occasion state; shared widget/layout integration; and view-lifecycle
  bindings in topic/search and recent notifications.
- The widget handles its own visibility separately from ancestor/window
  visibility. A loader hidden while detached also completes its occasion.
  Lifecycle destruction removes its observer and clears the view-owned state.
- `BundledLoadingTips` inspects the application's `R.xml.settings` once on first
  foreground demand and requires both the verified preference key/destination
  pair and a loadable AndroidX Fragment subclass. The current checkout has no AI
  entry; no sibling worktree was changed or queried at runtime.
- Standalone `javac` + JUnit 4.13.2 passed all 13 selector/occasion tests, using
  temporary output outside the repository. `git diff --check` also passed.
  Five catalog/capability tests are written and await generated Android resources
  in the coordinated Gradle run.
- The parent approved the final fragment handoff after the IP slice's initial
  app build, 196-test unit run, and app lint passed. The loading implementer added
  only the `LoadingLayout` import/type and binding immediately after ButterKnife
  binds the article view. No location delivery, retained-data reuse, request,
  transition delay, or teardown code was changed by this handoff.
- Added `TopicPagePrefetchContractTest.retainedPageLoadingTipsUseThePageViewLifecycle`
  to pin the article binding to the view owner after field binding. A standalone
  source/XML audit passed and rejected both removed and fragment-owner binding
  mutations. The executable Kotlin test awaits the parent's final Gradle run.
- All loading-tip product edits are handed back to the parent. Remaining
  verification is coordinated Android resource/Java/Kotlin compilation, the five
  capability tests and new binding regression, existing page-refresh/prefetch
  regressions, and the complete parent Android gate with all lint reports checked.
  Final spec updates and task completion stay with the parent/checker. No Gradle,
  device, or live-network operation was started by this implementer.

## Final Integration — 2026-09-12

Combined review and Android validation are complete: 223 app JVM tests passed,
including all 18 loading-tip tests and the new retained-page binding regression.
Debug resource/Java/Kotlin assembly passed, and all 13 Android module lint XML
reports contain zero Error/Fatal. The parent's `research/combined-check-report.md`
and `research/final-validation.md` supersede the pending checks in the historical
handoff above and record the unrelated repository diagnostic fixture failures.

Added `.trellis/spec/frontend/loading-usage-tips-contract.md` and component/index
links. The current build has eight eligible tips; the AI entry remains conditional
on the actual bundled settings destination. Parent owns commit/archive/journal.
