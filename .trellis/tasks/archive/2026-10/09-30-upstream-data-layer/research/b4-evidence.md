# U3 B4 — ordinary facade wired through typed decoder/legacy mapper

Date: 2026-10-01. Dedicated worktree `nga-just-works-upstream-adoption`, branch
`feat/upstream-adoption`, B3 base `758aa6b9`. B4 only. No B5 dead-helper/private-
message-copy cleanup or commit; main owns metadata/specs/checkpoint. No NGA,
device, signing credentials/guard bypass, publishing or toolchain change.

## Baseline evidence was captured before wiring

`src/test/resources/read-wire-parity/{inputs.json,baseline.json,README.md}`
contains **78 synthetic inputs × both facade modes =156 fixed actual outputs**.
The generator stage executed the actual production ArticleConvertFactory at
758aa6b9, with just the main-approved host-equivalent change
`!TextUtils.isEmpty(hot)`→`!hot.isEmpty()` for nonnull String.split entries.
The old Android static helper otherwise reaches a throwing host stub and hides
all17-path behavior. Empty/delimiter-only/ordered duplicate17 cases capture this
small equivalence. No Android stub-default bypass or duplicate expected mapper.

The capture passed before any facade/core/mapper wiring (`/tmp/u3-b4-baseline.log`).
The capture test then became a read-only comparison; no regeneration branch
remains. Snapshot hashes, recorded before wiring and unchanged:

- inputs.json: `39fe06fe1343cc081e3d332018f7023870f31d2e751862ab9e798c5f2e994a7d`
- baseline.json: `a84f626342d272950ae5d849d61a133f9d6397e6ae9ea52dd66e7bf91724f678`

Snapshots include all serializable Java row/topic fields with explicit nulls,
primitive defaults, complete attachments/nested comments, derived presentation,
renderer-entry state and ordered attachment keys, deterministic rendered output
and image lists, blacklist call order. Raw response equality is asserted
directly, not formatted/rebuilt. Expected mapping is production output, not a
second implementation of the new algorithm.

## Product changes and boundary

- `ArticleConvertFactory` preserves all three public signatures and raw/wrapper/
  token/ArticleErrors handling. It passes the parsed data once to the core
  decoder, maps ROW_MAP_OR_COUNT→scoped FORMAT, INDEXED_ROW→scoped CONTENT,
  UNREADABLE_VALUE→null, then calls ReadThreadLegacyMapper. Existing outer
  catches and NormalArticleParser's null→FORMAT/EMPTY/query checks remain.
- New app `ReadThreadLegacyMapper` creates existing Java ThreadData/ThreadRowInfo/
  ThreadPageInfo/Attachment objects. No raw JSON reparsing, no new public display
  models or cache/navigation types. Primitive defaults occur here. Lists remain
  mutable ArrayLists; attachment HashMap iteration preserves the old typed-bean
  iteration exposed to rendering (wire retains input order separately).
- Mapper retains row fallback metadata, user/blacklist overlay and partial
  reputation/group updates; raw client and existing clientModel helper,17
  splitting, normal-only WP unescape, source capability and recursive completeness,
  real floor evidence and nullable UID-owner identity.
- Comments are mapped/rendered before parents; full attachments/comments/user/
  blacklist and full resolved page prefix exist before renderer. Production
  `renderRow`/HtmlConvertFactory path is retained.
- Production attachment projection loop was extracted unchanged to package-static
  `buildAttachmentData`, called by buildHtmlData. Its exact cast/null behavior
  can now run in host tests without initializing theme/context first.
- Original now-unused ArticleConvertFactory parsing helpers remain until B5,
  as requested. Shared render helpers remain live. Active lib_bu_message and the
  app private-message copy are untouched. NormalArticleParser and AppArticleParser
  have **no source changes** in this batch.

## Core refinements driven by actual facade output

The first156-case comparison found concrete gaps in B3's isolated tests:

1. Bean aliases are assigned in actual map iteration order, with the last
   matching alias winning. Example `authorid:42,authorId:43` produced43; camel-
   case postDate/alterInfo/jsEscapAvatar and underscore post_count/member_group
   also survived the real bean. Decoder now uses JSON2's own
   `Fnv.hashCode64LCase` (not a guessed normalization) and actual map iteration
   for bean-field lookup. Root count/row/users getters remain exact. Attachment
   attachUrl/urlUtf8OrgName/subId aliases are included.
