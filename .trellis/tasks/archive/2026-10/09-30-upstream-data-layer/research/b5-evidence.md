# U3 B5 — unused parser removal and final consumer audit

Date: 2026-10-01. Worktree `nga-just-works-upstream-adoption`, branch
`feat/upstream-adoption`, base integration checkpoint `0eebe1e5` (B4
`5595c8ed` plus upstream reply-search navigation `c768a3cb`). Code frozen;
main owns checkpoint/spec/task status. U3 remains pending actual app R8 in U4.

## Deletion boundary and call-site proof

The product diff is pure deletion:190 lines from ArticleConvertFactory and the
350-line unused app MessageConvertFactory. No product/test behavior is added.

- Removed ArticleConvertFactory private `buildThreadPageInfo`,
  `buildThreadRowList`, `convertJsObjToList`, `isSourceScalar`,
  `hasCompleteSource`, `buildRowContent`, `buildRowVote`, `buildRowHotReplay`,
  `buildRowComment`, `buildRowClientInfo`, `buildRowUserInfo`, and their now-unused
  imports. Repository source reference searches found only calls inside this
  superseded private helper cluster. The live facade already calls core decode
  and ReadThreadLegacyMapper; all frozen parity suites survive the deletion.
- Preserved all facade public methods, raw/wrapper/error handling,
  `resolveAttachmentsPrefix` (existing attachment contract tests), `renderRow`,
  `buildHtmlData` and `buildAttachmentData`. Live mapper uses NgaImageHost and
  ArticleAuthorSupport; AppArticleParser, NormalArticleParser and these shared
  helpers have no B5 changes.
- Deleted only `sp.phone.mvp.model.convert.MessageConvertFactory`. Source/XML/
  keep-rule searches found no external reference. Active
  `com.justwen.androidnga.module.message.MessageConvertFactory` remains used by
  MessageRepository, MessageDetailRepository and MessageParserGoldenTest.
- Archived B4 oracle source stays outside executable tests. No deleted helper
  is loaded reflectively by current tests. Baseline resources stay immutable.

## Final direct-consumer reconciliation

The original43-file inventory maps to41 current JSON2 files, one removed unused
copy and one caller now delegating JSON serialization to ArticleNavigation.
This table preserves every original inventory row rather than treating an
empty old-import search as complete migration proof.

