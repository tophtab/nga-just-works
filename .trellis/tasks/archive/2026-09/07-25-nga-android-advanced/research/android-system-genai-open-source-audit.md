# Research: Android system GenAI open-source source audit

- Query: 寻找并源码审计真实、非仅官方 snippet 的开源 Android App，确认其如何调用 AICore / ML Kit GenAI / Gemini Nano，包括依赖、运行时 availability、模型下载、推理、错误与回退、API Key、许可证和成熟度；判断这种机制究竟是用户自费云 API，还是无云 API Key 的系统端侧调用。
- Scope: mixed（外部 GitHub 固定提交、Google Maven artifact、Google 官方文档，以及当前任务约束）
- Date: 2026-09-05

## Findings

### 1. 结论

找到了三个不是官方代码片段、而是有真实产品功能和发行记录的 Android 项目。最完整、最适合作为调用链证据的是 **ApkAnalyzer 4.1.2**；**Essentials 18.0-beta.1** 提供了第二条独立实现证据；**Meshtastic Android** 展示了 Google/F-Droid flavor 隔离和确定性本地 fallback，但其 ML Kit availability 实现不应照抄。

| 项目（固定版本） | 实际机制 | API Key / 谁承担云调用费 | availability / 下载 | 失败与回退 | 许可证与成熟度 |
| --- | --- | --- | --- | --- | --- |
| `MartinStyk/apk-analyzer@23a9463`，正式发行 `4.1.2` | `com.google.mlkit:genai-prompt:1.0.0-beta4` -> `Generation.getClient()` -> AICore / Gemini Nano | **无 API Key、无项目代理、无用户云模型账户**；Google 官方称每次调用不产生额外 server cost | 完整映射 `AVAILABLE / DOWNLOADABLE / DOWNLOADING / UNAVAILABLE`，用户点按钮触发 `download()`，完成后再次检查 | cancellation-safe；检查/下载/生成异常降级为 unavailable/false/null，AI 卡片隐藏，不影响 APK 分析主功能；没有云回退 | GPL-3.0；2017 年创建、正式发行、仓库自述 Google Play 2M+ 下载；但 AI 模块自身无专项自动化测试 |
| `sameerasw/essentials@76ac0f0`，发行 `v18.0-beta.1` | 同一 Prompt API，版本 `1.0.0-beta2`，把自然语言转成本地 automation suggestion | **无 Key、无 Firebase/云 AI client**；系统模型调用 | 先 `checkStatus()`；`DOWNLOADABLE`/`DOWNLOADING` 时收集 `download()` flow，再生成 | 返回 `Result`，UI toast 错误；没有云回退；输出先预览，用户确认才保存 automation | MIT；2,839 stars、72 forks、持续发布；但 AI 功能 2026-08 才加入，且无专项测试，取消/下载完成判断较弱 |
| `meshtastic/Meshtastic-Android@5f1a4e5`，snapshot release | Google flavor 才包含 `genai-prompt:1.0.0-beta4`；F-Droid flavor 不包含 ML Kit | **无 Key**；不是项目代付云推理 | 实现没有真正调用 `checkStatus()`，也没有下载流程；仅以 client 能否构造作为 available，属于缺陷 | 任何生成异常或空结果都使用确定性本地 summary；F-Droid 始终使用算法 summary | GPL-3.0；1,827 stars、511 forks、真实活跃项目；ML Kit adapter 无直接测试 |

所以，对用户问题的准确回答是：**确实存在“不是赠送额度，也不是作者替用户付费”的调用方式。应用通过 Google 提供的 ML Kit SDK 绑定 Android 系统中的 AICore，共用设备上的 Gemini Nano；调用代码不接收 API Key，也不访问开发者自建/付费的大模型 HTTP endpoint。模型初次下载需要网络，但推理在设备上执行。Google 当前官方用语是 “No additional server cost incurred for each API call”，比笼统称作“永久免费 API”更准确。**

这也不是所有 Android 手机都可用的免费云 API：设备、地区、AICore 状态、已下载模型、前台限制、短期/长期 quota 都可能使能力不可用。系统 provider 应是 capability-gated 的无 Key 路线，BYOK 云 provider 仍适合作为广覆盖的显式备选；不能在端侧失败后未经同意自动把同一 NGA 内容发到云端。

### 2. 检索范围与样本选择

- 2026-09-05 用 Sourcegraph 对全局公开索引执行精确依赖搜索 `"com.google.mlkit:genai-prompt"`，默认排除 fork 和 archive，共命中 10 个索引仓库，包括 `android/androidify`、`android/snippets`、`firebase/firebase-android-sdk`、`sameerasw/essentials`、`MartinStyk/apk-analyzer`、`meshtastic/Meshtastic-Android`、Mozilla/Waterfox 和 `google/adk-kotlin`。
- 排除了 Google 官方 snippets、SDK 自身、仅声明依赖而没有可追踪产品调用链的仓库。另通过 GitHub repository search 检查了 `gemini-nano-playground`、`nano-lab`、若干新建聊天/演示 App；它们可作实验样例，但发行/使用证据明显弱于本文三个项目。
- 所有源码结论都固定到 40 位 commit SHA；没有依赖浮动的 `main` 链接。仓库活跃度和 release 数据来自 2026-09-05 的 GitHub REST API 快照。
- 未使用真实手机、真实论坛内容或任何 API Key，也没有执行模型下载/推理。本文证明的是源码调用机制和发布状态，不是每台手机上的当前可用性或中文质量。

### 3. Primary audit: ApkAnalyzer `4.1.2`

#### 3.1 固定版本、真实应用与成熟度

