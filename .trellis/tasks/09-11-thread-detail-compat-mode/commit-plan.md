# Proposed work commits

Implementation, independent review and required local checks are complete. The user approved these work commits, finish-work and pushing the feature branch on 2026-09-12. The two file groups below are unchanged from the presented plan.

- Worktree: `/home/toph/nga-just-works-compat-mode`
- Branch: `feature/thread-detail-compat-mode`
- Base: `5bb92cf033aa32d749d10e1a497bc05173cd2955`, including `6203dad5`.
- Final evidence: [delivery.md](delivery.md) and [independent-check.md](independent-check.md).
- These are work commits. Task archive and journal bookkeeping follow afterwards through the finish-work flow.

## 1. `docs(upstream): record August changes and adoption decisions`

保存上游 8 月提交盘点、源码证据、合成解析案例、同步策略和逐项采用清单。

Files (27):

- `.trellis/tasks/09-11-upstream-august-2026-review/check-results.md`
- `.trellis/tasks/09-11-upstream-august-2026-review/check.jsonl`
- `.trellis/tasks/09-11-upstream-august-2026-review/design.md`
- `.trellis/tasks/09-11-upstream-august-2026-review/implement.jsonl`
- `.trellis/tasks/09-11-upstream-august-2026-review/implement.md`
- `.trellis/tasks/09-11-upstream-august-2026-review/prd.md`
- `.trellis/tasks/09-11-upstream-august-2026-review/research/adoption-map.md`
- `.trellis/tasks/09-11-upstream-august-2026-review/research/branch-and-small-fixes.md`
- `.trellis/tasks/09-11-upstream-august-2026-review/research/bugfix-browser.md`
- `.trellis/tasks/09-11-upstream-august-2026-review/research/commit-inventory.json`
- `.trellis/tasks/09-11-upstream-august-2026-review/research/cross-check-json-board.md`
- `.trellis/tasks/09-11-upstream-august-2026-review/research/data-refactor.md`
- `.trellis/tasks/09-11-upstream-august-2026-review/research/json-compatibility.md`
- `.trellis/tasks/09-11-upstream-august-2026-review/research/merge-dry-run.json`
- `.trellis/tasks/09-11-upstream-august-2026-review/research/overview.md`
- `.trellis/tasks/09-11-upstream-august-2026-review/research/probes/JsonProbe.java`
- `.trellis/tasks/09-11-upstream-august-2026-review/research/probes/UploadJsonProbe.java`
- `.trellis/tasks/09-11-upstream-august-2026-review/research/probes/fixtures/escaped_quote.json`
- `.trellis/tasks/09-11-upstream-august-2026-review/research/probes/fixtures/hex_spaced_field.json`
- `.trellis/tasks/09-11-upstream-august-2026-review/research/probes/fixtures/hex_unknown_first.json`
- `.trellis/tasks/09-11-upstream-august-2026-review/research/probes/fixtures/hex_unknown_last.json`
- `.trellis/tasks/09-11-upstream-august-2026-review/research/probes/fixtures/nested_hex.json`
- `.trellis/tasks/09-11-upstream-august-2026-review/research/probes/fixtures/valid_control.json`
- `.trellis/tasks/09-11-upstream-august-2026-review/research/probes/result.tsv`
- `.trellis/tasks/09-11-upstream-august-2026-review/research/probes/upload-result.tsv`
- `.trellis/tasks/09-11-upstream-august-2026-review/research/sync-strategy.md`
- `.trellis/tasks/09-11-upstream-august-2026-review/task.json`

## 2. `feat(android): adapt upstream thread detail compatibility mode`

保存默认关闭的实验室兼容模式、上游请求/解析适配、原生阅读与分页定位、缓存隔离、必要的 core 评论渲染修复、相关回归测试，以及本功能的任务文档和执行规范。

Files (88):

