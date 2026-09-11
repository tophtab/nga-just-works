# Implementation Integration Review

Date: 2026-09-12. Worktree:
`/home/toph/nga-just-works-thread-ip-location-loading-tips`.

This source review is complete. The authoritative final combined review is
`combined-check-report.md`, and the complete validation record is
`final-validation.md` in this directory.

## Source checks performed

- `ArticleListFragment.setData` remains the common successful normal/prefetch
  delivery seam. It passes online eligibility from `!loadCache` and does not
  inspect the selected page, viewport, or a location-specific prefetch window.
- `ArticleAuthorIds.fromPage` copies the complete delivered author set and
  excludes anonymous/nonpositive identities. It does not read bound holders.
- Adapter location updates use author/data-generation payloads and call only
  `onBindAuthorDetail`; the body WebView path stays in the full bind.
- Fragment view destruction closes location delivery, detaches its RecyclerView
  adapter, and unbinds view fields. Retained data is rendered cache-only into a
  replacement view; location UI receives lifecycle-bound data snapshots.
- Account capture uses the settled user list/index, covering the previously
  documented `removeUser` stale-active-user hazard without editing UserManager.
- Shared loader reachability is confined to the existing article/search/topic
  XML includes and `RecentNotificationFragment`. The separate simple-topic and
  Compose message loaders do not use those includes.

## Findings resolved before acceptance

The IP child's `research/implementation-review.md` preserved three reproduced
initial defects: non-profile rejection bodies, stops queued across an epoch
invalidation, and OkHttp's HTTP 503 follow-up. All three were fixed and verified
independently and in permanent regressions. The combined checker also fixed
cache-only subscriptions resuming another page's queue after pause expiry.

## Completed integration checks

- Completed IP handoff before the narrow shared-fragment tip binding.
- Verified foreground tip selection against successful offscreen prefetch and
  the existing 300 ms foreground success transition.
- Verified actual bundled AI entry/destination eligibility in both states.
- Passed app debug assemble/unit/lint and all-module lint with XML
  severity inspection, and ran the repository debug-unit diagnostic command.
- Recorded the two observed, known JVM fixture failures separately from the
  passing 223 app tests;
  no live NGA, ADB/device, or release check is part of this task.
