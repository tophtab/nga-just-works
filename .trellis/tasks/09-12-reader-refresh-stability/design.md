# Design: retain rendered bodies and hand off metadata without a blank

The user approved the two-part implementation summary and explicitly requested
commit, finish-work, and push after completion. No product decision remains.

## Body ownership

Restore release 5.6.1's important property: accepting a fresh response is not
itself a reason to destroy all existing body WebViews. Retain applicable views
for the same rows within the accepted page context. The adapter must still bind
new row data and image URL metadata; `LocalWebView`'s existing HTML equality
guard decides whether the actual body needs loading.

The newer reader supports variable row counts, so do not restore the old
fixed-length 20-element array. Correctly retain surviving rows, allocate for
new rows, and release removed or incompatible resources. Do not reuse a view
for an unrelated row or retired page/account/source generation merely because
its numeric list position matches. Unknown identities require conservative
handling. Existing Fragment reader invalidation and `onDestroyView` cleanup
remain authoritative. The prior unchanged READY render guard stays in place.

Use a narrow Android-free production owner/policy if needed to exercise real
retention/release behavior with lightweight fake resources. Avoid introducing
a generic application cache, RecyclerView redesign, or a new test framework.

## Metadata ownership

See `research/metadata-delivery.md` for the traced delivery path and races.
Keep valid snapshot observations while an ordinary response replaces the
location subscription. Eliminate both unconditional clearing operations:
adapter data assignment must not discard valid location state, and preparing
a replacement must not publish an artificial empty snapshot.

Separate pending replacement sequence from active delivery generation. Keep
the old active subscription able to publish account invalidation while waiting
for session settling. Install the new subscription (which synchronously
publishes current cached metadata) before closing the old one. Check pending
sequence, session signal, and close state before installing. Once replacement
is active, ignore the retired subscription's callbacks.

The repository must give the controller its newly registered subscription
before invoking the synchronous initial display callback. Use a package-private
registration callback while preserving the existing public subscribe API.
Retire the previous consumer during the new consumer's first publication, before
dispatch. If that publication closes or null-resets the page, the new handle is
already owned and can be closed immediately; a retired subscription must not
enqueue or start requests even when the physical request slot is idle.

Extract the existing Page orchestration into an Android-free, page-specific
controller if necessary for executable tests; the Android shell retains main
thread scheduling, lifecycle/LiveData adaptation, and weak-reference ownership.
Null input and close immediately invalidate pending work and obsolete consumers.
READY replay preserves online/cache-only intent and does not create new work.

Only current author details update. Compare the newly formatted value with the
holder's actually displayed text before assigning it. Do not compare two
snapshot lookups as a substitute: epoch invalidation can make both lookups null
while old text is still drawn. Preserve payload generation/author/holder checks,
anonymous/unknown filtering, expiry, session guards, and supplemental pacing.

## Boundaries and verification

Transport/parser/cache TTL/rate-limit behavior is unchanged. No refresh is
suppressed at the request layer. Content edits, changing row counts, title/menu/
anchor updates, and reader invalidation remain real updates.

Test the production retention owner and metadata handoff with fake resources,
clock, executor, and the existing real repository fake transport. Assert
resource identity/release counts and emitted value sequences, not copies of
implementation logic. Exercise synchronous close/null-reset with both occupied
and idle physical request slots. Preserve wiring contracts and update only assertions
whose intentionally changed seam moved. The final gate is the repository debug
build/JVM/lint matrix. No device/ADB or live NGA traffic was requested; do not
present host tests as a measurement of visual smoothness.

Rollback is limited to this task's adapter/controller changes and associated
tests/specs. Preserve compatibility/IP features and unrelated AI-profile work.
