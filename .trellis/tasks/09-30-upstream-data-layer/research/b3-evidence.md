# U3 B3 — typed normal-read data boundary (not yet wired)

Date: 2026-10-01. Dedicated worktree `nga-just-works-upstream-adoption`, branch
`feat/upstream-adoption`, B2 base `4a4296c2`. B3 only. App production code and
existing Java display/private-message types are unchanged. Main owns metadata,
specs, review and commit. No device, network, signing, secrets or SDK/toolchain
upgrade. Product/tests frozen for independent review before final gate accounting.

## Files and module direction

- `lib_core_data/build.gradle`: apply existing Kotlin Android plugin and JVM17;
  existing Java17, Android configuration and message Java types retained.
- `lib_core/build.gradle`: `api project(':lib_core_data')` because the public
  decoder result exposes the DTOs. No reverse app dependency.
- `lib_core_data/.../core/data/thread/`: ReadThreadWire, ReadPostWire,
  ReadUserWire, ReadTopicWire (+ typed reply), ReadAttachmentWire and ReadField.
- `lib_core/.../core/thread/ReadThreadWireDecoder.kt`: ReadDecodeMode,
  ReadShapeProblem, ReadThreadDecodeResult and pure decoder.
- `lib_core/src/test/.../core/thread/ReadThreadWireDecoderTest.kt`: 14 independent
  tests, including complete field assertions and malformed/optional cases.

`decode(JSONObject data, ReadDecodeMode)` accepts an already-parsed normal-read
`data` object. It never parses raw text, repairs wrappers, resolves hosts,
selects an account/source, calls network/cache/render code, or exposes app types.
DTOs contain no JSONObject/JSONArray/untyped payload map. The only dictionaries
are meaningful typed protocol tables: user ID→user, group ID→label,
attachment key→attachment. Comments are recursively typed posts in numeric
slot order. Local JSON objects stay inside decoder implementation.

`ReadField` retains original kind (MISSING/NULL/STRING/NUMBER/BOOLEAN/OBJECT/
ARRAY/OTHER), decoded value and conversion validity. Presence is never inferred
from a default zero or empty collection. Source fields retain the legacy string
coercion **and** `isSourceScalar`; B4 must choose based on mode, never treat a
serialized object/array/boolean as scoped editable source. No source text is
attached to failure results or exception causes. The local conversion catch
only captures getter/cast failure into field validity, not unrelated app errors.

## Complete field mapping and source anchors

Authority is current `ArticleConvertFactory.java:56` (envelope/shape/outer null),
`:110` (optional topic conversion), `:123` (current count/users), `:149` (row
conversion/source), `:282` (17), `:296` (comments), `:308` (client), `:314` (user),
plus every protocol/fallback field in current ThreadRowInfo and Attachment.

| Input family | DTO coverage and executed assertion |
| --- | --- |
| Page containers | __T, __U, __U.__GROUPS, __R, __GLOBAL; missing/null/wrong type remain distinguishable. `missingNullAndWrongShapeRemainDistinctForOptionalFields` |
| Counts / optional paging | __R__ROWS→currentRowCount and __ROWS→totalRowCount; only current count drives loop. __PAGE/__R__ROWS_PAGE retained optional, do not alter paging policy. Test total900/current1/page7/rowsPerPage30 |
| Prefix | __GLOBAL._ATTACH_BASE_VIEW exact string, including full path/trailing slash; invalid/missing global and nonstring prefix retained as states. No host normalization |
| Row identity | tid/fid/authorid/pid/lou and score, all explicit nullable fields with shape; numeric field coercion matches local JSON2 getter; missing/null not silently made positive/zero |
| Row source | subject/content/alterinfo (exact string/numeric/null/missing and structured type), vote, postdate, level; matrix includes high-precision decimal, boolean, object and array |
| Row fallback author | author, isanonymous, yz, js_escap_avatar, muteTime, aurvrc, signature, muted/mMuted, postCount/mPostCount, reputation/mReputation, memberGroup/mMemberGroup. Complete field test asserts values before any user overlay |
| Client / WP / hot replies | raw from_client and raw comma text from `17` preserved. Explicit client step historically overrides bean aliases; decoder does not infer model, unescape WP, split/drop duplicate hot IDs, or render |
| Attachments | map key/order, aid/url_utf8_org_name/dscp/size/ext/name/thumb/attachurl/type/subid; complete assertions incl sparse second entry. Null attachment entries remain typed null; wrong entry or invalid size/subid follows outer-null disposition |
| Comments | `comment` indexed object→recursive ReadPostWire list; declared local size with numeric lookup, scoped gaps→INDEXED_ROW, legacy skips. Own author/user association, parent source unaffected by malformed child source |
| User | username/avatar/yz/mute_time/rvrc/signature/postnum/memberid/buff keys, matching group `0` label. Each referenced user instance reused across posts/comments within one decode; distinct invocation gets new instances |
| Groups/fallback | missing user stays missing/null and leaves row fallback data intact; missing/invalid matching group label does not reject page. Bad referenced user casts or throwing group/buff accessor conversions preserve outer failure; getJSONObject array/scalar null results remain nonfatal. Unreferenced bad user retained as typed invalid entry, does not reject page |
| Topic | tid/fid/author/authorid/lastposter/replies/subject/titlefont/type/topic_misc/postdate; explicit existing camelCase aliases retained. Optional invalid conversion yields null typed topic without deleting rows |
| Topic navigation extras | page/pid/position/anonymity/board and replyInfo pidStr/tidStr/authorId/content/subject/postDate covered, so later facade does not lose fields accepted by old bean |
| Topic owner lookup | independent topicAuthorId preserves raw authorid lookup when optional topic bean conversion fails, matching original separation. Bad owner scalar causes outer failure only if a row is actually converted |
| Local output excluded | formattedHtmlData/imageUrls/presentation/blacklist are not wire fields or authoritative input. Anonymous/client display, mute-ID policy, host/renderer and source availability belong to B4 mapper |

