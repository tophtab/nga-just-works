# Design: Thread Author IP Location

Status: approved for implementation on 2026-09-12 (user: “确认吧。开干吧。”).
Parent: `../09-11-thread-ip-location-loading-tips/`.

## Boundary and display

The label describes an author's latest publicly observed NGA profile location,
not where a historical floor was posted. Replace level/reputation with
`IP 属地：广东   发帖：123`; if location is unavailable, show post count alone.
Preserve other author interactions and skip anonymous/nonpositive/invalid IDs.

Use the existing `USER.PROFILE` route as the sole network source. Do not add
undocumented thread fields, raw-IP geolocation, or location-history tracking.

## Page-driven enrichment

The existing page loader owns which pages are fetched, prefetched, reused, or
cancelled. Author location is enrichment of each successfully delivered page:

1. At the common page-delivery seam, `ArticleListFragment.setData(ThreadData)`,
   capture an immutable set of eligible author UIDs and the page/data generation.
   Both foreground and `PrefetchCallback` success already reach this seam.
2. Hand this set to an application-scoped location repository. Cache hits are
   immediately reusable; missing keys join the shared queued/in-flight map.
   Initiate work for each delivered page without waiting for any other page.
3. Continue the existing body render and loading transition independently.
   Location completion updates only matching author metadata through a dedicated
   adapter payload/bind; never issue a full list/body rebind for location updates.
4. Bind view consumers to their page/data generation. Remove obsolete consumers
   on replacement/destruction; account changes invalidate the old scope. Already
   dispatched work may finish into a still-valid cache scope, without stale UI
   delivery. Remove unsent work only when no valid consumer still needs it.

Do not add a separate current-plus-two location planner, require RESUMED for an
intentionally prefetched page, or wait until its floors enter the viewport.
View retention or adapter rebinding alone does not create a new request source.
A page request that finishes under the existing prefetch lifecycle can enrich
its delivered data; do not redefine that lifecycle through a location window.

`loadCache` remains cache-only for location data. Other online page deliveries
reuse the same repository without adding new thread reads. Metadata does not
change existing final-page freshness or prefetch promotion/reuse.

## Request volume

Count distinct eligible UIDs in the delivered pages, excluding fresh-cache,
queued/in-flight, and failure-blocked keys in the same account/origin scope.
Do not equate floors with requests or per-page body reads with profile reads.

With today's current-plus-two prefetch policy and roughly twenty rows per page,
an initial cold load can cover about sixty authors. This is an example, not an
implementation batch size, session cap, or instruction to fetch three pages.
Later prefetch adds only new uncached authors. See
`research/prefetch-request-volume.md` for the full count model.

## Cache and dispatch policy

| Item | Approved contract |
| --- | --- |
| Trigger | Every normal/prefetched online page delivery from the existing mechanism |
| Fixed request interval | None; no sleep, minimum-start-spacing timer, or token bucket |
| Duplicate reuse | One queued/in-flight lookup per origin/account/author key |
| Concurrency | One supplementary call in flight; immediately dispatch the next eligible key on completion |
| Freshness | 24 hours for retained successful/valid-empty observations |
| Scope | Normalized first-party origin + viewing-account UID (guest sentinel) + author UID |
| Persistent payload | Latest bounded location, observation/expiry/cooldown metadata; no Cookie or full profile |
| Store | Versioned app-private cache, serialized I/O, 1,000-entry bound; evict oldest observations while preserving server-pause guards; corruption becomes a miss |
| Cache eviction/loss | May require a new read before 24 hours; does not create periodic refresh |
| Ordinary failure | Suppress the affected key for ten minutes; no immediate retry loop |
| HTTP 429 | Stop queued work for at least thirty minutes or the longer valid Retry-After |
| Authentication/challenge/site rejection | Pause supplemental work for the affected account/session; no identity rotation |
| Explicit thread refresh | Same cache/reuse rules; no forced location invalidation |

Concurrency, cache freshness, and failure-specific cooldown are distinct from
normal-request spacing. The user's no-interval instruction must not be replaced
with another time-based pacing mechanism. Wall-clock timestamps remain only for
cache expiry and failure policy; no monotonic dispatch-delay scheduler is needed.
These policies are not evidence of NGA's risk-control thresholds.

Server stop ownership is separate from page/UI generation. A queued 429 or
session-rejection completion still updates the captured scope/session after
consumer invalidation, without updating obsolete views or stopping another
account. A canceled call retains the physical slot until its terminal callback.

## Transport and parsing

Reuse the established route, exact first-party host selection, GBK decoding,
known wrapper normalization, and identity headers. Freeze the viewing-account
session before sending and verify scope/generation before delivering results.

Do not invoke UI-oriented `JsonProfileLoadTask` wholesale: its callbacks dismiss
a global dialog and its failure path logs raw responses. Extract/share only a
small side-effect-free profile envelope parser so wrapper repairs do not diverge.
Keep the profile screen's behavior intact.

Use existing OkHttp/Retrofit dependencies with an operation-scoped configuration:
explicit Cookie/UA, HTTPS allowlisted origin, no automatic redirects or connection
retries, bounded response bytes, and no raw-body logging. Require a valid profile
envelope and matching returned UID when present; accept only bounded plain-text
location, not HTML, placeholders, malformed values, or raw-IP-looking values.

Implementation uses a 256 KiB decompressed response bound and a 20-second call
timeout. OkHttp 3.12's separate 503 follow-up ignores its connection-retry flag:
the operation's network interceptor removes only the 503 `Retry-After` hint
before that follow-up layer, preserving status/body and the original 429 retry
metadata. A loopback-server regression pins one physical request.

Unknown site-error/non-profile responses pause supplementary work conservatively;
do not fabricate a precise error code or continue blindly through other authors.
No broader transport or account-manager refactor belongs in this feature.

## Compatibility and rollback

Keep thread wire/parsing, page-prefetch planning/state, raw cache-file format,
profile layout, statistics fields, and moderation behavior compatible. The
location store is disposable and independently versioned. Removing the lookup
integration and detail presentation rolls back without migrating thread caches.

## Verification focus

Controlled page deliveries must prove normal and hidden-prefetch enrichment,
no fixed batch/page count, cross-page duplicate reuse, cache reload/expiry,
immediate dispatch with a free concurrency slot, incremental prefetch, preserved
final-page exclusion, late-result isolation, failure stops, offline reads, and
metadata-only rendering. Use fixtures/fakes; no live NGA call is required.
