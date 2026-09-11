# Thread Reader Menu and Cache Repair

## Change Boundary

The behavior gap is local to the floor menu resources, cached-reader tab setup,
and full-thread cache eligibility/metadata preparation. Keep the legacy reader
and the current `THREAD.PAGE` request/parser/prefetch path.

Expected product files:

- `nga_phone_base_3.0/src/main/res/menu/article_list_context_menu.xml` and
  `article_list_context_menu_with_tid.xml`: remove the requested entries.
- `nga_phone_base_3.0/src/main/java/sp/phone/ui/fragment/ArticleListFragment.java`:
  remove only the now-unreachable menu cases and unused imports. Keep the
  standalone support/oppose listeners and unrelated menu IDs.
- `nga_phone_base_3.0/src/main/java/gov/anzong/androidnga/activity/ArticleCacheActivity.java`:
  configure the tab limit from the number of cached pages before binding the
  existing pager.
- `nga_phone_base_3.0/src/main/java/sp/phone/ui/fragment/ArticleTabFragment.java`:
  make full-thread cache availability independent of launch-time `topicInfo`.
- `nga_phone_base_3.0/src/main/java/sp/phone/mvp/presenter/ArticleListPresenter.java`:
  complete the cache metadata at the save boundary from the already loaded
  `ThreadData` when launch metadata is absent.
- `nga_phone_base_3.0/src/main/java/sp/phone/mvp/presenter/ArticlePageCache.java`:
  share the full-thread eligibility predicate between the toolbar and save path,
  and prepare a page-parameter snapshot without Android runtime calls. This is
  the local seam anticipated for meaningful JVM cache behavior tests.

No general refactor, shared tab redesign, new navigation extras, or cache model
interface change is required by the current evidence. If implementation reveals
a necessary expansion, update this boundary before making it.

## R1: Floor Menus

Both XML menus are inflated by the same floor popup listener. Remove all
requested occurrences and the corresponding handler branches. `menu_favorite`
also belongs to `topic_list_menu.xml` / `TopicListActivity`; retain those uses.
The poll action `投票` (`menu_vote`) is distinct from the removed support/oppose
actions and remains unchanged.

## R2: Cached Page Distribution

The ordinary reader calls `setTabOnScreenLimit(count <= 5 ? count : 0)`.
`TabLayoutEx.TabAdapter.onBindViewHolder` divides measured width by a positive
limit; a zero limit uses the existing min/max tab widths. The cache activity
never sets this limit, causing compact tabs even with two cached pages.

Use the same rule with `mCachePageList.size()` before `setUpWithViewPager`.
Count the cached entries, not the highest page number or total server replies.
Keep the empty-cache handling and the adapter's mapping of sparse file numbers.
This single setup call does not justify altering the shared tab component.

## R3: Cache Eligibility and Metadata

Current flow:

```text
recent notification (tid, pid, searchPost=1)
  -> ArticleSearchFragment
  -> Show all (tid, title only)
  -> ArticleTabFragment + per-page cloned ArticleListParam
  -> loaded ThreadData(threadInfo, rawData)
  -> selected-page cache event
  -> ArticleListPresenter.cachePage
  -> ArticleListModel.cachePage
  -> cache/<tid>/<tid>.json and cache/<tid>/<page>.json
  -> TopicListModel.loadCache / ArticleCacheActivity
```

The missing `topicInfo` affects both the toolbar visibility and the model's
save guard. Repair both boundaries together:

1. Show caching for an online full-thread context with a valid `tid`, no `pid`,
   no author filter, and no reply-search mode. Do not use the source of navigation
   or launch metadata as a proxy for eligibility.
2. Resolve the selected page through the existing cache event. In the presenter,
   keep supplied topic-list metadata when present. When missing, serialize the
   already parsed `ThreadData.getThreadInfo()` with the existing Fastjson
   `JSON.toJSONString` convention. Accept only usable metadata for the same
   thread; never fabricate a placeholder title/author/thread.
3. Pass the prepared page parameters and the existing raw page data into the
   existing model. Prefer a request snapshot at the save boundary so metadata
   preparation does not change navigation or another pager page's parameters.
4. If no loaded page or usable description exists, avoid writes and success
   feedback; reuse the existing unavailable/failure behavior as appropriate.

The implementation exposes `ArticlePageCache.isCacheableContext(param)` to the
toolbar and keeps `prepare(param, data)` package-local for the presenter.
Eligibility is independent of the parent pager's initial `page=0`; preparation
requires a child page of at least 1 and nonblank raw data. Existing descriptions
must decode to the same tid and a nonblank subject; absent descriptions are
serialized from usable loaded metadata. A mismatched loaded thread is rejected.
Failed preparation leaves caller parameters and loaded data unchanged.

`TopicSearchFragment` and `TopicHistoryFragment` already serialize the same
`ThreadPageInfo` type; `TopicListModel.loadCache` reads it back with
`JSON.parseObject(..., ThreadPageInfo.class)`. This makes the fallback compatible
with current cache descriptions and avoids reparsing a raw response in the UI.

## Validation and Compatibility

- Inspect both menu resources and remaining handler references, then compile to
  catch resource IDs whose final declaration was removed.
- Verify the tab setup's order and 1-5/6+ boundary against the ordinary reader;
  use the actual cached-entry count for sparse page sets such as `[2, 7]`.
- Add focused offline behavior tests for missing launch metadata, existing
  metadata, selected-page identity, serialization/read-back compatibility,
  absent/mismatched metadata, and filtered-view exclusion. Do not create tests
  that merely mirror the deleted XML entries or a one-line layout assignment.
- Keep app unit tests and current refresh/prefetch tests green. Run the Android
  quality gate and inspect all module lint XML reports for Error/Fatal issues.

## Risks and Rollback

The main risk is showing a cache action without producing a cache description
that the list reader accepts, or allowing filtered page content into a full-page
cache slot. The offline read-back and eligibility cases address both. Device
visual playback is outside this task's authorized validation; do not claim it.
No data migration is involved, so reverting the changed source files restores
the earlier behavior while preserving existing caches.
