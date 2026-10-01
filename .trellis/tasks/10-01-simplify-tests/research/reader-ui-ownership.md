# Reader, UI and common coverage ownership

## Change boundary

Reduce maintained test duplication without modifying product behavior. Assigned scope is JVM tests outside AI/profile/AI editor classes. Python, device sources, Gradle configuration and specs are owned by main/other worker. No new harness. The starting point is the task’s 618-method second-pass baseline, not HEAD.

## Primary invariant owners and retained large suites

- `ArticleCacheStoreTest` (8): numeric sparse-page ordering/layout separation; owner checks for list/read/write/delete; unknown-size snapshot identity; corrupt envelope refusal; explicit replay dispatch/layout mismatch; path/symlink and mid-write account change; supported legacy ZIP import/export scope; real App/normal codec roundtrip. These are distinct persistence/data-loss boundaries; none removed.
- `ArticleReaderSessionTest` (11): source/layout handoff, offscreen-result exclusion, account/toggle invalidation, row-based anchor lookup, independent fallback/alignment budgets, legacy account retry, show-all query cleanup/quote identity, floor-based candidate navigation, unknown-target refusal, exact anchor identity during deferred delivery, anchor survival across alignment. Three-size arithmetic matrix reduced to two; ownership and stale-work boundaries remain.
- `ArticleByteClientTest` (11): query construction for two wire APIs; stable account snapshot across fallback/alignment; guest cookie; exact origin validation; redirect/retry/cookie-jar isolation; supported charset decoding; malformed charset/bytes refusal; declared/streamed/truncated size limits; terminal HTTP pre-read close; secret-safe I/O failure; deterministic cancellation. HTTP classification matrix now belongs to `ArticleErrorsTest`, with only redirect retained as wire integration.
- `ArticleBodyViewsTest` (21): lazy binding, HTML rebind, same-response ambiguous-slot replay, reorder/survivor lifetime, thread identity, unknown floor/kind, zero-pid root, ambiguous identity, duplicate identity, missing HTML, effective page, query coordinates, source, owner, generation, page size, resolved thread, missing context, non-identity metadata, clear/recreate, null/empty delivery. Identity dimensions are separate fields in resource scoping; one generic replacement cannot detect an omitted owner/source/generation field. Four ordinary/reorder/index duplicates removed.
- `NgaImageHostContractTest` (15): owns sanitizer, auto/manual selection, uncached page values and path-family rewrite. Removed UI mode snapshots and repeated defaults/legacy examples. Converter tests retain structural JSON errors; normal parser owns actual prefix extraction; attachment flow retains downstream consumers.
- `EmoticonOrderResolverTest` (10): keeps missing data/defaults, unknown and duplicate saved filenames, null elements, roundtrip, invalid indices, boundary moves, nonmutation, invalid movement. Dropped repeated property loops and trivial default/no-op tests.
- `EmoticonUtilsContractTest` (2): persisted filename uniqueness and all actual asset identities. Core decoder retains substitution/unknown-token integration; legacy decoder now samples ac/ng/pg size branches instead of repeating all 238 identities.
- `ArticlePageCacheTest` (12) owns legacy eligibility/description handling and snapshot preparation. Owned-page tests cover selected generation, unknown-size identity and parsed damage rejection; the store owns on-disk identity/corruption. Two duplicate fallback/snapshot methods removed.
- `NavigationDrawerGestureTest` (12) keeps input eligibility, leading direction, vertical/jitter separation, visible-drawer drag, fling transition, positional threshold, unmeasured layout, physical RTL offsets, resize, reset and consumed/multiple-pointer release. Copy/constants and already-settled fling permutations removed.
- Supported image-mode migration remains entirely in `VersionUpgradeHelperMigrationTest` (4); all old modes, missing/corrupt input and migration-marker idempotence retained.
- `ForumBoardBookmarkPersistenceTest` retains actual disk roundtrip, authoritative-empty versus corruption, backup recovery, stable keys, dedup and stale rollback. Home tab order has separate production resolver and transaction implementation, so its movement/rollback tests are not removable merely because bookmark behavior is similar.
- `LoadingTipStateTest` owns loading-occasion lifecycle; selector owns pool identity/repeat avoidance, catalog owns actual capability predicate. Repeated random draws reduced from 200 to 8 and singleton draws from 10 to 2.
- Editor source offsets, whole-token edits, stale revisions and retired queue tickets remain distinct from reader rendering; all methods retained, two duplicate size examples removed.
- `ArticleErrorsTest` owns status and structured-error classification; common empty/BOM inputs now execute it directly, removing 14 duplicated parser calls. Parser suites still verify integration with actual data and error precedence.
- Security-specific source guards retained: implicit selection-text export prohibition; settled-account/invalidation ordering in author-location wiring; release signing/production identity configuration. These are narrow static protection, not runtime proof. Generic menu/default/order snapshots removed.

