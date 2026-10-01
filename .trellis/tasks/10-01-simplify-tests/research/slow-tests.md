# Slow loopback network suites

## Changes and coverage

- `AiModelsClientTest`: remove `statusesUseFixedErrorsWithoutExposingResponseBodies`, which repeats the status/error/no-body-leak matrix already executed by `failedFallbackUsesExistingStatusErrorsWithoutAnotherAttempt`; `rootNonPathFailuresDoNotFallBackEvenWithHtmlBodies` and `explicitCustomAndEncodedPrefixesNeverFallBack` additionally retain root/custom-route behavior. This removes one method containing eight redundant loopback requests, not unique status coverage. Keep fallback deadline, cancellation, credentials, redirect, retry, parsing and size-limit cases. Delayed-body fixtures now wait 1 s rather than 2 s against the same 500 ms deadline.
- `AiSummaryClientTest`: retain all 25 methods and all assertions. Reduce success-stream progress throttling from 2 s to 500 ms; timeout fixtures from 2 s to 1 s while retaining the 500 ms client deadline. After reviewer feedback, cancellation uses a bounded progress-callback latch instead of any throttle: the test cancels before releasing the callback to consume the terminal event, avoiding a scheduler-dependent race. This reduces server-side throttling/shutdown waits without increasing client timeouts or loosening expectations.
- `NgaProfilePageSourceTest`: retain all 36 methods and assertions. Six transport-only scenarios now inject their own queue whose clock advances immediately when a scheduled cooldown is due. Real loopback I/O, deadline/cancellation behavior, parser errors and HTTP handling still execute. These cases no longer pay the production shared queue's 500 ms spacing between fixtures or inherit cooldown from another test. Explicit FakeTime tests above them still verify actual 499/500 ms boundaries, shared serialization, cancellation, retries and callback ordering. The raw 429 retry metadata test is retained because it covers transport-hook preservation.

No production files, Gradle exclusions, dependencies, device operations or external HTTP requests changed. Test count in these suites: 87 → 86.

## Targeted validation

Command:

```bash
/usr/bin/time -p ./gradlew :nga_phone_base_3.0:testDebugUnitTest \
  --tests sp.phone.ai.AiModelsClientTest \
  --tests sp.phone.ai.AiSummaryClientTest \
  --tests sp.phone.ai.summary.NgaProfilePageSourceTest --console=plain
```

Passed: 86 tests, zero failures/errors/skips. 223 actionable Gradle tasks, 6 executed and 217 up-to-date; wall time 57.94 s, including compilation. Log: `slow-tests-gradle.log`. Scoped `git diff --check` passed.

| Suite | Previous full-gate XML seconds | Targeted XML seconds |
| --- | ---: | ---: |
| AiModelsClientTest | 11.772 | 8.116 |
| AiSummaryClientTest | 13.842 | 5.595 |
| NgaProfilePageSourceTest | 14.547 | 1.972 |
| Total | 40.161 | 15.683 |

Observed suite execution decreased by 24.478 s (~61%) in this local sample. This is not a controlled benchmark or a claim that the whole Gradle workflow is 61% faster; JVM startup/order and system load differ. The direct improvement is removing artificial waits, especially profile cooldowns (14.547 → 1.972 s). These targeted figures precede the deterministic cancellation-latch improvement. Main session owns the subsequent combined full gate and independent review.

## Cancellation review follow-up

The independent reviewer accepted the callback latch as deterministic synchronization. After the main session's successful combined repository gate, reran only `sp.phone.ai.AiSummaryClientTest.cancellingAStreamAfterProgressCannotPublishSuccess` with the same module test task and `--tests` filter. Passed: one test, zero failures/errors/skips. Log: `cancellation-test-gradle.log`. Scoped `git diff --check` also passed. This targeted rerun overwrites the application's XML with one case; the main session captured full-gate totals beforehand.
