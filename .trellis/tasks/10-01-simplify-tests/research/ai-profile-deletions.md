# AI/profile deletion ledger

Status: implemented; baseline released by main before implementation. 79 methods deleted, assigned scope 328→249. Every row names a remaining owner or explicit low-risk coverage loss. Whole-method deletion also removes its loop rows; later matrix reductions are listed separately.

## AiConfigTest

- `existingCallersDefaultToRoastAndExplicitPromptSettingsAreRetained` — Constructor/default accessor inventory; store legacy migration and custom prompt integration retain meaningful configuration use. Null prompt constructor loses direct low-risk assertion.
- `normalizedConfigurationIsStableAcrossSaveAndLoad` — AiConfigStoreTest.savedConfigReopensAndReplacementIsOneRecord owns real reopen; URL normalization remains in normalizesBaseAndCompleteUrlsWithoutGuessingVersion.
- `httpKeepsTheSameNonSchemeUrlRestrictions` — Same scheme-independent validator matrix as rejectsOtherSchemesCredentialsQueryFragmentAndAmbiguousUrls; HTTP success stays covered. Numeric-port spelling variations lose direct low-risk coverage.

## AiConfigRecordTest

- `realAesGcmRoundTripKeepsAllFieldsTogether` — maximumLengthMultibyteCustomPromptFitsTheVersionTwoBound verifies every field with real AES; store reopen owns replacement. Direct ciphertext substring heuristic removed; encrypted record tampering and device storage checks retained.
- `legacyThreeFieldRecordLoadsWithTheNewRoastDefault` — AiConfigStoreTest.legacyStoredConfigLoadsWithoutCreatingAKeyAndUpgradesOnTheNextSave exercises the same real decoder and additionally verifies no write on read.

## AiConfigStoreTest

- `retainedCustomTextAndSelectionSurviveReopeningForEveryStyle` — AiConfigRecordTest.everyStyleRoundTripsWithTheExactRetainedMultilineCustomText owns encoding matrix; reopenedCustomPromptFlowsThroughCollectionToTheModelUsingTheControllerSnapshot owns storage integration.

## AiProfilePromptTest

- `defaultIsRoastAndTheTwoPresetsHaveDistinctInstructions` — Low-risk default/copy assertions; selectedInstructionsReplaceOnlyTheStyleAndKeepTheSameBoundedEvidence owns actual style application and store migration checks roast default.
- `customInstructionsKeepWhitespaceNewlinesAndUnicodeExactly` — AiConfigRecordTest.everyStyleRoundTripsWithTheExactRetainedMultilineCustomText and SummaryInputTest.selectedInstructionsReplaceOnlyTheStyleAndKeepTheSameBoundedEvidence preserve exact content.
- `presetRetainsCustomTextWithoutUsingItAsInstructions` — SummaryInputTest.selectedInstructionsReplaceOnlyTheStyleAndKeepTheSameBoundedEvidence owns preset isolation; prompt editor switching owns retention.

## AiModelsClientTest

- `authenticationChallengeDoesNotUseSystemCredentialsOrRetry` — AiSummaryClientTest.authenticationChallengeDoesNotUseSystemCredentialsOrRetry owns shared isolated base-client authenticator behavior; discovery cookie/key and redirect integration remain.
- `cancellationTerminatesInflightDiscoveryWithoutSuccess` — cancellingTheReturnedCallAlsoStopsTheFallback owns returned discovery Call cancellation, including fallback state.
- `stalledDiscoveryIsATimeoutRatherThanUserCancellation` — bodyReadTimeoutCannotBecomeSuccessOrCancellation and fallbackSharesTheOriginalTotalTimeoutBudget own discovery timeout mapping; no-response preheaders loses redundant direct variant.

## AiSummaryClientTest

