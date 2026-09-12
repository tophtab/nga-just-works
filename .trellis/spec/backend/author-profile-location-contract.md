# USER.PROFILE Author Location Enrichment

## 1. Scope / Trigger

This is a current-fork contract for `sp.phone.profile`, thread-floor metadata,
and supplementary author reads following `THREAD.PAGE` delivery. Use it when
changing location parsing, cache persistence, dispatch, account capture, or
`ArticleListFragment` / `ArticleListAdapter` integration.

The value is the author's latest publicly observed profile `ipLoc`, not the
location of a historical reply. The [operation registry](./nga-platform-operation-registry.md)
records the pinned Justwen `USER.PROFILE` source separately. Offline fixtures
verify this fork's behavior; they do not prove current NGA availability or a
safe server request rate.

## 2. Signatures

```java
ProfileSession.create(String origin, String uid, String cid, String userAgent)
ProfileLocationResult ProfileLocationParser.parse(String source, int requestedUid)
Set<Integer> ArticleAuthorIds.fromPage(ThreadData page)
AuthorLocationService.Page AuthorLocationService.bind(
        Context context, LifecycleOwner owner,
        Consumer<AuthorLocationRepository.Snapshot> display)
void AuthorLocationService.Page.deliver(ThreadData data, boolean online)
AuthorLocationRepository.Subscription AuthorLocationRepository.subscribe(
        Collection<Integer> authors, boolean online, Consumer<Snapshot> listener)
String AuthorLocationRepository.Snapshot.location(int author, long now)
void ArticleListAdapter.setAuthorLocations(AuthorLocationRepository.Snapshot locations)
```

The repository's transport seam returns cancellation and accepts one immutable
`ProfileSession`, one positive author UID, and a terminal result callback.
Repository methods and completions run on the Android main thread; network and
serialized file I/O run off it. A fake executor, clock, session source, and
transport exercise the same repository on the host JVM.

## 3. Contracts

### Delivery and presentation

- Existing thread loading/prefetch owns page selection. At successful page
  delivery, copy every distinct positive, nonanonymous row author; do not infer
  authors from bound holders, the viewport, or a new fixed page-count window.
- Normal and offscreen-prefetched pages share the repository immediately and
  independently. There is no all-pages barrier. The current-plus-two planner
  can produce about sixty distinct authors on a cold load; sixty is an estimate,
  not a batch size or request cap. Keep its strict final-page exclusion.
- Compatibility-reader acceptance precedes retaining, rendering, or enriching
  a page. Validate generation, effective page, and account/settings environment
  again before retained-view replay; saved-page replay also checks its owner.
  Reader invalidation clears retained data and obsolete location consumers.
  Both normal and prefetched successful callbacks deliver accepted pages while
  offscreen; foreground checks still govern source changes and failure UI.
- Saved-page reads and retained-view recreation use `online=false`. Restoring a
  view, binding a holder, or resuming a page must not itself create network work.
  A cache-only subscription must not call the dispatcher, including when a
  server pause just expired and another online consumer still has queued work.
  Location data is separate from the existing raw thread-page cache format.
- Render bodies without waiting for location. Show `IP 属地：广东   发帖：123`
  when known, or post count alone otherwise. Remove level/reputation from floor
  detail only; preserve underlying profile/statistics and other author actions.
- Asynchronous location changes use `AuthorMetadataPayload` and bind only
  `tv_detail`. Validate data generation, author UID, and bound row identity.
  Never reload the body WebView or run a full-list bind for these callbacks.
- Close obsolete consumers on data replacement/view destruction. Old snapshots
  carry an invalidatable session epoch; expiry is also checked when reading a
  snapshot. View recreation reuses retained thread data and cached observations.
  Ordinary STARTED/offscreen or Activity-stopped pages keep their view owners
  and still enrich. If a view was actually destroyed, a later body completion
  is retained without starting new work for its dead location consumer. Do not
  turn that owner-destruction guard into a foreground or page-range restriction.

### Request, identity, and parser

