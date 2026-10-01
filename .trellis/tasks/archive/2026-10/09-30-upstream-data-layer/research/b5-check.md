# U3 B5 independent check — PASS (code checkpoint)

Date: 2026-10-01. Worktree `nga-just-works-upstream-adoption`, branch
`feat/upstream-adoption`; base `0eebe1e5` integrates B4 `5595c8ed` and the
separately approved main reply-search fix `c768a3cb`.

B5 and the B0–B5 code sequence pass review. **U3 remains open**: actual app
R8/U3-A7 is pending U4. This report does not certify minified Android execution,
close/archive U3, or claim release publication.

## Findings (fixed)

None in B5. The B4 exact `_IsInBlackList` metadata fix and regression remain
committed and passing. No reviewer product edits or new tests were necessary
for this deletion-only checkpoint.

## Findings (not fixed)

- Actual app minification is still unverified because the existing B2 task
  graph encountered the signing guard. The production-bean classfile fixture
  proves its selected classes/rules only. U4 must supply actual app R8 evidence
  before U3 and parent acceptance; no signing-guard bypass or secret access.
- Previously documented Android preference/DataStore/lifecycle/UI orchestration
  and device playback limits remain source-reviewed, not device-tested.
- The known legacy malformed-message-loop behavior remains outside this
  migration's product-repair scope; valid-message tests do not certify it.

No B5-blocking defect found.

## Review scope and evidence

Read the full U3 PRD/design/implementation plan and check.jsonl references,
including the complete consumer/storage inventory, operation registry, reader/
cache/prefetch/profile/AI contracts, U1/U2 contracts and migration architecture.
Reviewed B0–B4 independent findings and evidence, the final B5 reconciliation,
updated JSON/wire cleanup specs, and the frozen B5 product diff.

- B0 supplied immutable real old-writer samples and an explicit mapping of all
  15 storage rows. B1/B2 established operation-local decode seams, migrated the
  inventoried runtime consumers to fixed JSON2, and executed old/new semantic
  storage directions. Honest Android-owner limits remain explicitly recorded.
- B3/B4 established typed core protocol data and app-owned presentation/source/
  blacklist/error policy. B4's five original findings and final exact-key fix
  are closed. Complete immutable facade comparisons remain executable after
  removal of the original private helpers; no archived oracle is on the final
  executable test path. No opaque JSON/app model crosses the core output.
- B5 removes exactly 190 lines of superseded private facade helpers/imports
  and the 350-line unused app MessageConvertFactory. No added product logic,
  schema migration, test weakening or unrelated deletion appears.
- Public getArticleInfo/getScopedArticleInfo/renderRow, source/raw/error
  handling and shared HtmlData/attachment projection remain intact. Both
  normal and App parsers still use the shared renderRow. The retained
  resolveAttachmentsPrefix supports existing contract tests.
- Reference scans find only the active lib_bu_message MessageConvertFactory,
  its two production repositories and golden tests. Removed private helper
  names have no executable source consumers. Active message behavior remains.
- Reconciled all original43 production consumers:41 still use JSON2, one dead
  copy is removed, and TopicSearchFragment delegates its serialization to
  ArticleNavigation. New decoder/projection consumers are included. Production
  source contains no fastjson1 imports or ParserConfig/SerializerFeature use.
- The integrated reply-search serializer and test import both use JSON2.
  Reply author identity remains separate from topic author identity; four
  regression tests cover native read/show-all and mismatched results.
- Git comparisons confirm the nine B0 golden resources remain unchanged since
  B0, and B4's156/104/72 captured input/output resources remain unchanged since
  its committed review. U1 persistence/exclusion/order and U2 media/prefix/
  source behavior remain covered by final tests. CSS and browser evidence are
  unchanged, so no duplicate browser run was needed.
- Reviewed current debug/release dependencyInsight output: runtime uses only
  JSON2 `2.0.59.android8`. App old `1.1.71.android` is testImplementation;
  ARouter compiler's `1.2.69` appears only in the kapt graph.

## Verification

Reused the unchanged implementer gates; reviewer did not start Gradle.

- TypeCheck/Debug: **pass**, app assembly and all-module JVM test gate,
  366 tasks; `/tmp/u3-b5-debug.log`.
- Tests: **pass**, independently parsed all13 module XML directories:
  **816 tests**, app703/core31, zero failures/errors/skips. Includes icon10,
  media6, prefix4, normal7, App9, reply-search4 and all facade parity suites.
- Lint: **pass**, fresh all-module rerun completed in141s with538/538 tasks
  executed. Independently parsed all13 current lint XML reports:
  **0 Error / 0 Fatal**; `/tmp/u3-b5-lint.log`.
- Runtime and processor dependency reports: reviewed and consistent with the
  single-runtime-library contract.
- `git diff --check`: pass.
- Device/ADB/NGA/external media/signing/publication: not run.

Ready for the B5 checkpoint and U4. U3 acceptance remains pending actual app R8.