- `productionTransportSendsHttpSummariesToTheConfiguredCustomEndpoint` — AiModelsClientTest.productionHttpLookupNeedsOnlyDraftEndpointAndKey exercises HTTP in production base transport; requestsOneUtf8StreamWithTheSharedTokenBudgetAndWithoutNgaHeaders owns POST wire. Custom summary endpoint composition loses direct integration assertion; AiConfig URL tests remain.
- `incompleteExhaustedAndMalformedStreamsRetainEarlierProgress` — AiStreamParserTest.lengthAndPrematureEofKeepPartialTextButHaveDifferentErrors and malformedEventsAndServerErrorsPreserveOnlyEarlierTypedProgress own parser matrix; streamingTimeoutFlushesTheLastCoalescedSnapshotAndNeverBecomesCancellation owns client error progress flushing.
- `cancellationTerminatesAnInflightCallWithNoSuccess` — cancellingAStreamAfterProgressCannotPublishSuccess retains real cancellation; discovery fallback has its own cancellation. Nonstream testConnection cancellation loses direct duplicate variant.
- `stalledCallIsATimeoutRatherThanUserCancellation` — bodyReadTimeoutCannotBecomeSuccessOrCancellation owns nonstream timeout; streamingTimeoutFlushesTheLastCoalescedSnapshotAndNeverBecomesCancellation owns streaming timeout.
- `maximumProfileMetadataAndRepliesFitBothPresetsWithoutClipping` — maximumCustomProfileFitsWithoutDroppingInstructionsOrReplies owns larger prompt-to-wire boundary; SummaryInputTest.selectedInstructionsReplaceOnlyTheStyleAndKeepTheSameBoundedEvidence owns presets.

## AiResponseParserTest

- `nestedPunctuationInsideTextIsNotCountedAsJsonStructure` — Low-risk punctuation variants; legacy typed JSON compatibility and bounded parser cases retained.
- `unclosedThinkingCannotBeCopiedAsAnAnswer` — AiStreamParserTest.unclosedMatchedTagsStayReasoningEvenWhenTheStreamCompletes owns shared accumulator unfinished-thinking rule; JSON reasoning integration remains.
- `ordinaryBracesDataLabelsAndTagsAfterProseStayLiteral` — AiStreamParserTest.failureReleasesOrdinaryTextHeldForProtocolScreening and shortThinkingTagAndLiteralTagsAfterProseAreHandledDifferently own shared accumulator text screening.
- `explicitlyIndexedFirstChoiceCanArriveAfterAnotherChoice` — AiStreamParserTest.usageAndNonzeroChoiceEventsNeverOverwriteTheAnswerOrEndTheStream and explicitZeroChoiceWinsOverAnUnindexedFirstRow own shared choice selector; JSON first-choice smoke remains.
- `modelListsUseTheSharedBoundedDecoderAndIgnoreSpecialKeys` — typeAndReferenceKeysAreOrdinaryData, malformedAndTooDeepJsonFailsWithoutLeakingInput, outputAndSharedParserInputAreBounded own SafeJsonParser; modelIds malformed shape integration remains. Direct model entry-point special-key repetition removed.

## AiStreamParserTest

- `invalidAndIncompleteUtf8AreRejectedStrictly` — invalidUtf8AfterValidEventsPreservesTheirLatestPartialSnapshot covers both malformed and truncated UTF-8 plus prefix retention.

## SummaryControllerTest

- `inputReceivesTheValidatedSnapshotWithoutAnExtraConfigurationRead` — AiConfigStoreTest.reopenedCustomPromptFlowsThroughCollectionToTheModelUsingTheControllerSnapshot owns configuration-to-model integration and exactly two reads.
- `retryDiscardsOldSuccessAndErrorEvenWhenTargetIsUnchanged` — retryClearsVisibleAndQueuedChannelsAndRejectsLateProgressForTheSameTarget owns same-target generation rejection; success/error terminal callbacks also covered by dismissClearsBothChannelsAndDiscardsQueuedAndFutureModelProgress.
- `changedTargetRejectsLateResultsAndCancelsItsHandle` — changedTargetDiscardsQueuedProgressAndCancelsTheOldRequest owns changed target invalidation and subsequent successful generation.

