# Validation: stable article refresh

## Accepted behavior

Fresh same-page responses retain applicable body resources and continue to
bind real HTML/image changes through the existing `LocalWebView` equality
guard. Removed, ambiguous or incompatible rows release their resources.
Current cached author locations survive subscription replacement without an
artificial blank. Metadata invalidation remains connected, unchanged drawn
text avoids redundant assignment, and synchronous close/null-reset can retire
a subscription before it starts a request.

The independent review and full gate are recorded in [check-results.md](./check-results.md).
The focused implementation gate is recorded in
[implementation-results.md](./implementation-results.md): 103 tests in seven
suites and the app debug assembly passed after the registration-hook fix.

## Final repository gate

```sh
./gradlew :nga_phone_base_3.0:assembleDebug testDebugUnitTest --continue --console=plain
./gradlew lintDebug --continue --rerun-tasks --console=plain
```

- Debug assembly and JVM gate: passed in 41 seconds, exit code 0.
- JUnit reports: 13 modules, 79 suites, **677 tests**; zero failures, errors or
  skips. The app contributes 592 tests, including 25 body-owner and 18
  page-controller tests.
- Forced lint: passed in 51 seconds; all 536 tasks executed.
- Lint XML reports: all 13 generated during this gate, zero Error/Fatal,
  835 Warning and four Information entries. This is not a zero-warning claim.
- Whitespace: `git diff --check` passed.

Raw logs and machine-readable report summaries are under
`/tmp/reader-refresh-final-check-jmxo9fkb/`. The committed independent review
records the selected input manifest and before/after comparison. Selection
includes tracked and untracked module sources/resources/tests, dependencies,
Gradle inputs and module lint rules, while excluding generated build output and
task documentation.

## Scope and limits

No device probing, installation, instrumentation or live NGA traffic was used.
Host tests execute the real resource owner, delivery controller and repository;
they do not render Android WebViews or measure device frame timing. The change
restores the 5.6.1 retention behavior in the current reader, without claiming a
measured device smoothness comparison. Genuine HTML changes, expired/invalid
metadata and conservatively unmatched rows still receive real updates.

The task is a direct fix on shared `main`, with no PR. The user explicitly
authorized commit, finish-work and push. The prior READY-entry fix remains;
unrelated AI-profile work was committed separately before this final gate.
