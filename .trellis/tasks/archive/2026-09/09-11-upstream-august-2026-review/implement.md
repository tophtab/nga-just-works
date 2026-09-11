# 调研执行与验证记录

此清单记录本轮研究工作。它不是产品移植批准，也不要求运行 `task.py start`。

## 已执行研究

- [x] 加载 Trellis 上下文，获得任务创建和保存结论的明确同意。
- [x] 获取上游远端引用，固定 master、master_release 和 fork 比较 SHA。
- [x] 枚举 2026 年 8 月全部提交，保留作者/提交日期，验证跨分支补丁等价。
- [x] 查看全部 16 个 release 提交及 2 个 master 提交的实际差异。
- [x] 对数据重构与宽泛 bugfix 分别进行专题研究；主会话并行核对图床、版块及构建差异。
- [x] 对照当前 parser、图床、预取、排序、缓存和已规划迁移任务。
- [x] 执行两库 jdata 语法实验（24 组合）和上传无引号字段实验（6 组合）。
- [x] 保存完整清单、总览、专题证据、合成输入与实验结果。

## 最终检查

- [x] 16 + 2 个 SHA 与 Git 日期筛选清单完全一致，无遗漏或额外提交。
- [x] 图床/版本记录分别通过 `git patch-id --stable` 等价检查。
- [x] 专题文件明确固定版本、原仓库路径/行号、事实与静态推断的区别。
- [x] 确认上传语法和 jdata 修复不被错误列为当前 fork 已复现故障。
- [x] 确认动态图标只更新偏好但不一定立即重算的问题；不误称 750871b3 删除了非空判断。
- [x] 新增 API 的默认值、触发条件、缓存/分页/图片/预取限制已记录。
- [x] 不将同名旧私信 parser 清理误称删除私信功能，不将热评字段删除误称已删除热评界面。
- [x] 检查本任务文档链接、清单覆盖、探针结果数量及产物范围；记录最终结果。
- [x] 完成 PRD 收敛及供用户阅读的中文结论。

主要检查命令/方法：

```bash
git ls-remote --symref upstream-justwen HEAD 'refs/heads/*'
git log --since='2026-08-01T00:00:00+08:00' --until='2026-09-01T00:00:00+08:00' upstream-justwen/master_release
git show <commit>
git diff --shortstat bd7da348 upstream-justwen/master_release
git status --short
```

JSON 探针的 javac/java 复现步骤见 `research/json-compatibility.md`。这些是独立 JVM 研究实验，不是 Android App 单测或端到端测试。

## 后续工作范围

用户可以从 `research/overview.md` 的候选项选择后续目标。进入产品实现前，需要按选定行为重新限定 PRD/验收条件，保留已有规范和本报告记录的兼容性边界。不能把研究任务创建同意视为合并上游或部署批准。

本轮只写本任务目录。并发工作区中的其他任务不属于本轮修改范围，不能一并归档、提交或清理。
