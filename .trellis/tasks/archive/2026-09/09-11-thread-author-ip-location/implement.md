# Implementation Plan: Thread Author IP Location

Status: approved for implementation on 2026-09-12 (user: “确认吧。开干吧。”).

Parent coordination update (2026-09-12): before handing off, read
`research/implementation-review.md`. A parallel read-only audit reproduced
three request-control defects in the initial implementation. Resolve or report
each finding with regression coverage; do not treat the initial parser/transport
slice as checked. The parent has made this report part of both final check
manifests. The audit agent made no product edits, so implementation ownership
remains with the original writer until handoff.

Shared-file handoff (2026-09-12): parent source review and the first app
assemble/unit/lint checks have passed for the IP fragment integration. The
remaining IP fixes concern profile parsing/transport/repository/tests.
`ArticleListFragment.java` now passes to the loading-tip writer for only its
`LoadingLayout` type/import/view-lifecycle binding. Preserve that narrow change;
do not overwrite it when recording IP completion. Parent performs the combined
full-scope check after this binding and the request-control regressions settle.

## Ordered work

- [x] Review the revised mechanism-driven PRD/design and latest approval, validate
  context, then activate this child with `task.py start`.
- [x] Load `trellis-before-dev` and the curated platform/network/UI contracts.
- [x] Add a side-effect-free profile envelope/location parser with fixtures;
  share wrapper normalization with the existing profile reader and add the
  bounded supplemental `USER.PROFILE` transport without its UI side effects.
- [x] Implement the application-scoped repository, bounded persistent cache,
  explicit account snapshot, shared in-flight reuse, immediate non-paced
  dispatch, and failure policy. Verify with fake storage/transport/clock.
- [x] Integrate each normal/prefetched page's author set at the existing successful
  page-delivery seam. Do not add another prefetch window, fixed page-count batch,
  visibility gate for prefetched data, or delay waiting for sibling pages.
- [x] Bind metadata-only delivery and stale-consumer cleanup; saved-page readers
  use cache only. Preserve the existing thread request and prefetch lifecycle.
- [x] Replace detail copy using resources; verify anonymous/missing data and
  body-WebView stability.
- [x] Dispatch `trellis-check`; fix confirmed findings and run the final quality
  gate after integrating both child tasks.
- [x] Update the USER.PROFILE/thread metadata specs from verified behavior.
  Parent coordinates eventual commit/archive/session wrap-up.

## Ownership and integration

Own `ArticleListAdapter.java`, the narrow page-enrichment binding in
`ArticleListFragment.java`, new repository/transport/parser/cache code, profile
parser sharing, and author-detail resources. Keep page planning/state/wire
behavior unchanged. Coordinate the shared fragment with the loading-tip child;
do not edit it concurrently. Do not modify or merge the sibling AI worktree.

## Checks

Meaningful tests cover:

- valid/wrapped/empty/malformed/error/challenge profile fixtures;
- persistent cache, TTL boundary, negative cache, corruption and eviction;
- independent normal and hidden-prefetch page completion, duplicate UIDs across
  concurrent pages, arbitrary delivered-page counts, and no all-pages barrier;
- immediate next dispatch without advancing a clock, plus the selected concurrency
  limit; no start-time-spacing, sleep, or time-quota requirement;
- incremental page prefetch, existing final-page exclusion, valid late completion,
  view/data/account changes, and cross-activity reuse;
- 429/Retry-After pause, authentication/challenge stop, and failure cooldown;
- metadata-only adapter updates and saved-page cache-only behavior.

Keep existing refresh, page-request-state, prefetch, and page-cache regressions
passing. Run the parent integration gate once after final code changes:

```bash
./gradlew :nga_phone_base_3.0:assembleDebug
./gradlew :nga_phone_base_3.0:testDebugUnitTest
./gradlew :nga_phone_base_3.0:lintDebug
./gradlew lintDebug --continue --rerun-tasks --console=plain
./gradlew testDebugUnitTest --continue
git diff --check
```

Inspect every module's lint XML for Error/Fatal entries. No live NGA probe,
device operation, or local Preview/Release package is required.

## Review and rollback

The revised summary was approved before activation. Replan if supported
profile parsing requires a broader transport change. Reject another page planner
or a request timer introduced as a substitute for the removed interval. Rollback
the independent lookup/detail integration; saved thread files need no migration.

## Implementation handoff — 2026-09-12

Implemented in the isolated feature worktree. See
`research/implementation-handoff.md` for the actual boundary, review resolutions,
and validation evidence. All three findings in `implementation-review.md` have
owner fixes plus permanent offline regressions. The last local module gate
passed Debug assemble, 219 app tests (46 location-specific), and app lint with
0 Error / 0 Fatal in XML. No live NGA/device operation ran.

The parent still owns combined `trellis-check`, all-module validation, spec
updates, commit, archive, and session wrap-up. No child implementation commit was
created, and unrelated/loading-tip changes were preserved.

## Final Integration — 2026-09-12

Combined Trellis review is complete. It added permanent late-429/credential/
physical-request assertions and fixed one additional boundary: a cache-only
subscription cannot resume an online queue when its pause expires. The final
app gate passed 223 tests, including 49 location/source-contract tests; all 13
Android lint reports have zero Error/Fatal. The repository debug-unit diagnostic
reproduced only documented example-fixture failures in statistics/debug modules.

The parent recorded full evidence in `research/combined-check-report.md` and
`research/final-validation.md`, and added
`.trellis/spec/backend/author-profile-location-contract.md` with operation,
prefetch, index, and frontend metadata links. The earlier handoff above remains
a historical snapshot; its pending parent review/spec steps are now complete.
