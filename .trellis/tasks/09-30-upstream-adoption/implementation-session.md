# 2026-10-01 实施会话

- 授权：沿用 approval.md 的 U1–U4（含 U3 B0–B5）整体批准。
- 源仓库：`/home/toph/nga-just-works`，main `557f7bea398ed7a7702a876ec99d92eff5d7672e`；仅有五任务及 R7 引用归档未跟踪。
- 实施工作区：`/home/toph/nga-just-works-upstream-adoption`，`feat/upstream-adoption`，由上述已提交基线建立。
- 六目录共 89 文件复制后逐文件 SHA256 一致，再更新五任务 metadata；父子关系和 approval 保留。原研究基线 `1a8413d9` 只作历史来源。
- U1 已由 task.py start 激活，产品实现/验收进行中。其余子任务尚未启动。
- 基线包含 R1–R6 正式交付和 557f7bea 编辑器修复，后续以现行源码及规范回归。
- 原始恢复材料不代表本次产品工作；R7 仅作归档引用。

## 后续工具链预取（不代表产品验收）

- 独立 SDK：`.android-sdk/platforms/android-36/android.jar` 已安装；已有 build-tools/cmdline-tools/platform-tools 以链接复用。U1 的 local.properties 未切换。
- Gradle 8.11.1：`/tmp/upstream-adoption-toolchain/gradle-8.11.1-bin.zip`，SHA256 与批准值 `f397b287023acdba1e9f6fc5ea72d22dd63669d59ed4a289a29b1a76eee151c6` 一致。
- AGP8.10.1、Kotlin/Compose compiler2.2.21 及传递依赖共149个 artifact 已通过独立临时 Gradle 工程预取到本机 cache。
- 临时预取脚本首次缺少 JVM variant attributes 导致 Guava variant 选择失败；补 runtime/library/standard-jvm attributes 后成功。未修改产品依赖来修复预取脚本。
- 预取日志 `/tmp/upstream-adoption-toolchain-prefetch.log`；正式 U4 仍须核验新 wrapper、实际模块依赖、编译/lint/R8，不以预取成功代替。

## U1 checkpoint

- 规划/恢复资料：`345db9cb`；U1产品及规范：`4ccd7564`。
- 独立check修复畸形数组成员导致部分更新的边界，最终35项板块聚焦测试、740项全模块测试通过，13模块lint 0 Error/Fatal、Debug构建通过。
- `09-30-upstream-board-icons/check.md` 保存独立结论；U1保留目录供U3联合验收引用，父任务收尾统一归档。
- U2已start，按原整体批准继续。

## U2 checkpoint / U3 start

- U2产品、规范及离线布局证据提交：`88ce8be3`。
- 749全模块测试通过，13模块lint 0 Error/Fatal、Debug构建通过；72离线布局场景通过，独立check PASS。浏览器/本地服务已关闭。
- U3已start，当前B0：先固定旧库golden与完整存储/消费者映射，逐批implement/check/commit。当前扫描43生产、13测试直接旧JSON消费者。

## U3 B0 checkpoint

- `05c2602e`：15项新增测试、9份旧格式golden，43生产/14测试消费者清单与逐存储/网络覆盖表。
- 相关747项测试通过；独立check加固属地writer目标文件测试，9项类测试重跑通过；无产品改动。
- 后续待补的Android回调/错误路径明确记录。B1开始，保持旧库，提取必要局部decode测试入口。
