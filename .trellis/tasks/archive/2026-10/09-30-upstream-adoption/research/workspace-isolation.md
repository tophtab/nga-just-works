# 独立工作区与未提交资料接续

当前交接补充（2026-09-30）：用户已整体批准，指定新会话实施；原工作区随后被清理，文档现已恢复到主仓库。下文保留规划/审核时的历史状态，当前授权与工作区以父任务approval.md、handoff.md和recovery.md为准。

- 创建日期：2026-09-30。
- 源工作区：`/home/toph/nga-just-works`，源分支 `feat/reader-access-location-navigation`。
- 本轮工作区：`/home/toph/nga-just-works-upstream-adoption`，分支 `feat/upstream-adoption`。
- 已提交基线：`1a8413d9`。Git 工作区从该基线创建，未 stash、reset、checkout 或覆盖源工作区。
- 初始复制五个现有 upstream 任务目录，以及引用所需的 `archive/2026-09/09-30-reader-native-loading-research`，共 36 个文件，逐文件 SHA-256 校验一致。
- 源任务目录保留原样；本轮后续文档更新只在独立工作区。初始校验清单保存在本机 `/tmp/upstream-adoption-worktree-copy.json`；它是临时审计材料，不是后续工作必要依赖。
- 复制 R7 归档是保持引用可读，不是恢复 R7 任务或纳入产品修复。归档原文不修改。
- 其他会话 R1–R6 的未提交产品代码、测试、规范未复制；最终集成应基于那些会话真实交付处理共享文件，不能用本分支验证结果替代它们。
- 没有本轮产品修改、构建、设备操作、真实 NGA 请求、提交或发布。U1–U4 均待整体方案统一审核。

## 整体规划与统一审核

| 交付 | 当前状态 | 下一步 |
| --- | --- | --- |
| U1 板块图标 | design/implement 已细化，planning | 等待四项整体方案统一审核 |
| U2 视频 | 详细设计/计划齐备，planning | 等待四项整体方案统一审核 |
| U3 数据层/JSON | 详细设计/计划齐备，planning | 完整消费者与B0–B5已齐备，等待统一审核 |
| U4 SDK 36 | 详细设计/计划齐备，planning | 完整平台/工具链计划已齐备，等待统一审核 |

父任务只负责范围与最终集成，不作为本轮实现目标；不新增第五类任务。

用户最新要求：所有分析和计划全部完成后统一确认。独立交付/回滚边界不等于逐项重新申请批准。
