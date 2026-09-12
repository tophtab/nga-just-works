# Restore stable article refresh and author metadata

## User request

Continue the page-flicker fix. Correct manual-refresh flicker, including IP
locations disappearing and reappearing. Compare the reader with release 5.6.1,
whose smoothness the user wants to recover. The user previously delegated
Trellis task choice and requested commit, finish-work, and push for this bug-fix
work; that authorization persists.

## Evidence

- Compatibility adaptation `7acc4e23` destroys every retained body WebView
  whenever `ArticleListAdapter.setData()` receives a different response object.
- `LocalWebView` has skipped equal HTML since upstream `bd9fd9f9` (2020).
  Destroying it loses this cache. Calling its load method during a row bind does
  not itself prove an actual Chromium reload; correct the earlier explanation.
- The adapter empties its author-location snapshot on each data delivery.
  `AuthorLocationService.Page.deliver()` also emits an empty snapshot before
  deferred resubscription restores cached metadata.
- Keep `16cc185b`, which avoids redundant unchanged READY list binding.

## Change boundary

The behavior lives in article view retention and metadata delivery, not page
transport. Expected product changes are the adapter and the author-location
delivery seam, with narrow helpers only where the production behavior needs
them. The main session owns specs and task artifacts; agents own their assigned
product/test files. Preserve unrelated dirty AI-profile work.

Do not redesign the reader, remove compatibility/IP features, change refresh
gestures, suppress real updates, extend network work, or change rate limits,
TTL, or session-stop behavior. Preserve reader/source/account invalidation and
view-destruction cleanup.

## Acceptance criteria

- [x] Record the relevant 5.6.1/current body-retention and metadata differences.
- [x] A same-page refresh preserves applicable WebViews, including when equal
      HTML arrives inside a new response object.
- [x] Changed HTML, added/removed/reordered rows, pages above 20 rows, and genuine
      reader/source/account changes display correct content.
- [x] Removed/inapplicable views and destroyed Fragment views release owned
      resources without use-after-destroy or cross-row state reuse.
- [x] Fresh known IP locations survive same-session refresh without an
      artificial empty delivery before a cache hit.
- [x] Account/domain/credential invalidation, expiry, anonymous/unknown authors,
      and missing values still clear inappropriate metadata.
- [x] Metadata-only changes bind author details without rebinding bodies;
      unchanged displayed details avoid redundant visible updates.
- [x] Behavioral regression tests exercise the actual production seam and fail
      on the former broken behavior, rather than testing a copied simulation.
- [x] Debug build, JVM tests, and all-module lint pass. Offline tests are not
      presented as device-visible smoothness measurements.

See [validation.md](./validation.md) for the focused and full repository gates,
independent review, and the limits of host-only verification.