## NgaProfilePageSourceTest

- `unavailableTopicsDoNotHideVisibleTopicsBeforeOrAfterThem` — maximumCollectionUsesOnlyTwoListReadsEvenWithSynchronousCallbacks owns unavailable topic filtering with visible entries; unavailableRecordsDoNotConsumeTheTwentyVisibleEntryLimit owns allowance. Low-risk middle-row order variant removed.
- `unmarkedForeignOrMalformedRecordsStillFailAfterUnavailableRows` — blankAndNonStringMarkersRetainNormalAuthorAndContentValidation owns fail-closed author/content; matchingTopicsAndWrappedResponsesAreAcceptedButOtherAuthorsAreRejected owns wrong UID. Position after skipped row loses direct combination.
- `allUnavailablePagesContributeNoActivityEvenWithoutUsableAuthorOrBody` — unavailableTopicListsStillAllowRepliesButNotAnEmptySummary owns empty unavailable integration; unavailableReplyRowsAndNestedBodiesCannotEnterThePrompt owns reply skip.
- `missingReplyBodyCannotSilentlyTurnIntoATitleOnlySummary` — blankAndNonStringMarkersRetainNormalAuthorAndContentValidation retains missing reply body error.
- `anExplicitEmptyFirstPageIsSuccessfulData` — emptyRetriesForEitherKindArePacedAndCanRecover executes explicit empty pages through source and loader.
- `cancelThenReopenWaitsForTheOldCallToFinishAndThenTheCooldown` — cancellationDoesNotWaitForABlockedResponseRead covers old physical call, new request and terminal cooldown with deterministic blocked read.
- `cancellationAtEitherListDiscardsLateCallbacksAndStopsLaterReads` — ProfileSummaryLoaderTest cancellationBeforeFirstPagePreventsReplyFetchEvenIfCallbackArrives/cancellationDuringReplyReadDiscardsItsResultAndFailure own list lifecycle; source blocked-read and waiting cancellation remain.
- `completedListCallbacksCannotChangeOrFailTheRemainingCollection` — ProfileSummaryLoaderTest.staleSameKindCallbacksCannotConsumeRetriesOrReplaceTheirResult owns attempt generation; dedicated source cancellation remains. Direct duplicated source callback variant removed.
- `replyTransportAndParserFailuresStopWithoutAPartialTopicSample` — ProfileSummaryLoaderTest.anErrorAfterAnEmptyResultIsTerminalForEitherKind owns no partial prompt; source HTTP/parser/charset bounds own classification. Six loopback cross-product variants removed.
- `replyDeadlineTerminatesCollectionWithoutProducingAPartialPrompt` — callDeadlineReportsAnErrorEvenThoughOkHttpMarksTheCallCanceled owns actual source timeout; loader error tests own partial-result rejection.
- `deliberateCancellationSuppressesTheTransportCallback` — cancellationDoesNotWaitForABlockedResponseRead owns cancellation suppression with deterministic completion instead of 300 ms absence wait.

## ProfileSummaryLoaderTest

- `twoSequentialFirstPageReadsStayBoundToTheViewedUid` — NgaProfilePageSourceTest.maximumCollectionUsesOnlyTwoListReadsEvenWithSynchronousCallbacks owns two-read viewed UID integration.
- `selectedCustomInstructionsAreUsedAfterBothPagesFinish` — AiConfigStoreTest.reopenedCustomPromptFlowsThroughCollectionToTheModelUsingTheControllerSnapshot owns stored custom prompt through loader.
- `eitherKindCanRecoverOnItsFirstOrSecondEmptyRetry` — NgaProfilePageSourceTest.emptyRetriesForEitherKindArePacedAndCanRecover owns last allowed retry recovery for both kinds. Earlier successful retry follows same branch.
- `firstPageFailureStopsWithoutAutomaticRetryOrPartialSummary` — anErrorAfterAnEmptyResultIsTerminalForEitherKind owns terminal error including late callbacks.
- `duplicateFirstPageCallbacksCannotStartMoreReads` — staleSameKindCallbacksCannotConsumeRetriesOrReplaceTheirResult owns duplicate callbacks and terminal count.
- `synchronousSourcesAndEmptyActivityFinishWithoutHanging` — NgaProfilePageSourceTest.unavailableTopicListsStillAllowRepliesButNotAnEmptySummary owns synchronous empty exhaustion; aSynchronousEmptyAttemptCannotOverwriteTheRetryCancelHandle owns handle cancellation.

