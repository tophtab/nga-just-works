# USER.PROFILE Author Location Enrichment

## Main branch integration

The integration of `experiment/auto-ip-query` into `main` restores automatic
author-location queries. `ArticleListFragment` binds
`AuthorLocationService` to its view owner and submits accepted online page
deliveries, including offscreen prefetch. Saved pages and retained-view
replays remain cache-only. The repository keeps its body-independent HTTP 503
stop and a 500 ms pause after each completed request.
Supplemental reads now use the Web profile route and its embedded `__UCPUSER`
object, following NGA UserInfo Enhance 2.0.10 and its pinned
[NGA Library 1414880](https://update.greasyfork.org/scripts/486070/1414880/NGA%20Library.js).
The manual profile screen retains its original JSON route.

An authorized live probe on 2026-09-12 used the operation's OkHttp 4.12 client
on the host JVM and one saved account on `bbs.nga.cn`. Two rounds queried the
same first twenty replies, containing eighteen distinct authors, without a
local profile cache. Both rounds returned twenty matching profiles with valid
locations. The 500 ms and 100 ms delays were measured after response completion;
mean request-start intervals were about 589 ms and 170 ms, with ten seconds
between rounds. This short repeated-author sample does not establish an Android
device result, a safe quota, or a comparison with JSON requests at the same rate.
The cause of the reported 503s remains unverified. The maintainer explicitly
selected 500 ms after each terminal callback for the Web-route integration;
the interval is not a guarantee about the server's quota.

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
void AuthorLocationPage.deliver(ThreadData data, boolean online)
void AuthorLocationPage.replay()
boolean AuthorLocationPage.isCurrent(AuthorLocationPage.Delivery delivery)
AuthorLocationRepository.Subscription AuthorLocationRepository.subscribe(
        Collection<Integer> authors, boolean online, Consumer<Snapshot> listener)
// Package-private: the page owns the handle before synchronous initial publication.
AuthorLocationRepository.Subscription AuthorLocationRepository.subscribe(
        Collection<Integer> authors, boolean online, Consumer<Snapshot> listener,
        Consumer<Subscription> onRegistered)
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
- `ArticleListPresenter` also routes `SHOW_READY_DATA` through `setData` on
  resume. Repeating the exact same nonnull `ThreadData` in one
  `AuthorLocationService.Page` is a replay. Its Android-free `AuthorLocationPage`
  controller preserves the existing subscription and online/cache-only intent;
  re-emit its latest current-generation `Delivery` through the lifecycle shell
  without creating a generation, closing the consumer, or entering session-
  settling/repository dispatch. This restores
  metadata if the adapter rebound its body without restarting queries.
  A new Page's retained-data delivery uses `online=false`; later READY replay
  must not promote it to online. Null delivery always clears the remembered
  response and consumer; close also releases the response reference. Session
  invalidation publishes an empty snapshot and must never recover an older
  snapshot from a separate replay cache. Fresh response objects still replace
  the subscription through the handoff below. A response arriving after view
  destruction is retained for later cache-only rendering.
- A fresh nonnull response must not blank valid metadata before a cache hit.
  Keep the current snapshot and active consumer while a replacement awaits
  account/session settling. Track pending replacement sequence independently
  of active output generation so account invalidation can still clear the
  displayed text during that wait. Install the new consumer, which publishes
  its authoritative cache snapshot synchronously, before closing the previous
  consumer. Dispose the previous consumer during the first new publication,
  before the new subscription can dispatch missing work; this prunes removed
  queued authors without interrupting shared in-flight authors. Then reject
  retired output. Validate pending sequence, session signal and close state
  before installation. The repository's package-private registration callback
  gives the controller ownership of the new subscription before synchronous
  publication. A display callback that closes or null-resets the page can then
  dispose it immediately. Only a still-active online consumer may enqueue or
  dispatch after publication; this also applies when the request slot is idle.
  Preserve the existing public subscribe API.
  Null/reset and close immediately invalidate pending work and dispose obsolete
  consumers. Keep this orchestration in an Android-free production seam with
  an Android lifecycle/LiveData shell; preserve weak ownership at the app-wide
  repository boundary.
- Render bodies without waiting for location. Show `发帖：123   IP 属地：广东`
  when known, or post count alone otherwise. Remove level/reputation from floor
  detail only; preserve underlying profile/statistics and other author actions.
- Asynchronous location changes use `AuthorMetadataPayload` and bind only
  `tv_detail`. Validate data generation, author UID, and bound row identity.
  Never reload the body WebView or run a full-list bind for these callbacks.
  Preserve the valid snapshot when assigning a fresh nonnull page. Read the
  next value through its epoch/TTL guard and compare the final detail string
  (post count plus location) with the holder's actually displayed text before
  assigning it. Do not infer that no clear is needed by comparing two snapshot
  lookups after invalidation: both can be null while old text remains visible.
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
- GET `/nuke.php?func=ucp&uid=<author>` with the same Web profile URL as Referer.
  Supply the captured `ngaPassportUid`/`ngaPassportCid` Cookie, browser
  `User-Agent`, and `X-User-Agent: Nga_Official` explicitly. Guest scope is
  account UID `0` with an empty Cookie. Validate UID/CID/header bounds before
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
- Prevent the OkHttp `503 + Retry-After: 0` follow-up independently of
  `retryOnConnectionFailure(false)`. The operation's network interceptor removes
  only the 503 retry hint before its follow-up layer, retaining status/body and
  all 429 retry metadata. The current app dependency is OkHttp 4.12. An actual
  loopback-server test must pin physical request count, not merely the number
  of calls to the transport mock.
- `ProfileLocationParser.parse` accepts Web profile HTML. Extract the object
  assigned to `__UCPUSER` in an inline script as data, without evaluating
  JavaScript or loading a WebView. Handle whitespace, nested containers,
  quoted braces, and escaped quotes/backslashes; limit object/array nesting to
  48 levels including the user object, and do not accept assignment-shaped
  HTML attributes, comments, quoted script text, or regex literals.
  Read `ipLoc` from the extracted object's top level, not from `data.0`.
- A missing assignment or unrecognized profile stops the captured session as
  a conservative local policy; it does not prove that the server issued a
  challenge. A located but malformed object is an ordinary parse failure.
  Never fall back to the old JSON route or to browser execution after failure.
- If `uid` is present it must match the requested author. Without it, require
  nonblank `username` and an established profile discriminator (`posts`,
  `group`, or `regdate`). A missing/blank `ipLoc` in a valid profile is a valid
  empty observation. Keep only the latest observation; do not adopt the
  userscript's location-history storage.
- The manual `JsonProfileLoadTask` still uses `ProfileEnvelopeParser` for its
  established JSON wrapper/comment and numeric-token repairs. Its request,
  profile building, and absence of raw parse-failure logging remain unchanged.
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
| Authentication / challenge / site rejection / HTTP 503 | Stop the captured immutable session in process memory, including its credentials and UA |
| Concurrency | One physical supplementary request in flight across all page consumers |
| Dispatch | Reuse queued/in-flight keys; the first idle request may start immediately, then wait at least 500 ms after each call's terminal callback before starting another |
| Expiry / unpause | Reconsider on later work events; expiry alone does not start a request |
| Cancellation | Retain the physical slot until the canceled call's terminal callback |
| Orphaned queued work | Remove an unsent author only when no valid online consumer needs it |

Request pacing is shared by the app-scoped repository across pages, prefetch,
refreshes, account changes, and origin changes. Use monotonic elapsed time for
this deadline; wall time still owns cache expiry and server pauses. A slow,
failed, or canceled call also leaves a full 500 ms gap after completion,
so connection setup and cancellation cannot cause back-to-back requests.

Use one cancellable owner-thread wakeup only while eligible online work awaits
the pacing deadline. Recheck session, cache, server stops, and consumers when it
runs. Cancel it when its queue becomes empty or its session is invalidated,
without resetting the app-wide deadline. Ignore obsolete wakeups. A delayed
wakeup never catches up with a burst, and pacing never schedules TTL refreshes
or automatic recovery from a server pause.

HTTP 503 always stops supplemental reads for the captured session before body
decoding, including an empty, missing, unreadable, oversized, malformed, or
profile-shaped body. The existing session-stop result is a local dispatch
decision; it does not assert that 503 means authentication rejection or rate
limiting. No later author, page refresh, or same-session consumer recreation
may restart the queue. Manual profile loading remains a separate user action.

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
| HTML with a valid matching `__UCPUSER` profile and location | Publish metadata and cache for 24 hours |
| Valid profile with absent / blank location | Cache valid empty; show post count only |
| Invalid author / session / configured origin | No supplementary request |
| Located but malformed profile object, invalid location, I/O failure, oversized / undecodable body without an HTTP 503 status | Per-author 10-minute failure cooldown |
| Redirect, HTTP 401/403, site error, mismatched UID, or text/HTML without a recognized profile assignment | Stop captured session; no redirect, identity rotation, fallback endpoint, or next-author loop |
| HTTP 429, including a queued completion after view/account invalidation | Persist account pause; preserve longer server delay |
| HTTP 503 with any body, including absent/unreadable body | Stop captured session before body decoding; never continue with the next author |
| Other HTTP 5xx containing a valid profile-shaped body | Ordinary failure, never success |
| Bounded HTTP 5xx challenge body | Session rejection, never empty success |
| Fresh persisted cache, including valid empty | No profile request after process recreation |
| Missing/corrupt/expired cache | Lookup only when an online delivery requires it |
| Offline page or retained view rebound | Cache-only display, including misses |
| Repeated nonnull response instance delivered to the same Page | Re-emit the current snapshot while preserving the existing consumer and online/cache-only intent; no new queries |
| Fresh response contains an author with a valid current-session cached location | Preserve the displayed location through deferred consumer handoff; no artificial empty before the cache hit |
| Account invalidates while replacement is pending | The active consumer immediately clears displayed metadata; stale pending work cannot restore it |
| Initial synchronous metadata display closes or null-resets the page | Dispose the registered consumer before request dispatch, including with an idle repository |
| Equivalent detail text arrives, including a same-session refresh | Keep the existing text; no redundant identical `setText` |
| Cache-only subscription after a 429 pause expires | Do not resume another page's queue; later real online work may resume it |
| Fast, slow, failed, or canceled request completes | Next supplemental call starts at least 500 ms after its terminal callback; one physical call remains the concurrency limit |
| Page/account changes while waiting | Cancel obsolete work; new eligible work still honors the shared pacing deadline |
| Wall-clock change or delayed wakeup | No shortened interval or catch-up burst |
| Data/author/view generation changed | Ignore obsolete UI payload; never reload a body WebView |

## 5. Good / Base / Bad Cases

- **Good**: one delivered page needs authors A/B; an independently prefetched
  page needs B/C. B shares queued/in-flight work, and C starts after the
  physical slot is free and the shared 500 ms pacing deadline passes.
- **Base**: an author has a fresh empty observation. The floor shows its post
  count and subsequent deliveries do not repeat that lookup during the TTL.
- **Bad**: query once per holder, start a separate three-page batch, block a
  thread to pace requests, refresh locations on every resume, or discard a
  received server stop because the original view disappeared.

## 6. Tests Required

- `ProfileLocationParserTest` and `ProfileSessionTest`: synthetic Web profiles,
  nested objects/arrays, quoted braces and escaped strings, assignment-shaped
  comments/attributes/quoted text/regex literals, the 48-level nesting bound,
  missing or malformed assignments, valid
  empty, UID binding, location bounds, exact origins, Cookie-safe input, and
  credential snapshot equality. Keep manual JSON envelope-repair coverage.
- `AuthorLocationRepositoryTest`: distinct delivered authors, duplicate sharing,
  immediate first dispatch, exact 499/500 ms pacing boundaries, slow/canceled
  calls, wall-clock changes, delayed/obsolete wakeups, incremental prefetch,
  fake-clock TTL/failure/429 boundaries,
  persistent late pauses, same-UID credential isolation, obsolete callbacks,
  shared-consumer disposal, and cache-only readers that cannot resume paused
  online work when its pause expires.
- `AuthorLocationStoreTest`: atomic read-back, corruption/version/bounds, TTL
  validation, latest observations, and retention of server-pause guards.
- `ProfileLocationTransportTest`: exact Web URL/Referer and immutable wire
  identity, successful GBK HTML extraction, strict response bounds,
  body-independent HTTP 503 stop classification, Retry-After, and one physical
  request with the actual configured client against loopback HTTP 503/429 fixtures.
- `AuthorLocationRepositoryTest` must feed an actual empty-503 classification
  into the queue and verify that pending and newly delivered authors stay
  stopped, including after same-session consumer invalidation.
- `ArticleAuthorLocationContractTest`: common delivery integration, complete
  author collection, view/account lifetime boundaries, and metadata-only binding.
  Trace presenter READY replay through the Fragment into Page delivery and
  pin its identity guard before generation/subscription replacement and any
  repository call. Cover re-emitting the current snapshot, null clearing,
  recreated-view cache-only replay, fresh responses, and retention after view
  destruction; keep the executable cache-only/expired-pause regressions.
  Keep existing page-state, prefetch, refresh, and page-cache regressions green.
- `AuthorLocationPageTest`: execute the production page-delivery controller on the host JVM with queued
  settlement, fake clocks/signals, and the real repository's fake transport.
  Capture all emitted values to catch an empty interposed before a cache hit;
  cover replacement/account invalidation, expiry, fresh/removed/anonymous
  authors, READY/cache-only intent, null/close, superseded pending work and
  synchronous subscriber reentrancy with both occupied and idle physical
  request slots. A test-local subscription wrapper does
  not cover the production Page's orchestration.
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

Wrong after switching to the Web route:

```java
if (html.trim().startsWith("<")) return ProfileLocationResult.rejected();
```

Correct: pass the HTML through the Web profile parser, which requires a real
`__UCPUSER` assignment and validates its profile identity before accepting
`ipLoc`:

```java
ProfileLocationResult result = ProfileLocationParser.parse(html, requestedUid);
```
