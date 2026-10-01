# U3 B0 — old-library baseline and coverage map

Date: 2026-10-01. Worktree: `/home/toph/nga-just-works-upstream-adoption`.
Product baseline: `88ce8be3` (U2), preceded by U1 `4ccd7564` and integrated
`557f7bea` R1–R6/latest editor fixes. No production/dependency/helper changes
in B0. Main-session metadata edits are separate. Freeze here for independent
check/commit before B1.

## Scope and fixture provenance

The gap was missing fixed old-writer field evidence, not missing migration
architecture. Added nine synthetic JSON snapshots under app
`src/test/resources/json-legacy`, with real production bean/codec/store tests;
existing behavior suites are retained. No user/cache/account/message data was
read. No NGA, signing, publication, device or APK operation was performed.
Snapshot assertions compare parsed trees, never object key order/whitespace.
Raw response strings remain exact (including BOM, escapes and Unicode).

`b0-consumers.txt` is the current complete source scan. Before B0 there were
43 production direct fastjson1 consumers and 13 test consumers (the planning
12 omitted U1's ForumBoardIconRefreshTest). B0 adds LegacyStorageGoldenTest as
one additional direct-import test consumer. New message/notification tests call
production parsers without importing JSON. U1 getter/setter exclusion and strict
category/group/forum member validation remain in the baseline. U2 reader/core
media tests are included in the executed gate. No production consumer disappeared.

## Storage coverage — every inventory row

Paths below are production owners; test names are executable unless labelled
source-only. The B0 gate proves old→old. B2 still owes old→new, new→old and
new→new against these snapshots and the existing suites.

| Inventory storage row | B0 evidence | Limit / later obligation |
| --- | --- | --- |
| Generic List preference / EmoticonOrderStore | string-list golden uses actual JSON List/String decoder; EmoticonOrderResolverTest covers names, ordering, unknown/duplicate entries and default behavior | PreferenceUtils/EmoticonOrderStore Android static context and actual preference writes are not host-executed; retain keys/removal/error catch on migration review |
| Search history | string-list preserves Unicode, quote, slash, duplicates, null and empty/missing parse behavior | SearchModel's three keys, cap 20, dedup/deletion are source-traced, not invoked through Android preferences |
| Recent topics | topic-history golden constructs actual ThreadPageInfo/ReplyInfo, asserts every persisted key, sparse defaults, array order, aliases and round trip | TopicHistoryManager preference lifecycle/cap40 is source-only; no singleton mocking or test-local history algorithm |
| Detail navigation description | same actual bean golden; ArticlePageCacheTest preserves valid supplied topicInfo including extensions, selected-page snapshots, identity and Unicode blanks | Intent/Bundle UI navigation is not device-executed |
| Legacy topic cache | ArticleCacheStoreTest knownLayouts... and actualAppAndNormalParsersRoundTrip... use actual store/replay, legacy path, exact raw/topicInfo | Preserve legacy null-owner layout; no rewriting into owned records |
| Owned cache v1 | owned-window golden through ArticleCacheCodec; explicit pageSize null, Int keys, raw string; existing store tests cover source/layout/owner/version/query errors, account-change staging, symlinks, unknown windows and sparse pages | No JSON2 claims; generation intentionally not persisted |
| Legacy ZIP | ArticleCacheStoreTest legacyZipImportsOnlyValidatedLegacyPathsAndDoesNotTouchOwnedRoot executes actual import/export and restored store | ZIP contains legacy only; no invented owned export |
| Board local list / remote raw | board-tree golden checks nested/repeated actual BoardEntity fields and parent/icon exclusion; HomeBoardOrderContractTest reads actual bundled asset; ForumBoardIconRefreshTest covers cache-prefix bootstrap/strict remote member validation | Context-bound repository raw-file IO is source-traced; raw remote text remains an opaque string |
| Board bookmarks incl old preference | legacy-boards golden on actual Board; board-tree via actual bookmark encoder/decoder; ForumBoardBookmarkPersistenceTest covers file round trip, valid empty, dedup, backup and corrupt preservation; U1 tests exercise icon injection/exclusion/restore | Existing rollback test is a pure candidate-current rule, not an OS concurrent-write test; staged .tmp recovery remains source-reviewed |
| Home order | HomeBoardOrderTest + HomeBoardOrderContractTest, actual resolver and bundled tree | Preference write-null/default vs JSON string null remains source-only; no Android preference execution |
| Filter users/keywords | filter-users captures both legacy public m* and JavaBean names with sparse nulls; filter-keywords compiles Pattern before encode and verifies exclusion; FilterWordModelTest retained | DataStore/global-preference migration orchestration is not executed; never clear old source on parse failure |
| Author location | author-locations fixed v1 through actual store, Long timestamps, omitted null, OBSERVATION/FAILURE/RATE_LIMIT and optional networkExpires; full AuthorLocationStoreTest covers TTL, bounds, corruption, isolation and old-build cooldown shape | Existing atomic replacement code remains unchanged; no claim of injected filesystem crash |
| Room account DB | source-only: AppDatabase version1, users uid/cid/nick_name/avatar_url columns; User JSON golden is filter storage, not a DB schema test | JSON library never owns Room; no schema migration or DB data opened |
| Posting drafts/edit restore | existing InlineMediaEditorWiringTest/InlineMediaSourceTest, source trace TopicPostFragment body/title/anony and legacy anoay fallback; latest R6 retained | Bundle is not JSON. No fabricated draft fixture/schema; Android recreation not executed |
| AI draft/key configuration | AiConfigRecordTest/AiConfigStoreTest/AiModelEditorStateTest/AiProfilePromptEditorStateTest retained; real record crypto/file seams, editor state | Binary encrypted config and transient drafts are not JSON bean storage; no keys read or changed |

## Network, source, identity and error matrix

| Acceptance family | Reused / added executable evidence | Honest gap |
| --- | --- | --- |
| Ordinary/App body and source | NormalArticleParserTest, AppArticleParserTest, ArticleConvertFactoryTest, ArticleRowPresentationTest; scalar/structured/missing content, WP, attachments/comments-before-render, UID OP/anonymous, blacklist, metadata degradation | Full production Android HtmlConvertFactory/asset render not invoked; renderer seams and U2 core decoder execute |
| Query, page, account, generation | ArticleReaderSessionTest, ArticleByteClientTest, ArticleLaunchTargetTest, ArticleOwnedPageCacheTest, ArticlePageCacheTest, prefetch/request-state suites | Fake transports/local source contracts, not NGA availability or device UI |
| Error categories/fallback | ArticleErrorsTest, reader/parser tests: EMPTY/FORMAT/CONTENT/AUTH/ACCESS/RATE_LIMIT/BUSINESS, HTTP/reasons/raw separation | No change to any fallback qualification |
| AI/profile | full ai/profile suites; added decimalPrecisionAndNestedSpecialKeysRemainOrdinaryData verifies BigDecimal precision, Long > 2^53, nested @type/$ref as data | Existing 48-level/512KiB guards stay; no global parser configuration added |
| Categories/U1 | full board package: strict member validation, prefix isolation, observer, tree snapshot, recovery/order, icon getter/setter exclusion | No CDN/device success claim |
| U2 media | core ForumBasicDecoderMediaTest, PageAttachmentPrefixFlowTest, normal/App media tests | Existing U2 browser evidence inherited; B0 did not rerun CSS-only browser measurements |
| Active messages | added MessageParserGoldenTest invokes lib_bu_message parser: list wrapper, coordinates/order, detail allmsgs/userInfo, source newline, requested floor, missing optional user, business rejection | Pair display sections/platform formatting not asserted; send repository callback remains B1 gap. No malformed list-row test because old loop can fail without advancing; do not silently repair in migration |
| Notifications | added ForumNotificationGoldenTest: exact indices incl 7/8 PID fallback, reply-before-message order, unread, timestamps, wrapper, empty arrays | Error-envelope branch calls Android Log via NLog and cannot run on current host stub; no success/failure claim for that branch |
| TOPIC.LIST / preflight / uploads | archived six jdata fixture and fixed dual-library probe retained as planning evidence, actual ThreadPageInfo aliases/attachment bean covered | B1 must execute actual TopicConvertFactory bean seam and NGA upload/Noname response decoders including unquoted success, code9, errors, malformed envelopes; no Android callback/network retry claim in B0 |
| Actions and remote filters | existing FilterWordModelTest retained; Report/Like/PostComment/ProxyBridge/AvatarApply/SearchBoard source inventory retained | B1/B2 must add reachable operation-local success/error evidence where needed; generic JSON parsing alone would not certify their callbacks |

## Checks and observations

Executed:

```text
./gradlew :nga_phone_base_3.0:testDebugUnitTest --tests 'sp.phone.json.LegacyStorageGoldenTest' --console=plain
./gradlew :nga_phone_base_3.0:testDebugUnitTest :lib_core:testDebugUnitTest :lib_base_common:testDebugUnitTest :lib_bu_message:testDebugUnitTest --continue --console=plain
```

Final XML counts: app 669 tests/74 suites; core 14/4; common 63/4; message 1/1:
747 tests, zero failures/errors/skips. Active message tests live in the app
runtime (included in app count), not the message module's example suite.
Core/common/message tasks reused unchanged validated outputs; app executed.
Initial new-test compile iterations corrected unavailable Files.readString/
writeString Android compile APIs and a missing assertion qualifier. One
notification error assertion reached unmocked `android.util.Log.e`; that
unexecutable case is explicitly excluded and retained above as a B1 gap, not
relabelled as passed. No existing assertion was relaxed or removed.

All-module lint/build results on baseline88ce8be3 remain parent U2 evidence;
B0 adds tests/resources/docs only, so no redundant lint or APK assembly was run.
R8 and three-direction JSON2 compatibility are B2 obligations, not B0 results.

## Independent B0 check follow-up

The reviewer hardened the new author-location golden test: decode the input,
write to a distinct initially absent output file, require that output to exist,
then compare its parsed contents. This prevents AuthorLocationStore's swallowed
write failures from passing by leaving the input file unchanged. No golden or
production file changed. The final focused rerun of
`./gradlew :nga_phone_base_3.0:testDebugUnitTest --tests 'sp.phone.profile.AuthorLocationStoreTest' --console=plain`
passed all 9 tests with zero failures/errors/skips (2026-10-01T07:12:22 XML).
Java/Kotlin test compilation passed. The focused invocation replaces app test
XML with the selected class; the 747-test count above describes the earlier
complete B0 run, independently checked before this rerun, not the current
filtered report directory. Full lint was not rerun for this test-only fix;
13 inherited U2 lint XML reports were inspected and have zero Error/Fatal.
See [b0-check.md](b0-check.md) for the independent checkpoint review.
