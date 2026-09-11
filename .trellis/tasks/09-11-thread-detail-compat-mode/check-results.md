# Implementer verification snapshot

Final reviewed delivery: [delivery.md](delivery.md). Later review fixes and the
278-test gate are recorded in [independent-check.md](independent-check.md).
The counts below intentionally preserve the earlier implementer snapshot.

Date: 2026-09-12. Workspace: `/home/toph/nga-just-works-compat-mode`, branch `feature/thread-detail-compat-mode`, baseline `5bb92cf033aa32d749d10e1a497bc05173cd2955` (includes `6203dad5`). This report covers the frozen implementer handoff, before any independent-review fixes. It does not replace the independent full-diff review or mark the task complete.

## Commands and outcomes

All commands ran from the workspace above. Log paths below are relative to this task directory.

| Command | Result | Log |
| --- | --- | --- |
| `./gradlew lintDebug --continue --rerun-tasks --console=plain` | Passed on frozen implementation in 45 seconds, 536 tasks executed; all 13 Android modules have lint XML and 0 Error/Fatal | `required-all-lint.log` |
| `./gradlew testDebugUnitTest --continue --console=plain` | Failed in 28 seconds at two known, unchanged fixture compilation tasks; 11 modules' available suites report 280 passing tests | `required-broad-debug-tests.log` |
| `./gradlew :nga_phone_base_3.0:testDebugUnitTest :lib_base_common:testDebugUnitTest :nga_phone_base_3.0:assembleDebug :nga_phone_base_3.0:lintDebug --console=plain` | Passed in 12 seconds, 556 actionable tasks (4 executed, 552 up-to-date); debug APK refreshed, app/common unit results and lint correctly reused for unchanged source | `final-verification.log` |
| `git diff --check` | Passed after final Gradle verification | Tool output; no diagnostics |
| `git diff 5bb92cf033aa32d749d10e1a497bc05173cd2955 -- lib_bu_statistics lib_module_debug` | Empty; neither failing module was changed | Tool output; no diff |

Earlier integration evidence remains in `required-unit-tests.log` (app 198 + common 62) and `required-debug-build.log` (debug build and app lint passed). The final results below supersede that earlier test count.

The broad diagnostic executed `:nga_phone_base_3.0:testDebugUnitTest` successfully (`required-broad-debug-tests.log:423`) after the last cache replay test was added. The final invocation reported the same task `UP-TO-DATE` (`final-verification.log:655`); it did not re-execute 199 cases. Common's unchanged 62-test suite was also up-to-date (`final-verification.log:410`). XML was read after the final command, not inferred from the Gradle exit status.

## Unit XML counts

Reports: `<module>/build/test-results/testDebugUnitTest/TEST-*.xml`. App has 31 suites / 199 tests; common has 4 suites / 62 tests. All 35 suites have **0 failures, 0 errors, 0 skipped**. The previous 260-test total became 261 because `ArticleCacheStoreTest` now has eight cases, including actual App/normal parser → store → replay and legacy dispatch coverage.

| App suite | Tests |
| --- | ---: |
| `gov.anzong.androidnga.AboutActivityContractTest` | 4 |
| `gov.anzong.androidnga.ReleaseWorkflowContractTest` | 9 |
| `gov.anzong.androidnga.SwipeBackContractTest` | 5 |
| `gov.anzong.androidnga.SystemThemeContractTest` | 4 |
| `gov.anzong.androidnga.activity.compose.board.ForumBoardBookmarkPersistenceTest` | 10 |
| `gov.anzong.androidnga.activity.compose.board.HomeBoardOrderContractTest` | 6 |
| `gov.anzong.androidnga.activity.compose.board.HomeBoardOrderTest` | 6 |
| `gov.anzong.androidnga.activity.compose.drawer.HomeNavigationDrawerContractTest` | 6 |
| `gov.anzong.androidnga.activity.compose.drawer.NavigationDrawerContentContractTest` | 2 |
| `gov.anzong.androidnga.activity.compose.drawer.NavigationDrawerGestureTest` | 15 |
| `gov.anzong.androidnga.activity.compose.filter.FilterWordModelTest` | 2 |
| `gov.anzong.androidnga.ui.widget.TopicListTitleRefreshContractTest` | 8 |
| `sp.phone.common.DefaultSettingsContractTest` | 9 |
| `sp.phone.common.VersionUpgradeHelperMigrationTest` | 4 |
| `sp.phone.mvp.model.convert.ArticleConvertFactoryTest` | 4 |
| `sp.phone.mvp.model.convert.PageAttachmentPrefixFlowTest` | 3 |
| `sp.phone.mvp.model.thread.AppArticleParserTest` | 8 |
| `sp.phone.mvp.model.thread.ArticleByteClientTest` | 11 |
| `sp.phone.mvp.model.thread.ArticleCacheStoreTest` | 8 |
| `sp.phone.mvp.model.thread.ArticleReaderSessionTest` | 7 |
| `sp.phone.mvp.model.thread.NormalArticleParserTest` | 3 |
| `sp.phone.mvp.presenter.ArticleOwnedPageCacheTest` | 2 |
| `sp.phone.mvp.presenter.ArticlePageCacheTest` | 14 |
| `sp.phone.mvp.presenter.ArticlePageRequestStateTest` | 7 |
| `sp.phone.mvp.viewmodel.ArticlePagePrefetchPlannerTest` | 5 |
| `sp.phone.ui.fragment.ArticlePageRefreshContractTest` | 7 |
| `sp.phone.ui.fragment.ArticleReaderUiContractTest` | 4 |
| `sp.phone.ui.fragment.TopicPagePrefetchContractTest` | 6 |
| `sp.phone.util.FunctionUtilsAvatarTest` | 3 |
| `sp.phone.view.webview.ArticleSelectionActionModeContractTest` | 10 |
| `sp.phone.view.webview.ArticleSelectionTextTest` | 7 |
| **App total** | **199** |

