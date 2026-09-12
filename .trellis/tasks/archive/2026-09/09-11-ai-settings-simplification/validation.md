# Validation Record — 2026-09-11

## Scope and User Overrides

All product work belongs to `feature/ai-summary` in
`/home/toph/nga-just-works-ai-summary`. The main checkout was not edited.
The resumed task includes the seven AI-settings changes and the later
profile-only `AI查成分` rename. Both summary prompts and input scopes remain
unchanged.

The user explicitly authorized a bounded live test with a temporary model-service
Key, then declined further local builds. That instruction supersedes the remaining
local Gradle/lint gates for this task. Commit, finish-work, and push were explicitly
authorized afterward. No device operations or live NGA traffic were performed.

## Completed Checks

| Check | Result |
| --- | --- |
| Trellis review of all 14 AI-settings source/resource/test files | Passed; no task-owned defect or corrective source edit required |
| Focused AI/settings Gradle tests | 122 tests in 13 suites; 0 failures, errors, or skips |
| Java/Kotlin main and unit-test compilation in completed Gradle tasks | Passed |
| Full application JVM tests | 263 completed: 259 passed and 4 unchanged workflow-contract baseline failures |
| Profile menu/dialog rename | Both resolve to exactly `AI查成分`; floor labels and prompts unchanged |
| Resource checks for the rename | Four XML files parsed; resource references verified |
| Final Trellis resource/spec/task review | Passed under the user's validation override |
| Whitespace checks | `git diff --check` passed |

The focused invocation was:

```text
./gradlew :nga_phone_base_3.0:testDebugUnitTest --tests 'sp.phone.ai.*' --tests 'sp.phone.ui.fragment.Ai*Test' --console=plain
```

The combined application invocation was:

```text
./gradlew :nga_phone_base_3.0:assembleDebug :nga_phone_base_3.0:testDebugUnitTest :nga_phone_base_3.0:assembleDebugAndroidTest --console=plain
```

Its unit-test failure stopped the combined gate before APK assembly completed.
The user then declined additional local builds. No further Gradle tasks were
started; the reviewer also stopped its own idle Gradle daemon.

Local evidence (temporary, outside the repository):

- `/tmp/ai-settings-review.x4TjDQ/01-focused.log`
- `/tmp/ai-settings-review.x4TjDQ/01-focused-test-summary.json`
- `/tmp/ai-settings-review.x4TjDQ/02-app-gate.log`

## Existing Workflow-Test Failures

`ReleaseWorkflowContractTest.kt` and `.github/workflows/build.yml` both exactly
match the pre-task HEAD. Four source-string assertions are stale:

- Line 68 expects an old `else` preview branch; the workflow uses `elif`.
- Line 134 expects a literal `debug-` assignment; the workflow derives a channel
  prefix.
- Line 164 expects jq `startswith(...)`; cleanup uses Bash patterns.
- Line 232 expects the former debug-prerelease cleanup step name; the current
  name refers to channel prereleases.

These unrelated assertions were not changed. The existing feature-branch push
workflow assembles the preview variant and does not invoke this JVM test suite.

## Authorized Live Model-Service Check

A temporary standalone harness compiled the current `AiConfig`, `AiError`,
`SafeJsonParser`, `AiResponseParser`, and `AiSummaryClient` source with Java 17,
OkHttp 4.12.0, Okio JVM 3.6.0, Kotlin stdlib 2.0.21, and the checked-in Fastjson
version, 1.1.71.android. The temporary Key was passed through hidden input and
child-process stdin; it was not written to a source file, command-line argument,
task artifact, or test log.

- The supplied bare service base produced `INVALID_RESPONSE` for model discovery.
- Using the same service's `/v1` base successfully returned 149 validated models.
- `testConnection` with `gemini-3.1-flash-lite` and the existing 8-token budget
  succeeded; the four-character reply matched the expected connection-success
  text.
- Two discovery operations and one short connection operation were performed.
  No forum content or account data was sent to the service.

The usable `/v1` address was reported directly to the user. Private service
coordinates and the Key are deliberately omitted from repository artifacts.
Nonsecret outcome evidence remains at
`/tmp/ai-settings-live-v0v1ki6k/live-check.log`.

## Not Run / Not Completed

- Debug APK assembly and instrumentation APK assembly did not complete.
- All-module lint and repository-wide JVM diagnostics were not run after the
  user's explicit stop instruction.
- Device installation, instrumentation execution, and device UI checks were not
  run per project policy and were not requested.

No build, lint, or device success is claimed for these omitted checks.
