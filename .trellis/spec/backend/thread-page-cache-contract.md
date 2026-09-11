# THREAD.PAGE Local Page Cache

## 1. Scope / Trigger

Use this contract when changing thread toolbar cache eligibility, preparing a
cached page, or reading the existing local cache descriptions. This is a
current-fork UI/persistence contract for already loaded `THREAD.PAGE` data; the
operation's request, parser, account handling, and prefetch behavior are unchanged.

## 2. Signatures

```java
public static boolean ArticlePageCache.isCacheableContext(ArticleListParam param)
// Package-local preparation seam used by ArticleListPresenter:
static ArticleListParam ArticlePageCache.prepare(ArticleListParam param, ThreadData data)
void ArticleListModel.cachePage(ArticleListParam param, String rawData)
```

The existing on-disk layout is:

```text
files/cache/<tid>/<tid>.json   # Fastjson ThreadPageInfo description
files/cache/<tid>/<page>.json  # Existing parsed page's raw-data string
```

`TopicListModel.loadCache` decodes the description with
`JSON.parseObject(rawData, ThreadPageInfo.class)`. `ArticleCacheActivity` and
`ArticlePagerAdapter` keep the actual cached page numbers when opening pages.

## 3. Contracts

- Eligibility requires a nonnull parameter object, `tid > 0`, `pid == 0`,
  `authorId == 0`, `searchPost == 0`, and `loadCache == false`.
- Do not gate toolbar visibility on `topicInfo` or `page > 0`. Notification →
  reply → `显示全部` passes tid/title without topic-list metadata, and a parent
  pager may initially have `page == 0`. Save preparation separately requires
  the selected child's 1-based page and nonblank `ThreadData.rawData`.
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
  and does not call the model. Success feedback belongs to the existing model
  after both file writes succeed.
- Saving a filtered or single-reply result into a full-page cache slot is
  forbidden. Keep the existing current-page event and model write/read path.

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

## 5. Good / Base / Bad Cases

- **Good**: open a reply from recent notifications, select `显示全部`, load the
  full thread, and save the selected page using its existing typed metadata.
- **Base**: opening from a board/topic list continues using its supplied
  description and the same cache file format.
- **Bad**: remove only the toolbar's null check, pass missing metadata to the
  model, save an author-filtered page into a full-thread slot, or send mutable
  live pager parameters to the asynchronous writer.

## 6. Tests Required

`ArticlePageCacheTest` covers eligibility independently of parent page number,
filtered/reply/cache exclusion, matching thread identity, missing and invalid
metadata, Unicode blank values, supplied JSON preservation, Fastjson description
read-back, and stable selected-page snapshots without input mutation. Keep the
existing refresh, page-request-state, and prefetch tests green.

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
    model.cachePage(snapshot, loadedPage.getRawData());
}
```

The toolbar uses full-thread eligibility; the presenter separately prepares and
validates a snapshot of the selected loaded page before the existing write.
