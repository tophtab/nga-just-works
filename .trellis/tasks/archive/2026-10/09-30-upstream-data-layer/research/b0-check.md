# U3 B0 independent check

Date: 2026-10-01. Worktree: `/home/toph/nga-just-works-upstream-adoption`.
Baseline: U2 `88ce8be3`, including U1 and formally integrated R1–R6.
Verdict: B0 is ready for the main session's checkpoint commit. This is not U3
acceptance; B1–B5 remain outstanding. No production/dependency changes or commit.

## Findings (fixed)

- File: `nga_phone_base_3.0/src/test/java/sp/phone/profile/AuthorLocationStoreTest.java`.
  The new golden test rewrote its input file, while `AuthorLocationStore.write`
  catches IO/runtime failures. A failed writer could leave the original golden
  untouched and falsely satisfy equality. The test now writes decoded entries
  to a separate initially absent file, asserts creation, and compares actual
  output to the immutable fixture. Production code and snapshots are unchanged.

## Findings (not fixed)

No remaining B0-blocking issue. The following documented gaps deliberately stay
outside this test/resource-only checkpoint:

- Notification error/malformed-envelope branches reach Android `NLog`/`Log.e`;
  the attempted new host test was removed, not an existing assertion. B1 must
  establish a reachable operation-local test seam before B2 parity is claimed.
  Current tests cover successful indices, PID fallback, ordering, unread state,
  wrapper and empty arrays only.
- B1/B2 still owe actual TopicConvertFactory jdata cases, upload/preflight and
  operation-local action/message-send success/error coverage, plus all three
  JSON2 storage directions and real reflection/R8 evidence. Archived probes and
  generic JSON tests do not close those obligations.
- Context-bound preferences, history lifecycle/caps, board raw IO, filter-store
  migration orchestration, Room and Android Bundle recreation are source-only
  where labelled in b0-evidence.md. Existing file/resolver/editor tests are not
  misrepresented as execution of those Android owners.
- The active message list parser can retain a malformed row after its caught
  exception and loop without progressing. This preexisting product behavior is
  explicitly recorded in b0-evidence.md; repair is a product judgment outside
  B0. Do not silently fix it as part of JSON import migration or claim malformed
  row coverage from the successful-list test.

## Coverage review

Read PRD/design/implement and all check.jsonl references, then reviewed the full
tracked test diff, new tests and all nine synthetic JSON snapshots. The tests
use actual ThreadPageInfo/ReplyInfo, User, FilterKeyword, Board/BoardEntity,
Attachment, bookmark codec, ArticleCacheCodec, AuthorLocationStore and active
message/notification parsers. Numeric/null/exclusion and raw-text assertions
match those production paths; goldens do not regenerate during test execution.
All 15 inventory storage rows have an explicit evidence/limitation mapping.
No existing assertion was removed or weakened. No test framework/global Android
stub setting was loosened to bypass the notification failure.

Independent source scan matched all 43 production and 14 current test direct
fastjson1 consumers in b0-consumers.txt. The planning count of 12 test consumers
predates U1 and B0; both additions are documented. U1 exclusions, U2 media, and
R1–R6 behavior remain baseline regressions. No spec contract changed in B0;
main owns later JSON2/decoder spec synchronization.

## Verification

- Tests: pass. Before the local correction, independently inspected actual XML:
  app 669 / 74 suites, core 14 / 4, common 63 / 4, message 1 / 1 = 747 tests;
  zero failures/errors/skips. This was the implementer's complete B0 run.
- After the correction: focused AuthorLocationStoreTest, 9 tests, zero
  failures/errors/skips; final XML timestamp `2026-10-01T07:12:22`.
  Command: `./gradlew :nga_phone_base_3.0:testDebugUnitTest --tests
  'sp.phone.profile.AuthorLocationStoreTest' --console=plain`.
  The filtered invocation replaces app XML; do not recount that directory as
  the earlier 669-test complete run.
- TypeCheck: pass, Java/Kotlin test compilation in the final focused Gradle run.
- Lint: inherited U2 gate, not rerun for tests/docs/resources only, as agreed for
  B0. Independently inspected 13 existing module XML reports: zero Error/Fatal.
- `git diff --check`: pass.
- No NGA/device/signing/publication operations. No JSON2 switch or B1 work.
