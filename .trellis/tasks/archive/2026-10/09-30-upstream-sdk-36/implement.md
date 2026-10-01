# U4 实施计划

状态：2026-09-30 用户已统一确认 U1–U4 全部方案，指定新会话实施；本轮未开始产品实施。

## 有序步骤

1. 按父计划在U1–U3稳定基线上start现有U4，记录实际基线与已包含的外部会话版本；不得覆盖原脏树。
2. 准备任务隔离的SDK/构建环境：platforms;android-36、build-tools;35.0.0、AGP8.10.1、Gradle8.11.1、Kotlin/Compose compiler2.2.21、JDK17。依赖预取与产品检查分开，不执行安装或发布。
3. 修改root SDK、catalog共用版本与wrapper，设置distributionSha256Sum=`f397b287023acdba1e9f6fc5ea72d22dd63669d59ed4a289a29b1a76eee151c6`。保留minSdk29/JVM17/版本/签名/fullMode/各buildType。
4. 必要时以compile36/target35隔离编译问题；迁移PostActivity/LauncherSubActivity两处返回入口，再将最终target设36。旧onBackPressed里纯fragment消费函数可以保留，不能把所有同名方法误删。
5. CI安装platform36与APK target检查同步；改本地workflow fixture、增加旧target拒绝场景，保留Build Tools35.0.0、原签名和release路由。
6. 按已完成的平台矩阵核对最终Manifest/资源/调用点，无新增opt-out/无用权限/重复insets。新发现必须有实际证据再做局部适配。
7. Trellis check核查完整变更与以下矩阵，跑质量门；完成规范SDK标签更新和真实交付记录。

## 验收矩阵

| ID | 检查 | 必须结果 |
| --- | --- | --- |
| U4-A1 | 所有模块与最终manifest/APK | compile36、target36、min29；Debug/Release/Preview应用ID和debuggable分别保持 |
| U4-A2 | 工具链/插件 | 选定AGP/Gradle/Kotlin/Compose compiler同组可编译，Java/Kotlin17一致；无unsupported压制 |
| U4-A3 | 系统back | Post面板先关闭；Launcher可选fragment优先；不消费只fallback一次；fake不可用view处理正确、无递归/重复fallback；lifecycle注销/销毁仅源码与编译核查，不写成运行通过 |
| U4-A4 | 现有UI/路由 | 当前drawer/WebView/toolbar/SwipeBack归属不变；无新增重复insets/方向锁/权限；保留U1排序、U2媒体及U3状态 |
| U4-A5 | CI与发布身份 | 本地workflow tests通过，target35假APK失败，target36通过；min29/包名/签名与原触发规则不变 |
| U4-A6 | Debug/全模块单测lint | 构建通过，实际测试报告齐全且无失败；全部Android模块lint 0 Error/Fatal |
| U4-A7 | Release/Preview | 无签名Release R8通过，Preview源码/manifest编译通过；保留Release混淆/Preview不混淆；U3反射fixture与mapping规则复查 |
| U4-A8 | 新APK原生对齐 | arm64库清单、ELF LOAD和zipalign16KB检查通过；不以原APK研究结果替代新产物 |

Android15/API35为用户主要使用环境，U4-A3/A4优先核对其已有交互：发帖面板先关闭、退出一次、抽屉/WebView返回、草稿/正文媒体、布局和排序保持。源码/JVM结果与Android15实机结果分开记录；本次确认不自动授权设备操作。

## 本地命令与产物检查

先预取缺失依赖，准备就绪后以`--offline`执行正式gate（不是先完整跑一遍再重复）：

```bash
./gradlew :nga_phone_base_3.0:assembleDebug testDebugUnitTest --continue --offline --console=plain
./gradlew lintDebug --continue --rerun-tasks --offline --console=plain
./gradlew :nga_phone_base_3.0:minifyReleaseWithR8 --dry-run --offline --console=plain
./gradlew :nga_phone_base_3.0:minifyReleaseWithR8 --offline --console=plain
./gradlew :nga_phone_base_3.0:compilePreviewKotlin :nga_phone_base_3.0:compilePreviewJavaWithJavac :nga_phone_base_3.0:processPreviewMainManifest --offline --console=plain
python3 scripts/test_release_workflow.py
```

Preview组合也先核对dry-run任务图，没有guarded assemble/bundle/package再执行。若存在签名guard依赖，不绕过，按实际图选择无签名源码/资源/manifest/R8等价验证并记录限制。
替代检查只证明实际覆盖的部分；若实际app R8或Preview必要编译仍未执行，对应验收保持未完成，不能凭classfile fixture或Debug通过关闭U3/U4和父任务。
若为返回行为新增androidTest，编译相应测试APK但不运行connected；纯策略测试必须调用生产逻辑，可使用fake消费/回退，不能把编译写成设备验证。

- 根据settings.gradle核对13个模块test/lint XML和实际计数；R8/Preview额外失败不能因Debug成功被忽略。
- 使用SDK的apkanalyzer或aapt检查Debug APK；Release/Preview的plain merged manifest用XML parser检查：target36/min29，Debug ID有`.debug`，Release/Preview ID无suffix，debuggable按原buildType（未声明debuggable按false读取）。APK工具不用于解析plain XML。
- 用Build Tools35.0.0 `zipalign -c -P 16 -v 4 <Debug APK>`，再提取实际native ELF program header核对≥16KB及ABI；记录哈希、大小与路径，不复制二进制进任务资料。
- 检查Release mapping/实际合并keep配置与可用usage输出；执行U3普通JVM/Debug语义回归，以及明确使用R8 `--classfile`输出的bean fixture。实际app Release的Android DEX不在JVM执行，产物检查不等于minified Android运行。
- 正式gate直接运行一次规范要求的全模块lint --rerun-tasks；此后只在报告缺失/不可信或源码变化时补跑，不机械重复已经有效的全量检查。

## 交付限制与回滚

设备的API29/35/36、导航模式、大屏/IME、系统picker与16KB运行均未授权执行；按政策标未运行，不自动变成用户必须补做的任务。真实NGA、BYOK LAN服务、账号登录同样不作为平台探针。
本项不请求发布/签名打包权限；现有签名流程未改变，日后实际打包按已存在授权边界处理。
独立提交U4；回滚配置/返回/CI的相关补丁即可，不回滚U1–U3、不迁移/清除用户数据。完成后父任务执行实际集成验收。

## 规划审核闭环

[独立审核](research/planning-review.md)的四处精度建议已落实：平台条件包含Android16+运行时；APK与plain manifest工具分开；fake策略与真实lifecycle/手势证据分开；classfile fixture与app DEX产物分开。产品步骤仍未执行。
