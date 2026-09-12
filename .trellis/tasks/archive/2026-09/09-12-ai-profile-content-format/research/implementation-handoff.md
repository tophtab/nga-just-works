# Implementation handoff: native topic-detail string controls

Date: 2026-09-12.
Worktree: `/home/toph/nga-just-works-ai-summary`, `feature/ai-summary`.
Integrated baseline: `7cb9e50b3fb6a0b51f5435abaedaef1d0b5ff436`.

## Files changed by the implementer

- `nga_phone_base_3.0/src/main/java/sp/phone/ai/summary/NgaTopicBodyParser.java`:
  extend the existing quote scanner to append normalized string text. Literal
  TAB/LF/CR become equivalent JSON escapes; a backslash immediately before
  those raw controls still fails. Check the existing normalized-response bound
  while appending. The product diff is 17 insertions and 4 deletions.
- `nga_phone_base_3.0/src/test/java/sp/phone/ai/summary/NgaProfilePageSourceTest.java`:
  add four regression methods and strengthen the existing GBK list/detail/reply
  integration case. The test diff is 74 insertions and 4 deletions.
- This handoff report and ignored validation evidence under
  `.temp/ai-profile-content-format/`.

The parent owns the task/spec changes, independent review, commits, merge, and
journal. The implementer made no Git commits or main-worktree changes.

## Behavior and regression coverage

The correction belongs to the local native `THREAD.PAGE` representation
adapter. Existing escapes remain intact, including escaped controls, Unicode
escapes, escaped quotes/backslashes, and literal backslash-plus-letter text.
Quoted envelope markers remain text. Numeric repairs and original-post
projection are unchanged. Unsupported raw C0 characters and malformed escapes
still reach the existing error path; the shared decoder remains unchanged.

New regressions inject raw characters **after** fixture serialization:

- `nativeStringWhitespacePreservesBodyTextAndExistingEscapeSemantics` pairs
  each supported raw control with its escaped counterpart, verifies exact
  decoded body text, and exercises adjacent escaped quotes/backslashes,
  existing control/Unicode escapes, literal backslash text, and quoted markers.
- `nativeStringWhitespaceInIgnoredMetadataCannotDiscardTheOriginal` covers
  native and escaped controls in synthetic `alterinfo` and user-table metadata,
  retaining only the verified original body.
- `unsupportedStringControlsAndMalformedEscapesStillFailInBodiesAndMetadata`
  rejects all other raw C0 values in both locations, malformed JSON escapes,
  and lone backslashes followed by raw TAB/LF/CR.
- `nativeWhitespaceExpansionMustFitTheResponseLimit` accepts a response whose
  normalized representation reaches exactly 524,288 characters, rejects the
  next character of expansion despite a raw response below that bound, and
  rejects a densely populated raw-TAB string.

The strengthened `firstPageCollectionNormalizesNativeStringControlsBeforeComposingThePrompt`
uses a loopback server and GBK bytes. A valid topic list, a detail containing
raw controls in both original text and ignored metadata, and a reply list now
complete in exactly three requests. The prompt preserves the cleaned original
and reply while excluding metadata, session sentinels, fetch IDs, and other
floors. Existing identity, numeric, wrapper, cancellation, request-budget, size,
and strict model-decoder tests remain in the focused suite.

## Red/green evidence

Before editing production code, the focused run executed 211 tests across 13
classes and failed exactly four expected success scenarios: original text,
ignored metadata, the accepted expansion boundary, and the loader sequence.
All four exposed `NGA 内容格式异常，请稍后重试`; the new rejection regression
passed. After the local correction, the same 211 tests passed with zero
failures, errors, or skips.

The earlier diagnosis's 204-test result belongs to its older feature baseline.
This repair was validated against integrated main, with four added test methods.

## Completed quality gates

| Gate | Verified result | Log / summary stem |
| --- | --- | --- |
| Focused AI before repair | Expected failure: 211 tests, 13 classes, 4 failures, 0 errors/skips | `focused-red` |
| Focused AI after repair | 211 tests, 13 classes, 0 failures/errors/skips | `focused-green` |
| App debug assembly, full app JVM tests, app lint | Build success; 515 tests, 58 classes, 0 failures/errors/skips; lint 0 Error / 0 Fatal | `app-debug-gate` |
| All-module lint with forced task rerun | 536 tasks executed; all 13 XML reports present, 0 Error / 0 Fatal; 835 warnings | `all-module-lint` |
| Repository debug unit tests | Build success; 600 tests, 76 classes, 13 modules, 0 failures/errors/skips | `repository-debug-tests` |
| App Android-test APK compilation/package | Build success; nonempty APK confirmed from output metadata | `android-test-apk-build` |
| `git diff --check` | Pass | Command result |

Every log and its `-summary.json` are under
`.temp/ai-profile-content-format/`. The red/green parser XML snapshots and the
green strict-model-parser XML are also retained there. Actual generated lint
reports are `<module>/build/reports/lint-results-debug.xml`; unit-test reports
are `<module>/build/test-results/testDebugUnitTest/TEST-*.xml`.

The repository aggregate correctly reused eligible cached library results and
the just-passed app results. Its summary records each Gradle task's cache status;
600 is the inspected report total, not a claim of 600 fresh executions in that
last invocation. Both `lib_bu_statistics` and `lib_module_debug` have one
passing, non-skipped test in their restored reports. There was no missing-JUnit
baseline exception or outside-scope failure.

Compiled test APK:
`nga_phone_base_3.0/build/outputs/apk/androidTest/debug/nga_phone_base_3.0-debug-androidTest.apk`
(811,135 bytes). It was not installed or run.

Commands, run serially from the feature worktree:

```bash
# Run once for red, then again after the product correction for green.
./gradlew :nga_phone_base_3.0:testDebugUnitTest --tests 'sp.phone.ai.*' --tests 'sp.phone.ai.summary.*' --console=plain
./gradlew :nga_phone_base_3.0:assembleDebug :nga_phone_base_3.0:testDebugUnitTest :nga_phone_base_3.0:lintDebug --console=plain
./gradlew lintDebug --continue --rerun-tasks --console=plain
./gradlew testDebugUnitTest --continue --console=plain
./gradlew :nga_phone_base_3.0:assembleDebugAndroidTest --console=plain
git diff --check
```

XML inspection required nonzero per-suite test counts, zero
failures/errors/skips, all 13 lint reports, and zero Error/Fatal findings.

## Remaining boundaries and parent follow-up

There is no outstanding implementation or quality-gate failure. Parent-owned
independent review and integration remain. No additional broad checks were run
after these gates passed.

No live NGA/model request, saved-credential access, ADB operation, device
installation, or instrumentation execution was performed. Device tests were
not run per project policy. The existing separately authorized two-request
diagnosis remains the live evidence; this handoff establishes the offline
repair and build/test results, not a new live or device observation.