## SummaryInputTest

- `profileCountsAndIdentifiersExcludeNullEntriesAndKeepEachKindsOrder` — profileInputCopiesAndBoundsEachPageAndReplyBody retains null filtering and ordered evidence labels; exact small 2/3 count formatting loses low-risk duplicate.
- `profileWithEitherPageEmptyKeepsAccurateCountsAndReplyIsolation` — ProfileSummaryLoaderTest.exhaustedEmptyRetriesStillAllowVisibleActivityFromTheOtherKind owns either-kind activity; plainTextKeepsVisibleTextAndQuotesWithoutRenderingOrRetainingMediaUrls owns cleaning; profileInputCopiesAndBoundsEachPageAndReplyBody owns topic body exclusion.
- `profileWithoutVisibleActivityIsEmpty` — ProfileSummaryLoaderTest.bothEmptyKindsStopAfterSixReadsWithoutAProfilePrompt owns empty activity outcome; null-list and null-only direct formatting permutations are low-risk loss.

## AiSummaryUiContractTest

- `layoutKeepsReasoningFoldedAndScrollableWithoutClippingOrLiveTokenAnnouncements` — Remove layout/source snapshot. Low-risk XML scrolling/accessibility attribute inventories no longer asserted; controller retains answer/reasoning behavior. No claim that JVM proves Android layout.

## AuthorLocationRepositoryTest

- `fastCompletionCannotStartNextAuthorWithin500Millis` — bothRandomIntervalEndpointsApplyOnlyAfterTerminalCallback owns 200/500 boundaries; closingConsumersCancelsDelayedWorkWithoutResettingTheGlobalInterval owns wakeup reuse.
- `slowRequestsKeepThePhysicalSlotAndStillLeave500QuietMillis` — bothRandomIntervalEndpointsApplyOnlyAfterTerminalCallback plus cancelledCallsKeepTheirSlotAndDelayNewWorkAfterTheTerminalCallback own completion-relative gap and physical slot.
- `accountChangesCancelPendingAuthorsWithoutResettingTheGlobalInterval` — cancelledCallsKeepTheirSlotAndDelayNewWorkAfterTheTerminalCallback owns cross-session pacing; accountSignalInvalidatesImmediatelyAndSameUidCredentialReplacementCannotLeak owns cancellation.
- `emptyQueueDoesNotPollAndLaterOnlineWorkStillHonorsTheInterval` — closingConsumersCancelsDelayedWorkWithoutResettingTheGlobalInterval owns delayed restart; no-poll empty-loop detail is low-risk loss.
- `serverStopsDoNotScheduleBackgroundRetries` — rateLimitPausesTheWholeScopeAndRespectsLongerServerDelay and siteRejectionStopsThatSessionWithoutRotatingAccountsOrRestartingOnRevisit own both terminal stop kinds.
- `empty503StopsQueuedAndFutureAuthorsForTheCapturedSession` — ProfileLocationTransportTest.serviceUnavailableStopsWithoutDependingOnAnErrorBody owns 503 mapping; siteRejectionStopsThatSessionWithoutRotatingAccountsOrRestartingOnRevisit owns repository stop.
- `arbitraryPageCountsAndBelowViewportAuthorsHaveNoBatchOrPageWindowBarrier` — sharedConsumersDetachIndependentlyAndUnsentOrphanAuthorsAreRemoved and AuthorLocationPageTest.handoffSharesInflightAuthorsAndRetiresRemovedQueuedAuthors own shared-author dispatch and actual page handoff; 162-author stress/no batch cap loses direct low-risk coverage.
- `freshAndValidEmptyCacheSurviveRepositoryRecreationButExpireOnDemand` — AuthorLocationStoreTest.latestAndValidEmptyObservationsRoundTripAndExpireAtExactly24Hours owns persistence/expiry; savedPageReadersUseOnlyFreshCacheAndNeverQueueMisses owns repository cache integration.
- `unknownSiteAndWrapperPrefixedChallengeFixturesStopBeforeAnotherAuthorStarts` — ProfileLocationParserTest.nonProfilePagesSiteMessagesAndOldJsonEnvelopesStopTheSession owns parser rejection; siteRejectionStopsThatSessionWithoutRotatingAccountsOrRestartingOnRevisit owns session stop.
- `replacingPageDataCannotDeliverAnOldAuthorsResultToTheNewRows` — AuthorLocationPageTest.retiredAuthorsCannotPublishIntoReplacementRows owns actual page replacement integration.
- `previouslyDeliveredSnapshotIsInvalidatedWhenAccountChanges` — AuthorLocationPageTest.accountDomainAndCredentialSignalsClearDuringThePendingGap asserts formerly drawn snapshot becomes null.
- `cacheOnlyOwnerResumeDoesNotUnpauseAnotherThreadsQueue` — cacheOnlyDeliveryCannotResumeAnOnlineQueueAfterItsRateLimitExpires plus sharedForegroundOwnerKeepsCallAndOfflineOwnerNeverResumesWork own offline demand. Exact offline owner toggle variant loses low-risk repeated combination.

