# Research: Pagination and cache adaptation

> Scope revision: the original source findings below remain evidence; conservative scope/admission recommendations are historical. Use ../design.md and the content-reuse-revision.md / query-pagination-revision.md reports for the current selected behavior.

- Query: Identify the native reader's exact pagination assumptions; recommend a bounded App API acceptance contract and a versioned cache design that preserves the repaired full-thread cache path.
- Scope: internal source inspection plus the supplied, pinned upstream snapshot; planning only.
- Date: 2026-09-11
- Current-fork baseline: parent supplied `5bb92cf0`, including cache repair `6203dad5`. No Git commands were run by this researcher.
- Upstream evidence: supplied snapshot of `22ba3082501bcbb08f52a66d787f970f59c2dda7`; feature originally introduced by `2becba2acc3f6c85340424cd09bb03fa7d759db0`.

## Findings

### 1. Exact current pagination and identity contracts

Paths below are relative to the repository. `app/` in the tables means `nga_phone_base_3.0/src/main/java/`; `U:` means the same original repository path under `/tmp/nga-upstream-august-2026-review/upstream-22ba3082/`.

| File and anchor | Source fact and consequence |
| --- | --- |
| `app/sp/phone/param/ArticleListParam.java:12`, `:34`, `:88`, `:103` | Parameters carry `pid`, `tid`, `authorId`, `page`, `searchPost`, title/content/topic metadata and `loadCache`; there is no page-size, total-page, response-format, or cache-owner field. Parcelable and shallow clone preserve the existing scalar/string fields. `equals` also includes request identity/page/search values, but does not include cache state. |
| `app/sp/phone/ui/adapter/ArticlePagerAdapter.java:45`, `:67`, `:85` | An online child always receives `page = position + 1`. Cache children instead receive the actual integer parsed from `mPageIndexList`; sparse cached pages are supported. Parent launch `page` is not used to select the initial online tab here. |
| `app/sp/phone/http/bean/ThreadData.java:10`, `:36`, `:44`, `:52` | `__ROWS` is separate from displayed `rowNum`. The model has raw response text, row list and thread description; it has no normalized paging metadata or response provenance. |
| `app/sp/phone/mvp/model/convert/ArticleConvertFactory.java:43` | The normal parser repairs known wrappers/text, reads `data.__ROWS` into `ThreadData.__ROWS`, stores the repaired text as raw data, and sets `rowNum` to the converted list size. |
| `app/sp/phone/mvp/model/convert/ArticleConvertFactory.java:79`, `:92`, `:118` | `__T` maps to `ThreadPageInfo`. `__R__ROWS` controls iteration over string keys `0..count-1`; non-object entries are skipped. This can produce fewer rendered rows than the declared count. Row `lou`, `tid`, and `pid` are mapped from the server object, not synthesized from list position. |
| `app/sp/phone/mvp/model/entity/ThreadPageInfo.java:19`, `:29`, `:100`, `:140`, `:148`, `:156` | Description fields include `replies`, `page`, `pid`, and `position`, but those fields are not the current pager's page-count authority. Do not replace `ThreadData.__ROWS` with `ThreadPageInfo.replies` accidentally. |
| `app/sp/phone/ui/fragment/ArticleListFragment.java:295` | Every delivered page publishes `data.__ROWS` as the activity's reply count. If no launch title exists, it dereferences `data.threadInfo.subject`. The first row with `lou == 0` also establishes the topic-owner name. A successful App result therefore needs usable thread metadata as well as rows. |
| `app/sp/phone/ui/fragment/ArticleTabFragment.java:98`, `:146` | Page count is `ceil(__ROWS / 20.0f)`, and this count also replans the next-two-page prefetch. `0` becomes zero tabs. No server `currentPage`, `perPage`, or `totalPage` is consumed. |
| `app/sp/phone/ui/fragment/ArticleTabFragment.java:303`, `:331`; `app/sp/phone/ui/fragment/ArticleListFragment.java:225` | Go-to-floor computes zero-based tab `floor / 20` and adapter index `floor % 20`; the receiving child scrolls to that index without searching by `lou`. Consequently, merely preserving a non-contiguous `lou` list does not preserve floor navigation. |
| `app/sp/phone/ui/adapter/ArticleListAdapter.java:198`, `:456`, `:532` | Quote links independently calculate the original page as `(lou + 20) / 20`, floors display their server `lou`, and list length uses `ThreadData.rowNum`. Changing only the tab calculation would still leave quote-page behavior inconsistent. |
| `app/sp/phone/mvp/presenter/ArticleListPresenter.java:263`, `:334` | Presenter comment/quote paths also embed the requested `param.page` in links. A response silently redirected to another page must not be presented under the old request page. |
| `app/sp/phone/mvp/model/ArticleListModel.java:49` | Normal requests transmit nonzero tid/pid/authorid and the requested page. They do not transmit `searchPost`; a separate task already owns that old gap. Do not assume the compatibility feature repairs it. |
| `app/sp/phone/ui/fragment/ArticleSearchFragment.java:39` | `显示全部` launches a new full reader with request tid and current title, without topic-list metadata. Existing cached metadata repair explicitly supports that path. PID-only discovery must not fabricate a thread ID or overwrite request identity. |
| `app/sp/phone/ui/fragment/ArticleListFragment.java:218` | Search/cache children do not prefetch. Online author-filtered children can still be regular pager children; there is no separate arbitrary-pagination implementation for them. |