| Original source | Final disposition |
| --- | --- |
| `lib_base_common/src/main/java/gov/anzong/androidnga/base/util/PreferenceUtils.java` | JSON2 (including annotations/local options where applicable) |
| `lib_bu_message/src/main/java/com/justwen/androidnga/module/message/MessageConvertFactory.java` | JSON2 (including annotations/local options where applicable) |
| `lib_bu_message/src/main/java/com/justwen/androidnga/module/message/compose/post/MessagePostRepository.kt` | JSON2 (including annotations/local options where applicable) |
| `nga_phone_base_3.0/src/main/java/gov/anzong/androidnga/activity/AvatarPostActivity.java` | JSON2 (including annotations/local options where applicable) |
| `nga_phone_base_3.0/src/main/java/gov/anzong/androidnga/activity/compose/SearchModel.kt` | JSON2 (including annotations/local options where applicable) |
| `nga_phone_base_3.0/src/main/java/gov/anzong/androidnga/activity/compose/board/ForumBoardModel.kt` | JSON2 (including annotations/local options where applicable) |
| `nga_phone_base_3.0/src/main/java/gov/anzong/androidnga/activity/compose/board/ForumBoardRepository.kt` | JSON2 (including annotations/local options where applicable) |
| `nga_phone_base_3.0/src/main/java/gov/anzong/androidnga/activity/compose/board/HomeBoardOrder.kt` | JSON2 (including annotations/local options where applicable) |
| `nga_phone_base_3.0/src/main/java/gov/anzong/androidnga/activity/compose/board/data/BoardEntity.kt` | JSON2 (including annotations/local options where applicable) |
| `nga_phone_base_3.0/src/main/java/gov/anzong/androidnga/activity/compose/filter/FilterKeyword.java` | JSON2 (including annotations/local options where applicable) |
| `nga_phone_base_3.0/src/main/java/gov/anzong/androidnga/activity/compose/filter/FilterManager.kt` | JSON2 (including annotations/local options where applicable) |
| `nga_phone_base_3.0/src/main/java/gov/anzong/androidnga/activity/compose/filter/FilterWordModel.kt` | JSON2 (including annotations/local options where applicable) |
| `nga_phone_base_3.0/src/main/java/sp/phone/ai/AiMessageAccumulator.java` | JSON2 (including annotations/local options where applicable) |
| `nga_phone_base_3.0/src/main/java/sp/phone/ai/AiResponseParser.java` | JSON2 (including annotations/local options where applicable) |
| `nga_phone_base_3.0/src/main/java/sp/phone/ai/AiSummaryClient.java` | JSON2 (including annotations/local options where applicable) |
| `nga_phone_base_3.0/src/main/java/sp/phone/ai/SafeJsonParser.java` | JSON2 (including annotations/local options where applicable) |
| `nga_phone_base_3.0/src/main/java/sp/phone/ai/summary/NgaProfilePageSource.java` | JSON2 (including annotations/local options where applicable) |
| `nga_phone_base_3.0/src/main/java/sp/phone/common/TopicHistoryManager.java` | JSON2 (including annotations/local options where applicable) |
| `nga_phone_base_3.0/src/main/java/sp/phone/mvp/model/TopicPostModel.java` | JSON2 (including annotations/local options where applicable) |
| `nga_phone_base_3.0/src/main/java/sp/phone/mvp/model/convert/ArticleConvertFactory.java` | JSON2 (including annotations/local options where applicable) |
| `nga_phone_base_3.0/src/main/java/sp/phone/mvp/model/convert/ErrorConvertFactory.java` | JSON2 (including annotations/local options where applicable) |
| `nga_phone_base_3.0/src/main/java/sp/phone/mvp/model/convert/ForumNotificationFactory.java` | JSON2 (including annotations/local options where applicable) |
| `nga_phone_base_3.0/src/main/java/sp/phone/mvp/model/convert/MessageConvertFactory.java` | Deleted unused app copy; active lib_bu_message parser retained |
| `nga_phone_base_3.0/src/main/java/sp/phone/mvp/model/convert/TopicConvertFactory.java` | JSON2 (including annotations/local options where applicable) |
| `nga_phone_base_3.0/src/main/java/sp/phone/mvp/model/entity/ThreadPageInfo.java` | JSON2 (including annotations/local options where applicable) |
| `nga_phone_base_3.0/src/main/java/sp/phone/mvp/model/thread/ArticleCache.kt` | JSON2 (including annotations/local options where applicable) |
| `nga_phone_base_3.0/src/main/java/sp/phone/mvp/model/thread/ArticleFailure.kt` | JSON2 (including annotations/local options where applicable) |
| `nga_phone_base_3.0/src/main/java/sp/phone/mvp/model/thread/ArticlePage.kt` | JSON2 (including annotations/local options where applicable) |
| `nga_phone_base_3.0/src/main/java/sp/phone/mvp/model/thread/ThreadAppBean.kt` | JSON2 (including annotations/local options where applicable) |
| `nga_phone_base_3.0/src/main/java/sp/phone/mvp/presenter/ArticlePageCache.java` | JSON2 (including annotations/local options where applicable) |
| `nga_phone_base_3.0/src/main/java/sp/phone/profile/AuthorLocationStore.java` | JSON2 (including annotations/local options where applicable) |
| `nga_phone_base_3.0/src/main/java/sp/phone/profile/ProfileEnvelopeParser.java` | JSON2 (including annotations/local options where applicable) |
| `nga_phone_base_3.0/src/main/java/sp/phone/profile/ProfileLocationParser.java` | JSON2 (including annotations/local options where applicable) |
| `nga_phone_base_3.0/src/main/java/sp/phone/profile/ProfileWebUserParser.java` | JSON2 (including annotations/local options where applicable) |
| `nga_phone_base_3.0/src/main/java/sp/phone/proxy/ProxyBridge.java` | JSON2 (including annotations/local options where applicable) |
| `nga_phone_base_3.0/src/main/java/sp/phone/task/AvatarFileUploadTask.java` | JSON2 (including annotations/local options where applicable) |
| `nga_phone_base_3.0/src/main/java/sp/phone/task/JsonProfileLoadTask.java` | JSON2 (including annotations/local options where applicable) |
| `nga_phone_base_3.0/src/main/java/sp/phone/task/LikeTask.java` | JSON2 (including annotations/local options where applicable) |
| `nga_phone_base_3.0/src/main/java/sp/phone/task/PostCommentTask.java` | JSON2 (including annotations/local options where applicable) |
| `nga_phone_base_3.0/src/main/java/sp/phone/task/ReportTask.java` | JSON2 (including annotations/local options where applicable) |
| `nga_phone_base_3.0/src/main/java/sp/phone/task/SearchBoardTask.java` | JSON2 (including annotations/local options where applicable) |
| `nga_phone_base_3.0/src/main/java/sp/phone/ui/fragment/TopicHistoryFragment.java` | JSON2 (including annotations/local options where applicable) |
| `nga_phone_base_3.0/src/main/java/sp/phone/ui/fragment/TopicSearchFragment.java` | Delegates to ArticleNavigation.fromSearchResult; JSON2 serialization in ArticlePage.kt |

