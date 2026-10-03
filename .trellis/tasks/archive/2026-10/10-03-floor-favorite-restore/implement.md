# Floor favorite restoration execution

## Reviewed planning

- [x] Complete issue, screenshot, original behavior, and PC evidence research.
- [x] Explain the original button/write behavior and distinguish navigation enhancements.
- [x] User approves restoring that original action only.
- [x] Converge PRD/design and curate implement/check context around the three-file restoration.
- [x] Activate task and dispatch the Trellis implementation agent.

## Implementation and checks

- [x] Restore both original menu entries and the shared `BookmarkTask.execute(tidStr, pidStr)` handler.
- [x] Update frontend floor-menu spec without changing other menu requirements.
- [x] Review the complete diff against original positions, captured clicked-row IDs, and scope.
- [x] Pass app compilation and lint; run repository Debug unit tests and lint, inspect XML reports for failures/errors.
- [x] Run final Trellis check over all task changes and resolve mechanical issues.
- [x] Record verification and update acceptance criteria.
- [x] Present the task-only commit plan, obtain user confirmation, and commit work as `090dce71`; archive and journal follow via finish-work.

## Validation commands

Implementation was reviewed without starting a separate Gradle build; the checker owns the single complete gate. The following command is only for diagnosing compilation independently if needed:

```bash
./gradlew :nga_phone_base_3.0:compileDebugJavaWithJavac :nga_phone_base_3.0:lintDebug --console=plain
```

Final gate:

```bash
./gradlew testDebugUnitTest lintDebug :nga_phone_base_3.0:assembleDebug --continue --console=plain
```

Inspect generated unit-test XML and all Android-module lint XML. Reuse valid results from the same unchanged diff rather than rerunning passing checks. No standalone type checker exists for these Java/resource edits; Android Java/Kotlin compilation is the type check.

No fresh source-string test is needed for this small restoration. No device or NGA read/write operation is authorized. The user subsequently authorized stable 6.2.5 publication through existing GitHub CI; local Release/Preview packaging remains excluded. Existing unrelated task `10-02-nga-client-api-comparison` must not be staged or edited.

## Rollback

Only revert the restoration lines in the three product files and their active spec update; preserve the earlier cache fixes, other menu changes, and all stored/server favorite data.

## Verification result

The full Debug gate passed: 562 JVM tests across 82 suites, zero failures/errors/skips; 13 Android-module lint reports with zero Error/Fatal; Debug compilation and APK packaging passed. See [check-report.md](check-report.md). Original-code lint warnings are documented there and are not blocking. No product changes followed this gate.

Implementation is complete and committed as `090dce71` after explicit confirmation. The user also authorized stable 6.2.5; release notes and local `verifyReleaseTag` with versionCode `60205000` passed. Finish-work archives/journals before fast-forward integration and main/tag push.
