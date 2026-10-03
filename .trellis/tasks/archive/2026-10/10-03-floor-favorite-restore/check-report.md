# Floor favorite restoration check

## Result

PASS for the approved restoration. No blocking product or specification issues were found in the task diff, and no checker fixes were needed. The checker did not change product code, specifications, PRD, design, or execution plan.

## Reviewed behavior

- Both restored XML entries match the title, ID and relative placement removed by `6203dad5`: ordinary menu after `menu_vote` and before `menu_show_this_person_only`; PID-context menu after `menu_ban_this_one` and before `menu_vote`.
- `ArticleListAdapter` tags the menu view with its bound row. `ArticleListFragment` captures that clicked row, restores it before dispatch, and derives `tidStr`/`pidStr` from its own IDs. Both menu branches reach the same `BookmarkTask.execute(tidStr, pidStr)` case, including PID-only reader entries.
- `BookmarkTask` retains its existing topic-favor request and response toast. The whole-thread action still uses `execute(int tid)`. No navigation, page-coordinate, preview, favorite removal, endpoint, parser, cache, or unrelated menu changes are present.
- The active floor-menu spec now documents the restoration while preserving support/oppose/signature exclusions. No new source-spelling tests are warranted for these existing wiring lines.

## Verification

Executed once:

```bash
./gradlew testDebugUnitTest lintDebug :nga_phone_base_3.0:assembleDebug --continue --console=plain
```

- Exit code 0; `BUILD SUCCESSFUL in 1m 36s`.
- 634 actionable tasks: 73 executed, 4 from cache, 557 up-to-date.
- Type check: Android Java/Kotlin compilation passed. This project has no separate type-check command for the changed Java/resources.
- All 13 Android-module lint XML reports exist and contain **0 Error / 0 Fatal**. Diagnostic totals: 821 warnings, 4 hints.
- JVM reports: **562 tests in 82 suites; 0 failures, 0 errors, 0 skipped**. Modules without test reports have no JVM test sources.
- `git diff --check` passed.
- Debug APK: `nga_phone_base_3.0/build/outputs/apk/debug/nga_phone_base_3.0-debug.apk`.
- Local build log: `/tmp/floor-favorite-restore-check-gradle.log`; parsed report counts: `/tmp/floor-favorite-restore-check-counts.json`.

| Module | JVM tests | Lint warnings | Lint hints |
| --- | ---: | ---: | ---: |
| nga_phone_base_3.0 | 507 | 736 | 1 |
| lib_core | 27 | 6 | 1 |
| lib_base_common | 26 | 26 | 0 |
| lib_bu_message | 1 | 12 | 2 |
| lib_base_ui_compose | 1 | 8 | 0 |
| lib_bu_statistics | No sources | 8 | 0 |
| lib_base_logger | No sources | 3 | 0 |
| lib_core_data | No sources | 0 | 0 |
| lib_base_network | No sources | 6 | 0 |
| lib_base_service_api | No sources | 1 | 0 |
| lib_bu_account | No sources | 5 | 0 |
| lib_base_ui | No sources | 7 | 0 |
| lib_module_debug | No sources | 3 | 0 |

Device checks were not run per project policy. No ADB, NGA request, live favorite mutation, signed Preview/Release build, commit, or push was performed. These local checks establish compilation and regression-suite health, not live server behavior.

## Nonblocking observations left unchanged

- Both restored literal `收藏` titles produce `HardcodedText` warnings. They preserve the explicitly approved original XML and locale-independent label; moving them to translated resources would be a separate localization choice.
- The restored switch case produces the same `NonConstantResourceId` warning as existing cases. `gradle.properties` explicitly sets `android.nonFinalResIds=false`, and the actual compilation passed. A resource-ID or switch migration is outside this restoration.
- Pre-existing documentation inconsistency outside this task: `component-guidelines.md`'s compatibility-reader matrix still describes `显示全部` opening an ordinary floor candidate and verifying PID, while its contract states page 1 without an original-reply anchor. Both statements were already present in HEAD. Recommend aligning the matrix with the authoritative navigation contract in a navigation/spec maintenance change; no navigation behavior or requirement was changed here.
