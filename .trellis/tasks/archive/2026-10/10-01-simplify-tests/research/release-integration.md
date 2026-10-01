# Integration with origin/main 6.2.0

The test-reduction commit was rebased onto upstream SDK 36 / Fastjson2 work. Existing benchmark/count evidence describes the pre-integration baseline; it is not a validation result for this combined revision.

## Conflict resolutions

- `ReleaseWorkflowContractTest.kt`: retain the reduced four-method suite and upstream minSdk 29 / compileSdk 36 / targetSdk 36 declaration plus staging checks. The version-code source-substring snapshot remains deleted: executable Python channel identity/staging and version derivation tests own that behavior.
- `HomeBoardOrderContractTest.kt`: retain deletion. Upstream only switched the JSON import to Fastjson2 and removed an obsolete source-wiring assertion. It added no new behavioral case requiring retention. Behavioral home-order/bookmark tests remain.
- `scripts/test_release_workflow.py`: retain SDK 36 fixture metadata and setup-package expectations. Wrong-target staging uses target 35 (the previous supported target) as its representative rejection; target 34 followed the same exact-equality branch and was removed. Application ID, version name/code, debug flag and minSdk rejection rows remain.
- Restore `lib_bu_message`'s direct `junit:junit:4.13.2` test dependency: upstream adds `MessagePostDecodeTest.kt`, so the dependency is no longer orphaned. The application's `MessageParserGoldenTest.java` is a separate upstream test with the application's existing JUnit dependency.

## Checks

- Audited every current module with `org.junit` imports under `src/test`: lib_base_common (3 files), lib_base_ui_compose (1), lib_bu_message (1), lib_core (4), application (74). Each has its own direct JUnit test dependency after restoration. No other dependency restoration needed.
- Focused Python checks passed: channel identities and staged assets, incorrect-manifest rejection, SDK setup packages; 3 methods, 1.431 seconds. These execute local fixture Bash/Git only.
- `git diff --check --cached` passed.
- No Gradle, device operation or `git rebase --continue` run by this worker. Main coordinates combined validation and release.
