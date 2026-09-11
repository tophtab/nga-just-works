# Implementation progress

Final result: implementation and independent review are complete; app/common/core
278 tests, debug build and all 13 lint XML checks passed. See
[delivery.md](delivery.md) and [independent-check.md](independent-check.md).
The checkpoints below are the implementer's historical progress log. The task
has completed both approved work commits and is being archived.

## Change boundary (before product edits)

- Workspace: `/home/toph/nga-just-works-compat-mode`, `feature/thread-detail-compat-mode`, baseline `5bb92cf033aa32d749d10e1a497bc05173cd2955` (includes `6203dad5`). No other worktree is an edit target.
- Gap: format failures cannot use upstream's implemented App reader; the legacy reader/cache assumes every page is an unfiltered 20-floor page. The approved feature needs one query/source/page context across transport, rendering, navigation, lifecycle and persistence.
- Owners: app-local Kotlin adapters for query/paging, source generations, App DTO projection, operation-specific byte transport and versioned cache; retain existing Java/Rx presenter/model/Activity/Fragment and native renderer boundaries.
- Expected edits: ArticleListModel/Contract/Presenter and page state for bounded same-account foreground fallback; ArticleConvertFactory/ThreadData/ThreadRowInfo/FunctionUtils for shared rendering and explicit row metadata; Activity, pager, list/scoped fragments, goto dialog and adapters for real page coordinates/anchors; ArticlePageCache, cache Activity and topic list/cache consumers for owner/layout save/list/open/read/delete; laboratory resource and cache zip scope copy; meaningful offline tests.
- Additional traced fixed-20 constraint: ArticleListAdapter's LocalWebView array has 20 elements. Make its capacity follow loaded rows while preserving per-page retention.
- Local refactor checks: inject rendering/blacklist seams to assert ordinary parser preparation order and WP/source behavior. Keep all attachment/comment/blacklist inputs before HTML and compare existing renderer/prefix regressions.
- Excluded: global JSON/SDK/network/login rewrites, independent board icons/video styling, invented sidecar protocols, commits/push/merge, NGA/account-storage probes, devices/signing/release work.
- Loaded: original PRD/design/implementation plan, real manifest, saved hook output, long Android quality/UI/query research in segments, content/network/source evidence, shared thinking guides and before-dev skill. Final design supersedes historical research restrictions.

## Validation

Product/test implementation is frozen for independent review as of 2026-09-12. No product changes preceded the boundary above.

Completed integration:

- Scoped ordinary/App bytes and typed failures, same-account one-shot fallback, one optional anchor alignment, reader-owned source/page generation and one-page handoff.
- Full/PID/author query routing, requested/actual page separation, variable/unknown layout navigation, actual PID/floor anchors, resolved show-all and ordinary quote hints.
- Shared native rendering with preserved source/raw, normal WP/attachment/comment preparation, explicit row identity/comment/score metadata, variable WebView retention and teardown.
- Default-off laboratory setting independent of the existing browser setting.
- Owned/versioned/layout-aware cache across prepare/save/list/open/replay/delete, unchanged legacy raw dispatch, numeric sparse tabs, bounded legacy-only ZIP import/export.
- Regression tests for parsing, state/anchors/budgets, account behavior, cache/ZIP/isolation, UI source boundaries and the normal renderer seams. Main session contributed the fake Call transport suite.

Validated before the last small handoff/ZIP/cleanup refinements:

- App/common unit gate: app 198 + common 62 tests, zero failure/error/skipped.
- Debug assemble + app lint: passed; app lint XML had zero Error/Fatal.

Validated on the frozen implementation:

- `./gradlew lintDebug --continue --rerun-tasks --console=plain`: passed, 536 executed tasks in 45 seconds.
- All 13 Android modules from settings.gradle have lint XML, each with zero Error/Fatal. See lint-xml-summary.md.

Final implementer verification completed on the frozen implementation:

- `./gradlew :nga_phone_base_3.0:testDebugUnitTest :lib_base_common:testDebugUnitTest :nga_phone_base_3.0:assembleDebug :nga_phone_base_3.0:lintDebug --console=plain`: passed in 12 seconds. The final invocation reused the unchanged unit-test outputs; the broad diagnostic had just executed the app suite successfully.
- Final XML: app 199 tests / 31 suites + common 62 tests / 4 suites = 261 tests, zero failure/error/skipped. The added eighth cache-store test accounts for the increase from the earlier 260-test gate.
- `./gradlew testDebugUnitTest --continue --console=plain`: diagnostic failed at exactly two known, unmodified fixture compilation tasks: `lib_bu_statistics:compileDebugUnitTestJavaWithJavac` (missing JUnit) and `lib_module_debug:kaptDebugUnitTestKotlin` (unresolved annotation stub). The executed/reused suites contain 280 passing tests across 11 modules; those two modules produced no test XML. `lib_base_ui` and `lib_core` passed in this run, so the historical four-failure list is not this run's result.
- `git diff --check`: passed. The two failing modules have no diff from baseline `5bb92cf033aa32d749d10e1a497bc05173cd2955`; the exact failure classes are documented in `android-quality-guidelines.md`.
- `check-results.md` records commands, log paths, every app/common suite count, all-module lint evidence and broad-diagnostic evidence. `implementation-results.md` records behavior, file ownership and limitations.

Gradle ownership was explicitly released to the main session after verification. Independent check owns subsequent product/test fixes and any necessary reruns; full-diff review remains pending. Main session owns specs and task metadata. No real NGA traffic, account-storage inspection, device, release/preview signing, commit or publishing action was performed.
