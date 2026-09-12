# AI 分支构建方案与复用边界

核对时间：2026-09-12。此文件为实现/检查代理提供可追溯上下文。

## 固定来源

- 工作分支：`feature/thread-detail-compat-mode@dade93ac12504f4664fd6853dc44850a89f3a91d`。
- 参考分支：`feature/ai-summary@4f3f4153fb5ff8a23724e49e403d97c440181132`。
- 可复用提交：`3ed2a4d1bc2af40844f7f2784d739f1206576b9b`
  (`ci(android): publish branch-labelled preview releases`)。
- 前一版 `c52e045c` 使用会过期的 Actions artifacts；维护者指定参考 AI
  分支当前方案，因此采用 `3ed2a4d1` 的 GitHub prerelease 下载方式。

## 已查明的实现

使用 `git show 3ed2a4d1:.github/workflows/build.yml` 和
`git show 3ed2a4d1:scripts/test_release_workflow.py` 读取参考文件。

AI workflow 已有：

- main / AI 白名单、branch 级运行取消；
- `asset_suffix=-feature-ai-summary`、`branch-feature-ai-summary-<sha12>`
  标签以及带完整分支名的 Release 标题；
- 动态输出经 step `env` 传递给 shell；
- production-ID 签名 preview、APK/checksum 和 manifest 校验；
- 同 SHA 重跑验证、发布成功门控、同分支旧版清理；
- 清理时验证前缀后的 SHA 长度，保护更长的共享前缀分支；
- 本地实际执行 workflow Bash 的 Git/APK/GitHub CLI fixture 测试。

## 扩展到所有分支时的缺口

1. `branches`、identity 的 `elif` 和发布步骤的 `if` 都仍有 AI 白名单。
2. 仅 `/ → -` 无法区分 `feature/a-b` 与 `feature/a/b`；用户本次要求所有
   分支，不能直接把这个变换当成唯一频道键。按 design 引入原始 ref 摘要。
3. 需要处理长名字、特殊字符、SHA 相同但分支不同的身份，并维持单层文件名。
4. 当前工作分支的 `ReleaseWorkflowContractTest.kt` 仍硬编码旧 shell
   结构、`debug-` 字符串和清理 step 名称，AI 分支之前已记录其中四项失效。
   同步时应一并适配这些受影响的现有测试；行为验证主要由实际 Bash fixture
   承担，避免用新的字符串快照代替行为验证。
5. 各 branch push 使用各自 workflow；四个已存在分支都需要包含公共提交。

## 既有规范与本次取代关系

`.trellis/spec/backend/android-quality-guidelines.md` 的签名发布契约仍适用
于签名、版本、校验、稳定版、离线测试及设备边界。其中“显式 branch
allowlist”被维护者 2026-09-12 的“所有分支都会自动构建 APK”明确取代。
实现与检查不得把旧白名单重新当作审批障碍。APK 文件名按 PRD/design；
Android packageId 与版本名不添加分支。

主会话已将质量规范的发布章节更新为本任务契约。该文件超过平台默认的
32 KiB 注入上限，检查代理应直接读取 `## Scenario: Signed GitHub release
APK` 章节到文件末尾，不能依赖自动注入的截断副本。此处 JSONL 因而保留
索引、跨层指南与本研究，发布章节通过直接读取补齐。

## 同步前基线核对

`git diff --name-only dade93ac 6aa13e6b -- .github/workflows scripts/ README.md
.trellis/spec/backend/android-quality-guidelines.md
nga_phone_base_3.0/src/test/java/gov/anzong/androidnga/ReleaseWorkflowContractTest.kt`
没有输出。main 与 IP/加载提示分支起点相同，因此这些公共文件可独立移植；
AI 分支的已有 workflow、Python fixtures、README 下载说明与发布规范须
按审查后的最终公共内容解决重叠。各分支的其余功能差异不参与同步。

AI 参考规范禁止以 commit/push 授权推导本地签名打包，也规定 main/tag
推送后不主动等待 Actions。采用这些已有交付边界：用本地离线/静态/JVM
检查验证改动，推送触发远端构建；若之后用户主动请求再检查远端结果。
