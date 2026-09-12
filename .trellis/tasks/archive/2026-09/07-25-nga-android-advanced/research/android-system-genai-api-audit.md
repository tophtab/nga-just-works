# Research: Android system-level on-device GenAI API audit

- Query: 截至 2026-09-05，核实 Android ML Kit GenAI / Gemini Nano / AICore 是否属于无需用户 API Key、没有云端按次费用的系统端侧调用；并核实中文、token、多轮、设备/地区、下载状态、配额、前台运行、Beta 与条款限制。
- Scope: mixed（Google/Android 官方文档、官方 API reference、官方 Maven metadata、Google 官方 sample，以及本任务现有计划/项目约束）
- Date: 2026-09-05（Asia/Shanghai；所有外部页面均在本日访问）

## Findings

### 1. 结论：这是“系统端侧、无云 Key/按次账单”，不是项目方替用户付费

可以把 ML Kit GenAI + AICore 准确描述为一种真实的“无用户云 API Key”机制，但不能笼统写成“所有 Android 手机免费无限调用”：

| 问题 | 截至本次审计的结论 | 证据强度 |
| --- | --- | --- |
| 推理在哪里执行 | Gemini Nano 由 Android 的 AICore 系统服务在设备上执行 | 官方明确陈述 |
| prompt/result 是否发到 Google 云端 | ML Kit 条款页明确说 input 与 output 不发送到 Google servers | 官方明确陈述；性能/使用 metrics 例外见下文 |
| 用户/项目是否要提供模型 API Key | Android 接入流程只有 Gradle 依赖、`Generation.getClient()`/feature client、状态检查与下载，没有 API Key、Google Cloud project 或 Billing 配置；Google 官方 sample 也没有模型 Key/Authorization 配置，manifest 甚至没有 `INTERNET` 权限 | 官方接入文档 + 官方 sample 的一致证据；官方没有一句独立的“no API key required”口号 |
| 是否有云端按次费用 | ML Kit overview 原文为 “No additional server cost incurred for each API call” | 官方明确陈述 |
| 谁承担计算 | 用户设备/AICore 使用本机硬件、电量、内存和存储；模型/配置由系统组件分发管理 | 官方架构说明 |
| 是否等于永久、无限、全设备免费 | 否。官方没有永久免费/SLA 承诺；有设备白名单、每 App 配额、长期电量配额、前台限制、模型下载、Beta/条款限制 | 官方明确陈述 |

官方原文（Android Developers, **Gemini Nano**，页面 last updated 2026-04-02 UTC）：

> “Gemini Nano lets you deliver rich generative AI experiences without needing a network connection or sending data to the cloud.”

> “On-device generative AI executes prompts locally, eliminating server calls.”

官方原文（ML Kit, **Overview of the ML Kit GenAI APIs**，页面 last updated 2026-09-01 UTC）：

> “GenAI APIs run entirely on-device”

> “No additional server cost incurred for each API call”

因此，它既不是 LNGA 那种用户自备云服务 Key 的 BYOK，也不是 App 作者维护公共 Key/代理并代付模型账单；它是 App 调用设备上由 Google AICore 管理的共享 Gemini Nano。产品文案宜写“端侧系统模型：无需配置云模型 Key；模型推理不产生每次云服务调用费”，不要简化成“永久免费 API”。

### 2. 隐私与联网边界：prompt 本地，但更新和 metrics 会联网

ML Kit Terms & Privacy（last updated 2025-05-14 UTC）原文：

> “processing of the input data (e.g. images, video, text) fully happens on-device, and ML Kit does not send that data and the resultant outputs to Google servers.”

同页同时明确：

- ML Kit 会不定期联系 Google 服务器，获取 bug fixes、updated models 和 hardware accelerator compatibility information。
- ML Kit 会向 Google 发送 App 中 API 的 performance/utilization metrics，用于性能、调试、维护、改进以及滥用检测。
- 开发者有责任按适用法律告知用户 Google 对 metrics 的处理。

Android 的 AICore 架构页还说明：AICore 本身没有直接互联网访问；包括模型下载在内的请求经开源 Private Compute Services companion APK 路由。AICore 在一次请求处理后不保存 input/output。这个“不保存”不能扩展解释为 App 自身也不会保存内容：实验性的 prefix caching 会在 App 私有存储内保存加密 cache 和包含原始 prefix 文本的 metadata，App 必须显式提供清理能力。