- Use HTTPS on exactly `bbs.nga.cn`, `bbs.ngacn.cc`, `nga.178.com`, or
  `ngabbs.com`, with default port 443. Reject userinfo, a nonroot base path,
  query/fragment, host suffix tricks, and unsupported configured origins.
- GET `/nuke.php?__lib=ucp&__act=get&lite=js&noprefix&uid=<author>` with profile
  Referer `/nuke.php?func=ucp&lite=jsx&uid=<author>`. Supply the captured Cookie,
  browser `User-Agent`, and `X-User-Agent: Nga_Official` explicitly. Guest scope
  is account UID `0` with an empty Cookie. Validate UID/CID/header bounds before
  constructing a request; never read mutable account state in an interceptor.
- `UserManager` can notify its new active index before assigning `activeUser`;
  `removeUser` can leave that active-user object stale afterward. Invalidate
  immediately on account list/index signals, then capture copied UID/CID from
  the settled list/index on the next main-loop turn. Also detect same-UID
  credential replacement and origin/UA preference changes. Do not repair this
  operation by changing the account manager's global behavior.
- The dedicated client has 10-second connect/read and 20-second call limits,
  no redirects or connection retries, and a 256 KiB decompressed response bound.
  Decode GBK strictly. Do not attach shared raw-body logging or UI side effects.
- OkHttp 3.12 can repeat `503 + Retry-After: 0` despite
  `retryOnConnectionFailure(false)`. The operation's network interceptor removes
  only the 503 retry hint before its follow-up layer, retaining status/body and
  all 429 retry metadata. An actual loopback-server test must pin physical
  request count, not merely the number of calls to the transport mock.
- Share `ProfileEnvelopeParser` with `JsonProfileLoadTask` for the established
  JS/comment and numeric-token repairs. The profile screen keeps its existing
  profile building; its local raw parse-failure log is removed.
- Require `data.0` to be a profile object. If `uid` is present it must match the
  requested author. Without it, require nonblank `username` and an established
  profile discriminator (`posts`, `group`, or `regdate`). A missing/blank
  `ipLoc` in a valid profile is a valid empty observation.
- A displayed location is trimmed plain text of at most 80 code points, with
  letters and the parser's narrow spacing/punctuation allowlist. Reject
  placeholders, controls, HTML, URLs, numeric/raw-IP forms, and nonstring values.

### Cache, queue, and stop ownership

| Item | Contract |
| --- | --- |
| Key | Normalized origin + viewing-account UID + author UID |
| Success / valid empty | Retain the latest observation for 24 hours |
| Ordinary failure | Suppress the author key for 10 minutes |
| HTTP 429 | Pause the origin/account for at least 30 minutes, or longer valid delta-seconds / RFC 1123 `Retry-After` |
| Authentication / challenge / site rejection | Stop the captured immutable session in process memory, including its credentials and UA |
| Concurrency | One physical supplementary request in flight across all page consumers |
| Dispatch | Reuse queued/in-flight keys; immediately use a free slot; no normal-request interval or timer |
| Expiry / unpause | Reconsider on later work events; expiry alone does not start a request |
| Cancellation | Retain the physical slot until the canceled call's terminal callback |
| Orphaned queued work | Remove an unsent author only when no valid online consumer needs it |

Persist disposable data at `cacheDir/author-locations-v1.json`, encoded as UTF-8:

```json
{"version":1,"entries":[{"origin":"https://bbs.nga.cn","account":"0","author":42,"kind":"OBSERVATION","location":"广东","observed":1000,"expires":86401000}]}
```

`kind` is `OBSERVATION`, `FAILURE`, or `RATE_LIMIT`; `location` is nullable.
Rate-limit records use author `0`, while author observations/failures require a
positive UID. Enforce exact observation/failure durations and the minimum
rate-limit duration on restore. Bound the store to 1,000 entries and 1 MiB,
write through a temporary file with atomic replacement where supported, and
treat corruption/unsupported versions as misses. Evict oldest ordinary records
before server-pause guards. Cache eviction or OS cache cleanup can cause an
earlier lookup; this is not scheduled polling.

