# Assertion-level scope and evidence

## ReleaseWorkflowContractTest.kt
Path: nga_phone_base_3.0/src/test/java/gov/anzong/androidnga/ReleaseWorkflowContractTest.kt
- Keep build-variant identity/signing/debuggability/minification configuration checks (line 14): Python stages stub APKs and cannot establish effective Gradle configuration.
- Keep checkout depth/filter wiring checks (line 37): Python does not execute actions/checkout. Remove the exact git-tag command spelling at line 51; scripts/test_release_workflow.py:354 owns reachable-tag behavior.
- Keep shared SDK/module inheritance/ABI configuration checks (line 55). Remove exact staging command strings at lines 79–80; scripts/test_release_workflow.py:616 executes the real staging shell with incorrect manifest values, including SDK values.
- Delete eachJobRunsExactlyOneGradleInvocationCarryingItsReleaseTasks (line 84). scripts/test_release_workflow.py:317 executes identity derivation and checks emitted Gradle tasks; :616 and :630 own staging rejection/signature failure. Intentional loss: exact Gradle invocation count, shell variable spelling, environment wiring snapshots, and prohibitions on future Gradle calls in staging/publication are no longer automatically asserted. Review these integration details when changing the workflow. No claim that emitted-task tests execute the actual Gradle build step.
- Remove helpers/imports only if unused after these edits.

## LoadingTipCatalogTest.java
Path: nga_phone_base_3.0/src/test/java/sp/phone/view/LoadingTipCatalogTest.java
- Replace synthetic XML construction/traversal (lines 43–101) with direct calls to LoadingTipCatalog.isAiSettingsEntry, preserving valid key/destination/class, missing class, wrong key and wrong destination outcomes. The existing helper does not execute production XML traversal.
- Retain AI membership toggling through eligibleTips. Remove fixed base count, resource inventory/immutability assertions (lines 31–39) and exact lookup counts (lines 54, 82). These are intentional low-risk assertion losses; production contracts do not change.
- Production predicate is nga_phone_base_3.0/src/main/java/sp/phone/view/LoadingTipCatalog.java:30. Spec frontend/loading-usage-tips-contract.md section 6 explicitly assigns pool size/copy/immutability inventory to resource review rather than snapshot tests.

## Retained outside scope
ArticleAuthorLocationContractTest and ArticleSelectionActionModeContractTest retain narrow security/lifecycle wiring guards without an existing runtime replacement. All resource lifetime, account isolation, migration snapshots, Python release behavior cases and loading selector/state tests remain intact.

## Validation
Run focused app tests for gov.anzong.androidnga.ReleaseWorkflowContractTest and sp.phone.view.LoadingTipCatalogTest. Run python3 -m unittest discover -s scripts -p 'test_release_workflow.py'. Finish with ./gradlew testDebugUnitTest lintDebug --continue --console=plain and inspect test/lint XML for failures/errors. No device, live publication or additional APK packaging. Record actual method/assertion/helper reductions without a quota; do not claim faster execution without measuring it.
