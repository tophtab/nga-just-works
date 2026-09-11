# Research: Author-location enrichment following thread-page delivery

- Query: Find the smallest integration for enriching authors when an ordinary or prefetched thread page arrives, preserving page/account/view lifetimes and existing thread behavior.
- Scope: Internal source only; supersedes the earlier fixed current-plus-two location-window interpretation. Research only, with no product edits, Git commands, builds, traffic, devices, or sibling-worktree changes.
- Date: 2026-09-11

## Findings

### Recommendation and ownership seam

Use `ArticleListFragment.setData(ThreadData)` as the single new **page-enrichment trigger**. Both ordinary loads and successful background prefetch already deliver the complete page there. Extract that delivered page's eligible author IDs, immediately hand them to the shared location cache/request service, and render the thread without waiting for location completion.

Each page proceeds when its own data arrives. There is no location-specific page planner, current-plus-two eligibility window, three-page barrier, or new thread fetch. “About 60 authors” can only describe an illustrative outcome of existing pagination/prefetch; it is not a batch size or correctness invariant. Request timing/count policy remains owned by the parent design.

Recommended division:

- `ArticleListFragment`: immutable author-ID extraction on successful page delivery; page-view subscription/teardown; location-only updates to its existing adapter.
- Shared location service already planned by the task: UID cache reuse and in-flight deduplication across independently arriving pages; request/session validity and subscriber cancellation. It must not retain Fragment, View, adapter, or mutable `ThreadData` references.
- `ArticleTabFragment`: parent-screen lifetime boundary if a shared screen subscription needs one. No new page-range coordinator is necessary.
- `ArticleShareViewModel`: optional existing channel for immutable resolved-location updates; do not add a visited-page author registry or redefine its prefetch list merely for request scheduling.
- `ArticleListPresenter`, `ArticlePagePrefetchPlanner`, `ArticlePagerAdapter`: preserve existing delivery, request-state, page-selection, and prefetch behavior. The location feature does not need to modify them to discover authors.

### Files found and source anchors

Paths below are relative to the repository root. `app/` abbreviates `nga_phone_base_3.0/src/main/java/` only within this report.

| File / anchor | Existing role |
| --- | --- |
| `app/sp/phone/ui/fragment/ArticleListFragment.java:295` | Receives complete `ThreadData`; already publishes reply count and assigns data to the adapter at `:313`. This common seam is independent of which floor Views happen to be bound. |
| `app/sp/phone/http/bean/ThreadData.java:20` | `getRowList()` exposes the loaded page's rows. |
| `app/sp/phone/http/bean/ThreadRowInfo.java:112`, `:136` | Anonymous flag and author UID accessors; use the location task's eligibility rules over these rows. Do not scrape nickname text or enumerate arbitrary users mentioned in content. |
| `app/sp/phone/mvp/presenter/ArticleListPresenter.java:72` | Ordinary foreground success stores the page and calls `setData` at `:77`. |
| `app/sp/phone/mvp/presenter/ArticleListPresenter.java:113` | Background prefetch success also stores the page and calls `setData` at `:120`, regardless of whether the page became foreground. |
| `app/sp/phone/ui/fragment/ArticleListFragment.java:209` | Existing prefetch-candidate observer invokes the child presenter. Its guard at `:218` requires online, non-search children of `ArticleTabFragment`. |
| `app/sp/phone/ui/fragment/ArticleTabFragment.java:98`, `:127`, `:146` | Reply-count and page-selection changes drive the existing prefetch publication. |
| `app/sp/phone/mvp/viewmodel/ArticleShareViewModel.java:62` | Copies prefetch candidates into an immutable LiveData snapshot. It currently has no page-author inventory. |
| `app/sp/phone/mvp/viewmodel/ArticlePagePrefetchPlanner.java:17` | Existing next-two planner; `:25` excludes the known final page using `candidatePage < totalPages`. |
| `app/sp/phone/ui/adapter/ArticlePagerAdapter.java:32`, `:45`, `:73` | Only current child is RESUMED; each child has cloned page parameters; current Fragment pointer is maintained for explicit actions. No enumeration API is needed for enrichment. |
| `app/sp/phone/ui/fragment/ArticleTabFragment.java:123` | Retains two offscreen pages in the Pager; retained Views are not evidence of a fresh data delivery. |
| `app/sp/phone/ui/adapter/ArticleListAdapter.java:382`, `:430` | Stores page data and binds individual rows. These are presentation paths; `onBindViewHolder` must not start location requests. |

