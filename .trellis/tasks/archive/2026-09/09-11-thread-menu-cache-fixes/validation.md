# Validation Record

## Environment and Ownership

- Work branch: `fix/thread-menu-cache`, based on `8284c703`.
- Java: OpenJDK 17.0.20. The existing `local.properties` selects the repository's
  `.android-sdk`; no environment or build configuration changes are needed.
- The implementation agent owns product/test changes and the initial app
  Gradle gate. The main session owns repository-wide diagnostics, task/spec
  records, and final integration. Gradle invocations are serialized between
  agents to avoid concurrent output/build-directory mutation.
- The unrelated untracked task `09-11-upstream-august-2026-review` existed at
  session start and is excluded from this task's changes and commit plan.

## Planned Checks

| Check | Status |
| --- | --- |
| App debug build, unit tests, and lint | Passed: 155 tests; lint 0 Error/Fatal |
| Cache metadata behavior and serialization/read-back tests | Passed: 14 ArticlePageCacheTest cases |
| Floor menu resources and surviving handlers | Passed: exact removals, preserved order/shared actions |
| Cached-tab rule, setup order, and sparse-page mapping | Passed: same rule as online, mapping unchanged |
| Independent full-scope Trellis review | Passed; Unicode-blank finding fixed, no remaining task defects |
| Fresh all-module lint plus XML Error/Fatal audit | Passed: all 13 modules have zero Error/Fatal |
| Repository-wide debug unit-test diagnostic | Completed: two documented legacy fixture failures |
| Task context validation and planning artifact checks | Passed before implementation |
| Device/instrumentation/E2E checks | Not run per project policy |
| Live NGA requests | Not run; offline validation only |

The initial report inventory found 13 Android modules and pre-existing lint XML
reports with zero Error/Fatal findings. Verification uses the fresh run recorded
below rather than that initial inventory.

## Initial App Gate

Command:

```bash
./gradlew :nga_phone_base_3.0:assembleDebug :nga_phone_base_3.0:testDebugUnitTest :nga_phone_base_3.0:lintDebug --console=plain
```

- Exit 0; `BUILD SUCCESSFUL in 2m 57s`; 552 tasks, 30 executed.
- Fresh app test XML: 24 suites, 155 tests, zero failures/errors/skips.
- New `ArticlePageCacheTest`: 14 passed. It exercises full-thread eligibility,
  the parent/child page distinction, invalid data, existing metadata preservation,
  Fastjson description read-back, and stable snapshots across page changes.
- Existing `ArticlePageRefreshContractTest` (7), `TopicPagePrefetchContractTest`
  (6), and `ArticlePageRequestStateTest` (7) all passed.
- App lint: zero Error/Fatal findings, 723 Warning and 1 Information.
- Log: `/tmp/thread-menu-cache-app-gate.log`.

## Full-Repository Gate and Static Checks

- `./gradlew lintDebug --continue --rerun-tasks --console=plain`: exit 0 in
  1m 45s, all 536 tasks executed. The main session and reviewer independently
  parsed all 13 module lint XML reports: zero Error/Fatal in every module.
  Log: `/tmp/thread-menu-cache-all-lint.log`.
- `./gradlew testDebugUnitTest --continue --console=plain`: exit 1 in 6s with
  exactly two build failures. `lib_bu_statistics`'s unchanged `ExampleUnitTest`
  cannot compile because `org.junit` is absent; `lib_module_debug`'s unchanged
  example test has an unresolved KAPT annotation stub (`NonExistentClass`).
  These match the baseline documented in the Android quality spec. App tests
  pass; no dependencies, examples, or test variants were changed to hide the
  unrelated failures. Log: `/tmp/thread-menu-cache-all-tests.log`.
- Parsed both floor menu XML files against `HEAD`: the normal menu retains 6
  entries and the reply menu 4, with exactly the requested removals and the same
  relative order. Verified standalone vote listeners, shared `menu_favorite`,
  and thread `menu_add_bookmark` remain wired.
- Verified cached-entry count and the 1-5/6+ sizing rule are applied before
  pager binding. `ArticlePagerAdapter`, both existing cache read/write models,
  `ArticleSearchFragment`, and `ArticleConvertFactory` are byte-unchanged.
- `git diff --check` passes.

## Review Correction and Final Gate

The reviewer found that `String.trim()` accepts a title made only of U+3000 or
U+00A0 as nonblank. The local correction uses the same code-point whitespace
predicate already used by `ArticleSelectionText`. Four existing test groups were
extended with those values and failed against the old helper, proving the gap.
They passed after the correction.

Final app gate:

```bash
./gradlew :nga_phone_base_3.0:assembleDebug :nga_phone_base_3.0:testDebugUnitTest :nga_phone_base_3.0:lintDebug --console=plain
```

- Exit 0, `BUILD SUCCESSFUL in 23s`, 552 tasks / 19 executed.
- Final XML: 24 app suites, 155 tests, zero failures/errors/skips; all 14 cache
  tests pass with the extended Unicode inputs.
- Final lint audit: all 13 modules have zero Error/Fatal. The other 12 modules
  are unchanged since the fresh all-module lint gate.
- Log: `/tmp/thread-menu-cache-reviewed-app-gate.log`.
- Independent reviewer: no remaining task defects. Full scope included both
  menus and shared actions, pagination boundary/sparse mapping, notification
  navigation, cache eligibility, description compatibility, and page snapshots.
- Updated specs agree with the final implementation. Local links and whitespace
  checks pass.

## Delivery Scope

The user explicitly requested commits, finish-work, and push. The work commit
`6203dad5` contains the eight product/test files and four updated/new spec files. Task
artifacts are committed by the task's own archive step, followed by the journal
commit. The pre-existing upstream-review task remains outside all three commits.
Delivery targets `origin/fix/thread-menu-cache`; no merge or release is implied.