## AuthorLocationPageTest

- `replayAfterInvalidationUsesCurrentEmptyOutput` — accountDomainAndCredentialSignalsClearDuringThePendingGap owns invalidated same-data delivery; no duplicate replay assertion.
- `recreatedCacheOnlyPageCannotResumeOtherPagesExpiredServerPause` — AuthorLocationRepositoryTest.cacheOnlyDeliveryCannotResumeAnOnlineQueueAfterItsRateLimitExpires owns pause; readyReplayWhilePendingCannotPromoteCacheOnlyIntent owns page replay intent.

## AiModelEditorStateTest

- `firstFailureOrEmptyResultStillAllowsManualEntry` — failedRefreshRetainsSameServiceChoicesAndSelection/emptyRefreshRetainsSameServiceChoicesAndCustomText own failure/empty usability; discoveryWithoutAConfiguredModelPreservesTextTypedWhileLoading owns no saved model. First-open cross-product loses direct duplicate.

## AiProfilePromptEditorStateTest

- `freshSettingsAndAnOpenedEditorBothDefaultToRoast` — switchingPresetsAndReopeningRetainsExactCustomText and invalidCustomCannotReplaceTheDraftAndCanBeCorrectedInTheSameEditor assert actual draft default during transitions.

## AiSettingsContractTest

- `aiConfigurationLivesOnlyOnTheSecondLevelScreen` — Low-risk source/layout navigation and row-count snapshot removed.
- `saveIsAnAccessibleToolbarActionUsingTheExistingIcon` — Low-risk toolbar icon/title spelling snapshot removed; save atomic behavior retained in store tests.
- `profilePromptPreferenceHasNonpersistentStyleLabels` — Low-risk preference labels/default XML snapshot removed; prompt persistence and exact custom text retain behavioral owners.

## Reduced input matrices

