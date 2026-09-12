# 所有分支自动构建 APK

## 目标

每个分支推送代码后都能在 GitHub Releases 下载对应的签名预览 APK，沿用 AI
分支的文件命名和分支预览版发布方式。维护者于 2026-09-12 明确要求修改
workflow，并同意建立本任务；前序会话中 commit、finish-work、push 的授权
继续适用于本次后续改动。

## 背景与已确认事实

- 当前兼容模式分支 `feature/thread-detail-compat-mode@dade93ac` 的构建入口
  `.github/workflows/build.yml:3` 只监听 `main` 与版本标签。
- `feature/ai-summary@4f3f4153` 中的 `3ed2a4d1` 已实现带分支后缀的 APK、
  分支独立的 GitHub 预发布记录和成功发布后的同分支旧版本清理；但其分支
  白名单仅包括 `main` 与 `feature/ai-summary`。
- GitHub 对分支推送读取该分支自己的 workflow；仅修改一个功能分支不能使
  已存在的其他分支自动获得新规则。
- 已有的 AI 分支方案只在 APK 文件名和 Release 元数据中加入分支标识。
  Android applicationId、版本名格式与签名仍共用现有配置。
- 实施期间，另一处工作删除了 IP/加载提示分支的本地分支、worktree 和
  远端 ref；再次查询后只剩下 main、AI、兼容模式三个现存分支。

## 需求

- R1：所有分支的代码推送均进入 APK 构建和预发布流程，包括含多级 `/` 的
  分支名。沿用现有纯 Markdown / `.trellis/**` 改动不构建的规则。
- R2：非 `main` 分支沿用 AI 方案的文件名
  `NGA-Just-Works-<版本>-<分支标识>.apk`，并生成同名 `.sha256`；例如
  当前分支后缀为 `-feature-thread-detail-compat-mode`。
- R3：每个分支有独立的 Release 标题、标签和旧预览清理范围；不同分支名
  转换成相同可读后缀时也不能覆盖或删除彼此的发布记录。
- R4：构建继续使用现有签名 `preview` 变体；`main` 的现有预览命名与
  `X.Y.Z` 稳定标签发布契约继续有效。
- R5：以可独立同步的公共 CI 提交更新全部现存分支：`main`、
  `feature/ai-summary`、`feature/thread-detail-compat-mode`。各分支保留自己的 App 功能代码；
  后续从已更新分支创建的新分支自动继承规则。
- R6：完成针对 workflow 的离线回归、静态检查及受影响的现有发布契约测试，
  然后提交、归档记录并推送。APK 打包和签名验证由 Actions 执行。

## 验收标准

- AC1 → R1：workflow 允许全部 branch refs，主分支、普通功能分支与多级分支
  均可派生预览构建参数；版本标签仍进入稳定版流程。
- AC2 → R2/R4：AI 分支和兼容模式分支的文件名分别带可读分支后缀，
  Android 版本和签名校验继续执行，产物为 APK 与校验文件。
- AC3 → R3：离线测试覆盖同 SHA 不同分支、名称转换碰撞、共享前缀、同次
  构建重跑和发布失败；清理只在成功发布后影响所属分支的旧预览。大小写
  不同的合法分支也应独立运行；仅精确 `main` 可以写入 Gradle 缓存。
- AC4 → R5：上述现有分支具有一致的公共构建规则；同步的提交不携带其他
  功能分支的 App 实现，远端分支推送结果可核对。
- AC5 → R6：受影响的回归和静态检查通过；任务记录列出提交、分支与验证
  结果，并区分本地验证和实际 Actions 打包状态。

## 范围外

- 修改 App 功能、Android applicationId、SDK、签名或版本号算法。
- 手动创建稳定版本标签、安装 APK、执行设备测试。
- 将某个功能分支整体合并到其他分支，或改写既有提交历史。
