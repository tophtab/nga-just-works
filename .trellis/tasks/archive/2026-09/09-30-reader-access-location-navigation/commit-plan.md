# Approved commit batch

User approved commit, finish-work, push and a new stable release on 2026-09-30. Execute the six code commits and docs below, add release notes for 6.1.0, archive this completed task tree, journal, and push main plus the stable tag. Unrelated task changes remain excluded.

## fix: unify emoticon asset mapping

- `lib_base_common/src/main/java/gov/anzong/androidnga/common/util/EmoticonUtils.java`
- `lib_base_common/src/test/java/gov/anzong/androidnga/common/util/EmoticonUtilsContractTest.java`
- `lib_core/src/main/java/gov/anzong/androidnga/core/decode/ForumEmoticonDecoder.java`
- `lib_core/src/test/java/gov/anzong/androidnga/core/decode/ForumEmoticonDecoderTest.java`
- `nga_phone_base_3.0/src/main/java/sp/phone/util/StringUtils.java`
- `nga_phone_base_3.0/src/test/java/sp/phone/util/LegacyEmoticonTest.java`

## fix: scope author location work to foreground threads

Also stage only the Activity-owner binding hunk from `ArticleListFragment.java`; the navigation hunks belong to R4.

- `nga_phone_base_3.0/src/main/java/sp/phone/profile/AuthorLocationCache.java`
- `nga_phone_base_3.0/src/main/java/sp/phone/profile/AuthorLocationPage.java`
- `nga_phone_base_3.0/src/main/java/sp/phone/profile/AuthorLocationRepository.java`
- `nga_phone_base_3.0/src/main/java/sp/phone/profile/AuthorLocationService.java`
- `nga_phone_base_3.0/src/main/java/sp/phone/profile/AuthorLocationStore.java`
- `nga_phone_base_3.0/src/main/java/sp/phone/profile/ProfileLocationResult.java`
- `nga_phone_base_3.0/src/main/java/sp/phone/profile/ProfileLocationTransport.java`
- `nga_phone_base_3.0/src/test/java/sp/phone/profile/AuthorLocationPageTest.java`
- `nga_phone_base_3.0/src/test/java/sp/phone/profile/AuthorLocationRepositoryTest.java`
- `nga_phone_base_3.0/src/test/java/sp/phone/profile/AuthorLocationStoreTest.java`
- `nga_phone_base_3.0/src/test/java/sp/phone/profile/ProfileLocationTransportTest.java`
- `nga_phone_base_3.0/src/test/java/sp/phone/ui/fragment/ArticleAuthorLocationContractTest.kt`

## fix: display evidence-based article errors

- `nga_phone_base_3.0/src/main/java/sp/phone/mvp/model/ArticleListModel.java`
- `nga_phone_base_3.0/src/main/java/sp/phone/mvp/model/thread/ArticleFailure.kt`
- `nga_phone_base_3.0/src/test/java/sp/phone/mvp/model/thread/ArticleByteClientTest.kt`
- `nga_phone_base_3.0/src/test/java/sp/phone/mvp/model/thread/ArticleErrorsTest.kt`

## feat: locate original reply when showing full thread

- `nga_phone_base_3.0/src/main/java/sp/phone/mvp/model/thread/ArticlePage.kt`
- `nga_phone_base_3.0/src/main/java/sp/phone/mvp/model/thread/ArticleReaderSession.kt`
- `nga_phone_base_3.0/src/main/java/sp/phone/mvp/presenter/ArticleListPresenter.java`
- `nga_phone_base_3.0/src/main/java/sp/phone/mvp/viewmodel/ArticleShareViewModel.java`
- `nga_phone_base_3.0/src/main/java/sp/phone/param/ArticleListParam.java`
- `nga_phone_base_3.0/src/main/java/sp/phone/ui/fragment/ArticleListFragment.java`
- `nga_phone_base_3.0/src/test/java/sp/phone/mvp/model/thread/ArticleReaderSessionTest.kt`
- `nga_phone_base_3.0/src/test/java/sp/phone/mvp/viewmodel/ArticleLaunchTargetTest.kt`
- `nga_phone_base_3.0/src/test/java/sp/phone/ui/fragment/ArticleReaderUiContractTest.kt`

## feat: handle NGA topic and reply app links

- `nga_phone_base_3.0/src/main/AndroidManifest.xml`
- `nga_phone_base_3.0/src/main/java/gov/anzong/androidnga/activity/ArticleListActivity.java`
- `nga_phone_base_3.0/src/main/java/sp/phone/param/ArticleLinkParser.java`
- `nga_phone_base_3.0/src/test/java/sp/phone/param/ArticleLinkEntryContractTest.kt`
- `nga_phone_base_3.0/src/test/java/sp/phone/param/ArticleLinkParserTest.java`

## feat: restore source-preserving editor media previews

- `nga_phone_base_3.0/src/main/java/sp/phone/mvp/presenter/TopicPostPresenter.java`
- `nga_phone_base_3.0/src/main/java/sp/phone/ui/fragment/TopicPostFragment.java`
- `nga_phone_base_3.0/src/main/java/sp/phone/view/editor/InlineMediaDecorator.java`
- `nga_phone_base_3.0/src/main/java/sp/phone/view/editor/InlineMediaEditText.java`
- `nga_phone_base_3.0/src/main/java/sp/phone/view/editor/InlineMediaLoadQueue.java`
- `nga_phone_base_3.0/src/main/java/sp/phone/view/editor/InlineMediaSource.java`
- `nga_phone_base_3.0/src/main/res/layout/fragment_topic_post.xml`
- `nga_phone_base_3.0/src/test/java/sp/phone/view/editor/InlineMediaEditorWiringTest.java`
- `nga_phone_base_3.0/src/test/java/sp/phone/view/editor/InlineMediaLoadQueueTest.java`
- `nga_phone_base_3.0/src/test/java/sp/phone/view/editor/InlineMediaSourceTest.java`

