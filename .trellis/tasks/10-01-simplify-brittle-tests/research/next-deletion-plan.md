# Approved second-round deletion plan

## Goal
Delete entire low-value tests rather than only shorten assertions. This extends the completed first round and was approved by the user together with commit, push, finish-work and stable publication.

## Scope
1. Delete ArticleConvertFactoryTest.java (2 methods): it exercises resolveAttachmentsPrefix, a package-private helper with no production callers. The live mapper uses NgaImageHost.attachmentsPrefix. Retain wire decoder, image host, facade parity and attachment flow tests.
2. Delete FilterWordModelTest.kt (1 method): four smoke-only isSuccess assertions plus println, no decoded-user/word validation. Intentional loss: direct convertEntity happy-path smoke coverage; FilterUpdateDecodeTest tests a different entry and is NOT a replacement.
3. Delete AiModelEditorStateTest.discoveryWithoutAConfiguredModelPreservesTextTypedWhileLoading (1 method): other retained editor tests cover draft preservation, loading/result state, custom mode and blank-model opening. The exact combined scenario is no longer asserted separately.
4. Delete NgaImageHostContractTest.legacyNgacnAttachmentHostIsRewritten (1 method): retained multi-occurrence and unnumbered legacy-host tests cover mapping dimensions; exact .cc attachment combination loses its dedicated assertion.
5. Delete ReadThreadFacadeParityTest.productionAttachmentProjectionRejectsOldRawFallbackAndNullEntries (1 method): historical raw-bean exception demonstration. Retain current facade/container snapshots. Intentional loss: direct helper CCE/NPE type and null-entry exception checks.
6. Delete ArticleSelectionTextTest.malformedEscapesDegradeWithoutThrowing (1 method): sole production caller consumes WebView.evaluateJavascript JSON output; malformed JSON escape fallback has low expected production relevance. Retain valid decoding/Unicode/blank tests. Intentional loss: malformed-escape fallback behavior.

## Out of scope
Production changes, security/cancellation/account/storage boundaries, deletion of entire parity suites, Python suite changes, devices and local APK packaging. Commit/push and stable publication are authorized follow-up work. No quotas, no ignored tests or moved scenario rows.

## Acceptance
Delete 7 methods including 2 whole files; remove imports/fixtures only if newly unused and owned solely by these deletions. Review actual remaining owners and enumerate intentional losses. Compile/run affected tests and downstream reader/common owners; run one full repository Debug unit-test/lint gate and inspect XML (zero failures/errors and lint Error/Fatal). Record counts from actual baseline, not an assumed 560 if other work changed it. No runtime speed claim without measurement.

## Evidence and artifacts
research/next-ai-audit.md and research/next-reader-audit.md hold detailed audit findings. ArticleSelectionText sole caller is ArticleSelectionActionModeCallback.java:124–125. Prior-round results remain authoritative for the completed first round. This lightweight extension uses this plan and an updated PRD; no architecture change requires design.md.