Additional/current JSON2 source paths outside those original43 (the introduced decoder/projection) were also enumerated:

- `lib_core/src/main/java/gov/anzong/androidnga/core/thread/ReadThreadWireDecoder.kt`
- `nga_phone_base_3.0/src/main/java/sp/phone/mvp/model/convert/ReadThreadBeanFallbacks.kt`

Upstream reply-search integration was separately committed before B5: both the
new ArticlePage serializer and ReplySearchNavigationTest use JSON2. The new test
is an active migration consumer, not an old-library oracle. Its four tests and
all affected navigation/parser tests pass in the final full gate.

Production `src/main` search for `com.alibaba.fastjson.`, `SerializerFeature`
and `ParserConfig` returns no matches. The root old version variable and old
fastjson keep were removed in B2; common/core/catalog resolve fixed
`com.alibaba.fastjson2:fastjson2:2.0.59.android8`. No global smart/auto-type flag
or fallback library was introduced.

Remaining executable fastjson1 references are deliberate old-read compatibility
oracles in AuthorLocationStoreTest, LegacyStorageGoldenTest,
ArticleCacheStoreTest and ArticlePageCacheTest. The app testImplementation is
`com.alibaba:fastjson:1.1.71.android`. Research/history/probe sources are not APK
consumers. ARouter's processor dependency is classified separately below.

## Preserved boundary and combined regression evidence

Core thread decoder/DTOs have no app/Android context, account or persistent cache
imports. Its userCache is per-decode UID association only. The narrow invalid
row-path interface and app-local fallback projection retain the B4 contract;
this batch changes neither. App keeps source/query/errors/cache/render state.

The full existing test gate covers U1 board icon persistence/exclusions and
ordering, U2 media parsing/prefix/source preservation, all storage/operation
JSON2 compatibility tests, strict/legacy/App readers, the fixed156/104/72 parity
outcomes, and the integrated reply-search navigation. No baseline/test weakening
was needed for deletion. Bundle draft keys, Room schema, filenames and stored
raw/topicInfo formats are untouched. Device checks were not run per policy;
no real NGA traffic, signing guard bypass or publishing occurred.

## Verification

- `:nga_phone_base_3.0:assembleDebug testDebugUnitTest --continue --console=plain`
  passed in88s,366 tasks. All13 XML test directories inspected: **816 tests**,
  app703/core31, zero failures/errors/skips (`/tmp/u3-b5-debug.log`).
- Fresh `lintDebug --continue --rerun-tasks --console=plain` passed in141s,
  all538 tasks executed. All13 XML reports inspected: zero Error/Fatal
  (`/tmp/u3-b5-lint.log`).
- Separate debug/release `dependencyInsight --dependency fastjson` runs pass:
  both runtime classpaths contain only JSON2 `2.0.59.android8`. Logs
  `/tmp/u3-b5-runtime-debug.log` and `/tmp/u3-b5-runtime-dependencies.log`
  (the latter reports releaseRuntimeClasspath).
- Current `dependencies --configuration kapt` confirms ARouter compiler1.5.2
  brings fastjson1.2.69 only to processors (`/tmp/u3-b5-kapt.log`); this is not
  APK runtime. Test-only1.1.71.android remains the explicit old-format oracle.
- All six B4 input/baseline SHA256 values match the immutable evidence recorded
  in b4-evidence.md; no fixtures were regenerated.
- `git diff --check` passes. Production changes are exactly540 deleted lines in
  two files. Main's concurrent task/spec metadata edits are not B5 product work.
- Actual app R8 remains **not completed**, pending U4 because of the existing
  signing guard. Prior B2 classfile reflection evidence does not substitute for
  current app minification. B5 code completion does not close/archive U3.
