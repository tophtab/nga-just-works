# Research: Expanded query and pagination adaptation

- Query: Replace the full-thread/20-row-only admission rule with a concrete adaptation of upstream `tid`, `pid`, `authorid`, and variable pagination, while preserving this fork's reader, foreground/prefetch behavior, and repaired cache path.
- Scope: internal source plus the already supplied upstream snapshot; planning only. This topic does not repeat the August audit or select business success codes.
- Date: 2026-09-11
- Baselines: parent supplied current fork `5bb92cf0` and upstream `22ba3082501bcbb08f52a66d787f970f59c2dda7`; feature source is pinned in `research/upstream-source.md` at `2becba2acc3f6c85340424cd09bb03fa7d759db0`.
- Status: this recommendation supersedes the **scope/admission and cache-page-union recommendations** in `pagination-cache-adaptation.md`. Its source inventory and existing cache invariants remain useful.

## Findings

### 1. Decisive recommendation

Adopt upstream's query fields and row mapping for all three useful reading contexts. Add one normalized paging/context value to the existing native model and one activity-scoped paging generation; do not replace the renderer or import upstream's entire data-model refactor.

| Context | Native behavior | Pagination meaning | Cache / prefetch |
| --- | --- | --- | --- |
| Full thread: positive `tid`, no PID, author filter, or reply-search disposition | Render the complete parsed response, preserving server order and original `lou` / PID. Short pages and floor gaps are not automatically malformed. | Use response `totalPage`; use positive `perPage` when supplied, including 10/30/40. Keep page number separate from list index and global floor. | Full-page saving remains supported, with format/page-layout identity. Only the ordinary source participates in background prefetch. |
| Only an author: `authorid != 0`, no PID | Preserve the filter in every request and render that query's pages. | These are **filtered query pages**. An original floor of 85 may be the first displayed item. Do not divide that floor by the filtered page size or use filtered totals as a global floor limit. | No full-thread cache. No automatic App prefetch; keep this query outside the full-thread prefetch candidate path. |
| PID / reply lookup: `pid != 0`, optionally with `tid` and `authorid`; or the existing reply-search screen | Preserve all supplied query fields. Require the requested PID to be present before claiming that it was located. Display a native scoped lookup and focus that actual row; returned surrounding rows may remain visible as context. | A PID response may be one row or a containing window. Its counters are not evidence that it is a complete unfiltered page. | No full-thread cache or prefetch. Offer `显示全部` using the resolved thread ID. |

Missing or inconsistent **optional paging metadata** should reduce navigation capabilities rather than discard otherwise validated post bodies. Invalid identity, an error response, or missing requested PID remains a real query failure. This is not permission to turn arbitrary JSON into success: content/error classification belongs to the companion parser/network design.

Do not implement only “allow perPage != 20” in the converter. The current pager, floor jump, outgoing quote metadata, prefetch reuse, and disk cache all need the small explicit changes below.

### 2. Files and source patterns

All paths are repository-relative. `app/` below expands to `nga_phone_base_3.0/src/main/java/`. `U:` expands to `/tmp/nga-upstream-august-2026-review/upstream-22ba3082/` plus the original repository path. No Git operation was needed to inspect this snapshot.

