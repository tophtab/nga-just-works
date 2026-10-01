# Test simplification audit

## Scope

Remove generated examples, low-value visual/text snapshots, and unused test dependencies. Keep all executable behavior regressions and critical integration/safety assertions. No production code, release workflow, test exclusions, device operations or live API requests changed.

## Deleted files

| File | Reason |
| --- | --- |
| `lib_base_common/src/test/java/gov/anzong/androidnga/base/ExampleUnitTest.java` | Arithmetic template only. |
| `lib_base_logger/src/androidTest/java/gov/anzong/androidnga/base/logger/ExampleInstrumentedTest.java` | Generated package-name assertion; no application behavior. |
| `lib_base_logger/src/test/java/gov/anzong/androidnga/base/logger/ExampleUnitTest.java` | Arithmetic template only. |
| `lib_base_network/src/androidTest/java/com/justwen/androidnga/base/network/ExampleInstrumentedTest.java` | Generated package-name assertion; no application behavior. |
| `lib_base_network/src/test/java/com/justwen/androidnga/base/network/ExampleUnitTest.java` | Arithmetic template only. |
| `lib_base_service_api/src/androidTest/java/com/example/lib_module_account_api/ExampleInstrumentedTest.java` | Generated package-name assertion; no application behavior. |
| `lib_base_service_api/src/test/java/com/example/lib_module_account_api/ExampleUnitTest.java` | Arithmetic template only. |
| `lib_base_ui/src/androidTest/java/com/justwen/androidnga/ui/ExampleInstrumentedTest.java` | Generated package-name assertion; no application behavior. |
| `lib_base_ui/src/test/java/com/justwen/androidnga/ui/ExampleUnitTest.java` | Arithmetic template only. |
| `lib_base_ui_compose/src/androidTest/java/com/justwen/androidnga/ui/compose/ExampleInstrumentedTest.kt` | Generated package-name assertion; no application behavior. |
| `lib_base_ui_compose/src/test/java/com/justwen/androidnga/ui/compose/ExampleUnitTest.kt` | Arithmetic template only. |
| `lib_bu_account/src/androidTest/java/com/justwen/androidnga/module/account/ExampleInstrumentedTest.java` | Generated package-name assertion; no application behavior. |
| `lib_bu_account/src/test/java/com/justwen/androidnga/module/account/ExampleUnitTest.java` | Arithmetic template only. |
| `lib_bu_message/src/androidTest/java/com/justwen/androidnga/module/message/ExampleInstrumentedTest.java` | Generated package-name assertion; no application behavior. |
| `lib_bu_message/src/test/java/com/justwen/androidnga/module/message/ExampleUnitTest.java` | Arithmetic template only. |
| `lib_bu_statistics/src/test/java/com/justwen/androidnga/cloud/ExampleUnitTest.java` | Arithmetic template only. |
| `lib_core/src/test/java/gov/anzong/androidnga/core/ExampleUnitTest.java` | Arithmetic template and two print-only decoder/encoding probes; no output assertions. Dedicated HtmlCommentBuilderTest and ForumEmoticonDecoderTest remain. |
| `lib_core_data/src/test/java/gov/anzong/androidnga/core/remote/ExampleUnitTest.java` | Arithmetic template only. |
| `lib_module_debug/src/androidTest/java/com/justwen/androidnga/module/debug/ExampleInstrumentedTest.kt` | Generated package-name assertion; no application behavior. |
| `lib_module_debug/src/test/java/com/justwen/androidnga/module/debug/ExampleUnitTest.kt` | Arithmetic template only. |
| `lib_base_ui_compose/src/test/java/com/justwen/androidnga/ui/compose/widget/ScaffoldAppSystemBarContractTest.kt` | Two source substrings for navigation-bar theme color/lightness; no lifecycle or inset behavior exercised. |
| `nga_phone_base_3.0/src/test/java/gov/anzong/androidnga/AboutActivityContractTest.kt` | Source snapshots of credits, URLs, removed QQ labels and padding expressions; no page/inset execution. |
| `nga_phone_base_3.0/src/test/java/gov/anzong/androidnga/SystemThemeContractTest.kt` | Theme imports, resource names/colors and padding-expression snapshots; no rendering or inset execution. |
| `nga_phone_base_3.0/src/test/java/gov/anzong/androidnga/activity/compose/drawer/NavigationDrawerContentContractTest.kt` | Literal bookmark labels and source ordering of spacer/about item; no drawer behavior. |

