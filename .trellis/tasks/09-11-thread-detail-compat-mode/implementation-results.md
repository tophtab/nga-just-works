# Implementation results — thread detail compatibility

This is the implementer handoff snapshot. The final reviewed result, including
the necessary core comment-builder fix, is in [delivery.md](delivery.md) and
[independent-check.md](independent-check.md).

Status: implementation and its local verification are complete; product and tests are handed to independent review. Gradle ownership has been released to the main session. Independent review remains pending; this is not a claim of live-interface coverage.

## Implemented behavior

The default-off `pref_show_with_app_api` laboratory setting enables an activity-local compatibility reader. New readers use the ordinary endpoint. An eligible foreground format failure can perform one same-account App request; a successful App source remains active for that query generation. Account/toggle/query changes retire prior pages. Invalid selected credentials stop and retire obsolete state rather than becoming a guest request. Normal full-query prefetch retains next-two planning, READY reuse, promotion/demotion and DETACH cancellation; background work cannot initiate App/account/browser side effects.

Full threads, author queries and PID/search lookups retain their independent parameters. The pager uses response context and actual page numbers. Unknown page counts remain an independently labelled current window; a known total with missing size still supports page navigation. Local floor/PID lookup uses actual rows even without a cross-page mapping. Cross-page floor arithmetic is a candidate only. A source/layout transition keeps a pending real anchor and permits at most one alignment read (ordinary page7/20 -> App page7/10 -> page13/10). Failed or unavailable alignment is reported explicitly. The new-generation handoff avoids an extra fetch and old callback cleanup cannot reset the new READY state.

The App adapter projects only upstream-consumed fields from Justwen `2becba2acc3f6c85340424cd09bb03fa7d759db0` (GPL-3.0). Unknown sidecars remain in raw source and are not invented into attachment, hot-post, parent-comment or score protocols. Shared rendering retains attachment prefixes, legacy comments/blacklist preparation and ordinary WP preprocessing. Confirmed HTML reply headers are normalized only for rendering or temporary quote text; editable source and response raw are preserved. Unreadable source stays as an incomplete row and cannot be cached or quoted/edited. Unknown UID attribution cannot create a UID0 link or mention; actual comments and recycled identity/score/floor controls use explicit facts. WebView retention follows the returned row count and releases views with the fragment view.

The cache store resolves a validated owner/thread/layout handle across save, list, open, replay and delete. Same-owner known source/size layouts merge numeric sparse pages; unknown sizes use independent one-window snapshots. Metadata is prepared from the selected child, retained verbatim when supplied, or filled from matching loaded topic data. Owned envelopes use explicit version/format validation and replay dispatch; corrupt envelopes cannot fall back to a different source, legacy file or network. Legacy raw entries are not migrated or assigned an invented owner. Cached App pages remain readable with compatibility disabled.

Old ZIP import now accepts only bounded `cache/<tid>/<positive-number>.json` entries and known directory shapes. It stages and validates every path before writes, preventing the old generic importer from writing an owned-cache root or traversing outside cache. This change is part of the cache-isolation boundary. Export enumerates the same validated legacy entries, excluding owned roots and write temporaries. Menu and result text explain that only old-format caches are exported.

## Validation

See `check-results.md` for commands, logs, per-suite counts and the broad debug diagnostic. On the frozen implementation, app/common have 199 + 62 passing tests (35 suites, zero failure/error/skipped); debug assemble and app lint pass. A forced rerun of all modules' lint tasks passes, and all 13 Android modules' lint XML reports contain zero Error/Fatal. `git diff --check` passes.

The broad debug diagnostic reports exactly two known, unmodified example-test compilation failures: missing JUnit in `lib_bu_statistics`, and an unresolved KAPT annotation stub in `lib_module_debug`. The two modules have no diff from the approved baseline and the failure classes match `android-quality-guidelines.md`. The remaining 11 modules report 280 passing tests; `lib_base_ui` and `lib_core` pass in this run. This diagnostic is recorded as failed, not as a full-repository test pass.

Main session supplied `ArticleByteClientTest.kt`; all other product/test edits below were implemented here. No Android device or real NGA request was used. Source-boundary UI tests do not establish on-device behavior.

## Limits and review follow-ups

- Independent full-diff review remains the next owner of product/test fixes. Main session owns specs and task metadata.
- No live endpoint response, service availability, real account credential storage or on-device WebView/lifecycle behavior was exercised. Pure/fake tests and Android source-boundary assertions establish local contracts only.
- New cache layouts intentionally are not part of the old ZIP format. Optional upstream fields without an implemented parser remain opaque by design.
- Cache IO is bounded (32 MiB page envelope, 512 KiB metadata); legacy import also limits each entry, total expansion (256 MiB) and entry count (10,000).
- Workspace remained `/home/toph/nga-just-works-compat-mode`, `feature/thread-detail-compat-mode`, baseline `5bb92cf0` including `6203dad5`. No commit, branch/worktree switch, push, merge, release or signing change occurred.

## Product and test files

