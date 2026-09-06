# AI 调用机制与费用责任对照

- 调查日期：2026-09-05（Asia/Shanghai）
- 问题：参考项目的 AI 到底由谁提供、是否需要用户自己的云 API Key、谁承担调用费用；不把注册送额度或限时赠送视为“免费 API”。
- 方法：更新各参考仓库的远端引用后固定 commit，检索依赖、系统 AI 类型、模型厂商 endpoint、认证 header 和实验分支；系统能力只采用平台官方文档作能力/费用依据。
- 边界：未使用任何真实 API Key、未发起模型推理、未把 provider 当前促销或账户余额当作项目能力。

## 先统一四种完全不同的机制

| 类型 | 调用链 | API Key / 账单 | 本报告如何表述 |
|---|---|---|---|
| 用户自带云 Key（BYOK） | App -> 第三方模型云 | 用户配置自己的 Key；是否计费取决于用户与模型厂商的账户/套餐 | “用户自备、项目不代付”，不能称项目提供免费 AI |
| 项目托管云端 | App -> 项目后端 -> 模型云 | 项目方持有 Key 并承担或转嫁费用 | 只有找到后端、公共凭据或额度契约才成立 |
| 操作系统端侧模型 | App -> 系统 AI API -> 设备内模型 | 不使用云模型 Key；没有逐次云推理账单，但受设备、地区、系统模型、条款和配额限制 | “设备本地、无需云 API Key”，不写“永久免费/所有手机免费” |
| App 自带/下载本地模型 | App -> App 内推理 runtime -> 本地权重 | 不使用云模型 Key；成本转为模型下载、存储、内存、电量、适配与模型许可证 | “本地模型”，不能冒充系统预装模型 |

## 当前 NGA 参考项目

| 项目与固定版本 | 主线事实 | 机制结论 |
|---|---|---|
| `apap6628114/nga_harmony@ea1edc91231bd4b1c50c13bcffc8d710b98fbe21`（2026-09-02） | 固定 `https://api.deepseek.com/responses`；配置要求用户输入 `apiKey` 和模型名；请求发 `Authorization: Bearer <apiKey>` | **BYOK**。不是鸿蒙系统大模型，不是项目方免费提供 |
| `fhyxz001/NgaLite@2d41c474eb0531783596e84ae86190391e2565bd`（2026-09-04） | 主线未检出生成式 AI SDK、模型 endpoint、模型 Key 或本地模型 runtime | 当前没有可归类的生成式 AI 调用机制 |
| `BugenZhao/MNGA@4f75d762582071a4e0fde7b96d520e6113382e9d`（2026-08-05） | 主线未检出生成式 AI 接入 | 当前主线没有 AI 调用机制 |
| `PoiScript/NGNGA@1049648448334e65f7145040ee4967804bd047a2`（2019-12-27） | 未检出生成式 AI 接入 | 没有 AI 调用机制 |
| `mlzzen/open-nga@7caf9a910091569c1fe612b2a324a6dbe4ee67f4`（2026-08-12） | 主线未检出生成式 AI 接入 | 当前没有 AI 调用机制 |
| `Justwen/NGA-CLIENT-VER-OPEN-SOURCE@cdad1abba80236602dc98999bee53d802412a7e4`（2026-08-07） | 主线未检出生成式 AI 接入 | 当前没有 AI 调用机制 |

“未检出”不是对未来分支的永久断言。本次在固定树上同时检索了 AICore/Gemini/ML Kit、Foundation Models/UIConversationContext、OpenAI/DeepSeek 及常见厂商 endpoint、`chat/completions`/`responses`、TensorFlow/LiteRT/ONNX/MNN/llama 等依赖和调用痕迹。

### LNGA：旧版和当前版都由用户自己提供 Key

旧审计快照 `8558a15e5a04c12bf6207265ac33493691aa605e` 有六个 OpenAI-compatible 预设和 custom provider，配置字段包含 `endpoint/apiKey/modelName`，统一使用 Bearer Key。它看起来“内置很多模型”，实际内置的是 endpoint/default model 元数据，而不是项目额度。

当前主线在提交 `0f1fa64` 后删除 `OpenAiCompatibleClient.ets`、Tavily adapter 和多 provider 搜索，改为 `ResponsesApiClient.ets` 与唯一 DeepSeek 预设：

- `AiConfig.ets:20-38` 把 endpoint 固定为 `https://api.deepseek.com`，并明确“用户仅需提供 API Key 与模型名称”；
- `ResponsesApiClient.ets:556-579` 直接向 DeepSeek `/responses` 发请求；
- `ResponsesApiClient.ets:631-636` 构造 `Authorization: Bearer ` + 用户 Key；
- `testConnection()` 也会发一次真实生成请求，并不是本地连通性探测；
- 最新源码导入的 Harmony kit 中没有系统大模型 kit。README 的“六服务商”文字仍在，但已经落后于源码，不能作为当前调用机制依据。