## Complete retained-method ownership inventory

Each method below owns its named observable invariant; this lists all classes in assigned scope, including unchanged compact suites. No deleted class was a domain’s only security/migration protection.

### `lib_base_common/src/test/java/gov/anzong/androidnga/common/util/EmoticonOrderResolverTest.java` (10)

- `resolve_returnsIdentity_whenSavedIsNull`
- `resolve_returnsEmpty_whenDefaultsIsNull`
- `resolve_ignoresUnknownFileName`
- `resolve_dropsDuplicateEntries`
- `resolve_toleratesNullElements`
- `toFileNames_roundTripsWithResolve`
- `toFileNames_skipsOutOfRangeIndexes`
- `move_toFirstAndLast`
- `move_doesNotMutateInput`
- `move_ignoresOutOfRangeArguments`

### `lib_base_common/src/test/java/gov/anzong/androidnga/common/util/EmoticonUtilsContractTest.java` (2)

- `fileNamesAreUniqueWithinEachCategory`
- `resolverKeepsAssetIdentityAfterCustomOrderAndReset`

### `lib_base_common/src/test/java/gov/anzong/androidnga/common/util/NgaImageHostContractTest.java` (15)

- `pageValuesAreNeverCachedAcrossCalls`
- `validServerFormsBecomeCompleteAttachmentPrefixes`
- `invalidServerFormsAreRejected`
- `autoModeFallsBackForMissingOrInvalidServerValue`
- `autoModeFallsBackForRetiredImageHosts`
- `manualModesIgnoreServerValue`
- `blankInputFallsBackToDefault`
- `surroundingSpaceAndPathAreStripped`
- `portIsPreserved`
- `malformedInputIsRejected`
- `legacyNgacnAttachmentHostIsRewritten`
- `pagePrefixOverridesOnlyLegacyAttachmentFamily`
- `unnumberedNonAttachmentHostBecomesUnnumberedNgaCn`
- `forumDomainsAreUntouched`
- `multipleOccurrencesAreAllRewritten`

### `lib_base_ui_compose/src/test/java/com/justwen/androidnga/ui/compose/widget/TabLayoutWithPagerContractTest.kt` (1)

- `renderedSlotsDriveConsecutiveMovesWithoutRecomposition`

### `lib_core/src/test/java/gov/anzong/androidnga/core/corebuild/HtmlCommentBuilderTest.java` (2)

- `onlyACompleteLeadingReplyHeaderIsRemoved`
- `plainShortMissingAndIncompleteContentRemainSafeAndUnmodified`

### `lib_core/src/test/java/gov/anzong/androidnga/core/decode/ForumEmoticonDecoderTest.java` (2)

- `agreementAndSparkleKeepCorrectAssetsInMixedRepeatedAndQuotedContent`
- `unknownAndMalformedCodesRemainLiteralAroundKnownTokens`

### `nga_phone_base_3.0/src/test/java/gov/anzong/androidnga/ReleaseWorkflowContractTest.kt` (4)

- `previewBuildKeepsProductionIdentityAndIsDebuggableWithoutMinification`
- `checkoutKeepsBranchHistoryAndUsesShallowTagsWithBloblessPartialClone`
- `sharedSdkAndPublishedApkChecksStayPinnedToApi29And35`
- `eachJobRunsExactlyOneGradleInvocationCarryingItsReleaseTasks`