- `AiModelsClientTest.baseCompleteAndCustomUrlsKeepTheirPrefixEncodingAndPort` — Remove /chat/completions and /v1 base variants (7→5 routes); configuration normalizer owns equivalence, discovery retains bare root, completed endpoint, custom prefix and encoded path.
- `AiModelsClientTest.baseCompleteAndCustomUrlsKeepTheirPrefixEncodingAndPort` — Remove custom base duplicate (5→4 routes); completed custom endpoint and AiConfig normalization retained.
- `AiModelsClientTest.rootModelRoutesFallBackOnceFor404And405` — Remove complete-endpoint × fallback-status duplicate (4→2 scenarios); both 404/405 retained and endpoint normalization has its own owner.
- `AiModelsClientTest.rootModelRoutesFallBackOnceFor404And405` — Request assertion adjusted to exactly two remaining attempts per status, not weakened.
- `AiModelsClientTest.rootHtmlDocumentsFallBackRegardlessOfContentType` — Drop plain lowercase doctype duplicate; mixed-case/BOM/whitespace doctype and HTML tag branches retained.
- `AiModelsClientTest.rootHtmlDocumentsFallBackRegardlessOfContentType` — Drop repeated BOM html and doctype-tab variants (6→3 documents overall); keep BOM/case/whitespace doctype, mixed-case html tag, and self-closing delimiter.
- `AiModelsClientTest.partialHtmlTokensAndDocumentsOutsideThePrefixDoNotFallBack` — Remove two same non-leading marker cases (6→4); keep both invalid marker delimiters, truncated token and beyond-peek-bound document.
- `AiModelsClientTest.explicitCustomAndEncodedPrefixesNeverFallBack` — Remove second version spelling; explicit /v1 retained.
- `AiModelsClientTest.explicitCustomAndEncodedPrefixesNeverFallBack` — Remove encoded version/slash-only duplicates, retain encoded tenant slash/unicode path (18→9 route/status scenarios overall). Root encodedPath eligibility branch remains challenged.
- `AiModelsClientTest.invalidDraftFieldsNeverReachTheServer` — Endpoint validation matrix 10→1 representative (embedded secret); AiConfigTest owns full rules. Client retains no-server-request and private error assertion.
- `AiModelsClientTest.invalidDraftFieldsNeverReachTheServer` — Key validation matrix 7→1 injection representative; AiConfigTest owns bounds/blank/unicode; null callback remains.
- `AiModelsClientTest.rootNonPathFailuresDoNotFallBackEvenWithHtmlBodies` — 10→4 nonpath statuses, one per mapping class including 503 immediate-retry risk; 403/408/421/500/502/504 duplicate mapping branches removed.
- `AiModelsClientTest.failedFallbackUsesExistingStatusErrorsWithoutAnotherAttempt` — 8→2 failed fallback statuses: preserve otherwise-retriable 404/405 no-third-attempt; root mapping owns remaining classes and serviceUnavailableCannotConsumeAnExtraDiscoveryResponse retains fallback 503.
- `AiModelsClientTest.redirectsNeverForwardTheKeyToAnotherServer` — 10→4 redirect scenarios across first/fallback stages; representative rewrite/preserve-method redirects retained. 301/303/308 duplicates share disabled followRedirects.
- `AiModelsClientTest.redirectsNeverForwardTheKeyToAnotherServer` — Exact remaining request count updated; destination count stays zero.
- `AiModelsClientTest.malformedEmptyNonModelAndInvalidUtf8ResponsesAreErrors` — Remove JSON text/invalid id/partial list/oversized id/depth combinations from transport (9→4 responses × two attempts =18→8 rows); AiResponseParserTest owns those five typed-decoder rules.
- `AiModelsClientTest.knownChunkedAndDecompressedResponsesAreBounded` — Remove two dedup-row-limit transport permutations (8→6); parser modelRowCountIsBoundedBeforeDeduplication owns exact row limit; declared/chunked/gzip byte bounds retained at both attempts.
- `AiSummaryClientTest.classifiesHttpFailuresWithoutExposingServerBody` — 7→5 status rows; 403 duplicates authentication, 503 retains dedicated one-shot no-retry case.
- `AiSummaryClientTest.redirectsDoNotForwardTheKeyOrBody` — 3→2 redirect rows, preserve rewrite/preserve-method representatives; 308 same disabled-follow branch.
- `AiSummaryClientTest.redirectsDoNotForwardTheKeyOrBody` — Exact request assertion adjusted to two retained scenarios.
- `NgaProfilePageSourceTest.blankAndNonStringMarkersRetainNormalAuthorAndContentValidation` — 36→8 marker/kind/value scenarios: blank string vs non-string branch; remove null, empty string, false, numeric/object/list duplicates. Both marker names and topic/reply owners remain and each validates matching/wrong/missing authored data.
- `NgaProfilePageSourceTest.invalidTopicIdsFailBeforeReturningActivity` — 12→6 invalid ID rows: missing, nonpositive, noncanonical, injection, overflow, wrong type. Remove empty/negative/plus/exponent/object/list duplicates.
- `NgaProfilePageSourceTest.chunkedOversizeAndUnsupportedCharsetFailBeforeProducingActivity` — 4→3 transport rows; remove charset-whitespace spelling duplicate, retain invalid charset, invalid UTF-8 and streaming limit.
- `NgaProfilePageSourceTest.chunkedOversizeAndUnsupportedCharsetFailBeforeProducingActivity` — Adjust loop to exactly remaining response rows.
- `NgaProfilePageSourceTest.chunkedOversizeAndUnsupportedCharsetFailBeforeProducingActivity` — Exact request count adjusted.
- `AiStreamParserTest.splitUtf8MultilineDataAndAllSseLineEndingsAreSupported` — 9→3 newline/fragment rows; byte-at-a-time is the maximal UTF-8 split, keep CR/LF/CRLF parser branches.
- `ProfileLocationTransportTest.redirectAndAccessResponsesStopWithoutReadingTheirBodies` — 6→3 status rows, redirect/authentication/not-found representatives; unreadable body still proves no body parse.
- `ProfileLocationTransportTest.serviceUnavailableStopsWithoutDependingOnAnErrorBody` — 6→2 body rows; null and oversized unreadable body establish early 503 classification. Empty, valid, malformed and small-unreadable duplicates removed.
- `ProfileLocationTransportTest.non200StatusCannotTurnAWebProfileIntoSuccess` — 4→2 non-200 rows; keep unexpected success status vs server failure, drop 502/504 aliases.
- `AiConfigRecordTest.authenticatedMalformedVersionsAndPromptFieldsFailClosed` — Remove oversized CUSTOM record row: retained DETAILED proves retained text bound applies even when inactive; AiProfilePrompt owns all-style validation.
- `AiConfigRecordTest.rejectsTruncatedExtendedAndOversizedRecords` — Remove empty record duplicate, retain null, truncated header, truncated ciphertext, extended record and absolute size limit.
- `SummaryInputTest.knownFloorsKeepTheirOriginalPromptAfterRowPresentationChanges` — 2→1 row retaining zero-floor boundary; clickedFloorIsAnImmutableSnapshotWithoutCommentsOrAccountFields already covers positive floor 7.