- `.trellis/spec/backend/index.md`
- `.trellis/spec/backend/nga-platform-operation-registry.md`
- `.trellis/spec/backend/thread-detail-compat-contract.md`
- `.trellis/spec/backend/thread-page-cache-contract.md`
- `.trellis/spec/backend/thread-page-prefetch-contract.md`
- `.trellis/spec/frontend/component-guidelines.md`
- `.trellis/spec/frontend/index.md`
- `.trellis/tasks/09-11-thread-detail-compat-mode/.gitignore`
- `.trellis/tasks/09-11-thread-detail-compat-mode/check-results.md`
- `.trellis/tasks/09-11-thread-detail-compat-mode/check.jsonl`
- `.trellis/tasks/09-11-thread-detail-compat-mode/commit-plan.md`
- `.trellis/tasks/09-11-thread-detail-compat-mode/delivery.md`
- `.trellis/tasks/09-11-thread-detail-compat-mode/design.md`
- `.trellis/tasks/09-11-thread-detail-compat-mode/execution-review-notes.md`
- `.trellis/tasks/09-11-thread-detail-compat-mode/history/design-v1.md`
- `.trellis/tasks/09-11-thread-detail-compat-mode/history/implement-v1.md`
- `.trellis/tasks/09-11-thread-detail-compat-mode/history/planning-review-v1.md`
- `.trellis/tasks/09-11-thread-detail-compat-mode/implement.jsonl`
- `.trellis/tasks/09-11-thread-detail-compat-mode/implement.md`
- `.trellis/tasks/09-11-thread-detail-compat-mode/implementation-results.md`
- `.trellis/tasks/09-11-thread-detail-compat-mode/independent-check.md`
- `.trellis/tasks/09-11-thread-detail-compat-mode/lint-xml-summary.md`
- `.trellis/tasks/09-11-thread-detail-compat-mode/planning-review.md`
- `.trellis/tasks/09-11-thread-detail-compat-mode/prd.md`
- `.trellis/tasks/09-11-thread-detail-compat-mode/progress.md`
- `.trellis/tasks/09-11-thread-detail-compat-mode/research/content-reuse-revision.md`
- `.trellis/tasks/09-11-thread-detail-compat-mode/research/foreground-network-adaptation.md`
- `.trellis/tasks/09-11-thread-detail-compat-mode/research/pagination-cache-adaptation.md`
- `.trellis/tasks/09-11-thread-detail-compat-mode/research/parser-render-adaptation.md`
- `.trellis/tasks/09-11-thread-detail-compat-mode/research/query-pagination-revision.md`
- `.trellis/tasks/09-11-thread-detail-compat-mode/research/upstream-source.md`
- `.trellis/tasks/09-11-thread-detail-compat-mode/scope-revision.md`
- `.trellis/tasks/09-11-thread-detail-compat-mode/task.json`
- `lib_base_common/src/main/res/values/donottranslate.xml`
- `lib_core/src/main/java/gov/anzong/androidnga/core/corebuild/HtmlCommentBuilder.java`
- `lib_core/src/test/java/gov/anzong/androidnga/core/corebuild/HtmlCommentBuilderTest.java`
- `nga_phone_base_3.0/src/main/java/gov/anzong/androidnga/activity/ArticleCacheActivity.java`
- `nga_phone_base_3.0/src/main/java/gov/anzong/androidnga/activity/ArticleListActivity.java`
- `nga_phone_base_3.0/src/main/java/sp/phone/http/bean/ThreadData.java`
- `nga_phone_base_3.0/src/main/java/sp/phone/http/bean/ThreadRowInfo.java`
- `nga_phone_base_3.0/src/main/java/sp/phone/mvp/contract/ArticleListContract.java`
- `nga_phone_base_3.0/src/main/java/sp/phone/mvp/model/ArticleListModel.java`
- `nga_phone_base_3.0/src/main/java/sp/phone/mvp/model/TopicListModel.java`
- `nga_phone_base_3.0/src/main/java/sp/phone/mvp/model/convert/ArticleConvertFactory.java`
- `nga_phone_base_3.0/src/main/java/sp/phone/mvp/model/entity/ThreadPageInfo.java`
- `nga_phone_base_3.0/src/main/java/sp/phone/mvp/model/thread/AppArticleParser.kt`
- `nga_phone_base_3.0/src/main/java/sp/phone/mvp/model/thread/ArticleAccount.kt`
- `nga_phone_base_3.0/src/main/java/sp/phone/mvp/model/thread/ArticleAuthorSupport.kt`
- `nga_phone_base_3.0/src/main/java/sp/phone/mvp/model/thread/ArticleByteClient.kt`
- `nga_phone_base_3.0/src/main/java/sp/phone/mvp/model/thread/ArticleCache.kt`
- `nga_phone_base_3.0/src/main/java/sp/phone/mvp/model/thread/ArticleFailure.kt`
- `nga_phone_base_3.0/src/main/java/sp/phone/mvp/model/thread/ArticlePage.kt`
- `nga_phone_base_3.0/src/main/java/sp/phone/mvp/model/thread/ArticleReaderSession.kt`
- `nga_phone_base_3.0/src/main/java/sp/phone/mvp/model/thread/ArticleRowPresentation.kt`
- `nga_phone_base_3.0/src/main/java/sp/phone/mvp/model/thread/LegacyArticleCacheArchive.kt`
- `nga_phone_base_3.0/src/main/java/sp/phone/mvp/model/thread/NormalArticleParser.kt`
- `nga_phone_base_3.0/src/main/java/sp/phone/mvp/model/thread/ThreadAppBean.kt`
- `nga_phone_base_3.0/src/main/java/sp/phone/mvp/presenter/ArticleListPresenter.java`
- `nga_phone_base_3.0/src/main/java/sp/phone/mvp/presenter/ArticlePageCache.java`
- `nga_phone_base_3.0/src/main/java/sp/phone/mvp/presenter/ArticlePageRequestState.java`
- `nga_phone_base_3.0/src/main/java/sp/phone/mvp/presenter/TopicListPresenter.java`
- `nga_phone_base_3.0/src/main/java/sp/phone/mvp/viewmodel/ArticleShareViewModel.java`
- `nga_phone_base_3.0/src/main/java/sp/phone/param/ArticleListParam.java`
- `nga_phone_base_3.0/src/main/java/sp/phone/ui/adapter/ArticleListAdapter.java`
- `nga_phone_base_3.0/src/main/java/sp/phone/ui/adapter/ArticlePagerAdapter.java`
- `nga_phone_base_3.0/src/main/java/sp/phone/ui/adapter/TopicListAdapter.java`
- `nga_phone_base_3.0/src/main/java/sp/phone/ui/fragment/ArticleListFragment.java`
- `nga_phone_base_3.0/src/main/java/sp/phone/ui/fragment/ArticleSearchFragment.java`
- `nga_phone_base_3.0/src/main/java/sp/phone/ui/fragment/ArticleTabFragment.java`
- `nga_phone_base_3.0/src/main/java/sp/phone/ui/fragment/TopicCacheFragment.java`
- `nga_phone_base_3.0/src/main/java/sp/phone/ui/fragment/dialog/GotoDialogFragment.java`
- `nga_phone_base_3.0/src/main/java/sp/phone/util/FunctionUtils.java`
- `nga_phone_base_3.0/src/main/res/menu/menu_cache_list.xml`
- `nga_phone_base_3.0/src/main/res/values/strings.xml`
- `nga_phone_base_3.0/src/main/res/xml/settings_lab.xml`
- `nga_phone_base_3.0/src/test/java/sp/phone/common/DefaultSettingsContractTest.java`
- `nga_phone_base_3.0/src/test/java/sp/phone/mvp/model/thread/AppArticleParserTest.kt`
- `nga_phone_base_3.0/src/test/java/sp/phone/mvp/model/thread/ArticleByteClientTest.kt`
- `nga_phone_base_3.0/src/test/java/sp/phone/mvp/model/thread/ArticleCacheStoreTest.kt`
- `nga_phone_base_3.0/src/test/java/sp/phone/mvp/model/thread/ArticleErrorsTest.kt`
- `nga_phone_base_3.0/src/test/java/sp/phone/mvp/model/thread/ArticleReaderSessionTest.kt`
- `nga_phone_base_3.0/src/test/java/sp/phone/mvp/model/thread/ArticleRowPresentationTest.kt`
- `nga_phone_base_3.0/src/test/java/sp/phone/mvp/model/thread/NormalArticleParserTest.kt`
- `nga_phone_base_3.0/src/test/java/sp/phone/mvp/presenter/ArticleOwnedPageCacheTest.kt`
- `nga_phone_base_3.0/src/test/java/sp/phone/mvp/presenter/ArticlePageCacheTest.java`
- `nga_phone_base_3.0/src/test/java/sp/phone/mvp/presenter/ArticlePageRequestStateTest.java`
- `nga_phone_base_3.0/src/test/java/sp/phone/ui/fragment/ArticleReaderUiContractTest.kt`
- `nga_phone_base_3.0/src/test/java/sp/phone/ui/fragment/TopicPagePrefetchContractTest.kt`

## Files outside this plan

- No unrecognized dirty files exist in this worktree.
- Raw build logs are ignored by the task-local `.gitignore`; the committed reports preserve commands and results.
- Other worktrees are outside these commits. Root-main copies of the two task directories retain planning snapshots with links to the current worktree; before a future merge, reconcile those copies without losing changes.
- This plan includes no task archive or journal bookkeeping yet. Those follow the work commits.

## Confirmation boundary

Stage only the exact files in each group and create the two commits in order on the feature branch. Then archive the two completed tasks, record the session and push `feature/thread-detail-compat-mode` to `origin`, as the user requested. Do not amend; merging into main or publishing a release is not part of this instruction.

Workflow source: `.trellis/workflow.md`, Phase 3.4, step 5: “Present the plan once, ask for one-shot confirmation”.
