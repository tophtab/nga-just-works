# U3 B3 independent check

Date: 2026-10-01. Worktree `nga-just-works-upstream-adoption`.
Baseline: B2 `4a4296c2`. Scope: typed core_data/core additions only; no app facade
wiring or B4 implementation. Main owns shared specs, metadata and commit.
Verdict after fixes: B3 ready for checkpoint commit; B4 wiring remains pending.

## Findings (fixed)

File: `lib_core/src/main/java/gov/anzong/androidnga/core/thread/ReadThreadWireDecoder.kt`
and its `ReadThreadWireDecoderTest.kt`.

1. Wrong-type `__T` was treated as optional-null success. Actual
   ArticleConvertFactory.buildThreadPageInfo casts this container outside its
   local bean-conversion catch, so arrays/scalars/string objects cause the
   outer-null path. The decoder now reports UNREADABLE_VALUE for this cast;
   conversion failures inside a valid topic object still degrade only metadata.
2. Throwing scoped count conversions were classified ROW_MAP_OR_COUNT. The
   original getInteger runs before the shape condition and reaches the outer
   catch on conversion failure. The decoder now validates that conversion first
   as UNREADABLE_VALUE, even with an invalid row map. Decoded null/negative/
   out-of-range counts retain ROW_MAP_OR_COUNT, and indexed gaps retain CONTENT's
   INDEXED_ROW disposition.
3. Groups, matching group entries and buffs used strict casts, unlike the
   original getJSONObject accessors. The decoder now preserves encoded-object
   string conversion, valid null conversion for arrays/scalars, and malformed
   string failure. Original kinds remain represented. Validation remains at
   author association: malformed groups still fail when the referenced user is
   missing; authorid0 skips that access. Missing/invalid matching group labels
   remain optional rather than whole-page failures.

Corrected the corresponding original new-test assumptions and added two tests
covering wrong topic containers and the group/buff conversion matrix. All 14
decoder tests pass. Updated b3-evidence.md; main refined the shared wire spec.
These changes preserve existing behavior rather than add a parser fallback.

## Findings (not fixed) / B4 requirements

No remaining identified B3 blocker. B4 is not certified by core tests and must
close these explicit integration obligations before switching the facade:

- Preserve public getArticleInfo/getScopedArticleInfo/renderRow and Java model
  contracts. Keep original raw, wrapper/token handling and ArticleErrors in app.
  Map ROW_MAP_OR_COUNT to scoped FORMAT, INDEXED_ROW to scoped CONTENT, and
  UNREADABLE_VALUE to facade null; retain legacy null/skip and NormalArticleParser
  null-to-FORMAT/empty/query validation. Compare actual facade outputs, not just
  isolated core enums. Include combined-error ordering and the corrected cases.
- Apply old primitive defaults in the mapper only. Verify canonical/legacy alias
  and fallback field values through the old/new facade, including topic extras,
  source presence/null/kind and valid-versus-invalid optional topic distinctions.
  The raw topic owner lookup remains independent of optional topic conversion.
- Preserve user-overlay order and partial updates: blacklist only with the
  original present user; username/anonymous, avatar/yz/muteTime, aurvrc fallback,
  signature, then the grouped postCount → rvrc Float/10 → memberGroup block.
  A failed reputation conversion must not undo postCount or apply a later group
  update. Missing user leaves row fallback intact; buffs apply existing mute IDs.
- Filter scoped source scalars before subject-as-body fallback, retain recursive
  completeness, floor evidence, UID navigation and nullable OP. A bad alterinfo
  alone does not invalidate otherwise readable source. Preserve normal-only WP,
  raw client-to-model mapping and ordered/duplicate `17` splitting.
- Map attachments and recursive comments with original order and full page
  prefix before rendering; comments render before parents with their own users.
  Keep app blacklist/host/render policy out of core and retain separate App parser.
- Exercise existing reader/source/query/account/generation/paging/cache/U1/U2
  regressions through the wired mapper. Source-only Android orchestration is not
  device evidence. Actual app R8 remains the separate pending U3-A7/U4 obligation.

## Review evidence

Read all six new DTO files, complete decoder and test file, Gradle changes,
current ArticleConvertFactory, ThreadRowInfo, NormalArticleParser, B3 evidence
and main-owned ordinary-read-wire-contract.md. Reviewed conversion behavior
against the actual fixed JSON2 getJSONObject/getInteger implementation as well
as call-site casts/catches, rather than assuming generic JSON getter semantics.

Five wire families plus typed reply/ReadField preserve consumed post/topic/user/
attachment data and kind/presence/validity. Current count and total are separate;
scoped prevalidation and legacy indexed skipping remain distinct. Tests assert
all declared scalar/attachment/topic/reply/user values, recursive comment order,
user reuse within one call, independent instances across calls, and no retained
mutable source-object references. Per-decode user cache is local. DTOs expose no
JSONObject/JSONArray or app display models. Decoder does no whole-response raw
parse, wrapper repair, account/network/cache-path/render/host selection. Its
operation-local string-object accessor conversion preserves existing behavior.

core_data adds only existing Kotlin Android/JVM17 configuration; core exports
its DTO dependency because the public decoder result uses those types. Existing
Java message types and app production files are unchanged. The shared wire spec
now reflects the three corrected distinctions and states adapter ownership,
without claiming B4 is wired.

## Verification

- TypeCheck/build: pass, final `:nga_phone_base_3.0:assembleDebug
  testDebugUnitTest --continue --console=plain` (44s, 366 tasks).
- Tests: pass, independently summed all 13 module XML: 803 tests, zero failures/
  errors/skips. Core decoder 14; total core 28; app 693 unchanged. Final decoder
  XML timestamp `2026-10-01T08:00:25`.
- Lint: pass, fresh all-module rerun completed (32s, all 538 tasks executed);
  independently inspected all 13 generated XML reports: zero Error/Fatal.
- `git diff --check`: pass.
- No commit, B4 app edits, NGA/device/signing/publication operations.

Logs: `/tmp/u3-b3-check-focused.log`, `/tmp/u3-b3-check-debug-final.log`,
`/tmp/u3-b3-check-lint.log`.
