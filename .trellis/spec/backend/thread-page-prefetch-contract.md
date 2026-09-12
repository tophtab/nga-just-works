# THREAD.PAGE Topic Pager Prefetch

This contract governs the current-activity, memory-only prefetch path for the
legacy Android topic pager. It changes when a normal online topic page may be
requested, while preserving the ordinary `THREAD.PAGE` wire fields. The
[compatibility reader](./thread-detail-compat-contract.md) adds explicit
query/source/layout generations; automatic prefetch remains ordinary-only.

## 1. Scope / Trigger

Use this contract when changing `ArticleTabFragment`, `ArticleListFragment`,
`ArticleShareViewModel`, `ArticleListPresenter`, or page-retention behavior for
normal online topic reading.

The prefetch path is not used by `ArticleSearchFragment` or
`ArticleCacheActivity`. This contract does not authorize live NGA requests,
ADB, installation, or instrumentation.

## 2. Signatures

```java
ArticlePagePrefetchPlanner.plan(int currentPage, int totalPages)
ArticleShareViewModel.setPrefetchPages(List<Integer> pages)
LiveData<List<Integer>> ArticleShareViewModel.getPrefetchPages()
ArticleReaderSession ArticleShareViewModel.initializeReader(ArticleListParam param)
LiveData<ArticleReaderState> ArticleShareViewModel.getReaderState()
int ArticleShareViewModel.adoptPage(ArticleRequestKey key, ThreadData data, boolean foreground)
void ArticleListContract.Presenter.prefetchPage()
void ArticlePageRequestState.reset()
ArticlePageRequestState.ForegroundLoadDecision requestForegroundLoad(boolean explicitRefresh)
```

`ForegroundLoadDecision` distinguishes `START`, `WAIT_FOR_PREFETCH`,
`SHOW_READY_DATA` and `NONE`. Only `SHOW_READY_DATA` permits redisplaying the
retained response through the success path; `NONE` means a foreground request
is still active and its loading state must be retained.

Each `ArticleListPresenter` also owns an Android-free page request state with
these states:

```text
IDLE -> PREFETCHING -> READY
IDLE/READY -> FOREGROUND_LOADING -> READY or IDLE
```

An in-flight prefetch may be marked as promoted while its page is foreground.
`ON_PAUSE` must remove that promotion without cancelling the underlying
request.

## 3. Contracts

- Page numbers are 1-based. The planner considers only `currentPage + 1` and
  `currentPage + 2`, and returns a candidate only when
  `candidatePage < totalPages`. The known final page is never prefetched.
- Candidate lists are new immutable snapshots published from the
  activity-scoped `ArticleShareViewModel`. Replan both when the selected page
  changes and when an accepted `ArticleReaderState.paging.totalPages` changes.
  Ordinary parsing derives that count from its normal page contract; the UI
  must not reinterpret App or filtered `__ROWS` with a hardcoded divisor.
- The normal topic `ViewPager` retains two offscreen pages. A child observes
  candidates only when it is an online full-query child of
  `ArticleTabFragment`, its generation matches the reader, its source is
  `READ_PHP`, and total pages are known. PID, author-filtered, cached, App and
  unknown-total windows do not publish or consume prefetch candidates.
- Prefetch calls the ordinary `ArticleListModel.loadPage(...)` entry. With
  compatibility off this is the legacy Retrofit path. With it on, the
  `ArticleOperation` overload uses the same scoped ordinary byte/parser path
  as a foreground ordinary read, with an account/origin snapshot. Preserve
  `/read.php` fields, normal conversion and `FragmentEvent.DETACH` cancellation;
  never route a prefetch to `loadScopedPage` with `APP_API`.
- `ArticleRequestKey` binds query, source, page size, owner, generation and page;
  a presenter also checks its request sequence and current account/settings.
  An environment or layout change invalidates READY/in-flight reuse and clears
  candidates before the new generation publishes work. An offscreen result
  cannot adopt a changed source, page size or effective page for the reader.
- A background prefetch does not start refresh UI and its failure does not
  show a Toast, open WebView, rotate accounts, or affect the visible page.
- Entering a page during prefetch promotes the same request instead of starting
  a duplicate. Success displays that result. Failure starts the existing
  foreground load and error chain only while the page remains foreground.
- Leaving a promoted page before the prefetch completes clears the promotion
  and refresh indicator. A later failure is background-only and returns the
  page to idle.
- A successful prefetched page skips automatic foreground loading. An explicit
  refresh from `READY` always starts a foreground request for the reader's
  current source. An explicit
  refresh during `PREFETCHING` coalesces with the same in-flight request; if it
  fails while foreground, the normal foreground fallback begins.