所以，对本项目应区分两种 consent 文案：

- `SystemOnDeviceProvider`：正文/prompt 留在设备；可能下载模型/配置并发送不含正文的 API metrics。
- `ByokCloudProvider`：正文/prompt 会发给用户选择的第三方 provider，并可能按其账户计费。

不能因为端侧推理就宣称“完全零联网”或“没有任何数据发往 Google”。

### 3. 中文能力：三个专用文本 API 明确不支持中文；Prompt API 未给出中文保证

| API | 官方列出的语言（2026-09-01 页面） | 中文 NGA 内容判断 | 输入限制 |
| --- | --- | --- | --- |
| Summarization | English, Japanese, Korean | **不含中文，不能作为中文楼层摘要的受支持路径** | `< 4000 tokens`；`ARTICLE` 还必须 `> 400 characters`，至少约 300 English words 表现最佳；输出固定 1–3 bullets |
| Rewriting | English, Japanese, French, German, Italian, Spanish, Korean | **不含中文** | `< 256 tokens` |
| Proofreading | English, Japanese, French, German, Italian, Spanish, Korean | **不含中文** | `< 256 tokens` |
| Prompt | 文档只说 natural-language text/custom prompt，未发布支持语言 allowlist，也未明确保证简体/繁体中文 | 可以作为中文实验候选，但**不能标注为官方支持中文**；必须按 Nano 版本/设备做中文质量集评测并运行时降级 | 文档要求 input `< 4000 tokens`；输出配置范围见下一节 |

Prompt overview 把 “Short translations” 和 “Guided summarization” 列为用例，不等于给中文作出支持承诺。总 overview 还明确说 language availability 可能随设备配置及已下载模型变化。官方的 `Content`、`GenerateContentRequest` 和 `GenerativeModel` reference 也没有公开语言枚举或中文能力表。

对 NGA 的直接影响：

- “当前楼层 AI 总结”不能调用 feature-specific `Summarization` 并声称支持中文。
- 若采用系统模型，只能使用通用 `Prompt`，按 `getBaseModelName()` 记录 Nano v2/v3/v4 分层测试结果，并在 UI 标为试验性能力；中文质量不达门槛时回退 BYOK，不能静默给出低可信摘要。
- token 不能用汉字数或“约 3000 English words”换算；应调用 SDK `countTokens()`。

### 4. Prompt token、输出与多轮上下文

Prompt Get Started（last updated 2026-07-15 UTC）写明：

- input 必须 `< 4000 tokens`；避免要求超过 4K tokens 的长输出。
- `maxOutputTokens` 可配置。
- streaming 与 non-streaming 均支持。

当前官方 Java reference 对 `GenerateContentRequest.getMaxOutputTokens()` 的原文限制是 `1..4096`，默认 `4096`。`GenerativeModel.getTokenLimit()` 返回包含 input + output 的总 token limit；`countTokens()` 只统计 input，因此正确判定条件是：

```text
countTokens(fullRequest) + requestedMaxOutputTokens <= generativeModel.getTokenLimit()
```

不能只验证正文 `< 4000`；system instructions（官方建议低于约 100–200 tokens）、schema、prefix 与重发的历史都占上下文。不同设备/模型的总上限应以运行时 `getTokenLimit()` 为准。

多轮方面，AICore Developer Preview 官方页明确说：

> “each prompt will be treated independently from previous interactions.”

Prompt package 没有云端会话或自动历史 API。`Content` 被定义为一次 conversation turn/message，`GenerateContentRequest` 可承载 content 列表，且可以使用 prefix cache，但 App 仍须自行保存、裁剪并在后续请求中重新组织所需上下文；cache 只是复用前缀的推理状态，不是自动对话记忆。Google 官方 `OpenPromptActivity` 虽显示 chat-like UI，每次仍只由当前 request 构造 `GenerateContentRequest` 后调用 `generateContent()`。

因此本任务的“继续追问”必须是本地显式 history policy，并接受 `< 4000` input/总 token limit；建议优先保留冻结的对象上下文 + 最近少量追问，达到预算后让用户选择清除/截断，不应假定 AICore 记住上一轮。

