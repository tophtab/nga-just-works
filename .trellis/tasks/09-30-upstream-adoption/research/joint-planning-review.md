# Research: U1–U4 整体规划交叉审核

当前交接补充（2026-09-30）：用户已整体批准，指定新会话实施；原工作区随后被清理，文档现已恢复到主仓库。下文保留规划/审核时的历史状态，当前授权与工作区以父任务approval.md、handoff.md和recovery.md为准。

- Query: 复核父任务和全部子任务设计/实施计划之间的共享契约、依赖顺序、可执行质量门及统一审批边界。
- Scope: internal；只读规划与已有研究证据，少量核对 settings.gradle/源码签名 guard；不重做源码盘点。
- Date: 2026-09-30
- 工作区：`/home/toph/nga-just-works-upstream-adoption`。本次只写父任务 research，不修改产品或运行构建、测试、设备、网络。

## Findings

结论：四项已形成完整的采用/适配方案，默认 U1 → U2 → U3 B0–B5 → U4 顺序可执行，未发现需要新增任务或改变产品范围的架构矛盾。发现的残留 scope 表述和质量门精度问题已反馈主会话；关闭记录如下。所有代码工作仍须等待用户对 U1–U4 的一次整体确认，研究与规划审核不构成实施批准。

### 1. 需要收口的旧 scope 文字（中，主会话已承接）

**位置：** U2 `design.md` 的代码与验证边界末段；父 `research/concurrent-work-boundaries.md` 的 R5/R6 行。

U2 仍写“U3 后续搬迁 HtmlData/渲染边界”，父表还写“迁移 HtmlData/decoder import”“迁移草稿/HTML 数据类型”“U4 返回确认流程”。这些是旧预期残留，与最终 U3/U4 确定方案冲突：

- U3 明确保留 core HtmlData/AttachmentData/CommentData 和现有公共展示接口；新增的是 wire DTO/core decoder 与 app mapper。
- 草稿仍为已有 Bundle 状态，没有 schema 或草稿数据类型迁移计划。
- U4 只适配系统 back dispatcher 和既有面板消费/fallback，不新增返回确认语义。

**修订要求：**统一改为“U3 调整 JSON/wire/mapper 边界时保留现有 HtmlData、媒体、R5 表情和 R6 源文/预览/Bundle 契约；U4 返回分发适配保留已有编辑行为”。主会话已明确承接这些文档修订。产品文件范围无需扩大。

### 2. U3 × U4 的实际 R8 门不能被替代证据关闭（已修正）

初始材料将“独立 classfile shrinker fixture”和“实际 app Release minify/mapping”并列，但未明确任务图受签名 guard 阻挡时谁保持未完成。

审核期间主会话已在 U3 `implement.md` 和 U4 `implement.md` 写明：

- classfile fixture 必须使用同工具链 R8、真实生产 bean/keep 规则和动态反射输入，避免专用全类 keep 或常量可达性掩盖问题。
- 实际 app Release 是 Android DEX，不拿它在 JVM 运行，也不把 classfile fixture 当作实际 app shrink 成功。
- 检查本次 dry-run 后若能无签名执行实际 `minifyReleaseWithR8` 就执行并查看本次产物；否则保留具体未完成项，不弱化 guard 或读签名秘密。
- B5 可以标代码批次完成，但 U3 不因缺实际 R8 证据而提前关闭；U4 必须补齐联合证据。U3/U4/父任务所需门未满足时都不能报告完成。

这解决了 U3 完成判定与父 S3→S4 顺序的潜在冲突。U4 工具链升级后重跑 U1 的注解排除和 U2 媒体 fixture 仍在计划内。

### 3. 最终 lint 命令统一到真实质量规范（主会话正统一）

本轮主会话核对 `.trellis/spec/backend/android-quality-guidelines.md` 的 Validation gate，发现最终全模块 lint 明列 `--rerun-tasks`。初稿只在报告不可信时才加该参数，与项目规定不完全一致。

**确定处理：**最终 gate 分成 Debug assemble + 全模块 test，以及单次全模块 `lintDebug --continue --rerun-tasks --console=plain`；U4 正式 gate 继续带 `--offline`。不要先不带参数完整跑 lint 再重复。父/四子任务命令和解释须一致；所有 Android 模块 XML 的 Error/Fatal 为 0 仍是硬门。

这是检查命令收口，不改变实现范围，也不需要用户新增许可。主会话已承接统一修改。

## 已通过的交叉面