### `nga_phone_base_3.0/src/test/java/gov/anzong/androidnga/activity/compose/board/ForumBoardBookmarkPersistenceTest.kt` (9)

- `moveBookmarkPreservesRelativeOrder`
- `invalidMoveDoesNotMutateOrder`
- `stableKeyUsesFidAndStidInsteadOfHistoricalId`
- `failedOlderWriteCannotRestoreOverNewerMutation`
- `onlyJsonArrayIsAnAuthoritativeEmptyList`
- `duplicateStableKeysKeepTheirFirstPosition`
- `fileRoundTripRestoresOrderAfterReload`
- `corruptFileFallsBackToEmptyWithoutDeletingEvidence`
- `validBackupRecoversWhenPrimaryIsCorrupt`

### `nga_phone_base_3.0/src/test/java/gov/anzong/androidnga/activity/compose/board/HomeBoardOrderTest.kt` (5)

- `savedOrderDropsUnknownAndDuplicateIdsThenAppendsMissingDefaults`
- `malformedPreferenceFallsBackToDefaults`
- `moveSupportsBothDirectionsAndRejectsInvalidOrNoOpIndices`
- `defaultOrderProducesNoPreferenceValue`
- `olderFailedCandidateCannotRestoreOverNewerOrder`

### `nga_phone_base_3.0/src/test/java/gov/anzong/androidnga/activity/compose/drawer/NavigationDrawerGestureTest.kt` (12)

- `openingCanStartAnywhereInsideSettledFavoritePager`
- `toolbarLaterPageUnsettledPagerAndReorderCannotOpen`
- `closedDrawerClaimsOnlyLogicalLeadingHorizontalMovement`
- `jitterWaitsAndVerticalMovementStaysWithContent`
- `openDrawerOwnsEitherHorizontalDirectionForDragClose`
- `fastFlingPicksAnchorByDirectionRegardlessOfHowFarTheSheetTravelled`
- `slowReleaseFallsBackToTheHalfWidthPositionalThreshold`
- `settlingBeforeTheSheetIsMeasuredKeepsTheCurrentAnchor`
- `sheetUsesAbsolutePhysicalOffsetsForBothLayoutDirections`
- `resizingKeepsASettledClosedDrawerAtTheClosedAnchor`
- `immediateResetRestoresTheCapturedStableAnchorAfterCoroutineCancellation`
- `onlyUnconsumedSinglePointerReleaseSettles`

### `nga_phone_base_3.0/src/test/java/gov/anzong/androidnga/activity/compose/filter/FilterWordModelTest.kt` (1)

- `testConvertEntity`

### `nga_phone_base_3.0/src/test/java/sp/phone/common/VersionUpgradeHelperMigrationTest.java` (4)

- `missingValueRemainsMissingAndUsesNewDefault`
- `oldModesMapToNewStorageContract`
- `corruptValueFallsBackToAuto`
- `completedMigrationDoesNotRemapNewValues`

### `nga_phone_base_3.0/src/test/java/sp/phone/mvp/model/convert/ArticleConvertFactoryTest.java` (2)

- `missingGlobalFallsBackWithoutChangingOtherPageData`
- `missingMalformedOrNonStringFieldFallsBack`

### `nga_phone_base_3.0/src/test/java/sp/phone/mvp/model/convert/PageAttachmentPrefixFlowTest.java` (2)

- `attachmentBuilderAndImageListUseOnePagePrefix`
- `retiredPagePrefixFeedsBodyAndAttachmentConsumers`

### `nga_phone_base_3.0/src/test/java/sp/phone/mvp/model/thread/AppArticleParserTest.kt` (8)

