# 所有分支 APK 构建设计

## 实现边界与来源

在现有 `/home/toph/nga-just-works-compat-mode` worktree 实现公共 CI 改动。
复用 AI 分支 `3ed2a4d1bc2af40844f7f2784d739f1206576b9b` 的
`.github/workflows/build.yml` 与 `scripts/test_release_workflow.py`；不整体
合并 AI 分支。归属代码仅为 workflow、它的验证脚本、现有发布契约测试与
下载说明；主会话维护规范、任务记录和分支同步。

## 触发与构建

- `on.push.branches` 改为 `['**']`；保留版本标签匹配和纯文档路径过滤。
- 身份派生和发布步骤允许任意 `push + refs/heads/`，清除 AI 分支白名单。
- GitHub concurrency group 忽略大小写，直接用原始 ref 会使
  `feature/Foo` 与 `feature/foo` 互相取消。增加无 checkout、无 secrets、
  `permissions: {}` 的小型前置 job，输出完整原始 `GITHUB_REF` 的 SHA-256。
  构建 job 等待该输出，以摘要分组；同一分支的新推送取消旧运行，稳定
  标签不自动取消。
- GitHub 表达式的字符串比较也忽略大小写；缓存只读判断使用身份步骤已经
  生成的受控输出，避免把 `Main` 或 `MAIN` 当成精确的 `main`。
- 保留签名 `assemblePreview`、生产 applicationId、`X.Y.Z-debug.N`
  versionName、语义基数与提交距离 versionCode，以及完整历史的 branch
  checkout。正式标签继续使用现有 `verifyReleaseTag + assembleRelease`。
- 不新增 `workflow_dispatch` 或 PR 发布入口；现有 Wrapper 校验独立保留。

## 文件名与发布隔离

可下载文件沿用 AI 分支格式：

| 来源 | APK 文件名 | Release 标题 |
| --- | --- | --- |
| `main` | `NGA-Just-Works-<版本>.apk` | `NGA Just Works <版本> (Debug)` |
| `feature/ai-summary` | `NGA-Just-Works-<版本>-feature-ai-summary.apk` | `NGA Just Works <版本> (Debug, feature/ai-summary)` |
| `feature/thread-detail-compat-mode` | `NGA-Just-Works-<版本>-feature-thread-detail-compat-mode.apk` | `NGA Just Works <版本> (Debug, feature/thread-detail-compat-mode)` |
| `X.Y.Z` 标签 | `NGA-Just-Works-X.Y.Z.apk` | `NGA Just Works X.Y.Z` |

每个 APK 均有 `.apk.sha256`。分支名只影响文件名和 Release 元数据。

仅把 `/` 替换为 `-` 会使 `feature/a-b` 和 `feature/a/b` 碰撞。因此：

- 可读文件后缀把 `/` 及其他文件名不安全字符转换为 `-`，保留 ASCII
  字母、数字、点、下划线与连字符；限制可读部分为 80 ASCII 字节，
  防止 Git 允许的长分支名超过产物文件名上限。没有 ASCII 字母或数字时使用
  `branch`。普通现有分支的文件名保持 AI 方案的格式。
- 非 `main` 的内部 Release 标签采用
  `branch-<可读分支标识>-<原始分支名 SHA-256 前 12 位>-<提交 SHA 前 12 位>`。
  同样后缀的分支因此仍有独立的发布标签与清理范围。摘要依据原始分支名，
  不依赖可读后缀，也不随提交改变。
- `main` 继续使用 `debug-<sha12>`；稳定标签保持原值。分支标签保持在
  `branch-` 名字空间，避免旧 `main` workflow 的 `debug-*` 清理误删。
- 动态分支、标题和标签均作为带引号的环境变量/参数处理，不插入执行脚本
  源码；不允许分支名成为命令、路径层级或 jq 源码。

## 发布与清理

沿用 AI 分支的同 SHA 重跑验证、APK manifest/签名/校验和验证、同名附件
替换与成功发布后的清理。每次只删除当前分支内部标签前缀后接恰好 12 位
小写十六进制提交 SHA 的旧 prerelease，排除当前标签；失败不得启动清理。

