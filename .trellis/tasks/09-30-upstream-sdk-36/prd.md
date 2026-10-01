# U4：Android SDK36 完整适配

状态：2026-09-30 用户已统一确认整体方案，留待新会话实施；批准记录见父任务 approval.md。

## 目标与价值

采用上游compile/target36，完成当前工程必要的工具链和Android16行为适配；最低支持Android10、应用身份、签名和既有数据保持。

## 已确认背景

当前compile/target35、min29；上游22ba3082仅改SDK和自身版本。官方平台/工具链与实际代码已全面核对，不能只复制两行SDK。
选定AGP8.10.1/Gradle8.11.1/Kotlin与Compose compiler2.2.21/JDK17；保留Build Tools35.0.0和业务库。
两处Activity旧返回入口需要迁移；当前无edge-to-edge opt-out或方向锁，不能据此重写全局insets。三个现有原生库静态对齐检查符合16KB，无强制升级依据。

用户主力手机为Android15/API35。保持Android15现有体验是主要兼容性目标；SDK36不提高最低系统要求。

## 需求与验收

- U4-A1/A2：所有模块采用compile36，最终应用target36/min29，选定工具链可编译；包名/版本/签名/Java17保持。
- U4-A3/A4：Post面板优先消费返回，Launcher fragment委派和fallback保持；现有drawer/WebView/路由/排序/媒体/草稿状态不回退，不加入无关权限或opt-out。
- U4-A5：CI平台安装与最终APK断言同步36，本地测试证明错误target35产物被拒绝；不改自动发布触发与签名流程。
- U4-A6/A7：Debug构建、全模块单测/lint、Release R8与Preview编译/manifest检查通过。Release保持混淆，Preview保持原本不混淆；本次无签名打包。
- U4-A8：新Debug APK原生ELF/ZIP对齐检查通过；原缓存库/旧APK研究不作为新产物验收。
- 平台适用/不适用和未运行的设备项都有记录；不把源码检查、编译或静态对齐说成设备播放/返回/大屏/16KB运行通过。

## 范围外

不升minSdk，不发布/安装，不采用36.1/37、AGP9/Gradle9或全量AndroidX升级；不重做UI/窗口/存储架构，不引入新的权限或预测动画。
不把旧键盘/存储gate风险当SDK36必然回归，不吸收R7产品修复或改变读取/兼容策略。

## 规划产物与状态

[设计](design.md)、[实施计划](implement.md)、[工具链/二进制证据](research/platform-toolchain-evidence.md)、[全部平台适用性矩阵](research/android16-applicability-matrix.md) 已齐备，并获U1–U4统一批准。
当前暂保留planning，已获批准但未实施；新SDK/工具链在新会话实施时预取，未执行Android构建、签名、设备或真实NGA请求。