## Trimmed methods

| File / method | Reason |
| --- | --- |
| `lib_base_ui_compose/src/test/java/com/justwen/androidnga/ui/compose/widget/TabLayoutWithPagerContractTest.kt` / `pagerExtensionPointsRemainOptional` | Signature/default-value source snapshot; callers compile against actual signature. |
| `lib_base_ui_compose/src/test/java/com/justwen/androidnga/ui/compose/widget/TabLayoutWithPagerContractTest.kt` / `callerModifierIsAttachedOnlyToHorizontalPager` | Counts assignment spelling/source position without exercising modifier behavior. |
| `nga_phone_base_3.0/src/test/java/gov/anzong/androidnga/activity/compose/board/HomeBoardOrderContractTest.kt` / `liveDrawerContractNamesTheNewAdjacentBoard` | Tests Markdown wording, not product behavior; bundled-board JSON parsing and order/persistence tests remain. |
| `nga_phone_base_3.0/src/test/java/gov/anzong/androidnga/activity/compose/drawer/HomeNavigationDrawerContractTest.kt` / `homeContentStaysStationaryWhileSheetAndScrimTrackProgress` | Source snapshot of Box, offset, alpha, and width; executable physical-offset and drawer-state tests remain. Rendered scrim/layout appearance was never exercised. |
| `nga_phone_base_3.0/src/test/java/gov/anzong/androidnga/activity/compose/drawer/HomeNavigationDrawerContractTest.kt` / `implementationAvoidsObsoleteAndInternalGestureApis` | Broad recursive Java/Kotlin source scan forbidding identifiers anywhere (including unrelated code); behavior tests and focused wiring assertions remain. |

## Dependencies

All Java/Kotlin test sources were inventoried before pruning. Retain module-local JUnit in the four modules that still own JVM tests; retain application AndroidX JUnit/runner for encrypted AI configuration instrumentation. Runner declarations remain harmless configuration and are unchanged. Remove nine unused JVM JUnit declarations and seven Android test dependencies from modules without device test sources (including already-unused common/core declarations).

| Build file | Removed test-only dependencies |
| --- | --- |
| `lib_base_common/build.gradle` | `androidTestImplementation 'androidx.test.ext:junit:1.1.1'`; `androidTestImplementation 'androidx.test.espresso:espresso-core:3.2.0'` |
| `lib_base_logger/build.gradle` | `testImplementation 'junit:junit:4.+'`; `androidTestImplementation 'androidx.test.ext:junit:1.1.5'`; `androidTestImplementation 'androidx.test.espresso:espresso-core:3.5.1'` |
| `lib_base_network/build.gradle` | `testImplementation 'junit:junit:4.13.2'` |
| `lib_base_service_api/build.gradle` | `testImplementation 'junit:junit:4.13.2'` |
| `lib_base_ui/build.gradle` | `testImplementation 'junit:junit:4.13.2'` |
| `lib_base_ui_compose/build.gradle` | `androidTestImplementation 'androidx.test.ext:junit:1.2.1'`; `androidTestImplementation 'androidx.test.espresso:espresso-core:3.6.1'` |
| `lib_bu_account/build.gradle` | `testImplementation 'junit:junit:4.13.2'` |
| `lib_bu_message/build.gradle` | `testImplementation 'junit:junit:4.13.2'` |
| `lib_bu_statistics/build.gradle` | `testImplementation 'junit:junit:4.13.2'` |
| `lib_core/build.gradle` | `androidTestImplementation 'androidx.test.ext:junit:1.1.1'` |
| `lib_core_data/build.gradle` | `testImplementation 'junit:junit:4.13.2'` |
| `lib_module_debug/build.gradle` | `testImplementation 'junit:junit:4.13.2'` |

