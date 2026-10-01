# U1–U4 整体规划核查记录

当前交接补充（2026-09-30）：用户已整体批准，指定新会话实施；原工作区随后被清理，文档现已恢复到主仓库。下文保留规划/审核时的历史状态，当前授权与工作区以父任务approval.md、handoff.md和recovery.md为准。

日期：2026-09-30。范围：设计、实施计划与研究证据；不是产品验收。统一审核入口为 [review.md](review.md)。

## 专项审核与闭环

| 审核 | 已落实的修正 |
| --- | --- |
| [U1](../09-30-upstream-board-icons/research/planning-review.md) | 只接受实际 result 数组；插入去重保留原 map 已知 ID 范围；错误类型 preference 安全回退；实际树递归与结构通知分开 |
| [U2](../09-30-upstream-video-support/research/planning-review.md) | 区分正则原文与 Java 字符串；URI 分段保留尾空段；encoded-dot 检查不区分大小写；保留旧 typed/absolute 行为 |
| [U3](../09-30-upstream-data-layer/research/planning-review.md) | Topic tree 使用已验证的 DisableReferenceDetect，再局部 SupportSmartMatch；固定单次解析/core shape/app 错误映射；B2 产生本次 R8 证据，classfile 与 Android DEX 分开；实际 minify 未完成时不得提前关闭 U3 |
| [U4](../09-30-upstream-sdk-36/research/planning-review.md) | Android16+ 运行时限定；APK 与 plain merged manifest 使用各自检查工具；fake 返回策略与生命周期/手势证据分开；缩减 fixture 不冒充 Android runtime 验证 |
| [跨任务](research/joint-planning-review.md) | U2/父计划不再暗示搬迁 HtmlData、改草稿类型或新增返回确认；B2 删除旧 runtime/keep，B5 清无消费者工具；固定 U3/U4/父任务最终验收关闭条件 |

所有实施计划的完整质量门均直接安排一次规范要求的全模块 `lintDebug --rerun-tasks`，配合实际 XML 的 0 Error/Fatal 检查；不先跑普通 lint 再机械重复。已有检查若仍有效且代码未变更，不重复执行。

## 证据等级

- 已有证据：固定上游和当前源码、全部 JSON 消费者/存储清单、Android16 官方适用性矩阵、两个固定版本 JSON 的合成 JVM probe、旧 AAR/APK native 静态检查。
- 本轮产品代码、Android 构建、产品单测、lint、实际 app R8、离线媒体浏览器验收尚未执行。探测源码只用于设计研究，不代表实现功能已通过测试。
- 设备、真实 NGA、签名打包、提交、发布均未执行；没有恢复 R7 产品修复。

## 文档和任务状态

- 沿用父任务及原四个子任务，均有 PRD、design、implement、implement/check context；没有新建任务。
- 全部保持 `status=planning`、`implementation_approved=false`；规划元数据为 `all_designs_ready_for_joint_review`。
- 独立专项审核和父级联合审核均纳入相应 context；大型规范仍要求分段读全，不把注入截断视为全文。
- 最终验证结果在下方记录；用户统一确认仍未取得，任何子任务尚未 start 实施。

## 工作区保全

- 工作区 `/home/toph/nga-just-works-upstream-adoption`，分支 `feat/upstream-adoption`，基线 `1a8413d9`。
- 源工作区初始复制的 36 个文件 SHA-256 全部仍一致；其中 14 个 R7 归档文件在独立工作区也与初始副本一致。
- `git diff --name-only HEAD` 为空；仅有五任务规划/研究与带入的 R7 引用归档未跟踪文件。没有复制或覆盖源工作区其他会话的未提交产品、测试或规范。
- 后续正式整合遵循 [并行工作边界](research/concurrent-work-boundaries.md)，不将未合入的 R1–R6 版本称为已联合验证。

## 最终校验结果

- 五个任务分别执行 `task.py validate`，全部退出码 0；父任务新增联合审核 context 后再次校验通过。
- 35 份 Markdown、90 个本地 Markdown 链接、7 份 JSON、118 条 JSONL context 检查通过；必需产物、relatedFiles、父子关系、planning/未批准状态均正确，无悬空引用或尾随空白。
- 未发现生成的 class/JAR/APK/AAR/SO/DEX、范围外未跟踪文件或 tracked diff；源目录 36 文件和归档副本的校验和复核通过。
- validator 仅有已知的 32 KiB 注入截断警告，涉及 android-quality-guidelines、component-guidelines、ai-summary-contract 三份现行规范。计划和 context 已要求实施者分段读全；不修改全局注入配置，也不把警告隐藏成完整注入。
- 旧逐项审批或 HtmlData/草稿搬迁表述扫描只命中禁止恢复的提醒、历史审核发现及修正记录，当前执行方案没有这些残留要求。

核查已完成；等待用户对四项整体方案确认，实施复选项继续保持未勾选。