2. Scoped facade used a HashMap copy, removed only invalid **literal canonical**
   content/subject/alterinfo keys, then performed bean alias conversion. Mixed
   `content:{},Content:"alias source"` displays alias source but is source-
   unavailable. Typed `ReadScopedSourceWire` carries this effective projection;
   post source ReadField retains the canonical raw scalar evidence and legacy
   alias-selected text. Literal lou presence is separate from alias-selected
   numeric Lou (known12 but floorKnown=false). This does not reinterpret source
   validity or introduce a whole-response reparse.
3. Missing matching group versus an empty decoded group both have null labels
   but different overlay behavior. `groupResolved` distinguishes them: after
   successful rvrc float conversion, empty group clears the fallback label;
   missing/null/invalid group preserves it. A failed rvrc parse retains the
   earlier postCount update and does not apply a later group change. Integer
   aurvrc fallback and reputation/10 remain separate conversions.
4. JSON2's old generic Attachment map conversion can retain a raw JSONObject
   after malformed size conversion. This is not a valid Attachment. Core now
   retains typed invalid-entry metadata rather than rejecting before comment
   slot validation. App rejects that invalid attachment at the rendering phase,
   after recursive comments/user processing, without exposing raw JSON or making
   a fake Attachment valid. Thus a scoped comment gap still wins as CONTENT.
   Null entries remain actual null map entries; production renderer rejects them
   naturally at the same dereference boundary.

Core tests now also assert alias/source/floor behavior and deferred invalid
attachment versus scoped comment-gap ordering. Corrected the B3 assumption
that malformed attachment size must fail before all nested-comment validation;
the fixed pre-wiring facade proves otherwise.

## Exact parity results and narrowly scoped allowances

All156 case IDs remain in the fixed baseline. The comparison has no blanket
failure-ignore branch. **151 cases match complete output and traces exactly**;
the remaining five are individually named and asserted:

| Exact case IDs | Observation / retained contract |
| --- | --- |
| `wrong-entry:false`, `wrong-entry:true` | Both old/new return null. Old executes one nested-comment renderer before discovering invalid parent user; decode-first executes no discarded renderer. New outcome and empty trace asserted |
| `bad-buffs:false`, `bad-buffs:true` | Both return null. Old has one discarded comment renderer and parent blacklist lookup before malformed buff decoding; new rejects before these. Exact outcome and new empty traces asserted |
| `attachment-before-gap:false` | Old injected no-op renderer lets invalid raw JSONObject remain in an Attachment-typed map and returns a false success. New typed mapper returns null; exact old success/new null asserted. Actual production attachment projection cannot render old value (see proof below) |

`attachment-before-gap:true` has **exact CONTENT parity**, including no rendering:
comment-slot gap occurs before attachment rendering failure. Null attachment
cases retain exact no-op-renderer parity; actual production failure is separately
verified. Every usable successful result has exact fields and complete
render/blacklist traces, with no allowances.

`productionAttachmentProjectionRejectsOldRawFallbackAndNullEntries` executes the
actual old JSON2 ThreadRowInfo bean conversion and the production attachment
projection now called by buildHtmlData. Malformed size leaves a raw JSONObject
and causes ClassCastException; a null entry causes NullPointerException. Thus
the legacy no-op-renderer artifact does not waive the actual production outer-
null behavior or scoped CONTENT ordering, and does not justify leaking opaque
JSON through the typed boundary.

Side-effect analysis for the four rejected traces: production renderer mutates
only discarded row HTML/image lists while reading existing theme/settings/assets;
it does not deliver UI, request network or write article caches. Actual callers
are ArticleListModel, legacy cache replay and NormalArticleParser. Production
blacklist lookup calls UserManagerImpl.checkBlackList→FilterManager.filterUserById,
which scans current in-memory users. FilterManager singleton initialization can
load/migrate its existing preferences when first touched, so avoiding a rejected
lookup can defer that initialization; it adds no new write or action. The
requested decode-first architecture intentionally avoids discarded work. Main
reviewed and accepted these precise differences before freeze.

## User/source/render rules retained

- Missing user leaves complete row fallback intact; present user overwrites
  username/avatar/yz/mute/aurvrc/signature exactly, preserving row anonymous=true
  unless the old logic changes it.39-character anonymous names use existing
  helper (including malformed hex fallback). Existing buff IDs117/105 apply.
- postCount update precedes Float parsing; bad/null rvrc does not erase prior
  fallback reputation/group. A valid float but missing/invalid group retains
  fallback group; empty valid group clears it.
