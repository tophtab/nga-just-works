# Independent comprehensive review

Reviewed the full uncommitted change against HEAD (including both earlier
passes), the third-pass baseline/ownership/deletion artifacts, current test
sources and relevant production seams. No production behavior or publishing
workflow change is part of this reduction. Main owns serialized Gradle/Python
execution; this reviewer did not start competing suites or device operations.

## Findings

Initial review found no blocking loss of unique critical coverage. The final
13-removal follow-up identified one distinct integration assertion described
below; the reviewer restored it with main's agreement. Main's rerun passed.
Flagged orphan OkHttp imports after the repository test deletion;
implementer cleanup subsequently removed them. A final changed-test import
reference scan found no remaining single-use import names.

The historical spec instruction to feed an actual empty HTTP 503 into the
repository harness needs ownership-based wording. Main owns that spec update.
The deleted test manually called `readResponse` and passed its result to
`Harness.complete`; it did not exercise real transport-to-repository wiring.
`ProfileLocationTransport.readResponse` returns `rejected()` before reading any
503 body. Retained null/unreadable-body tests protect this ordering, and the
real-client test protects the no-retry boundary. Repository rejection and
queued-stop tests independently protect stopped sessions and invalidation.
Requiring this exact composite fixture would preserve duplicated checks.

### Last 13-removal follow-up

Twelve additions to the deletion ledger are justified by retained ownership or
explicitly acknowledged low-risk loss. In particular, both failed-clear tests
exercise subsequent successful cleanup, queued progress/success tests exercise
the controller executor, JSON client tests retain reasoning and immutable
empty-model integration, and repository/Page handoff tests retain shared
inflight authors and orphan retirement.

One deletion had a distinct failure signal and was fixed:
`SummaryInputTest.explicitCommentDoesNotTreatItsRawNumberAsAKnownFloor` uses
`lou=7` with COMMENT presentation. The cited remaining `missingFloor...` test
uses `lou=-1`. Replacing `FloorSummaryInput`'s use of
`ArticleRowPresentation.hasFloor(row)` with `row.getLou() >= 0` would now pass
all remaining SummaryInput tests while inventing a comment floor in the
prompt. Lower-level classifier tests cannot detect this caller bypass.
Restored the exact compact test from the third-pass baseline after main's
agreement; changed no production code. Main validated the restored case.

The earlier arbitrary-page-count ledger row also referenced the subsequently
deleted `independentForegroundAndHiddenPrefetchDeliveries...` owner. Corrected
it to final shared-consumer and Page handoff owners while preserving the
explicit 162-author stress-coverage loss.

## Critical ownership checked

- Secrets/storage: configuration v1 migration still runs the real decoder,
  proves no write/key creation on read and upgrades on save. Record tests keep
  nonce/authentication, malformed versions, style/text bounds and tampering.
  Store tests keep lost versus temporarily unavailable keys, failed atomic
  replacement and both independent deletion failures. The maximum multibyte
  real-AES record and stored-prompt/controller path replace repeated roundtrips.
  The Android preference secret-persistence guard remains intact.
- Accounts/cache/export: all eight owned cache tests retain corruption,
  owner-scoped list/read/write/delete, supported legacy ZIP import/export,
  traversal/symlink refusal and mid-write owner change. Image-mode migration
  keeps all four tests. Author repository/page tests retain same-UID credential
  replacement, captured-account stop ownership, pending invalidation and stale
  delivery rejection. Selection's implicit external-text-export guard remains.
- Cancellation/transport: discovery and summaries actually share generic
  `AiSummaryClient.enqueue` and failure classification. Retained fallback
  cancellation covers `onFailure`; retained progress cancellation covers
  `onResponse` cancellation while decoding. The progress latch has a bounded
  wait and unconditional release. The profile blocked-read test joins response
  completion before asserting suppression and verifies the subsequent cooldown.
  Declared/chunked/decompressed limits, real deadlines, no redirected secrets,
  and independent network/session identities remain.
- Reader/UI/editor: parser status matrices now belong to the common classifier;
  both parsers retain real error-precedence integration. Body resource tests
  independently protect owner/source/generation/query/page-size fields, which
  are separate identities, not redundant examples. Editor token/source-offset,
  stale-revision and retired-ticket protection remains. Removed layout/copy
  snapshots are explicitly acknowledged as lost assertions, not runtime proof.
- Release: retained Bash fixtures prove signing refusal, all six manifest
  fields, asset integrity, tag/SHA and prerelease identity, partial lookup
  failure, all publication failure sites and stop-on-delete-failure. The
  cleanup matrix keeps exact AI legacy migration and lookalike refusal.
  Arbitrary legal branch tests execute create and PATCH with shell sentinels,
  making the separate title-literal test redundant. Collision/case/raw-ref
  identity still has actual publication coverage. Kotlin retains production
  signing/debuggable configuration and single-Gradle-invocation guards.
- Python parser seams/device: all eight invalid release-note rows remain via
  the real pure validator; real CLI success and failure remain. All three
  device tests are unchanged and own actual Keystore/AtomicFile guarantees
  that host fakes cannot establish.

## Reduction limits

The 250–350 JVM ambition is not met (472 after the restored regression:
250 AI-owned plus 222 reader-owned methods, 63 files and 10,770 lines). Reader large
suite retention has concrete field/transition justification in its ownership
ledger. AI/profile retention protects distinct framing, typed input, privacy,
resource bounds and asynchronous transitions; these cannot safely be removed
by a uniform quota. Final reporting must disclose the unmet count target and
measured performance separately. Deleted direct formatting/default/Unicode
blank spelling and source-wiring combinations are documented coverage losses.
No scenario moved into a loop, skipped test or disabled variant is being counted
as deletion. Changed request-count assertions match the actual remaining rows.

## Verification

- `git diff --check`: pass at independent review.
- Changed JVM import reference scan: no orphan imports found after cleanup.
- Python: main's `final-python-benchmark.json` records three successful serial
  discovery runs; `final-python-3.log` reports 32 tests, `OK`.
- JVM tests: pass, 472 tests across 63 suites, zero failures/errors/skips.
  Reviewer independently parsed `comprehensive-final-gate.json` and checked
  totals; `review-fix-tests.log` records successful execution after restoring
  the COMMENT regression.
- TypeCheck: pass through Java/Kotlin test compilation in the Gradle gate.
- Lint: pass, all 13 module reports contain zero Error/Fatal issues. Initial
  combined execution reported 21 internal KAPT-stub `NoSuchFileException`
  LintErrors; main reran only lint tasks after compilation stabilized.
  `lint-recovery.log` records success. No source/config suppression was added.
- Device execution: not run per project policy.

Independent review is complete with no unresolved code/coverage findings.
Main owns the final controlled benchmark and delivery summary.
