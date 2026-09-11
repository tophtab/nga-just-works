# Thread Reader Evidence

All findings below describe the current fork at `8284c703`; they are source
observations, not live service verification.

## Menu Scope

- `ArticleListFragment.java:153` chooses normal vs `with_tid` floor resources by
  `pid`. `article_list_context_menu.xml` includes all four removed actions;
  `article_list_context_menu_with_tid.xml` includes support, oppose, and favorite.
- `ArticleListFragment.java:108,131,134,137` dispatches the removable cases.
  Its separate `mSupportListener` / `mOpposeListener` are still used by the row
  adapter and must stay. `menu_vote` opens a poll and is unrelated.
- `TopicListActivity.java:123` and `topic_list_menu.xml:56` also use
  `menu_favorite`; this shared ID and those uses are outside the removal.

Java fragment paths above are under
`nga_phone_base_3.0/src/main/java/sp/phone/ui/fragment/`; activity paths are under
`nga_phone_base_3.0/src/main/java/gov/anzong/androidnga/activity/`; resources are
under `nga_phone_base_3.0/src/main/res/menu/`.

## Pager Ownership

- `ArticleTabFragment.java:103` updates the on-screen tab limit to the page
  count when the count is at most five, otherwise zero.
- `ArticleCacheActivity.java:51-59` knows the cached-entry count but only binds
  the pager; no limit is set. `fragment_article_tab.xml:22` has no limit override.
- `lib_base_common/src/main/java/gov/anzong/androidnga/base/widget/TabLayoutEx.java`
  uses `getMeasuredWidth() / mTabOnScreenLimit` for positive limits. It has no
  independent cache special case.
- `nga_phone_base_3.0/src/main/java/sp/phone/ui/adapter/ArticlePagerAdapter.java`
  clones page params and maps the cached string labels to actual page numbers.
  Keep this mapping; the requested change is spacing, not cache-file sorting.

## Cache Metadata Round Trip

1. `RecentNotificationFragment.java:160-163` passes tid, pid, title, search mode.
2. `ArticleSearchFragment.java:39-45` opens the full thread with tid/title only.
3. `ArticleTabFragment.java:297` hides `menu_download` when `topicInfo` is null.
   Its cache handler already resolves `mViewPager.getCurrentItem() + 1`.
4. `ArticleListFragment.java:221` observes the cache event and invokes the
   presenter for the matching page.
5. `nga_phone_base_3.0/src/main/java/sp/phone/mvp/presenter/ArticleListPresenter.java:369`
   passes params and raw page data to the cache model without filling metadata.
6. `nga_phone_base_3.0/src/main/java/sp/phone/mvp/model/ArticleListModel.java:117`
   rejects an empty `topicInfo`; otherwise it saves a Fastjson description in
   `cache/<tid>/<tid>.json` and page content in `cache/<tid>/<page>.json`.
7. `nga_phone_base_3.0/src/main/java/sp/phone/mvp/model/convert/ArticleConvertFactory.java:68,79`
   already parses response `__T` into the same `ThreadPageInfo` used by topic
   lists; there is no need to parse the wire format again or issue another call.
8. `TopicSearchFragment.java:283` / `TopicHistoryFragment.java:131` provide the
   existing `JSON.toJSONString(info)` convention.
9. `nga_phone_base_3.0/src/main/java/sp/phone/mvp/model/TopicListModel.java:66`
   reads descriptions with `JSON.parseObject(rawData, ThreadPageInfo.class)`;
   `TopicCacheFragment.java:92` opens the cache reader with the saved tid/title.

The filter-only navigation at `ArticleListFragment.java:122-129` also lacks
launch metadata, so simply deleting the null check would newly expose caching
for partial author-filtered pages. Use an explicit full-thread eligibility rule.

## Validation Constraints

- Existing app tests include `ArticlePageRefreshContractTest`,
  `TopicPagePrefetchContractTest`, and pure presenter request-state tests.
- There is no existing focused cache-description behavior test in the app.
- The applicable operation is `THREAD.PAGE`; this repair is a local cache/UI
  delta and preserves its current transport/parser contract.
- `.trellis/spec/backend/android-quality-guidelines.md:149` specifies the
  device-independent Gradle/lint gate and the known aggregate test limitations.

## Context Loading Note

The component and Android quality specs exceed the automatic injection byte
limit. Agents must explicitly read the applicable sections if injection is
truncated: `Contextual floating action buttons`, `Article current-page refresh`,
`Global device-operation authorization policy`, and `Validation gate`. Do not
change the global injection configuration for this product task.
