# R1–R6 integration verification — 2026-09-30

All six approved requirements implemented and independently checked. No outstanding product findings. User approved the commit batch, finish-work, push and release. All six code changes are committed.

## Required command gate

| Command | Result |
| --- | --- |
| `./gradlew :nga_phone_base_3.0:assembleDebug` | Passed; /tmp/reader-r1-r6-assemble.log |
| `./gradlew :nga_phone_base_3.0:testDebugUnitTest` | Passed; /tmp/reader-r1-r6-app-tests.log |
| `./gradlew :nga_phone_base_3.0:lintDebug` | Passed; /tmp/reader-r1-r6-app-lint.log |
| `./gradlew lintDebug --continue --rerun-tasks --console=plain` | Passed; /tmp/reader-r1-r6-all-lint.log |
| `./gradlew testDebugUnitTest --continue` | Passed; /tmp/reader-r1-r6-all-tests.log |

## XML results

All 13 Android lint reports contain zero Error/Fatal; warnings remain visible. All 724 tests passed with zero failures, errors or skips. App: 635 tests.

| Module | Tests |
| --- | --- |
| lib_base_common | 63 |
| lib_base_logger | 1 |
| lib_base_network | 1 |
| lib_base_service_api | 1 |
| lib_base_ui | 1 |
| lib_base_ui_compose | 9 |
| lib_bu_account | 1 |
| lib_bu_message | 1 |
| lib_bu_statistics | 1 |
| lib_core | 8 |
| lib_core_data | 1 |
| lib_module_debug | 1 |
| nga_phone_base_3.0 | 635 |

## Acceptance evidence

- R1: supplied Via/type5 parser cases, invalid/conflicting values, separate scheme registration and detach-before-reset hot intake; platform chooser limitation retained.
- R2: thread owner, shared cancellation, synchronous fetch cancellation race, random pacing, tail retry, no timed third call and cache compatibility.
- R3: actual status/finite structured cause wording, neutral unknown HTML, preserved stop/recovery behavior and one error callback.
- R4: FULL query plus separate actual reply target, ordinary candidate/source-size alignment, missing-target notice and lifecycle-safe anchor consumption; R1 hot reset integrated.
- R5: all 238 mappings, corrected AC pair, filename order, main/legacy decoder compatibility.
- R6: source ranges/roundtrip, whole displayed-token replacement, async revision/ticket bounds, source restoration and no reupload wiring.

Each child verification.md records its focused checks. Final reviewer also examined all six code diffs together. `git diff --check` passed.

## Limits and excluded work

Device/ADB/IME/chooser runtime checks were not run per project policy. No live NGA, posting, installation, publication or diagnostic operations were performed in this implementation session. R7 product work remains deferred; its separate research archive and new upstream planning tasks are other-session work. Prior research completion is not a reader fix.

## Stable release preparation

6.1.0 notes validated; semantic versionCode 60100000. Release scripts: 36 tests passed. Signing/packaging/publication are owned by the existing GitHub Actions tag workflow; no local release APK packaging or device operation.