| File / line | Current pattern and implication |
| --- | --- |
| `app/sp/phone/param/ArticleListParam.java:12`, `:34`, `:88`, `:103` | Carries tid, PID, authorId, page, searchPost, title/content/topicInfo/loadCache; Parcelable and clone preserve them. There is no source, normalized pagination, or resolved identity. `equals` includes content by reference and omits cache/source/account, so it is not a suitable new request/cache key. |
| `app/gov/anzong/androidnga/activity/ArticleListActivity.java:32`, `:52`, `:59` | Only `searchPost` currently chooses `ArticleSearchFragment`; a raw PID link without that flag enters the ordinary pager. URL input reads `page`; the fallback Bundle path does not. Initial page intent must be normalized once and actually applied. |
| `app/sp/phone/ui/fragment/TopicSearchFragment.java:274` | Reply-search entries provide tid/page/title plus PID, authorId, and searchPost. This demonstrates why PID plus author is a real combination, not a reason to drop one field. |
| `app/sp/phone/mvp/model/ArticleListModel.java:49` | Existing `/read.php` construction sends page and optional tid/PID/authorid. It does **not** send searchPost. |
| `U:nga_phone_base_3.0/src/main/java/sp/phone/mvp/model/ArticleListModel.java:80` | App path is POST `/app_api.php?__lib=post&__act=list`; form has page and optional tid/PID/authorid. It likewise does not send searchPost or perPage. This proves request construction, not current server semantics. |
| `U:lib_core_data/src/main/java/com/client/androidnga/core/data/bean/ThreadAppBean.kt:11`, `:27`, `:37`, `:41`, `:100` | DTO declares currentPage/perPage/totalPage/vrows and per-row tid/PID/lou/author. Several numeric fields have zero defaults; a local adapter must retain absence separately from explicit zero. |
| `U:lib_core/src/main/java/com/client/androidnga/core/parse/ThreadInfoAppParse.kt:29`, `:43`, `:90` | Parser copies vrows into totalRows and maps rows unchanged; it ignores all three paging fields and gets tid via unchecked `result[0]`. It does not implement PID/author response validation. |
| `app/sp/phone/http/bean/ThreadData.java:10`, `:36`, `:44` | `__ROWS` and displayed rowNum are already different. Add normalized metadata here; do not repurpose either field into a manufactured count. |
| `app/sp/phone/mvp/model/entity/ThreadPageInfo.java:19`, `:29`, `:100`, `:140` | This is topic description metadata. Its replies/page fields are not the reader's paging authority. Reusing it as response pagination would conflict with topic-list and cache-description uses. |
| `app/sp/phone/ui/fragment/ArticleListFragment.java:295` | Every delivered page publishes `data.__ROWS` globally. This must become a context-checked paging update so an old ordinary prefetch cannot overwrite a new App layout. |
| `app/sp/phone/mvp/viewmodel/ArticleShareViewModel.java:16`, `:24`, `:62` | The activity currently shares scalar reply count and immutable integer prefetch candidates. This is the minimal owner for current query/source/layout/generation, not a process-global cache. |
| `app/sp/phone/ui/fragment/ArticleTabFragment.java:98`, `:121`, `:146` | Page count is `ceil(__ROWS / 20.0f)`. Adapter initially has one page; the fragment never applies the launch page. Offscreen limit is 2; preserve it. |
| `app/sp/phone/ui/adapter/ArticlePagerAdapter.java:45`, `:60`, `:67` | Online page is position+1; cache page comes from actual sparse page numbers. Changing count alone does not invalidate already retained fragments with a different source/query layout. |
| `app/sp/phone/ui/fragment/ArticleTabFragment.java:303`, `:331`; `app/sp/phone/ui/fragment/ArticleListFragment.java:225` | Jump-to-floor sends `(floor / 20, floor % 20)` and immediately scrolls to that list index. This fails for filtered results, deleted rows, and a target child that has not loaded yet. |
| `app/sp/phone/ui/fragment/dialog/GotoDialogFragment.java:35`, `:82`, `:88` | Dialog assumes a known page count and floors `0..rowCount-1`. It needs separate page/floor capabilities instead of treating any positive count as both. |
| `app/sp/phone/ui/adapter/ArticleListAdapter.java:198`, `:456`, `:532` | Outgoing quote has an independent `(lou + 20) / 20` calculation; floor label uses server lou and displayed count uses rowNum. Preserve the latter two. |
| `app/sp/phone/mvp/presenter/ArticleListPresenter.java:263`, `:334` | Other quote/comment builders use `param.page`. In an author/App page this may not be the original unfiltered `/read.php` page. |
| `lib_core/src/main/java/gov/anzong/androidnga/core/decode/ForumBasicDecoder.java:52`, `:57`, `:130` | Rendered `[pid=pid,tid,page]` links use tid+PID+searchpost=1 and deliberately do not consume the third page field. Simple PID links also exist. Native reply navigation can use PID identity without importing a foreign page number. |
| `app/sp/phone/ui/fragment/ArticleSearchFragment.java:39` | `显示全部` currently copies original request tid and title. A PID-only request leaves tid=0 unless the successful result's resolved identity is used. |
| `app/sp/phone/ui/fragment/ArticleListFragment.java:209`, `:218`; `app/sp/phone/mvp/presenter/ArticleListPresenter.java:100`, `:157` | Current prefetch guard excludes cache/search but not PID/author. Presenter stores READY data without an explicit source/query key. New keyed checks belong before changing presenter state or data, not only in the ViewModel observer. |
| `app/sp/phone/mvp/presenter/ArticlePageCache.java:16`, `:26`, `:53` | Existing full-context eligibility, Unicode blank handling, title validation, supplied metadata preservation, and selected-child clone are required. Loaded response page metadata must not be confused with `ThreadPageInfo.page`; existing tests intentionally distinguish them. |
| `app/sp/phone/mvp/model/ArticleListModel.java:117`, `:139` | Current raw writer/reader has only tid/page location and always uses the ordinary parser. Format dispatch and the new cache layout handle must reach both methods. |
| `app/sp/phone/mvp/model/TopicListModel.java:66`, `:196`; `app/sp/phone/ui/fragment/TopicCacheFragment.java:89` | Cache listing, open, and delete currently identify a cached thread only by tid. Multiple page layouts need a bounded cache-entry handle through these consumers. |
| `app/gov/anzong/androidnga/activity/ArticleCacheActivity.java:51`, `:59`, `:64` | Retains actual sparse page labels and repaired equal-width tabs based on available page count. It currently enumerates a tid directory and string-sorts filenames; use the shared cache resolver and numeric page order. |