- `variableAndSparsePagesPreserveRowsSourceAndPrefix`
- `optionalOpaqueExtensionsNeverRejectOrEnterSource`
- `pidOnlyAndAuthorQueriesKeepTheirActualCoordinates`
- `optionalPaginationOnlyLimitsDependentNavigation`
- `unavailableBodyIsRetainedAndCannotPretendToBeComplete`
- `commentUnknownIdentityAndReplyHeaderAreExplicit`
- `malformedBodyWithValidSubjectStillMarksPageIncomplete`
- `knownErrorsWinOverResidualDataAndCodeAloneIsNotSuccess`

### `nga_phone_base_3.0/src/test/java/sp/phone/mvp/model/thread/ArticleByteClientTest.kt` (11)

- `bothSourcesPreserveFullAuthorAndPidQueryFields`
- `fallbackAndAlignmentKeepTheSameSnapshotAndChangeOnlySourceOrPage`
- `guestHasAnExplicitEmptyCookieWithoutGlobalAccountLookup`
- `onlyExactProjectHttpsOriginsReachTheCallFactory`
- `clientHasNoRedirectRetryCookieJarOrSharedInterceptors`
- `declaredCharsetsAndTheGbkDefaultPreserveOriginalTextAndCloseBodies`
- `invalidCharsetDeclarationsAndMalformedBytesFailWithoutEncodingGuesses`
- `declaredAndStreamedLimitsTruncationAndEmptyBodiesAreDistinctFailures`
- `httpStopsAreClassifiedBeforeParsingAndNeverCreateAnotherCall`
- `ioFailureIsSingleAttemptAndDoesNotExposeItsOriginalMessage`
- `disposalCancelsAnInFlightCallAndSuppressesItsLateFailure`

### `nga_phone_base_3.0/src/test/java/sp/phone/mvp/model/thread/ArticleCacheStoreTest.kt` (8)

- `knownLayoutsMergeNumericSparsePagesAndPreserveOriginalMetadata`
- `ownershipAppliesToTitleListReadWriteAndDelete`
- `unknownPageSizeSnapshotsNeverUnionWindowsOrDropNullMetadata`
- `corruptOwnedEnvelopesNeverFallBackToLegacyOrAnotherSource`
- `replayDispatchIsExplicitAndChecksStoredLayoutAgainstParsedResponse`
- `pathsCannotTraverseOrUseSymlinksAndInterruptedOwnerChangeDoesNotCommit`
- `legacyZipImportsOnlyValidatedLegacyPathsAndDoesNotTouchOwnedRoot`
- `actualAppAndNormalParsersRoundTripStoredSourceAndReportedCoordinates`

### `nga_phone_base_3.0/src/test/java/sp/phone/mvp/model/thread/ArticleErrorsTest.kt` (7)

- `bomPrefixedUnknownHtmlIsStillAnAccessStop`
- `bomAndWhitespaceWithoutContentRemainEmpty`
- `actualHttpEvidencePreservesStatusAndRecoveryPolicy`
- `structuredCausesHaveBoundedMessagesWithoutFabricatingHttpStatus`
- `arbitraryHtmlDoesNotProveDeletionChallengeOrHttpStatus`
- `ordinaryContentMentionsDoNotBecomeSiteErrors`
- `legacyHttpPresentationRetainsUnrecognizedExceptionsAndTheirPolicyIdentity`

### `nga_phone_base_3.0/src/test/java/sp/phone/mvp/model/thread/ArticleReaderSessionTest.kt` (11)

- `sourceAndLayoutHandoffRetiresEveryOldKeyAndIsConsumedOnce`
- `normalMetadataTransitionAndBackgroundResultsCannotReplaceCurrentSource`
- `changedAccountOrToggleResetsSourceAndRejectsInflightPages`
- `pendingAnchorsWaitForTheirPageAndMatchRealRowsWithoutModulo`
- `fallbackAndAlignmentBudgetsAreIndependentBoundedAndForegroundOnly`
- `legacyAccountRetryCountsBeforeAdvancingAndRejectsEmptyOrRepeatedCookie`
- `showAllUsesResolvedIdentityClearsFiltersAndQuoteHintKeepsOrdinaryConvention`
- `showAllCandidatesUseOrdinaryFloorsAndNeverLookupPageOrAnonymousIdentity`
- `unknownOrMissingTargetKeepsReadableFullQueryWithoutInventingFloorOrScanning`
- `delayedConsumptionKeepsPendingUntilEligibleAndRejectsEqualReplacementAndRetiredGeneration`
- `launchTargetSurvivesSourceAlignmentAndOldPostedScrollCannotConsumeNewAnchor`

