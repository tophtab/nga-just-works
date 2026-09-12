# Execution and validation

1. Read the PRD, design, baseline and metadata reports, and curated spec context.
2. Restore body resource retention for real same-page refreshes. Preserve
   changing HTML, row identity/context, variable counts, and lifecycle cleanup.
3. Remove the metadata blank between response deliveries. Keep session
   invalidation observable throughout deferred replacement and use metadata-only
   text updates that avoid reassigning unchanged displayed values.
4. Add executable regressions for equal fresh responses, changed HTML/rows,
   growing/shrinking/reordered pages, context changes and disposal; metadata
   cache handoff, deferred replacement races, invalidation, expiry, new authors,
   READY/cache-only replay and close. Cover synchronous close/null-reset before
   subscription return with an idle request slot. Update existing wiring tests
   as needed.
5. Run focused tests, then independent Trellis check and the full debug gate.
6. Correct the old reload explanation and document the new contracts in specs.
7. Commit task-owned changes, archive with finish-work, record the journal and
   push the branch, as explicitly requested.

## Agent ownership

The implement agent owns product/test changes to the article adapter and narrow
body-retention helpers, the author-location delivery shell/controller, and
their directly related tests. The repository's package-private registration
callback belongs to the same delivery boundary; its public API, transport, TTL,
pacing and stop policy remain unchanged. A separate body-test worker owns
`ArticleBodyViewsTest.java`. The main session owns task/spec documentation.
The check agent reviews/fixes the complete owned diff after implementation.
Only one agent runs Gradle at a time; announce and release build ownership.
Agents must preserve all other edits and must not commit or push themselves.

## Gate

Use `--console=plain` and keep reports under this task's validation record.
Focused host-JVM tests may run first while implementing. The final check runs:

```sh
./gradlew :nga_phone_base_3.0:assembleDebug testDebugUnitTest --continue --console=plain
./gradlew lintDebug --continue --rerun-tasks --console=plain
```

Inspect every module's JUnit reports (nonzero expected tests, no failures/errors/
skips) and all 13 lint XML reports (zero Error/Fatal). These aggregate commands
include the application unit/lint requirements. Preserve and report unrelated
workspace inputs; never stage them in this task's commits. No ADB, installations,
connected tests, device probes, or live NGA requests.