`__ROWS` behaves as total displayed floor slots, including the initial floor, in this UI contract: a valid first page starts at floor 0, floors 0–19 are tab 1, and floors 20–39 are tab 2. This is a statement about current calculations, not independent proof of every NGA response's semantics. In particular, do not “fix” App `vrows` by adding one just because the ViewModel calls it `replyCount`.

### 2. What the upstream App API actually establishes

- `U:lib_core_data/src/main/java/com/client/androidnga/core/data/bean/ThreadAppBean.kt:5` declares nullable `currentPage`, integer `perPage`, `totalPage`, `vrows`, and a result list. Result rows declare `lou`, `pid`, `tid`, `author`, and `isTieTiao` at `:100` onward. Declarations alone do not prove missing-field behavior or all wire semantics.
- `U:lib_core/src/main/java/com/client/androidnga/core/parse/ThreadInfoAppParse.kt:25` copies `vrows` into `totalRows`; `:45` copies row IDs and floor unchanged; `:90` derives thread tid from `result[0]`. It never applies `currentPage`, `perPage`, or `totalPage` to navigation and does not guard an empty result before indexing it.
- There is no supplied real App response fixture. The previous audit, `.trellis/tasks/archive/2026-09/09-11-upstream-august-2026-review/research/bugfix-browser.md`, already records the upstream test's missing `tem.json` and lack of assertions. Requiring fixtures does not authorize a live request.

### 3. Recommended minimal native pagination contract

Keep the existing 20-floor navigation model for this feature. Validate that a response fits it before producing a successful `ThreadData`; do not manufacture a different page count by scaling `vrows`, truncating a larger page, or fetching multiple App pages to assemble one native page.

The following is a **proposed admission rule**, not a newly verified server specification:

1. Initially admit ordinary full-thread reads only: requested `tid > 0`, child `page >= 1`, `pid == 0`, `authorId == 0`, `searchPost == 0`. Unsupported filtered/PID contexts continue to their existing foreground error/browser policy; they must never become an unfiltered App request. This is the narrowest contract that preserves current floor-jump and cache semantics without inventing response behavior.
2. Require explicit `perPage == 20` and positive `vrows`. Keep DTO/validator fields nullable or inspect key presence so a missing value is not silently treated as a valid default. `rowNum = accepted result.size`; `__ROWS = vrows` exactly. Do not copy the number of rows in this one page into `__ROWS`.
3. Calculate `expectedTotalPages = (vrows + 19L) / 20L` using widened arithmetic. If `totalPage` is present, require it to be positive and equal to that value. If absent, deriving it from validated `vrows` is an explicit normalization within this 20-floor contract. Do not treat an explicitly supplied zero as absent.
4. If `currentPage` is present, require it to equal the requested page and be within `1..expectedTotalPages`. If absent, accept only after the floor sequence proves the requested page below; do not blindly substitute the request page.
5. For ordinary full pages, require a nonempty row list of exactly `min(20, vrows - 20L * (page - 1))` rows, with `lou[i] == 20L * (page - 1) + i`. Every row must have the requested positive tid; `pid >= 0`; nonzero reply PIDs should be unique. Never renumber `lou`, synthesize missing posts, sort rows to hide a malformed response, or replace an absent tid with the request tid. Keep a zero PID available for the initial floor; the current client uses this convention in its quote paths.
6. Missing/empty results, page mismatch, non-20 page sizes, inconsistent counts, a floor gap, duplicated/inserted sticky rows, negative IDs, or mixed tids produce an unsupported/malformed result and finish the loading/error chain. They must not create a zero-tab “success” or enter cache. This strict rule may reject valid server variants; that is deliberate until fixtures establish an alternative safe mapping.
7. Preserve thread description from available top-level subject/author/fid plus validated row tid, without indexing an empty result. Blank subject is insufficient for the existing save contract. Optional author/reply fields must not be fabricated merely to make caching succeed.

