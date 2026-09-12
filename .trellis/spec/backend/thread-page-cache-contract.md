# THREAD.PAGE Local Page Cache

## 1. Scope / Trigger

Use this contract when changing thread toolbar cache eligibility, preparing a
page snapshot, or listing/opening/reading/deleting cached pages. It preserves
the repaired metadata preparation from `6203dad5` and adds explicit owner and
source/page-layout storage for the [compatibility reader](./thread-detail-compat-contract.md).
Cache replay is local and never triggers ordinary/App requests or a fallback.

## 2. Signatures

```java
public static boolean ArticlePageCache.isCacheableContext(ArticleListParam param)
// Package-local preparation seam used by ArticleListPresenter:
static ArticleListParam ArticlePageCache.prepare(ArticleListParam param, ThreadData data)
void ArticleListModel.cachePage(ArticleListParam param, ThreadData data, OnHttpCallBack<String> callback)
void ArticleListModel.loadCachePage(ArticleListParam param, OnHttpCallBack<ThreadData> callback)
```

App-local storage signatures in `sp.phone.mvp.model.thread`:

```kotlin
ArticleCacheEntry(tid: Int, owner: String?, layoutId: String?)
ArticleCacheEntry.from(param: ArticleListParam): ArticleCacheEntry
ArticleCacheEntry.forPaging(paging: ArticlePagingInfo): ArticleCacheEntry
ArticleCacheWrite(entry: ArticleCacheEntry, page: Int, topicInfo: String,
    raw: String, paging: ArticlePagingInfo?)
ArticleCacheStore(filesDir: File)
ArticleCacheStore.list(currentOwner: String?): List<ArticleCacheRecord>
ArticleCacheStore.pages(entry: ArticleCacheEntry, currentOwner: String?): List<Int>
ArticleCacheStore.read(entry: ArticleCacheEntry, page: Int, currentOwner: String?): ArticleStoredPage
ArticleCacheStore.write(write: ArticleCacheWrite, ownerNow: Supplier<String?>): ArticleCacheEntry
ArticleCacheStore.delete(entry: ArticleCacheEntry, currentOwner: String?)
ArticleCacheCodec.encode(write: ArticleCacheWrite): String
ArticleCacheCodec.decode(text: String, entry: ArticleCacheEntry, page: Int): ArticleStoredPage
ArticleCacheReplay.parse(stored: ArticleStoredPage): ThreadData
LegacyArticleCacheArchive.exportArchive(filesDir: File, output: OutputStream): Int
LegacyArticleCacheArchive.importArchive(input: InputStream, filesDir: File): Int
```

The layouts are:

```text
files/cache/<tid>/<tid>.json                     # Legacy topic description
files/cache/<tid>/<page>.json                    # Legacy normal raw response
files/thread-cache-v1/<owner>/<tid>/<layout>/topic.json
files/thread-cache-v1/<owner>/<tid>/<layout>/pages/<page>.json
```

Legacy handles have null owner/layout together. An owned handle uses a positive
decimal account UID or `guest`; no Cookie/cid is a path or persisted field.
Known layouts are `read_php-<positive size>` or `app_api-<positive size>`.
Unknown sizes use `<source>-window-<32 lowercase hexadecimal UUID characters>`
for an independent snapshot, never a merged set of unknown-size pages.

`ArticleListParam.cacheOwner/cacheLayoutId` carry the validated handle through
navigation. `ArticleCacheRecord.asThreadInfo()` uses the existing Fastjson
description bean plus transient cache handle/summary fields. Those fields must
be excluded from JSON serialization and deserialization.

## 3. Contracts

### Eligibility and metadata preparation

- Eligibility requires a nonnull parameter object, `tid > 0`, `pid == 0`,
  `authorId == 0`, `searchPost == 0`, and `loadCache == false`.
- Do not gate toolbar visibility on `topicInfo` or `page > 0`. Notification →
  reply → `显示全部` passes tid/title without topic-list metadata, and a parent
  pager may initially have `page == 0`. Save preparation separately requires
  the selected child's 1-based page, nonblank `ThreadData.rawData`, a nonempty
  row list and complete content.
- When normalized paging exists, its query, resolved tid, effective page and
  generation must match the selected child. Ownerless App data is not a legacy
  cache candidate. Do not use `ThreadPageInfo.page` as page authority.
