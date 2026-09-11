# Validation

## Final Implementation

- Both summary actions stream answer/reasoning projections into the shared
  transient dialog. Reasoning starts folded; copy uses the complete answer only.
- Both prompts contain `回复正文在1000字以内`. The client does not truncate
  generated answers to that length.
- Summary and connection-test requests use the final user-selected
  `max_tokens: 10000`, with default thinking and no separate reasoning budget.
- Empty, exhausted, interrupted, malformed, and oversized responses are distinct
  failures. Partial content survives terminal coalescing, buffered-prefix
  screening, and a later UTF-8 error. Cancellation remains generation-owned.

## Executed Checks

- Independent full-scope Trellis check completed with no remaining concrete
  source findings. Reviewed transport, decoding, controller ownership, UI, prompts,
  synthetic regressions, resource limits, and credential isolation.
- Static Java delimiter/test-name checks passed for all 15 changed/new Java files.
- Both modified XML files parsed; resource references and fold/copy wiring passed.
- Request-field, prompt, timeout, and resource-limit source checks passed.
- `git diff --check` passed.
- The supplied endpoint/Key were absent from all changed tracked/untracked files.
  Their only configuration file is ignored under `.temp`, with mode `0600`;
  `.temp` has no tracked files. Captured data and response text remain private.
- Task JSON and curated context references validated before archive.
- Remote branch preflight showed no divergence from the existing worktree base.

## Actual Model Request

The one user-requested final probe used `glm-5.3-flash`, the current profile
prompt and captured first-page sample, genuine SSE, and App-style endpoint
normalization. It sent neither a token cap nor a thinking override. HTTP 200,
`stop`, and `[DONE]` confirmed normal completion in 49.05 seconds with a
617-character body. The recorded usage was 2,680 input tokens and 3,876 output
tokens, totaling 6,556 by addition. A separate reasoning-token count was not
recorded and cannot be derived precisely from character counts.

This successful request preceded the final 10,000-token decision. It is not a
live test of that later parameter or the Android client itself. Earlier probes
also showed reasoning-only exhaustion; success is not guaranteed for every call.
See `research/model-response-evidence.md` and `research/reference-token-limits.md`.

## Explicitly Unexecuted

Per the retained user restriction, no Gradle/lint, Java compilation, JVM/JUnit
execution, APK build, device operation, or remote workflow inspection ran.
The added and updated parser, fake-server, controller, input, and UI-contract
tests were reviewed statically but not executed. No Android runtime or APK pass
is claimed. These are disclosed validation limits, not a request for the user
to perform an additional acceptance step before handoff.

The user explicitly authorized commit, `finish-work`, and branch push after the
permitted checks. Those operations are the final handoff steps.
