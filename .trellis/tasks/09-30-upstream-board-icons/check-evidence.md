# U1 implementation check evidence

Date: 2026-10-01. Worktree `/home/toph/nga-just-works-upstream-adoption`, baseline `557f7bea`.
Original implementer evidence updated after the independent reviewer fix. Independent review and root cause: `check.md`.

## Executed commands

```bash
./gradlew :nga_phone_base_3.0:testDebugUnitTest --tests 'gov.anzong.androidnga.activity.compose.board.*' --console=plain
./gradlew :nga_phone_base_3.0:assembleDebug testDebugUnitTest --continue --console=plain
./gradlew lintDebug --continue --rerun-tasks --console=plain
git diff --check
```

- Focused board gate (`/tmp/u1-review-focused.log`): passed, 35 tests, zero failures/errors/skips (3 resolver, 10 icon refresh, 10 bookmark persistence, 6 home order, 6 home order contracts).
- Final Debug assemble + all-module unit gate: passed, 740 tests, zero failures/errors/skips. Final reviewer rerun includes malformed category/member validation and its regression. Log `/tmp/u1-review-debug.log` reports `BUILD SUCCESSFUL in 43s`, 360 tasks (4 executed, 356 up-to-date).
- Forced all-module lint: passed, all 13 XML reports present, 0 Error / 0 Fatal. `BUILD SUCCESSFUL in 46s`, all 536 tasks executed. Log `/tmp/u1-review-lint.log`.
- `git diff --check`: passed after independent reviewer fix.

## Unit XML inspection

Read `build/test-results/testDebugUnitTest/TEST-*.xml` in every module from `settings.gradle`. All 13 modules have nonzero executed test counts, no failures/errors/skips:

| Module | Tests |
| --- | ---: |
| lib_bu_statistics | 1 |
| nga_phone_base_3.0 | 651 |
| lib_core | 8 |
| lib_base_logger | 1 |
| lib_base_common | 63 |
| lib_core_data | 1 |
| lib_bu_message | 1 |
| lib_base_network | 1 |
| lib_base_service_api | 1 |
| lib_bu_account | 1 |
| lib_base_ui_compose | 9 |
| lib_base_ui | 1 |
| lib_module_debug | 1 |
| **Total** | **740** |

## Lint XML inspection

Every `build/reports/lint-results-debug.xml` was read after the forced run completed. Warning totals are diagnostic, not blocking:

| Module | Warnings | Error/Fatal |
| --- | ---: | ---: |
| lib_bu_statistics | 6 | 0 / 0 |
| nga_phone_base_3.0 | 728 | 0 / 0 |
| lib_core | 8 | 0 / 0 |
| lib_base_logger | 8 | 0 / 0 |
| lib_base_common | 39 | 0 / 0 |
| lib_core_data | 1 | 0 / 0 |
| lib_bu_message | 11 | 0 / 0 |
| lib_base_network | 7 | 0 / 0 |
| lib_base_service_api | 2 | 0 / 0 |
| lib_bu_account | 6 | 0 / 0 |
| lib_base_ui_compose | 12 | 0 / 0 |
| lib_base_ui | 8 | 0 / 0 |
| lib_module_debug | 4 | 0 / 0 |

## Limits

Device operations not run per project policy. No live NGA/media requests, signed APK builds, release/preview task graph, install, screenshot, publication, or account operation. JVM/state/serializer and static UI evidence does not prove device pixels, gestures, CDN availability, or full model/Activity lifecycle behavior on hardware.
