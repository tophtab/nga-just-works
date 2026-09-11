# Thread Author Public IP Location

## Goal

Show the author's latest publicly observed NGA profile location in thread floors,
and populate it as part of the existing page loading and prefetch flow.

## Background and Evidence

Parent: `../09-11-thread-ip-location-loading-tips/`.
`ArticleListAdapter.java:460` currently displays level, reputation, and post count.
`JsonProfileLoadTask.java:47-51,169` already requests `USER.PROFILE` and reads
`ipLoc`; `ProfileData.java:41` carries it and `ProfileActivity.java:168-170`
displays it. This is an author-profile value, not a historical per-reply location.

The reference script's exact version, dependency, hashes, endpoint, one-hour
caches, and missing concurrency/rate-limit protections are recorded in
`research/ip-location-source-and-cache.md`.

The user clarified the integration rule: whenever the existing mechanism
prefetches a thread page, its author locations are prefetched too. The page
count comes from that mechanism; this is not a fixed three-page batch. They
also explicitly requested no fixed interval between ordinary requests.

## Requirements

- R1: Remove level and reputation only from thread-floor detail text; preserve
  post count, author actions, moderation state, and underlying profile data.
- R2: Show a known location as `IP 属地：广东` next to post count. Missing,
  expired, failed, anonymous, or invalid-author cases show post count alone.
  Do not expose raw IPs or imply the profile value is a reply's posting location.
  Retain only the latest public observation.
- R3: Every online page delivered by normal loading or existing prefetch triggers
  location-cache lookup and missing-author queries for that page's eligible UIDs.
  Do not wait for other pages, impose a separate page-count/window rule, or gate
  prefetched authors on screen visibility. Share cached and in-flight work across
  all page deliveries. Keep existing page selection and final-page exclusion.
- R4: Do not add a fixed inter-request interval or a time-based dispatch throttle.
  The approved policy uses a 24-hour persistent cache, including valid
  empty results, and one supplementary request in flight with immediate dispatch
  when its slot is free. Cache loss/eviction can require a new lookup; no safe NGA
  rate is inferred. Concurrency and freshness are not fixed request spacing.
- R5: Ordinary failed keys cool down for ten minutes. Rate-limit responses stop
  queued work for at least thirty minutes or a longer server delay; authentication
  or challenge responses stop supplemental work for the affected session.
  Do not rotate identities or automatically loop retries.
- R6: Page content does not wait for location lookups. Saved-page disk-cache reads
  perform no location network requests. View/account/data-generation changes must
  not receive another scope's callbacks or reload a body WebView.

## Acceptance Criteria

- [x] AC1/R1-R2: Known location and post count render correctly, removed labels
  disappear, and missing/anonymous cases preserve readable content.
- [x] AC2/R3-R4: Normal and offscreen-prefetched page deliveries both enrich their
  authors. Repeated UIDs across concurrent deliveries share one lookup, and fresh
  retained cache entries survive process recreation without supplementary reads.
- [x] AC3/R3-R4: There is no fixed page count or all-pages barrier. Each delivered
  page starts its work independently; a free dispatch slot is used immediately,
  without advancing a clock. Existing prefetch/final-page behavior is preserved.
- [x] AC4/R5: Failure cooldown and rate-limit/auth/challenge stops are proven with
  controlled responses and a fake clock, including server delay handling.
- [x] AC5/R6: Recycling, refresh, navigation, destruction, and account changes
  cannot update the wrong author or reload body WebViews.
- [x] AC6/R3,R6: Cold multi-page, duplicate-author, incremental prefetch, late page
  completion, corrupt/expired cache, and offline cases behave correctly; existing
  refresh and page-cache regressions remain green.

Final combined verification (2026-09-12): app Debug build, 223 app JVM tests
(49 location/source-contract tests), and all 13 Android lint reports passed
with zero Error/Fatal. Cache-only reads also have a regression proving they
cannot resume another online page's queue after a 429 pause expires.
Android lifecycle/RecyclerView integration is pinned by source contracts alongside
executing page/repository tests. No live NGA or device run was performed. See the
parent's `research/combined-check-report.md` and `research/final-validation.md`
for review evidence and the documented repository-wide diagnostic failures.

## Out of Scope

A second page-prefetch planner, fixed three-page batching, visible-floor-only
fetching, normal-request pacing, raw-IP lookup services, location history, new
undocumented thread fields, profile UI redesign, and broad network migration.
