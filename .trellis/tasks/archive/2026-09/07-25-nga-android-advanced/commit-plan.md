# AI 任务提交方案

状态：用户已回复“行”，一次性确认以下工作提交与归档/日志顺序。
本文件保留获批清单，执行结果以 Git 历史及开发日志为准。
批准时工作树基线：`9e0b59d2`。快照共 100 个修改/新增文件，纳入 61 个，排除 39 个。

## 1. feat(android): add BYOK AI settings and contextual summaries

42 个 App 文件：独立加密配置与模型客户端、设置页、楼层/资料总结、共用弹窗、
离线测试与构建依赖。完整文件清单：

- `nga_phone_base_3.0/build.gradle`
- `nga_phone_base_3.0/src/androidTest/java/sp/phone/ai/AiConfigStoreInstrumentedTest.java`
- `nga_phone_base_3.0/src/main/java/gov/anzong/androidnga/activity/ProfileActivity.java`
- `nga_phone_base_3.0/src/main/java/sp/phone/ai/AiConfig.java`
- `nga_phone_base_3.0/src/main/java/sp/phone/ai/AiConfigRecord.java`
- `nga_phone_base_3.0/src/main/java/sp/phone/ai/AiConfigStore.java`
- `nga_phone_base_3.0/src/main/java/sp/phone/ai/AiError.java`
- `nga_phone_base_3.0/src/main/java/sp/phone/ai/AiResponseParser.java`
- `nga_phone_base_3.0/src/main/java/sp/phone/ai/AiSummaryClient.java`
- `nga_phone_base_3.0/src/main/java/sp/phone/ai/AndroidAiConfigFile.java`
- `nga_phone_base_3.0/src/main/java/sp/phone/ai/AndroidAiKeyProvider.java`
- `nga_phone_base_3.0/src/main/java/sp/phone/ai/SafeJsonParser.java`
- `nga_phone_base_3.0/src/main/java/sp/phone/ai/summary/AiSummarySources.java`
- `nga_phone_base_3.0/src/main/java/sp/phone/ai/summary/FloorSummaryInput.java`
- `nga_phone_base_3.0/src/main/java/sp/phone/ai/summary/NgaProfilePageSource.java`
- `nga_phone_base_3.0/src/main/java/sp/phone/ai/summary/ProfileSummaryInput.java`
- `nga_phone_base_3.0/src/main/java/sp/phone/ai/summary/ProfileSummaryLoader.java`
- `nga_phone_base_3.0/src/main/java/sp/phone/ai/summary/SummaryController.java`
- `nga_phone_base_3.0/src/main/java/sp/phone/ai/summary/SummaryText.java`
- `nga_phone_base_3.0/src/main/java/sp/phone/ui/fragment/ArticleListFragment.java`
- `nga_phone_base_3.0/src/main/java/sp/phone/ui/fragment/SettingsAiFragment.java`
- `nga_phone_base_3.0/src/main/java/sp/phone/ui/fragment/dialog/AiSummaryDialog.java`
- `nga_phone_base_3.0/src/main/res/layout/dialog_ai_summary.xml`
- `nga_phone_base_3.0/src/main/res/menu/article_list_context_menu.xml`
- `nga_phone_base_3.0/src/main/res/menu/article_list_context_menu_with_tid.xml`
- `nga_phone_base_3.0/src/main/res/menu/menu_user_profile.xml`
- `nga_phone_base_3.0/src/main/res/values/strings_ai_settings.xml`
- `nga_phone_base_3.0/src/main/res/values/strings_ai_summary.xml`
- `nga_phone_base_3.0/src/main/res/xml/settings.xml`
- `nga_phone_base_3.0/src/main/res/xml/settings_ai.xml`
- `nga_phone_base_3.0/src/test/java/sp/phone/ai/AiConfigRecordTest.java`
- `nga_phone_base_3.0/src/test/java/sp/phone/ai/AiConfigStoreTest.java`
- `nga_phone_base_3.0/src/test/java/sp/phone/ai/AiConfigTest.java`
- `nga_phone_base_3.0/src/test/java/sp/phone/ai/AiResponseParserTest.java`
- `nga_phone_base_3.0/src/test/java/sp/phone/ai/AiSummaryClientTest.java`
- `nga_phone_base_3.0/src/test/java/sp/phone/ai/summary/AiSummaryUiContractTest.java`
- `nga_phone_base_3.0/src/test/java/sp/phone/ai/summary/NgaProfilePageSourceTest.java`
- `nga_phone_base_3.0/src/test/java/sp/phone/ai/summary/ProfileSummaryLoaderTest.java`
- `nga_phone_base_3.0/src/test/java/sp/phone/ai/summary/SummaryControllerTest.java`
- `nga_phone_base_3.0/src/test/java/sp/phone/ai/summary/SummaryInputTest.java`
- `nga_phone_base_3.0/src/test/java/sp/phone/common/DefaultSettingsContractTest.java`
- `nga_phone_base_3.0/src/test/java/sp/phone/ui/fragment/AiSettingsContractTest.java`

## 2. docs(ai): record summary contracts and task verification

19 个规范/任务文件。该提交明确包含本任务原有的 6 个规划文件和 6 份研究文件，
不把它们冒充为本轮新研究。研究保留原有日期与证据，旧的双后端/聊天建议由
当前已批准 PRD 取代；本次产品只实现 BYOK 设置与两个总结入口。

完整文件清单：

