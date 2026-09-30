# Execution plan — implemented and verified

> Task organization (2026-09-30): R7 was detached as an [archived research deliverable](../archive/2026-09/09-30-reader-native-loading-research/delivery.md). This parent owns R1–R6 only; further R7 repair/removal work remains deferred.

This plan records the six-item implementation scope. The earlier planning session did not activate tasks. Update 2026-09-30: R7 research was detached and archived; its product work remains outside this six-item implementation plan.

## Planning completion

- [x] Capture six implementation requests in parent/child PRDs; preserve the original seventh as detached research.
- [x] Inspect current implementation, existing contracts and public checkInfo.js.
- [x] Compare all 238 picker mappings with the main decoder; record two AC mismatches.
- [x] Curate real spec/research entries for child implement/check manifests.
- [x] User selected automatic resume of outstanding online location work on return.
- [x] Resolve R1: supplied Via nga://openType=2?... and saved JS type 2/5 match; no further user material needed.
- [x] Record user-selected random 200–500 ms interval and accepted R4 behavior.
- [x] Confirm network retry count: first attempt plus exactly one tail retry if needed.
- [x] User selected 30-second suppression after both network attempts fail; no timer-triggered third attempt.
- [x] Complete child design.md / implement.md for R1–R6.
- [x] Perform lossless PRD convergence pass after all six product scopes are settled.
- [x] User explicitly approved the final six-item PRD in the 2026-09-30 implementation session.
- [x] Activated approved children individually in the implementation session; parent remains the coordinating record.

## Delivery order in the new session

1. R5: mapping correction/consolidation; provides R6’s stable emoticon resolver.
2. R2: thread visibility, cancellation, random spacing, one tail retry, confirmed resume and 30-second network-failure suppression.
3. R3: evidence-based error wording using synthetic HTTP/body fixtures. Do not implement R7 diagnostics or change browser/source policy.
4. R4: full-reader target metadata and bounded actual-reply navigation, after shared R2/R3 files are integrated.
5. R1: confirmed external URI intake, integrated with R4 launch fields. System chooser behavior remains a device limitation.
6. R6: restore editor/draft image and emoticon previews using R5’s resolver.
7. Review all six together; R7 research is archived independently and its deferred product work is not a dependency of R3/R4.

## Focused validation

- R5: all supported category/name codes resolve to the picker asset; known reversed pair, repeated tokens and unknown-token handling. Verify filename-based order is preserved.
- R3: 403, redirect, 200 HTML, explicit deleted/missing, access challenge, regular text mentioning deletion; no additional authenticated request.
- R2: exit while queued/in flight, stop/resume, view recreation, shared authors across threads, cancellation race, stale completions, 429/503, cache restore, timer deadline. Use Android-free production seams and fake scheduler/transport.
- R4: first/later page, differing source page sizes, pid-only reply, missing floor, deleted target, source/account generation changes, unchanged ordinary show-all behavior.
- R6: original-source round-trip, editing around/deleting media, mixed/repeated tokens, image load failure/cancellation, restoration without duplication, no re-upload.
- R1: local URI parser and manifest checks using confirmed formats; invalid identifiers, unsupported hosts and extras. Device tests are not implied.

## Required verification after product implementation

Use the current Android quality contract; default device-independent gate:

```bash
./gradlew :nga_phone_base_3.0:assembleDebug
./gradlew :nga_phone_base_3.0:testDebugUnitTest
./gradlew :nga_phone_base_3.0:lintDebug
./gradlew lintDebug --continue --rerun-tasks --console=plain
./gradlew testDebugUnitTest --continue
```

Inspect every Android module lint report for zero Error/Fatal; a process exit alone is insufficient. Focused tests must execute nonzero cases with no failures.
No device operations are authorized. Bounded authenticated NGA read comparisons for the four user-supplied R7 samples and one production-client transport comparison were explicitly authorized and completed; this does not authorize unrelated operations or unbounded probing. Planning-only changes do not require an app build.

## Review and rollback points

Dispatch Trellis implement/check roles only after task activation and approved artifacts. Give exclusive ownership of shared reader files.
Review each child before integrating the next. Preserve independent commits/revert boundaries without touching unrelated user changes.
Update relevant project contracts only with approved resulting behavior. Do not archive parent or claim completion while selected deliverables remain open.

## Final execution status — 2026-09-30

R5 → R2 → R3 → R4 → R1 → R6 implemented and independently checked. All five required commands passed; 724 tests and 13 clean blocking-severity lint reports. Specs synchronized. See integration-verification.md and commit-plan.md. No commits/publication yet.

## Release repair execution

- [x] Set explicit setup packages in .github/workflows/build.yml.
- [x] Add regression asserting bootstrap avoids tools and retains required SDK packages.
- [x] Run scripts unit suite and workflow syntax check; independent Trellis check.
- [x] Update quality spec and 6.1.1 notes; validate version identity.
- [x] Commit, finish-work and push main/6.1.1.
