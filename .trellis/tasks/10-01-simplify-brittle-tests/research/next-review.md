# Second-round review

## Findings (fixed)
None. No source corrections necessary.

## Findings (not fixed)
None. No unresolved scope, specification or release-note issues identified.

## Scope and retained protection
- The second-round diff removes exactly the seven approved methods, including two complete files, as listed in next-deletion-plan.md. The prior-round changes to ReleaseWorkflowContractTest and LoadingTipCatalogTest remain consistent with their completed review.
- ArticleConvertFactoryTest exclusively covered the superseded package-private prefix helper. Current wire extraction, image host behavior, live mapper/facade snapshots and attachment consumer tests remain.
- FilterWordModelTest deletion intentionally loses its unique remote-list happy-path smoke checks. FilterUpdateDecodeTest is a different operation and is not described as a substitute.
- The removed AI editor method combines operations retained in generation-aware draft preservation, custom-mode, empty-model and duplicate-callback tests. Inspected production open/modelsLoaded: no special empty-model branch; loaded choices do not assign the draft. The exact removed combination is explicitly an accepted loss.
- The removed legacy image-host method loses its dedicated `.ngacn.cc` attachment-regex combination. Remaining mapping dimensions are related coverage, not an exact replacement; plan accurately discloses this.
- Historical raw-bean exception demonstration removed without altering any facade/container/fallback snapshots or fixture data. Updated inline comment now accurately describes current facade rejection instead of referring to the deleted method.
- Malformed escape fallback test removed while valid callback decoding, Unicode and blank-selection tests remain. Sole current caller remains the WebView callback. The abnormal-input behavior loss is approved.
- Production, security, cancellation, account isolation and storage owners are unchanged. No ignores, exclusions, parameterized-row moves or replacement framework added.

## Specification and publication text
- The Android quality paragraph correctly records the obsolete prefix entry point, current owning path, historical-exception scope and continued requirement for all three distinct parity fixture suites. It does not weaken current release or migration contracts.
- release-notes/6.2.1.md describes a test-maintenance release, with no application behavior change or performance claim. It includes the three required ordered sections with nonempty list items and a 6.2.0...6.2.1 comparison link. Main session reports the release-notes validator passed; reviewer did not rerun it.

## Verification
- Lint: pass. Independently inspected all 13 generated Android lint XML reports; zero Error/Fatal.
- TypeCheck: pass via successful Java/Kotlin compilation in implementation's affected-owner and repository Debug gates.
- Tests: pass. Affected-owner report: common 26, core 27, app 498. Independently inspected final repository XML: 553 tests across 81 reports, zero failures/errors/skips. Full gate log ends BUILD SUCCESSFUL.
- Python: main session reports all 32 tests passed; no duplicate invocation by reviewer.
- Stable tag validation: main session reports `CI_VERSION_NAME=6.2.1 CI_VERSION_CODE=60201000 RELEASE_TAG=6.2.1 ./gradlew verifyReleaseTag` passed. No local release APK packaging was required.
- git diff --check: pass.
- No test or lint gates rerun by reviewer. Device operations and local APK packaging not run per task scope/project policy.

Result: approved for main-session commit/publication workflow; no implementation fixes remain.
