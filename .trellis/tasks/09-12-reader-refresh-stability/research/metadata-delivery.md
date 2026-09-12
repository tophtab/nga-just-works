# Research: Stable author-location delivery during refresh

- Query: Find the smallest correct correction for IP-location text disappearing and reappearing during manual article refresh; identify an executable host-JVM seam and preservation requirements.
- Scope: internal source inspection; no code changes, Git operations, Android device, ADB, or live NGA calls.
- Date: 2026-09-12

## Findings

### Observed cause and relevant files

Paths below are relative to the repository root. Line numbers refer to the source inspected before implementation.

| File | Responsibility / evidence |
| --- | --- |
| `nga_phone_base_3.0/src/main/java/sp/phone/ui/adapter/ArticleListAdapter.java:382` | Every `setData` increments the payload generation and resets `mAuthorLocations` to `Snapshot.empty()`, even when a new response has the same authors and the existing observations remain fresh. |
| `nga_phone_base_3.0/src/main/java/sp/phone/profile/AuthorLocationService.java:216` | `Page.deliver` preserves identity-equal READY replays, but a fresh object closes the subscription, emits empty at line 235, and subscribes only after `whenSessionSettled`. |
| `nga_phone_base_3.0/src/main/java/sp/phone/profile/AuthorLocationService.java:164` | Settlement deliberately posts to a subsequent main-loop turn, then waits if account values are still being updated. |
| `nga_phone_base_3.0/src/main/java/sp/phone/profile/AuthorLocationRepository.java:180` | `subscribe` publishes a fresh cache snapshot synchronously before it enqueues or dispatches missing online work. No empty placeholder is required ahead of a cache hit. |
| `nga_phone_base_3.0/src/main/java/sp/phone/profile/AuthorLocationRepository.java:55` | A snapshot lookup checks its shared invalidatable epoch and the entry's TTL at the time of reading. Closing a subscription does not invalidate the epoch. |
| `nga_phone_base_3.0/src/main/java/sp/phone/profile/AuthorLocationRepository.java:193` | Account invalidation invalidates the old epoch first, publishes empty to all still-attached consumers, detaches them, and cancels obsolete work. |
| `nga_phone_base_3.0/src/main/java/sp/phone/ui/fragment/ArticleListFragment.java:494` | An accepted successful response renders the body first, then calls the Page delivery seam. Both foreground and accepted offscreen deliveries use it. |
| `nga_phone_base_3.0/src/main/java/sp/phone/ui/fragment/ArticleListFragment.java:392` | View reconstruction renders retained data and requests cache-only metadata; destruction closes Page and releases its adapter. |
| `nga_phone_base_3.0/src/main/java/sp/phone/profile/ArticleAuthorIds.java:16` | Copies all distinct positive, nonanonymous authors from a delivered page; null rows and null page/list are supported. |
| `nga_phone_base_3.0/src/test/java/sp/phone/ui/fragment/ArticleAuthorLocationContractTest.kt` | Source-string assertions pin Android wiring and current Page implementation details, including the problematic empty delivery. |
| `nga_phone_base_3.0/src/test/java/sp/phone/profile/AuthorLocationRepositoryTest.java:600` | The existing test-local `Page` is only a repository-subscription wrapper, not the production service's Page orchestration. |

The visible sequence for a same-session refresh can be: existing location, adapter reset/body rebind to posts-only, service empty payload, deferred subscription's immediate cached location. It does not require a new profile request. Existing TTL, pacing, and stop policies are not the cause and need not change.

### Recommended smallest production boundary

Extract the existing non-Android Page orchestration into a narrow `AuthorLocationPage` (name is illustrative) used by `AuthorLocationService.Page`. Give it the existing repository, a settlement callback/executor, a session-signal supplier, and an output consumer. The Android wrapper should retain lifecycle ownership, LiveData observation, account capture, and weak delivery ownership. Do not build a general state framework or add a test framework.

The essential change is a subscription handoff, with **two distinct versions**:

1. A pending-delivery sequence rejects obsolete settlement callbacks after successive new response objects, null delivery, or close.
2. An active-output generation identifies the still-attached subscription whose snapshots may reach the display.

For a fresh nonnull response, record its identity and copied authors and advance the pending sequence, but keep the active subscription, active output generation, and current snapshot alive until replacement is ready. Do not emit empty while waiting. The old subscription must still be allowed to deliver an account-invalidating empty during this interval.

When settlement runs, validate closed state, the pending sequence, and the captured session signal. Install the new subscription, which immediately publishes the authoritative cache snapshot, then close the previous subscription. With an unchanged session and fresh overlapping observations there is no intervening empty. A changed scope or actual missing/expired observation still produces posts-only. Retain the existing unconditional deferred settlement; bypassing it could start work before an account signal that the old implementation would have rejected.

