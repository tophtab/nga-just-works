# THREAD.PAGE Compatibility Reader

## 1. Scope / Trigger

Use this contract when changing the laboratory compatibility setting, App
thread reads, normalized query/page metadata, source transitions, row projection,
or native navigation consuming these values. The adapter lives in the app's
`sp.phone.mvp.model.thread` package and feeds the existing Java/Rx reader.
The retained core `HtmlCommentBuilder` also participates when rendering known
nested ordinary comments; its header handling must accept incomplete-content
display text without fabricating or truncating source.

The App operation is source-observed in Justwen commit
`2becba2acc3f6c85340424cd09bb03fa7d759db0`, not in the July pinned network
snapshot. Source implementation is not a live-service guarantee. Reuse its
existing request/DTO/row mapping; do not invent dedicated attachment, hot-reply,
comment-parent, or score protocols for fields the upstream parser never used.

See [platform access](./nga-platform-access-rules.md),
[network foundation](./network-foundation-contract.md),
[prefetch](./thread-page-prefetch-contract.md), and
[local cache](./thread-page-cache-contract.md) for adjacent boundaries.

## 2. Signatures

Source-observed request:

```text
POST /app_api.php?__lib=post&__act=list
form: page, optional nonzero tid, pid, authorid
headers: explicit Cookie, browser User-Agent, X-User-Agent: Nga_Official
setting: pref_show_with_app_api = false by default
```

`searchPost` is a local routing/query-disposition field in the inspected reader;
neither source request builder sends it to App. `perPage` is a response field,
not a new request option.

App-local types and helpers:

```kotlin
ArticleQuery(tid: Int, pid: Int, authorId: Int, searchPost: Int)
ArticleQuery.from(param: ArticleListParam): ArticleQuery
ArticleRequestKey(query: ArticleQuery, generation: Long,
    source: ArticleSource, pageSize: Int?, owner: String?, page: Int)
ArticleNavigation.showAll(param: ArticleListParam, data: ThreadData?): ArticleListParam?
ArticleNavigation.quoteAddress(row: ThreadRowInfo): String
ArticleNavigation.alignmentPage(anchor: ArticleAnchor?, result: ThreadData): Int?
ArticleNavigation.handoffNotice(data: ThreadData, anchor: ArticleAnchor?): String?
ArticleSourceText.normalizeReplyHeader(source: String): String
ArticleSourceText.renderBody(row: ThreadRowInfo): String?
ArticleRowPresentation.canReply(row: ThreadRowInfo): Boolean
ArticleQuote.authorMarkup(row: ThreadRowInfo): String
ArticleQuote.mention(row: ThreadRowInfo): String?
ArticleByteClient.read(operation: ArticleOperation): Observable<String>
ArticleByteClient.validateOrigin(origin: String): String
ArticleReaderSession.key(page: Int): ArticleRequestKey
ArticleReaderSession.adopt(key: ArticleRequestKey, data: ThreadData, foreground: Boolean): Int
```

The existing core comment builder uses package-local
`HtmlCommentBuilder.stripReplyHeader(String content): String` for its complete
leading reply-header rule. Null becomes empty display text; nonmatching text
is preserved verbatim. App display projection uses `renderBody` for both main
rows and known nested comments without changing their editable source.

`ArticlePagingInfo` carries query/source/resolvedTid, requested/effective page,
nullable pageSize/totalPages/totalRows, pageBasis, floor-mapping capability,
invalid-metadata status, reportedCurrentPage, owner and generation.
`ThreadData` carries that context independently of original row count/raw data.
`ArticleRowPresentation` carries explicit kind, floor/user/score/source validity,
and nullable UID-based OP identity. It is display metadata, not a fabricated
server response schema.

## 3. Contracts

### Query identity and navigation

- Full, author-filtered and PID/search lookup are distinct query kinds. Retain
  all supplied `pid` and `authorid` fields throughout fallback and page reads.
- A PID-only result establishes `resolvedTid` from validated matching data.
  Do not derive it from PID arithmetic or an unrelated first result. A lookup
  must contain the target before reporting a successful location.
- `显示全部` builds a fresh full-thread request at page 1 using resolvedTid;
  clear PID, author and search disposition. Retain a launch description only
  after confirming its thread identity. Do not mutate the lookup request.
- Server page, global floor and adapter index are different coordinates.
  Preserve row order and original floor/PID. Short pages and floor gaps do not
  independently invalidate readable rows.
- Positive reported page size is used as supplied. Missing size remains
  unknown; do not infer it from result length or replace it with 20 for App.
  Reported total pages may support page navigation without floor arithmetic.
- Only a full query may use a documented vrows/page-size fallback to derive
  total pages. Filtered counts are not global floor bounds. Invalid optional
  paging metadata reduces navigation capability instead of discarding otherwise
  valid body content or manufacturing a one-page thread.