This option avoids product edits to `ArticleTabFragment`, its ViewModel, and floor-jump arithmetic. The current `ceil(... / 20.0f)` implementation has float precision limits at extremely large counts; the adapter admission test should confirm its derived count equals the existing UI calculation or reject the unrealistic input, rather than claiming arbitrary integer-range support.

**Filtered/PID extension, if selected by the main design:** it needs a separately defined contract. An author page preserves original global `lou`, while `vrows`/page count may describe a filtered subset; current floor jumps and Presenter quote pages then become ambiguous. A PID lookup also may return a containing page, one row, or another page number. Minimum safe validation would require the exact requested PID and matching tid when supplied, preserve original floor, and disable full-thread cache/prefetch; it would still require navigation/quote behavior decisions. Upstream passing `pid` and `authorid` is not enough evidence to promise these cases. Do not silently include them in the full-thread validator.

### 4. Existing cache data flow and consumers

The repaired path is:

```text
selected child cache event
  -> ArticleListPresenter.cachePage
  -> ArticlePageCache.prepare(selected params, loaded ThreadData)
  -> cloned params carrying valid topicInfo
  -> ArticleListModel.cachePage
  -> files/cache/<tid>/<tid>.json         (ThreadPageInfo description)
     files/cache/<tid>/<page>.json        (normal parser's raw string)
  -> TopicListModel.loadCache / TopicCacheFragment
  -> ArticleCacheActivity / ArticlePagerAdapter
  -> ArticleListModel.loadCachePage -> ArticleConvertFactory
```

| Consumer / anchor | Contract to preserve or extend |
| --- | --- |
| `app/sp/phone/mvp/presenter/ArticlePageCache.java:16` | Cache eligibility requires full online thread (`tid > 0`, no pid/author/search/cache flag); the parent may have page 0. |
| Same file `:26`, `:37`, `:53` | Preparation separately requires selected child page >= 1 and nonblank raw data. Loaded tid must match. Preserve valid supplied `topicInfo` verbatim; otherwise serialize usable loaded `ThreadPageInfo`. Return a clone; do not mutate page/navigation state. Unicode blank handling at `:65` must remain. |
| `app/sp/phone/mvp/model/ArticleListModel.java:117` | Writer is asynchronous, writes description then raw page, and announces success only after both writes. The path contains no owner identity. |
| Same file `:139` | Loader reads the raw file and always invokes the legacy parser. On failure it invokes the one-argument callback `读取缓存失败！`; there is currently no network fallback in this loader. An App JSON body here would be dispatched incorrectly. |
| `app/sp/phone/mvp/model/TopicListModel.java:66` | The cache list enumerates `files/cache/` and reads each `<dirname>/<dirname>.json` directly into `ThreadPageInfo`. A body-only owner check would still leave titles visible across accounts. |
| Same file `:196` | Deleting a cached topic deletes the complete tid directory. Mixed-owner data in that directory would need an explicit deletion policy. |
| `app/sp/phone/ui/fragment/TopicCacheFragment.java:89` | Opening a cache entry creates params containing tid, title and `loadCache = true`; no format/owner/storage handle is currently passed. |
| Same file `:75` | Export/import actions delegate to the cache Presenter. Their storage behavior was not traversed in this bounded research. Any new cache root or account filtering must include an explicit decision about these entry points; do not assume existing export automatically includes a new directory. |
| `app/gov/anzong/androidnga/activity/ArticleCacheActivity.java:64` | Page enumeration excludes names containing tid, strips `.json`, then sorts strings. It does not inspect response format or owner. Do not add sidecars/temporary files here without filtering them, or claim the existing list is numeric-sorted. |
| Same file `:51`, `:59`; `app/sp/phone/ui/adapter/ArticlePagerAdapter.java:45` | Adapter retains actual sparse page numbers; equal-width tabs use the number of cached entries for counts 1–5. These repaired behaviors are independent of response format and must stay. |