因此，无论旧版还是新版，LNGA 的经济关系都是：项目不提供模型额度，用户自己向云模型厂商取得 Key，费用/限额/数据处理关系属于用户与该厂商。

### MNGA 的系统能力实验不能算已提供功能

远端实验分支 `bz/smart-reply@95d3409b26aea397e4411c85227e65bdc3a162e8` 只给 `ContentTextEditorView.swift` 增加 `UIConversationContext`，尝试把一段对话上下文交给 iOS 系统输入能力：

- 没有第三方模型 endpoint 或 API Key；机制方向属于 Apple 系统能力；
- 它没有用 `FoundationModels.SystemLanguageModel` / `LanguageModelSession` 主动请求并取得通用模型结果；
- commit 标题就是 `not works`，且未进入主线。

所以它只能证明作者试验过“无云 Key 的系统集成”，不能证明 MNGA 当前免费提供可用 AI。

### HarmonyOS 手机：未找到普通第三方 App 可用的通用系统 LLM completion

截至 2026-09-05 的公开官方 SDK/API，未找到 HarmonyOS **手机**上的普通第三方 App 可以直接执行“任意 prompt -> 返回通用 LLM completion”的系统模型接口。需要区分几个名称相近但调用方向不同的能力：

- `@kit.DataAugmentationKit` 的 `localChatModel` 是真实的系统管理端侧问答模型：无需云模型 Key，`init()` 触发本地 AI 模型管理/下载，`chat()` 在端侧回答。但官方当前限定 HarmonyOS `6.0.0(20)`、企业开发者申请、仅 PC/2-in-1；不能用于当前手机 App 承诺。
- Agent Framework Kit 的 Function 组件是拉起已在小艺开放平台发布的指定智能体 UI；公开 API 没有把任意 NGA DTO 作为 prompt 并将 completion 回调给 App 的契约，也不能从“平台提供模型”推导出无条件免费权益。
- Intents Kit 是 HarmonyOS/小艺理解意图后调用 App，方向是“系统调用 App”，不是 App 调系统大模型生成摘要。
- Data Augmentation RAG 要求开发者自己实现 `ChatLLM.streamChat()`；官方 ModelArts 示例使用云 endpoint 和 Bearer API Key，RAG 框架本身不附送模型推理。
- CANN/MindSpore Lite 提供本地推理 runtime，但模型权重由开发者准备/转换/交付；这属于 App 自管本地模型，不是手机系统赠送的通用模型。

所以，用户关于鸿蒙系统模型的机制方向是存在的，但公开可用范围目前没有覆盖本项目所需的 HarmonyOS 手机普通第三方 App。LNGA 当前选择 DeepSeek BYOK 不是在调用这一系统能力。

## 非 NGA：确实存在无云 Key 的系统端侧机制

### Android ML Kit GenAI / AICore

Android 官方当前机制符合问题中所说的“系统自带大模型”：App 通过 ML Kit GenAI API 调用 AICore 管理的 Gemini Nano，在设备上执行推理。官方概览明确写明：输入、推理和输出在本地处理，离线可用，并且每次 API 调用不产生额外服务器成本。它不是免费云 API，也不是 App 作者替用户付费；它避开了云推理调用本身。

但是它不是所有 Android 手机的公共免费接口：

- Prompt API 要求 Android API 26+；本项目 `minSdk 29` 在静态版本上满足，但设备仍必须有受支持的 AICore/Gemini Nano 组合。Get Started 页面示例仍写 `1.0.0-beta2`，而 2026-07-21 release notes、Google Maven metadata 与官方 sample 已到 `1.0.0-beta4`，实现时必须锁定重新验证过的版本；
- 每次展示/调用前必须处理 `UNAVAILABLE`、`DOWNLOADABLE`、`DOWNLOADING`、`AVAILABLE`；首次使用可能下载模型；
- Prompt 输入少于 4000 tokens，不适合要求超过 4000 tokens 的长输出；AICore 有每 App 短时繁忙配额和长期（例如每日）电量配额；只允许前台推理；解锁 bootloader 的设备不支持；
- AICore 当前不支持真正的多轮会话；可流式返回单次生成，但不能直接等同现有云端多轮聊天；
- 专用 Summarization API 只声明 English/Japanese/Korean，不支持 NGA 的中文正文。Prompt API 没有给出可据此承诺所有受支持设备都具备中文质量的固定语言表，因此必须按实际 base model/设备做中文评测后才能开放，不能只看 `AVAILABLE`；
- API 仍为 Beta，无 SLA/稳定弃用承诺；Additional Terms 包含 18+、地区、用途、指标数据告知和 rate limit 等发布门槛。

官方证据：