### 5. 运行时状态、模型下载与初始化

Prompt API 使用 `generativeModel.checkStatus()`；Summarization/Rewriting/Proofreading 等专用 API 使用 `checkFeatureStatus()`。两者都返回四态：

| 状态 | 含义/产品行为 |
| --- | --- |
| `UNAVAILABLE` | 设备不支持，或设备尚未取得最新支持配置；不要展示可执行入口，提供 BYOK/无 AI 降级 |
| `DOWNLOADABLE` | 此设备支持但模型/feature 尚未下载；显示预计下载状态并调用 `download()`/`downloadFeature()`；专用 API 的首次 inference 也可触发下载 |
| `DOWNLOADING` | 下载中；专用 API 文档称请求可等待下载完成再执行，但产品不应表现为卡死，须显示进度/取消/稍后重试 |
| `AVAILABLE` | 当前已下载且可推理；仍可能受配额、前台、策略与资源错误影响 |

官方要求在展示相关 UI 前先调用 status API。刚设置/重置设备或清空/重装 AICore 后，AICore 可能需要从几分钟到几小时下载最新配置；网络/DNS失败也会令 feature 不可用。设备解锁 bootloader 时 API 不受支持。专用 API 还可能需下载很小的 LoRA adapter；若 Gemini Nano 基座未存在，下载会更久。Prompt 可调用 `warmup()` 降低首次推理等待，但仍必须正常处理失败。

`UNAVAILABLE` 不是稳定的“永不支持”判定，也不应仅靠机型字符串预判；官方反复称 status API 是特定设备、语言/options 配置的 definitive runtime check。

### 6. 当前设备覆盖（官方 overview 更新于 2026-09-01）

SDK 的 `minSdk 26` 只是安装/API 下限，不代表任意 Android 26+ 设备可运行。根项目 `minSdk 29` 满足 SDK 版本门槛（`build.gradle:121-123`），但实际硬件必须在 AICore 支持范围且 runtime status 可用。

#### Feature-specific APIs（Summarization / Proofreading / Rewriting / Image Description）