`app/sp/phone/http/bean/ArticlePage.java` has an old untyped `page` map, but the source search found no reader consumer. It is not an existing typed-pagination implementation to adopt.

### 3. What upstream proves, and what the adapter decides locally

**Source-observed:** both endpoints accept client construction with optional tid/PID/authorid and page; App DTO includes the named paging fields and original per-row identities. Existing reply-search UI and decoder use PID to open replies. Upstream App code does not implement a separate incompatible-query exclusion.

**Not established:** whether every live App PID response is a singleton or containing page; whether author results include the opener as context; whether author vrows counts the whole thread or the filtered set; whether App currentPage in a PID response is a full-thread page; omitted-field behavior; deleted/sticky row accounting; or a stable numeric business success code. There is no independent real App fixture in the supplied research. The DTO is not a response fixture.

The following are deliberate local behaviors: server page numbers are consumed as page coordinates for the request that produced them; a scoped lookup is displayed natively when it contains the requested identity; an unknown page size reduces floor arithmetic, not reading; query/source layout changes invalidate reuse. None is a claim that a live site currently honors every request.

### 4. Minimal normalized model

Use one immutable app-level value associated with `ThreadData` (example name `ArticlePagingInfo`) plus a request snapshot. Suggested conceptual fields:

```text
requestKey: readerGeneration, ownerKey, queryKind, requestedTid, pid,
            authorId, searchPostDisposition, source, requestedPage
resolvedTid: positive thread ID established from valid primary rows
effectivePage: page for this query; pageBasis = REPORTED | REQUESTED | LOOKUP_WINDOW
reportedCurrentPage / perPage / totalPage / vrows: nullable wire values
effectivePageSize / effectiveTotalPages: nullable normalized values
countScope: FULL_QUERY | FILTERED_QUERY | LOOKUP | UNKNOWN
canEstimateFloorPage: capability, not an assumption implicit in nonnull perPage
```

The implementation can combine these fields into fewer classes; do not grow a second parallel thread-model hierarchy. Keep `ThreadData.rowNum == rendered rowList.size()` and retain raw vrows/`__ROWS` faithfully where appropriate. Remove `__ROWS / 20` as the UI authority. Do not manufacture `vrows + 1` or multiply vrows to make an old pager appear correct.

Normalization rules:

