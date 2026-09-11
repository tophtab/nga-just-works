# Final Offline Validation

Date: 2026-09-12. Worktree:
`/home/toph/nga-just-works-thread-ip-location-loading-tips`.
Branch: `feature/thread-ip-location-loading-tips`; baseline: `5bb92cf0`.

## Result

Both features satisfy the reviewed acceptance criteria. The combined checker
found and fixed all in-scope findings; its detailed source/lifecycle review,
regressions, and final source hashes are in `combined-check-report.md`.

| Check | Final result |
| --- | --- |
| App Debug assembly / resource / Java / Kotlin compilation | Passed |
| App Debug JVM tests | 223 tests, 33 suites, 0 failures, 0 errors, 0 skipped |
| Location-specific and Android source contracts | 49 tests within the app suite |
| Loading-tip policy/catalog | 18 tests within the app suite, plus retained-page binding in the existing 7-test prefetch contract suite |
| App lint | XML: 0 Error / 0 Fatal |
| Full Android lint rerun | Passed; all 13 expected module XML reports present, 0 Error / 0 Fatal |
| Repository Debug unit diagnostic | Exit 1: two previously documented, unrelated example-test build failures |
| Spec references | Seven changed/new spec files checked; no broken relative links |
| Whitespace gate | Working-tree and staged diff checks passed, including all new files |

The app tests ran after the last product fix: cache-only subscriptions cannot
restart an online queue after a 429 pause expires. The full-module lint rerun
also ran after that fix. No product edit followed those checks. The subsequent
repository diagnostic reused the successful app test result as UP-TO-DATE.

## Commands and logs

```bash
./gradlew :nga_phone_base_3.0:assembleDebug \
  :nga_phone_base_3.0:testDebugUnitTest \
  :nga_phone_base_3.0:lintDebug --console=plain
./gradlew lintDebug --continue --rerun-tasks --console=plain
./gradlew testDebugUnitTest --continue --console=plain
git diff --check
```

- Final app gate: exit 0, 18 seconds, 552 actionable tasks;
  `/tmp/nga-thread-location-loading-tips-combined-check-app-final.log`.
- Final full-module lint: exit 0, 37 seconds, all 536 tasks executed;
  `/tmp/nga-thread-location-loading-tips-all-module-lint-final.log`.
- Repository diagnostic: exit 1, 9 seconds, 277 actionable tasks;
  `/tmp/nga-thread-location-loading-tips-repository-debug-tests.log`.

App test XML is in `nga_phone_base_3.0/build/test-results/testDebugUnitTest/`.
Each module's lint evidence is `build/reports/lint-results-debug.xml`. The
parent enumerated the modules from `settings.gradle`, rejected missing reports,
parsed every XML, and required zero Error/Fatal issues; process success alone
was not treated as lint success. The final app report contains 223 tests across
33 suites with zero failures/errors/skips.

## Repository diagnostic baseline

The observed failures are already documented in
`.trellis/spec/backend/android-quality-guidelines.md`:

1. `:lib_bu_statistics:compileDebugUnitTestJavaWithJavac`: upstream
   `ExampleUnitTest.java` imports `org.junit` without its test dependency.
2. `:lib_module_debug:kaptDebugUnitTestKotlin`: upstream example test produces
   an unresolved `NonExistentClass` annotation stub.

This run reported only those two failures. The quality contract also records
other historical fixtures; `lib_base_ui` and `lib_core` returned FROM-CACHE in
this diagnostic, so this report does not claim a fresh rerun of those examples.
No unrelated test configuration, dependencies, fixtures, or variants were
modified to conceal diagnostic failures. The required app feature gate passes.

## Scope of verification

Executing policy/repository/parser/cache tests used synthetic data, fake clocks,
temporary files, and loopback HTTP. The configured client's real physical
request count was tested for HTTP 503/429; no live NGA endpoint was contacted.
Lifecycle/RecyclerView integration and tip layout were source-reviewed and
supported by policy/source-contract tests, not executed in an Android UI runtime.
Device/ADB/instrumentation checks were not run per project policy. No
Preview/Release packaging, remote push, main-branch merge, or publication ran.

The shipped catalog has eight eligible instructions. AI guidance requires the
actual bundled settings entry and loadable destination; this branch does not
merge the sibling AI feature. IP text describes the latest public profile
observation and follows the existing page delivery/prefetch mechanism, with
24-hour cache reuse and no fixed inter-request interval.
