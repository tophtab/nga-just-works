# Approved repair execution plan

## Completed diagnosis

- [x] Locate the AI feature worktree and confirmed UI error source.
- [x] Execute the current focused AI JVM suite: 204 tests, zero failures.
- [x] Independently review native reader compatibility and fixture gaps.
- [x] Reproduce literal TAB/LF failures with current compiled production code.
- [x] Obtain explicit bounded live-read authorization and reproduce the current
  error using two requests; stop after the first failing detail.
- [x] Verify the same failing response succeeds after in-memory control
  escaping, with original-post identity validation unchanged.
- [x] Persist sanitized evidence and the minimal proposed repair boundary.

## Product repair and main integration

- [x] Review the concrete repair proposal with the maintainer before Phase 2.
  The subsequent request explicitly approves repair and merge into main.
- [x] Fast-forward the feature worktree to the integrated `main@7cb9e50b` base.
- [x] Refresh task status and relevant spec context, then start the task after
  the recorded repair approval. Context validation passed (5 implement entries,
  7 check entries). The AI-summary and Android-quality specs exceed the native
  injection file-size cap; each worker must read their relevant complete
  sections directly rather than relying only on injected excerpts.
- [x] Dispatch the implementation agent with ownership of the local topic-body
  normalizer and its existing test class. Add a raw-wire regression that fails
  on the baseline before implementing the correction.
- [x] Implement the local conversion; preserve existing escape and identity
  semantics, body text, and bounds.
- [x] Run the focused AI suite, including original and new failing cases.
  Four baseline failures are resolved; all 211 focused tests pass.
- [x] Run the applicable Android quality gate: app debug assembly, app JVM tests,
  app lint, all-module lint with XML Error/Fatal inspection, and repository
  debug unit tests. The final reports cover 600 tests across 13 modules with
  zero failures/errors/skips; all 13 lint reports have zero Error/Fatal issues.
- [x] Build the app Android-test APK as required by the AI contract. No device
  installation or instrumentation execution was performed.
- [x] Dispatch the independent check agent over the full repair diff and test
  evidence. Full-scope review passed without findings or further code changes;
  see `independent-check.md`.
- [x] Update the AI-summary compatibility contract and task validation report.
- [ ] Commit the verified work and merge the correction into main; verify the
  merged product tree matches the checked tree, rerunning affected checks if
  integration changes code.
- [ ] After the correction is present in main, archive the completed task and
  record the journal, then bring main forward to include that bookkeeping.
- [ ] Present the commit, merge, and validation results. Separate release
  publication and device operations remain outside the authorized scope.

## Validation commands

```bash
./gradlew :nga_phone_base_3.0:testDebugUnitTest --tests 'sp.phone.ai.*' --tests 'sp.phone.ai.summary.*' --console=plain
./gradlew :nga_phone_base_3.0:assembleDebug :nga_phone_base_3.0:testDebugUnitTest :nga_phone_base_3.0:lintDebug --console=plain
./gradlew lintDebug --continue --rerun-tasks --console=plain
./gradlew testDebugUnitTest --continue --console=plain
./gradlew :nga_phone_base_3.0:assembleDebugAndroidTest --console=plain
git diff --check
```

Do not run concurrent Gradle invocations, ADB, connected tests, model requests,
or another live NGA probe as a default validation step. The explicit live
observation plan was for this diagnosis and has completed.