1. **Explicit, positive totalPage wins for source-page navigation.** It is not required to equal `ceil(vrows / perPage)` when the meaning of vrows is uncertain. Counts can disagree because they describe different things; do not hide all content solely for that reason.
2. Positive perPage is the source page size; do not send a new perPage request parameter, clamp to 20, split larger responses, or concatenate network pages into artificial 20-row pages.
3. If totalPage is absent, a full unfiltered response with positive perPage and positive vrows may use integer `ceil(vrows / perPage)` as the documented source-derived local fallback, because upstream already uses vrows as its totalRows. Record the count basis. Do not apply that fallback to author/PID results without independent count-scope evidence.
4. If perPage is absent, explicit totalPage still supports ordinary page navigation. Keep page size unknown. Do not infer size from `result.size()` (a short last page, missing posts, or a filtered result would give a false size).
5. Full/author page requests with no reported currentPage can retain requestedPage as their coordinate, with `pageBasis=REQUESTED`. This is the same explicit request-based convention used by the existing normal reader, not proof that the server echoed it. A PID lookup with absent currentPage is a lookup window, not an asserted full page.
6. A reported currentPage different from requestedPage must be reconciled at the pager owner. Display/relabel it at that actual source coordinate or keep it as a scoped window; never save or publish it beneath the old page label. Validate the outstanding query/generation before any reconciliation.
7. Explicit nonpositive/overflowing paging fields or internally impossible page bounds are unusable metadata, not valid zero-page success. An otherwise valid body can remain a current-window result with paging actions disabled and a concise explanation. Preserve the bad/missing distinction internally for tests. Do not clamp an invalid page count to an apparently valid total of one.
8. With no usable page total and no justified derivation, render the available response as an **unknown-total window**, retaining its actual/requested page coordinate. A one-entry adapter means one available window, not “this thread has one page.” Hide arbitrary-page/floor pickers and App prefetch. Use the existing explicit browser path for broader navigation; do not automatically scan pages to discover a total. A separate user-driven unknown-total next-page UI is optional follow-up, not required to accept the body.
9. Do not require exactly perPage rows or contiguous lou for reading. Keep primary row identities and ordering, and let recognized comments remain associated with their parent. Mixed primary thread IDs or a supplied positive tid that disagrees with resolvedTid are identity failures, not paging degradation.

For legacy normal results, the model wrapper supplies source `READ_PHP`, pageSize=20, the current `__ROWS`-based page calculation using widened integer arithmetic, and the unchanged request identity. The ordinary parser/renderer need not be rewritten just to carry this metadata. Legacy cache replay gets the same explicit legacy layout.

### 5. PID, author, and searchPost contracts

#### PID and reply windows

- Keep requestedTid/PID/authorId unchanged in the network snapshot. For PID-only input, leave requestedTid=0 in that snapshot and compute a separate resolvedTid only after validating the matching target row. Never fabricate tid from the PID or assign the first arbitrary row's tid.
- Require the requested nonzero PID among the accepted primary rows; reject a response that only happens to return other posts from the requested tid. Validate all primary rows against one resolved thread. Scope any optional author restriction consistently; do not erase it on retry/fallback.
- A valid single row with lou=173 is immediately useful. Render it with floor label 173 and list index 0; perPage, vrows, and a dense 0-based page are not prerequisites for this view.
- A containing window can also be shown natively with the matching PID focused by actual list lookup. Its presence does not make a PID query cacheable as a complete full-thread page.
- Prefer routing PID entries to the existing scoped `ArticleSearchFragment` presentation (including raw PID links that currently enter the ordinary pager), with a `显示全部` action. This is the smallest coherent fix for repeated `pid` being carried into every ordinary pager child. If preserving the full-pager shell is chosen instead, it must explicitly represent a lookup window and must not generate children by PID+page automatically.
- `显示全部` creates **new** parameters: `tid = resolvedTid` (or the already validated requested tid), `pid=0`, `authorId=0`, `searchPost=0`, `page=1`, `loadCache=false`, current usable title. Preserve valid same-thread topicInfo only if already present; do not require it. Start a new paging generation. Do not repurpose the mutable lookup request or treat a PID response as the new full-thread page.
- The minimal selected behavior starts the full thread at page 1, matching the existing action. If later adding “show all at this reply,” carry a PID/floor anchor and resolve it against the **full-query** layout. PID response currentPage/perPage is not sufficient evidence of that layout.

#### Only an author

- Carry authorId on every initial request, selected page, refresh, and App fallback. Query identity includes it even when a page's rows happen to match an unfiltered page.
- Use totalPage as this query's page count. Preserve original lou without requiring contiguity; never replace original floor 85 with display index 0 or reinterpret vrows as maximum original floor.
- “Go to page” remains available when this query has usable page metadata. Arbitrary global floor arithmetic is disabled for the filtered context. Existing PID links still open the exact reply normally.
- A payload that clearly contains an unfiltered primary result despite the author restriction must not be presented as verified “only this person.” The parser should distinguish known context rows (if evidence supports them) from primary filtered rows. Do not invent an opener-exception protocol from the DTO alone. Failure to prove such a special-case row does not invalidate the general author-query implementation.
- Add/use `显示全部` for an author-filtered view to clear authorId and other scoped fields through the same fresh full-thread navigation helper. Its state must not leak into the full-thread cache/preload context.

#### searchPost