- If loaded thread metadata is present, its tid must match the request. A usable
  cache description has that same tid and a nonblank subject; optional author,
  board, and reply fields retain the existing bean behavior.
- Preserve a supplied valid `topicInfo` string verbatim, including its extra
  fields. It remains usable when the response omits thread metadata. Reject an
  invalid supplied description instead of silently replacing it.
- When `topicInfo` is absent or blank, serialize the already parsed
  `ThreadData.getThreadInfo()` with `JSON.toJSONString`. Do not issue a second
  request, reparse the wire response in the UI, or fabricate title/author fields.
- Blank checks must cover Unicode whitespace with
  `Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint)`;
  `String.trim()` alone misses ideographic spaces and nonbreaking spaces.
- Preparation returns a cloned `ArticleListParam` carrying the completed
  description. It must not mutate the parent pager, selected child, or loaded
  metadata. The model writes asynchronously, so a later navigation change must
  not alter the cache write's tid/page/description.
- The presenter keeps its existing no-op before any page has loaded. Invalid
  preparation returns null; the presenter reports the existing cache failure
  and does not call the model. The model reports success only after both writes
  succeed; presenter feedback must still belong to the current request/view.
- Saving a filtered or single-reply result into a full-page cache slot is
  forbidden. `显示全部` creates a new full-query read; its successfully loaded
  page can be saved using the same metadata preparation.

### Envelope and replay

- New-chain ordinary/App responses with a reliable owner use an envelope;
  ownerless legacy normal data keeps the old raw layout. Do not migrate, relabel
  or fabricate ownership for existing raw files.
- An owned page records `schema="thread-page"`, numeric `version=1`,
  `format="read_php"|"app_api"`, `owner`, `tid`, `queryKind="FULL"`,
  `layoutId`, effective `page`, original `requestedPage`, nullable `pageSize`,
  `pageBasis`, and original decoded `raw`. Preserve an explicit null page size.
  Do not persist rendered HTML, credentials or reader generation.
- Replay dispatch is explicit: legacy raw uses `ArticleConvertFactory`, owned
  ordinary raw uses `NormalArticleParser`, and owned App raw uses
  `AppArticleParser`. The compatibility setting is irrelevant to local replay.
  Validate the parsed query/tid/effective page, source, size and page basis
  against the stored envelope; new pages must also be complete and nonempty.
- Existing but malformed, unknown-version, wrong-owner or wrong-layout owned
  pages fail locally. Do not try a different parser, another cache layout or
  the network to disguise the failure.

### Store, index and UI delivery

- One `ArticleCacheStore` owns list/open/read/write/delete resolution. The
  current account's owned versions and legacy entries remain distinct records;
  titles and page bodies share the same owner/layout directory. Different
  accounts, sources or page sizes must not overwrite or merge each other.
- Known layouts merge only their actual positive page numbers, sorted
  numerically. Unknown-size saves get separate window handles. Updating or
  deleting a selected handle affects only that version; the old cache and
  other accounts/layouts remain intact.
- Handles validate their UID/tid/layout components; callers do not pass file
  paths. Storage rejects paths escaping its private root or resolving through
  symlinks. I/O is bounded: UTF-8 page files at most 32 MiB, descriptions at most
  512 KiB, and at most 10,000 entries in a traversed directory.
- Writes use immutable text/paging snapshots, background I/O, staged files and
  replacement. Recheck the account before replacement and feedback; an account
  change cannot redirect an old save into the new account's directory.
- Read/list delivery also rechecks the current account. A cache reader returning
  after an account change must not display the old account's page. List requests
  carry a sequence so older results cannot replace newer listings.
- `ArticleCacheActivity` calls `super.onCreate` before completing a missing-
  argument exit. It opens only the selected handle, retains actual sparse page
  labels, and uses `setTabOnScreenLimit(count <= 5 ? count : 0)` before tab
  setup. The number of stored entries controls tab width, not the largest page.
- List subtitles describe ordinary/compatibility/legacy display and saved-page
  count. Keep internal layout/schema fields out of user-facing labels.
