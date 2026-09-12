# IP query merge implementation and validation

Date: 2026-09-12. The final correction and checks below ran in the authoritative
`/home/toph/nga-just-works` worktree on main, based on `8a6b3249` with the pending
`f5bcec31` merge. The working files include independent unstaged AI and article
flicker changes. This worker preserved them and did not stage or commit files.
The coordinator separately validates the exact staged product tree before commit.

Stable release publication remains deferred. This worker created no release
notes, version edits, tags, or publication actions.

## Reviewer-found replay gap and final correction

The initial Fragment restoration reconnected the intended page delivery and
view-lifecycle calls. Review then found that
`ArticleListPresenter.onResume -> requestForegroundLoad(false) -> SHOW_READY_DATA
-> showData(mThreadData)` also replays the same response through Fragment.setData.
Previously every Page.deliver call closed/recreated its subscription. This could
promote a recreated view's cache-only consumer to online and restart lookups.
Simply suppressing delivery after a body rebind would instead lose the adapter's
location metadata.

The correction belongs to `AuthorLocationService.Page`, the subscription owner:

- Remember the last delivered ThreadData. After the closed check, replaying the
  same nonnull object only re-emits `updates.getValue()` if its generation is
  current, then returns. It does not change generation, close the subscription,
  settle a session, or call the repository. Pending first delivery and the
  original online/cache-only intent survive.
- Read the current LiveData value, with its existing invalidatable snapshot,
  rather than retaining a second snapshot. Account invalidation remains
  authoritative and cannot recover stale location metadata.
- Fresh response objects still replace the consumer. Null uses the existing
  clear path and resets remembered data. Close releases the response reference.
- A new Page first receiving retained data with online=false stays cache-only
  when the presenter subsequently replays that response with online=true.

This follow-up changed only `AuthorLocationService.java`,
`ArticleAuthorLocationContractTest.kt`, and this evidence file. It did not edit
Fragment rendering, presenter logic, AI files, specifications, or the Git index.
The final integration is no longer described as a verbatim source-branch
restoration: it includes this additional Page replay correction. Parallel
Fragment owner/rendering changes remain outside this worker's ownership.

Two added source-contract tests trace Presenter READY replay through Fragment to
Page, pin replay/side-effect ordering and current-generation LiveData use, and
check null/close cleanup. Existing contracts also verify retained-view
cache-only delivery and retention of responses after view destruction. Executing
repository tests continue to cover saved-page misses/expiry, cache-only delivery
after a rate-limit pause expires, account invalidation, and immutable snapshots.

## Final main-worktree checks

Focused IP, UI, refresh, prefetch, and cache regressions:

```bash
./gradlew :nga_phone_base_3.0:testDebugUnitTest --tests 'sp.phone.profile.*' --tests 'sp.phone.ui.fragment.ArticleAuthorLocationContractTest' --tests 'sp.phone.ui.fragment.ArticleReaderUiContractTest' --tests 'sp.phone.ui.fragment.TopicPagePrefetchContractTest' --tests 'sp.phone.ui.fragment.ArticlePageRefreshContractTest' --tests 'sp.phone.mvp.presenter.ArticlePage*Test' --tests 'sp.phone.mvp.presenter.ArticleOwnedPageCacheTest' --tests 'sp.phone.mvp.viewmodel.ArticlePagePrefetchPlannerTest' --max-workers=2 --console=plain > /tmp/nga-ip-merge-main-focused-tests.log 2>&1
```

Passed in 13 seconds: 121 tests in 13 classes, zero failures/errors/skips. The
profile package contributes 63 tests; the integration contract class now has 8.

Repository-wide Debug JVM gate:

```bash
./gradlew testDebugUnitTest --continue --max-workers=2 --console=plain > /tmp/nga-ip-merge-main-all-unit-tests.log 2>&1
```

Passed in 39 seconds: 633 tests in 77 classes across all 13 modules, zero
failures/errors/skips. The app has 548 tests, including 224 in `sp.phone.ai`.
The AI count includes 13 existing unstaged WIP tests beyond the earlier isolated
checkout. Parsed all generated Debug JUnit XML files and required nonzero test
counts for every module with test sources. Gradle reused unchanged library
results; the app test task executed in both invocations.

Refreshed app lint, reusing reports for unchanged library modules:

```bash
./gradlew :nga_phone_base_3.0:lintDebug --rerun-tasks --max-workers=2 --console=plain > /tmp/nga-ip-merge-main-app-lint.log 2>&1
```

Passed in 44 seconds with all 500 tasks executed. Parsed every
`build/reports/lint-results-debug.xml` for the 13 settings.gradle includes and
required each report to exist. All reports have zero Error/Fatal issues.

| Module | JVM tests | Lint Error/Fatal | Warnings | Information |
| --- | ---: | --- | ---: | ---: |
| lib_bu_statistics | 1 | 0 / 0 | 6 | 0 |
| nga_phone_base_3.0 | 548 | 0 / 0 | 729 | 1 |
| lib_core | 5 | 0 / 0 | 8 | 1 |
| lib_base_logger | 1 | 0 / 0 | 8 | 0 |
| lib_base_common | 62 | 0 / 0 | 33 | 0 |
| lib_core_data | 1 | 0 / 0 | 1 | 0 |
| lib_bu_message | 1 | 0 / 0 | 11 | 2 |
| lib_base_network | 1 | 0 / 0 | 7 | 0 |
| lib_base_service_api | 1 | 0 / 0 | 2 | 0 |
| lib_bu_account | 1 | 0 / 0 | 6 | 0 |
| lib_base_ui_compose | 9 | 0 / 0 | 12 | 0 |
| lib_base_ui | 1 | 0 / 0 | 8 | 0 |
| lib_module_debug | 1 | 0 / 0 | 4 | 0 |
| **Total** | **633** | **0 / 0** | **835** | **4** |

The test/lint graphs compiled affected production and unit-test sources.
`git diff --check` passed. No suppression or lint baseline changed.

## Earlier preliminary validation

Before the reviewer found the Page replay gap, the initial integration in
`/home/toph/nga-just-works-release-6.0.0` passed 119 focused tests (27 seconds),
618 repository tests (33 seconds), and full regenerated lint across 13 modules
(1 minute 20 seconds; zero Error/Fatal, 835 warnings and 4 information findings).
Those results predate the Page correction and do not validate its final behavior.
They used the same focused selection and full unit command above, plus:

```bash
./gradlew lintDebug --continue --rerun-tasks --max-workers=2 --console=plain > /tmp/nga-ip-merge-lint.log 2>&1
```

The earlier test logs remain at `/tmp/nga-ip-merge-focused-tests.log` and
`/tmp/nga-ip-merge-all-unit-tests.log`. Use the main-worktree results above for
this worker's final state and the coordinator's separate staged-tree evidence
for the actual commit scope.

## Limits and handoff

No code or validation failure remains in the assigned scope. Source contracts
are wiring checks, not Android runtime execution. Device tests were not run per
project policy. No APK assemble/package task, ADB operation, live NGA request,
or CI polling was performed. The coordinator owns exact staged-tree review,
commits, Trellis finish-work, and pushing main.
