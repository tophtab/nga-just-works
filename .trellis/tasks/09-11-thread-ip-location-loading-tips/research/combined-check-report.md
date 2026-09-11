# Combined Trellis Check: Author Location and Loading Tips

Date: 2026-09-12. Reviewer: `check_ip_location_loading_tips`.
Worktree: `/home/toph/nga-just-works-thread-ip-location-loading-tips`.
Branch: `feature/thread-ip-location-loading-tips`, baseline `5bb92cf0`.

## Scope and context

This is the Phase 2.2 combined check for the parent and both children, not only
the active `09-11-loading-usage-tips` task. The reviewer read all three PRDs,
designs, implementation plans, and check manifests; the source/research entries
in those manifests; the IP implementation handoff and independent request-control
reports; and the parent integration review. Package discovery reported a single
repository with frontend/backend layers.

Applicable sources read directly included frontend component and shared thinking
guides, Android quality gate, NGA access/operation/network contracts, and the
thread-page prefetch/cache contracts. Relevant complete sections were read from
disk instead of relying on truncated component/quality context injection.

All tracked and untracked product/test changes were reviewed. The reviewer owned
only confirmed in-scope fixes, tests, and this report. The parent owns final spec
updates, task bookkeeping, repository-wide verification, commits, and archiving.
No other implementation/check agent was spawned.

## Findings (fixed)

### F4: Cache-only subscriptions could resume a paused online queue

- File: `nga_phone_base_3.0/src/main/java/sp/phone/profile/AuthorLocationRepository.java:153`.
- Issue: `subscribe(..., false, ...)` did not enqueue its own missing authors,
  but still invoked `dispatch()`. After an existing online consumer's 429 pause
  expired, merely opening a saved page or recreating a retained view could start
  another author request from that consumer's pending queue. This violated the
  approved cache-only/no-new-request-side-effects boundary.
- Reproduction: a disposable Java probe compiled the actual repository/cache/
  session/result sources, used a fake clock/transport, delivered online authors
  `[41, 42]`, completed author 41 with 429, advanced the clock to expiry, then
  subscribed cache-only author 99. The physical-request list changed from `[41]`
  to `[41, 42]` at that cache-only subscription. No network was used by the probe.
- Fix: line 158 now guards enqueue/dispatch with `loaded && online`.
  Cache-only subscriptions still publish fresh observations and receive valid
  existing work, but cannot restart a queue. Cache restore, real online page
  delivery, and existing request completion retain their normal dispatch roles.
- Regression: `AuthorLocationRepositoryTest.java:125`,
  `cacheOnlyDeliveryCannotResumeAnOnlineQueueAfterItsRateLimitExpires`, verifies
  cached display, cache-only missing-author exclusion, no resume from saved or
  retained reads, and immediate resume after a later actual online delivery.
  The clock does not advance between those cache-only/online calls.

### F5: Permanent request-control assertions needed the independent edge cases

- Files: `nga_phone_base_3.0/src/test/java/sp/phone/profile/AuthorLocationRepositoryTest.java`
  and `ProfileLocationTransportTest.java`.
- Issue: the production fixes for the independent report's F1-F3 were already
  present, but permanent tests did not yet cover persisted late 429 state,
  a queued rejection against changed credentials for the same UID, and challenge
  body/longer-429 metadata through the actual configured client's socket path.
- Fix: applied only `request-control-fixes.patch`'s test hunks after inspecting
  them, matching both base hashes, and running `git apply --check
  --whitespace=error-all`. No stale production prototype was applied.
- Added `queuedRateLimitPauseIsPersistedAfterConsumerInvalidation` and
  `queuedRejectionIsBoundToTheOriginalCredentialsForTheSameUid` to the existing
  repository harness. The real loopback 503/429 test now verifies one physical
  request, the original 503 challenge body reaching session-stop classification,
  and a retained `Retry-After: 3600` producing a one-hour pause.

The initial app check with those test hunks passed 222 tests. F4 was discovered
in the final side-effect review, fixed locally, and followed by a fresh full app
build/unit/lint check reporting 223 tests. The earlier result is not substituted
for validation of the final source.

## Findings (not fixed)

None in the reviewed product scope. No unresolved design/interface change or
local lint/compiler/test failure was identified. The repository-wide debug-unit
fixture baseline is a separate parent-owned diagnostic, not a newly accepted
feature defect. No unrelated dependencies, fixture tests, or build variants were
changed to hide that baseline.

## Verified behavior and cross-layer review

### Page delivery and request ownership

