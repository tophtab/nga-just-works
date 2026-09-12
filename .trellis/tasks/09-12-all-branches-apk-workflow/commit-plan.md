# 提交与同步计划

维护者已授权 commit、finish-work、push，并于 2026-09-12 批准将公共 CI
改动同步到全部现存分支。IP/加载提示分支随后被并行工作删除，实际目标为
其余三个分支。下列分组沿用该授权，不再重复请求确认；提交只在
最终质量检查通过后执行。

## 1. `ci(android): publish APK previews from every branch`

公共修改，可单独 cherry-pick 到其他分支：

- `.github/workflows/build.yml`
- `scripts/test_release_workflow.py`
- `nga_phone_base_3.0/src/test/java/gov/anzong/androidnga/ReleaseWorkflowContractTest.kt`
- `README.md`
- `.trellis/spec/backend/android-quality-guidelines.md`

没有计划增加独立的通用辅助模块；发布身份处理复用现有 workflow 的身份
步骤，并增加只计算原始 ref 摘要的前置 job，避免 GitHub 并发组大小写
折叠。若最终检查引入必要的额外文件，在提交前更新本清单。

## 2. `docs(ci): record all-branch APK rollout and validation`

本任务目录下的 PRD、设计、执行计划、来源研究、JSONL 上下文、任务元数据、
实现/检查结果、交付与分支同步记录；日志 `*.log` 不提交。该记录提交
保留在兼容模式分支，无需为更新其他分支的构建配置而移植任务归档。

## 收尾提交

完成公共提交同步及本地验证后，使用 Trellis 自动生成任务归档和开发日志
提交。普通快进推送现有三个分支；不 amend、不 force-push、不创建稳定标签。

## 不属于本任务的改动

开始时各产品 worktree 均干净；最后提交前再次分类，保留并排除新出现的
无关改动。每个分支的 App 功能差异均不属于本次公共提交。

main 的另一项 Trellis 更新及加载提示改动保留在原工作区。AI 原先的功能
WIP 已由另一处工作提交并推送；同步采用其最新 `5288e895` 基线，保留
README 新增致谢及全部功能代码。

## 执行结果

| 分支 | 公共 CI 落地提交 | 处理 |
| --- | --- | --- |
| `feature/thread-detail-compat-mode` | `8ceb57d9e73dd1476cea32ecd21175451c3818bd` | 已审查的五文件公共提交 |
| `feature/ai-summary` | `dc601c9390979b0a35e367156e0af58634448f0e` | 基于 `5288e895` 移植并快进接收；README 保留新致谢 |
| `main` | `b918b4a4181d0b189c19a7671be65d00535bb787` | 并行工作已合入同一 `8ceb57d9`；最终树与独立准备的公共移植完全一致 |

独立审查通过且没有剩余问题。完整验证为 36 项 Python 测试、9 项 JVM
发布契约测试、actionlint、7 段 Bash 语法检查；移植后的 main/AI 另通过
四项定向 smoke 检查。三个分支的四个公共代码/规范文件完全一致；main
与兼容模式 README 一致，AI 仅保留其最新基线已有的额外致谢。

任务记录使用本计划第 2 项提交，随后由 Trellis 归档及记录日志；工作提交
引用、验证范围和推送方式见 `delivery.md`。