| 交叉面 | 审核结论 |
| --- | --- |
| U1 → U3 JSON 注解 | U1 使用现阶段 fastjson1 双向排除派生 iconUrl，U3 B2 在完整切换时迁移 getter/setter 注解；旧树/收藏、反射和 shrinker fixture 都覆盖，无“先切一半” API 窗口 |
| U1 前缀 vs U2 附件前缀 | 两条路径刻意分离；板块图标不复用附件设置，也不把 CDN host 加入携带账号头的请求范围；U2 只消费现有 NgaImageHost 完整结果 |
| U2 → U3 普通/兼容渲染 | U3 保留 AppArticleParser 与公共 renderer；ordinary wire/mapper 调整不改 U2 tags、源文、评论/签名或图床；U3 U4 均重跑 U2 fixture |
| U2 语法 | 先前候选正则表示、split 尾空段和编码点大小写问题已在 U2 design/implement 明确，无正则/Java escaping 歧义 |
| U3 JSON 原子切换 | B2 同时切全部生产/测试 API、注解、runtime 依赖与 keep；B5 仅清理确定无消费者工具。父表已同步，不再把删旧库错误延后到 B5 |
| U3 DTO 边界 | 当前 JSON 已由 app 入口取得，core 不再次 parse/修复 raw；core 自有 wire/mode，不依赖 app ArticleSource/ArticleFailure；app 映射保留精确 FORMAT/CONTENT/null/sourceUnavailable 分支 |
| U3 本地兼容 | 全存储三方向语义往返；U1 iconUrl 不进入文件；Bundle/Room/schema 不升级，原 raw/topicInfo 保留；无清缓存策略 |
| U3 → U4 工具链 | 先以旧工具链隔离 JSON/模型行为，U4 再改 AGP/Gradle/Kotlin/SDK；JVM17、min29、Release minify/Preview非minify 均保持；无循环实施依赖 |
| U4 CI/平台 | SDK pair、platform 安装和 APK target fixture 同步；Build Tools/JDK/签名/版本规则保留；back dispatcher 是具体平台适配，不扩展编辑器或读取策略 |
| U4 原生/设备结论 | 新 Debug APK 重查 ELF/zipalign，已有研究不能代替新产物；对齐、离线布局、classfile fixture、Android 设备运行被区分，不以未经授权设备测试作为交付前置 |
| 外部 R1–R6 | 独立工作区、不复制脏源码；正式交付按稳定点整合并记录版本，不以旧基线报告宣称已验证外部修改 |
| 审批/任务生命周期 | 父子任务沿用；整体获批后顺序 start 子任务，切批不重复申请实施许可；没有产品实施、发布、设备或真实 NGA 调用的提前授权 |

## Files Found / Review Scope

已读取以下任务目录的 `design.md` 和 `implement.md`：

- `.trellis/tasks/09-30-upstream-adoption/`
- `.trellis/tasks/09-30-upstream-board-icons/`
- `.trellis/tasks/09-30-upstream-video-support/`
- `.trellis/tasks/09-30-upstream-data-layer/`
- `.trellis/tasks/09-30-upstream-sdk-36/`

补充核对：父并行工作边界；U3 consumer/migration 研究；U4 platform-toolchain 研究；U2 前次 planning-review；`settings.gradle` 的 13 模块；当前 app build.gradle 的 Release/Preview 签名 guard。未重新审计全部消费者或平台 API，子任务研究证据作为依据。

## Related Specs / External References

- `.trellis/workflow.md`：规划产物、统一审核后 start 与父子职责。
- `.trellis/spec/backend/android-quality-guidelines.md`：Debug/JVM/全部 lint、设备 opt-in。
- `.trellis/spec/backend/thread-detail-compat-contract.md`：源/展示/账号/分页/错误及完整附件前缀。
- 本次未新增外部检索；各子任务固定上游、JSON 库实验与官方工具链资料沿用原 research 引用。

## Caveats / Not Found

- 这是规划一致性审核，不是产品 build/test/R8/布局或运行验证。
- 审核期间主会话仍在更新最终文档；上面两项“主会话承接”的文字收口应由最终引用/文本检查确认写入，但无需继续扩展研究或新建任务。
- 未发现必须向用户额外询问的技术选型；用户下一步只需审核完整 U1–U4 方案。

## 主会话修订闭环（2026-09-30）

上述旧 scope 表述、B2/B5 删除时点、实际 R8 未完成判定与五份实施计划的单次完整 lint 命令均已修正。最终文档和 context 校验通过，详情见父任务 planning-validation.md；本记录保留审核时发现的原问题供追溯。