兼容历史 AI 预览：仅当原始 ref 精确为 `refs/heads/feature/ai-summary`，
才允许在新预览成功发布后清理其旧 `branch-feature-ai-summary-<sha12>`
格式。该兼容规则是单一已存在频道的迁移，不对其他分支推导旧前缀。
其他分支、正式版、无关 prerelease 和共享较长前缀均保留。

## 现有分支同步

2026-09-12 规划时首次读取远端 heads 和各 worktree，均干净并与远端一致：

| 分支 | 起点 | worktree |
| --- | --- | --- |
| `main` | `6aa13e6b937812bd8afe03be218ca22bbd66dd40` | `/home/toph/nga-just-works` |
| `feature/ai-summary` | `4f3f4153fb5ff8a23724e49e403d97c440181132` | `/home/toph/nga-just-works-ai-summary` |
| `feature/thread-detail-compat-mode` | `dade93ac12504f4664fd6853dc44850a89f3a91d` | `/home/toph/nga-just-works-compat-mode` |
| `feature/thread-ip-location-loading-tips` | `6aa13e6b937812bd8afe03be218ca22bbd66dd40` | `/home/toph/nga-just-works-thread-ip-location-loading-tips` |

实施期间再次核对时，IP/加载提示分支已被另一处工作删除，本地与远端均
不再存在；不恢复该已删除分支，实际目标为其余三个现存分支。同时 main
出现未提交的 Trellis 工具更新和加载提示改动。AI 的另一项功能工作随后
提交并推送为 `69d41002`、`5288e895`；最终同步以 `5288e895` 为基线，保留
其 README 新增致谢，未使用 stash 或还原并行改动。

实现与任务记录分开提交；在临时隔离 worktree 中把公共 CI/测试/说明/规范
提交 cherry-pick 到 main/AI 的当前 HEAD，处理 AI 既有实现的重叠。验证只
包含公共文件后，原 worktree 以 `merge --ff-only` 接收对应 CI 提交，保留
各自未提交改动；不执行功能分支合并。同步前重新检查 HEAD/远端/修改
范围；若并行工作推进了 HEAD，就在最新 HEAD 上重新准备公共提交。
推送使用普通快进方式。
最终执行中，main 已由另一处工作在 `b918b4a4` 合入同一公共 CI 提交
`8ceb57d9`；其提交树与本任务从 `081746b2` 独立准备的 `a802a28e` 完全
一致，因此复用该结果，不重复 cherry-pick，也不干预 main 的未提交工作。
AI 的公共适配提交为 `dc601c93`，以快进方式接收到真实 AI worktree。
后续从已更新分支创建的分支继承全分支规则；从历史提交建立的旧分支仍
需包含这项 CI 提交，这属于 GitHub 的分支 workflow 读取方式。

## 验证与交付

- 复用实际执行 Bash 的离线 Git/APK/GitHub fixtures；覆盖全分支入口、
  特殊/超长名称、名称碰撞、大小写不同分支的并发键与 main 缓存区分、
  重跑、分页 API、失败与跨分支清理。
- 更新 Android 中因 workflow 结构变化失效的发布契约断言；保留真正的
  applicationId、构建变体、版本/签名等约束，不用失效断言阻碍新契约。
- 执行 Python 回归、actionlint、Bash 语法检查和受影响的 JVM 发布契约测试；
  YAML-only 调整不重复全项目 Android 产品构建矩阵。
- 本机未在 PATH 找到 actionlint/shellcheck；实现阶段使用已有本地工具
  路径或安装到临时工具目录。`jq`、Python 与 Java 已可用。
- 远端 APK 打包和签名在推送后由 Actions 完成；按既有规范不主动等待发布
  job，也不下载或安装 APK。交付区分本地已验证事项与远端待执行事项。

回退时 revert 公共 CI 提交并同步对应分支；不 force-push，不改动已经
发布的稳定版或用户数据。
