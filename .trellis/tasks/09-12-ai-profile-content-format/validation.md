# Repair validation

Date: 2026-09-12. Worktree: `/home/toph/nga-just-works-ai-summary`.
Integrated baseline: `main@7cb9e50b3fb6a0b51f5435abaedaef1d0b5ff436`.

## Result and change boundary

The implemented local quote scanner escapes native TAB/LF/CR while preserving
decoded text, existing escapes, quoted markers, and original-post identity.
Unsupported raw controls and malformed escapes retain the existing error.
The shared JSON/model decoder is unchanged. Product edits are confined to
`NgaTopicBodyParser.java`; regression changes are in the existing
`NgaProfilePageSourceTest.java`.

## Executed checks

| Gate | Result |
| --- | --- |
| Focused AI before product edit | 211 tests in 13 classes; exactly 4 expected failures reproduced the reported error |
| Same focused AI suite after correction | 211 tests in 13 classes; 0 failures/errors/skips |
| App debug build and full JVM suite | Build success; 515 tests in 58 classes, 0 failures/errors/skips |
| App debug lint | 0 Error / 0 Fatal |
| All-module lint with `--rerun-tasks` | 536 tasks executed; all 13 XML reports present, 0 Error / 0 Fatal; 835 warnings |
| Repository debug unit-test gate | Successful command; 600 reported tests in 76 classes across 13 modules, 0 failures/errors/skips |
| App Android-test APK build | Success; APK exists and is 811,135 bytes |
| Context manifest validation | 5 implement entries and 7 check entries valid; oversized-spec injection warnings handled by direct reads |
| Diff whitespace check | Pass |

The implementer, reviewer, and parent inspect generated reports rather than
inferring success from Gradle/lint exit codes alone. The parent independently
confirmed all 13 module test/lint reports and the test APK. Repository unit
results reuse the preceding successful app run (`UP-TO-DATE`) and eligible
library cache results (`FROM-CACHE`); 600 is the inspected report total, not
600 newly executed cases in the last invocation. The two formerly broken
library examples each have a nonzero passing result on the integrated baseline.

Commands ran serially. Exact commands, log names, test names, and cache
qualifications are in `research/implementation-handoff.md`; local evidence is
under `.temp/ai-profile-content-format/`. The completed independent review is
recorded in `independent-check.md` before the work commit.

## Acceptance mapping

- AC1–AC2: `research/live-result.md` locates the first topic detail, and
  `research/offline-reproduction.md` plus the red/green XML establish the exact
  failure class against production code.
- AC3–AC4: live diagnosis, offline repair, and APK compilation are distinguished;
  only synthetic content and sanitized structural observations enter artifacts.
- AC5: four new regression methods and the strengthened GBK loader integration
  cover raw/escaped controls, ignored metadata, malformed escape rejection,
  normalized-size boundaries, body preservation, and complete composition.
- AC6: the required build/test/lint/test-APK gates passed on the current main
  baseline with the correction applied.
- AC7: work-commit and main-integration evidence is recorded in `delivery.md`
  once the checked change has been committed and merged.

## Verification limits

The original two-request live diagnosis is complete and was not repeated.
Repair verification uses synthetic fixtures and loopback servers, including
the actual GBK decode/list/detail/reply path. Device tests were not run per
project policy; the Android-test APK was compiled and packaged only. No raw
live body, credential, profile identifier, or model output is part of the
committed reports. There is no remaining quality-gate failure.