The complete post/attachment test asserts every listed post scalar/fallback and
all ten attachment fields. Topic/navigation test asserts every topic/reply
field. User test asserts every user field/group/buff key, identity reuse and
snapshot independence after input mutation. Other tests address exact malformed
combinations, not just absence of exceptions.

## Three failure dispositions and ordering

Core reports a shape problem; app error classes are not imported.

| Core result | Existing boundary to preserve in B4 | Tests |
| --- | --- | --- |
| ROW_MAP_OR_COUNT | Scoped bad/missing __R or decoded count null/negative/count>map size → FORMAT (getter exceptions remain UNREADABLE_VALUE); validate this before total/row conversion | strictCountAndIndexedRowFailures... |
| INDEXED_ROW | Scoped declared numeric slot missing/nonobject, including nested comments → CONTENT | strictCountAndIndexedRowFailures..., nestedCommentGaps... |
| UNREADABLE_VALUE | Existing outer cast/bean conversion failure → facade null, not a new typed CONTENT/FORMAT throw; NormalArticleParser still converts null to its own FORMAT | invalidPrimitiveAndContainerConversions..., primitiveFailurePrecedesNestedGap... |
| Success with absent rows field | Legacy null/missing __R produces no rows once required integer counts and users cast are valid | legacySkipsBadIndexedRows... |
| Success with skipped slots | Legacy skips nonobject/missing rows, count<0 yields empty; total never drives traversal | legacySkipsBadIndexedRows... |
| Success with invalid source field | Object/array/boolean body remains typed invalid scalar; row/parent retained. Mapper determines scoped sourceUnavailable vs legacy coerced text | sourceShapeRetainsExactText..., nestedCommentGaps... |
| Success with null optional topic | Topic conversion failure degrades metadata only; no fabricated default topic for missing/null input; nonobject __T fails its outer cast | malformedOptionalTopicDegrades... |

A coercible string `"1"` count passes existing scoped getInteger precheck but
fails the subsequent original Int cast: this explicitly remains UNREADABLE_VALUE.
Row primitive/attachment conversion precedes nested-comment validation, so bad
score plus a comment gap must retain outer-null disposition. Topic authorid
conversion is checked during row processing, after nested/user validation; zero
rows do not trigger an unused owner failure. Failures contain only enum and
field path; user response text is not included.

## B4 obligations (not claimed complete by B3)

1. Keep raw text, wrapper/token repair and ArticleErrors in current facade.
   Map three core failure dispositions to existing scoped/legacy branches;
   preserve NormalArticleParser's outer-null FORMAT and EMPTY/query checks.
2. Map wire values into existing ThreadData/ThreadRowInfo/Attachment/
   ThreadPageInfo; use old primitive defaults only at this adapter, retain null
   topic and row fallback fields. Do not reparse or expose opaque JSON tables.