### 5. Versioned page envelope and parser dispatch

Recommended logical schema (field names are proposed, fixture values are synthetic):

```json
{
  "kind": "nga.thread-page-cache",
  "version": 1,
  "format": "app_post_list_v1",
  "ownerKey": "uid:12345",
  "tid": 100001,
  "page": 2,
  "raw": "<decoded App response text>"
}
```

- Support an explicit `read_php_v1` discriminator as well if the chosen storage design writes new normal responses through the codec. The format means parser contract, not networking preference. Turning compatibility mode off must not prevent reading an already saved App page.
- Store decoded raw text as a JSON string, not a reserialized payload object or generated HTML. This preserves the response's original fields and page-scoped image prefix for re-rendering under current UI/image settings. New files should explicitly use UTF-8; preserve the existing legacy raw reader's compatibility.
- Keep the description as existing Fastjson `ThreadPageInfo` JSON, preserving prepared `topicInfo` exactly. It needs a storage/owner boundary too; making only the page body account-aware does not protect titles.
- The request/response layer, not the parser, supplies `ownerKey` from the exact attempted account snapshot. Only a non-secret stable UID key is persisted, never Cookie/cid. A guest key is valid only for a deliberately anonymous snapshot, never a fallback for an unknown account. An account switch before an asynchronous save/result delivery must be checked against that snapshot.
- A codec returns an explicit format/owner/tid/page plus raw text. Validate envelope version, allowed format, owner and location identity before dispatch. Unknown version/format, malformed envelope, tid/page mismatch, blank raw body, or owner mismatch is a local cache failure. Do not try both parsers and accept whichever returns something.
- For the original legacy location, an unwrapped file is dispatched only to `ArticleConvertFactory`. There is no proven untagged App-cache population in this fork to migrate. For a new-version location, an envelope is mandatory. An envelope error must not be fed into the old parser merely to recover.
- Decoding an App page reuses the same App admission/adapter logic as the live result and restores its format/provenance on `ThreadData`. The cache reader stays local; rejection never issues App/read.php requests, rotates accounts, or opens a WebView automatically.

### 6. Storage options for the main design

The platform contract requires account-scoped caches where visibility depends on the account (`.trellis/spec/backend/nga-platform-access-rules.md:384`). Current `files/cache/<tid>` is shared across accounts. Merely adding `ownerKey` to the page envelope is insufficient for list titles, overwrites, and deletion.

| Option | Shape | Benefits | Necessary changes / limitations |
| --- | --- | --- | --- |
| **Recommended: separate owned storage for new compatible pages** | Example: `files/thread-cache-v1/<ownerKey>/<tid>/<tid>.json` and `<page>.json`, with per-page envelopes. Leave `files/cache/...` raw legacy data in place. | Ownership applies equally to titles and pages; another account cannot overwrite/delete the first account's new cache. Existing raw files stay readable without migration. | A shared cache index/resolver must be used by cache list, opening, page enumeration, load and deletion. Navigation must carry or revalidate the selected owner/storage handle. Export/import scope must be consciously adapted or documented. No arbitrary owner/path from an Intent may bypass the active-owner check. |
| Same shared directory plus envelope-aware index | Continue `files/cache/<tid>/<page>.json`, embed owner and the original prepared description in each new envelope; do not trust the shared description for an inaccessible page. | Avoids a second root; old filenames remain. | Every writer must refuse to overwrite an existing other-owner envelope before writing either file; list/page filtering and deletion must understand mixed ownership. Shared `<tid>.json` can no longer be the sole title authority. There is only one file per tid/page, so two accounts cannot both save that slot without extending naming/storage. This is less minimal than it first appears. |

