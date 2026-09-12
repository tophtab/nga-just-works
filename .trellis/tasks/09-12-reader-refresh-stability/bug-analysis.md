# Bug Analysis: article refresh loses stable rendered state

## 1. Root cause category

- **E — Implicit assumption:** a new response object was treated as a new
  rendered page. An adapter bind calling `loadDataWithBaseURL` was also mistaken
  for proof that the platform WebView loaded HTML, overlooking the override.
- **B — Cross-layer contract:** response ownership, body-view lifetime and
  metadata subscription lifetime changed together even when the visible values
  remained valid. Replacing a consumer was conflated with invalidating its data.
- **D — Test coverage gap:** request/cache tests and source wiring checks did
  not exercise body-resource disposal or every metadata emission during the
  actual Android Page controller's deferred handoff.

## 2. Why the earlier correction was incomplete

1. `16cc185b` correctly avoids repeated list binding for an unchanged READY
   response. It does not cover a fresh response object from manual refresh.
2. The initial explanation stopped at the adapter's load call instead of
   following the `LocalWebView` override. The override has skipped equal HTML
   since 2020; destroying the instance is what loses that protection.
3. The compatibility adaptation sized body retention for variable page lengths
   but tied release to response-reference inequality. The spec repeated this
   overbroad "release on replacement" rule, so following it preserved the bug.
4. Adapter assignment and Page delivery each cleared author metadata before a
   fresh cache hit. The old source contract even pinned an empty-delivery string;
   it did not verify whether the UI had to pass through that empty state.

## 3. Prevention mechanisms

| Priority | Mechanism | Specific action | Status |
| --- | --- | --- | --- |
| P0 | Resource ownership | Preserve applicable body resources across fresh same-page data; release only obsolete ownership | Implemented; 25 body-owner tests pass |
| P0 | Delivery handoff | Keep invalidation connected until replacement publishes its authoritative snapshot; own the new handle before synchronous output | Implemented; 18 page-controller tests pass |
| P0 | Behavioral tests | Exercise production retention/controller seams and record release counts and all emitted values | 103 focused tests pass across seven suites |
| P0 | Spec correction | Distinguish a wrapper call from actual platform loading and response replacement from page retirement | Documented |
| P1 | Honest validation | Separate source/JVM evidence from Android visual measurement | Recorded in validation and check results |

## 4. Systematic expansion

- Trace both full-body refresh and supplemental metadata completion because
  they share adapter presentation but have different ownership and update costs.
- Preserve the existing request state machine, refresh behavior, account
  acceptance and metadata repository. No global cache/framework replacement is
  needed to repair these two ownership boundaries.
- Review subclass overrides and resource destruction together when diagnosing
  duplicate loads. Check value sequences and identity across a fresh response,
  not just same-object replay or a final-state assertion.
- Clearing already-drawn text requires comparing against what was drawn, not
  re-reading a revoked snapshot whose old value now returns null.
- A synchronous initial subscription callback can destroy its owner before
  `subscribe` returns. Give the owner its registered handle before publication,
  then check consumer validity before dispatch. Test this with an idle request
  slot; keeping another request active can hide an unwanted dispatch.

## 5. Knowledge capture

- [x] Correct frontend retained-entry and manual-refresh lifetime contracts.
- [x] Specify author-location handoff and unchanged-text behavior.
- [x] Add the correction to the prior archived task's root-cause explanation.
- [x] Record final behavioral test results and independent review: 677 tests,
      13 lint reports without Error/Fatal, and 1,198 unchanged inputs. See
      [validation.md](./validation.md) and [check-results.md](./check-results.md).

The project is an Android application, not the Trellis template source package;
its local project specs have no `src/templates/markdown/spec/` counterpart.
