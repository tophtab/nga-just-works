# Actual execution update

Parent executed the isolated build and selected tests on 2026-10-02:
25 tests across 7 suites passed, zero failed/skipped. See
[live-test summary](lnga-live-test-summary.md) for conditions and limits.
The earlier inspection-only notes below preceded this execution.

# Research: LNGA offline App HTML validation

- Query: Can LNGA signed HTML reading be shown effective through existing tests?
- Scope: external source, offline fixtures; no live NGA requests
- Date: 2026-10-02
- Pinned checkout: `/tmp/nga-api-comparison-WnvQGx/lnga_harmony`, HEAD `df6cfcc2e4cfc2efa95269aac398707e9d4d3e81`.

## Findings

Execution delegated to the parent because this research role may write only inside the task research directory. No temporary checkout, dependency cache, or compiler output was written outside that scope by this researcher. Parent-run execution results will be incorporated when available. This is not a claim that tests passed.

### Files found and code patterns

Paths below are relative to LNGA checkout:

- `tools/bbcode-ts/package.json:7`: build invokes TypeScript; tests build then run Node tests. Two development dependencies, TypeScript and Node type definitions. Fetch, inspect and sync scripts exist and must not be run for this offline experiment.
- `tools/bbcode-ts/tests/app-html-parser.test.ts:63`: four synthetic tests cover sticky-comment pseudo-floor isolation, fixed pagination argument positions, main-page topic author/time, and later-page missing author/time without falsely using the first visible reply.
- `tools/bbcode-ts/tests/app-html-mode-coverage.test.ts:6`: visible text coverage threshold is 90%, not exact parity.
- `tools/bbcode-ts/tests/app-html-mode-coverage.test.ts:9`: nine strict reply metadata fields. Attachments, hot replies, rich structures and all optional metadata are not asserted by this suite.
- `tools/bbcode-ts/tests/app-html-mode-coverage.test.ts:22`: existence guard skips when fixture list is empty; a green run must verify nonempty sample count and skips.
- `tools/bbcode-ts/tests/app-html-mode-coverage.test.ts:59`: topic author can be unavailable (null comparison), and only intersecting-user username mismatches are rejected.
- `tools/bbcode-ts/tests/html-coverage.ts:34`: fixtures are located relative to process working directory, so execution must use `tools/bbcode-ts` root.
- `tools/bbcode-ts/tests/html-coverage.ts:42,66`: sample enumeration/loading reads local files; no request operation.
- `tools/bbcode-ts/tests/html-coverage.ts:218,413`: comparison can apply explicit floor-gap maps and uses actual `lou` rather than JSON page-local array keys.
- `tools/bbcode-ts/samples/app-html-pairs.lst:1`: five pairs, two threads; one first page and one four-page thread. These are historical successful response samples, not current HTTP availability tests.

### Reproduction command shape

Parent should make an isolated temporary copy of `tools/bbcode-ts`, preserving local samples without printing their contents; then from that copy:

```sh
npm ci --ignore-scripts --no-audit --no-fund
npm run build --ignore-scripts
node --test dist/tests/app-html-parser.test.js dist/tests/app-html-mode-coverage.test.js
```

Do not invoke fetch, inspect, probe, snapshot or sync commands. Install lifecycle scripts are disabled. Compiler/tests need no NGA traffic. Capture only result totals and aggregate numeric coverage; failure messages can contain response field values and should be redacted before persistence.

Fixture filesystem check completed: all 10 response files exist; none of the five App pairs has an active `rowShift` map in `html-pair-gaps.json`. No raw sample contents were output.

Expected test inventory from static inspection: 25 tests in 7 suites (4 synthetic tests + 1 existence test + 5 pairs × 4 tests). Expected inventory is not a run result.

## External references

- https://github.com/apap6628114/lnga_harmony
- Existing study: [LNGA signed HTML feasibility](lnga-signed-html-feasibility.md)

## Related specs

- `.trellis/spec/backend/nga-platform-access-rules.md`: local fixtures and explicit scope for live testing; no secret/raw-response persistence.
- `.trellis/spec/backend/ordinary-read-wire-contract.md`: missing fields, global vs page-local row identity, and source validity must remain explicit when adapting to this Android client.

## Caveats / Not Found

- Offline parser tests do not exercise the live signature, transport, account state, request acceptance, HTTP status, or Android renderer.
- Passing historical samples cannot establish fewer 403 responses, nor a GET-vs-POST effect.
- No actual failing user thread or named account was provided in this subtask.
- No product code changed; no credentials loaded; no NGA or media requests issued by this researcher.