## Retained coverage

- Parsing, article cache/request/session state, network fake-server and session-safety regressions remain unchanged.
- AI encrypted configuration instrumented tests remain (3 methods); no device checks run per project policy.
- ReleaseWorkflowContractTest and all Python release/version/notes tests remain unchanged.
- HomeBoardOrderContractTest still parses bundled board JSON and checks persistence/reorder wiring; HomeBoardOrderTest and ForumBoardBookmarkPersistenceTest retain executable order, stale-write rollback and file recovery coverage.
- TabLayoutWithPagerContractTest retains executable consecutive stable-key movement and focused lifecycle/drag/accessibility wiring.
- NavigationDrawerGestureTest retains gesture, LTR/RTL physical offsets, settled-state, resize and cancellation behavior; HomeNavigationDrawerContractTest retains drag transactions, close/accessibility and reorder gates.
- Article/AI/link/editor/network ContractTest files were not blanket-deleted.

## Validation

| Check | Result |
| --- | --- |
| Baseline `./gradlew testDebugUnitTest --continue --console=plain` | Success; 32.18 s wall time; all 281 actionable tasks UP-TO-DATE. Existing XML: 731 tests, zero failures/errors/skips. This is cached validation, not a measured test execution. |
| Final `./gradlew testDebugUnitTest lintDebug --continue --console=plain` | Success; 109.65 s wall time; 603 actionable tasks, 148 executed / 455 UP-TO-DATE. 701 tests, zero failures/errors/skips across four remaining test modules. |
| Lint XML inspected after final gate | All 13 Android modules have a report; zero Error / Fatal. |
| `python3 -m unittest discover -s scripts` | 37 tests passed; unittest elapsed 61.130 s; ran in parallel with Gradle. |
| `git diff --check` | Passed. |
| Device tests | Not run per project policy. |

JVM test files: 89 → 73; methods: 731 → 701 (30 removed). Android test files: 9 → 1; methods: 11 → 3 (8 generated examples removed). JVM-owning modules: 13 → 4. The remaining XML counts are common=62, compose=5, core=5, application=629; modules without sources have no leftover XML reports.

The before/after durations are **not comparable**: the baseline was fully cached and unit-only, while the final gate recompiled changed test/dependency inputs, executed affected tests and included repository-wide lint. No percentage speedup is claimed. The concrete reduction is nine empty test suites, 38 unnecessary test methods in total, 16 unused dependency declarations, and one broad recursive source scan. The main session also removes duplicate/forced Gradle verification from the normal documented gate.

Raw command output and counts are in `baseline-gradle.log`, `baseline-time.txt`, `baseline-counts.json`, `after-gradle.log`, `after-time.txt`, `after-counts.json`, and `python-tests.log`.

## Second pass: source snapshots

The first pass retained too many assertions that merely searched production source for names. This pass removes another **82 JVM methods** (6 entire files, selective removal in 12 mixed files), without grouping methods to disguise the count. Actual method count after both workers finish is recorded separately. No Gradle command was run by this implementer during the second pass; the coordinated final gate follows both workers.

The first-pass retained-coverage bullets above describe that checkpoint. The table below supersedes them for the final suite. UI gesture/lifecycle/rendering source snapshots are no longer represented as executed behavior. No replacement device coverage is claimed.