- Scoped sourceAvailable uses raw scalar content/subject plus effective
  content/subject/nonempty valid alterinfo. Numeric/empty text, subject fallback,
  malformed nested source and bad alterinfo preserve existing behavior. Legacy
  source coercion remains permissive. No app-source WP transformation added.
- Current row count drives traversal, total count remains metadata; all original
  integer-cast/null/count-range/scoped indexed/legacy-skip distinctions survive.
- Topic bean conversion can degrade metadata only; wrong container still nulls
  the page. Independent raw owner lookup persists even if optional bean fails.
- Raw response, valid topicInfo/cache schema/keys, source/account/generation,
  resolvedTid, real floors and page-size policy remain unchanged app ownership.
  No R7 recovery or browser-policy change.

## Initial verification (superseded by correction gate below)

- Focused core/facade parity tests pass (`/tmp/u3-b4-focused.log`).
- `:nga_phone_base_3.0:assembleDebug testDebugUnitTest --continue` passes (48s,
  366 tasks). Full13-module XML count **806 tests, zero failures/errors/skips**:
  app695 (two new parity/projection tests), core29 (15 decoder tests), other
  existing suites retained. The156-case loop is one test, not counted as156.
- Final all-module tests rerun after tightening the trace allowances to exact
  IDs: passed (45s,287 tasks),806 tests remain zero failures/errors/skips
  (`/tmp/u3-b4-final-tests.log`).
- Fresh all-module `lintDebug --continue --rerun-tasks` passed (82s,538/538
  tasks executed); all13 XML reports inspected: zero Error/Fatal. Existing
  warnings/informational issues remain (`/tmp/u3-b4-lint.log`).
- `git diff --check` passes. Baseline snapshot hashes unchanged; no app source
  changes in NormalArticleParser/AppArticleParser. Shared wire spec reviewed
  before work; main owns updates for the concrete refinements above.

Product/tests frozen and main notified for independent review. No B5 cleanup or
commit. Actual app R8 remains pending U4/U3-A7 due the existing signing guard;
this batch does not claim new minified app/device evidence.


## Independent-review corrections and final freeze

The initial review in `b4-check.md` blocked B4 on five concrete parity losses.
All five are corrected without modifying the original 156-case oracle or its
five exact allowances:

1. Nonempty canonical `vote` overrides the bean-selected alias; empty/null vote
   retains the bean fallback.
2. Every matching bean alias is converted in actual map iteration order. An
   earlier malformed assignment cannot disappear behind a later valid alias.
   Matching uses JSON2's Fnv/bean semantics, including primitive null defaults.
3. Attachment containers use smart matching (`Attachs` included), and every
   assignment is validated. Typed invalid attachment evidence still defers
   failure until the original consumption phase. Topic ReplyInfo and attachment
   items preserve the actual JSON2 bean-array positional mapping as demonstrated
   by captured old-facade output.
4. Bean `hotReplies` survives when literal `17` is absent/null; nonnull literal
   `17`, including empty, explicitly overrides it.
5. All omitted display-bean fields are projected locally with actual
   ThreadRowInfo ObjectReader metadata: `comments`, `hotReplies`, `isInBlackList`,
   `_IsInBlackList`, `formattedHtmlData`, `mFormattedHtmlData`, `imageUrls`,
   `mImageUrlList`, `presentation`, `fromClientModel`, `from_client_model`.
   Smart aliases resolve through that metadata. Client/presentation fields are
   subsequently overwritten but their original conversion still runs. Bare
   bean comments remain unprocessed display fallbacks; nonnull protocol
   `comment` overrides them and stays core-owned. User overlay overrides the
   bean blacklist only when the original matching-user path performs its lookup.
   Renderer assignments preserve the original display/image precedence.

`ReadThreadBeanFallbacks` contains only the app-local finite projection and
invalid structural row paths. `decode(data, mode, invalidBeanRows = emptySet())`
checks these paths at the original bean-conversion stage, before nested comment
validation. A malformed bean `comments` combined with a nested comment gap must
return outer-null, not CONTENT; scoped root count/slot prevalidation still wins
first. `ReadPostWire.sourcePath` associates typed rows with the projection.
No raw JSON, app bean, display state or callback crosses into core_data/core.