- A floor-derived page is a candidate. Scroll only after finding the actual
  PID, or actual floor when PID is unavailable, in the loaded list. Keep pending
  anchors until their matching generation/page is ready.
- Outgoing quote hints use the established ordinary 20-floor convention when
  an original floor is available; never substitute App or filtered page numbers.
  PID remains the actual reply-link identity.

### Source and lifecycle

- A new reader starts with the ordinary source. Enabled eligible foreground
  format failure may try App once with the same query/account/model origin.
  Successful switching establishes the source for that reader generation.
  Subsequent selected-page reads and explicit refresh use that source.
- Turning compatibility off, changing account/query, or opening a new reader
  resets source state. Explicit refresh still makes a fresh request; READY is
  not evidence that a user-requested refresh has completed.
- Source/page-size changes invalidate old retained page identities, pending
  work and READY reuse. Generation/account/query checks apply before changing
  data, title, counts or loading state, including delayed UI completions.
  `ArticleShareViewModel.adoptPage` publishes state synchronously; a newly
  created page may consume the handoff before the previous callback returns.
  That previous callback must not reset the new page's READY/loading state.
- Preserve a usable PID/floor anchor across source changes. At most one extra
  foreground alignment request is allowed; never scan unknown pages or alternate
  indefinitely between sources. If alignment fails, make the unavailable
  position explicit instead of reporting a wrong-index success.
  Compute the handoff notice before display consumes the pending anchor. For
  App data, no anchor or no actual target match requires the position-not-kept
  notice, including when the body also needs an incomplete-content notice.
- Background work remains ordinary-source full-query prefetch. Preserve
  next-two, non-final-page planning, coalescing, promotion/demotion and DETACH
  cancellation. Background failure cannot switch source/account or open UI.

### Data projection and errors

- Decode only consumed field types. Preserve the original response separately
  from editable source and formatted HTML. Unknown optional extensions remain
  opaque; their presence or shape alone is not a whole-page failure.
- Upstream App parser does not project `attches`, `hot_post`, `comment_to_id`,
  `html_head_extra`, or calculate score from `vote_good`/`vote_bad`. Do not
  infer parent PID/nesting or execute optional head HTML. Existing inline
  body/media rendering remains available.
- Reuse complete attachment prefixes through `NgaImageHost`; keep manual-host
  priority, historical URL normalization and response-local context.
  Prepare known attachments/comments/blacklist state before rendering HTML.
- Normalize only the confirmed HTML reply-header dialect; preserve unrelated
  source and use literal-safe replacement on renderer/quote input. Preserve the
  App row's editable source; never replace it with formatted HTML. Keep legacy WP preprocessing on
  the normal path rather than assuming App has identical escaping.
- Explicit comment metadata overrides sparse-field heuristics. Known comments
  hide inappropriate comment/author-filter actions. Missing optional author
  details do not turn ordinary rows into comments. Unknown score is not zero.
  Readable independent comment/unknown-kind rows with an actual own PID can
  still use existing source-based reply/quote actions. Row kind alone is not
  a reason to disable an action whose required inputs are known.
- UID OP comparison requires meaningful known identity; shared zero/anonymous
  sentinels cannot create OP badges or enable profile/filter actions.
- Unusable core source with usable identity must remain visibly unavailable,
  not silently omitted. Do not enable source-dependent actions or save an
  incomplete response as a complete cached page. Invalid query/row identity
  remains a result failure.
  Apply this consumed-source check to scoped ordinary reads as well as App:
  legacy Fastjson `getString` coercion of object/array content is not evidence
  of readable source. A valid subject does not make malformed content complete.
  Keep the default-off/legacy parser's existing acceptance path separate, and
  preserve established valid empty/subject/alter/blacklist source semantics.
  A damaged known nested comment does not discard its readable parent or the
  whole page. Retain the child and its identity, project the incomplete notice
  as display input, and mark the page incomplete recursively. The core comment
  builder strips only a complete recognized reply header; headerless text and
  incomplete/unrelated bold markup remain intact. Never add a fake header just
  to satisfy an unconditional substring operation.
- Recognized business/auth/access/rate-limit failures win over residual data.
  Unknown numeric `code` gets no invented meaning: independently validate the
  data branch. Empty/scalar/malformed results are not successful thread pages.
- New scoped transport uses explicit account/origin context, bounded bytes,
  operation-owned charset handling, cancellation and no automatic redirect or
  identity rotation. Preserve the ordinary request contract and keep this
  boundary separate from the legacy global Retrofit converter/interceptors.
- The byte client limits both declared and streamed body length to 4 MiB,
  checks truncation, and closes responses/streams. A declared UTF-8/UTF8,
  GBK/GB2312/GB18030 charset is decoded strictly; missing charset uses the
  explicit source-derived GBK compatibility rule. Invalid/duplicate/unsupported
  declarations fail without trying a succession of encodings.