`searchPost` is currently a local presentation/routing marker in this reader; neither inspected request builder sends it. It is retained in request identity and cache eligibility. Do not invent an App `searchpost` field as part of this feature.

Known entries from `TopicSearchFragment` already identify a reply by PID and optional author; these can reuse upstream App fields without blanket rejection. A search disposition with no usable tid/PID is not a valid invented lookup. A search disposition with tid but PID=0 may represent the opener; treat it as a scoped result until its intent/rows are established, not as an excuse to save it as a full page.

### 6. Page navigation, floor navigation, and outgoing quotes

Use one page-coordinate helper rather than independent arithmetic in UI classes.

- Apply initial page selection after count/available-page coordinates are known. Preserve requested deep-link/history page as a pending source-page target; do not let the adapter silently overwrite it with page 1. Full/author page<=0 normalizes once to 1 for online requests.
- Page selection is always a source-query page. `ArticlePagerAdapter` should carry a request snapshot plus generation and actual page number; its sparse-page capability can serve cache/current-window cases. Tab width still depends on the number of available tabs.
- Replace the go-floor Rx payload's adapter index with a typed/persisted pending anchor `{generation, targetPage?, targetPid?, targetLou?}` in the activity state. The target child consumes it only after its matching data arrives. This also prevents losing an event sent before a new fragment registers its observer.
- Resolve a loaded anchor by actual row PID first, or actual lou when PID is unavailable. Only then scroll to the matching adapter index. Comments or floor gaps must not shift that result. If the target is absent, state that it was not found; never scroll to `floor % pageSize` and claim success.
- For an unfiltered layout with positive pageSize and a justified ordinary floor-window mapping, `floor / pageSize + 1` is a **candidate** page. Fetch it as a foreground navigation and verify the actual row. A sparse/out-of-range response can disable that arithmetic capability without losing page reading. No invented `floor` request parameter or network page scan.
- For unknown page size or filtered/PID query scope, global floor-to-page arithmetic is unavailable. A loaded-page anchor lookup can still succeed; page navigation and PID links remain useful. The goto dialog needs independent page/floor availability and a known global floor bound, not filtered vrows.

**Outgoing quote page hint must not become an App page number.** Existing `[pid=pid,tid,page]` is posted content that other normal/web clients may consume. The native decoder already navigates by PID independently of that field. Keep the existing legacy full-thread hint calculation (20-floor ordinary convention, using safe arithmetic) isolated in a quote-address helper for rows with usable original lou; make the Presenter builders use that helper instead of `param.page`. Do not use filtered page 1 or App page 18 as if it were ordinary page 9. For missing floor metadata, an existing PID-only tag/link path is preferable to fabricating a page. This source inspection does not establish that every external client shares the same page-size setting.

### 7. Source transitions and request reuse

The expanded scope requires an activity-level source/layout decision. Upstream's presenter-local `mUserCompatMode` (`U:.../ArticleListPresenter.java:113`, `:146`) is insufficient for a retained multi-page reader with two different page sizes.

Recommended session policy:

1. Start an online reader on the ordinary source as today; the compatibility preference remains default off. A successful allowed foreground App fallback establishes the App source for that **reader query/generation**.
2. Subsequent selected pages/explicit refreshes in that generation use App foreground reads consistently. Do not let page 1 be App/10 and page 2 silently start ordinary/20. App has no background prefetch. A deliberate source reset or relevant preference/account/query change starts another generation.
3. Source or positive page-size changes reset retained page identity, not just page count. Advance generation, clear prefetch candidates, discard old READY data, recreate/invalidate old child fragments, and ignore old callbacks before changing presenter data, loading state, title, counts, or cache ownership. Preserve DETACH cancellation; generation checks cover races cancellation alone does not prevent.
4. A foreground result may propose a layout transition. The owning pending request is checked, then its result is explicitly adopted into the new generation at the correct source coordinate. An old offscreen completion cannot itself change the reader's source/layout.
5. Capture a navigation anchor before transition: explicit requested floor/PID, otherwise a known current post, otherwise a justified old full-page start floor. Page number alone is not a cross-source anchor.
6. Example: ordinary page 7 at size 20 targets floor 120. Its App fallback for numeric page 7 reports size 10 and returns floors 60–69. Adopt the App layout, then make **at most one additional foreground alignment request** for App page 13, and find floor 120/PID in the actual list. Do not publish page 7's body under the old meaning of page 7. This additional read is bounded navigation reconciliation, not a background retry loop.
7. If no reliable cross-source mapping exists (filtered counts, missing pageSize, inconsistent floor layout), show the valid returned source window at its real coordinate with a concise notice that position could not be retained; do not claim a successful floor jump. No automatic search over unknown pages. Returning to a full-thread first page is a separate explicit navigation, not silent filter removal.
8. If the alignment request fails or its expected row is absent, finish loading, keep any deliberately displayed valid source window, and report the missing position. Do not recursively alternate sources or continue probing page numbers. An explicit later refresh is a new foreground operation.