- <https://developers.google.com/ml-kit/genai>（页面更新 2026-09-01）：端侧数据处理、无逐次 server cost、当前设备清单、配额和前台限制；
- <https://developer.android.com/ai/gemini-nano>：AICore 架构、本地 prompt、无模型请求记录、模型下载由 Private Compute Services 间接处理；
- <https://developers.google.com/ml-kit/genai/prompt/android/get-started>（页面更新 2026-07-15）：API 26 下限、四态 availability、下载、stream、4000-token 限制；版本号须结合 release notes/Maven metadata；
- <https://developers.google.com/ml-kit/genai/summarization/android>（页面更新 2026-09-01）：Summarization 语言与限制；
- <https://developers.google.com/ml-kit/genai-terms>（2025-05-14 条款版本）：18+、生产/预览、地区、用途、metrics disclosure 等约束。

### 三个真实 Android 项目如何无 Key 调用

这条路线不只存在于官方说明或孤立 snippet。源码审计固定了三个真实项目：

| 项目 | 核心调用链 | Key/云费用 | 可借鉴与局限 |
|---|---|---|---|
| `MartinStyk/apk-analyzer@23a9463fa3c1dddd46e9958aee79eae78a0d2583`（release 4.1.2） | `genai-prompt:1.0.0-beta4` -> lazy `Generation.getClient()` -> `checkStatus()` -> 用户触发 `download()` -> re-check -> `generateContent()` | 无 Key、无项目代理；系统 AICore 本地推理 | 四态和 cancellation-safe 错误处理最完整；失败隐藏 AI 卡片，不影响主功能。AI adapter 本身仍缺专项测试 |
| `sameerasw/essentials@76ac0f04f8b9b699db3565d392d2a2cb2740a994`（v18.0-beta.1） | `genai-prompt:1.0.0-beta2` -> status/download -> `generateContent()` -> JSON suggestion preview | 无 Key、无云 AI client | 默认关闭，模型结果先预览再由用户确认；下载/取消状态处理不够严谨 |
| `meshtastic/Meshtastic-Android@5f1a4e58307d30a0a23c3e0b206562448b4ba00a` | Google flavor 使用 beta4 Gemini Nano provider；F-Droid flavor 绑定纯算法 summary | 两条都无云模型 Key/费用 | 证明 Google proprietary dependency 可与 free flavor 隔离；但其 Gemini availability 只看 client 非空、没真实 `checkStatus()`，不能照抄 |

这些项目的模型调用都没有 endpoint、Bearer header、云 project/billing 配置，说明真实调用链就是 App 绑定系统 AICore，而不是作者在后台代付。完整固定文件/行证据见 `research/android-system-genai-open-source-audit.md`。

同时要保留两个工程事实：当前 Prompt beta4 POM 会引入 Kotlin stdlib 2.3.21，而本项目 Kotlin plugin 是 2.0.21，必须先做依赖/编译/R8 spike；ML Kit AAR 受 ML Kit Terms 约束而非 Apache/MIT 开源许可证，GPL-2.0-only 组合分发和目标渠道必须审查，必要时像 Meshtastic 一样隔离 source set/flavor。

### Apple Foundation Models 是同类机制，但 MNGA 没有采用

Apple 官方 `SystemLanguageModel` 是 Apple Intelligence 的设备端文本模型，App 通过 `LanguageModelSession` 等系统 API 使用，不需要在调用接口里配置第三方云模型 Key；可用性取决于设备、地区、系统模型准备状态和语言。它是 iOS/iPadOS/macOS 等平台能力，不能移植为 Android 后端。

截至本次固定分支，MNGA 只试验了 `UIConversationContext`，并没有采用这一可编程 Foundation Models 调用链。官方资料：

- <https://developer.apple.com/documentation/foundationmodels/systemlanguagemodel.md>
- <https://developer.apple.com/documentation/foundationmodels.md>
- <https://developer.apple.com/documentation/uikit/uiconversationcontext.md>

## 对当前 Android 计划的直接含义

1. 不再把“AI = BYOK”写死在业务场景里；场景请求、预览、结果和对象绑定应面向统一执行后端。
2. 设计两个不同能力的实现：`SystemOnDeviceProvider` 与 `ByokCloudProvider`。系统端侧路径不需要 Key；云端路径仍由用户自备 Key。
3. “自动”只能在已验证的系统端侧能力中自动选择；系统不可用时应提示用户选择/配置云端，**不得因 fallback 静默把正文发到云端并产生费用**。
4. UI 必须显示“设备本地”或具体云 provider，并分别说明联网、模型下载、数据是否离开设备、是否可能产生云费用；不要使用笼统的“免费”。
5. 由于中文、设备覆盖、真正多轮、Beta/条款和当前无可用 AICore 实机证据，系统路径先作为有明确晋级门的能力；BYOK 保留为兼容回退，而不是废弃。
6. App 自带/下载本地模型是第三条无云 Key 路线，但它不是系统模型，体积、性能、中文质量和许可证需要独立评估，不应偷偷塞进当前 MVP。

更完整的专项证据：

- `research/android-system-genai-api-audit.md`
- `research/android-system-genai-open-source-audit.md`
- `research/harmony-system-ai-and-local-model-audit.md`
