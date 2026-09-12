# 执行计划

## 开始门槛

- [x] 维护者已同意建立本任务。
- [x] 已查看当前与 AI 分支 workflow、既有发布规范及回归。
- [x] PRD / design / implement 已保存，真实 JSONL 上下文已配置。
- [x] 展示最终方案后，维护者于 2026-09-12 回复“开始”，批准实施、全部
  现存分支 CI 同步及既定提交收尾流程。规划时四个分支之一已被并行工作
  删除，实际同步剩余三个分支。

## 实现与审查

1. 主会话激活任务，按 Trellis 自动模式分派 `trellis-implement`，限定所有
   产品文件编辑于 `/home/toph/nga-just-works-compat-mode`。其他 worktree
   由主会话在提交阶段顺序同步。
2. 实现代理复用 `3ed2a4d1` 的 workflow/离线测试，扩展任意 branch refs、
   安全可读后缀、带原始分支摘要的内部标签、精确的同频道清理，以及旧 AI
   标签格式的一次迁移兼容。更新 README 下载段落。
3. 实现代理调整 `ReleaseWorkflowContractTest.kt` 中受影响的旧断言。
   其文件所有权为 `.github/workflows/build.yml`、
   `scripts/test_release_workflow.py`、必要的发布身份辅助脚本、
   `nga_phone_base_3.0/src/test/java/gov/anzong/androidnga/ReleaseWorkflowContractTest.kt`、
   `README.md`。不改 App 功能实现。
4. 主会话同步 `.trellis/spec/backend/android-quality-guidelines.md` 的
   发布契约，明确用户本次“所有分支”要求取代旧显式白名单；维护任务记录。
5. 按顺序运行必要验证并保存结果；在实现完成后分派 `trellis-check` 做最后
   一轮完整变更审查与自修复，不与实现代理竞争同一文件。

## 验证

- `python3 -m unittest discover -s scripts`：既有版本号、release notes、
  迁移来的实际 Bash 发布回归及新增任意分支/命名碰撞测试。
- `actionlint .github/workflows/build.yml`；对修改后的 `run: |` Bash
  块做 `bash -n`，在可用时结合 shellcheck。
- `./gradlew :nga_phone_base_3.0:testDebugUnitTest --tests gov.anzong.androidnga.ReleaseWorkflowContractTest --console=plain`。
- `git diff --check`，审查只涉及获准的公共 CI/测试/文档/任务文件。
- 离线清理测试包含普通分支、多个 `/`、`feature/a-b` 对
  `feature/a/b`、长/特殊名字、与旧 AI 标签相似的分支、main/稳定标签、
  同 SHA 不同分支、重跑、发布失败、API 分页/失败、删除失败。
- 不运行本地签名 APK 打包、设备操作或真实 GitHub Release 删除测试。

## 提交、同步与收尾

1. 在兼容模式分支上将公共 CI/测试/README/规范作为独立提交；任务规划、
   研究与检查记录独立提交，便于只移植公共修改。
2. 重新核对 main/AI 工作区、HEAD 和远端，在临时隔离 worktree 中
   cherry-pick 公共提交，按已审查设计解决 AI 分支重叠，再以快进方式
   更新对应分支。保留 main/AI 的无关 WIP，不恢复已删除的 IP 分支。
3. 对各分支最终 workflow 及公共测试检查一致性，并运行 Python/静态回归；
   JVM 相关文件若同步中另有变化，补充对应检查。
4. 将全部现存分支的同步结果、测试结果及预期文件名写入交付记录。执行 finish-work，
   归档本任务并记录日志；沿用既有 commit/push 授权。
5. 使用非强制推送发布三条现存分支，记录远端 HEAD。按现有规范交付后由
   Actions 完成打包，不以主动轮询或设备操作作为收尾前提。

## 风险与回退点

- 名称转换碰撞用原始 ref 摘要隔离发布记录；文件名保留 AI 分支的可读形式。
- 旧 AI 清理兼容仅匹配该精确分支，避免误删其他分支。
- cherry-pick 前复查状态；遇到并发改动先保留并重新适配。
- 回退使用 revert 公共 CI 提交；不重写分支历史。

## 独立审查后的定向修复

- 审查确认 GitHub 的 concurrency group 与表达式比较忽略大小写。
- 实现代理增加只派生原始 ref 摘要的前置 job，并修正仅精确 main 写缓存
  的受控判断；补充实际 Bash 并发键/缓存回归后交回同一检查代理复审。
- 这补齐了已经批准的全分支隔离要求，不增加 App 功能或新的发布入口。

## 执行结果

- 公共实现、规范同步及独立审查已完成；审查报告无剩余问题。
- main 的并行合并结果已包含 `8ceb57d9`，其最终树与独立准备的 CI 移植
  一致，直接复用。原工作区 Trellis/加载提示 WIP 保持未提交。
- AI 在实施期间推进到 `5288e895`；基于该最新版本生成 `dc601c93`，仅
  修改计划内五个公共文件，保留新功能及 README 致谢，真实分支已快进接收。
- 三个现存分支的公共 workflow、测试与发布规范一致；目标分支、提交和
  验证证据汇总于 `delivery.md`。任务记录、归档与日志仅在兼容模式分支保存。