The main network design owns exact allowed fallback/error classes and same-account snapshots. These rules do not authorize identity rotation or manufacture App success semantics.

### 8. Prefetch changes that preserve the fork's useful behavior

Retain `ArticlePagePrefetchPlanner.plan` (`app/sp/phone/mvp/viewmodel/ArticlePagePrefetchPlanner.java:17`) and its next-two/non-final invariant unchanged.

- Gate planning and receiving on **full unfiltered ordinary-source query**, known totalPages, online mode, and matching generation/owner. PID, author, reply-search, cache, App-source, and unknown-total windows publish no candidates.
- A candidate is more than an integer page in this feature. Either publish immutable `{generation, queryKey, sourceLayoutKey, page}` values or keep a generation-tagged immutable candidate set checked by every child. Include authorId/PID/search disposition in the query key even when the guard ordinarily excludes them.
- Reuse READY/in-flight work only for equal request keys including generation, owner, query, source/layout, and page. Do not use `ArticleListParam.equals` or `mThreadData != null` as proof that a body belongs to the selected page after a transition.
- Keep promotion/demotion, no duplicate request, explicit refresh, background silence, and final-page freshness behavior. A promoted ordinary prefetch that fails can enter the allowed foreground fallback; a still-background failure cannot rotate source, accounts, or open a browser.
- Preserve activity-only retention and offscreen limit 2. Publish updated paging totals only if the result belongs to the active context. A late ordinary prefetch after switching to App must not overwrite the App page count.

This narrows the accidental existing PID/author prefetch eligibility while extending their foreground reader support; it does not remove ordinary full-thread prefetch.

### 9. Cache identity must include the page layout

The existing `<tid>/<page>.json` slot is not sufficient once page 2 can mean floors 20–39, 10–19, or a filtered subset. Keep the previous research's explicit versioned-envelope and account-scoped storage direction, with this correction:

```text
logical cache entry = ownerKey + resolvedTid + FULL_QUERY + format + layoutId
known layoutId     = format + positive source pageSize
page key           = entry handle + actual effective page number
unknown layoutId   = unique saved-window snapshot ID (one page only)
```

Suggested envelope additions to the earlier design: `queryKind`, `pageSize` (nullable), `layoutId`, `pageBasis`, plus existing version/format/owner/tid/page/raw. Preserve the original raw response and prepared description. Do not store Cookies/cid or generated HTML.

- Only a complete parsed **full-query** response is eligible for the full-thread save action. A valid PID/author/reply-search view stays excluded even when its rows coincidentally cover a full page or resolvedTid is now positive.
- For ordinary new pages, the layout is explicitly `read_php_v1/20`; for App pages it is e.g. `app_post_list_v1/10`. These must be separate saved page groups even for the same owner/tid/page. App/20 and ordinary/20 also remain separate source layouts.
- Preserve old `files/cache/<tid>/<page>.json` as the legacy ordinary/20 store. An old unwrapped file is dispatched only to the normal parser. Never interpret it as App merely because the preference changed.
- **Do not apply the old proposed union-by-page-number across layouts.** `[ordinary page 2, App page 7]` is not one coherent cached pager when their source layout differs. Listing/open/delete/export/import must carry a bounded entry/layout handle, not just tid. The smallest clear UI can list distinct cached versions with a concise mode/page-size subtitle; each opens its own actual page numbers. It need not introduce a new browsing architecture.
- For unknown pageSize, maximize reading/save utility by saving an independent one-window snapshot, not putting it into a shared `unknown` group that could combine unknown 10- and 20-row layouts. Do not auto-union unknown snapshots or retroactively label them with a subsequently observed size. If the implementation chooses to omit unknown-size saves initially, that is a narrower cache capability and must be stated separately; it is not a reason to reject native reading.
- A reported response page mismatch must have been reconciled to the selected child/layout before save. Require selected-child context and response context to agree. Never substitute `ThreadPageInfo.page` for that check; retain the existing cache metadata tests where its value is unrelated.
- Keep valid supplied topicInfo verbatim, same-thread loaded metadata validation, Unicode blank handling, cloned selected-page parameters, feedback after successful file writes, and the notification → `显示全部` save path. Same-thread title metadata is independent of source layout.
- Decoder validates envelope version, format, owner, tid, queryKind, page, and layout against the selected entry before parser dispatch. Unknown versions/formats or mismatches fail locally. Reopening an App cache works while the compatibility preference is off. No cache failure triggers a network fallback.
- Old caches have no provable owner; preserve their existing readable compatibility status without relabeling them as the current account. New entry ownership covers both titles and bodies. Owner scoping alone is insufficient to prevent source/page-size collision.
- `TopicListModel`, `TopicCacheFragment`, `ArticleCacheActivity`, both cache model methods, and export/import must use the same resolver. Keep numeric sparse page labels and the repaired one-through-five equal-width tab rule. Deleting one source layout must not delete another account/layout simply because tid matches.