### Data flow and retained pages

```text
existing normal read / existing background prefetch
  -> existing successful ThreadData callback
  -> ArticleListFragment.setData(data)
       -> unchanged thread rendering
       -> copy eligible distinct author IDs from all delivered rows
       -> shared location cache / shared per-UID in-flight request
       -> valid page-view subscriber updates matching author labels
```

- Extract the entire delivered page, including rows below the viewport. Row binding, scroll events, `getCurrentFragment()`, and visible-position scans do not determine query demand.
- A successful prefetched page triggers enrichment while its child is offscreen. Do not require the child to be RESUMED: `BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT` intentionally leaves valid prefetch children STARTED.
- Retaining a previous page, revisiting ready data, rebinding holders, or replaying a resolved-location map must not independently initiate a new query pass. Existing `prefetchPage()` skips a presenter that already has `mThreadData` (`ArticleListPresenter.java:158`); normal ready-page reuse is owned by `ArticlePageRequestState`.
- A later **actual** refreshed page delivery can extract its new author set again. Shared cache/single-flight rules handle repeats; a newly appearing author is eligible without forcing other pages to reload.
- If an existing page request remains in flight after navigation and succeeds while its owner is still valid, its ordinary delivery can enrich that page. Do not cancel or discard it merely because it is no longer within a separately computed current-plus-two window: that would add a competing policy absent from the existing prefetch mechanism.
- The known final page is still excluded from automatic thread prefetch by the existing planner. When the user actually loads it, its normal successful delivery can enrich its authors. Do not duplicate the exclusion in a second location planner.
- Read rows only after parser success. A failed prefetch has no page-author delivery and must not synthesize a location request, Toast, account rotation, or new foreground action. Existing failure/promotion behavior stays at `ArticleListPresenter.java:169`.

### Lifecycle, account changes, and stale UI delivery

- Bind new UI observation to the **child view lifecycle**, not the Fragment or Activity lifetime. `BaseMvpFragment.java:22` attaches the presenter to the Fragment; `BasePresenter.java:48` detaches only at Fragment destruction, so `mBaseView != null` alone is insufficient proof that the old child view still exists.
- Give each new child view/subscription a generation or disposable identity. A result must still match its live view and the UID on the row being updated. On `onDestroyView`, remove observers/dispose that page's subscription; an old callback cannot attach itself to a replacement view.
- Parent-screen/view destruction releases that screen's subscriptions and prevents further UI delivery/new work for that dead owner. If a shared UID request also serves another live page/screen, release this consumer without cancelling the other consumer's result. Cancellation when the last consumer leaves remains part of the shared service's request policy.
- A prefetched child's `ON_PAUSE` is **not** parent-screen destruction. Existing `ArticleListPresenter.java:190` merely removes foreground promotion and leaves its underlying request alive. Keep that behavior; child visibility must not inadvertently cancel intended offscreen location prefetch.
- Lifecycle-aware observers naturally stop UI delivery below STARTED. Do not use `observeForever` or a process-global adapter listener. If the parent design explicitly closes work on screen stop, apply that to the parent screen, not to every child pause.
- For account/session change, invalidate old location subscriptions/results through the task's request-scope token. Compare immutable identity/session values, not only a mutable `User` object or numeric active index; re-login of the same UID can replace credentials. Do not add automatic next-account retry to the location service.
- Existing notification caveat: `lib_bu_account/src/main/java/com/justwent/androidnga/bu/UserManager.kt:49` exposes active-index LiveData and `:146` exposes user-list LiveData. `setActiveIndex()` publishes the index at `:58` **before** assigning `activeUser` at `:59`; account replacement can publish only the list (`:94`). Treat both signals as invalidation cues; do not synchronously capture a new session from `getActiveUser()` during the first index callback. Resolve the settled current session before new work. This is a narrow integration hazard, not a proposal to redesign/fix the account manager.
- Implementation-start review on 2026-09-12 also found that `removeUser()` calls
  `setActiveIndex()` before publishing its reduced user list. Removing A from
  `[A, B]` can therefore leave `getActiveUser()` pointing at A even after the
  callback turn, while the settled list/index select B. The supplemental
  operation must snapshot the settled list/index and copy that user's identity
  and credentials instead of trusting `getActiveUser()` alone. Test this as a
  local snapshot boundary; account-manager repair remains outside scope.
