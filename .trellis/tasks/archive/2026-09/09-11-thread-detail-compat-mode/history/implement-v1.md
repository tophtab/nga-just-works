# Implementation plan — 帖子详情兼容模式

> 已过期：执行清单需按 scope-revision.md 与新版 prd.md 修订。下文保留历史，不代表当前范围，也不满足启动门槛；不得以本清单排除 PID/作者查询或所有非20分页。

## Entry gate and baseline

- 当前 planning，任务创建已获授权。最终规划摘要得到后续明确实施批准后，才 `task.py start` 并派发实现；本文件不是已完成实现记录。
- 实现分支计划 `feature/thread-detail-compat-mode`，基于 `5bb92cf033aa32d749d10e1a497bc05173cd2955` 或确认包含它的更新基线，必须保留 `6203dad5`。不从旧 main 覆盖缓存修复，不 merge/cherry-pick 上游整笔提交。
- 启动前复查 dirty paths，保留本任务与先前 August review 文档。干净 root 可建功能分支；出现别的产品 WIP 则用隔离 worktree。现有 AI summary worktree 不动。
- 本任务是一个端到端功能，以下步骤有依赖，不拆成并行修改 Presenter 的多个任务。
- Context 校验提示 android-quality-guidelines.md 与 component-guidelines.md 超过单文件注入上限。implement/check agent 都必须用读取工具读原文件（可分段），不能只依赖截断后的自动注入；其他角色上下文照 manifest 加载。
- auto 模式按 workflow 派发 `trellis-implement`，随后独立 `trellis-check`。prompt 必须以 `Active task: .trellis/tasks/archive/2026-09/09-11-thread-detail-compat-mode` 开始，声明子 agent 直接实现/检查，不再递归派发；清楚分配文件，提醒保留其他人的修改。

## Ordered checklist

### 1. Pure contracts and adapter

- [ ] 加载 before-dev、PRD/design、implement.jsonl 及 pinned source；确认变更面和保留 Java/Rx 边缘的理由。
- [ ] app Kotlin DTO、显式类型/存在性读取、page/content admission、精确 quote normalization。
- [ ] 合成首/中/末页和拒绝 fixtures，标明非真实抓包；最小可测 request/error/attempt policy。
- [ ] 保存上游 attribution，不复制未检查 result[0]、截断 image prefix、忽略 code 或静默丢字段。

### 2. Native renderer reuse

- [ ] ArticleConvertFactory 共享 renderer/client/anonymous leaf；legacy WP 和用户 JSON 解析保留。
- [ ] 映射 ThreadData/ThreadRowInfo、页面完整 prefix、用户/屏蔽、源文/HTML/raw 分离。
- [ ] 最小 UI 适配：普通行 override、未知 score visibility、未知 identity 的动作/引用保护；保留已删除 floor 菜单和独立 support/oppose/poll。
- [ ] 旧图片、附件/评论顺序、avatar、WP 和 quote/source 等价回归；不改 core。

### 3. Foreground operation

- [ ] endpoint-specific Kotlin client + legacy Rx facade，严格 origin/account/headers、byte/charset/limit/error、无 redirect/retry、DETACH。
- [ ] Model 新方法/分类适配；normal wire/parser 不替换，不动全局 String converter。
- [ ] Presenter normal-first → one App → terminal，generation/account/settings 重检与统一 loading 收尾。
- [ ] 保留独立 prefetch callback/state/READY；旧 retry 先 count 再 next Cookie，拒绝空/相同 Cookie。
- [ ] paused/stale/cancel 抑制副作用；WebView 保留查询上下文；新增 default-off 实验室资源。

### 4. Cache integration

- [ ] ThreadData 只含非秘密 provenance；immutable write record 复用 ArticlePageCache.prepare。
- [ ] codec/store 实现 owner root、v1 read_php/app_api envelope、原 legacy raw 和严格 dispatch。
- [ ] list/open/read/delete 的统一 index，真实页码、owner 验证/Parcelable、标题隔离和账号变更处理。
- [ ] 新页优先、同页普通保存更新、损坏页不换源、其他 owner 隔离；metadata 原文/补齐和缓存页签不回退。
- [ ] 后台有界写入，完整成功后反馈；旧 zip 范围说明准确，新 root 不意外打包或降为共享 raw。

### 5. Full review and handoff

