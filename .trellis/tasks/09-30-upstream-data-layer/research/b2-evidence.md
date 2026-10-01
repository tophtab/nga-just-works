# U3 B2 — atomic JSON2 runtime migration

Date: 2026-10-01. Dedicated worktree `nga-just-works-upstream-adoption`, branch
`feat/upstream-adoption`, B1 base `1b4f8ade`. Scope is B2 only; no B3 DTO/mapper
move. Product/tests frozen for independent review. No commit, NGA request,
account/secret read, device/ADB, signing setup, publish, or toolchain change.

## Implementation and deliberate behavior

All 43 inventoried production fastjson1 files (including U1 repository additions)
and test consumers use JSON2. `lib_base_common` exports the existing catalog
`com.alibaba.fastjson2:fastjson2:2.0.59.android8`; root old version variable and
obsolete fastjson1 keep removed. Existing lib_core catalog dependency stays.
`com.alibaba:fastjson:1.1.71.android` exists only as an app test oracle.

Options remain operation-local:

- Topic seam parses a generic tree with DisableReferenceDetect, then converts
  with SupportSmartMatch. All six archived jdata inputs preserve forum/time
  fields; no input-field removal regex. Wrapper remains in TopicConvertFactory.
- Article conversion and preflight use local SupportSmartMatch where the
  legacy bean contract needs it. ThreadPageInfo uses JSON2 alternateNames on
  **both getters and setters** for authorid/lastposter/titlefont/topic_misc/
  postdate; writer names remain authorId/lastPoster/titleFont/topicMisc/postDate.
  Getter-only aliases silently lost authorId in an early probe, now corrected
  and asserted by immutable storage golden and shrunk-bean fixture.
- Upload decoders locally AllowUnQuotedFieldNames; compression/retry/callbacks
  remain in the existing NGA upload owner. Third-party envelope stays distinct.
- SafeJsonParser/ProfileWebUserParser locally AllowUnQuotedFieldNames,
  DisableReferenceDetect and DisableSingleQuote; existing bounded guards,
  full-document checks, literal special keys and decimal precision retained.
- JSON2 JSONObject's ordered backing replaces `JSONObject(true)`. Owned-cache
  writing uses JSONWriter.WriteMapNullValue for explicit pageSize null.
- JSON2 exclusion annotations preserve cache handles/summary, compiled Pattern,
  board parent and icon behavior. Report ResultBean and NonameUploadResponse
  now implement existing JavaBean, covered by production consumer keep rules.

One approved characterization delta: unused Topic extension containing unknown
`@type`/`$ref` was rejected by old typed decoding; B2 treats it as ordinary
unused data and still preserves consumed forum/time fields. AI/profile safety
assertions are not relaxed. No global parser options, AutoType, FieldBased,
IgnoreCheckClose, class loading, old-runtime fallback or stored-data clearing.

## Real operation seams and tests

The main explicitly authorized minimal seams to close B0's Android-bound gaps.
No generic network/parser framework or Android stub-default switches were added.

| Operation | Executed proof / preserved owner behavior |
| --- | --- |
| Topic list | TopicConvertFactoryDecodeTest: six immutable jdata fixtures, CU/F/T/P aliases, stringified parent/subforums, missing/malformed envelopes, approved special-key delta |
| NGA upload / avatar upload | TopicPostUploadDecodeTest and AvatarUploadDecodeTest: distinct success/error/malformed shapes, local unquoted fields, compression selection; actual transport/retry remains original |
| Preflight / categories | TopicPostMetadataDecodeTest: auth/null/error/malformed; indexed categories stop at first gap and retain null label |
| Private-message send | MessagePostDecodeTest in message module: wrapper stripping, three success strings, data-first, business error, empty failure, malformed propagates to existing post catch |
| Remote filter update | FilterUpdateDecodeTest: data-first success/null, error message, unknown envelope, malformed failure; existing FilterWordModelTest retained |
| Like | OperationResponseDecodeTest: data.0 number coercion, empty data null; missing data/business error/malformed still throw to original callback catch |
| Report | Same suite invokes actual deliverResult with recording OnHttpCallBack: error-first, data success, no callback for empty envelope, malformed throws to existing reactive path |
| Comment | Same suite invokes nested Response.decode: script extraction, __MESSAGE indices, code200 + exact success marker, replacement/trim, business denial, empty/malformed fallback; outer success remains sticky via OR |
| Board search | Same suite: data.0 fid coercion/name, missing nullable title retained before existing known-board lookup, business error and malformed failures |
| Proxy | ProxyResultDecodeTest: actual decode + data-first selection, no login/empty fallback messages, malformed and wrong-shape cast; original log/catch remains |
| Avatar apply | AvatarReplyDecodeTest: actual data/error decode + selected-result semantics, null fallback vs empty object null, malformed/wrong-shape cast; original two parse/log catches and data-first orchestration retained |
| Notifications | Existing full list golden + new actual decode error tests: wrapper/index/PID fallback/order/unread; error/malformed throws at original logging catch boundary, missing index stays null |
| Ordinary/App reader, messages, profile, AI, U1/U2 | Existing full source/account/page/cache/error/AI/profile/board/media suites migrated and retained; B2 does not change reader policy or rendering architecture |

Small seam visibility changes: message checkResult/filter convertUpdateResult
become internal. Package-static methods stay at concrete Java owners. Search
returns fid/nullable title instead of constructing BoardEntity prematurely.
Comment Response is nested so testing does not initialize the outer task's
Android URL dependency. Network/UI/logging orchestration is source-reviewed,
not represented as host-executed Android callbacks.

## Storage matrix — all inventory rows

Nine B0 JSON goldens are immutable (no resource edits). New writers are compared
to those old snapshots using both JSON2 and the fixed old parser; actual typed
old readers and typed new readers additionally assert persisted bean semantics.