- Google: Pixel 9 / 9 Pro / 9 Pro XL / 9 Pro Fold；Pixel 10 / 10 Pro / 10 Pro XL / 10 Pro Fold；Pixel 11 / 11 Pro / 11 Pro XL / 11 Pro Fold
- Honor: Honor 400 Pro、Magic 7、Magic 7 Pro、Magic 8 Pro、Magic V5
- iQOO: iQOO 13、iQOO 15
- Lenovo: Idea Tab Pro Gen 2、Legion Tab Gen 5 (8.8")
- Motorola: Razr 60 Ultra、Razr Ultra 2025、Signature
- OnePlus: OnePlus 13、13s、15、15R
- OPPO: Find N5、Find X8/X8 Pro、Find X9/X9 Pro、Reno 14 Pro 5G、Reno 15 Pro 5G/Mini 5G/Max 5G
- POCO: F7 Ultra、F8 Pro、F8 Ultra、X7 Pro、X8 Pro
- realme: GT 7 Pro、GT 7T
- Samsung: Galaxy S25/S25+/S25 Ultra、S26/S26+/S26 Ultra、Z Fold7、Z Flip8、Z Fold8/Z Fold8 Ultra、Z TriFold
- Sharp: AQUOS R11
- Sony: Xperia 1 VIII
- vivo: X200/X200 Pro/X200 FE/X200T、X300/X300 Pro、X Fold3 Pro/X Fold5、T4 Ultra
- Xiaomi: 14T Pro、15/15T/15T Pro/15 Ultra、17/17 Ultra、Pad Mini

#### Prompt API（按设备上的 Nano 基座分组）

- `nano-v2`: Honor Magic V5/7/7 Pro；iQOO 13；Motorola Razr 60 Ultra/Razr Ultra 2025；OnePlus 13/13s；OPPO Find N5；POCO F7 Ultra/F8 Pro/F8 Ultra/X7 Pro/X8 Pro；realme GT 7 Pro；Samsung Z Fold7/Z TriFold；vivo X200 FE/T4 Ultra；Xiaomi 14T Pro/15/15T/15T Pro/15 Ultra/17/17 Ultra/Pad Mini。
- `nano-v3`: Pixel 9 全系列与 Pixel 10 全系列；Honor Magic 8 Pro；iQOO 15；Lenovo Idea Tab Pro Gen 2/Legion Tab Gen 5；Motorola Signature；OnePlus 15/15R；OPPO Find X8/X8 Pro/X9/X9 Pro 与 Reno 14/15 Pro 系列；realme GT 7T；Samsung S26 系列；Sharp AQUOS R11；Sony Xperia 1 VIII；vivo X200/X200 Pro/X200T/X300/X300 Pro。
- `nano-v4`: Pixel 11 全系列；Samsung Galaxy Z Flip8、Z Fold8、Z Fold8 Ultra。

官方称会继续扩展列表，并警告同一 prompt 在不同 Nano 版本上可产生不同输出。不能把硬编码列表当授权真相；它只适合解释原因或遥测分层，实际入口仍由 `checkStatus()` 决定。

### 7. 地区范围：没有正向国家清单，不能承诺中国大陆可用

本次检查的 overview、Prompt、Summarization、Rewriting、Proofreading、AICore Developer Preview 和 API reference **没有发布正向 supported country/region 列表**。Additional Terms 只给出负向约束：不得在出口管制禁止的国家访问/提供服务，不得绕过 technical or geographical restrictions。

机型列有 Honor、OPPO、vivo、小米等品牌，并不证明这些机型的中国大陆固件/销售地区都带可用 AICore。官方还说语言/feature availability 取决于具体 device configuration 与已下载模型。因此只能表述为：

- 地区正向覆盖：官方当前未说明；
- 中国大陆可用性：**未获得一手资料证明，不能承诺**；
- 最终判定：目标发行地区的真实量产固件 + runtime status + 官方条款，缺一不可。

### 8. 配额、频控和前台限制

ML Kit overview 的 “Quota per application” 给出的机制而非数字：

- 短时间请求过多会返回 `ErrorCode.BUSY`，建议 exponential backoff。
- App 超过长期（例如 daily）配额可能返回 `PER_APP_BATTERY_USE_QUOTA_EXCEEDED`；API reference 称这是为保护电池而按 calling app UID 限制。
- `GenAiException.retryDelay` 会在配额错误时给出建议等待时长（未知时为 zero）。
- 官方未公开每分钟/每日数值，不能把它设计成有确定次数的“免费额度”。
- inference 只允许 App 为 top foreground application 时运行；后台、包括 foreground service，都会返回 `BACKGROUND_USE_BLOCKED`。
- Developer Preview 的 quota bypass 仅 Pixel 测试设备可用，且可能影响系统健康；不能进入产品方案或验收假设。

这意味着楼层摘要/用户活动分析必须是前台、显式用户操作；后台预摘要、WorkManager、前台服务继续生成都不成立。遇到 `BUSY` 不应无限重试或自动切云 provider 并发送原文，回退必须再次向用户说明数据将离开设备并取得对应 consent。

### 9. Beta、版本与 Additional Terms 的发布门槛

Prompt、Summarization、Rewriting、Proofreading 页面都带相同警告：

> “This API is offered in beta, and is not subject to any SLA or deprecation policy. Changes may be made to this API that break backward compatibility.”

版本证据存在文档漂移：Prompt Get Started 代码块仍写 `1.0.0-beta2`，而 2026-07-21 官方 release notes、Google Maven metadata 和官方 sample 已是 `genai-prompt:1.0.0-beta4`。实现时应锁定经验证的明确版本，不可复制 guide 中的旧号；feature-specific 四项仍是 `1.0.0-beta1`。Structured Output 是 Alpha，prefix caching 是 Experimental 且只支持部分 Prompt 设备，不能纳入稳定 MVP 假设。

ML Kit GenAI API Additional Terms（页面正文 **Last modified: May 14, 2025**；本次访问页 footer last updated 2025-10-29 UTC）还有几个会直接影响 NGA 客户端发布的门槛：

1. 使用者必须年满 18 岁；不得把 API 用在面向或可能被 18 岁以下用户访问的 App/服务中。当前 NGA 客户端没有从本次研究中获得满足该条件的证据，这是 system provider 的高优先级产品/法律 blocker，而非普通设置提示即可自动解决。
2. 条款说 API Clients 可以用于 production，但 Google 可施加 rate limits；同时禁止把标为 Preview、Experimental Access 或相似 designation 的服务用于 production。核心 API 标为 Beta 是否落入 “similar designation” 文义需要发布前法律/Google 书面解释，不能由工程自行断言。
3. 不得绕过技术/地域限制；只能用于官方文档描述的 features；不得反向工程/提取模型或用其开发竞争模型/产品。
4. 生成结果可能不准确或冒犯；开发者负责 client 与用户体验安全，并须遵守 Prohibited Use Policy。
5. 必须告知用户 metrics 数据处理（见第 2 节）。

在条款与中文/设备验证完成前，建议将 AICore provider 保持 feature flag/off-by-default，而不是写入“所有用户默认免费 AI”的首发验收标准。

### 10. 对当前任务设计的建议

现有 PRD 把 AI 固定为 BYOK，并要求项目不代理/不代付（`prd.md:22-23,40-45,52`）；这与系统端侧 provider 并不冲突，但需要把单一网络 adapter 设计扩展为能力分层：

```text
AiScenarioRequest (同一冻结对象/payload preview)
  -> SystemOnDeviceProvider (ML Kit Prompt; no key; local inference)
       -> runtime status / model download / language-quality gate / foreground quota
  -> ByokCloudProvider (OpenAI-compatible; user key; external transfer/cost fallback)
```

建议的规划变化：

- 不删除 BYOK；端侧只覆盖命中的 AICore 设备，BYOK/禁用 AI 是其余设备和中文质量失败的显式回退。
- provider UI 必须显示 `端侧 / 内容不离开设备 / 无需 Key / 可能下载模型 / 有系统配额` 与 `云端 BYOK / 内容将发送 / 可能计费`，不能共用一个含糊的“免费”标签。
- 楼层中文摘要首选通用 Prompt 的受控实验，不使用不含中文的 Summarization API；用户行为分析也必须有 Nano 版本分层的事实性中文 eval dataset。
- 继续追问由 App 管理有界历史，不能假设系统记忆；每轮重新做 token budget，历史/对象过期沿用当前冻结 identity/generation contract（`design.md:70-88`）。
- provider capability 必须把 `Unavailable / Downloadable(progress) / Available / Busy(retryDelay) / BatteryQuota / ForegroundBlocked / UnsupportedLanguage / TermsBlocked` 建模成 UI 可见状态。
- system provider 不经过 NGA Retrofit/OkHttp，也不需要 KeyVault；但仍复用 consent、redaction、对象绑定、结果归属和日志禁止规则。BYOK 继续遵守独立 no-Cookie client 与 KeyVault 边界（`design.md:52-68`；`.trellis/spec/backend/network-foundation-contract.md:242-253`）。
- 发布前新增三个非代码决策门：18+ 条款适配、目标发行地区量产机可用性、中文 Prompt 质量/安全门。任一未通过，只隐藏 system provider，不阻断论坛核心或 BYOK。

## Files Found

- `.trellis/tasks/07-25-nga-android-advanced/prd.md` — 当前 AI 是 BYOK、两个对象上下文场景、Key/consent/费用提示及 no-proxy 边界（关键行 `22-23`, `27-32`, `40-45`, `52`）。
- `.trellis/tasks/07-25-nga-android-advanced/design.md` — 当前 `ProviderAdapter`、对象冻结、继续追问和验证设计（关键行 `52-68`, `70-88`, `98-103`）。
- `.trellis/tasks/07-25-nga-android-advanced/research/nga-harmony-ai-source-audit.md` — 已确认 LNGA 是带 Bearer Key 的云端 BYOK，不是 Harmony/Android 系统模型；本文件补充真正系统端侧路线。
- `build.gradle` — 根工程当前 `minSdk 29 / compileSdk 35 / targetSdk 35`（`build.gradle:120-123`）；满足 ML Kit API 26 的 SDK 下限，但不改变机型白名单。
- `.trellis/spec/frontend/android-migration-architecture.md` — ViewModel/Repository/Flow 和生命周期所有权（`:17-67`），适合把 AICore 包在独立 provider repository 中。
- `.trellis/spec/backend/network-foundation-contract.md` — 禁止内容/secret 日志与跨 host credential 泄漏（`:242-253`）；system provider 不应复用 NGA network client。

## Code Patterns

### Google 官方 sample（固定提交）

固定 `googlesamples/mlkit@f1047837fe1e02d58063bfc30b830fad23ffbc35`（commit 2026-07-20/21）：

- [`android/genai/app/build.gradle:60-66`](https://github.com/googlesamples/mlkit/blob/f1047837fe1e02d58063bfc30b830fad23ffbc35/android/genai/app/build.gradle#L60-L66) 只声明 ML Kit artifacts（Prompt 为 beta4），无云 SDK、Key 或 billing dependency。
- [`AndroidManifest.xml:17-64`](https://github.com/googlesamples/mlkit/blob/f1047837fe1e02d58063bfc30b830fad23ffbc35/android/genai/app/src/main/AndroidManifest.xml#L17-L64) 没有 `INTERNET` 权限或 API-key metadata；系统服务负责模型分发路径。
- [`OpenPromptActivity.kt:391-419`](https://github.com/googlesamples/mlkit/blob/f1047837fe1e02d58063bfc30b830fad23ffbc35/android/genai/app/src/main/java/com/google/mlkit/genai/demo/kotlin/OpenPromptActivity.kt#L391-L419) 使用 `checkStatus()`、`download()` 及 cancellation listener。
- [`OpenPromptActivity.kt:425-468`](https://github.com/googlesamples/mlkit/blob/f1047837fe1e02d58063bfc30b830fad23ffbc35/android/genai/app/src/main/java/com/google/mlkit/genai/demo/kotlin/OpenPromptActivity.kt#L425-L468) 直接调用本地 `GenerativeModel.generateContent()`/streaming callback。
- [`OpenPromptActivity.kt:781-786`](https://github.com/googlesamples/mlkit/blob/f1047837fe1e02d58063bfc30b830fad23ffbc35/android/genai/app/src/main/java/com/google/mlkit/genai/demo/kotlin/OpenPromptActivity.kt#L781-L786) 通过 `Generation.getClient(generationConfig)` 取得 client，没有 credential 参数。

这些模式证明官方预期调用链是 `SDK -> AICore -> on-device model`，而非 `HTTP Authorization -> 云模型 endpoint`。它们是机制证据，不应直接复制 sample 的 Activity/UI/lifecycle 实现到当前 MVVM 工程。

## External References

以下均为 Google/Android 一手资料，访问日期均为 2026-09-05：

1. Android Developers, **Gemini Nano**（last updated 2026-04-02 UTC）：<https://developer.android.com/ai/gemini-nano> — AICore 架构、local inference、no cloud、offline、Private Compute Services、request isolation 与模型管理。
2. ML Kit, **Overview of the ML Kit GenAI APIs**（last updated 2026-09-01 UTC）：<https://developers.google.com/ml-kit/genai> — no per-call server cost、完整设备清单、Nano v2/v3/v4、per-app quota 与 foreground-only。
3. ML Kit, **Prompt API overview**（last updated 2026-07-15 UTC）：<https://developers.google.com/ml-kit/genai/prompt/android> — custom text/multimodal prompt、Beta 警告、Prompt 与专用 API 取舍。
4. ML Kit, **Get started with Prompt API**（last updated 2026-07-15 UTC）：<https://developers.google.com/ml-kit/genai/prompt/android/get-started> — API 26、client/status/download、streaming、input/output 限制、unlocked bootloader 与初始化错误。
5. Android API reference, `GenerativeModel`: <https://developers.google.com/android/reference/com/google/mlkit/genai/prompt/GenerativeModel> — `countTokens()`、`getTokenLimit()`、status/download/generate contracts。
6. Android API reference, `GenerateContentRequest`: <https://developers.google.com/android/reference/com/google/mlkit/genai/prompt/GenerateContentRequest> — `maxOutputTokens` 1..4096（default 4096）和 request contents。
7. Android API reference, `Content`: <https://developers.google.com/android/reference/com/google/mlkit/genai/prompt/Content> — 单次 conversation turn/message 的结构。
8. ML Kit, **GenAI Summarization API**（last updated 2026-09-01 UTC）：<https://developers.google.com/ml-kit/genai/summarization/android> — English/Japanese/Korean、4000-token、article minimum、feature status/download。
9. ML Kit, **GenAI Rewriting API**（last updated 2026-09-01 UTC）：<https://developers.google.com/ml-kit/genai/rewriting/android> — 七种语言（无中文）、256-token、runtime feature check。
10. ML Kit, **GenAI Proofreading API**（last updated 2026-09-01 UTC）：<https://developers.google.com/ml-kit/genai/proofreading/android> — 七种语言（无中文）、256-token、runtime feature check。
11. ML Kit, **AICore Developer Preview program**：<https://developers.google.com/ml-kit/genai/aicore-dev-preview> — prompt 相互独立、preview model、Wi-Fi 下载、Pixel-only quota bypass；只用于理解限制，不作为 production capability。
12. Android API reference, `GenAiException.ErrorCode`: <https://developers.google.com/android/reference/com/google/mlkit/genai/common/GenAiException.ErrorCode> — `BUSY`、`PER_APP_BATTERY_USE_QUOTA_EXCEEDED`、`BACKGROUND_USE_BLOCKED` 等。
13. Android API reference, `GenAiException`: <https://developers.google.com/android/reference/com/google/mlkit/genai/common/GenAiException> — 配额错误的 `retryDelay`。
14. ML Kit, **Terms & Privacy**（last updated 2025-05-14 UTC）：<https://developers.google.com/ml-kit/terms#privacy> — input/output 不送 Google，更新与 metrics 例外及用户告知义务。
15. **ML Kit GenAI API Additional Terms of Service**（正文 last modified 2025-05-14；footer last updated 2025-10-29 UTC）：<https://developers.google.com/ml-kit/genai-terms> — 18+、production/rate limits、preview/experimental、地域/用途/安全限制。
16. ML Kit **Release notes**（last updated 2026-09-01 UTC）：<https://developers.google.com/ml-kit/release-notes> — Prompt beta4（2026-07-21）、4096 output token 变更等。
17. Google Maven metadata（lastUpdated 20260721184558）：<https://dl.google.com/dl/android/maven2/com/google/mlkit/genai-prompt/maven-metadata.xml> — 当前 latest/release `1.0.0-beta4`。
18. Google official sample：<https://github.com/googlesamples/mlkit/tree/f1047837fe1e02d58063bfc30b830fad23ffbc35/android/genai> — 无 Key 的实际 SDK/AICore 调用链。

## Related Specs

- `.trellis/spec/frontend/android-migration-architecture.md:17-67` — route/screen/ViewModel/repository 分层、不可变 `UiState`、Coroutine/Flow 与 lifecycle ownership。
- `.trellis/spec/backend/network-foundation-contract.md:242-253` — 内容/secret 禁止日志与敏感 header 的 host 边界。
- `.trellis/tasks/07-25-nga-android-advanced/prd.md:22-23,40-45,52` — 现有 BYOK、consent、费用提示、no-proxy 与对象场景验收。
- `.trellis/tasks/07-25-nga-android-advanced/design.md:52-88` — provider adapter、对象冻结/归属、可取消 streaming 与继续追问设计。

## Caveats / Not Found

- 官方文档没有一句独立的 “no API key required”；本结论来自完整官方接入 contract 不含 credential、官方 sample 无 Key/网络权限，以及官方明确的 on-device/no-server-cost 声明。不要把网页自身 DevSite 的公共站点 key 误认为 SDK 所需模型 key。
- 未找到 Prompt API 的官方中文支持矩阵或中文质量承诺；页面提供中文翻译版本也不等于模型支持中文。
- 未找到正向国家/地区清单，尤其没有中国大陆量产固件可用性的官方保证。
- 未找到公开的短期/长期配额数值；配额是设备/AICore 动态策略，只能处理 typed errors 和 `retryDelay`。
- 未在实体支持设备上执行中文 prompt、模型下载、配额或离线测试；当前结论是官方 contract audit，不是设备实测可用性证明。
- 设备名单和 artifact 版本会变化；实现/发布当天必须重新访问 overview、terms、release notes，并以 runtime status 为最终判定。
- “无每次 server cost”不等于永久价格承诺、没有设备资源成本或没有任何网络/telemetry；也不免除 Google terms、年龄、地域、隐私告知和内容安全义务。
- Additional Terms 对 Beta 是否属于禁止 production 的 “other similar designation” 存在文本解释风险；需要发布前向 Google/法律确认，不能由本研究替代法律意见。
