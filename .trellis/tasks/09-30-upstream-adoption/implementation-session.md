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

## U3 B1 checkpoint

- `1b4f8ade`：主题/NGA上传/外部头像上传三处局部decode入口，14项测试及6份jdata样本。
- Debug构建、778项全模块测试、13模块lint 0 Error/Fatal、独立check PASS。
- B2开始原子JSON2切换、完整存储三方向兼容与反射/R8验证；旧库仍只能作为测试oracle，不进入新runtime。

## U3 B2 checkpoint

- `4a4296c2`：完整JSON2 runtime迁移、存储三方向兼容、操作级解析测试入口及规范。
- Debug构建、789项全模块测试、13模块lint 0 Error/Fatal、独立check PASS；9份旧golden未改。
- 生产bean classfile R8夹具通过，运行时只有JSON2，旧库仅test/kapt。
- 实际app R8被现有packageReleaseResources签名guard挡住，未绕过/未读凭据；U3-A7及父验收保持待U4。B3开始typed wire/core decoder独立实现。

U4预取补充：已校验的Gradle8.11.1 distribution已放入标准wrapper cache `~/.gradle/wrapper/dists/gradle-8.11.1-bin/bpt9gzteqjrbo1mjrsomdt32c`，供新wrapper离线启动。

## U3 B3 checkpoint

- `758aa6b9`：五类wire DTO、ReadField状态与core decoder；core_data启用Kotlin/JVM17。
- 独立check修复topic强转/count转换/groups-buffs访问语义，14项core decoder测试、803项全模块测试通过，13模块lint 0 Error/Fatal。
- app尚未接线。B4开始捕获旧facade完整对比样本，并接回既有Java模型与渲染链。

## B4 独立复核修正中

首轮 B4 Debug、806项测试、13模块lint通过，但独立check用保留的旧生产helper发现额外别名/bean回退差异，尚未提交。
详见 U3 `research/b4-check.md`；正在固定补充旧facade基线并修复，B5须等复核通过。
本次用户追加完成后commit/finish-work/push/新版本发布授权，具体边界记录在approval.md。

## 并行主分支修复待整合

本次检查源仓库及远端main已推进到 `c768a3cbf7db4101472f15f3a7ea5b3b846de6d1`（回复搜索以回帖作者导航）。
待B4冻结提交后整合，B5需迁移新ArticlePage.kt中的JSON调用并保留新增ReplySearchNavigationTest；不能覆盖该已正式提交修复。
源仓库原始六个未跟踪目录仍为89文件，已制作并逐文件SHA256校验 `/tmp/upstream-adoption-source-plans-20261001.tar.gz`，原目录尚未移动/删除。

## U3 B4 最终代码门

普通facade接入typed wire/legacy mapper；显示bean回退保持在app层，补全别名逐次转换及附件/评论/热评优先级。
冻结旧输出样本156+104+72；独立review另修正JSON2实际 `_IsInBlackList` 字段名并加入双模式回归。
最终Debug构建、812项测试通过；13模块完整lint及局部修正后app lint均0 Error/Fatal。独立最终结论见U3 `research/b4-check.md`。
B4后先整合main `c768a3cb`，再B5纯删除清理；实际app R8仍待U4。

B4已提交 `5595c8ed`。main `c768a3cb`已整合，保留回复搜索导航修复并迁移新增JSON调用/测试到JSON2；41项导航/主题/读帖/缓存聚焦测试通过。B5开始，保持实际app R8待U4的未完成状态。

## U3 B5 代码批次完成

删除540行无消费者app私信副本及旧facade内部解析helper；共享渲染/附件/作者helper与活跃message parser保留。
816项测试、Debug构建、13模块新鲜lint0 Error/Fatal通过；debug/release runtime仅JSON2，kapt旧库单独说明。完整43行消费者清单及独立check见U3 research/b5-evidence.md/b5-check.md。
U3代码批次完成但实际app R8未执行，任务保持in_progress；U4补真实R8/Preview证据后才可验收关闭。

B5提交 `c2131fe9`，U4已start。承接main修复的merge提交为 `0eebe1e5`。U4实施与质量门进行中，既定签名guard和设备边界不变。

## U4当前授权范围全部验证通过，待最小配置访问授权

SDK36/toolchain/返回/CI代码完成；Debug、820单测、37Python测试、13模块lint0 Error/Fatal，新Debug APK三库16KB、三variant manifest、新R8 8.10.24生产bean六项均通过独立check。发布说明6.2.0及父integration-check.md准备完成。
唯一剩余：实际app R8及Preview Kotlin/Java编译。新图300/230任务不含APK/AAB打包或签名，但资源package任务命中未修改guard。遵守原计划未读凭据；需仅为这两项加载现有签名配置的追加授权。U4未提交，U3/U4/父不归档、不推送。

## 最终验收 PASS

用户对具体收尾方案继续指示后，按已说明范围在进程环境加载既有签名配置；未输出/复制配置，未改变guard。实际app R8（3分7秒）与Preview Kotlin/Java（13秒）均通过，无新增代码修复。独立check检查当前mapping/合并规则/DEX及图，无剩余问题。
U3-A7/U4-A7及父集成验收已满足；保留820测试、37脚本测试、13模块lint0 Error/Fatal与新APK16KB/manifest证据。下一步提交、五任务finish-work及6.2.0推送。源main另有在途测试精简改动，保持其工作树/分支不动，从隔离HEAD推送远端main。