| Inventory row | Old → new, new → old, new → new evidence / limits |
| --- | --- |
| Generic preference / emoticon order | string-list golden parses into ordered String list with quote/slash/Unicode/null; old typed parseArray + new typed read; actual EmoticonOrderResolverTest retained. PreferenceUtils keys/write/error orchestration source-only |
| Search history | Same string-list three-way codec evidence; SearchModel cap20/three keys/dedup/removal unchanged by import migration, Android preference lifecycle source-only |
| Recent topics | topic-history golden + typed old/new reads asserts all ThreadPageInfo/ReplyInfo fields/defaults/order; manager cap40 and preference orchestration unchanged/source-only |
| Navigation description | Same bean golden plus ArticlePageCacheTest generated description decoded by old/new typed readers; supplied valid topicInfo preserves exact text/extensions. Intent UI source-only |
| Legacy cache | ArticleCacheStoreTest actual store/replay, old bean description and opaque original raw; knownLayouts/actualAppAndNormalParsersRoundTrip retain legacy paths and source. Description codec covered by topic golden; raw bytes never JSON-reserialized |
| Owned cache v1 | fixed owned-window through actual ArticleCacheCodec; old parser compares entire newly emitted envelope, old writer→new codec preserves metadata/raw; actual store tests preserve owner/source/layout/version/query rejection, explicit pageSize null and numeric types |
| Legacy ZIP | actual import/export/restore tests retain exact description/raw UTF-8 and validated paths, owned root untouched. Archive carries legacy opaque bytes, not a new JSON schema |
| Board list / remote raw | board-tree typed old/new recursion; actual bundled asset read; U1 strict remote decode/member/prefix tests. Context raw-file IO source-only, opaque remote text storage unchanged |
| Bookmarks / old preference | legacy-boards golden typed old/new readers checks fid/stid/name/head (read-only BoardKey was never restored by old reader); new board-tree via actual repository encoder/decoder additionally old typed decode; file atomic/backup/corrupt/valid-empty tests retained |
| Home order | string-array three-way codec evidence and actual HomeBoardOrder tests; null-write restoring default vs JSON string null remains unchanged source-reviewed Android preference wiring |
| Filter users / keywords | both fixed goldens typed old/new, sparse/null/legacy public fields/enabled; Pattern compiled before writer exclusion assertion. DataStore/global-preference transfer source-only, no changed keys or cleanup |
| Author location | fixed v1 old bytes→actual store; newly written separate file compared by old/new parsers and reread by actual store; Long timestamps/optional extension/null absence/5 entries + full TTL/bounds/corruption/isolation suite |
| Room accounts | not JSON: unchanged Room version1/columns/index preferences; no fabricated JSON DB test |
| Posting drafts/edit restore | not JSON: unchanged Bundle keys/body/title/anony and existing source/editor tests; no device recreation claim |
| AI draft/key settings | not JSON bean storage: existing encrypted record/store and editor-state tests retained; no key access or new format |

## Dependency and reflection evidence

`b2-dependencies-debug.txt` and `b2-dependencies-release.txt` show JSON2 2.0.59.android8
only on app runtime, shared through common/core. `b2-dependencies-kapt.txt`
separately shows ARouter compiler's fastjson1 1.2.69, a processor dependency.
App testImplementation old 1.1.71.android is a migration oracle, not runtime.
Production source scan (Java/Kotlin/Gradle/ProGuard, excluding tests/builds)
finds no `com.alibaba.fastjson.` or removed old version/config/serializer APIs.

`probes/b2-r8/` contains reproducible classfile fixture/source/input/result and
fresh mapping/configuration. Actual production classes, real Compose Board
state, current AGP8.6.1 embedded R8 8.6.27; no copied fake beans or fixture bean
keeps. Dynamic externally supplied names prevent inferred reflection keeps.
Original bean classes excluded from post-shrink classpath. All six bean families
pass: nested fields/aliases, canonical writer keys, cache/icon exclusion both
ways, compiled Pattern exclusion, report/avatar response fields. Actual app and
common keep rules retain JavaBean and Annotation/Signature metadata. Harness
helper obfuscation visible in mapping; no dontshrink/dontobfuscate.

Actual `:nga_phone_base_3.0:minifyReleaseWithR8 --dry-run` was attempted and failed
at existing build.gradle:30 signing guard. A read-only graph print identifies
`:nga_phone_base_3.0:packageReleaseResources` matching that broad guard; exact
output retained in `b2-r8-guard.log`. No guard bypass or credentials loaded.
**No current app R8 mapping/merged-rules evidence is claimed. U3-A7 and parent
acceptance remain pending through U4**; host classfile fixture is not a substitute
for actual app shrinking or minified Android device execution.

## Verification

- Focused full app/message tests passed after operation seams.
- `./gradlew assembleDebug testDebugUnitTest --console=plain` passed (48s,
  482 tasks). All 13 module test reports: **789 tests, zero failures/errors/skips**
  (app693/common63/core14/compose9/message2, eight other modules one each).
- Fresh `./gradlew lintDebug --continue --rerun-tasks --console=plain` passed
  (34s, all 536 tasks executed). All 13 XML reports inspected: zero Error/Fatal;
  existing warnings/informational issues remain. See `b2-lint-summary.txt`.
- `git diff --check` passed. Nine old goldens unchanged. JSON compatibility
  contract reviewed and consistent with this implementation.

Logs: `/tmp/u3-b2-debug.log`, `/tmp/u3-b2-operation-tests.log`,
`/tmp/u3-b2-lint.log`. Exact dependency/R8 evidence is copied into this research
folder. Product/test freeze reported to main before independent checker begins;
no B3 work or commit by implementer.
