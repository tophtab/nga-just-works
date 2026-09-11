# Public IP Location: Source and Request Behavior

Research date: 2026-09-11. Local baseline: `fix/thread-menu-cache`,
commit `5bb92cf033aa32d749d10e1a497bc05173cd2955`.
This is source inspection, not live NGA verification.

## External evidence

- [NGA UserInfo Enhance](https://greasyfork.org/zh-CN/scripts/416741-nga-userinfo-enhance), version 2.0.10, MIT.
- [Inspected script source](https://update.greasyfork.org/scripts/416741/NGA%20UserInfo%20Enhance.user.js).
  SHA-256: `77b654c7cce6b498679a107a1909a805345942bd592fda7271b82188b77ecdca`.
- The script pins [NGA Library 1.1.3, revision 1414880](https://update.greasyfork.org/scripts/486070/1414880/NGA%20Library.js).
  SHA-256: `0dd63c80b3193ee3e90ec70ef34c2ddcac6f84a7de9595dce04c2574dd82d42f`.
- Source line numbers below refer to those exact downloads. Temporary copies
  were inspected locally; no NGA user profile, account, or raw IP was queried.

## Actual lookup

Script lines 1435-1506 create the location label. When the user enables this
field, line 1480 calls `api.getIpLocations(uid)` and renders returned locations.
Province-name normalization and timestamp tooltips are presentation only.

Library lines 1771-1806 implement `getUserInfo(uid)`:

1. Read `USER_INFO_CACHE` by UID.
2. On a miss, GET `nuke.php?func=ucp&uid=<uid>`.
3. Extract the JSON assigned to `__UCPUSER =` from the profile HTML.
4. Cache a successfully parsed object and return it.

Library lines 1812-1857 implement `getIpLocations(uid)`. It reads that
profile object's `ipLoc`, deduplicates a history list by location text, moves
the latest observed location to the front, and records the observation time.
It does not determine an address from a raw IP, and does not obtain the
location at which each individual reply was posted.

## Frequency, caching, and missing protections

| Mechanism | Source behavior |
| --- | --- |
| Profile cache | `USER_INFO_CACHE`, UID key, one-hour freshness; module settings at lines 594-601 |
| Location cache | `USER_IPLOC_CACHE`, UID key, one-hour freshness; settings at lines 602-609 |
| Expiration | Compare record timestamp plus one hour with current time when queried; no periodic hourly request |
| Persistence | IndexedDB; location history is retained past freshness expiry because `persistent: true`; profile records are also stored on disk but expired records are deleted |
| Fresh location hit | Return cached locations; no profile request |
| Missing location in a valid profile object | The successfully parsed profile object can still suppress a new profile request for its one-hour freshness period |
| Failed fetch / invalid profile parse | No dedicated negative-cache/backoff policy; another invocation may try again |
| Simultaneous lookups | No in-flight promise map for a UID; cold concurrent calls can duplicate requests before cache writes finish |
| Global request rate | No per-second/per-minute limit in this call chain |
| Cache sharing | UID-based within the browser's IndexedDB origin; different NGA origins or browser stores do not share this cache |
| Queue | A `Queue` exists for other operations; `getUserInfo` and `getIpLocations` do not enqueue their requests |
| Rate limit / challenge | No location-path 429/`Retry-After`/challenge pause policy |
| Request headers | `API.request` at lines 1664-1677 uses `fetch` with configurable `X-User-Agent`, default `Nga_Official`; this is not a rate limit or a guarantee against access controls |

Cache behavior is implemented at library lines 1454-1526. The distinction
between freshness and persistence matters: an expired location history can be
returned as old data if no new location is obtained.

For a cold page containing N distinct eligible authors, approximately N profile
requests may start close together. Repeated authors can add races. Revisiting
fresh, successfully cached authors usually adds zero requests. Therefore
"one-hour cache" must not be described as a strict one-request-per-user-per-hour
guarantee. No safe NGA threshold or immunity to risk controls can be inferred.

## Current Android seams

- `ArticleListAdapter.java:460` renders level, reputation, and post count.
- `ThreadRowInfo.java:15` and `ArticleConvertFactory.java:262-332` already
  provide author identity, anonymous status, and metadata. There is no
  established thread-page IP-location field in this baseline; do not invent one.
- `JsonProfileLoadTask.java:47-51` already uses operation `USER.PROFILE`:
  `nuke.php?__lib=ucp&__act=get&lite=js&noprefix&uid=<uid>`, with a
  same-origin profile Referer.
- `JsonProfileLoadTask.java:88-111` normalizes the response wrapper and reads
  `data.0`; line 169 reads `ipLoc`.
- `ProfileData.java:41` carries that location;
  `ProfileActivity.java:168-170` displays it.
- Calling `JsonProfileLoadTask.execute` directly for floor decoration is
  inappropriate: its callbacks dismiss a global activity dialog and its error
  parser logs the full response. Extract/reuse only the bounded, side-effect-free
  wire/parser behavior needed by this read.
- `ArticlePagerAdapter.java:32` uses
  `BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT`. A resumed-child gate would incorrectly
  suppress the user's requested location enrichment for prefetched pages.
- `ArticleListFragment.java:237-258,295-317` owns the RecyclerView and the common
  successful page-delivery seam. Extract all delivered authors there, including
  offscreen/below-viewport authors; do not start lookups from row binding/scroll.
- `ArticleListAdapter.java:471-500` reloads a LocalWebView on full bind.
  Location delivery must update only metadata, not issue full row/list rebinding.
- `ArticleListParam.loadCache` distinguishes offline saved-page readers.
  Location cache must remain separate from raw thread-page cache files.

## Current adaptation after user clarification

Use the established Android `USER.PROFILE` endpoint, not the script's HTML
scraper. Keep only the latest public location rather than historical tracking.

Author enrichment follows every successfully delivered online page from the
existing normal/prefetch mechanism. It is not visible-floor-only or a separate
fixed-three-page job. Reuse cached/queued/in-flight keys across all deliveries,
and do not add a fixed inter-request interval or time-based pacing mechanism.

The remaining proposed cache/concurrency/failure choices live in `../design.md`.
`prefetch-request-volume.md` explains why today's prefetch can expose about sixty
authors initially without making sixty or three pages an implementation rule.
The parent's `research/author-location-prefetch-integration.md` identifies the
common delivery seam and lifecycle/account guard requirements.

## Relevant contracts

- `.trellis/spec/backend/nga-platform-operation-registry.md`: `USER.PROFILE`.
- `.trellis/spec/backend/nga-platform-access-rules.md`: host, account, error,
  size, logging, and offline validation boundaries.
- `.trellis/spec/backend/network-foundation-contract.md`: existing identity
  headers and profile/session compatibility.
- `.trellis/spec/backend/thread-page-prefetch-contract.md`.
- `.trellis/spec/backend/thread-page-cache-contract.md`.
- `.trellis/spec/frontend/component-guidelines.md`: LocalWebView rendering.
- `.trellis/spec/backend/android-quality-guidelines.md`.
