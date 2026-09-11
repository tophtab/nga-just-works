# IP Location Implementation Handoff

Date: 2026-09-12. Worktree: `/home/toph/nga-just-works-thread-ip-location-loading-tips`.
Branch: `feature/thread-ip-location-loading-tips`.

## Implemented boundary

The behavior gap lives at successful page delivery and author metadata binding.
`ArticleListFragment.setData` renders immediately and submits a copied eligible
author set from every online delivery, including hidden prefetch. There is no
new page planner, visible-floor gate, fixed page count, request timer, or body
fetch. Saved pages and recreated retained views use cache only. The existing
presenter/prefetch/cache wire and final-page behavior were left intact.

`ArticleListAdapter` replaces level/reputation copy with location and post count,
or post count alone. Location callbacks use generation/author/holder-identity
checked payloads that only bind `tv_detail`, never a body WebView. View teardown
closes subscriptions, detaches the old adapter, and unbinds view fields; guards
prevent the Fragment-lived presenter from updating a destroyed view.

`sp.phone.profile` now owns:

- Shared USER.PROFILE wrapper normalization and validated location projection.
- Immutable origin/account/Cookie/UA snapshots from the settled selected user
  list/index; both account observables invalidate before deferred capture.
- An application cache/repository with one physical call in flight, shared
  queued/in-flight UIDs, 24-hour success/valid-empty observations, 10-minute
  ordinary failure cooldown, and captured-session rejection stops.
- Serialized, bounded, atomic app-private persistence in
  `cacheDir/author-locations-v1.json`. The 1,000-record bound evicts oldest
  observations while retaining server pause guards during observation churn.
  No Cookie, UA, full profile, raw IP, or location history is persisted.
- Dedicated transport with explicit identity headers, exact HTTPS origins,
  a 256 KiB decompressed bound, 10-second connect/read and 20-second call limits,
  no redirect/connection retry, and no raw logging or activity effects.
- Lifecycle-owned LiveData subscriptions with weak repository sinks, data
  generations, disposal, and session-epoch checks.

The legacy profile reader now shares the wrapper parser. Its existing profile
building and UI behavior remain; its raw parse-failure logger was removed.

## Audit resolutions

The independent observations remain in `implementation-review.md` with their
original source hashes. Owner resolutions are covered by permanent tests:

| Finding | Resolution and regression |
| --- | --- |
| F1: Unknown/wrapped site rejection continues queue | Known wrapper normalization distinguishes non-profile responses from malformed profile JSON. Plain text, wrapper-prefixed HTML, and bounded 503 HTML challenge stop. Parser fixtures plus `unknownSiteAndWrapperPrefixedChallengeFixturesStopBeforeAnotherAuthorStarts` exercise the entire queue effect. |
| F2: Queued stop lost during UI/account invalidation | Apply 429/rejection to the request's captured scope/session before rejecting an obsolete UI epoch. `queuedStopResponseSurvivesSameSessionConsumerInvalidation` and `queuedStopIsOwnedByCapturedAccountAndStillAppliesWhenReturningToIt` cover both response kinds without touching another account's queue/UI. |
| F3: OkHttp 503 follow-up | A narrow network interceptor removes the 503 retry hint before OkHttp 3.12's follow-up stage, preserving body/status and all 429 retry metadata. `actualClientDoesNotRepeat503WithZeroRetryAfterAndPreserves429Metadata` uses a real loopback socket and asserts one physical request and original status. |

Stops and callbacks are deliberately separate: obsolete success results cannot
populate another session's cache or UI, while a received server stop cannot be
discarded merely because its UI consumer changed.

## Verification

Final local module command:

```bash
./gradlew :nga_phone_base_3.0:assembleDebug \
  :nga_phone_base_3.0:testDebugUnitTest \
  :nga_phone_base_3.0:lintDebug --console=plain
git diff --check
```

Result: passed. The application XML reports 219 tests, 0 failures, 0 errors,
including 46 location-specific tests across parser, session, persistence,
transport, repository/page delivery, and Android source contracts. Existing page
prefetch, request-state, refresh, and saved-page tests also passed. This snapshot
includes the parallel loading-tip implementation/tests; it is not the parent's
final combined check. The app lint XML reports 0 Error / 0 Fatal.

The first test compilation found that `Files.readString/writeString` are absent
from this Android compilation API; tests now use `readAllBytes/write`. No product
dependency or unrelated test configuration was changed. Logs are in
`/tmp/nga-ip-location-review-fixes.log`; actual XML reports are under the app's
`build/` directory.

Tests use synthetic fixtures, fake transport/clock, temporary cache files, and a
loopback-only HTTP server. No live NGA profile, device, installation, Preview,
Release package, push, or commit was performed. UI lifecycle/payload wiring is
source-checked because the app's JVM suite has no Android UI runtime.

## Parent integration follow-up

- Complete combined Trellis review and all-module lint/debug test diagnostics,
  then capture verified contracts in shared specs and finish the task workflow.
- The loading-tip implementer can add to `onViewCreated`, `onDestroyView`, and
  `hideLoadingView` on top of the new view-lifetime guards; no IP code needs a
  loading-tip visibility or RESUMED gate.
- HTTPS allowlist currently contains `bbs.nga.cn`, `bbs.ngacn.cc`, `nga.178.com`,
  and `ngabbs.com`. Legacy unsupported/invalid configured origins (including
  `nga.donews.com`) skip this supplemental read without changing thread loading.
  No live availability claim is made for any listed host.