- `ArticleListFragment.setData` remains the shared successful normal/prefetch
  delivery seam. `ArticleAuthorIds.fromPage` copies all distinct eligible author
  IDs from the delivered rows, including never-bound/below-viewport floors, and
  excludes null, anonymous, and nonpositive identities.
- The enrichment seam has no selected-page/RESUMED/viewport gate, fixed page
  count, three-page barrier, new thread read, or second prefetch planner.
  Existing `ArticleListPresenter`, model, planner, and final-page exclusion are
  unchanged. Seven arbitrary 23-author page deliveries are covered by executing
  repository tests alongside the unchanged prefetch/request/refresh regressions.
- The app-scoped service shares cache/queued/in-flight work. Only one physical
  supplementary request occupies the slot; cancellation does not free it until
  its terminal callback. Ordinary completion dispatches the next eligible author
  immediately, without advancing the fake clock or using a pacing timer.
- Cache-only saved/retained readers now neither enqueue their own misses nor
  resume an expired paused queue. New online page data is required for that
  demand-driven resume; no background timer was added.

### Late page completion versus destroyed views

`BaseMvpFragment` attaches the presenter for the Fragment lifetime;
`BasePresenter.performDestroy` detaches only at Fragment `ON_DESTROY`.
`ArticleListModel.loadPage` still binds both request/parser delivery stages to
`FragmentEvent.DETACH`. Therefore a view-destroyed but retained Fragment can, in
principle, receive a model completion before its Fragment is detached.

That framework possibility is distinct from ordinary offscreen prefetch:

- The supported online pager uses `FragmentStatePagerAdapter` with
  `BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT` and offscreen limit two. Its retained
  STARTED pages still have views and location owners. Pausing the selected page,
  navigating within retained pages, or stopping its Activity does not trigger
  the `getView() == null` exclusion; a valid late success still enriches there.
- `ArticleListActivity.setupFragment` installs one root thread/search Fragment
  without adding it to a back stack. The audited app and shared host sources
  contain no `setRetainInstance`, `addToBackStack`, or manual `setMaxLifecycle`
  path holding these thread Fragments at a viewless CREATED state. Ordinary
  pager eviction/activity teardown destroys the Fragment and retains the
  existing presenter-detach/request-cancellation semantics.
- If a view has actually been destroyed, `onDestroyView` has already closed
  its location Page, detached the adapter, and unbound fields. `setData` retains
  the body in `mDeliveredData` but initiates no new work for that dead view
  owner. A later replacement view renders that retained data and uses
  `deliver(..., false)` for cache-only location rebinding. Already-dispatched
  profile work may finish into a still-valid account cache, without old UI
  delivery; pending orphan jobs are pruned.

This matches the approved owner-destruction rule and does not impose a new
page-range/foreground policy on valid prefetch. No fetch-on-rebind trigger was
introduced. This is source/lifecycle reasoning plus pure repository and wiring
regressions, not an executed Android lifecycle test.

### Session, cache, and transport boundaries

- Session capture reads the settled `UserManager` list/index after immediate
  invalidation, handling both index-before-activeUser notification and
  `removeUser`'s stale `getActiveUser()` behavior. Both account observables are
  view-lifecycle bound, and UID/CID/UA are copied into an immutable session.
- Every dispatched completion rechecks scope even while lifecycle observers
  are stopped. Late 429/session-rejection signals are recorded against the
  captured scope/session before obsolete UI epochs are rejected. Same-UID
  changed credentials are distinct rejection sessions; another account cannot
  receive the old result or inherit its session rejection.
- Success/valid-empty observations last 24 hours; ordinary failures cool down
  their key for ten minutes. 429 pauses at least thirty minutes or a longer
  valid server delay. Authentication/challenge/unknown non-profile rejection
  stops the affected immutable session. No automatic identity rotation occurs.
- Cache identity includes normalized HTTPS origin, viewing-account UID (guest
  sentinel), and author UID. The versioned app-private file is serialized,
  bounded, atomically replaced, and contains at most 1,000 latest records.
  Observation eviction preserves server-pause guards. Corrupt, invalid, expired,
  and backwards-clock observations become misses.
- The dedicated USER.PROFILE transport carries explicit Cookie, established
  browser UA/official header, and same-origin profile Referer; validates an exact
  HTTPS origin; disables redirects and connection retries; bounds decompressed
  reads to 256 KiB; and uses 10-second connect/read and 20-second call timeouts.
