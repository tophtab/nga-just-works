# 发布 5.5.0 版本

## Goal

以最终确认的 `origin/main` 为发布边界，提供准确的中文 changelog，并通过仓库
现有 GitHub Actions 流程发布签名、非预发布的稳定版 `5.5.0`。

## Background

- 用户明确指定版本号为 `5.5.0`，并已通知最后一项 bug 修复完成，可以继续发布。
- 当前最高稳定标签和 GitHub Release 为 `5.3.2`；规划核对时本地、远端和
  GitHub 均不存在 `5.4.0` 或 `5.5.0`。
- 规划核对时 `main` 与 `origin/main` 一致，最新提交为 `1800c224`；对应的
  Debug 发布工作流 `31394314340` 已成功完成。
- `5.3.2..1800c224` 的产品变化包括：恢复 API 29 安装下限、首页栏次自定义
  排序、移除随机加载语、固定板块/主题 FAB、主题非末页预读取、全仓历史 Lint
  错误清理，以及长按当前主题页签按五秒间隔刷新。
- 最后一项修复 `7c4cc7df` 将 `ConfirmDialog` 的 `context!!` 改为
  `requireContext()`；其任务已验证 13 个 Android 模块均为
  `0 Error / 0 Fatal`。
- 稳定版身份由精确的 `X.Y.Z` 标签注入；现有流程读取
  `release-notes/<tag>.md`，构建、签名并验证 Release APK 后创建 GitHub
  Release，因此不需要修改 Gradle 本地兜底版本号。
- GitHub 当前账号对 `tophtab/nga-just-works` 具有 `ADMIN` 权限，四个发布签名
  Secret 名称均已配置；任何 Secret 值不得读取、记录或提交。
- 发布说明提交 `6e7217ab` 已推送到 `origin/main`；本地和远端 annotated tag
  `5.5.0` 均解引用到该提交，标签对象为 `1d17fb86`。标签推送成功后按项目合同
  未轮询 GitHub Actions。

## Requirements

- R1. 新增 `release-notes/5.5.0.md`，作为完整 GitHub Release 正文。文件必须按
  顺序且仅包含一次 `## 新增`、`## 删除`、`## 修复`，每节至少有一个列表项，
  并以 `5.3.2...5.5.0` 完整比较链接收尾。
- R2. Changelog 必须描述最终发布树相对 `5.3.2` 的用户可见净变化，并明确纳入
  `7c4cc7df`：全仓共清除 12 个历史 Lint 错误，13 个 Android 模块达到
  `0 Error / 0 Fatal`。
- R3. 不修改 `build.gradle` 的本地版本号、发布 workflow、应用 ID、签名身份、
  SDK/ABI 策略、构建变体或附件命名；稳定版版本号继续完全来自 `5.5.0` 标签。
- R4. 执行前必须重新获取远端并审计 `5.3.2..HEAD`。若出现规划后新增的产品
  提交，必须先更新 changelog 和规划摘要，不得直接发布过期说明。
- R5. 发布提交只暂存并提交 `release-notes/5.5.0.md`。本轮尚未提交的任务规划、
  其他 Trellis 差异以及任何并行工作必须保持在发布提交和稳定标签之外。
- R6. 发布前必须通过 release notes 校验、发布 workflow 契约测试、静态差异检查
  和 Trellis 全量审查。复用 `7c4cc7df` 后已经完成的全仓 Lint 与最新成功 Debug
  构建证据；没有新的产品代码时不重复本地签名打包。
- R7. 将 release notes 提交非强制推送到 `origin/main`，然后在该提交创建 annotated
  tag `5.5.0`，标签信息为 `NGA Just Works 5.5.0`，并只推送该精确标签引用。
- R8. 不手工创建替代 Release，不上传本地或 Debug APK。标签推送后由 GitHub
  Actions 构建、签名、校验和发布；按项目合同不轮询或等待该 workflow。
- R9. 稳定标签和附件视为不可变。若标签推送后的 CI 发现问题，不移动或覆盖
  `5.5.0`，而是保留失败证据并以新的修复版本处理。

## Acceptance Criteria

- [x] `release-notes/5.5.0.md` 已覆盖截至 `1800c224` 的全部已确认发布变化，
      包括 `7c4cc7df` 的确认框上下文/Lint 修复。
- [x] 当前 changelog 草稿通过 `scripts/validate_release_notes.py` 结构校验。
- [x] 执行前重新确认本地 `main`、`origin/main`、发布范围以及 `5.5.0` 标签和
      Release 仍不存在。
- [x] 发布说明校验、validator 测试、发布 workflow 契约测试和 `git diff --check`
      全部通过。
- [x] 发布提交的 staged diff 只包含 `release-notes/5.5.0.md`。
- [x] `origin/main` 包含 release notes 提交，且本地/远端 annotated tag `5.5.0`
      均解析到该提交。
- [x] `git push origin refs/tags/5.5.0` 成功；不进行后续 Actions 轮询，也不手工
      创建或替换 GitHub Release。
- [x] 无关工作区改动、签名材料和 Secret 均未进入提交、标签或发布附件。

## Out of Scope

- 为跳过的 `5.4.0` 创建占位标签、Release 或说明。
- 修改产品代码、版本推导、GitHub Actions 发布逻辑或签名配置。
- 本地构建签名 APK、安装 APK、ADB、模拟器或真机验证。
- 发布到应用商店，或回填 `5.3.2` 之前的历史 Release 说明。
- 标签推送后持续监控 GitHub Actions；失败调查仅在用户后续明确要求时进行。
