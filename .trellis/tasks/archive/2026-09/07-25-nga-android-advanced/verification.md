# AI 设置与总结验收记录

日期：2026-09-06。范围：已批准的 BYOK 设置、当前楼层总结、当前资料页用户总结。
验收针对基于 `9e0b59d2` 的实现工作树；功能提交为 `edd69f21`。
本记录保存提交前的验证结果；获批提交及归档顺序见 `commit-plan.md`，
执行记录见 Git 历史和开发日志。没有发布操作。

## 结果

任务范围内的实现、独立审查与离线质量门已完成，无剩余审查阻塞项。
主设置增加独立 AI 二级页，保存一份加密配置；两个总结入口复用客户端和
可取消弹窗。资料输入只读取目标用户的主题第一页、回复第一页。

| 验证 | 命令或证据 | 结果 |
| --- | --- | --- |
| App JVM 测试 | `:nga_phone_base_3.0:testDebugUnitTest` | 34 个测试类，228 项；failure / error / skip 均为 0 |
| Debug App | `:nga_phone_base_3.0:assembleDebug` | 构建成功 |
| Android 测试 APK | `:nga_phone_base_3.0:assembleDebugAndroidTest` | 编译、打包成功；未执行 3 项设备测试 |
| 所有模块 lint | `lintDebug --continue --rerun-tasks --console=plain` | 13 份 XML 报告，Error / Fatal 均为 0；仍有非阻塞 warning |
| 仓库 debug 测试诊断 | `testDebugUnitTest --continue --console=plain` | 两处已记录的非本任务示例测试失败，详见下文 |
| 设置页反射入口 | 对实际 `settings.xml` AI 条目的 AAPT2 资源链接验证 | 生成保留类名与无参构造的规则，无需额外 `@Keep` |
| 文本与上下文清单 | `git diff --check`、JSON/JSONL 解析与文件存在性检查 | 通过 |

最终 App 验证命令：

```bash
./gradlew :nga_phone_base_3.0:testDebugUnitTest :nga_phone_base_3.0:assembleDebug :nga_phone_base_3.0:assembleDebugAndroidTest --console=plain
```

日志为 `/tmp/nga-ai-app-final.log`（BUILD SUCCESSFUL，13 秒）、
`/tmp/nga-ai-lint.log`（BUILD SUCCESSFUL，2 分 7 秒）和
`/tmp/nga-ai-repository-tests.log`。这些是本机临时证据，不进入提交。

## 验收覆盖

本次新增 87 项 AI JVM 测试；其余 141 项 App 既有测试也通过。

| 需求 | 实现与离线证据 |
| --- | --- |
| 一级入口、二级设置页与返回路径 | `AiSettingsContractTest`（3 项）及既有 `DefaultSettingsContractTest`；沿用 `LauncherSubActivity` 导航。UI 合约测试是静态检查 |
| 配置校验、重载、清除、Key 隔离 | `AiConfigTest`（5）、`AiConfigRecordTest`（4）、`AiConfigStoreTest`（9）；真实 AES-GCM 记录、损坏/丢 Key、原子写失败、部分清除失败。Android 存储适配器另有 3 项已编译的 instrumentation 测试 |
| 连接测试与独立模型请求 | `AiResponseParserTest`（7）、`AiSummaryClientTest`（15）；UTF-8、JSON 字段类型/边界、Cookie/认证隔离、状态错误、超时、取消、重定向与 `503 Retry-After: 0` 单次 POST |
| 被点击楼层与有限资料输入 | `SummaryInputTest`（5）、`ProfileSummaryLoaderTest`（9）、`NgaProfilePageSourceTest`（15）；行快照、被查看 UID、每类第一页、回复正文/作者、长度、时区、GBK/声明字符集与畸形响应 |
| 共用弹窗、未配置引导与生命周期 | `SummaryControllerTest`（11）、`AiSummaryUiContractTest`（4）；配置前置与发送前复查、重试、复制入口、暂停/刷新取消、相同目标重试的迟到回调隔离 |

代码审查另确认：Key 编辑器不预填已存 Key，不进入 Preference/Bundle、
autofill 或内容捕获；模型请求不经过 NGA Cookie、编码与正文日志链。
测试全部使用虚构数据、替身和本地 MockWebServer。

独立审查发现并复核了两项修复：NGA 总超时必须进入终止错误状态，不能因
OkHttp 内部 `isCanceled()` 而丢弃回调；存在但畸形的 Content-Type 必须报协议
错误，不能静默回退 GBK。回归用例使用实际解析到的 OkHttp 4.12.0。

## AAPT 与发布验证边界

使用与项目 AGP 缓存一致的 `aapt2 8.6.1-11315950`，从当前
`settings.xml` 提取真实 `pref_ai_settings` 条目并在临时目录 compile/link。
默认模式生成 `<init>(...);` 保留规则；`--proguard-minimal-keep-rules`
模式生成：

```proguard
-keep class sp.phone.ui.fragment.SettingsAiFragment { <init>(); }
```

该规则保留反射使用的类名和构造，与既有 `SettingsLabFragment`、
`SettingsSizeFragment` 的 release AAPT 规则一致。AndroidX Fragment 的
consumer 规则即使允许混淆，也不撤销 AAPT 的强保留规则。
此证据是离线资源链接检查，不是完整 Release/R8 构建或发布验证。
复现脚本为 `/tmp/nga-ai-aapt2-evidence-f_bpjxnl/probe.py`；
该目录下 `output/commands-and-output.log` 记录版本、命令和退出码，
`output/proguard-default.pro` / `output/proguard-minimal.pro` 保留生成规则，
`output/inputs.json` 保留提取来源与 SHA-256。

## 仓库诊断的既有失败

本次全仓库诊断退出码为 1，实际只报告以下两处：

- `lib_bu_statistics:compileDebugUnitTestJavaWithJavac`：上游
  `ExampleUnitTest.java` 缺少 `org.junit` 依赖。
- `lib_module_debug:kaptDebugUnitTestKotlin`：上游示例测试生成
  `error.NonExistentClass` 注解桩。

两者已列在 `.trellis/spec/backend/android-quality-guidelines.md` 的仓库
诊断基线中，本任务未修改这两个模块。它们不属于 AI 功能门；没有为隐藏
诊断失败而增加产品依赖、跳过测试或修改变体。该规范另列的 `lib_base_ui`
与 `lib_core` 问题本次未重现，对应任务为 UP-TO-DATE，不宣称已修复。

## 未执行与产物

设备测试为 **not run per project policy**：没有调用 ADB、查询设备、安装
APK、执行 instrumentation 或启动模拟器。真实 Keystore、设备重启、界面
操作和第三方服务兼容性没有运行时实测；这不是本任务交付阻塞项。
没有发起真实 NGA/模型请求，没有签名发布、推送或 Release 操作。

本机产物：

- `nga_phone_base_3.0/build/outputs/apk/debug/nga_phone_base_3.0-debug.apk`
- `nga_phone_base_3.0/build/outputs/apk/androidTest/debug/nga_phone_base_3.0-debug-androidTest.apk`
- `nga_phone_base_3.0/build/reports/tests/testDebugUnitTest/index.html`
- 每个 Android 模块的 `build/reports/lint-results-debug.xml`

新增规范 `.trellis/spec/backend/ai-summary-contract.md` 保存了加密存储、
单次模型 POST、NGA 字符集、两次页面操作与底层重传的区别，以及暂停/超时
处理规则；backend/frontend 索引与 Java 互操作例外已同步。
工作提交方案另列于 `commit-plan.md`；任务状态在归档前保持 `in_progress`。