Projection traversal uses the original `getInteger` count coercion, catches its
failure only to defer classification to core, and visits sorted existing
canonical numeric slots below that count. This keeps sparse/huge invalid counts
bounded by supplied slots. A direct real-bean regression verifies String/Long/
Int counts preserve hotReplies/blacklist and max-Int sparse traversal ignores
noncanonical `01`. Core tests verify every numeric/boolean alias assignment and
invalid app-bean path precedence, including irrelevant paths.

### Additional immutable old-facade evidence

The isolated oracle is the actual B3 `758aa6b9` ArticleConvertFactory, renamed
for host execution with only the previously approved nonnull hot-string
`isEmpty` equivalence. Source is archived in
`research/probes/b4-correction/LegacyBaselineArticleConvertFactory.java`, SHA256
`4245bd0d4332a1055e451ae716d0c7423e923b05413b0d5e4d3ca5f691f70034`.
It was removed from executable tests after capture; final comparisons do not
call retained old private helpers and remain valid after B5 deletion.

- `read-wire-fallback-parity`:52 inputs ×2 modes =104 captured outcomes
  (`/tmp/b4-correction-capture.log`).100 compare exactly. The four named no-op
  artifacts `attachment-size:false/true` and `attachment-subid:false/true`
  assert old no-op success versus new null, and are covered by production
  rendering evidence below. No other exception is permitted.
  Inputs SHA256 `a0bdc354c4c87962f255b6f55e8b59837b25c741384afd4ebdb2c7953f9eb0d0`;
  baseline `fe0a0a385a249f262ea455e488319852e46878264abbf59ff6b0b70c0bb38c6d`.
- `read-wire-container-parity`:36 inputs ×2 modes =72 captured outcomes
  (`/tmp/b4-container-capture.log`). All72 compare exactly. The old oracle's
  injected renderer calls the actual production `buildAttachmentData` before
  recording its snapshot; malformed raw attachment artifacts therefore have
  their real null outcome. Includes attachment containers/scalars/arrays,
  ReplyInfo smart aliases/arrays, null primitive assignments, display aliases,
  hot fallback objects/numbers and source-bearing bean comments.
  Inputs SHA256 `b04d92bf5fa07815aab8ef7bc88c3f9eac6ee5cce4709bec40bc67a96ce55fe0`;
  baseline `b75a648930a37560048fa4a1064f447e6f36de490eeb577f06bd62e870926905`.

All six input/baseline hashes plus oracle source hash were rechecked at freeze.
The original156 test and its exact allowances remain unchanged. No baseline was
regenerated from the corrected mapper. The finite field inventory and actual
bean metadata cover the review's fallback class rather than excluding fields.

### Final correction gate

- Final `:nga_phone_base_3.0:assembleDebug testDebugUnitTest --continue
  --console=plain` passed in47s (366 tasks), after the last count/traversal edit.
  All13 test-report directories inspected: **811 tests**, app698/core31,
  zero failures/errors/skips. Log `/tmp/b4-correction-debug-final.log`.
- Fresh `lintDebug --continue --rerun-tasks --console=plain` passed in61s,
  all538 tasks executed. All13 module XML reports inspected: zero Error/Fatal.
  Log `/tmp/b4-correction-lint-final.log`.
- `git diff --check` passes. No B5 cleanup/commit/toolchain changes.
  Product/tests frozen and main notified for independent re-review.
- Actual app R8 remains pending U4/U3-A7 because of the signing guard; the
  correction gate does not claim actual minified app or device execution.


### Independent reviewer correction and final gate

The reviewer enumerated actual production JSON2 field metadata and found the
exact `_IsInBlackList` setter field absent from the finite app fallback list
(the lowercase spelling only covered a smart alias). An isolated host probe
against the archived B3 facade demonstrated old=true/new=false in both modes.
The inventory now uses the exact metadata name. The new real-bean/public-facade
regression covers `_IsInBlackList`, `_isInBlackList` and `isInBlackList` in both
modes without changing any captured baseline. See `b4-check.md` for the final
independent PASS and retained original blocked-review history.

After that local fix, app assembleDebug, all app Debug tests and app lint passed
in52s (554 tasks,18 executed), log `/tmp/b4-review-fix-gate.log`. Independently
parsed all13 current module test/lint report directories: **812 tests**,
app699/core31, zero failures/errors/skips; zero lint Error/Fatal. Unchanged
modules reuse the fresh full correction gate above. `git diff --check` passes.
No B5 cleanup, commit, device, NGA, signing or application R8 claim was added.
