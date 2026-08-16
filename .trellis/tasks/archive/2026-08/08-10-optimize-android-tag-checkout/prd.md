# 优化 Android Checkout 与 versionCode

## Goal

缩短 Android main preview 与稳定标签发布的源码准备时间，避免 checkout 下载构建
不需要的历史文件内容；同时把内部 `versionCode` 改为可读、严格递增且兼容
preview/stable 覆盖安装的 SemVer 前缀编码。

## Background

- `.github/workflows/build.yml` 当前对 main push 和稳定 tag 共用
  `actions/checkout@v4`，并固定设置 `fetch-depth: 0`。
- main preview 在 `Derive release identity` 中执行
  `git tag --merged "$GITHUB_SHA" --sort=-version:refname`，因此当前仍需要完整可达
  历史和标签。
- 稳定 tag 分支直接从 `GITHUB_REF_NAME` 获取版本身份，不执行上述历史查询，只需要
  当前 tag 对应的提交和工作树。
- 当前 HEAD 的 tracked blob 约 `11 MiB`；全历史 blob 未压缩合计约 `224 MiB`，
  本地 Git pack 约 `141 MiB`。main 的版本推导只需要 commit/tag 可达关系，不需要
  历史版本的 blob 内容。
- `5.5.0` run `31397725685` 的 checkout step 为 `2m32s`，其中完整 fetch 占约
  `2m31s`；实际切换 tag 仅约 `0.13s`。相邻 main preview 的同类完整 fetch 约
  `3.8s`，说明全量传输会放大 runner/网络波动。
- workflow 当前使用 `4043 + GITHUB_RUN_NUMBER` 生成 `versionCode`；`5.5.0` run
  `#26` 因而得到 `4069`。该值严格递增但无法从数值识别对应的稳定版本。
- preview 与 stable 使用相同 applicationId 和签名，因此新产物的 `versionCode`
  必须大于已安装产物，才能保持 preview → stable 以及 stable → 后续 preview 的
  覆盖升级能力。

## Requirements

- 稳定 tag checkout 必须设置 `fetch-depth: 1`：获取当前 tag 对应的提交、该提交的
  完整工作树以及必要的 tag 元数据，但不下载父提交历史和历史版本中已删除的文件。
- checkout 必须使用 Git partial clone `filter: blob:none`。main preview 继续设置
  `fetch-depth: 0`，保留完整 commit/tag 可达关系以维持现有
  `git tag --merged "$GITHUB_SHA"` 版本推导契约，但不预取历史 blob；当前提交的
  完整工作树仍必须可用于构建。
- 除 `versionCode` 生成方式外，不改变稳定 tag 匹配规则、`versionName`、Gradle
  任务、签名、APK 校验、发布说明、GitHub Release 或 debug prerelease 清理行为。
- `versionCode` 必须使用 `Mmmppbbb` 十进制布局：major 不补零，minor/patch 各占
  两位，build slot 占三位。计算公式为
  `major * 10_000_000 + minor * 100_000 + patch * 1_000 + build_slot`。
- 稳定 tag 的 build slot 必须为 `000`。已经发布的 `5.5.0` APK 保持原 code
  `4069`，不重建；新公式下 `5.5.0` 的语义基准为 `50,500,000`，后续 preview
  使用 `50,500,001–999`。未来 `5.5.1` 为 `50,501,000`，`5.6.0` 为
  `50,600,000`。
- main preview 的语义版本前缀继续来自最近的可达稳定 tag，build slot 使用该 tag
  之后的 first-parent commit 距离再加一，因此同一提交的重跑保持相同 code，正常
  追加 main 提交时严格递增；例如 `5.6.0` 后的 preview 为 `50,600,001–999` 范围。
- minor、patch 必须限制为 `0–99`，preview build slot 必须限制为 `1–999`，最终值
  必须在 Android `1–2,100,000,000` 范围内；超界必须在构建前明确失败。
- versionCode 推导必须放在独立、可单元测试的脚本中，workflow 只负责提供稳定
  版本和 build slot。
- 为 checkout 深度分流和 versionCode 数据流补充 focused workflow contract test，
  防止未来把 main 错误改成浅克隆、把 tag 退回完整历史或恢复 run-number offset。
- 运行与改动风险相称的静态和 JVM 验证；不运行 ADB、安装、instrumentation 或
  真实 NGA 网络访问。

## Out of Scope

- 改写 main preview 的稳定版本发现机制。
- 改用显式版本文件或预构建 Release artifact promotion。
- 清理或重写 Git 历史中的大文件。
- 提前构建/复用 Release APK、关闭 R8、升级 AGP/Gradle 或调整 Gradle 缓存。
- 修改 `gradle-wrapper-validation.yml` 或其他无关 workflow。

## Acceptance Criteria

- [x] `.github/workflows/build.yml` 在稳定 tag ref 上为 checkout 解析出
      `fetch-depth: 1`，在 main branch ref 上解析出 `fetch-depth: 0`。
- [x] checkout 对 main 和稳定 tag 都启用 `filter: blob:none`，main 仍能取得当前
      完整工作树和 commit/tag 可达关系，而不会预取历史 blob。
- [x] main preview 的 `git tag --merged "$GITHUB_SHA"` 逻辑保持不变。
- [x] 当前 `5.5.0` 基准推导为 `50,500,000`，未来 `5.5.1`/`5.6.0` 分别推导为
      `50,501,000`/`50,600,000`；preview 使用稳定前缀加 first-parent 距离
      build slot，并保持严格升级顺序。
- [x] versionCode 推导脚本覆盖正常 SemVer、两位 minor/patch、preview slot、格式错误、
      字段溢出和 Android 上限。
- [x] `ReleaseWorkflowContractTest` 明确覆盖 tag 浅克隆、main partial clone、稳定
      build slot `000`、preview commit-distance slot 以及旧 offset 被移除。
- [x] focused `ReleaseWorkflowContractTest`、versionCode 脚本单测、workflow YAML/
      静态检查和 `git diff --check` 通过。
- [x] 改动以独立提交 push 到 `origin/main`，Trellis task 完成 finish-work；不等待或
      监控 push 后的远端 GitHub Actions。