`repository.subscribe()` invokes the listener before returning. Take this synchronous publication into account when activating the output generation and assigning the returned handle. If a callback synchronously closes or supersedes the Page, close the newly returned handle rather than leaking it. A brief overlap must never allow an old-output callback to overwrite the replacement after activation.

This approach needs no repository cache/dispatch redesign. The old consumer remains present only until the scheduled replacement is installed, and closing it then prunes unsent orphaned authors using existing repository logic. Tests should verify that removed authors do not dispatch after the handoff and that overlapping consumers still share one physical request. If exact cancellation before settlement becomes a requirement, an immediate cache-only observer would need a separate safe repository seam; do not casually call `subscribe()` early because it synchronizes account state.

### Identity, invalidation, and lifecycle rules to preserve

- **READY replay:** identity-equal, nonnull data must replay the latest current output before touching versions, authors, subscriptions, settlement, or the repository. It retains the original online/cache-only intent, including while the first delivery is still pending.
- **Fresh response:** preserve displayed current-session metadata while awaiting the replacement, but actually replace the subscription using the new author's set and new delivery intent.
- **Null:** always clear remembered response identity, invalidate pending work and active output, close any active consumer, and publish empty immediately. Repeated null delivery cannot be treated as a READY replay.
- **Close:** release the remembered response and active consumer, invalidate pending callbacks/output, and suppress subsequent emissions. A deferred registration after close must not occur.
- **Account signal during replacement:** retain the active subscriber and its accepted output generation until replacement. Repository invalidation can then visibly clear text immediately. The stale queued replacement must not register under the new session. A matching latest callback with a mismatched signal should settle into empty, never recover an older snapshot.
- **Account/domain/credential changes without a prior observed signal:** `subscribe()` and lifecycle synchronization still call `synchronizeSession()`. Keep that authoritative check; do not treat the Page's last response identity or page number as account identity.
- **TTL:** use `Snapshot.location(author, now)` on every relevant bind/replay. Never freeze the old location into a replay cache that bypasses epoch or expiry.
- **View recreation:** a new Page receives retained data with `online=false`; a later READY replay must not promote it to online. `onStart` should synchronize the repository and replay its current snapshot to clear expired text without dispatching supplementary requests.
- **Weak ownership:** the app-scoped repository must not acquire a strong path to an Activity, Fragment, adapter, or Page containing such an output callback. Preserve the weak sink boundary when extracting orchestration.

### Adapter presentation changes

Keep `mAuthorLocations` for nonnull data replacement; clear it for true null/reset. Existing snapshot epochs and TTL make retained observations safe to read. A new row must read its own positive author UID; anonymous rows must always have no location and unknown-user rows keep their hidden detail visibility. Do not reuse a position's old author's text.

The current `setAuthorLocations` comment at lines 400–401 identifies an essential trap: after `invalidateSession`, both `oldSnapshot.location(uid, now)` and `empty.location(uid, now)` return null, even though a TextView can still contain `IP 属地：广东`. Comparing these two lookups can suppress the clear. The comparison baseline must be **what was actually drawn**, not a re-read of the old snapshot.

The smallest safe visible-update guard is to compute the new final detail string in `onBindAuthorDetail` and call `setText` only when it differs from `holder.detailTv.getText()`. If avoiding unchanged `notifyItemChanged` calls as well, maintain a small record of the final detail value last bound for each current row identity. Update it from actual binding, prune/reset it when rows are replaced, and compare new desired values against that record. Missing entries must still be handled by the full bind; do not confuse an unbound row with a row known to display posts-only. Avoid introducing an immutable rendered-string cache as the metadata authority: snapshots remain authoritative for deciding the next value.

Keep payload checks for adapter data generation, author UID, and `holder.nickNameTV.getTag() == row` (`ArticleListAdapter.java:465`). A metadata-only update must still bind only `tv_detail`, never enter body binding or call `notifyDataSetChanged`. The final detail value includes post count as well as location, so legitimate post-count changes also bind.

### Executable host-JVM test proposal

Use JUnit 4 already present in `nga_phone_base_3.0/build.gradle`; `ThreadData`, `ThreadRowInfo`, `ArticleAuthorIds`, and the real repository already execute on the host. A production `AuthorLocationPage` can be tested with the existing fake transport/clock/scheduler style plus a queued settlement callback and controllable signal counter. This verifies the actual delivery algorithm, unlike the old test-local Page wrapper or source assertions.

