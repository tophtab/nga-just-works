# THREAD.PAGE Topic Pager Prefetch

This contract governs the current-activity, memory-only prefetch path for the
legacy Android topic pager. It changes when a normal online topic page may be
requested, but it does not change the `THREAD.PAGE` wire contract.

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
void ArticleListContract.Presenter.prefetchPage()
```

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
  changes and when the latest `ThreadData.__ROWS` changes the total page count.
- The normal topic `ViewPager` retains two offscreen pages. A child observes
  candidates only when it is an online, non-search child of
  `ArticleTabFragment`.
- Prefetch must call the existing `ArticleListModel.loadPage(...)` path. Do not
  copy or alter the `/read.php` URL, Cookie/header behavior,
  `ArticleConvertFactory`, account-aware foreground retry, or
  `FragmentEvent.DETACH` cancellation.
- A background prefetch does not start refresh UI and its failure does not
  show a Toast, open WebView, rotate accounts, or affect the visible page.
- Entering a page during prefetch promotes the same request instead of starting
  a duplicate. Success displays that result. Failure starts the existing
  foreground load and error chain only while the page remains foreground.
- Leaving a promoted page before the prefetch completes clears the promotion
  and refresh indicator. A later failure is background-only and returns the
  page to idle.
- A successful prefetched page skips automatic foreground loading. An explicit
  refresh from `READY` always starts the normal foreground request. An explicit
  refresh during `PREFETCHING` coalesces with the same in-flight request; if it
  fails while foreground, the normal foreground fallback begins.
- Prefetched data lives only in the page Fragment/presenter inside the current
  topic Activity. Do not persist it or share it across topics or activities.
- Author location is separate enrichment of each valid online page delivery,
  including offscreen prefetch. Its independent persistent cache does not
  persist thread pages or add a page-selection window. Follow the
  [author-location contract](./author-profile-location-contract.md); loading tips
  require foreground visibility, while this enrichment does not.

## 4. Validation & Error Matrix

| Condition | Required result |
| --- | --- |
| Current 3, total 6 | Candidates `[4, 5]` |
| Current 3, total 5 | Candidate `[4]`; page 5 is not requested |
| Current 3, total 4 | No candidates |
| Invalid page or total | Empty candidate list |
| Candidate event reaches cache/search/non-pager page | No prefetch request |
| Same page is already prefetching or loading | No duplicate request |
| Background prefetch succeeds | Store/render in that offscreen page and enter `READY` |
| Background prefetch fails | Return to `IDLE`; no user-facing side effect |
| Page enters during prefetch | Wait for and promote the same request |
| Promoted prefetch fails while foreground | Start the existing foreground load/error chain |
| Promoted page pauses before completion | Clear promotion; later failure stays silent |
| Explicit refresh from ready data | Start a normal foreground request |
| Fragment detaches | Existing RxLifecycle binding cancels the request |

## 5. Good / Base / Bad Cases

- **Good**: page 3 of 6 publishes immutable candidates 4 and 5; both use the
  existing model path, and opening page 4 while its request is running reuses
  that request.
- **Base**: page 3 of 4 publishes no candidates. Opening page 4 performs the
  ordinary foreground load and receives the newest known replies.
- **Bad**: prefetch page 5 of 5, create a second Retrofit/parser path, let a
  background failure rotate accounts or open WebView, or retain foreground
  promotion after `ON_PAUSE`.

## 6. Tests Required

- Pure JVM planner tests must cover `3/6`, `3/5`, `3/4`, first/penultimate/final
  pages, invalid inputs, overflow, immutability, and the invariant
  `currentPage < candidatePage <= currentPage + 2 && candidatePage < totalPages`.
- Pure request-state tests must cover duplicate suppression, successful reuse,
  background failure, foreground promotion/fallback, pause demotion, ready-data
  refresh, and explicit refresh coalescing during prefetch.
- Source-contract tests must pin offscreen limit 2, both replanning triggers,
  immutable LiveData publication, online pager guards, silent prefetch failure,
  foreground retry/WebView separation, and unchanged model wire/parser/DETACH
  anchors.
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