For the recommended owned storage, main design must select a visible index policy. A practical merged view lists one thread for the current owner plus eligible legacy pages, uses the actual union of page numbers, and prefers the current owner's new-version page if both stores contain the same page number. Missing new-version page may resolve to the legacy raw file locally; an existing but invalid/foreign-owner envelope must fail, not quietly substitute another snapshot. If duplicate-page preference is chosen, define how a later normal-page save replaces the current owner's entry so an old App copy cannot permanently shadow fresher data. Writing all newly attributable pages as envelopes in the owner root resolves that issue but needs accurate provenance for legacy account retries too. This provenance policy is for the main network design to decide.

Legacy data has no reliable owner. Continuing to read it preserves compatibility; it does **not** establish that old caches were isolated. Do not silently relabel or migrate those files to whichever account happens to be selected.

### 7. Expected implementation touch points

Exact class names for new helpers are recommendations; no code was changed by this research.

| File / seam | Expected change |
| --- | --- |
| New app-level App DTO + converter, alongside `app/sp/phone/mvp/model/convert/ArticleConvertFactory.java:39` | Nullable wire-presence handling, pure pagination/identity validator, native model adapter. Renderer details belong to the other research topic. |
| `app/sp/phone/http/bean/ThreadData.java:18` | Carry response format and non-secret attempted-owner provenance for save/replay; avoid storing a Cookie here. |
| `app/sp/phone/mvp/model/ArticleListModel.java:117`, `:139` | Use immutable cache write input and explicit codec/store dispatch; preserve asynchronous success/error behavior. |
| `app/sp/phone/mvp/presenter/ArticlePageCache.java:26` and its Presenter call site | Preserve repaired eligibility/description cloning; reject an App save without valid provenance/paging admission. Introduce a separate prepared cache record if changing the model signature is cleaner than adding networking fields to navigation params. |
| New pure cache codec + cache index/resolver in `app/sp/phone/mvp/model/` or a dedicated local-cache package | Version/format/identity validation and owned-versus-legacy file resolution, shared by every consumer. |
| `app/sp/phone/mvp/model/TopicListModel.java:66`, `:196` | Enumerate current-owner plus legacy titles with the chosen merge policy; delete only the intended store/owner entries. |
| `app/sp/phone/ui/fragment/TopicCacheFragment.java:89`; `app/sp/phone/param/ArticleListParam.java:34` | Carry a bounded storage/owner selection if needed; update Parcelable/clone expectations and revalidate at read time. |
| `app/gov/anzong/androidnga/activity/ArticleCacheActivity.java:64` | Enumerate valid numeric page files via the resolver; keep actual page-number mapping and count-based tab sizing at `:59`. |
| Cache export/import Presenter entry points invoked by `TopicCacheFragment.java:75` | Locate before implementation if owned root is chosen; ensure the chosen scope preserves account isolation. Exact implementation path was not traversed, so it is not claimed as already understood. |
| `nga_phone_base_3.0/src/test/java/sp/phone/mvp/presenter/ArticlePageCacheTest.java`; new validator/codec/store tests | Extend behavior coverage without replacing existing repaired-cache tests. |

The strict 20-floor choice does not itself require edits to `ArticleTabFragment`, `ArticleListFragment` paging publication, `ArticlePagerAdapter`, `ThreadPageInfo`, or quote-page formulas. Filtered/PID support or arbitrary page-size support would change this boundary and needs explicit design before implementation.

### 8. Focused offline test matrix

