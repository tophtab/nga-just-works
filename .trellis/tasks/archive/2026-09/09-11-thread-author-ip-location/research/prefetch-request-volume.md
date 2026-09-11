# Request Volume When Location Follows Page Prefetch

Research date: 2026-09-11. Source inspection and synthetic count reasoning only;
no live NGA traffic or server threshold was measured.

## User-owned integration rule

Whenever the existing mechanism reads or prefetches a thread page, its author
locations are retrieved/cached after that page's author list is available.
Pages proceed independently. There is no location-specific page count, window,
three-page barrier, or visibility gate. The user also requested no fixed interval
between ordinary requests. The current-plus-two example estimates possible
volume; it must not become a second scheduling mechanism.

## Existing flow evidence

- `ArticlePagePrefetchPlanner.java:17-28` in `sp/phone/mvp/viewmodel` plans the
  next two pages and excludes the known final page. Location enrichment consumes
  actual deliveries; it does not copy or override this planner.
- `ArticleTabFragment.java:98-107,127-129,146-150` replans after reply-count and
  selected-page changes. `:123` also retains two offscreen views in each direction;
  retention itself does not establish loaded data or a location request trigger.
- `ArticleListFragment.java:209-222` subscribes eligible online pager children.
- `ArticleListPresenter.java:152-166` uses the existing page model and skips
  already-loaded/in-flight work. `:112-122` stores successful background prefetch
  and calls `setData(data)` whether or not that page was promoted.
- `ArticleListFragment.java:295-317` is the normal/prefetched page-delivery seam.
  This is where a complete immutable author-ID set can be handed to enrichment.
- `ArticleListFragment.java:252` caches twenty RecyclerView items. Row binding is
  neither proof of visibility nor a reliable list of all prefetched authors.

## Count model

For any observed group of page deliveries, let A be their distinct valid,
nonanonymous author keys. Let C be fresh cached keys, I queued/in-flight keys,
and B failure-blocked keys, all in the same origin/account scope.

`new profile requests = size(A minus (C union I union B))`

One thread page is one `THREAD.PAGE` read, even if it contains twenty authors.
Those authors can require twenty additional `USER.PROFILE` reads. The current
thread response has no established per-floor IP-location field to replace those
queries. The shared cache/in-flight map must span independent page deliveries.

| Illustration, estimating twenty rows per full page | Additional profile reads |
| --- | --- |
| Current and two prefetched pages happen to deliver sixty distinct eligible cold authors | Up to about sixty |
| Those page deliveries contain forty-five unique authors, fifteen already fresh in cache | Thirty |
| A UID occurs on several concurrently completing pages | One lookup, shared across consumers |
| All delivered authors already have fresh retained cache entries | Zero |
| Moving ahead causes one new page to be prefetched while earlier pages are ready | Only its newly encountered uncached authors add reads, about twenty at most for a full page |
| Existing planner initially reads only current page and one future page | Demand comes only from those actual deliveries, about forty rows |
| A known final page is not prefetched | No location work for it until the existing normal loader delivers it |

Actual row counts come from returned lists. Sixty is not a batch size, session
cap, or hardcoded author limit. Repeated UIDs, anonymous/deleted rows, partial
pages, existing cache, and the underlying prefetch decision change the count.

## Completion, reuse, and ownership

Each page hands off its eligible authors as soon as its own existing load succeeds;
there is no wait for sibling pages and thread rendering does not wait for profile
responses. Actual refreshed data can submit a new author set; fresh/in-flight
keys are reused. Retaining/rebinding/revisiting already-ready data does not by
itself start another query pass.

If an existing page request is permitted to finish after navigation and delivers
data to a valid owner, its locations follow that completion too. Do not reject it
using a new current-plus-two window. On owner/data/session destruction, release
obsolete consumers and suppress stale UI updates; shared work can still serve
another valid consumer. No extra thread read is issued just to collect UIDs.

## No fixed ordinary-request interval

Do not add a minimum-start-spacing timer, sleep, per-second quota, or token bucket.
An available concurrency slot dispatches eligible queued work immediately.
The proposed single-in-flight concurrency bound, persistent cache freshness, and
failure-specific cooldown are separate policies still subject to final review.
None is evidence of a safe NGA risk-control threshold.

## Verification scenarios

Use synthetic independent page deliveries and controlled requests for arbitrary
page counts, below-viewport authors, overlapping UIDs, cache/in-flight reuse,
newly prefetched pages, valid late completion, no page-count barrier, immediate
dispatch without advancing time, and preserved original final-page behavior.
The integration seam and lifecycle hazards are documented in the parent task's
`research/author-location-prefetch-integration.md`.