### `nga_phone_base_3.0/src/test/java/sp/phone/mvp/model/thread/ArticleRowPresentationTest.kt` (3)

- `readableCommentsAndUnknownKindsCanReplyUsingTheirOwnPidWithoutAnAuthorIdentity`
- `replyRequiresUsableSourceAndAnActualReplyOrRecognizedTopicRoot`
- `unavailableDisplayInputPreservesTheEditableSourceAndDoesNotInventAHeader`

### `nga_phone_base_3.0/src/test/java/sp/phone/mvp/model/thread/NormalArticleParserTest.kt` (6)

- `scopedAndLegacySeamsPreserveAttachmentCommentBlacklistAndWpPreparationOrder`
- `pagePrefixesStayIndependentAndUidIdentityWorksWithoutFirstFloor`
- `knownErrorsAndWrongQueryAreRejectedBeforeSuccessfulDelivery`
- `malformedCoreBodyIsNotSerializedIntoAnApparentlyCompletePost`
- `scopedSourceValidationKeepsKnownEmptySubjectAlterNumericAndWpBehavior`
- `unusableNestedCommentKeepsItsKnownParentAndMarksThePageIncomplete`

### `nga_phone_base_3.0/src/test/java/sp/phone/mvp/model/thread/ReplySearchNavigationTest.kt` (3)

- `replyToAnotherAuthorsTopicUsesReplyIdentityAndRemainsNativeReadable`
- `ownTopicAndNonReplyNavigationPreserveTheirQueryKinds`
- `absentOrUnparseableReplyAuthorNeverBecomesTheTopicAuthor`

### `nga_phone_base_3.0/src/test/java/sp/phone/mvp/presenter/ArticleOwnedPageCacheTest.kt` (3)

- `onlyTheActualCompleteSelectedGenerationGetsAnOwnedSnapshot`
- `knownLayoutSnapshotsReuseTheirIdentityAndUnknownSizesAreIndependent`
- `damagedScopedNormalSourceCannotBeSavedAsACompleteOwnedPage`

### `nga_phone_base_3.0/src/test/java/sp/phone/mvp/presenter/ArticlePageCacheTest.java` (12)

- `fullThreadWithoutLaunchMetadataIsEligibleBeforeChildPageLoads`
- `replyAuthorSearchAndCacheContextsAreNeverEligibleOrPrepared`
- `invalidOrMissingThreadContextCannotCache`
- `missingLaunchMetadataRoundTripsAsExistingCacheDescription`
- `suppliedDescriptionIsKeptVerbatimWhenLoadedMetadataDiffers`
- `existingDescriptionWorksWhenResponseOmitsThreadMetadata`
- `snapshotRetainsSelectedChildPageWithoutMutatingPagerOrLoadedData`
- `missingLoadedPageOrRawDataCannotCache`
- `unusableLoadedDescriptionsCannotBecomeCacheEntries`
- `mismatchedLoadedThreadIsRejectedEvenWithSuppliedDescription`
- `invalidSuppliedDescriptionsAreRejectedRatherThanOverwritten`
- `unselectedOrInvalidPageCannotProduceCacheRequest`

### `nga_phone_base_3.0/src/test/java/sp/phone/mvp/presenter/ArticlePageRequestStateTest.java` (7)

- `prefetchIsDeduplicatedAndReadySkipsAutomaticForegroundLoad`
- `enteringDuringPrefetchWaitsAndFailureStartsNormalForegroundLoad`
- `enteringDuringPrefetchReusesASuccessfulRequest`
- `leavingBeforePrefetchFailureKeepsTheFailureInTheBackground`
- `explicitRefreshCoalescesWithAnInFlightPrefetchAndFallsBackNormally`
- `explicitRefreshStillStartsFromReadyAndFailureKeepsExistingDataReady`
- `resumeAndRepeatedRefreshCannotRestoreOldDataWhileARequestIsPending`