| File | Removed | Retained / rationale |
| --- | --- |
| `nga_phone_base_3.0/src/test/java/gov/anzong/androidnga/SwipeBackContractTest.kt` | Entire file (5 methods) | Only source names/constants and implementation-order snapshots; does not execute navigation-mode detection or gestures. |
| `nga_phone_base_3.0/src/test/java/gov/anzong/androidnga/ui/widget/TopicListTitleRefreshContractTest.kt` | Entire file (8 methods) | Only source spelling of toolbar callbacks and refresh calls; does not invoke a click, loading guard or scroll. |
| `nga_phone_base_3.0/src/test/java/gov/anzong/androidnga/activity/compose/drawer/HomeNavigationDrawerContractTest.kt` | Entire file (4 methods) | Remaining source spelling assertions do not exercise gestures; executable NavigationDrawerGestureTest retained. |
| `nga_phone_base_3.0/src/test/java/sp/phone/ui/fragment/ArticleReaderUiContractTest.kt` | Entire file (8 methods) | Only source snippets; actual reader session, request state, navigation, cache and adapter body ownership tests remain. |
| `nga_phone_base_3.0/src/test/java/sp/phone/ui/fragment/TopicPagePrefetchContractTest.kt` | Entire file (7 methods) | Source callbacks/annotations/guards; executable prefetch planner and request state tests remain. |
| `nga_phone_base_3.0/src/test/java/sp/phone/view/editor/InlineMediaEditorWiringTest.java` | Entire file (2 methods) | Only spelling of editor/decorator calls; source parser, queue and revision behavior tests remain. No Android lifecycle execution was provided. |
| `lib_base_ui_compose/src/test/java/com/justwen/androidnga/ui/compose/widget/TabLayoutWithPagerContractTest.kt` | `edgeMovementAndTalkBackActionsCoverTheConfiguredRange`, `stableKeysDriveGestureOrderAndLogicalSelection`, `shortClickRemainsPagerAnimationAndLongPressOwnsOnlyActiveDrag`, `pagerReportsSettledStateAndDisposalWithoutOwningDrawerGesture` | `renderedSlotsDriveConsecutiveMovesWithoutRecomposition` |
| `nga_phone_base_3.0/src/test/java/gov/anzong/androidnga/activity/compose/board/HomeBoardOrderContractTest.kt` | `favoriteAndTabReordersShareOnlyThePagerGate`, `homeWiresOnlyNonBookmarkTabsIntoTheOrderTransaction`, `modelKeepsBookmarkFixedAndPersistsOnlyTheDisplayOverlay`, `localBoardCacheVersionIsBumpedForTheNewDefault` | `bundledBoardListUsesTheNewCompleteDefaultOrder` |
| `nga_phone_base_3.0/src/test/java/sp/phone/param/ArticleLinkEntryContractTest.kt` | `newIntentRetiresOldObserversBeforePublishingEvenForSameQuery` | `schemeHasItsOwnBrowsableFilter` |
| `nga_phone_base_3.0/src/test/java/sp/phone/mvp/model/convert/PageAttachmentPrefixFlowTest.java` | `allCoreConsumersReadTheHtmlDataPagePrefix` | `attachmentBuilderAndImageListUseOnePagePrefix`, `retiredPagePrefixFeedsBodyAndAttachmentConsumers` |
| `nga_phone_base_3.0/src/test/java/sp/phone/mvp/model/thread/ReplySearchNavigationTest.kt` | `searchClickUsesTheTestedNavigationBuilder` | `replyToAnotherAuthorsTopicUsesReplyIdentityAndRemainsNativeReadable`, `ownTopicAndNonReplyNavigationPreserveTheirQueryKinds`, `absentOrUnparseableReplyAuthorNeverBecomesTheTopicAuthor` |
| `nga_phone_base_3.0/src/test/java/sp/phone/ui/fragment/ArticleAuthorLocationContractTest.kt` | `supplementalTransportDoesNotReuseTheUiTaskOrItsLoggingSideEffects`, `metadataPayloadNeverEntersBodyBindingAndChecksGenerationAuthorAndRecycledHolder`, `normalAndPrefetchedCompletionsDeliverToOffscreenPages`, `wholeActivityForegroundOwnsAllPagesWithoutFragmentVisibilityGates`, `viewLifecycleClosesTheTestedControllerAndReplaysCurrentOutputAfterSessionCheck`, `readyResumeReplaysRestoreCurrentMetadataWithoutReplacingThePageSubscription`, `successfulPageDeliveryEnrichesIndependentlyAndRetainedViewRebindingIsCacheOnly` | `staleReaderDataCannotBeRetainedRenderedOrUsedForAuthorRequests`, `accountSignalsInvalidateBeforeDeferredCaptureAndUseSettledListIndex` |
| `nga_phone_base_3.0/src/test/java/sp/phone/view/webview/ArticleSelectionActionModeContractTest.kt` | `theDeadTextViewTakeoverIsGone`, `selectionMenuIdsAreDeclaredAndNotBorrowedFromTheTopicListMenu`, `unknownItemsFallBackToTheWrappedCallback`, `everyActionGuardsItsFailureModes`, `prepareAlwaysRebuildsAndReportsTheMenuAsUpdated`, `menuIsClearedAndRebuiltAsCopySelectAllSearchInThatOrder`, `callbackIsACallback2ThatForwardsTheContentRect`, `wrappingIsGuardedAgainstDoubleWrappingAndNullCallbacks`, `localWebViewOverridesBothStartActionModeOverloads` | `noProcessTextEntryPointIsReintroduced` |
| `nga_phone_base_3.0/src/test/java/sp/phone/ui/fragment/ArticlePageRefreshContractTest.kt` | `postFabLongPressRefreshesTheCurrentPageAndRepeatsWhileHeld`, `longPressRepeaterIsTheOnlyPressAndRepeatLoop`, `tabLongPressRepeatsOnlyWhileTheSelectedTabRemainsPressed`, `selectedPageLongPressRefreshesImmediatelyAndEveryFiveSeconds`, `onlyLiveArticlePagesReceiveReplyFabClearance`, `boardAndArticleFabsStayVisibleAndKeepTheirDirectActions` | `articleOverflowDoesNotExposeRefresh` |
| `nga_phone_base_3.0/src/test/java/sp/phone/ai/summary/AiSummaryUiContractTest.java` | `copyReadsTheCompleteAnswerProjectionIndependentOfReasoningVisibility`, `reasoningFoldChangesOnlyOnUserToggleOrRetryAndSurvivesProgressRendering`, `sharedDialogForwardsBothStreamingChannelsAndRendersErrorsSeparately`, `sharedDialogIsScrollableAndDismissalCancelsItsController`, `profileSourceUsesTheControllerConfigurationAndDefersSessionReads`, `profileUsesLoadedUidAndCancelsWhenThePageIsPaused`, `retainedArticlePagesCancelOnPauseAndDataReplacement`, `bothFloorMenusExposeTheSameActionAndProfileStartsHidden` | `layoutKeepsReasoningFoldedAndScrollableWithoutClippingOrLiveTokenAnnouncements` |
| `nga_phone_base_3.0/src/test/java/sp/phone/ui/fragment/AiSettingsContractTest.java` | `discoveryUsesTheDraftServiceAndDoesNotRewriteTheTextEditor`, `aiSettingsUsesTheExistingChildActivityRouteAndTitle` | `aiConfigurationLivesOnlyOnTheSecondLevelScreen`, `saveIsAnAccessibleToolbarActionUsingTheExistingIcon`, `apiKeyBypassesAutomaticPreferenceAndViewPersistence`, `profilePromptPreferenceHasNonpersistentStyleLabels` |
| `nga_phone_base_3.0/src/test/java/gov/anzong/androidnga/ReleaseWorkflowContractTest.kt` | `stableReleaseUsesValidatedVersionedNotesWhileDebugKeepsGeneratedNotes`, `workflowVerifiesUpgradeIdentityAndCleansOnlyItsPublishedChannel`, `branchesPublishNamedPreviewsAndTagsPublishStableRelease`, `workflowDerivesVersionCodeFromSemanticBaseAndPreviewCommitDistance`, `rootGradleAcceptsDebugDistributionNamesAndRejectsLegacyPreviewNames` | `previewBuildKeepsProductionIdentityAndIsDebuggableWithoutMinification`, `checkoutKeepsBranchHistoryAndUsesShallowTagsWithBloblessPartialClone`, `sharedSdkAndPublishedApkChecksStayPinnedToApi29And35`, `eachJobRunsExactlyOneGradleInvocationCarryingItsReleaseTasks` |