## Second ownership pass

- `AiModelsClientTest.baseCompleteAndCustomUrlsKeepTheirPrefixEncodingAndPort` — AiConfigTest normalizesBaseAndCompleteUrlsWithoutGuessingVersion/acceptsHttpLanAddressesAndPreservesCustomPrefixesAndPorts own base/completed URL equivalence. explicitCustomAndEncodedPrefixesNeverFallBack asserts derived custom/encoded models routes on actual requests, productionHttpLookupNeedsOnlyDraftEndpointAndKey asserts port and /v1/models, root fallback tests assert /models.
- `AiModelsClientTest.networkFailuresAtEitherAttemptDoNotTryAnotherPath` — failedHtmlPeekDoesNotStartFallback owns actual root I/O error; AiSummaryClientTest.disconnectIsANetworkErrorAndIsNotRetried owns shared error callback path. Discovery fallback disconnect variant is low-risk direct loss; fallback cancellation and timeout retained.
- `AiConfigStoreTest.clearRemovesRecordAndKeyAndCanBeRepeated` — failedFileDeletionStillDestroysTheKey and failedKeyDeletionStillRemovesConfiguration each verify recovery clear and both resources independently. Repeated successful clear loses trivial idempotent variant.
- `AiResponseParserTest.takesOnlyTheFirstTextChoice` — JSON first-choice behavior remains in emptyReasoningOnlyAndUsageOnlyResponsesNeverBecomeAnswers (later answer is not substituted); successful text parsing is exercised by JSON client smoke.
- `AiResponseParserTest.reasoningAndTheCompleteAnswerAreSeparateIncludingInlineThinking` — AiStreamParserTest.nativeAndInlineThinkingStaySeparateAcrossEveryTagSplit owns shared accumulator separation; AiSummaryClientTest.jsonFallbackPublishesReasoningAndRetainsTheEntireAnswer owns JSON reasoning integration. Combined native+inline JSON variant removed.
- `AiResponseParserTest.emptyModelListsAreValidAndImmutable` — AiModelsClientTest.emptyDataIsASuccessfulImmutableList executes real parser and also proves empty list never triggers fallback.
- `SummaryControllerTest.callbacksAreMarshalledThroughTheOwnerExecutorAndReachSuccess` — successKeepsUndeliveredReasoningAndCopiesTheFullAnswerAboveOneThousandCharacters and progressDisplaysCumulativeAnswerAndReasoningBeforeCompletionWithoutEnablingCopy verify queued output and success. Detailed intermediate input-executor ordering loses low-risk repeated happy path.
- `SummaryControllerTest.inputSourceProgressCannotBeRenderedAsModelOutput` — InputSource callback default no-op is a trivial interface default; low-risk direct default assertion removed. Model progress and input final result lifecycle retained.
- `SummaryInputTest.explicitCommentDoesNotTreatItsRawNumberAsAKnownFloor` — RESTORED after independent review. A COMMENT with positive raw number must not enter the prompt as a known floor; missingFloorIsOmittedWithoutLosingBodyOrTargetIdentity uses a negative number and cannot detect the caller bypassing ArticleRowPresentation.hasFloor. This retained integration regression is not counted as deletion.
- `AuthorLocationPageTest.activeOutputStillReceivesResultsWhileReplacementIsPending` — Low-risk direct smoothness assertion for an old subscriber completion during pending gap removed; freshResponseKeepsKnownLocationThroughoutDeferredCacheHandoff retains visible cache stability and handoffSharesInflightAuthorsAndRetiresRemovedQueuedAuthors retains inflight sharing.
- `AuthorLocationRepositoryTest.synchronousCompletionDoesNotCancelAlreadyFinishedHandle` — Low-risk cancel-call counter on an already terminal handle is an implementation detail; reentrantPauseBeforeFetchReturnsCancelsHandleAndKeepsPhysicalSlot retains important live-handle reentrancy.
- `AuthorLocationRepositoryTest.independentForegroundAndHiddenPrefetchDeliveriesShareAuthorsThroughThePacedQueue` — sharedConsumersDetachIndependentlyAndUnsentOrphanAuthorsAreRemoved owns shared author across consumers and physical queue. AuthorLocationPageTest.handoffSharesInflightAuthorsAndRetiresRemovedQueuedAuthors owns actual new page handoff. Removed three-success overlap permutation.
- `AiProfilePromptEditorStateTest.reloadingSavedSettingsReplacesBothTheDraftAndAnyOpenEditor` — cancelDiscardsEditsButPreservesThePreviouslyAcceptedSettingsDraft covers reset stored data, subsequent editor and late callback generation; direct reset-during-open convenience behavior is low-risk loss.

Review adjustment: retain the compact four-row root nonpath/HTML fallback exclusion test. Authentication and rate-limit failures must remain explicitly unable to trigger a second request.
