# Independent review: native topic-detail string controls

Date: 2026-09-12. Reviewer: `trellis-check`.
Worktree: `/home/toph/nga-just-works-ai-summary`, `feature/ai-summary`.
Reviewed baseline: `7cb9e50b3fb6a0b51f5435abaedaef1d0b5ff436` (integrated main).

The independent review found no issues. The checked repair and its completed
offline quality gate are ready for the parent-owned commit and main merge.

## Findings (fixed)

None. The reviewer changed only this report.

## Findings (not fixed)

None. No correctness, regression-coverage, contract, or scope issue was found.

## Review scope and contract checks

The reviewer explicitly loaded this worktree's PRD, design, execution plan,
`check.jsonl` and its referenced files, backend index, complete AI-summary and
Android-quality contracts, platform access rules, and diagnostic evidence.
The review covered the complete product/test/spec diff, implementation handoff,
retrospective, and integration plan. Source tracing included the unchanged
shared decoder, NGA source, profile loader, and prompt composition.

- `NgaTopicBodyParser.appendQuoted` tracks escapes from the original input.
  Consecutive backslashes retain their parity: an even pair permits a following
  raw TAB/LF/CR to be escaped, while an unmatched backslash before one of those
  controls fails. Existing escaped controls, Unicode escapes, quotes,
  backslashes, and literal backslash-plus-letter text retain their decoded
  values. Quoted JS/error-fill markers stay inside the string scanner.
- Only literal TAB/LF/CR receive the new representation repair. Other raw C0
  string characters remain visible to the unchanged strict preflight and fail.
  The malformed-escape regressions include unknown escapes, incomplete or
  invalid Unicode escapes, unterminated strings, and raw-control/backslash
  combinations. The shared model-parser raw-newline rejection still passes.
- The raw-input limit and normalized-output limit remain 524,288 characters
  at the parser boundary; the network byte limit remains separate and unchanged.
  Each expanded string character is counted before shared parsing. The tests
  accept the exact normalized limit and reject both its next character and a
  densely populated raw-TAB string without clipping.
- Normalization still precedes the existing requested-topic, viewed-author,
  explicit-floor-zero, and original-content checks. Only the verified original
  reaches `Entry.withBody`, then profile composition. The original selection,
  1,200-character cleaned-body bound, source-processing and prompt bounds,
  sequential request budget, cancellation, and late-callback paths are unchanged
  and retain their existing regression coverage.
- New fixtures inject controls after JSON serialization. Their paired escaped
  cases and exact decoded-text assertions exercise the source representation
  defect. The saved pre-repair run fails exactly four expected success cases;
  the rejection regression already passes. The strengthened GBK loopback test
  completes topic-list/detail/reply collection in three requests and excludes
  ignored metadata, session sentinels, fetch IDs, and other floors from the
  prompt while preserving the cleaned original and reply.
- The AI-summary contract records the local ownership, supported characters,
  escape distinction, malformed-input behavior, expansion limit, failure matrix,
  and required raw-wire regressions. No platform configuration or generated
  template touchpoint is affected by this parser-only product change.

## Verification

The implementer ran Gradle serially. This reviewer independently inspected the
saved command outcomes, focused XML snapshots, every current module lint and
Debug unit-test XML report, and Android-test APK output metadata. No duplicate
Gradle invocation was needed.

Evidence stems below are under `.temp/ai-profile-content-format/`, with `.log`
and `-summary.json` files. The complete command list is in
`research/implementation-handoff.md`.

| Check | Result | Evidence stem |
| --- | --- | --- |
| Lint | Pass: all 13 module reports present, 0 Error / 0 Fatal; 835 warnings; all 536 tasks rerun | `all-module-lint` |
| TypeCheck / compilation | Pass: Java/Kotlin Debug compilation, app assembly, unit-test compilation, and Android-test Java compilation | `app-debug-gate`, `all-module-lint`, `android-test-apk-build` |
| Focused regression before repair | Expected red: 211 tests / 13 classes, exactly 4 failures, 0 errors/skips | `focused-red` |
| Focused regression after repair | Pass: 211 tests / 13 classes, 0 failures/errors/skips | `focused-green` |
| Full app JVM suite | Pass: 515 tests / 58 classes, 0 failures/errors/skips | `app-debug-gate` |
| Repository Debug JVM suite | Pass: 600 tests / 76 classes / 13 modules, 0 failures/errors/skips | `repository-debug-tests` |
| Android-test APK build | Pass: output metadata names a present 811,135-byte APK | `android-test-apk-build` |
| Whitespace check | Pass: `git diff --check`; new report checked separately | Reviewer command |

The repository aggregate reused eligible `FROM-CACHE` and `UP-TO-DATE` results,
including the preceding successful full app run. The 600-test total describes
the inspected reports, not 600 fresh executions in that final invocation.
Every module has a nonzero report, including one passing test each in
`lib_bu_statistics` and `lib_module_debug`.

The test APK is
`nga_phone_base_3.0/build/outputs/apk/androidTest/debug/nga_phone_base_3.0-debug-androidTest.apk`.
Device instrumentation was not run per project policy. This review made no live
NGA/model request, accessed no saved credentials or personal samples, and does
not infer device or live-service behavior from the offline results. The earlier
two-request diagnosis remains separately recorded in `research/live-result.md`.

## Integration handoff

The implementation and evidence satisfy the diagnosis, repair, regression, and
quality acceptance criteria AC1-AC6. At review time, AC7 (main containing the
repair), commit recording, archive, and journal remain the parent's delivery
steps. The reviewed integration plan rechecks main and preserves concurrent
work, with affected checks required if integration changes the product tree.

The reviewer confirmed these Git blob hashes remained unchanged throughout
the completed gate:

| Reviewed file | Git blob |
| --- | --- |
| `NgaTopicBodyParser.java` | `aec21b62d5f8119a57c06f36f83a0a3fb49678a2` |
| `NgaProfilePageSourceTest.java` | `22b3bdac58a370c6dc7fc5ab6ea9151a36523040` |
| `.trellis/spec/backend/ai-summary-contract.md` | `8b8f8e11ad5ad75881042178d41f9cf9644ae0d7` |