## docs: record reader contracts and R1–R6 verification

- `.trellis/spec/backend/author-profile-location-contract.md`
- `.trellis/spec/backend/nga-platform-operation-registry.md`
- `.trellis/spec/backend/thread-detail-compat-contract.md`
- `.trellis/spec/backend/thread-page-prefetch-contract.md`
- `.trellis/spec/frontend/component-guidelines.md`
- `.trellis/spec/frontend/editor-inline-media-contract.md`
- `.trellis/spec/frontend/index.md`
- `.trellis/tasks/09-30-reader-access-error-messages/check.jsonl`
- `.trellis/tasks/09-30-reader-access-error-messages/design.md`
- `.trellis/tasks/09-30-reader-access-error-messages/implement.jsonl`
- `.trellis/tasks/09-30-reader-access-error-messages/implement.md`
- `.trellis/tasks/09-30-reader-access-error-messages/prd.md`
- `.trellis/tasks/09-30-reader-access-error-messages/task.json`
- `.trellis/tasks/09-30-reader-access-error-messages/verification.md`
- `.trellis/tasks/09-30-reader-access-location-navigation/check.jsonl`
- `.trellis/tasks/09-30-reader-access-location-navigation/commit-plan.md`
- `.trellis/tasks/09-30-reader-access-location-navigation/design.md`
- `.trellis/tasks/09-30-reader-access-location-navigation/handoff.md`
- `.trellis/tasks/09-30-reader-access-location-navigation/implement.jsonl`
- `.trellis/tasks/09-30-reader-access-location-navigation/implement.md`
- `.trellis/tasks/09-30-reader-access-location-navigation/integration-verification.md`
- `.trellis/tasks/09-30-reader-access-location-navigation/prd.md`
- `.trellis/tasks/09-30-reader-access-location-navigation/research/current-behavior.md`
- `.trellis/tasks/09-30-reader-access-location-navigation/research/followup-script-links-editor.md`
- `.trellis/tasks/09-30-reader-access-location-navigation/task.json`
- `.trellis/tasks/09-30-reader-author-location-lifecycle/check.jsonl`
- `.trellis/tasks/09-30-reader-author-location-lifecycle/design.md`
- `.trellis/tasks/09-30-reader-author-location-lifecycle/implement.jsonl`
- `.trellis/tasks/09-30-reader-author-location-lifecycle/implement.md`
- `.trellis/tasks/09-30-reader-author-location-lifecycle/prd.md`
- `.trellis/tasks/09-30-reader-author-location-lifecycle/task.json`
- `.trellis/tasks/09-30-reader-author-location-lifecycle/verification.md`
- `.trellis/tasks/09-30-reader-edit-inline-media/check.jsonl`
- `.trellis/tasks/09-30-reader-edit-inline-media/design.md`
- `.trellis/tasks/09-30-reader-edit-inline-media/implement.jsonl`
- `.trellis/tasks/09-30-reader-edit-inline-media/implement.md`
- `.trellis/tasks/09-30-reader-edit-inline-media/prd.md`
- `.trellis/tasks/09-30-reader-edit-inline-media/task.json`
- `.trellis/tasks/09-30-reader-edit-inline-media/verification.md`
- `.trellis/tasks/09-30-reader-emoticon-rendering/check.jsonl`
- `.trellis/tasks/09-30-reader-emoticon-rendering/design.md`
- `.trellis/tasks/09-30-reader-emoticon-rendering/implement.jsonl`
- `.trellis/tasks/09-30-reader-emoticon-rendering/implement.md`
- `.trellis/tasks/09-30-reader-emoticon-rendering/prd.md`
- `.trellis/tasks/09-30-reader-emoticon-rendering/task.json`
- `.trellis/tasks/09-30-reader-emoticon-rendering/verification.md`
- `.trellis/tasks/09-30-reader-reply-floor-navigation/check.jsonl`
- `.trellis/tasks/09-30-reader-reply-floor-navigation/design.md`
- `.trellis/tasks/09-30-reader-reply-floor-navigation/implement.jsonl`
- `.trellis/tasks/09-30-reader-reply-floor-navigation/implement.md`
- `.trellis/tasks/09-30-reader-reply-floor-navigation/prd.md`
- `.trellis/tasks/09-30-reader-reply-floor-navigation/task.json`
- `.trellis/tasks/09-30-reader-reply-floor-navigation/verification.md`
- `.trellis/tasks/09-30-reader-web-app-links/check.jsonl`
- `.trellis/tasks/09-30-reader-web-app-links/design.md`
- `.trellis/tasks/09-30-reader-web-app-links/implement.jsonl`
- `.trellis/tasks/09-30-reader-web-app-links/implement.md`
- `.trellis/tasks/09-30-reader-web-app-links/prd.md`
- `.trellis/tasks/09-30-reader-web-app-links/task.json`
- `.trellis/tasks/09-30-reader-web-app-links/verification.md`

## Other work excluded

The new upstream task directories and archived reader-native-loading research are separate work. They are excluded from this batch. Existing parent-task organization edits remain intact; review their inclusion with the task documentation commit.

Workflow source: `.trellis/workflow.md`, Phase 3.4 step 5: “Present the plan once, ask for one-shot confirmation”. User confirmation received; push and release are now also explicitly authorized.