| Scenario | Behavioral assertion |
| --- | --- |
| Seed A's fresh location; deliver a new response containing A | Record **all** emitted A values before, during, and after draining settlement. No null may be interposed; no additional network request for fresh A. |
| New response has A plus a new author B, or removes/reorders authors | A remains visible; B reads only B's cache/result; old A cannot be attributed by row index; missing B may legitimately show posts-only. |
| New B has a fresh cached result from another consumer | The replacement's synchronous publication displays B from cache without a profile call. |
| A becomes anonymous, UID is invalid, row is null, or observation is valid-empty | Eligibility and detail behavior remove location; anonymous/invalid authors do not enter the request set. |
| TTL expires before fresh delivery or lifecycle replay | Previously drawn text clears, cached lookup does not refresh itself, and cache-only/READY replay does not start network work. |
| New response is pending and repository invalidation occurs | Output clears immediately before settlement runs; old snapshot lookup also becomes null; stale pending callback cannot subscribe or repopulate metadata. |
| Domain/account/same-UID credentials change | Old output cannot leak into the new epoch; underlying repository's stop and credential isolation regressions remain green. |
| Rapid fresh response A then B before settlement | Only B's pending replacement installs; stale callbacks cannot close B's consumer or overwrite its output. |
| Same response replay while first delivery is pending | No new settlement/consumer generation; original online flag is retained. |
| Same response replay after metadata arrives | Re-emit the current output without replacing a consumer or changing request counts. |
| Same response replay after invalidation | Re-emit empty/current output, never a saved pre-invalidation snapshot. |
| Null or close before settlement / after active registration | No future registration/output; old consumer's queued orphan work is pruned; remembered response reference is released. |
| Recreated Page starts cache-only then gets READY `online=true` replay | No supplementary request, including after another consumer's 429 pause has expired. |
| Synchronous new-subscription output closes the Page | Returned new handle is disposed; no hidden consumer leak or later update. |
| Old author completion after handoff | It cannot overwrite new rows; shared request and one-physical-slot rules are unchanged. |

If a pure production rendered-detail tracker is needed for notification suppression, test it with real repository snapshots: record a drawn location, invalidate the repository (so old snapshot lookup already returns null), then assert a clear notification is required; record posts-only and assert equivalent snapshots produce no repeated notification. Also cover expiry, reused author UID with anonymous state, and changed post count. Keep Android source wiring checks for the exact production tracker call sites and metadata-only binding, without claiming they execute Android widgets.

### Existing tests to revise rather than preserve verbatim

`ArticleAuthorLocationContractTest.kt:64–104` currently requires the fields and control flow inside `AuthorLocationService.Page`, including the unconditional empty at line 99. Move delivery behavior assertions into executable tests against the extracted production seam. Keep concise source checks proving the Android wrapper uses that seam, closes with its view owner, and observes current output safely. The Android wiring/source acceptance checks at lines 121–194 remain useful, but exact old generation variable strings need to reflect the split pending/active model.

`AuthorLocationRepositoryTest.java:303` is an executable cache-only/expired-pause regression and must remain. The pacing, cancellation, persistence, late server stop, credential isolation, cache freshness, and old-snapshot invalidation tests remain authoritative repository coverage; this fix does not justify changing their contracts.

### Related specs

- `.trellis/spec/backend/author-profile-location-contract.md`: delivery, READY replay, account settlement, epoch/TTL, request pacing and stop ownership. Update its fresh-response handoff description and test seam references after implementation.
- `.trellis/spec/frontend/component-guidelines.md:599`: author detail presentation, payload-only binding, lifecycle behavior.
- `.trellis/spec/backend/thread-page-prefetch-contract.md`: preserve accepted online/prefetched delivery and retained cache-only replay behavior.
- `.trellis/spec/backend/android-quality-guidelines.md`: debug/unit/lint gate; offline execution is not Android visual validation.
- `.trellis/workflow.md`: research persists here; main session owns the design, spec update, validation, commit and finish steps.

### External references and versions

No external browsing was needed. The module already uses JUnit 4.13.2 and OkHttp MockWebServer 4.12.0; no additional framework is needed for this seam. The contract's pinned profile route is background context only; this research proposes no parser, wire, retry, or pacing changes.

## Caveats / Not Found

- No Android visual measurement was performed; the evidence proves the avoidable empty/cache-hit delivery sequence, not the exact number of visible frames on a particular device.
- The 5.6.1 comparison is owned by the main session. The parent reports that 5.6.1 has no author-location feature; do not claim that an old IP presentation policy is being restored.
- Merely retaining a snapshot after closing its consumer is insufficient: epochs revoke future lookups but do not erase text already drawn. Keeping invalidation delivery connected throughout handoff is a requirement.
- This file recommends implementation seams and behavioral tests; it does not claim that they have been implemented or run.