- Origins are exact HTTPS project hosts on the default port with a root path;
  reject credentials, query/fragment, malformed host preferences or suffix
  lookalikes before constructing an authenticated request. App uses POST form;
  scoped ordinary reads preserve the existing GET parameters. Credentials are
  transient ArticleAccount/ArticleOperation values with redacted string forms.
- Default-off legacy account retry must count accounts before obtaining a next
  Cookie and reject empty/identical credentials. No secret enters response
  metadata, cache records, diagnostics or exception text.

## 4. Validation & Error Matrix

| Input / event | Required behavior |
| --- | --- |
| Compatibility off | No App request; existing reader with account retry guard |
| PID plus author restriction | Preserve both fields; locate actual PID; no full-thread cache |
| Author page with floors 3, 85, 144 | Preserve floors; use filtered page coordinates, not modulo scrolling |
| App page size 10/30/40 or a short final page | Preserve all rows and reported page size; no artificial 20-row regrouping |
| Missing size but valid total pages | Read/page navigation available; unsupported floor arithmetic unavailable |
| Unknown optional sidecar or code with valid data | Preserve source/raw; no fabricated interpretation or blanket veto |
| Ordinary page 7/size20 switches to App size10 | Keep floor120 anchor; at most one page13 alignment read, then verify actual row |
| Previous-generation completion or delayed UI callback | No overwrite of current body/title/count/READY/loading |
| Auth/challenge/rate limit | Stop; no alternate identity or automatic App fallback |
| Missing core content | Visible unavailable state; no incomplete-page cache |
| Scoped ordinary content is an object/array with a valid subject | Keep the row visibly incomplete; do not enable source actions or owned cache |
| Known nested comment has unusable source | Keep parent/child readable with a child notice; mark page incomplete and block cache |
| Readable independent COMMENT/UNKNOWN row has its own valid PID | Reply/quote remain available; no invented parent identity |
| Cache replay while compatibility is off | Dispatch stored format locally; never request the network |

## 5. Good / Base / Bad Cases

- **Good:** reuse App row conversion, render a PID response at its real floor,
  then use resolvedTid for a new full-thread request when the user selects
  `显示全部`.
- **Base:** ordinary full-thread reading retains its 20-floor wrapper and
  existing next-two/non-final prefetch; the compatibility preference is off.
- **Bad:** reject every nonempty unused field, call every row with no avatar a
  comment, put filtered page 1 into a full-thread cache, or reuse a READY page
  after its account/source/layout has changed.

## 6. Tests Required

Use synthetic data and fake transport; never send NGA traffic:

- `AppArticleParserTest`: full/PID/author queries, variable/unknown page metadata,
  optional opaque fields, missing user/score/content, malformed content with a
  usable subject, source/raw preservation and error precedence.
- `NormalArticleParserTest`: ordinary wrapper/raw preservation, WP/source
  handling, attachments/comments/blacklist before rendering, UID identity,
  response-local image prefixes and query validation. Cover consumed-source
  types and incomplete-cache prevention in scoped reads without changing the
  legacy/default-off acceptance path. Include damaged known nested comments,
  preserved parent/child identities and incomplete display projection.
- `ArticleReaderSessionTest`: exact anchors and bounded alignment, old-key
  rejection, background/source handoff, account/settings invalidation,
  count-before-Cookie retries, show-all navigation and neutral quote attribution.
- `ArticleByteClientTest`: synthetic `Call.Factory` requests/bytes, exact URL/
  form/headers, source/page changes with one account snapshot, guest requests,
  origin/redirect/retry policy, strict charset/size handling, body closure,
  HTTP/network classification, redaction and cancellation. No socket is opened.
- `ArticleCacheStoreTest` and existing `ArticlePageCacheTest`: explicit replay,
  owner/layout isolation, independent windows, metadata, selected-page snapshots,
  damaged-page rejection and legacy archive boundaries.
- `ArticleErrorsTest`: BOM/outer whitespace classification as HTML/empty/JSON,
  no fallback for access/empty outcomes, and preserved original raw text.
- `ArticleRowPresentationTest` and core `HtmlCommentBuilderTest`: action
  eligibility from real source/PID rather than row kind; only complete leading
  reply headers are stripped; short, headerless, unrelated and incomplete
  content stays intact.

Preserve existing request-state, prefetch, image-host and current-page refresh
tests. A pure policy test does not replace tracing its presenter/UI callers,
especially synchronous LiveData handoff and delayed view actions.

Run the app/common JVM gate, debug build and repository Android quality gate;
when changing the shared comment builder, include core JVM tests and core lint.
Inspect every required lint XML for zero Error/Fatal. These checks establish
local behavior, not real-service success rate. Device tests remain opt-in.

## 7. Wrong vs Correct

### Wrong

```java
int index = floor % 20;
list.scrollToPosition(index);
// A filtered/sparse/App page does not share this coordinate system.
```

### Correct

```java
int index = anchor.find(data.getRowList());
if (index >= 0) {
    list.scrollToPosition(index);
}
// Otherwise report that the target was not found; do not claim a successful jump.
```