| Case | Expected result |
| --- | --- |
| Full-thread page 1, 20-row size, vrows 21, totalPage 2, floors 0–19 | Native result; `__ROWS == 21`, `rowNum == 20`; two tabs. |
| Page 2 of same thread, one final row with lou 20 | Native result; total remains 21, quote floor implies page 2; final page is not prefetched. |
| Single opener (`vrows == 1`, floor 0) | One native page; no fabricated reply count. |
| `currentPage` absent, full floor sequence exactly proves request page | Accept under the explicit normalization rule. |
| Supplied page differs from request; totalPage contradicts vrows | Reject; no success/cache write. |
| perPage 10/40/missing/0, empty result, missing/zero/negative vrows | Reject unsupported structure; finish foreground loading. |
| Floor gap/duplicate/sticky insertion, cross-thread row, duplicate reply PID | Reject under the initial full-thread admission rule. |
| PID/author/search request | No silent broadening to full-thread App load; preserve context in existing error/browser path. |
| Old unwrapped page | Legacy parser only; previous cache description still readable. |
| Valid App envelope while compatibility preference is off | App parser locally; no request. |
| Same owner, mixed formats on sparse pages `[2, 7]` | Correct parser for each actual page; two equal-width tabs. |
| Malformed/unknown envelope, wrong tid/page/owner, blank raw | Cache failure; neither alternate parser guessing nor network fallback. |
| Prepared metadata absent/valid/mismatched/Unicode blank | Existing `ArticlePageCacheTest` outcomes preserved, including notification → 显示全部 flow. |
| Account changes before save or replay callback | Reject/discard stale owned operation according to snapshot policy; do not relabel it. |
| Account A and B save same tid/page in owned storage | Separate pages/descriptions; A's title/body not listed or loaded under B. |
| Delete/list/export/import with owned and legacy stores | Only selected/current-owner scope is affected; legacy compatibility and explicitly chosen export behavior are verified. |
| New file parser rerenders page-level image prefix | Raw prefix reaches existing image normalization after reopening; no global-prefix contamination. |

Use pure synthetic JVM fixtures for validator, codec and temporary-directory store tests; retain app unit, request-state and prefetch coverage. Apply the Android quality gate (debug build/unit tests/lint and zero Error/Fatal in every module's lint XML). No test here requires real NGA traffic, account storage access, ADB, installation, or instrumentation.

### External references / versions

- Upstream feature: <https://github.com/Justwen/NGA-CLIENT-VER-OPEN-SOURCE/commit/2becba2acc3f6c85340424cd09bb03fa7d759db0>
- Supplied final upstream tree: <https://github.com/Justwen/NGA-CLIENT-VER-OPEN-SOURCE/tree/22ba3082501bcbb08f52a66d787f970f59c2dda7>
- Existing legacy parser uses Fastjson 1 (`com.alibaba.fastjson`); upstream App parser uses Fastjson 2. This topic recommends an explicit local adapter/codec, not a project-wide JSON dependency migration.

### Related specs

- `.trellis/workflow.md`: planning-only research; this file does not activate implementation.
- `.trellis/spec/backend/thread-page-cache-contract.md`: full-thread eligibility, metadata preservation, selected-page clone and existing directory/description behavior. The selected new-format extension must be recorded as an intentional extension to this contract.
- `.trellis/spec/backend/thread-page-prefetch-contract.md`: 1-based page numbers, last-page exclusion, normal-only background requests, foreground promotion/demotion, DETACH behavior.
- `.trellis/spec/backend/nga-platform-access-rules.md`: source evidence versus current API claims, account-scoped persistence, bounded/offline validation and no live traffic by default.
- `.trellis/spec/backend/network-foundation-contract.md`: attempted-account ownership and parser/cache/error boundaries; networking details remain with the main research topic.
- `.trellis/spec/backend/android-quality-guidelines.md`: offline quality gate; device access is opt-in.

## Caveats / Not Found

- There is no independently verified `vrows`/filtered pagination/App sticky-row fixture. The proposed admission rule deliberately supports only responses demonstrably compatible with the existing 20-floor UI; it may reject legitimate variants until evidence supports them.
- No guarantee of App endpoint availability, business success-code semantics, nested attachment/comment formats, or correct PID/author filtering can be drawn from this source inspection. Those are not filled in by assumptions here.
- New cache ownership affects more than the page parser. The main design still chooses the store/index policy and whether newly saved normal responses also receive proven owner snapshots. Existing caches cannot be retroactively attributed safely.
- Export/import implementation, cache activity account-switch lifecycle, and Activity launch page-selection behavior were not fully traversed in this bounded research. Their relevant seams are identified above; no claim of complete behavior coverage is made.
- No product source, specification, task metadata, or other task directory was edited; no Git, device, account-file, network or build operation was executed by this researcher.