Additional trimming inside retained methods: AiSettings save/profile-prompt checks now inspect packaged XML only; removed their Java spelling assertions. ArticlePageRefresh checks only parsed overflow-menu XML. Removed source-reading fields, helpers and imports that those deletions made unused.

The remaining source-code readers are deliberate exceptions:

- `AiSettingsContractTest.apiKeyBypassesAutomaticPreferenceAndViewPersistence`: key leakage prevention across view saved-state/autofill/content capture/screenshot surfaces is Android-only and has no host behavioral substitute here.
- `ArticleAuthorLocationContractTest`: two account/generation/invalidation-order guards preserve unique Android session boundary evidence alongside executable repository/session tests.
- `ArticleSelectionActionModeContractTest`: one negative guard prevents implicit external article-text export; selection decoding behavior is still tested independently.
- `ReleaseWorkflowContractTest`: four checks preserve Gradle production identity/signing/debuggability, checkout depth, shared SDK inheritance and the single Gradle build invocation. Python tests execute actual identity, staging, publication, cleanup and notes Bash, so their duplicated Kotlin snapshots were deleted. SDK bootstrap package checks remain in Python.

Resource/file readers are not source snapshots: HomeBoardOrder parses shipped JSON; ArticleLinkEntry parses manifest intent filters; AiSummaryUi parses layout/accessibility/saveEnabled; DefaultSettings parses preferences; NgaImageHost validates dropdown data. AuthorLocationStore and ArticleCacheStore read files written by production persistence and remain unchanged. Actual attachment rendering, reply-navigation identity validation, stable tab movement, emoticon uniqueness, release Python fixtures and the other behavior suites remain.

