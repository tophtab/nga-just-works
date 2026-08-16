# Implementation Plan

1. Load frontend specifications and inspect the exact Compose/resource APIs used by each target file.
2. Update the navigation drawer and message detail semantic colors.
3. Update search field/history surfaces and content colors while preserving behavior.
4. Add light/night legacy editor colors and update the two XML layouts.
5. Add focused source/resource contract coverage for the new theme bindings.
6. Run formatting/compilation and targeted JVM tests; inspect the final diff for scope drift.
7. Run the Trellis quality check and record any residual device-only risk.

Validation commands:

```text
./gradlew :nga_phone_base_3.0:testDebugUnitTest --tests gov.anzong.androidnga.SystemThemeContractTest
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

Rollback points: Compose files, legacy resource definitions, and tests are separate change groups. Revert one group without reverting the others if verification identifies a regression.

## Validation Outcome

- Targeted `SystemThemeContractTest`, Compose/Kotlin compilation, and `assembleDebug` passed.
- `:nga_phone_base_3.0:lintDebug` and `:lib_bu_message:lintDebug` passed; only the existing ButterKnife lint registry warning was reported.
- Repository-wide `testDebugUnitTest` remains blocked by unrelated baseline failures in `lib_bu_statistics` (missing JUnit dependency in its example test) and `lib_core` (`ExampleUnitTest.testQuote`).