- Legacy zip import/export remains limited to `files/cache`. The UI states
  that new-format caches are not included; this batch does not implement their
  zip transport or silently include the new storage root. Export enumerates
  validated legacy records from the same store as the cache list, writes their
  topic description and readable pages, and returns the exported thread count;
  it does not copy arbitrary files or temporary files below the legacy root.
  Import accepts only
  legacy `cache/<positive tid>/<positive number>.json` files and their directory
  entries, stages and checks archive paths/byte bounds before replacement, and rejects
  paths targeting the owned root. This restriction is necessary because the
  former generic unzip destination was the entire private files directory.
  Limit imports to 10,000 entries, 32 MiB per file and 256 MiB total decoded data;
  reject duplicate file paths and symlink destinations. The importer returns
  the number of imported files, not the exported thread count. Do not extend
  this helper into a general archive importer.

## 4. Validation & Error Matrix

| Input | Required outcome |
| --- | --- |
| Full-thread parent, page 0, no launch metadata | Toolbar cache action is eligible |
| Selected child page 7, no launch metadata, valid loaded metadata | Snapshot page 7; serialize a readable description |
| Valid supplied description, same-thread loaded metadata | Preserve supplied JSON verbatim |
| Valid supplied description, absent loaded metadata | Keep compatibility with the existing description |
| Nonnull loaded metadata has a different tid | Reject preparation, including when supplied metadata is valid |
| Missing raw data, invalid page, or unusable description | No cache write or success feedback |
| Subject consists only of ASCII/ideographic/nonbreaking spaces | Reject the unusable description |
| pid, author filter, reply search, or cache-reader context | Hide the action and reject preparation |
| Caller parameters change after preparation | Previously prepared write target and description stay unchanged |
| Empty/incomplete content or mismatched query/page/generation | Reject preparation; no complete-page cache |
| Main body is readable but a known nested comment is incomplete | Reject complete-page caching while preserving local readable content |
| Same tid/page under accounts A/B or ordinary/App sizes | Separate entries, titles, bodies and deletion targets |
| Two unknown-size saves | Two independent window entries |
| Cached pages 2, 7 and 10 in one known layout | Three numerically ordered tabs with original labels |
| App page saved, compatibility later disabled | Local App parser replay, no request |
| Malformed owned page or unknown schema/version/source | Explicit local failure, no alternate parser/cache/network |
| Account changes before write/replay delivery | Reject/suppress stale operation; no cross-account data |
| Cache Activity lacks launch parameters | Complete required superclass lifecycle, then finish safely |
| User exports legacy cache zip | Include only old-format cache and disclose that scope |

## 5. Good / Base / Bad Cases

- **Good**: open a reply from recent notifications, select `显示全部`, load the
  full thread, and save the selected page using its existing typed metadata.
- **Base**: opening from a board/topic list continues using its supplied
  description. Existing ownerless raw caches continue to open locally.
- **Bad**: remove only the toolbar's null check, pass missing metadata to the
  model, save an author-filtered page into a full-thread slot, or send mutable
  live pager parameters to the asynchronous writer.

## 6. Tests Required

`ArticlePageCacheTest` covers eligibility independently of parent page number,
filtered/reply/cache exclusion, matching thread identity, missing and invalid
metadata, Unicode blank values, supplied JSON preservation, Fastjson description
read-back, and stable selected-page snapshots without input mutation. Storage
tests use temporary directories and synthetic ordinary/App pages to cover
owner/layout isolation, repeated saves, unknown-size windows, sparse ordering,
selected-version deletion, explicit replay, invalid envelopes and account
changes. UI/source regressions cover launch guards, cache list identity,
one-through-five equal tabs and legacy zip scope. Keep the existing refresh,
page-request-state, and prefetch tests green.

Run the app debug build/unit/lint gate and the repository Android quality gate.
Inspect lint XML for every Android module because a successful lint process can
still contain Error/Fatal issues. Device operations remain opt-in; offline
codec/source checks do not constitute device UI or live NGA verification.

## 7. Wrong vs Correct

### Wrong

```java
menu.findItem(R.id.menu_download).setVisible(param.topicInfo != null);
// Navigation provenance incorrectly decides whether the loaded thread can be saved.
```

### Correct

```java
menu.findItem(R.id.menu_download)
        .setVisible(ArticlePageCache.isCacheableContext(param));
ArticleListParam snapshot = ArticlePageCache.prepare(selectedPageParam, loadedPage);
if (snapshot != null) {
    model.cachePage(snapshot, loadedPage, callback);
}
```

The toolbar uses full-thread eligibility; the presenter separately prepares and
validates a snapshot of the selected loaded page before storage selects its
explicit legacy or owned layout.