- Existing thread wire cancellation stays `FragmentEvent.DETACH` in `app/sp/phone/mvp/model/ArticleListModel.java:81`, `:101`. Location subscription teardown must not change that, the thread retry callbacks, or page request-state transitions.

### Meaningful offline tests

1. Deliver normal page authors `[A, B, A]`, then an offscreen-prefetch page `[B, C]`: all eligible loaded authors are covered, B is deduplicated across in-flight requests, and neither page waits for the other to arrive.
2. Deliver a page with many below-viewport rows: location demand comes from all eligible rows even if no holder was bound for most of them. Anonymous/invalid IDs remain ineligible under the location contract.
3. Retain/rebind/revisit ready page data without another successful page delivery: no new request trigger. Deliver a genuine refresh containing D: D is handled and cached/in-flight authors are reused.
4. Start a page request, navigate elsewhere, then complete the original request while its owner is alive: enrichment follows that existing completion; there is no location-window rejection. Destroy that page view before its location response: no stale adapter/View update.
5. Complete a valid offscreen prefetch while the child is STARTED: enrichment still occurs. A prefetch error produces no author enrichment and preserves the current foreground/background error behavior.
6. Destroy the parent view or invalidate the account/session: stale callbacks cannot update its UI or create more work. When two live consumers share a UID request, releasing one does not remove the other's result. Same-UID credential replacement is covered.
7. Preserve `ArticlePagePrefetchPlannerTest`, `ArticlePageRequestStateTest`, and `TopicPagePrefetchContractTest`: final-page exclusion, request promotion/reuse, silence on background failure, retained-page behavior, and DETACH transport binding remain unchanged.

Use fakes for page deliveries and the shared location service. No live thread/profile request, APK packaging, or device run is needed to verify this ownership/data-flow policy. This research pass did not execute tests.

### Related specs and references

- `.trellis/spec/backend/thread-page-prefetch-contract.md`: authoritative existing prefetch planner, promotion, retention, and lifetime contract.
- `.trellis/spec/backend/network-foundation-contract.md:168`: existing global Cookie lookup is compatibility behavior; new location-scope validity must not silently rewrite it.
- `.trellis/spec/backend/nga-platform-access-rules.md:250`: immutable account/session ownership for a new operation; no automatic identity rotation.
- `.trellis/spec/frontend/component-guidelines.md:505`: existing current-page refresh and retained reading behavior must remain intact.
- `.trellis/spec/backend/android-quality-guidelines.md`: offline checks and opt-in device policy.
- External references/versions: none required; analysis uses this checkout's legacy Android Views, FragmentStatePagerAdapter, LiveData, and RxLifecycle implementation.

## Caveats / Not Found

- The current code has no location repository or location subscriber to reuse; this report identifies the integration seam for the service being planned elsewhere, not a new framework.
- The original request for a separately filtered current-plus-two author window was superseded by the user's mechanism-driven clarification. Do not turn the earlier proposal into an implementation requirement.
- The shared `setData` method also serves cached/search readers. Preserve the task's explicit eligibility for those readers; do not silently add next-page prefetch to them. The existing online-pager guard is at `ArticleListFragment.java:218`.
- The account observables are not an atomic session-change API. This research flags only the stale-capture hazard; account-storage repairs, thread account handoff changes, and additional request-count/timing policies remain outside this subtask.