### 10. Meaningful offline test matrix

Use synthetic response fixtures to test local contracts; label them synthetic. These tests establish adapter behavior, not current endpoint availability.

| Case | Required observation |
| --- | --- |
| Full App query with perPage 10/30/40, explicit current/total pages | Native body, exact row identities/order, correct source-page tab count; no truncation or 20-row regrouping. |
| Full App final page with fewer rows than perPage | Accept body; correct totalPage and selected page; no inference that result.size is pageSize. |
| Full page with lou gaps, filtered page floors `[3, 85, 144]` | Preserve floors; actual list lookup finds requested row; no floor modulo scrolling. |
| perPage absent, totalPage=7, currentPage=3 | Page 3 of seven source pages remains readable; floor-to-page arithmetic unavailable. |
| Full vrows/perPage present, totalPage absent | Documented integer fallback only for full query; overflow checked. |
| Author/PID vrows present, totalPage absent | Never derive full or filtered page count from unknown-scope vrows; available-window presentation. |
| Explicit invalid/contradictory optional paging fields with valid body | Defined metadata degradation; no zero-tab success, fake pageSize20, or manufactured floor limit. |
| Supplied currentPage differs from request | Rebase/display correct source coordinate; no stale page cache write. |
| PID-only one row with tid=100001, PID target, lou=173 | Native lookup, resolvedTid established, displayed row index0/floor173; `显示全部` launches tid100001/page1 with all scoped fields cleared. |
| PID-containing window with target at list index4 | Focus exact PID after load; counters do not turn it into a full-query cache page. |
| PID target absent, mismatched supplied tid, mixed primary tids | Identity failure; no accidental other-post success or full cache. |
| Reply-search entry with PID and author | Outgoing App form preserves both fields; searchPost remains local identity, not an invented form key. |
| Author page refresh, source fallback, page2 | authorid remains present and query-scoped counters remain separate from global floors. |
| App10 floor85 quote from full or filtered view | Quote identity remains correct; third-field legacy hint is not App page9 or filtered page1; rendered native link targets PID. |
| Jump target sent before destination data/fragment exists | Pending keyed anchor survives until matching data; old generation cannot consume it. |
| Ordinary20 page7 → App10 fallback page7 | New layout, bounded one-read page13 alignment, exact floor120 lookup; no old/new page2 collision. |
| Missing mapping or absent alignment target | Clear position-unavailable outcome; no wrong-index success or unbounded page scan. |
| Old ordinary prefetch finishes after App transition | No presenter READY/title/count/body/cache overwrite; App selected-page load uses App source. |
| Same page number but different owner/query/source/pageSize/generation | No in-flight/READY reuse across keys. |
| Ordinary full-page prefetch promotion/failure/pause/refresh | Existing next-two, final-page exclusion, foreground promotion/demotion, and background silence preserved. |
| Parent page0, selected page7, loaded ThreadPageInfo.page1 | Existing eligibility/metadata clone tests remain correct; new paging context validates selected page independently. |
| Save App10 page2 and ordinary20 page2 for same tid/owner | Two coherent layout entries; neither overwrites or shadows the other by integer page alone. |
| Two unknown-size saved windows | Separate snapshot handles; never merged into one fake page layout. |
| Legacy raw page and new App envelope with preference off | Explicit normal/App dispatch respectively; both local, no requests. |
| Sparse same-layout cache pages `[2, 7]`, numeric pages `[2, 10]` | Correct actual page identity, numeric order, two equal-width tabs. |
| Owner/layout mismatch, unknown envelope version/format | Cache failure with no parser guessing or network fallback. |
| Remove/list/export/import one cache-layout entry | Chosen handle/owner boundary retained; other layouts and accounts remain intact. |

