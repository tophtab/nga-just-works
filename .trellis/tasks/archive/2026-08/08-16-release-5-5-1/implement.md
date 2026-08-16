# 5.5.1 发布执行计划

1. 补齐 `release-notes/5.5.1.md` 和本任务规划文档。
2. 运行发布说明校验、focused `ReleaseWorkflowContractTest` 和 `git diff --check`；明确不运行本地 release/preview assemble、签名工具、ADB 或 Action 轮询。
3. 只暂存本任务相关文件和发布说明，创建独立发布提交并推送 `main`。
4. 确认 `origin/main` 已包含该提交，创建并推送精确 tag `5.5.1`。
5. 向用户报告 commit 和 tag；签名 APK、SHA-256 与 GitHub Release 由 tag workflow 负责。