Initial restore completes before queued requests dispatch, avoiding a cold
network burst ahead of persistent cache lookup. Never persist Cookies, CID, UA,
full profiles, raw IPs, or a location history.

A stop response belongs to the request identity independently of its UI epoch.
Record a received 429 or session rejection against the captured scope **before**
discarding obsolete consumers. A late stop must survive same-session
invalidation and switching away/back, while not clearing another account's
queue. A stale successful response cannot populate another session's UI/cache.
Cached observations and 429 guards are account-scoped; credential-specific
rejections must not block a replacement credential for the same UID.

## 4. Validation & Error Matrix

| Condition | Required result |
| --- | --- |
| Valid matching profile with location | Publish metadata and cache for 24 hours |
| Valid profile with absent / blank location | Cache valid empty; show post count only |
| Invalid author / session / configured origin | No supplementary request |
| Malformed profile JSON, invalid location, I/O failure, oversized / undecodable body | Per-author 10-minute failure cooldown |
| Redirect, HTTP 401/403, site error, mismatched UID, or non-profile text/HTML after wrapper normalization | Stop captured session; no redirect, identity rotation, or next-author loop |
| HTTP 429, including a queued completion after view/account invalidation | Persist account pause; preserve longer server delay |
| HTTP 5xx containing a valid profile-shaped body | Ordinary failure, never success |
| Bounded HTTP 5xx challenge body | Session rejection, never empty success |
| Fresh persisted cache, including valid empty | No profile request after process recreation |
| Missing/corrupt/expired cache | Lookup only when an online delivery requires it |
| Offline page or retained view rebound | Cache-only display, including misses |
| Cache-only subscription after a 429 pause expires | Do not resume another page's queue; later real online work may resume it |
| Data/author/view generation changed | Ignore obsolete UI payload; never reload a body WebView |

## 5. Good / Base / Bad Cases

- **Good**: one delivered page needs authors A/B; an independently prefetched
  page needs B/C. B shares queued/in-flight work, and C starts as soon as the
  physical slot becomes free, without advancing a clock.
- **Base**: an author has a fresh empty observation. The floor shows its post
  count and subsequent deliveries do not repeat that lookup during the TTL.
- **Bad**: query once per holder, start a separate three-page batch, sleep
  between normal requests, refresh locations on every resume, or discard a
  received server stop because the original view disappeared.

## 6. Tests Required

- `ProfileLocationParserTest` and `ProfileSessionTest`: known wrappers, valid
  empty, UID binding, malformed/non-profile responses, location bounds, exact
  origins, Cookie-safe input, and credential snapshot equality.
- `AuthorLocationRepositoryTest`: distinct delivered authors, duplicate sharing,
  immediate dispatch, incremental prefetch, fake-clock TTL/failure/429 boundaries,
  persistent late pauses, same-UID credential isolation, obsolete callbacks,
  shared-consumer disposal, and cache-only readers that cannot resume paused
  online work when its pause expires.
- `AuthorLocationStoreTest`: atomic read-back, corruption/version/bounds, TTL
  validation, latest observations, and retention of server-pause guards.
- `ProfileLocationTransportTest`: explicit wire identity, strict bounded GBK,
  stop classification, Retry-After, and one physical request with the actual
  configured client against loopback HTTP 503/429 fixtures.
- `ArticleAuthorLocationContractTest`: common delivery integration, complete
  author collection, view/account lifetime boundaries, and metadata-only binding.
  Keep existing page-state, prefetch, refresh, and page-cache regressions green.
- Run the app debug build/unit/lint and repository Android quality gate. Source
  contracts do not constitute Android UI execution or live NGA verification.

## 7. Wrong vs Correct

Wrong for an asynchronous location-only completion:

```java
adapter.notifyDataSetChanged(); // Rebinds article bodies and their WebViews.
```

Correct:

```java
adapter.setAuthorLocations(snapshot); // Checked author metadata payloads only.
```

Likewise, `retryOnConnectionFailure(false)` alone is insufficient evidence of
one physical HTTP request. Keep the dedicated 503 follow-up prevention and its
real-client regression when changing OkHttp configuration or version.