Known coverage limits: source-only callback spelling checks for title refresh, swipe-back, pager wiring, long-press repeat, AI dialog lifecycle, renderer binding and editor Android restoration are removed. These tests never executed those platform behaviors; this change does not claim platform UI coverage.

Spec references for main-session alignment include frontend component guidelines (drawer/refresh/selection/prefetch/reader), loading-usage-tips (loading lifecycle source assertion), backend thread-page-prefetch, author-profile-location and ai-summary contracts.

## Final second-pass full gate

`./gradlew testDebugUnitTest lintDebug --continue --console=plain` passed in 61.28 s wall time (584 tasks, 21 executed / 563 up-to-date). All 618 JVM cases passed with zero failures/errors/skips; all 13 lint reports have zero Error/Fatal. The full per-suite snapshot is preserved in `second-pass-results.json` before targeted revalidation overwrites test XML.

Overall JVM reduction: 731 to 618 (113 methods removed); device examples remain 11 to 3. Full suite duration sum was 45.339 s in first-pass evidence and 23.379 s in this local second-pass run. This is test-execution timing from XML, not end-to-end build speed, and not a controlled repeated benchmark.

Python source/workflow files did not change; the 37-test passing first-pass result remains applicable and was not rerun. The reviewer requested deterministic synchronization for stream cancellation; that latest test-only latch change gets one focused revalidation after this gate.

Latest deterministic cancellation check passed independently: 1 case, 0 failures/errors/skips, 0.326 s XML time. Review accepted synchronization; no further source changes or test runs required.