### `nga_phone_base_3.0/src/test/java/sp/phone/mvp/viewmodel/ArticleLaunchTargetTest.kt` (3)

- `retainedViewModelAndPageClonesNeverReseedConsumedTarget`
- `newIntentToSameQueryRetiresOldKeyAndSeedsOnlyNewLaunch`
- `environmentRetirementDoesNotReplayLaunchTargetOnRotation`

### `nga_phone_base_3.0/src/test/java/sp/phone/mvp/viewmodel/ArticlePagePrefetchPlannerTest.java` (3)

- `plansOnlyTheNextTwoNonFinalPages`
- `handlesFirstPenultimateAndFinalPages`
- `rejectsInvalidAndOverflowingBoundaries`

### `nga_phone_base_3.0/src/test/java/sp/phone/param/ArticleLinkEntryContractTest.kt` (1)

- `schemeHasItsOwnBrowsableFilter`

### `nga_phone_base_3.0/src/test/java/sp/phone/param/ArticleLinkParserTest.java` (5)

- `actualViaLinkAndReplyIdentity`
- `rejectsMalformedOrUnsupportedIdentity`
- `knownFieldsOnlyAndIdenticalDuplicates`
- `webLinksKeepExistingQueryFields`
- `externalOrDisguisedWebOriginsCannotBecomeAuthenticatedReads`

### `nga_phone_base_3.0/src/test/java/sp/phone/ui/adapter/ArticleBodyViewsTest.java` (21)

- `changedHtmlKeepsSameRowResourceAvailableForRebinding`
- `readyReplayPreservesUnidentifiedAndDuplicateSlots`
- `allocationIsLazyEvenForLargePagesAndUnboundRowsCanDisappear`
- `growthShrinkAndReorderRetainOnlySurvivingRows`
- `samePidFromDifferentThreadCannotInheritResource`
- `positivePidDoesNotRequireKnownFloorOrPostKindForReuse`
- `knownOriginalPostWithZeroPidRetainsResource`
- `unknownIdentitiesGetIndependentResourcesAndRetireOnFreshResponse`
- `duplicateIdentityNeverSharesOrRetainsAmbiguousResources`
- `losingHtmlReleasesBodyWhileOtherRowsKeepTheirResources`
- `effectivePageChangeRetiresResourcesEvenIfRequestedPageIsUnchanged`
- `everyQueryCoordinateParticipatesInResourceScope`
- `sourceChangeRetiresResources`
- `ownerReplacementAndLossRetireResources`
- `readerGenerationChangeRetiresResources`
- `pageSizeReplacementAndLossRetireResources`
- `resolvedThreadChangeRetiresResourcesWithOtherwiseEqualScope`
- `missingPagingContextCannotJustifyReuseAcrossFreshResponses`
- `changedCountsAndRequestMetadataKeepSameEffectivePageResources`
- `clearReleasesOnceAndSameResponseCanCreateNewResourcesAfterward`
- `nullDeliveryAndEmptyOrMissingRowsReleaseOwnedResources`

### `nga_phone_base_3.0/src/test/java/sp/phone/ui/fragment/ArticleAuthorLocationContractTest.kt` (2)

- `staleReaderDataCannotBeRetainedRenderedOrUsedForAuthorRequests`
- `accountSignalsInvalidateBeforeDeferredCaptureAndUseSettledListIndex`

### `nga_phone_base_3.0/src/test/java/sp/phone/util/FunctionUtilsAvatarTest.java` (3)

- `normalizesRetiredAvatarHostFromDirectUrl`
- `normalizesRetiredAvatarHostFromNestedAvatarJson`
- `preservesCurrentOtherAndUnextractableAvatarValues`

### `nga_phone_base_3.0/src/test/java/sp/phone/util/LegacyEmoticonTest.java` (2)

