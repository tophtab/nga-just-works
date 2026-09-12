# Implementation Plan

1. [x] Reproduce failures and record the final explicit user requirements.
2. [x] Transport owner: update client/parser/errors and focused fake-server and
   parser tests, using the callback contract in design.md.
3. [x] UI owner: update controller, dialog, XML/strings, and focused controller/UI
   tests. Keep changes independent of transport implementation internals.
4. [x] Main: update both prompts and their input tests; integrate all changes.
5. [x] Main: update the AI spec and run the user-requested streaming protocol
   probe against the same captured sample; report actual token usage. The
   successful no-cap probe predates the final 10,000-token parameter choice.
6. [x] Check agent: independently review complete scope, cancellation, framing,
   first-choice selection, completion/error semantics, resource bounds, complete
   output over 1,000 characters, copy separation, and secret isolation.
7. [x] Run permitted static checks: git diff --check, XML parsing, source/fixture
   consistency, and secret-value scanning. No Gradle/Java compilation, APK, device,
   or remote workflow checks. Added JVM tests must be labeled unexecuted.
8. [x] Record actual verification in `validation.md` and the user's authorization
   for the subsequent work commit, task archive/journal, and branch push.

The main integration risk is progress/terminal state interaction. Keep the new
progress callback defaulted; preserve input-source and settings interfaces.
No code outside the reviewed transport, shared UI, prompts, tests, and spec is
needed. Return to the plan before broadening those boundaries.

The full-scope check also owns the final user-selected `max_tokens: 10000`
parameter and its request regressions, plus any local parser/terminal fixes.
This supersedes earlier assertions that all request token fields were absent.
