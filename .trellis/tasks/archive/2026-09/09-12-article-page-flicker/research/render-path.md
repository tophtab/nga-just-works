# Retained page rendering investigation

## Evidence

- `ArticlePagerAdapter` uses `BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT`; selecting
  a retained child pauses/resumes it without recreating its view.
- `ArticleListPresenter.onResume` calls `requestForegroundLoad(false)`.
  `ArticlePageRequestState` returns `SHOW_READY_DATA` for READY, which calls
  `showData(mThreadData)` and `ArticleListFragment.setData(data)`. Cached pages
  also redeliver retained data on resume.
- `ArticleListFragment.setData` (around line 466) validates identity, updates
  AI ownership, stores `mDeliveredData`, and calls `renderData` when a view
  exists. `renderData` always calls adapter `setData` and
  `notifyDataSetChanged`, even for the identical `mDisplayedData` response.
- `ArticleListAdapter.onBindContentView` (around line 548) calls
  `loadDataWithBaseURL` on every full bind, even with a retained WebView.
  This reloads unchanged HTML on page resume.
- Prefetch success renders offscreen content; entry rebinds it. Background
  completion must continue to render normally.
- `onDestroyView` releases WebViews, detaches the adapter and clears
  `mDisplayedData`. `onViewCreated` creates a fresh adapter and restores current
  `mDeliveredData`. Preserve this view-lifetime reset.
- `renderData` also updates the activity title, topic-owner metadata, options
  menu and pending PID/floor anchor. `onResume` already consumes pending anchors
  and invalidates options, but does not set the title. Do not lose foreground
  effects when avoiding body binding.
- Presenter row actions do not currently redeliver mutated `mThreadData`.
  Any accepted new response must render, even for the same page number.
- `banThisSB` mutates the clicked row's blacklist flag in place. Its nickname
  badge previously updated as an incidental result of a later resume rebind.
  The fix must explicitly notify that row after a real flag change, resolving
  its index against current displayed data so a stale menu cannot target a
  replacement row. This keeps the change within the same Fragment.

## Change boundary

The gap is unnecessary body binding during READY-data reuse. That decision
belongs to the fragment's current view and displayed response, not the request
state: dropping READY delivery in the presenter confuses view restoration
with retained-view reuse.

Expected product edit: `ArticleListFragment.java`, unless concrete evidence
requires a small supporting change. Keep foreground metadata/navigation
independent from body binding. No WebView cache rewrite, pager animation or
request-state redesign, or new public API is needed. Main owns spec/task edits.

The implementation review found one additional rendering input: the shared
topic-owner string can first become known when the user visits page 1 after
entering a later page. `setTopicOwner` only assigns the adapter field, and OP
badges update during full row binding. Therefore the guard compares both
response identity and the last displayed owner value. A changed owner allows
the existing full bind once; unchanged repeated entry skips it. This preserves
OP badges without expanding the fix into adapter payload changes.

## Validation

Use existing Android-free request/reader tests and appropriate UI contracts.
Regression coverage should protect side-effect boundaries and view-lifetime
restoration, rather than merely assert a helper name. Do not add Robolectric,
runtime stubs, or a production abstraction solely to test a guard.

The full gate is app Debug assembly, all Debug unit tests, and all-module Debug
lint rerun with XML inspection for zero Error/Fatal issues. No ADB or live NGA
requests. Source/JVM checks do not prove device-visible smoothness.

## Workspace boundary

Pre-existing edits: `AiSummaryClient.java`, `AiModelsClientTest.java`,
`.trellis/spec/backend/ai-summary-contract.md`, and two unrelated September 12
task directories. Preserve them and exclude them from this task's commits.

During final checks, the parallel `merge-ip-query-release-6-0-0` task restored
automatic author-location enrichment and switched the shared checkout back to
main. It owns those staged edits and delivery replay semantics in
`AuthorLocationService.Page`; preserve them. This task still owns only its
unstaged render guard/blacklist-row change and retained-entry spec additions.
See `validation.md` for the required integrated recheck.