3. Apply user overlay in original order: blacklist only where old user lookup
   exists; username/anonymousName, avatar/yz/muteTime, aurvrc parse or0, signature,
   then postCount, rvrc float/10 and group (partial updates survive the old
   grouped try/catch). Buff mute IDs still come from existing ForumConstants.
4. Apply source-scalar filtering for scoped content/subject/alterinfo before
   old subject-as-content fallback. Preserve sourceAvailable criterion,
   recursive contentComplete, real floor evidence, UID navigation/OP nullable.
   Bad alterinfo alone is filtered but does not invalidate valid content.
5. Preserve raw from_client→existing clientModel helper; normal-only `103 ` WP
   unescape; `17` comma splitting retains order/duplicates and skips empty IDs.
6. Fully map attachments/comments/user/blacklist and full page attachment prefix
   before renderer. Comments render before parent, use own user/avatar; no new
   HTML/URL model. AppArticleParser stays separate and retains U2 behavior.
7. Keep cache/navigation description/source bytes, query/account/generation and
   pagination decisions in app. Do not activate optional __PAGE fields as a new
   pagination policy, do not add fallback/network behavior.
8. Run app facade parity and full regression matrix after actual wiring. B3 core
   tests do not by themselves certify the unimplemented mapper or Android UI.

## Verification

- Core/core_data compile and unit tests passed after adding the typed boundary.
- `./gradlew :lib_core:testDebugUnitTest :lib_core_data:testDebugUnitTest
  :nga_phone_base_3.0:assembleDebug testDebugUnitTest --continue --console=plain`
  passed (45s,366 tasks). All13 modules: **801 tests, zero failures/errors/skips**;
  core26 includes12 original decoder tests; app693 and other prior tests retained.
- Fresh all-module lint `lintDebug --continue --rerun-tasks --console=plain`
  passed (34s,538/538 tasks executed); all13 module XML reports inspected and
  have zero Error/Fatal. Existing warnings/informational issues remain.
- `git diff --check` passed. No app production changes or old runtime JSON imports
  introduced; core_data DTOs contain no JSON/Android/app/network/cache types.
- Initial compile iteration corrected cross-module Kotlin smart-cast assumptions
  by binding nullable public properties to local vals. No tests weakened.

Logs: `/tmp/u3-b3-tests.log`, `/tmp/u3-b3-debug.log`, `/tmp/u3-b3-lint.log`.
Actual app R8 remains open from B2/U3-A7 for U4 because of existing signing guard;
B3 does not claim new app shrinking evidence. No B4 wiring or commit performed.

## Independent review corrections

The checker found and corrected three compatibility errors before B4 wiring:

1. The original `__T` cast occurs outside buildThreadPageInfo's optional bean
   conversion catch. Nonobject topic containers now fail UNREADABLE_VALUE;
   malformed fields inside an object may still yield optional null topic.
2. A throwing scoped count getter occurs before the row-map/count condition.
   Such conversions now fail UNREADABLE_VALUE, including with a bad/missing row
   map. Successfully decoded null/negative/out-of-range values retain
   ROW_MAP_OR_COUNT, and indexed gaps retain INDEXED_ROW.
3. Groups, matching group entries and buffs use original getJSONObject semantics:
   encoded object strings convert, arrays/scalars can return null, and malformed
   strings remain invalid conversions. Original input kind is retained, including
   group entry kind. No whole-response reparsing was introduced. Validation stays
   at the original author association point: missing user still evaluates groups,
   while authorid0 bypasses it. Unreferenced malformed entries do not reject rows.

Existing incorrect expectations were corrected and two focused regression tests
added (14 decoder tests total). B4 must retain partial postCount/reputation/group
updates in their original grouped try/catch order; the decoder carries raw rvrc
and nullable group state without performing that policy. Final post-fix gate
results are recorded in b3-check.md and below when completed.

Final independent-review gates passed after the corrections: Debug assembly and
all-module tests, 803 tests with zero failures/errors/skips (core28 including
14 decoder tests, app693); fresh all-module lint rerun, all538 tasks executed,
all13 XML reports zero Error/Fatal. Logs are `/tmp/u3-b3-check-debug-final.log`
and `/tmp/u3-b3-check-lint.log`. See [b3-check.md](b3-check.md) for findings and
specific uncompleted B4 facade/mapper parity requirements. No app wiring changed.
