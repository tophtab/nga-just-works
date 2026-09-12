# 所有分支 APK 构建交付记录

日期：2026-09-12。公共实现及三个现存分支的本地同步已完成，最终独立
审查通过，无剩余代码、测试或规范问题。

## 最终行为

- 所有分支的代码推送触发签名预览 APK 构建；仅 Markdown / `.trellis/**`
  改动沿用既有跳过规则。新分支从更新后的提交建立即可继承规则。
- 功能分支沿用 AI 分支命名，例如
  `NGA-Just-Works-<版本>-feature-ai-summary.apk`、
  `NGA-Just-Works-<版本>-feature-thread-detail-compat-mode.apk`。
  每个 APK 附带 `.apk.sha256`，Release 标题包含完整分支名。
- 每个分支仅清理自己的旧预览，成功发布后才清理。名称转换碰撞、大小写
  不同的分支和共享前缀均有独立的发布/并发身份；仅精确 main 可写缓存。
- main 预览与版本标签发布继续使用既有命名。Android applicationId、
  签名及版本号算法沿用原方案，分支 APK 安装到同一应用。

## 来源与分支同步

复用 AI 分支 `3ed2a4d1bc2af40844f7f2784d739f1206576b9b` 的 workflow
及实际执行 Bash 的离线测试，再扩展为全分支发布。

| 分支 | 最终采用的同步基线 | 公共 CI 提交或包含它的合并提交 |
| --- | --- | --- |
| `feature/thread-detail-compat-mode` | `dade93ac12504f4664fd6853dc44850a89f3a91d` | `8ceb57d9e73dd1476cea32ecd21175451c3818bd` |
| `feature/ai-summary` | `5288e89513c81193ea160e8c15daedd74caac459` | `dc601c9390979b0a35e367156e0af58634448f0e` |
| `main` | `081746b2d6c314102e563c395400eef1992c1571` | `b918b4a4181d0b189c19a7671be65d00535bb787` |

`8ceb57d9` 仅包含 workflow、Python fixtures、JVM 发布契约测试、README
下载说明和发布规范五个公共文件。AI 移植提交也仅涉及这五个文件；README
通过三方合并保留 `5288e895` 新增的 `lnga_harmony` / `nga-analyzer` 致谢，
下载段落之后的内容与该基线完全一致。

main 在同步期间由另一处工作合入同一公共 CI 提交。本任务核对其完整提交
树与从 `081746b2` 独立准备的移植提交 `a802a28e` 完全一致，复用该结果。
本任务未改动 main 的 Trellis/加载提示 WIP，也未把这些未提交改动打包。
AI 移植在临时 detached worktree 中完成后，以 `merge --ff-only` 接收。

IP/加载提示分支已由并行工作删除，本次未恢复。任务记录、归档与开发日志
保存在兼容模式分支，其他分支只需要公共构建改动。

## 验证结果

| 检查 | 结果 |
| --- | --- |
| 全部 Python 回归 | 36 项通过：25 workflow、8 version-code、3 release-notes |
| JVM `ReleaseWorkflowContractTest` | 9 项通过，无失败/错误/跳过 |
| actionlint v1.7.7 | 公共实现及移植版本通过 |
| Bash / Python 语法 | 7 段实际 workflow Bash 及 Python AST 通过 |
| main / AI 移植验证 | 各通过 4 项身份、并发及精确 main 缓存 smoke 检查；最终 AI 耗时 2.801 秒 |
| 分支内容一致性 | 四个公共代码/规范文件完全一致，README 仅保留 AI 已有额外致谢 |
| 任务引用与 diff | JSON / JSONL 引用校验、`git diff --check` 通过 |

完整审查见 `independent-check.md`，实现验证见 `implementation-results.md`。
移植未改变公共逻辑，因此不重复完整 Android 构建。Shellcheck 未安装，
本次未运行 Shellcheck。

## 提交与发布边界

沿用维护者已授权的 commit、finish-work、push 流程：先提交公共代码与
任务记录，再由 Trellis 归档本任务、记录日志，最后正常推送三个分支。
推送使用核对过的提交指向明确远端 ref，不 amend、不 force-push、不创建
稳定版本标签。兼容模式分支还包含后续任务记录与收尾提交。

本地已验证的是上述发布逻辑、契约与同步内容；实际签名 APK 由 GitHub
Actions 在代码推送后构建。本次未执行本地签名打包、真实 Release 删除、
设备测试或 APK 安装，也不主动轮询 Actions 结果。远端构建是否成功不属于
上述本地通过结论。