Existing tests to extend/preserve: `ArticlePageRequestStateTest.java`, `ArticlePagePrefetchPlannerTest.java`, `ArticlePageCacheTest.java`, `ArticleConvertFactoryTest.java`, `TopicPagePrefetchContractTest.kt`, and `ArticlePageRefreshContractTest.kt` under `nga_phone_base_3.0/src/test/`. Add pure tests for normalized paging, query identity, pending anchors, source transition budget, and cache layout codec/resolver. Avoid tests that merely assert the new class/field names.

After implementation, run the Android debug/unit/lint gate and inspect every module's lint XML for zero Error/Fatal, as specified. This research did not run builds or product tests because it makes no product changes. No NGA traffic, account storage reads, ADB, installation, or device tests are needed for this matrix.

### External references / versions

- Feature source: <https://github.com/Justwen/NGA-CLIENT-VER-OPEN-SOURCE/commit/2becba2acc3f6c85340424cd09bb03fa7d759db0>
- Supplied final August snapshot: <https://github.com/Justwen/NGA-CLIENT-VER-OPEN-SOURCE/tree/22ba3082501bcbb08f52a66d787f970f59c2dda7>
- Existing app parser uses Fastjson 1; upstream App DTO/parser uses Fastjson 2. This recommendation does not require migrating the whole project to Fastjson 2. Preserve source attribution when adapting DTO/parser code.

### Related specs

- `.trellis/workflow.md`: Phase 1.2 delegated, persisted research; no task activation or implementation by this agent.
- `.trellis/spec/backend/thread-page-prefetch-contract.md`: ordinary source, next-two non-final pages, request promotion/demotion and silence. Source/query/generation guards intentionally extend that contract for the new feature.
- `.trellis/spec/backend/thread-page-cache-contract.md`: full-query eligibility, selected-child metadata preservation, old raw format. New owned/layout storage is an explicit extension; existing cache fixes remain.
- `.trellis/spec/backend/nga-platform-operation-registry.md:43`: pinned `THREAD.PAGE` ordinary wire fields and its source-evidence status. App variant remains explicitly sourced from the August feature, not invented as a bootstrap fact.
- `.trellis/spec/backend/nga-platform-access-rules.md`: source evidence vs live guarantees, one account snapshot, data/error classification, account-scoped persistence, offline validation.
- `.trellis/spec/backend/network-foundation-contract.md:242`, `:261`: request identity and real error boundaries; no automatic account rotation introduced by this design.
- `.trellis/spec/frontend/component-guidelines.md:447`, `:459`, `:505`: preserve current floor menu removals, cached-tab widths, current-page refresh behavior. Source-change alignment is an intentional task-specific extension, not a general scroll-restoration refactor.
- `.trellis/spec/backend/android-quality-guidelines.md:149`: debug/unit/lint verification and per-module lint inspection; devices remain opt-in.

## Caveats / Not Found

- No live API or genuine App response fixture was available. Numeric success codes, author-count scope, PID containing-page semantics, and special-context rows remain unverified. The broader implementation targets these query shapes while recognizing unexpected responses explicitly.
- Filtered global-floor arithmetic cannot be recovered from perPage alone. The proposed page/lookup behavior is useful without claiming that impossible mapping.
- A consistent reader-wide App source is required once adopting App's different page size. A plan that keeps independent per-child source decisions needs an equivalent proven coordinate translation; none exists in the inspected code.
- The unknown-total window policy deliberately avoids automatic page discovery. It supports native content without claiming unseen pages are absent. Adding a manual unknown-total next-page UI would be an additional bounded product choice.
- Cache export/import internals were not retraversed here; existing research identifies their entry points. The main design must ensure the selected layout handle survives those operations rather than assuming the old tid-only export can handle a new root.
- Only this research file was written. No product/spec/config/task-metadata file, other task directory, Git state, credentials, network state, or device was changed.