- `lib_base_common/src/main/res/values/donottranslate.xml`
- `nga_phone_base_3.0/src/main/java/gov/anzong/androidnga/activity/ArticleCacheActivity.java`
- `nga_phone_base_3.0/src/main/java/gov/anzong/androidnga/activity/ArticleListActivity.java`
- `nga_phone_base_3.0/src/main/java/sp/phone/http/bean/ThreadData.java`
- `nga_phone_base_3.0/src/main/java/sp/phone/http/bean/ThreadRowInfo.java`
- `nga_phone_base_3.0/src/main/java/sp/phone/mvp/contract/ArticleListContract.java`
- `nga_phone_base_3.0/src/main/java/sp/phone/mvp/model/ArticleListModel.java`
- `nga_phone_base_3.0/src/main/java/sp/phone/mvp/model/TopicListModel.java`
- `nga_phone_base_3.0/src/main/java/sp/phone/mvp/model/convert/ArticleConvertFactory.java`
- `nga_phone_base_3.0/src/main/java/sp/phone/mvp/model/entity/ThreadPageInfo.java`
- `nga_phone_base_3.0/src/main/java/sp/phone/mvp/model/thread/AppArticleParser.kt`
- `nga_phone_base_3.0/src/main/java/sp/phone/mvp/model/thread/ArticleAccount.kt`
- `nga_phone_base_3.0/src/main/java/sp/phone/mvp/model/thread/ArticleAuthorSupport.kt`
- `nga_phone_base_3.0/src/main/java/sp/phone/mvp/model/thread/ArticleByteClient.kt`
- `nga_phone_base_3.0/src/main/java/sp/phone/mvp/model/thread/ArticleCache.kt`
- `nga_phone_base_3.0/src/main/java/sp/phone/mvp/model/thread/ArticleFailure.kt`
- `nga_phone_base_3.0/src/main/java/sp/phone/mvp/model/thread/ArticlePage.kt`
- `nga_phone_base_3.0/src/main/java/sp/phone/mvp/model/thread/ArticleReaderSession.kt`
- `nga_phone_base_3.0/src/main/java/sp/phone/mvp/model/thread/ArticleRowPresentation.kt`
- `nga_phone_base_3.0/src/main/java/sp/phone/mvp/model/thread/LegacyArticleCacheArchive.kt`
- `nga_phone_base_3.0/src/main/java/sp/phone/mvp/model/thread/NormalArticleParser.kt`
- `nga_phone_base_3.0/src/main/java/sp/phone/mvp/model/thread/ThreadAppBean.kt`
- `nga_phone_base_3.0/src/main/java/sp/phone/mvp/presenter/ArticleListPresenter.java`
- `nga_phone_base_3.0/src/main/java/sp/phone/mvp/presenter/ArticlePageCache.java`
- `nga_phone_base_3.0/src/main/java/sp/phone/mvp/presenter/ArticlePageRequestState.java`
- `nga_phone_base_3.0/src/main/java/sp/phone/mvp/presenter/TopicListPresenter.java`
- `nga_phone_base_3.0/src/main/java/sp/phone/mvp/viewmodel/ArticleShareViewModel.java`
- `nga_phone_base_3.0/src/main/java/sp/phone/param/ArticleListParam.java`
- `nga_phone_base_3.0/src/main/java/sp/phone/ui/adapter/ArticleListAdapter.java`
- `nga_phone_base_3.0/src/main/java/sp/phone/ui/adapter/ArticlePagerAdapter.java`
- `nga_phone_base_3.0/src/main/java/sp/phone/ui/adapter/TopicListAdapter.java`
- `nga_phone_base_3.0/src/main/java/sp/phone/ui/fragment/ArticleListFragment.java`
- `nga_phone_base_3.0/src/main/java/sp/phone/ui/fragment/ArticleSearchFragment.java`
- `nga_phone_base_3.0/src/main/java/sp/phone/ui/fragment/ArticleTabFragment.java`
- `nga_phone_base_3.0/src/main/java/sp/phone/ui/fragment/TopicCacheFragment.java`
- `nga_phone_base_3.0/src/main/java/sp/phone/ui/fragment/dialog/GotoDialogFragment.java`
- `nga_phone_base_3.0/src/main/java/sp/phone/util/FunctionUtils.java`
- `nga_phone_base_3.0/src/main/res/menu/menu_cache_list.xml`
- `nga_phone_base_3.0/src/main/res/values/strings.xml`
- `nga_phone_base_3.0/src/main/res/xml/settings_lab.xml`
- `nga_phone_base_3.0/src/test/java/sp/phone/common/DefaultSettingsContractTest.java`
- `nga_phone_base_3.0/src/test/java/sp/phone/mvp/model/thread/AppArticleParserTest.kt`
- `nga_phone_base_3.0/src/test/java/sp/phone/mvp/model/thread/ArticleByteClientTest.kt`
- `nga_phone_base_3.0/src/test/java/sp/phone/mvp/model/thread/ArticleCacheStoreTest.kt`
- `nga_phone_base_3.0/src/test/java/sp/phone/mvp/model/thread/ArticleReaderSessionTest.kt`
- `nga_phone_base_3.0/src/test/java/sp/phone/mvp/model/thread/NormalArticleParserTest.kt`
- `nga_phone_base_3.0/src/test/java/sp/phone/mvp/presenter/ArticleOwnedPageCacheTest.kt`
- `nga_phone_base_3.0/src/test/java/sp/phone/mvp/presenter/ArticlePageCacheTest.java`
- `nga_phone_base_3.0/src/test/java/sp/phone/ui/fragment/ArticleReaderUiContractTest.kt`
- `nga_phone_base_3.0/src/test/java/sp/phone/ui/fragment/TopicPagePrefetchContractTest.kt`