| Common suite | Tests |
| --- | ---: |
| `gov.anzong.androidnga.base.ExampleUnitTest` | 1 |
| `gov.anzong.androidnga.common.util.EmoticonOrderResolverTest` | 23 |
| `gov.anzong.androidnga.common.util.EmoticonUtilsContractTest` | 8 |
| `gov.anzong.androidnga.common.util.NgaImageHostContractTest` | 30 |
| **Common total** | **62** |

The broad diagnostic also ran these suites. Each row has 0 failures/errors/skips. Together with app/common, the available XML contains 46 suites / 280 tests across 11 modules. The two compilation-failing modules have no unit XML, so they are not zero-test passes.

| Module | Suite | Tests |
| --- | --- | ---: |
| `lib_core` | `gov.anzong.androidnga.core.ExampleUnitTest` | 3 |
| `lib_base_logger` | `gov.anzong.androidnga.base.logger.ExampleUnitTest` | 1 |
| `lib_core_data` | `gov.anzong.androidnga.core.remote.ExampleUnitTest` | 1 |
| `lib_bu_message` | `com.justwen.androidnga.module.message.ExampleUnitTest` | 1 |
| `lib_base_network` | `com.justwen.androidnga.base.network.ExampleUnitTest` | 1 |
| `lib_base_service_api` | `com.example.lib_module_account_api.ExampleUnitTest` | 1 |
| `lib_bu_account` | `com.justwen.androidnga.module.account.ExampleUnitTest` | 1 |
| `lib_base_ui_compose` | `com.justwen.androidnga.ui.compose.ExampleUnitTest` | 1 |
| `lib_base_ui_compose` | `com.justwen.androidnga.ui.compose.widget.ScaffoldAppSystemBarContractTest` | 1 |
| `lib_base_ui_compose` | `com.justwen.androidnga.ui.compose.widget.TabLayoutWithPagerContractTest` | 7 |
| `lib_base_ui` | `com.justwen.androidnga.ui.ExampleUnitTest` | 1 |
| **Additional total** | | **19** |

## Broad diagnostic failure evidence

Exactly two tasks failed. Both match the documented diagnostic baseline in `.trellis/spec/backend/android-quality-guidelines.md:207–222` and are in modules unchanged from the approved baseline. No dependency was added or test disabled to hide the failures.

1. `:lib_bu_statistics:compileDebugUnitTestJavaWithJavac` — `required-broad-debug-tests.log:297–317`. `lib_bu_statistics/src/test/java/com/justwen/androidnga/cloud/ExampleUnitTest.java` imports `org.junit.Assert` and `org.junit.Test`; the compiler reports `package org.junit does not exist`, the unresolved `@Test`, and unresolved `assertEquals`. The module build file has no unit-test JUnit dependency. This corresponds to the missing-JUnit fixture documented at quality-guideline lines 214–215.
2. `:lib_module_debug:kaptDebugUnitTestKotlin` — `required-broad-debug-tests.log:349–352`. The generated `ExampleUnitTest.java` stub reports `incompatible types: NonExistentClass cannot be converted to Annotation` at `@error.NonExistentClass()`. This corresponds to the unresolved example-test KAPT annotation fixture at quality-guideline line 218.

The final failure summary names those same two tasks at log lines 425–452. The historical notes also mention `lib_core` and `lib_base_ui`; **those failures did not occur in this run**. Their test tasks executed (`required-broad-debug-tests.log:378`, `:397`), and their XML has respectively 3 and 1 passing cases. The broad diagnostic remains a failed command even though it produced no failing executed test cases.

## Lint evidence

Parsed `settings.gradle` and checked `<module>/build/reports/lint-results-debug.xml` for every included Android module after the frozen all-module rerun and final app refresh. All 13 reports exist; every report has 0 Error/Fatal. Warning counts are preserved in [lint-xml-summary.md](lint-xml-summary.md); warnings were not suppressed to make this gate pass.

## Coverage and remaining review

The local tests cover the consumed App projection and typed error classification; account/source/generation and one-shot fallback/alignment budgets; actual anchors and variable/unknown layouts; fake byte responses/cancellation/limits without sockets; native normal-render preparation; owner/layout cache store, replay and legacy ZIP path isolation; and the Activity/Fragment/adapter source boundaries. Existing renderer, attachment prefix, avatar, refresh, request-state and prefetch tests also pass. The main session contributed all 11 `ArticleByteClientTest` cases.

Independent full-diff review is still pending and owns any subsequent product/test fixes and their reruns. UI source assertions and pure/fake tests do not validate real Android lifecycle or WebView behavior. Real NGA traffic, account-storage inspection, devices/ADB/instrumentation, release/preview signing, commits, push and merge were outside the authorized scope and were not performed. Device checks are not a default handoff requirement.

Gradle execution ownership was explicitly released to the main session after the final command and XML inspection. The implementer will not run further Gradle tasks or modify product/tests unless the main session assigns new work.