- Repository: <https://github.com/MartinStyk/apk-analyzer>
- Audited commit: [`23a9463fa3c1dddd46e9958aee79eae78a0d2583`](https://github.com/MartinStyk/apk-analyzer/commit/23a9463fa3c1dddd46e9958aee79eae78a0d2583)
- Release: [`4.1.2`](https://github.com/MartinStyk/apk-analyzer/releases/tag/4.1.2)。GitHub annotated tag 明确指向上述 commit，而不是“接近 release 的 main 快照”。
- GitHub API 快照：仓库 2017-06-14 创建，356 stars、69 forks，未归档；README 声明从 2017 年发布、Google Play 2M+ downloads，并提供正式 Play 链接（这是仓库作者声明，不是本文独立核验的商店统计）：[`README.md:7-20`](https://github.com/MartinStyk/apk-analyzer/blob/23a9463fa3c1dddd46e9958aee79eae78a0d2583/README.md#L7-L20)。
- 产品功能不是 AI playground：它分析设备中真实 APK，并在详情页增加一张端侧 AI summary 卡片。README 明确说 summary 由 ML Kit GenAI Prompt API 本地生成，数据不作为云模型 prompt 离开设备：[`README.md:57-90`](https://github.com/MartinStyk/apk-analyzer/blob/23a9463fa3c1dddd46e9958aee79eae78a0d2583/README.md#L57-L90)。
- 源码许可证为 GPL-3.0：[`README.md:9-18`](https://github.com/MartinStyk/apk-analyzer/blob/23a9463fa3c1dddd46e9958aee79eae78a0d2583/README.md#L9-L18)。当前 NGA 工程是 GPL-2.0-only，故可以研究其架构，但不能直接复制 GPL-3.0 代码，除非另有明确兼容性决定。

#### 3.2 依赖与系统服务边界

- `core:ai-insights` 直接声明 `implementation(libs.mlkit.genai.prompt)`：[`core/ai-insights/build.gradle.kts:11-15`](https://github.com/MartinStyk/apk-analyzer/blob/23a9463fa3c1dddd46e9958aee79eae78a0d2583/core/ai-insights/build.gradle.kts#L11-L15)。
- version catalog 固定 `com.google.mlkit:genai-prompt:1.0.0-beta4`：[`gradle/libs.versions.toml:44-46`](https://github.com/MartinStyk/apk-analyzer/blob/23a9463fa3c1dddd46e9958aee79eae78a0d2583/gradle/libs.versions.toml#L44-L46)、[`gradle/libs.versions.toml:109-110`](https://github.com/MartinStyk/apk-analyzer/blob/23a9463fa3c1dddd46e9958aee79eae78a0d2583/gradle/libs.versions.toml#L109-L110)。
- Hilt provider 只调用无参数 `Generation.getClient()`，没有 endpoint、API Key、project id 或 Bearer token：[`AiInsightsModule.kt:22-26`](https://github.com/MartinStyk/apk-analyzer/blob/23a9463fa3c1dddd46e9958aee79eae78a0d2583/core/ai-insights/src/main/kotlin/sk/styk/martin/apkanalyzer/core/aiinsights/di/AiInsightsModule.kt#L22-L26)。
- Google Maven 的 `genai-prompt-1.0.0-beta4.aar` manifest 声明 `android:minSdkVersion="26"`、`targetSdkVersion="35"`、`com.google.android.apps.aicore.service.BIND_SERVICE`，并查询包 `com.google.android.aicore`。这直接证明 SDK 的运行时边界是系统 AICore binder service，而不是应用代码里的云 HTTP client：<https://dl.google.com/dl/android/maven2/com/google/mlkit/genai-prompt/1.0.0-beta4/genai-prompt-1.0.0-beta4.aar>。
- AAR 的 `minSdk 26 / target 35` 与当前 NGA 工程 `minSdk 29 / compileSdk 35 / targetSdk 35` 的 Android SDK floor 表面兼容（本工程 `build.gradle:121-123`）；但 beta4 POM 会引入 Kotlin stdlib `2.3.21`，当前工程 Kotlin plugin 是 `2.0.21`，必须先做独立 dependency-resolution/compile spike，不能只依据 manifest 判定构建兼容：<https://dl.google.com/dl/android/maven2/com/google/mlkit/genai-prompt/1.0.0-beta4/genai-prompt-1.0.0-beta4.pom>。

#### 3.3 完整调用链

```text
AiSummaryCard
  -> AiSummaryViewModel observes repository.availability
  -> AppAiDescriptionRepository
  -> AiDescriptionGenerator
  -> OnDeviceAiEngine
  -> Lazy<GenerativeModel> from Generation.getClient()
  -> checkStatus() / download() / generateContent(prompt)
  -> AICore / Gemini Nano on device
```

1. `OnDeviceAiEngineImpl` 先排除 emulator，再调用 `client.get().checkStatus()`；准确映射 `AVAILABLE`、`DOWNLOADABLE`、`DOWNLOADING`，其余状态/异常均为 `Unavailable`：[`OnDeviceAiEngineImpl.kt:17-34`](https://github.com/MartinStyk/apk-analyzer/blob/23a9463fa3c1dddd46e9958aee79eae78a0d2583/core/ai-insights/src/main/kotlin/sk/styk/martin/apkanalyzer/core/aiinsights/ai/OnDeviceAiEngineImpl.kt#L17-L34)。这里 Prompt API 的实际方法名是 `checkStatus()`；不要把 feature-specific Summarization API 的 `checkFeatureStatus()` 机械套过来。
2. 当 UI 收到 `Downloadable` 时展示显式下载按钮；`Downloading` 展示 spinner；`Available` 才加载 summary；`Unavailable`/失败则隐藏 AI 卡片：[`AiSummaryViewModel.kt:27-53`](https://github.com/MartinStyk/apk-analyzer/blob/23a9463fa3c1dddd46e9958aee79eae78a0d2583/feature/app-detail/impl/src/main/kotlin/sk/styk/martin/apkanalyzer/feature/appdetail/impl/components/aisummary/AiSummaryViewModel.kt#L27-L53)、[`AiSummaryCard.kt:83-100`](https://github.com/MartinStyk/apk-analyzer/blob/23a9463fa3c1dddd46e9958aee79eae78a0d2583/feature/app-detail/impl/src/main/kotlin/sk/styk/martin/apkanalyzer/feature/appdetail/impl/components/aisummary/AiSummaryCard.kt#L83-L100)。
3. 点击下载后，repository 用共享 app scope 设置 `Downloading`，避免重复下载；engine 收集 `download()` flow 直到 `DownloadCompleted`，然后再次检查 `FeatureStatus.AVAILABLE`；无论成败都刷新 availability：[`AppAiDescriptionRepositoryImpl.kt:45-66`](https://github.com/MartinStyk/apk-analyzer/blob/23a9463fa3c1dddd46e9958aee79eae78a0d2583/core/ai-insights/src/main/kotlin/sk/styk/martin/apkanalyzer/core/aiinsights/appdescription/AppAiDescriptionRepositoryImpl.kt#L45-L66)、[`OnDeviceAiEngineImpl.kt:36-43`](https://github.com/MartinStyk/apk-analyzer/blob/23a9463fa3c1dddd46e9958aee79eae78a0d2583/core/ai-insights/src/main/kotlin/sk/styk/martin/apkanalyzer/core/aiinsights/ai/OnDeviceAiEngineImpl.kt#L36-L43)。
4. 生成时直接调用 `generateContent(prompt)`，读取首个 candidate text；没有 HTTP request builder、认证 header 或远端模型 ID：[`OnDeviceAiEngineImpl.kt:45-49`](https://github.com/MartinStyk/apk-analyzer/blob/23a9463fa3c1dddd46e9958aee79eae78a0d2583/core/ai-insights/src/main/kotlin/sk/styk/martin/apkanalyzer/core/aiinsights/ai/OnDeviceAiEngineImpl.kt#L45-L49)。
5. repository 只在 availability 为 `Available` 时生成，解析和验证输出；无效输出最多用相同 prompt 再试一次；对同一个 APK 的并发请求用 `Mutex + Deferred` 合并，成功结果按输入 hash 缓存：[`AppAiDescriptionRepositoryImpl.kt:68-107`](https://github.com/MartinStyk/apk-analyzer/blob/23a9463fa3c1dddd46e9958aee79eae78a0d2583/core/ai-insights/src/main/kotlin/sk/styk/martin/apkanalyzer/core/aiinsights/appdescription/AppAiDescriptionRepositoryImpl.kt#L68-L107)、[`AppAiDescriptionRepositoryImpl.kt:145-169`](https://github.com/MartinStyk/apk-analyzer/blob/23a9463fa3c1dddd46e9958aee79eae78a0d2583/core/ai-insights/src/main/kotlin/sk/styk/martin/apkanalyzer/core/aiinsights/appdescription/AppAiDescriptionRepositoryImpl.kt#L145-L169)。

#### 3.4 错误、取消与回退

- availability、download、generation 都通过项目的 `runCatchingCancellable` 包装；它专门重新抛出 `CancellationException`，仅把其他 throwable 转成失败：[`RunCatching.kt:3-11`](https://github.com/MartinStyk/apk-analyzer/blob/23a9463fa3c1dddd46e9958aee79eae78a0d2583/core/common/src/main/kotlin/sk/styk/martin/apkanalyzer/core/common/coroutines/RunCatching.kt#L3-L11)。这是比普通 `catch (Exception)` 更适合 NGA 的模式。
- 非取消错误记录不含 prompt/response 的状态日志，然后转为 `Unavailable`、`false` 或 `null`：[`OnDeviceAiEngineImpl.kt:24-49`](https://github.com/MartinStyk/apk-analyzer/blob/23a9463fa3c1dddd46e9958aee79eae78a0d2583/core/ai-insights/src/main/kotlin/sk/styk/martin/apkanalyzer/core/aiinsights/ai/OnDeviceAiEngineImpl.kt#L24-L49)。
- 它没有云 AI fallback；AI 返回 null 时卡片隐藏，而 APK 的原始分析仍工作。这证明系统模型可以作为非阻断增强能力。
- 但它把下载和生成放入 application scope，导航离开后任务可以继续；这适合可缓存的 APK 描述，不适合本任务要求“离开/账号切换/对象失效即取消”的楼层和用户分析。NGA 应复用 cancellation-safe wrapper 和 typed availability，不复用 application-scope 生命周期。
- UI 没有用户可见的生成错误/重试状态，失败会从 Loading 变 Hidden；下载 flow 也没有 byte progress 或失败原因。这不满足当前任务的明确错误状态要求。

#### 3.5 API Key 与费用判断

- 固定 AI 模块中唯一 client 构造是 `Generation.getClient()`，唯一模型调用是 `checkStatus()` / `download()` / `generateContent()`；没有配置页、Key 类型、Authorization header、endpoint 或云 provider adapter。
- README 也把 AI 网络活动限定为系统模型下载，并把普通网络活动列为 Firebase telemetry；没有按调用访问云模型 endpoint：[`README.md:102-119`](https://github.com/MartinStyk/apk-analyzer/blob/23a9463fa3c1dddd46e9958aee79eae78a0d2583/README.md#L102-L119)。
- 因此这条链是 **no-key system-on-device**，不是 BYOK，也不是作者运营公共 Key/代理。它不能证明“Google 永久不会改变商业条款”，但能证明当前 SDK/API 形态不要求终端用户或作者在调用时提供云模型 Key。

#### 3.6 测试可信度

- GitHub recursive tree 对该 commit 返回完整的 1,401 entries；`core/ai-insights` 与 `components/aisummary` 下没有 `src/test` / `src/androidTest`，Sourcegraph 也未找到 `OnDeviceAiEngine`、download 或 `AiSummaryViewModel` 的测试。项目整体有 CI，但不能把整体 CI 等同于 ML Kit adapter 的行为验证。
- AI 模块 2026-08-12 才通过 `Add on-device AI app summary (#146) (#148)` 加入；2026-08-31/09-01 又追加 emulator 和 lazy-client 修复。项目整体成熟、该能力已进入正式 4.1.2 release，但 AI integration 本身仍新且 beta，应视为“生产中的可信实现样本”，不是已充分验证的 reference implementation。

### 4. Secondary audit: Essentials `v18.0-beta.1`

#### 4.1 版本、依赖与 no-key 证据

- Repository: <https://github.com/sameerasw/essentials>
- Audited release commit: [`76ac0f04f8b9b699db3565d392d2a2cb2740a994`](https://github.com/sameerasw/essentials/commit/76ac0f04f8b9b699db3565d392d2a2cb2740a994)，tag/release [`v18.0-beta.1`](https://github.com/sameerasw/essentials/releases/tag/v18.0-beta.1) 直接指向该 commit。
- GitHub API 快照：2025-12 创建，2,839 stars、72 forks、未归档；最新稳定 `v17.3` 的单个 APK asset 有 12,142 次 GitHub 下载，且仓库有 14 页 release。它明显不是为本次调查临时建立的 sample。
- App 使用 MIT License：[`LICENSE:1-20`](https://github.com/sameerasw/essentials/blob/76ac0f04f8b9b699db3565d392d2a2cb2740a994/LICENSE#L1-L20)。如果未来参考具体代码仍需保留 MIT copyright/permission notice。
- version catalog 固定 Prompt API `1.0.0-beta2`，并在 app module 直接 implementation：[`libs.versions.toml:14-15`](https://github.com/sameerasw/essentials/blob/76ac0f04f8b9b699db3565d392d2a2cb2740a994/gradle/libs.versions.toml#L14-L15)、[`libs.versions.toml:58-59`](https://github.com/sameerasw/essentials/blob/76ac0f04f8b9b699db3565d392d2a2cb2740a994/gradle/libs.versions.toml#L58-L59)、[`app/build.gradle.kts:236-238`](https://github.com/sameerasw/essentials/blob/76ac0f04f8b9b699db3565d392d2a2cb2740a994/app/build.gradle.kts#L236-L238)。
- App plugin 列表只有 Android/Kotlin Compose/KSP，没有 Google Services 或云模型插件：[`app/build.gradle.kts:3-7`](https://github.com/sameerasw/essentials/blob/76ac0f04f8b9b699db3565d392d2a2cb2740a994/app/build.gradle.kts#L3-L7)。AI service 同样只以无参数 `Generation.getClient()` 取得系统 client；这是第二条独立的 no-key 证据。

#### 4.2 产品调用链

1. 设置页先调用 `isSupported()`，只有 `AVAILABLE / DOWNLOADABLE / DOWNLOADING` 才显示用户 opt-in 开关；开关默认 false：[`GenAIAutomationService.kt:27-36`](https://github.com/sameerasw/essentials/blob/76ac0f04f8b9b699db3565d392d2a2cb2740a994/app/src/main/java/com/sameerasw/essentials/domain/genai/GenAIAutomationService.kt#L27-L36)、[`SettingsActivity.kt:313-320`](https://github.com/sameerasw/essentials/blob/76ac0f04f8b9b699db3565d392d2a2cb2740a994/app/src/main/java/com/sameerasw/essentials/ui/activities/SettingsActivity.kt#L313-L320)、[`SettingsActivity.kt:840-848`](https://github.com/sameerasw/essentials/blob/76ac0f04f8b9b699db3565d392d2a2cb2740a994/app/src/main/java/com/sameerasw/essentials/ui/activities/SettingsActivity.kt#L840-L848)。
2. `suggestAutomation()` 再检查 status。`UNAVAILABLE` 返回失败；`DOWNLOADABLE` 调用 `download().collect` 并捕获 `DownloadFailed`；`DOWNLOADING` 继续 collect；`AVAILABLE` 直接进入推理：[`GenAIAutomationService.kt:39-76`](https://github.com/sameerasw/essentials/blob/76ac0f04f8b9b699db3565d392d2a2cb2740a994/app/src/main/java/com/sameerasw/essentials/domain/genai/GenAIAutomationService.kt#L39-L76)。
3. 它把用户文本和本地 automation tags 组成 prompt，使用 `generateContentRequest(TextPart(...))` 设置 temperature，随后调用 `generativeModel.generateContent(request)`，解析第一个 candidate 的 JSON：[`GenAIAutomationService.kt:78-156`](https://github.com/sameerasw/essentials/blob/76ac0f04f8b9b699db3565d392d2a2cb2740a994/app/src/main/java/com/sameerasw/essentials/domain/genai/GenAIAutomationService.kt#L78-L156)、[`GenAIAutomationService.kt:156-179`](https://github.com/sameerasw/essentials/blob/76ac0f04f8b9b699db3565d392d2a2cb2740a994/app/src/main/java/com/sameerasw/essentials/domain/genai/GenAIAutomationService.kt#L156-L179)。
4. ViewModel 暴露 `Idle / Loading / Success / Error`，用 `viewModelScope` 执行；成功结果进入 preview，用户明确 Confirm 后才映射并保存 automation，不会把模型结果直接执行：[`DIYViewModel.kt:31-51`](https://github.com/sameerasw/essentials/blob/76ac0f04f8b9b699db3565d392d2a2cb2740a994/app/src/main/java/com/sameerasw/essentials/viewmodels/DIYViewModel.kt#L31-L51)、[`DIYViewModel.kt:114-145`](https://github.com/sameerasw/essentials/blob/76ac0f04f8b9b699db3565d392d2a2cb2740a994/app/src/main/java/com/sameerasw/essentials/viewmodels/DIYViewModel.kt#L114-L145)。
5. 错误通过 `Result.failure` 进入 UI，当前只显示 Toast 后 reset：[`DIYScreen.kt:245-258`](https://github.com/sameerasw/essentials/blob/76ac0f04f8b9b699db3565d392d2a2cb2740a994/app/src/main/java/com/sameerasw/essentials/ui/features/automation/DIYScreen.kt#L245-L258)。没有 cloud/BYOK fallback。
6. App 内说明明确区分：推理本地、模型下载后可 offline，且有前台限制与 AICore per-app battery/inference quota：[`strings.xml:1507-1508`](https://github.com/sameerasw/essentials/blob/76ac0f04f8b9b699db3565d392d2a2cb2740a994/app/src/main/res/values/strings.xml#L1507-L1508)。

#### 4.3 可借鉴与不能照抄

- 可借鉴：设置默认关闭；运行时隐藏不支持能力；模型结果必须经过用户 preview/confirm；系统模型错误不触发云发送。
- 不能照抄：整个 service 用 `catch (Exception)`，没有显式重新抛出 coroutine cancellation；`download().collect` 只记录 `DownloadFailed`，没有要求收到 `DownloadCompleted` 或在下载后重新检查 `AVAILABLE`；UI 把下载和生成都压成一个 `Loading`，没有进度、取消或 typed quota/background error。
- GitHub tree 的 1,272 entries 中没有名称/内容对应 `GenAIAutomationService` 的 test。AI 功能首次加入 commit 是 2026-08-06，审计 release 是 beta；仓库与发行渠道成熟不代表该 AI feature 已经成熟。

### 5. Cross-check: Meshtastic Android 的 flavor/fallback 做法

Meshtastic 是有代表性的产品级交叉检查，但不是 availability 的推荐实现。

- Repository/commit: [`meshtastic/Meshtastic-Android@5f1a4e58307d30a0a23c3e0b206562448b4ba00a`](https://github.com/meshtastic/Meshtastic-Android/commit/5f1a4e58307d30a0a23c3e0b206562448b4ba00a)，同 commit 有 GitHub `Snapshot 29322164` release；2026-09-05 为 1,827 stars、511 forks，GPL-3.0。
- 依赖固定 `genai-prompt:1.0.0-beta4`，但只用 `googleImplementation`；不会进入 F-Droid flavor：[`libs.versions.toml:73-77`](https://github.com/meshtastic/Meshtastic-Android/blob/5f1a4e58307d30a0a23c3e0b206562448b4ba00a/gradle/libs.versions.toml#L73-L77)、[`androidApp/build.gradle.kts:324-334`](https://github.com/meshtastic/Meshtastic-Android/blob/5f1a4e58307d30a0a23c3e0b206562448b4ba00a/androidApp/build.gradle.kts#L324-L334)。
- Google flavor 绑定 `GeminiNanoSummaryProvider`；F-Droid flavor 绑定 `AlgorithmicSummaryProvider`，后者不需要模型：[`GoogleAiModule.kt:42-54`](https://github.com/meshtastic/Meshtastic-Android/blob/5f1a4e58307d30a0a23c3e0b206562448b4ba00a/androidApp/src/google/kotlin/org/meshtastic/app/di/GoogleAiModule.kt#L42-L54)、[`FdroidAiModule.kt:30-39`](https://github.com/meshtastic/Meshtastic-Android/blob/5f1a4e58307d30a0a23c3e0b206562448b4ba00a/androidApp/src/fdroid/kotlin/org/meshtastic/app/di/FdroidAiModule.kt#L30-L39)。这说明“无需 Key/无需按次付费”和“依赖是自由开源软件”是两件不同的事。
- ML Kit provider 同样用无参数 `Generation.getClient()`；生成时限制 temperature/topK/max output tokens，空输出或任意异常立即返回确定性算法 summary：[`GeminiNanoSummaryProvider.kt:44-68`](https://github.com/meshtastic/Meshtastic-Android/blob/5f1a4e58307d30a0a23c3e0b206562448b4ba00a/androidApp/src/google/kotlin/org/meshtastic/app/discovery/GeminiNanoSummaryProvider.kt#L44-L68)、[`GeminiNanoSummaryProvider.kt:81-100`](https://github.com/meshtastic/Meshtastic-Android/blob/5f1a4e58307d30a0a23c3e0b206562448b4ba00a/androidApp/src/google/kotlin/org/meshtastic/app/discovery/GeminiNanoSummaryProvider.kt#L81-L100)。
- **关键缺陷**：`checkAvailability()` 的注释承认 `checkStatus()` 是 suspend，但实际仅判断 `generativeModel != null`；没有调用 status，也没有 `download()`。因此“client 可构造”会被误报为 available，最后靠 generation exception fallback：[`GeminiNanoSummaryProvider.kt:102-110`](https://github.com/meshtastic/Meshtastic-Android/blob/5f1a4e58307d30a0a23c3e0b206562448b4ba00a/androidApp/src/google/kotlin/org/meshtastic/app/discovery/GeminiNanoSummaryProvider.kt#L102-L110)。
- 它有 interface/algorithmic fallback contract 测试，但没有 `GeminiNanoSummaryProvider` 的直接测试：[`DiscoverySummaryAiProviderTest.kt:56-128`](https://github.com/meshtastic/Meshtastic-Android/blob/5f1a4e58307d30a0a23c3e0b206562448b4ba00a/feature/discovery/src/commonTest/kotlin/org/meshtastic/feature/discovery/DiscoverySummaryAiProviderTest.kt#L56-L128)。
- 对 NGA 的启示是复用“provider interface + 本地确定性 fallback + distribution flavor”思想，availability 和 cancellation 则采用 ApkAnalyzer 那种 typed/suspend 路线。云 BYOK fallback 涉及不同数据去向，不能像本地算法 fallback 一样静默执行。

### 6. Google 官方机制与“免费”的准确边界

#### 6.1 这不是免费云额度

- ML Kit GenAI overview 明确说 GenAI APIs 建立在 AICore 之上，共用设备中的 Gemini Nano；输入、推理、输出本地处理，在无可靠网络时仍能推理，并写明 **“No additional server cost incurred for each API call”**：<https://developers.google.com/ml-kit/genai>（页面 2026-09-05 抓取；当前页面 last-updated 信息见页面底部）。
- `genai-prompt` AAR 直接绑定 `com.google.android.apps.aicore.service.BIND_SERVICE`；三个项目的实际生成链均没有 API Key。模型下载可联网，但生成不是开发者/用户拿 Key 调云 API。
- 所以建议产品文案写：**“使用设备系统中的 Gemini Nano；不需要云 API Key；模型准备完成后推理在设备上进行；不会产生云模型按次调用费。”** 不写“永久免费 API”或“完全不联网”，因为模型/配置下载和 SDK diagnostics 仍可能联网，未来条款也可能变化。

#### 6.2 仍有系统限制和数据披露义务

- AICore 有 per-app quota：短时间请求过多可返回 `ErrorCode.BUSY`，长期额度可能返回 `PER_APP_BATTERY_USE_QUOTA_EXCEEDED`；只允许 top foreground app 推理，后台会返回 `BACKGROUND_USE_BLOCKED`：<https://developers.google.com/ml-kit/genai#quota_per_application>。
- 支持设备与设备上的 Gemini Nano 版本不同，语言能力还取决于设备配置和已下载模型。不能用静态品牌白名单代替每次/每会话 runtime status：<https://developers.google.com/ml-kit/genai#supported_devices>。
- Prompt API 页面明确标为 beta、无 SLA 或 deprecation policy，并警告可能有 breaking changes：<https://developers.google.com/ml-kit/genai/prompt/android>（last updated 2026-07-15 UTC）。
- “prompt 不作为云推理数据发送”不等于 SDK 零 telemetry。Google 的 Android data disclosure 页列出 GenAI SDK 会为 diagnostics/usage analytics 收集 device/app info、user/device/installation identifiers、performance metrics、API configuration、**input/output size**、feature version、download/event types 和 error codes；页面没有说收集 prompt/output 内容本身：<https://developers.google.com/ml-kit/android-data-disclosure>（last updated 2026-07-15 UTC）。NGA 的隐私说明与 Play Data Safety 需要如实覆盖这些 metadata。
- Additional Terms 允许 API Clients 用于 production，但同时禁止将标为 Preview、Experimental Access 或类似 designation 的服务用于 production，并允许 rate limits；Prompt 页面又把当前 API 标为 beta。是否把 beta 解释为“类似 designation”不能由源码审计替代，release 前应做条款/法务确认：<https://developers.google.com/ml-kit/genai-terms>（last updated 2025-10-29 UTC）。

#### 6.3 许可证/分发不是“免费调用费”的同义词

- Google Maven POM 的 license 字段是 `ML Kit Terms of Service`，不是 Apache/MIT 开源许可证：<https://dl.google.com/dl/android/maven2/com/google/mlkit/genai-prompt/1.0.0-beta4/genai-prompt-1.0.0-beta4.pom>。
- Meshtastic 为 F-Droid 专门排除 ML Kit，说明无 Key、无 server cost 仍可能是 non-free/proprietary distribution dependency。
- 当前 NGA fork 是 GPL-2.0-only；把 ML Kit AAR 随 APK 分发前必须单独检查 Google 条款、GPL-2.0 组合分发和目标发行渠道。本文不作法律意见。若不能接受该依赖，可保留 BYOK 或评估自行下载的开源本地模型，但那不是“系统预装 AICore”同一机制。

### 7. 对当前 NGA 规划的具体含义

本研究支持把当前“只允许 BYOK”扩展成统一 provider 能力，而不是用系统模型完全替代 BYOK：

```text
AiProvider
  ├─ SystemOnDeviceProvider (AICore / Gemini Nano, no key)
  └─ ByokCloudProvider      (OpenAI-compatible, user key)
```

建议合同：

1. `SystemOnDeviceProvider` 不创建 `ProviderConfig.apiKey`，也不经过 `KeyVault`/OkHttp；其状态至少是 `Checking / Available / Downloadable / Downloading / Unavailable(reason) / Busy / QuotaExceeded / ForegroundRequired / Error`。
2. provider adapter 内使用 `Generation.getClient()` 的 lazy construction、`checkStatus()`、用户触发 `download()`、下载后 re-check、cancellation-safe exception mapping；不要使用 client-non-null heuristic。
3. 楼层总结/用户分析的 generation job 绑定对象 key、账号/provider generation 和 screen/request scope。不要照搬 ApkAnalyzer 的 application-scope generation；取消必须传播到系统调用，旧结果必须丢弃。
4. 系统 provider unavailable 时先显示原因和“改用 BYOK”的显式操作；不能自动将已为“端侧处理”预览过的 NGA payload 发给云 provider。切换到 BYOK 后重新走 provider + data-category consent 和 payload preview。
5. 端侧模型输出也要验证、限 token、标注可能不准确。用户活动分析尤其不能因“本地”就恢复政治倾向/敏感属性推断。
6. 为 NGA 中文正文做真实支持设备验证。官方只保证语言能力依设备/模型配置而异；三个审计项目均未提供中文 NGA 长文本质量、token 上限或多轮对话证据。
7. 先做最小 build spike：`compile/target 35`、Kotlin 2.0.21 下解析最新 beta4，release/R8 构建、API 29 安装、支持设备 status/download/generate、unsupported device、不在前台、quota、取消和 process recreation。失败时不应迫使整个论坛 App 提升 SDK/Kotlin 工具链。
8. 若需要 F-Droid/完全自由软件构建，采用 Meshtastic 风格的 source set/flavor：Google flavor 含 SystemOnDeviceProvider，free flavor 绑定 `UnavailableSystemProvider` 或纯算法 fallback；两者共享 domain/UI 状态合同。

这会改变当前任务 PRD 的一句关键产品假设：`BYOK` 仍是云后端政策，但不应再被定义为唯一 AI provider 类型。最终计划应把“项目不运营代理、不提供公共 Key/额度”保留，同时补充“支持设备可选择系统端侧 provider，不需要 Key；不支持时可显式切换 BYOK”。

## Files Found

### ApkAnalyzer `23a9463fa3c1dddd46e9958aee79eae78a0d2583`

- `core/ai-insights/build.gradle.kts` — ML Kit Prompt API module dependency。
- `gradle/libs.versions.toml` — `genai-prompt:1.0.0-beta4` version pin。
- `core/ai-insights/.../di/AiInsightsModule.kt` — `Generation.getClient()` singleton provider。
- `core/ai-insights/.../ai/AiAvailability.kt` — typed four-state availability。
- `core/ai-insights/.../ai/OnDeviceAiEngineImpl.kt` — emulator guard、status、download、generation、error mapping 的核心调用链。
- `core/common/.../coroutines/RunCatching.kt` — cancellation-safe exception wrapper。
- `core/ai-insights/.../appdescription/AppAiDescriptionRepositoryImpl.kt` — shared availability、download lifecycle、coalescing、validation/retry/cache。
- `feature/app-detail/impl/.../aisummary/AiSummaryViewModel.kt` — availability 到 UI state 的映射。
- `feature/app-detail/impl/.../aisummary/AiSummaryCard.kt` — download/download-progress/loading/hidden UI。
- `README.md` — product/release、on-device privacy、network and device requirements 声明。
- `LICENSE` — GPL-3.0 text。

### Essentials `76ac0f04f8b9b699db3565d392d2a2cb2740a994`

- `gradle/libs.versions.toml` — Prompt API beta2 和 schema dependencies。
- `app/build.gradle.kts` — app plugins、minSdk 26 和 ML Kit dependency。
- `app/src/main/java/com/sameerasw/essentials/domain/genai/GenAIAutomationService.kt` — status、download、prompt、generate、parse/error 完整链。
- `app/src/main/java/com/sameerasw/essentials/viewmodels/DIYViewModel.kt` — generation state 和 confirm-before-save。
- `app/src/main/java/com/sameerasw/essentials/ui/features/automation/sheets/NewAutomationSheet.kt` — supported + opt-in gate。
- `app/src/main/java/com/sameerasw/essentials/ui/activities/SettingsActivity.kt` — feature availability 与开关。
- `app/src/main/res/values/strings.xml` — on-device/offline/foreground/quota 用户说明。
- `LICENSE` — MIT license and notice requirement。

### Meshtastic Android `5f1a4e58307d30a0a23c3e0b206562448b4ba00a`

- `gradle/libs.versions.toml`、`androidApp/build.gradle.kts` — beta4 只进入 Google flavor。
- `androidApp/src/google/.../GeminiNanoSummaryProvider.kt` — ML Kit generation 和 deterministic fallback；同时包含 availability 缺陷。
- `androidApp/src/google/.../GoogleAiModule.kt` — Google provider binding。
- `androidApp/src/fdroid/.../FdroidAiModule.kt` — F-Droid algorithmic binding。
- `feature/discovery/.../AlgorithmicSummaryProvider.kt` — no-model deterministic fallback。
- `feature/discovery/.../DiscoverySummaryAiProvider.kt` — shared provider boundary。
- `feature/discovery/src/commonTest/.../DiscoverySummaryAiProviderTest.kt` — interface/fallback tests，未覆盖 ML Kit adapter。
- `LICENSE` — GPL-3.0 text。

### Google artifact

- `com.google.mlkit:genai-prompt:1.0.0-beta4` AAR — minSdk/targetSdk、AICore bind permission/package query。
- 同版本 POM — transitive dependencies、Kotlin stdlib version、ML Kit Terms license metadata。
- `maven-metadata.xml` — 2026-09-05 最新/release 仍为 beta4，lastUpdated `20260721184558`：<https://dl.google.com/dl/android/maven2/com/google/mlkit/genai-prompt/maven-metadata.xml>。

## Code Patterns

- **推荐**：`Lazy<GenerativeModel>` -> suspend `checkStatus()` -> typed status -> explicit user download -> wait for `DownloadCompleted` -> re-check `AVAILABLE` -> `generateContent()`。
- **推荐**：所有 system call 使用会重新抛出 `CancellationException` 的 wrapper；错误转 typed domain state，不记录 prompt/response。
- **推荐**：系统 provider 与云 provider 使用相同 domain interface，但各自拥有完全不同的 secret、network、consent 和 execution-location policy。
- **推荐**：端侧错误用本地确定性结果/功能不可用状态降级；若要切云 provider，必须重新 consent，不能静默 fallback。
- **推荐**：proprietary Google dependency 只进入允许的 distribution flavor，free/F-Droid flavor 绑定无依赖 implementation。
- **反例**：以 `Generation.getClient()` 能否构造代替 `checkStatus()`；这会把没有模型/不支持设备误判为 ready。
- **反例**：普通 `catch (Exception)` 包住 suspend generation；容易破坏 cancellation contract。
- **反例**：忽略 `DownloadCompleted`、不在下载后 re-check、把 downloading/generating 都压成无取消的单一 loading。
- **反例**：把“on-device prompt”宣传成“应用完全没有任何数据联网”；SDK diagnostics metadata 和模型下载仍需披露。

## External References

- ML Kit GenAI overview（on-device benefits、supported devices、quota、foreground restriction）: <https://developers.google.com/ml-kit/genai>
- ML Kit Prompt API（beta/no SLA/breaking-change warning）: <https://developers.google.com/ml-kit/genai/prompt/android>
- ML Kit Android data disclosure: <https://developers.google.com/ml-kit/android-data-disclosure>
- ML Kit GenAI Additional Terms: <https://developers.google.com/ml-kit/genai-terms>
- Google Maven Prompt beta4 AAR: <https://dl.google.com/dl/android/maven2/com/google/mlkit/genai-prompt/1.0.0-beta4/genai-prompt-1.0.0-beta4.aar>
- Google Maven Prompt beta4 POM: <https://dl.google.com/dl/android/maven2/com/google/mlkit/genai-prompt/1.0.0-beta4/genai-prompt-1.0.0-beta4.pom>
- Google Maven Prompt metadata: <https://dl.google.com/dl/android/maven2/com/google/mlkit/genai-prompt/maven-metadata.xml>
- GitHub REST repository/release/tree data queried 2026-09-05: <https://api.github.com/repos/MartinStyk/apk-analyzer>、<https://api.github.com/repos/sameerasw/essentials>、<https://api.github.com/repos/meshtastic/Meshtastic-Android>。

## Related Specs

- `.trellis/tasks/07-25-nga-android-advanced/prd.md:22-25` — 当前 BYOK/no-project-proxy、Key 和 SDK matrix 产品约束；需在后续规划中加入 no-key system provider，而不是删除 BYOK。
- `.trellis/tasks/07-25-nga-android-advanced/prd.md:29-32,40-47` — 楼层/用户对象绑定、consent、取消、错误和测试仍适用于端侧 provider；只有“数据离开设备”的提示内容不同。
- `.trellis/tasks/07-25-nga-android-advanced/design.md:52-68` — 现有 `ProviderAdapter -> Flow` 可扩展为 cloud/system 两种 adapter；KeyVault 只属于 BYOK。
- `.trellis/tasks/07-25-nga-android-advanced/design.md:70-88` — 对象 key/generation/cancellation 不能照搬 ApkAnalyzer 的 app-scope lifecycle。
- `.trellis/tasks/07-25-nga-android-advanced/design.md:94-102` — GPL/source ledger、依赖许可证和 API 35 test gate。
- `.trellis/spec/frontend/android-migration-architecture.md:20-67` — route/stateless screen/ViewModel/repository/Flow ownership；ApkAnalyzer 的 UI state 模式与之接近。
- `.trellis/spec/frontend/android-migration-architecture.md:69-96` — license preservation 与依赖边界。
- Root `build.gradle:121-123` — 当前 `minSdk 29 / targetSdk 35 / compileSdk 35`。

## Caveats / Not Found

- 没有在当前环境中运行 AICore、下载 Gemini Nano 或做真机推理；因此没有证明任一具体设备/地区在 2026-09-05 可用，也没有延迟、质量、耗电或 quota 实测。
- 三个真实项目都没有 ML Kit adapter 的直接专项自动化测试。Meshtastic 只测 provider interface/fallback，ApkAnalyzer 和 Essentials 的 AI 路径在固定 tree 中无对应 tests。它们证明“真实项目这样接入”，不证明所有细节都是最佳实践。
- 未发现这些项目要求用户填写 API Key，也未发现它们为上述 ML Kit Prompt 调用配置公共 Key/付费代理；但这不等于整个 App 零网络，ApkAnalyzer 有 Firebase telemetry，系统模型/配置也可能下载。
- Google 官方文档明确说无 additional server cost per call，但未作“永久免费、永不改政策”的承诺。Prompt API 仍是 beta；条款、设备清单和支持模型可变。
- Google SDK 有 diagnostics/analytics metadata 收集；“forum prompt/output 不做云端推理”与“任何数据都不出设备”不能混为一谈。
- Prompt API 的中文质量、NGA BBCode 清洗后长文本上限、多轮聊天、流式输出和本任务两个场景的可靠性未被这三个项目证明，必须另做官方能力核实与授权真机评测。
- `genai-prompt:1.0.0-beta4` 的 AAR Android floor 与本工程匹配，但其 POM dependency graph 和当前 Kotlin 2.0.21/Gradle/AGP/R8 的真实兼容性未构建验证。
- ML Kit Prompt SDK 本身按 ML Kit Terms 分发，不是开源库；当前 GPL-2.0-only APK 的组合分发、F-Droid 接受度和 notice/Data Safety 义务需要独立审查。本文不是法律意见。
- 本文件只研究并提出规划影响，没有修改 PRD/design/implement，也没有启动 planning task 或写产品代码。
