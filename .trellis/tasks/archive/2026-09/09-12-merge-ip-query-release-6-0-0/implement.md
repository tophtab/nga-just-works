# Execution plan

## Completed preparation and implementation

- [x] Create and activate the Trellis task with explicit maintainer authorization.
- [x] Record the later decision to defer stable 6.0.0 publication.
- [x] Inspect main `70ff31df65610ed0f7724135394fd51cc40e254c` and source branch
  `f5bcec31202635a5992d4fbf81537c71c93a86d4`; prepare the merge in isolation.
- [x] Resolve author-location, operation-registry, and component-guide conflicts;
  synchronize the backend index and prefetch/loading-tip contracts.
- [x] Restore Fragment bindings and online/cache-only delivery boundaries while
  keeping committed main AI behavior.
- [x] Transfer the pending merge to the main worktree at the user's explicit
  request; retain newer main `8a6b3249ba117f6d72b51f21e01e22cc47f78adf`.
- [x] Preserve unstaged AI and article-page-flicker changes, including the three
  shared Fragment/component/prefetch files; stage only IP integration hunks.
- [x] Fix repeated READY delivery at `AuthorLocationService.Page`: preserve
  consumer/online intent and republish the latest current-generation snapshot.
  Null clears replay identity and close releases the retained response.
- [x] Add two focused contract regressions and review the correction against
  both the committed renderer and parallel owner/body rendering changes.
- [x] Preserve the parallel reader commit `16cc185b` and its archive/journal
  commits as main advances during final checks; regenerate the product
  snapshot with that reader fix included, leaving AI WIP unstaged.

## Validation gates

- [x] Initial Python workflow/version/notes regression suite: 36 tests passed.
- [x] Initial isolated integration: 119 focused tests, 618 total tests across
  13 modules / 77 classes, including 211 AI tests; zero failures/errors/skips.
- [x] Initial regenerated lint: all 13 Android modules, 0 Error/Fatal, 835
  warnings and 4 information; compiled production and test sources.
- [x] Final main-worktree focused gate: 121 tests across 13 classes, including
  8 integration contract cases; zero failures/errors/skips.
- [x] Final main-worktree gate: 633 JVM tests passed across 13 modules / 77
  classes, including 224 AI tests; all 13 lint reports have 0 Error/Fatal.
  The additional 13 AI tests belong to unrelated unstaged WIP.
- [x] Materialize staged tree `2d02a35d33a348cabf2fde85b8cc166e3c106ee8` in the
  existing validation directory through a separate temporary Git index;
  confirm tracked files match without changing root index or working files.
- [x] Exact staged product gate: 620 JVM tests passed across 13 modules / 77
  classes, including 211 AI tests and 8 integration contracts; all 13 lint
  reports have 0 Error/Fatal (835 warnings / 4 information).
- [x] Full-scope code review: the Page replay finding is resolved; no other
  parser, transport, queue, identity, cache, delivery, or metadata findings.
- [x] Record final verification in `research/staged-validation.md` and confirm
  the main index still matches the tested product tree after the gate.
- [x] After main advances with the reader fix, validate final product snapshot
  `151737f3029ceac5b869dd1bdf332ba844af49cb`: 620 tests passed, all 13 lint
  reports have 0 Error/Fatal. Only Trellis bookkeeping differs afterward.

## Commit and finish procedure

1. Inspect the final index and run `git diff --cached --check`; commit the
   real source-branch merge plus this task's artifacts on main, excluding all
   parallel unstaged changes.
2. Archive this direct-main task with `--skip-branch-validation` (no PR was
   requested or created), then record the work commit in the journal.
3. Confirm parallel working changes remain and no new stable tag/version/
   release-notes file exists; push main only without force or CI polling.

Archive status and the developer journal record the local finish-work result.
The final push result is reported after those bookkeeping commits are created.

## Merge result

Committed the real merge on main as
`2a2e479c771e7ef99459715bdfacdd7f1688b0ad`, with first parent
`2239e4d39c856f270bd3d2259c97cd94365f527c` and source parent
`f5bcec31202635a5992d4fbf81537c71c93a86d4`. The 18 product paths match the
validated snapshot; the work commit also records this task's nine artifacts.
Verified both branch histories and the independent reader commit are in main
ancestry. All six original shared/AI working-file hashes are preserved; the
three AI files and their separate task remain unstaged after the merge.

## Evidence and boundaries

See `research/implementation-verification.md` for implementation commands and
`research/check-verification.md` for the final independent review. Final
staged-snapshot evidence is recorded separately so root WIP is not confused
with released/committed scope. Production edits after transfer occur only in
main; the other directory is a validation copy.

The reviewer traced `onResume -> SHOW_READY_DATA -> showData(mThreadData)` into
repeated online subscription creation. Keeping idempotence in Page delivery
avoids changing the parallel rendering task's title/menu/topic-owner/anchor
behavior. No new abstraction or request endpoint was introduced by this fix.

No APK package task, ADB/device operation, or live NGA request is part of the
checks. Device tests were not run per project policy. Stable publication is
explicitly deferred: no new tag, version change, or 6.0.0 notes were created.
