# U1 规划核查记录

当前交接补充（2026-09-30）：用户已整体批准，指定新会话实施；原工作区随后被清理，文档现已恢复到主仓库。下文保留规划/审核时的历史状态，当前授权与工作区以父任务approval.md、handoff.md和recovery.md为准。

日期：2026-09-30。范围：规划产物和本地源码证据，不是产品验收。

## 独立研究核查闭环

研究子代理的 [方案核查](research/planning-review.md) 没有发现架构阻塞，提出三项需明确的边界，现已写入 design/implement：

- R1：原始顶层必须是 object，result 必须是实际数组；空数组可更新前缀，缺失/null/非数组不能更新。
- R2：新增去重沿用应用时 boardMap 已知 ID（包括收藏），接受插入后逐次更新；全对象图标刷新另遍历实际树，避免混淆。
- R3：prefix preference 错误类型或读取异常局部回退到原始缓存/default，不能中断本地初始化。

上述为设计明确化，尚未实施或运行行为测试。实现时必须验证真实 fastjson 注解行为与 Compose Snapshot 观察通知。

## 文档与隔离检查

- U1 implement/check 清单均包含真实规范与研究文件；移除未填充 database 模板项。
- 五个既有任务均为 planning，implementation_approved=false；父子关系保持原有四项。
- 本轮文档只写入独立工作区；源工作区初始复制的 36 个任务/归档文件校验和仍一致。
- 独立工作区 tracked diff 为空，仅增加/更新任务目录文档；无产品代码改动。
- task.py validate 与 Markdown/JSONL 本地引用检查见本轮工具结果。大规范的 32 KiB 注入警告是已知限制，implement.md 已要求代理分段读取完整内容。
- 未运行 Gradle 构建、产品单测、lint、设备操作、真实 NGA 请求、提交或发布。

此处记录的是 U1 局部规划核查，纳入 U1–U4 整体方案；用户统一审核通过前不得 start 任何子任务。其他三项的最终状态见父任务 review.md，R7 产品修复继续搁置。