- F1-F3 from the independent review remain fixed: supported wrapper-prefixed
  non-profile text is rejected; late server stops survive consumer invalidation;
  the operation-only network interceptor disables OkHttp 3.12's automatic
  `503 + Retry-After: 0` replay while preserving response status/body and 429
  metadata. The permanent test counts actual loopback requests.
- Shared envelope normalization keeps the legacy profile reader's wrapper
  repairs. Location parsing accepts a valid matching profile identity and
  bounded plain text only. No raw IP, full profile, Cookie, location history, or
  response body is logged/persisted by the supplementary implementation.

### Metadata and loading UI

- `ArticleListAdapter` retains post count and replaces floor level/reputation
  with known public location. Missing/anonymous/expired values omit the label.
  This is the latest profile observation, not a historical posting location.
- Location payloads check data generation, author identity, and bound row
  identity, then call only the author-detail binder. They never invoke the
  body WebView binding path or `notifyDataSetChanged` for location delivery.
- Each loader is bound after view-field initialization to the Fragment's view
  lifecycle. Selection requires RESUMED plus attached/shown/window-visible
  loading UI. Pausing or hiding an ancestor preserves the unfinished occasion;
  hiding the loader itself or destroying its owner ends it. Background prefetch
  does not consume a tip and ready pages do not resurrect their loading overlay.
- Existing foreground success/error/empty transitions remain, including the
  pre-existing 300 ms article-success transition. No timer, extra waiting period,
  remote copy, operation-dialog change, or pull-refresh overlay was introduced.
- Eight base tips match the implemented online-page, reply/compose FAB, title,
  favorite-board, home-tab, and emoticon gestures. AI eligibility requires the
  actual `pref_ai_settings`/`SettingsAiFragment` entry plus a loadable Fragment
  destination. Neither is present in this build, so its AI tip is excluded.
- XML/static checks preserve spinner/wrapper IDs, dimensions, 8 dp margin and
  semantic background. Tip text uses 16 sp, wrap-content/multiline sizing,
  semantic `text_color`, 24 dp horizontal padding, and no truncation, forced
  accessibility announcement, or duplicated content description.

## Verification

Final command after all reviewer product/test edits:

```bash
./gradlew :nga_phone_base_3.0:assembleDebug \
  :nga_phone_base_3.0:testDebugUnitTest \
  :nga_phone_base_3.0:lintDebug --console=plain \
  > /tmp/nga-thread-location-loading-tips-combined-check-app-final.log 2>&1
git diff --check
```

- Lint: **pass**, inspected app XML: **0 Error / 0 Fatal**; 723 warnings and one
  informational issue remain diagnostic. `lintAnalyzeDebug` and the unit-test
  analysis ran against the changed source; unchanged report output was reused
  by Gradle as expected. Parent owns final all-module report inspection.
- TypeCheck: **pass** through Android resource compilation and Java/Kotlin debug
  compilation in `assembleDebug` and the current test build.
- Tests: **pass**, **223 tests / 33 suites**, **0 failures / 0 errors / 0 skipped**.
  XML suite timestamps: `2026-09-11T17:27:09` to `2026-09-11T17:27:11` UTC.
- Location coverage: **49 tests** (repository 21, store/cache 7, parser 7,
  transport 7, session 3, Android source contracts 4).
- Loading coverage: **18 tests** (catalog/capability 5, selector 4, occasion
  policy 9), plus the **7-test** existing/new prefetch wiring contract suite.
- Additional static/XML audit and `git diff --check`: **pass**.
- Gradle result: exit 0, `BUILD SUCCESSFUL in 18s`, 552 actionable tasks.

Final repository source SHA-256:
`5238e02b709d4a9675e2093144dc97b291462f38fb5e1a895a459ad61a6e45e9`.
Final repository-test SHA-256:
`a663b4d4cf3c471287579a138982a219349d98eeafe6b6d694043c797c504e84`.
Final transport-test SHA-256:
`d510c672db77d50b320c79f26c2b8259a15ce84ecda8617eb2c9c2f904d04df1`.
The three other request-control production hashes remain those documented in
the independent `request-control-fix-validation.md` report.

No live NGA traffic or device/ADB/instrumentation execution ran. Android UI
behavior was source-reviewed and supported by pure policy tests; no device UI
or current NGA availability claim is made. No Preview/Release build, push,
commit, archive, publication, or sibling-worktree mutation was performed.

## Handoff

Product/test ownership and Gradle ownership are released to the parent. The
parent should finish repository-wide debug diagnostics/all-module lint XML
inspection, capture the verified contracts in specs (including F4's cache-only
dispatch boundary), and complete task/commit/archive/session bookkeeping. No
further product edits are pending from this reviewer.
