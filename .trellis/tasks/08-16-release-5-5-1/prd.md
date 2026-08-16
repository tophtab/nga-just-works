# 发布 NGA Android 5.5.1

## Goal

将当前 `main` 发布为稳定版本 `5.5.1`，由 GitHub Actions 生成并签名 APK、校验发布契约、计算 SHA-256 并创建 GitHub Release。

## Requirements

- 新增 `release-notes/5.5.1.md`，严格符合稳定版发布说明校验器的标题、顺序和列表要求。
- 本地只执行发布前置静态检查、发布说明校验和 focused contract test；不运行 `assembleRelease`、`assemblePreview`、`apksigner`、`adb`，不读取签名材料，不生成 APK 或 checksum。
- 将发布说明和本任务规划文件以独立提交推送到 `origin/main`。
- 在远端 `main` 确认包含发布提交后，创建并推送精确标签 `5.5.1`；不在本地创建签名产物，也不轮询 Action。
- 稳定标签必须由现有 `.github/workflows/build.yml` 处理：验证版本/签名/manifest、生成 SHA-256，并使用发布说明创建 Release。

## Acceptance Criteria

- [ ] `python3 scripts/validate_release_notes.py release-notes/5.5.1.md` 通过。
- [ ] `./gradlew :nga_phone_base_3.0:testDebugUnitTest --tests gov.anzong.androidnga.ReleaseWorkflowContractTest` 通过。
- [ ] `git diff --check` 通过，提交只包含本任务相关文件。
- [ ] 发布提交已推送且远端 `main` 指向该提交。
- [ ] 标签 `5.5.1` 已创建并推送；Action 接管签名构建、SHA-256 和 GitHub Release。