- `.trellis/spec/backend/ai-summary-contract.md`
- `.trellis/spec/backend/index.md`
- `.trellis/spec/frontend/android-migration-architecture.md`
- `.trellis/spec/frontend/index.md`
- `.trellis/tasks/07-25-nga-android-advanced/check.jsonl`
- `.trellis/tasks/07-25-nga-android-advanced/commit-plan.md`
- `.trellis/tasks/07-25-nga-android-advanced/design.md`
- `.trellis/tasks/07-25-nga-android-advanced/implement.jsonl`
- `.trellis/tasks/07-25-nga-android-advanced/implement.md`
- `.trellis/tasks/07-25-nga-android-advanced/notes.md`
- `.trellis/tasks/07-25-nga-android-advanced/prd.md`
- `.trellis/tasks/07-25-nga-android-advanced/research/ai-call-mechanism-comparison.md`
- `.trellis/tasks/07-25-nga-android-advanced/research/android-system-genai-api-audit.md`
- `.trellis/tasks/07-25-nga-android-advanced/research/android-system-genai-open-source-audit.md`
- `.trellis/tasks/07-25-nga-android-advanced/research/harmony-system-ai-and-local-model-audit.md`
- `.trellis/tasks/07-25-nga-android-advanced/research/nga-harmony-ai-context.md`
- `.trellis/tasks/07-25-nga-android-advanced/research/nga-harmony-ai-source-audit.md`
- `.trellis/tasks/07-25-nga-android-advanced/task.json`
- `.trellis/tasks/07-25-nga-android-advanced/verification.md`

其中以下 6 份研究在本轮开始前已有未提交修改/新增，用户已一并确认原样保存
到本任务文档提交中：

- `.trellis/tasks/07-25-nga-android-advanced/research/ai-call-mechanism-comparison.md`
- `.trellis/tasks/07-25-nga-android-advanced/research/android-system-genai-api-audit.md`
- `.trellis/tasks/07-25-nga-android-advanced/research/android-system-genai-open-source-audit.md`
- `.trellis/tasks/07-25-nga-android-advanced/research/harmony-system-ai-and-local-model-audit.md`
- `.trellis/tasks/07-25-nga-android-advanced/research/nga-harmony-ai-context.md`
- `.trellis/tasks/07-25-nga-android-advanced/research/nga-harmony-ai-source-audit.md`

## 排除的原有 Trellis 修改

以下 39 个文件不是本次 AI 实现产物，不纳入上述提交；保留现有工作树内容。
提交时只按上面的确切路径暂存，不使用 `git add .` 或 `git add -A`。

- `.agents/skills/trellis-brainstorm/SKILL.md`
- `.agents/skills/trellis-continue/SKILL.md`
- `.agents/skills/trellis-meta/references/local-architecture/context-injection.md`
- `.agents/skills/trellis-meta/references/local-architecture/task-system.md`
- `.claude/commands/trellis/continue.md`
- `.claude/hooks/inject-subagent-context.py`
- `.claude/hooks/inject-workflow-state.py`
- `.claude/hooks/session-start.py`
- `.claude/skills/trellis-brainstorm/SKILL.md`
- `.claude/skills/trellis-meta/references/local-architecture/context-injection.md`
- `.claude/skills/trellis-meta/references/local-architecture/task-system.md`
- `.codex/hooks/inject-subagent-context.py`
- `.codex/hooks/inject-workflow-state.py`
- `.codex/hooks/session-start.py`
- `.grok/agents/trellis-check.md`
- `.grok/agents/trellis-implement.md`
- `.grok/commands/trellis-continue.md`
- `.grok/skills/trellis-brainstorm/SKILL.md`
- `.grok/skills/trellis-meta/references/local-architecture/context-injection.md`
- `.grok/skills/trellis-meta/references/local-architecture/task-system.md`
- `.trellis/.template-hashes.json`
- `.trellis/.version`
- `.trellis/scripts/add_session.py`
- `.trellis/scripts/common/__init__.py`
- `.trellis/scripts/common/active_task.py`
- `.trellis/scripts/common/config.py`
- `.trellis/scripts/common/developer.py`
- `.trellis/scripts/common/git.py`
- `.trellis/scripts/common/io.py`
- `.trellis/scripts/common/paths.py`
- `.trellis/scripts/common/safe_commit.py`
- `.trellis/scripts/common/task_context.py`
- `.trellis/scripts/common/task_store.py`
- `.trellis/scripts/common/task_utils.py`
- `.trellis/scripts/common/tasks.py`
- `.trellis/scripts/common/trellis_config.py`
- `.trellis/scripts/common/workflow_phase.py`
- `.trellis/scripts/task.py`
- `.trellis/workflow.md`

## 确认后的收尾

先依次创建上面两个工作提交，再运行 Trellis 归档和开发日志流程：

1. `chore(task): archive nga-android-advanced`：仅归档该任务目录及脚本维护的任务关系。
2. `chore: record journal`：记录验收结果、设备测试未执行的边界和工作提交哈希。

归档/日志提交在工作提交之后；不 amend，不 push，不创建或发布 Release。
Debug APK 和临时验证日志不进入提交。

按 `.trellis/workflow.md` Phase 3.4：
“Present the plan once, ask for one-shot confirmation”。
一次确认涵盖两个工作提交、明确列出的继承任务文档、39 个排除文件和后续归档/日志。
用户已回复“行”同意该范围与收尾顺序。