- READY delivery on resume must not force a full body rebind when that response
  and its topic-owner metadata are already displayed in the surviving page
  view. Keep the render decision
  in the view owner so a recreated view still restores retained data. Preserve
  foreground metadata/navigation and normal completion UI; see the
  [retained article entry contract](../frontend/component-guidelines.md#retained-article-page-entry).
- A repeated load/resume while `FOREGROUND_LOADING` leaves the request and its
  refresh indicator active. A `NONE` load decision is not proof that retained
  data is ready: redisplaying it through the success path would move the state
  to `READY` and allow a duplicate request before the current request finishes.
  Reuse retained data only when the state actually permits ready-data reuse.
- Prefetched data lives only in the page Fragment/presenter inside the current
  topic Activity. Do not persist it or share it across topics or activities.
- Main disables automatic author-location queries. Normal page delivery and
  offscreen prefetch must not start supplemental `USER.PROFILE` requests.
  The separate `experiment/auto-ip-query` branch retains that enrichment; see
  the [author-location contract](./author-profile-location-contract.md).
  This separation does not change page selection, retention, or loading tips.

## 4. Validation & Error Matrix

| Condition | Required result |
| --- | --- |
| Current 3, total 6 | Candidates `[4, 5]` |
| Current 3, total 5 | Candidate `[4]`; page 5 is not requested |
| Current 3, total 4 | No candidates |
| Invalid page or total | Empty candidate list |
| Candidate event reaches cache/search/non-pager page | No prefetch request |
| Candidate reaches author/App/unknown-total/old-generation page | No prefetch request |
| Same page is already prefetching or loading | No duplicate request |
| Old data remains during refresh; another load/resume arrives | Keep foreground loading; do not mark the old data ready |
| Background prefetch succeeds | Store/render in that offscreen page and enter `READY` |
| Enter a READY page with unchanged response/owner in its surviving view | Reuse body content; preserve foreground metadata/navigation |
| Recreate a READY page's view | Bind retained data into the new adapter |
| Background prefetch fails | Return to `IDLE`; no user-facing side effect |
| Page enters during prefetch | Wait for and promote the same request |
| Promoted prefetch fails while foreground | Start the existing foreground load/error chain |
| Promoted page pauses before completion | Clear promotion; later failure stays silent |
| Explicit refresh from ready ordinary data | Start an ordinary foreground request |
| Explicit refresh after adopting App source | Start a foreground App request; no prefetch |
| Environment or source/layout changes | Clear candidates and retire old READY/in-flight identities |
| Offscreen response reports a different layout/page | Reject adoption; no reader-wide source change |
| Fragment detaches | Existing RxLifecycle binding cancels the request |

## 5. Good / Base / Bad Cases

- **Good**: page 3 of 6 publishes immutable candidates 4 and 5; both use the
  existing model path, and opening page 4 while its request is running reuses
  that request.
- **Base**: page 3 of 4 publishes no candidates. Opening page 4 performs the
  ordinary foreground load and receives the newest known replies.
- **Bad**: prefetch page 5 of 5, duplicate the ordinary model/parser path, let a
  background failure rotate accounts or open WebView, or retain foreground
  promotion after `ON_PAUSE`.

## 6. Tests Required

- Pure JVM planner tests must cover `3/6`, `3/5`, `3/4`, first/penultimate/final
  pages, invalid inputs, overflow, immutability, and the invariant
  `currentPage < candidatePage <= currentPage + 2 && candidatePage < totalPages`.
- Pure request-state tests must cover duplicate suppression, successful reuse,
  background failure, foreground promotion/fallback, pause demotion, ready-data
  refresh, explicit refresh coalescing during prefetch, and reset on identity
  retirement. Reader tests must reject old keys after source/page-size/account
  changes and ensure offscreen results cannot change the layout.
  Include ready data → explicit refresh → repeated automatic/explicit loads;
  old retained data cannot complete the refresh or unlock another request.
- Source-contract tests must pin offscreen limit 2, both replanning triggers,
  immutable LiveData publication, online pager guards, silent prefetch failure,
  foreground retry/WebView separation, ordinary model wire/parser/DETACH
  anchors and the explicit enabled/scoped transport boundary. Tests should
  exercise identity invalidation and side-effect budgets rather than merely
  asserting that a helper name appears in the source.
- Run `:nga_phone_base_3.0:testDebugUnitTest`,
  `:nga_phone_base_3.0:assembleDebug`, and
  `:nga_phone_base_3.0:lintDebug`; inspect the lint report instead of relying
  only on its process exit. Device tests remain opt-in under the Android
  quality contract.

## 7. Wrong vs Correct

### Wrong

```java
for (int page = currentPage + 1; page <= currentPage + 2; page++) {
    if (page <= totalPages) {
        loadPageWithNewRetrofitCall(page);
    }
}
```

This includes the known final page and duplicates the request/parser/error
stack.

### Correct

```java
List<Integer> pages = ArticlePagePrefetchPlanner.plan(currentPage, totalPages);
// Each matching online pager child calls its existing presenter/model path.
```

The planner enforces strict final-page exclusion, while the presenter state
machine keeps one request per page and separates background from foreground
failure behavior.