- `canonicalAssetsRetainLegacyCategoryDimensions`
- `legacyCaseAliasesAndUnknownTextArePreserved`

### `nga_phone_base_3.0/src/test/java/sp/phone/view/LoadingTipCatalogTest.java` (4)

- `optionalCapabilityAddsOnlyItsOwnInstruction`
- `aRealSettingsEntryAndItsBundledFragmentEnableAi`
- `aPreferenceWithAMissingDestinationDoesNotAdvertiseAi`
- `entryAndDestinationMustBelongToTheSamePreference`

### `nga_phone_base_3.0/src/test/java/sp/phone/view/LoadingTipSelectorTest.java` (4)

- `emptyPoolHasNoTipAndDoesNotForgetThePreviousChoice`
- `singleItemPoolCanBeReused`
- `eachChoiceBelongsToThePoolAndNeverImmediatelyRepeats`
- `changingEligibilityUsesIdentityInsteadOfTheOldIndex`

### `nga_phone_base_3.0/src/test/java/sp/phone/view/LoadingTipStateTest.java` (7)

- `visibleButNotResumedPrefetchNeverConsumesATip`
- `resumingWithoutAnAttachedVisibleWindowDoesNotSelect`
- `pausingAndAncestorHidingPreserveTheUnfinishedOccasion`
- `hidingTheLoaderFinishesTheOccasionEvenWhileOffscreen`
- `anEmptyCatalogIsSelectedOnlyOncePerOccasion`
- `separateViewsShareRepeatAvoidanceButKeepTheirOwnOccasion`
- `destroyingTheOwnerDropsThePreviousOccasion`

### `nga_phone_base_3.0/src/test/java/sp/phone/view/editor/InlineEmoticonSizeTest.java` (3)

- `acUsesReaderWidthInDensityPixelsAndPreservesAspectRatio`
- `nonAcFamiliesKeepNaturalReaderSizesInsteadOfASquareThumbnail`
- `narrowEditorClampsWidthWithoutDistortingTallEmoticons`

### `nga_phone_base_3.0/src/test/java/sp/phone/view/editor/InlineMediaLoadQueueTest.java` (2)

- `repeatedMediaHaveIndependentTicketsAndAtMostTwoLoads`
- `editOrViewRetirementDropsQueueAndStaleCompletions`

### `nga_phone_base_3.0/src/test/java/sp/phone/view/editor/InlineMediaSourceTest.java` (5)

- `mixedRepeatedMediaKeepExactSourceOffsetsAndRoundTrip`
- `unknownMalformedAndUnsupportedTokensRemainSource`
- `imageHostNormalizationDoesNotModifyMarker`
- `displayedMediaDeletionReplacementAndSelectionAreAtomic`
- `callbacksCannotDecorateNewInputDeletedMediaOrRetiredView`

### `nga_phone_base_3.0/src/test/java/sp/phone/view/webview/ArticleSelectionActionModeContractTest.kt` (1)

- `noProcessTextEntryPointIsReintroduced`

### `nga_phone_base_3.0/src/test/java/sp/phone/view/webview/ArticleSelectionTextTest.java` (6)

- `missingSelectionsDecodeToAnEmptyString`
- `quotedResultsAreUnwrappedAndUnescaped`
- `unicodeEscapesAreRestored`
- `malformedEscapesDegradeWithoutThrowing`
- `blankDetectionCoversNonAsciiWhitespace`
- `blankDetectionRejectsAnyVisibleCharacter`

## Review limitations and trade-offs

The 250–350 overall method target is not achieved by this batch alone: this scope retains 222 of 290 methods. Remaining dense reader cases are field-specific security/persistence/lifecycle boundaries, rather than interchangeable happy paths. Low-risk defaults/copy/getter/no-op combinations intentionally lose direct assertions as recorded in the deletion ledger.

No orphan external fixture files identified: removed classes embedded fixtures; remaining parser/storage tests construct synthetic data inline. Source-reading helpers and their imports were removed with the relevant classes or method. No last test dependency consumer was removed from any module.
