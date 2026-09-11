# Research verification — 2026-09-11

## Result

本轮调研验收完成。总览位于 [research/overview.md](research/overview.md)，PRD 的 R1—R5 均有对应结果。任务保留 planning 状态作为研究与后续选项记录；没有启动产品实施。

## Completed checks

| 检查 | 结果与边界 |
| --- | --- |
| 远端与基线 | 已 fetch，并用 ls-remote 核对 `master@cdad1abb`、`master_release@22ba3082`；比较固定在本轮开始时 `main@8284c703` |
| 提交覆盖 | JSON 清单与 Git 日期筛选集合相等：16 个 release + 2 个 master；总览恰好列出 16 个 release SHA |
| 重复补丁 | `31c1c820/8862cdd4` 和 `cdad1abb/bd7da348` 分别具有相同稳定 patch-id |
| 全部主题 | 数据/清理专题覆盖 9 笔；宽泛修复/备用接口专题覆盖 2 笔；主会话覆盖图床、jdata、图标和两笔发版 |
| jdata 对照 | 6 合成输入 × 2 库 × 原始/上游过滤 = 24 组合；18 个解析成功、6 个预期 JSON 拒绝，与报告表格一致 |
| 上传语法对照 | 2 合成输入 × 3 模式 = 6 组合；5 个解析成功、1 个预期 JSON 拒绝，与报告表格一致 |
| 研究交叉核对 | `research/cross-check-json-board.md` 未发现图标/JSON 报告的具体事实错误；该轮复核未重复执行 Git/实验 |
| 文档/结果完整性 | 本任务 Markdown 本地链接全部存在、无尾空格或 TBD；探针结果数量与异常分布符合结论；没有复制 class/JAR 产物 |
| Trellis 上下文 | `python3 .trellis/scripts/task.py validate .trellis/tasks/09-11-upstream-august-2026-review` 通过；implement 5 条、check 4 条真实规范/研究条目 |

## Limits and workspace ownership

- 本次没有执行 Android 构建、安装、设备测试或真实 NGA 请求。JSON 实验只证明列出的库与合成输入，不是整个网络或上传功能验证。
- 新 API 可用性、服务端字段实际含义、具体 UI 故障触发率和性能未验证；报告已区分源码事实与静态风险。
- 本轮源码检查结束时，另一并行任务已切换到 `fix/thread-menu-cache` 并修改 Activity、Presenter、Fragment、菜单等文件。它们不是本任务改动；本报告没有把这些未提交状态当作 `8284c703` 的内容，也不对它们做清理或提交。
- 本任务仅写 `.trellis/tasks/09-11-upstream-august-2026-review/`，并在 `/tmp/nga-upstream-august-2026-review` 保存临时参考源码与实验编译产物；没有修改产品源码或共享规范。
- PRD 已做最终收敛：确认事实归入 Background，R1—R5 与 AC1—AC5 一一对应，没有阻塞本轮调研的未决问题。后续产品范围属于另一次选择。

## Follow-up: upstream synchronization feasibility

- 用户进一步询问整体同步上游与按代码适配的取舍，仍属研究范围。
- 固定 `main@8284c703` 和上游 `22ba3082`，使用 `git merge-tree` 进行仅写 Git 对象的三方模拟：整体合并有 17 个冲突路径。
- 以 `750871b3` 为显式基线模拟应用 `2becba2a` 的差异，有 7 个冲突路径；不是实际执行 cherry-pick。
- 完整元数据见 `research/merge-dry-run.json`，判断与局限见 `research/sync-strategy.md`。两个返回码 1 都是预期的冲突结果；未生成实际分支/索引/工作目录修改，没有编译或验证候选合并代码。