- [ ] 执行下列 feature gates，保存 check-results.md；修复新失败后按需重跑。
- [ ] 主会话用 update-spec 审实际发现，将新增 operation、本地接收边界、cache/prefetch/UI delta 写入 English specs。
- [ ] check agent 审完整功能 diff、artifacts 和所有受影响模块；不能只审最后一小块。
- [ ] 结果报告说明实现、离线检查、真实协议和新 cache zip 限制；没实现/没检查不得标完成。
- [ ] 按 workflow 提供具体 commit 分组供一次审阅，随后 finish/archive/journal；无授权不 push/merge/release。规划阶段不执行提交步骤。

## Offline behavior matrix

| Group | Required cases |
| --- | --- |
| Paging | single opener、20-row page、final 1-row；vrows vs rowNum；缺 currentPage 但楼层证明；错页/overflow/empty/mixed tid/duplicate/非20拒绝 |
| Optional/source | missing author、合法/损坏匿名、client/mute/blacklist；精确 HTML quote vs BBCode、literal $/backslash、幂等；source/raw 保留 |
| Unsupported | 非空 attches/hot_post/comment_to_id/html_head_extra、true/非法 isTieTiao、0/[]/{} 哨兵、未知 code；无 partial render/cache |
| Renderer | 自动/手动完整 prefix、两页上下文、旧 attachment/comment 顺序、avatar、WP；unknown score 与 recycled legacy visibility |
| Policy | 四开关组合，0/1/2 账号，空/相同 next Cookie，同账号 App，switch mid-request，刷新 normal-first，无 duplicate/sticky |
| Foreground | READY/refresh、prefetch coalescing/promotion/failure/pause、DETACH、旧代次；后台无 Toast/browser/App |
| Transport | method/URL/form/headers，allowlist/suffix/port/path/损坏 origin，redirect/retry，UTF-8/GBK/缺失与非法 charset，size/truncation，401/403/429/network/cancel |
| Cache | legacy + 两种 envelope，开关关闭回读，稀疏页2/7，schema/version/tid/page/owner错误，Unicode metadata，A/B同帖同页，update/delete/switch，无网络fallback |
| Export | 旧流程仍只导出/导入 legacy；新 root 排除且范围说明准确 |

保留 ArticlePageCacheTest、ArticlePageRequestStateTest、ArticlePagePrefetchPlannerTest、TopicPagePrefetchContractTest、ArticlePageRefreshContractTest、ArticleConvertFactoryTest、PageAttachmentPrefixFlowTest、FunctionUtilsAvatarTest 和 common NgaImageHost tests。新增测试断言输出/副作用，不仅匹配实现方法名。

## Validation commands

先跑新增 pure/fake tests 和相关回归，再执行默认 gate：

```bash
./gradlew :nga_phone_base_3.0:testDebugUnitTest :lib_base_common:testDebugUnitTest --console=plain
./gradlew :nga_phone_base_3.0:assembleDebug :nga_phone_base_3.0:lintDebug --console=plain
./gradlew lintDebug --continue --rerun-tasks --console=plain
./gradlew testDebugUnitTest --continue --console=plain
```

按 android-quality-guidelines 的 XML 校验脚本逐一检查 settings.gradle 中所有 Android module 报告：全部存在，0 Error / 0 Fatal。进程退出0不等于 lint 合格。

最后一条是 repository debug 诊断。已知 lib_base_ui/lib_bu_statistics 缺 JUnit、lib_core Android-dependent ExampleUnitTest、lib_module_debug KAPT 示例失败单独记录；不掩盖，也不为了它们改无关依赖。如果实现实际修改这些模块，需要处理其 owned baseline 或回到范围审阅。

不运行 aggregate test（会进入签名变体）、签名打包、真实 NGA、账号凭据读取、ADB/connected/install/device、push/release。默认验收不要求用户配合设备操作。

## Risk/rollback and activation checklist

- renderer 抽取必须旧链等价；Presenter 不能覆盖预取；未知协议不能靠猜测让 fixture 变绿。
- owner/title/body/index/delete 成套验证，不清空/迁移旧缓存，不双 parser 试错，不触其他账号。
- SDK 29/35/35、依赖版本及现有登录不变；新 cache zip 明确延期。
- 默认 off 可停止 App 请求；已存新页仍可本机回读，旧文件不因撤回代码而丢失。

- [ ] 用户在最终规划摘要后明确批准实施。
- [ ] PRD/design/implement 收敛，implement.jsonl/check.jsonl 有真实上下文且 validate 通过。
- [ ] 实现基线含6203dad5，其他 WIP 已隔离。
- [ ] 最新摘要与技术/延期范围一致；发生实质变更重新审阅。
