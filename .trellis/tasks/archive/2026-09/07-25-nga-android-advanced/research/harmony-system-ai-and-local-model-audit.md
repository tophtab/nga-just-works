# Research: HarmonyOS 系统 AI 与无云 Key 本地模型审计

- Query: 核验 `nga_harmony` 及非 NGA 项目的 AI 实际调用链；重点判断 HarmonyOS 手机是否向普通第三方 App 提供无需开发者/用户云 API Key 的通用大模型接口，并区分 BYOK、系统端侧模型、平台托管智能体和 App 自管本地模型。注册赠送、试用额度和限时活动不计为“免费 API”。
- Scope: mixed（本地固定源码、外部固定提交、HarmonyOS/华为云/Android 官方文档；未调用真实模型服务）
- Date: 2026-09-05（Asia/Shanghai）

## Findings

### 执行结论

截至 2026-09-05，在本次检索到的公开官方 SDK/API 中，**没有找到可供 HarmonyOS 手机上的普通第三方 App 直接执行“任意 prompt -> 返回通用 LLM completion”的系统模型接口**。这不是断言华为内部、合作伙伴白名单或未来版本一定不存在，而是当前公开能力边界。

华为已经公开了真正符合“系统管理模型、端侧推理、无需云模型 Key”定义的 `localChatModel`：它来自 `@kit.DataAugmentationKit`，由“本地AI模型管理”下载默认模型，数据在端侧处理，App 调用 `init()` / `chat()`，接口没有 API Key 参数。但是当前官方约束是：

- 仅支持 `PC/2in1`；
- 仅对企业开发者提供申请能力；
- 从 HarmonyOS `6.0.0(20)` 开始；
- 需要联网权限完成模型管理/下载，下载完成后的问答是端侧处理；
- 官方页面当前把默认模型标为 `Qwen25-7B-Instruct`。

因此它是“真系统端侧模型”的证据，却**不能作为本项目面向手机用户的可用方案**。

HarmonyOS 手机上还能看到 Agent Framework Kit、Intents Kit、CANN Kit、MindSpore Lite Kit 等 AI 能力，但它们分别是“拉起已发布的小艺智能体 UI”“系统理解意图并分发到 App”“运行开发者模型的计算引擎”“系统内置推理 runtime”。它们都不能据此改写成“手机 App 免费获得一个系统通用 LLM completion API”。

当前 NGA 参考项目中，只有 LNGA/`nga_harmony` 主线检出生成式 AI：它把请求直接发到 DeepSeek `/responses`，要求用户填写 API Key，并使用 `Authorization: Bearer <用户 Key>`，经济机制是 **BYOK**。其余已审计 NGA 客户端主线未检出生成式 AI 接入，不能拿来证明免费机制。

非 NGA 项目确实有无云 Key 路线。MNN Chat 是一个可复核的 Android 实例：App 让用户下载模型权重，再通过本地 native runtime 推理。这没有逐次云模型账单，但模型不是系统预装，代价转为下载体积、存储、RAM、CPU/NPU、耗电、设备适配和模型许可证。Android AICore/Gemini Nano 则是另一类真正的系统端侧路线，已在 `android-system-genai-api-audit.md` 单独审计；它仍受支持设备、模型下载、前台、配额、语言质量和条款限制。

### 能力与费用责任分类

| 项目/能力 | 实际调用链 | 云模型 Key | 模型由谁提供/管理 | 应如何回答“免费吗” | 能否直接用于当前 Android 手机计划 |
|---|---|---:|---|---|---|
| LNGA 当前主线 | App -> `api.deepseek.com/responses` | 用户必须提供 | DeepSeek 云；用户账户 | BYOK，项目不代付；不是免费系统模型 | 可参考业务形态，云端仍须按本项目安全边界重做 |
| 其他已审计 NGA 客户端主线 | 未检出生成式 AI 调用 | 不适用 | 不适用 | 没有 AI，不能归类为免费调用 | 无可复用 provider |
| HarmonyOS `localChatModel` | App -> Data Augmentation Kit -> 系统管理的本地模型 | 不需要云模型 Key | HarmonyOS“本地AI模型管理”下载/管理 | 端侧、免云模型运维；不能宣传为零成本或所有手机免费 | 当前不能：只支持企业开发者的 PC/2in1 |
| Agent Framework Kit Function 组件 | App UI -> 指定 `agentId` -> 小艺托管智能体对话框 | Function API 无模型 Key 参数 | 先在小艺开放平台创建、关联、发布智能体 | 平台能力，不是返回 completion 的免费模型 API；公开 SDK 文档也未给出无条件免费推理权益 | 不能替代对象绑定的楼层摘要 backend |
| Intents Kit | App 共享/注册意图；HarmonyOS 从小艺入口调用 App | 不适用 | 系统大模型用于理解/分发用户意图 | 系统 AI 分发能力，不是 App 主动调用模型 | 不能替代生成式摘要 provider |
| Data Augmentation RAG（通用接入） | RAG -> 开发者实现 `ChatLLM.streamChat()` -> 自选 LLM | 取决于开发者选择；官方 ModelArts 示例用 Bearer API Key | 开发者自行选择云/自建模型 | RAG 框架本身不赠送推理；示例是云 BYOK | 不能当免费模型；且 RAG 当前只支持 PC/2in1 |
| CANN Kit / CANN LM Engine | App -> CANN runtime -> App 准备的转换后模型/权重 | 本地推理无云 Key | 开发者准备原始模型、量化、转换、部署 | 无云调用费，但模型与设备资源由 App/用户承担 | 理论上的独立本地路线；当前 LM Engine 要求 Kirin X90，不是通用 Android provider |
| MindSpore Lite Kit | App -> 系统内置推理引擎 -> App 提供的 `.ms` 模型 | 本地推理无云 Key | runtime 系统内置，模型由 App 下载/打包/转换 | “引擎内置”不等于“通用大模型内置” | 需另做模型、体积、性能和许可证研究，不进当前 MVP |
| MNN Chat Android | App -> MNN native runtime -> 用户下载的本地模型 | 本地推理无云 Key | App 模型市场/用户下载权重 | 无逐次云账单，但不是系统模型，存在明显设备成本 | 可证明第三条路线可行；不应偷偷并入当前 MVP |
| Android ML Kit GenAI / AICore | App -> ML Kit -> AICore -> Gemini Nano | 不需要云模型 Key | Android 系统服务管理模型 | 官方称无逐次 server cost；不是全 Android 通用、无限或永久免费 | 是当前规划中的 `SystemOnDeviceBackend`，须通过设备/中文/条款门 |
| 盘古 / ModelArts MaaS | App/服务 -> 华为云模型 endpoint | 需要云侧认证凭据 | 华为云 | 云服务账户与计费/配额契约；赠送额度不改变机制 | 只能作为显式 cloud/BYOK provider，不是手机系统模型 |

### 1. `nga_harmony` 当前实现仍是用户自备 Key

本次以 `apap6628114/nga_harmony@ea1edc91231bd4b1c50c13bcffc8d710b98fbe21` 固定当前主线证据：

- `entry/src/main/ets/model/AiConfig.ets:20-38` 固定 `https://api.deepseek.com`，并明确“用户仅需提供 API Key 与模型名称”；
- `entry/src/main/ets/service/ai/ResponsesApiClient.ets:556-579` 直接向 DeepSeek `/responses` 发非流式请求；
- 同文件 `:593-610` 的连接测试会调用真实生成，不是零推理的本地检查；
- 同文件 `:621-636` 固定 `/responses` URL，并构造 `Authorization: Bearer ` + 用户 Key；
- `entry/src/main/module.json5:15-18` 申请 `ohos.permission.INTERNET`；
- 根与 entry 的 `oh-package.json5` 均没有三方模型 SDK 依赖；对该固定树的 `.ets`/`.json5` 全量检索未出现 `DataAugmentationKit`、`localChatModel`、`AgentFrameworkKit` 或 `IntentsKit`。

`oh-package.json5` 没有依赖并不能单独证明没有系统 Kit，因为部分 Harmony Kit 由系统 SDK 提供；真正闭合证据是“全树没有对应 import/调用”加上“存在明确的 DeepSeek HTTP endpoint、用户 Key 字段和 Bearer header”。

结论不因旧版/新版变化而改变：旧快照是六个 OpenAI-compatible 预设 + 用户 Key，当前主线收窄为固定 DeepSeek Responses API + 用户 Key。内置 provider 名称、endpoint 或默认模型只是配置元数据，不是项目提供的模型额度。

### 2. HarmonyOS 真正的系统端侧问答：存在，但当前不在手机开放范围

官方“端侧问答模型”指南给出两种部署方案：自建云端大模型，或采用 Kit 提供的端侧问答模型；端侧方案的官方表述是免除云端大模型运维成本、数据在端侧处理。其接入链如下：

```text
App
  -> import { localChatModel } from '@kit.DataAugmentationKit'
  -> localChatModel.init()
       -> 拉起“本地AI模型管理”并由用户下载默认模型
  -> localChatModel.chat(QuestionInfo, Config, callback)
       -> Answer(content, isFinished)
```

API reference 的可复核契约：

- 模块起始版本：HarmonyOS `6.0.0(20)`；
- 系统能力：`SystemCapability.DataAugmentation.LocalChatModel`；
- `init(): Promise<boolean>`；
- `chat(info, config, callback): Promise<void>`，支持流式/非流式；
- `QuestionInfo.content` 官方建议控制在 4500 字节内；
- 错误码覆盖 timeout、loading failure、request failure、busy、parameter constraint；
- API 没有 endpoint、API Key、AK/SK 或 Bearer credential 参数。

它不是完全“无需网络”：应用要申请 `ohos.permission.INTERNET`，首次模型管理会展示隐私声明并下载模型。准确文案应是“模型准备可能联网；推理数据在设备端处理；无需云模型 API Key”，而不是“永远离线、完全零成本”。

决定性限制来自同一官方指南：当前仅支持 `PC/2in1`，且仅对企业开发者提供申请能力。Data Augmentation Kit 总览也把 RAG 和端侧问答都列为 PC/2in1；Phone 只出现在“智慧化数据检索”范围。检索能力可以返回本地知识，但不等于通用文本生成。

因此，如果未来另做 HarmonyOS PC 企业版，可以把它包装成一个独立、动态 capability-gated backend；现在不能用它支撑“鸿蒙手机自带大模型可供本 App 免费调用”的产品承诺。

### 3. HarmonyOS 手机上其他带“AI/大模型”字样的 Kit 不是同一种接口

#### 3.1 Agent Framework Kit：托管智能体入口，不是 App 内 completion

官方定义是“拉起指定智能体”。应用须先在小艺开放平台上线智能体，Function 组件再根据 `agentId` 打开智能体对话框。开发指南还要求终端登录华为账号且联网；Kit 支持 Phone/Tablet、中国境内。

`FunctionComponent` / `FunctionController` 的公开 API 主要提供：

- `agentId`、错误回调和展示 options；
- `isAgentSupport(...) -> Promise<boolean>`；
- 对话框打开/关闭事件。

公开 Function API 没有“传入 NGA 楼层 DTO、把生成文本作为 App callback 返回”的 completion contract。小艺开放平台确实让开发者从平台模型列表选择智能体基础模型，但智能体还要完成应用关联、发布、隐私政策、内容合规与审核。平台有模型可选不能推导出第三方 App 获得了无条件免费、可编程的系统模型接口。

Agent Framework 还包含 A2A 协议；官方将其定义为智能体之间的任务/消息/产物通信。它仍以已配置智能体为前提，本次没有找到把 A2A 描述为普通 App 可任意调用、无条件免费的大模型 completion entitlement。即使未来评估 A2A，也应作为“平台托管 agent 集成”单独建模，不能伪装成端侧 provider。

#### 3.2 Intents Kit：系统调用 App，而不是 App 调用大模型

Intents Kit 会利用 HarmonyOS 大模型理解用户显性/潜在意图，从小艺对话、搜索或建议把请求分发到 App。官方运行逻辑明确区分：

- 意图共享：App 主动向 HarmonyOS 提供已发生/预测的意图与实体；
- 意图调用：HarmonyOS 从系统入口主动调用 App 已注册的功能。

这条数据流没有“App 请求模型生成一段摘要”。它仅面向企业开发者，还需要能力申请、调试权限、应用市场上架、意图注册和华为审核。它可增强系统入口发现/调用 NGA 功能，但不能实现当前 AI 楼层摘要 backend。

#### 3.3 Data Augmentation RAG：框架不自动附送 LLM

RAG 的通用开发指南要求开发者继承 `ChatLLM` 并自行实现 `streamChat()`；文档明确写“LLM由开发者自行选择”。官方样例直接调用：

```text
https://api.modelarts-maas.com/v2/chat/completions
Authorization: Bearer <模型 API-KEY>
```

因此 RAG 的知识加工、检索和会话编排不能当作免费推理来源。它可以接开发者的自建云模型，也可以在符合资格的 PC/2in1 上结合独立的 `localChatModel`，但 RAG 框架本身不提供手机通用模型额度。

### 4. CANN 与 MindSpore Lite：无云 Key 的本地计算，不是系统送通用模型

#### 4.1 CANN Kit / CANN LM Engine

CANN Kit 是面向 Kirin NPU/CPU 的模型转换与推理运行环境。一般 CANN Kit 适用于带 Kirin NPU 的 Phone、Tablet、PC/2in1、TV，但当前 CANN LM Engine 文档另行要求 `Kirin X90`，并只列出若干支持的 Qwen/DeepSeek/GLM 模型结构。

官方 LM Engine pipeline 明确从“用户原始模型”开始：量化 -> 导出 ONNX -> 转换为 CANN 模型 -> 集成 LLM Engine。固定官方样例 `harmonyos_samples/cannkit_samplecode_lm_engine_cpp@e71ebe9fd808cbe310cef6204f616becf49a5828` 也印证：

- `README.md:27-30` 的输入是用户原始模型、量化权重和量化系数；
- `CANN_LLM/.../llm_demo.cpp:45-53` 要求用 `hdc` 把模型/配置推入 App 沙箱；
- `.../pages/Index.ets:1-2,31-33,73-78` 通过本地 `libentry.so` 调 `loadmodel()` / `modelinfer()`。

这条路线不需要云 API Key，但开发者要取得合法模型权重、转换并交付/下载它，用户设备承担内存、存储、耗电和兼容性成本。CANN 是加速器，不是预装的自由问答模型。

#### 4.2 MindSpore Lite Kit

MindSpore Lite runtime 是 HarmonyOS 系统内置部件，支持 Phone、Tablet、PC/2in1、TV、Wearable；但官方开发流程仍要求：

1. 准备或转换模型为 `.ms`；
2. App 加载 `.ms` 模型文件；
3. 填充输入 tensor；
4. 执行推理并读取输出。

官方 C/C++ 指南的 `OH_AI_ModelBuildFromFile(..., model_path, ...)` 和运行示例 `./demo mobilenetv2.ms` 都证明模型文件由应用侧提供。“推理引擎系统内置”只减少 runtime 集成，不表示每台手机都预装一个第三方 App 可自由 prompt 的 LLM。

### 5. 非 NGA Android 实例：MNN Chat 的“免费”来自本地权重，而非免费 API

固定 `alibaba/MNN@bef71b9756a2c77549eddbe33eb97290e3b16602`：

- `apps/Android/MnnLlmChat/README_CN.md:21-35` 声明完全本地运行；安装后浏览并下载模型；同时警告 LLM 硬件要求高，低配置设备可能很慢、不稳定或无法运行；
- `.../modelmarket/ModelMarketItem.kt:6-19` 为每个模型记录下载源、repo path 和实际文件大小；
- `.../llm/LlmSession.kt:57-115` 从本地配置/模型路径初始化 native runtime；
- 同文件 `:168-184` 把 prompt 交给 `submitNative(...)` 在本地生成。

它证明 App 自管模型可以做到“用户没有云 Key也能使用”，但不证明有免费云 API，也不证明模型由 Android/HarmonyOS 系统提供。对本项目而言，这是潜在第三条 `AppManagedLocalBackend`，应单独评估 APK/下载体积、最低 RAM、量化模型、中文质量、热量/电量、ABI/NPU 覆盖、许可证与更新机制，不应混进已经规划的 AICore + BYOK MVP。

### 6. 华为云盘古 / ModelArts 仍是云服务，不是 HarmonyOS 手机系统模型

盘古和 ModelArts MaaS 的 API 文档属于华为云模型服务。云侧请求使用 API Key 或 AK/SK 等认证，并受云账户、配额和计费合同约束。HarmonyOS RAG 官方示例本身已经直接展示 ModelArts MaaS endpoint 与 Bearer API Key，足以排除“RAG 自动免费提供模型”的误解。

本研究没有统计注册送额度、活动额度或试用期，因为这些不会改变调用链和费用责任：只要请求进入用户/开发者的云服务账户，它就是 cloud/BYOK 或项目托管云，而不是系统端侧模型。

### 7. 对当前任务计划的直接影响

现有计划的双后端分层是正确的，无需因为“鸿蒙手机有大模型”传闻删除 BYOK：

```text
AiScenarioRequest
  -> SystemOnDeviceBackend
       -> Android ML Kit Prompt / AICore / Gemini Nano
  -> ByokCloudBackend
       -> 用户配置的 OpenAI-compatible 云 provider

未来独立评估（不进当前 MVP）：
  -> AppManagedLocalBackend
       -> MNN / llama.cpp / LiteRT 等 runtime + App/用户下载的模型权重
```

规划与 UI 应坚持以下区分：

1. `SystemOnDevice`：写“设备本地、无需云 API Key、可能下载系统模型、受设备/地区/系统配额限制”；不要写“所有手机永久免费”。
2. `Cloud BYOK`：写具体 provider、内容将离开设备、用户账户可能计费；LNGA 属于这一类。
3. `AppManagedLocal`：若未来做，写模型来源、下载体积、存储/RAM/电量和许可证；不要冒充系统模型。
4. `Hosted Agent/Intent`：只在产品确实要接小艺入口或智能体生态时另立集成，不作为当前生成式 backend。
5. system/local 失败不得自动把论坛内容发往 cloud。用户必须看到新执行位置、payload preview 和费用/数据外发提示后再选择。

如果未来另建 HarmonyOS 原生客户端，`localChatModel` 只应在企业开发者资格、PC/2in1 设备、能力申请和运行时初始化都成立时出现；手机端保持不可用，而不是回退到 Agent Framework 后声称等价。

## Files Found

### 本地任务与参考源码

- `.trellis/tasks/07-25-nga-android-advanced/prd.md` — 已把 Android 系统端侧与 cloud BYOK 分成两条路径，并禁止赠送额度叙事和静默上云（关键行 `5`, `22-24`, `30-36`, `44-60`）。
- `.trellis/tasks/07-25-nga-android-advanced/design.md` — provider-neutral `AiExecutionBackend`、route resolver、对象冻结和两种 backend 设计（关键行 `52-85`）。
- `.trellis/tasks/07-25-nga-android-advanced/implement.md` — AICore facade、BYOK client 与无静默 cloud fallback 的执行顺序（关键行 `16-28`, `59-65`, `74-75`）。
- `.trellis/tasks/07-25-nga-android-advanced/research/ai-call-mechanism-comparison.md` — 当前 NGA 项目、Android AICore 与 Apple Foundation Models 的费用责任对照。
- `.trellis/tasks/07-25-nga-android-advanced/research/android-system-genai-api-audit.md` — Android ML Kit GenAI/AICore 的 SDK、设备、token、配额、前台、中文与条款审计。
- `.trellis/tasks/07-25-nga-android-advanced/research/nga-harmony-ai-source-audit.md` — LNGA 旧快照的 OpenAI-compatible/BYOK、安全与可移植性深审。
- `references/nga-clients/nga_harmony/entry/src/main/module.json5` — 本地旧快照申请 INTERNET 权限，没有系统模型能力声明。
- `references/nga-clients/nga_harmony/entry/src/main/ets/service/ai/ActiveAiService.ets` — 本地旧快照要求 endpoint/model/API key 完整后才调用云客户端（关键行 `77-106`）。
- `references/nga-clients/nga_harmony/entry/src/main/ets/service/ai/OpenAiCompatibleClient.ets` — 本地旧快照向 provider endpoint 发 HTTP，并构造 Bearer header（关键行 `443-476`, `852-871`）。
- `references/nga-clients/nga_harmony/entry/src/main/ets/service/ai/AiModelPresets.ets` — 本地旧快照的预设只是 endpoint/default model/API Key placeholder 元数据（关键行 `55-63`）。

### 外部固定源码

- `apap6628114/nga_harmony@ea1edc91231bd4b1c50c13bcffc8d710b98fbe21` — 当前 LNGA DeepSeek Responses API + 用户 Key 机制。
- `harmonyos_samples/cannkit_samplecode_lm_engine_cpp@e71ebe9fd808cbe310cef6204f616becf49a5828` — CANN LM Engine 要求开发者准备模型并从 App 本地 native 层加载/推理。
- `alibaba/MNN@bef71b9756a2c77549eddbe33eb97290e3b16602` — MNN Chat 下载模型、初始化本地 runtime 并本地生成的 Android 实例。

## Code Patterns

### LNGA current main（固定提交）

- [`AiConfig.ets:20-38`](https://github.com/apap6628114/nga_harmony/blob/ea1edc91231bd4b1c50c13bcffc8d710b98fbe21/entry/src/main/ets/model/AiConfig.ets#L20-L38) — 固定 DeepSeek endpoint，并要求用户 API Key/模型名。
- [`ResponsesApiClient.ets:556-579`](https://github.com/apap6628114/nga_harmony/blob/ea1edc91231bd4b1c50c13bcffc8d710b98fbe21/entry/src/main/ets/service/ai/ResponsesApiClient.ets#L556-L579) — 真实 HTTP POST 生成请求。
- [`ResponsesApiClient.ets:593-610`](https://github.com/apap6628114/nga_harmony/blob/ea1edc91231bd4b1c50c13bcffc8d710b98fbe21/entry/src/main/ets/service/ai/ResponsesApiClient.ets#L593-L610) — 连接测试也执行一次生成。
- [`ResponsesApiClient.ets:621-636`](https://github.com/apap6628114/nga_harmony/blob/ea1edc91231bd4b1c50c13bcffc8d710b98fbe21/entry/src/main/ets/service/ai/ResponsesApiClient.ets#L621-L636) — `/responses` 与 Bearer Key。
- [`module.json5:15-27`](https://github.com/apap6628114/nga_harmony/blob/ea1edc91231bd4b1c50c13bcffc8d710b98fbe21/entry/src/main/module.json5#L15-L27) — INTERNET 等权限；固定树没有 system LLM Kit import。

### CANN LM Engine sample（固定提交）

- [`README.md:27-30`](https://gitee.com/harmonyos_samples/cannkit_samplecode_lm_engine_cpp/blob/e71ebe9fd808cbe310cef6204f616becf49a5828/README.md#L27-L30) — pipeline 输入为用户原始模型及量化权重。
- `CANN_LLM/CANN_LLM_Engine_Demo/CANNLLMEngineDemoNext/entry/src/main/cpp/llm_demo.cpp:45-53` — 通过 `hdc` 放入模型/配置并读取 App 沙箱路径。
- `CANN_LLM/CANN_LLM_Engine_Demo/CANNLLMEngineDemoNext/entry/src/main/ets/pages/Index.ets:1-2,31-33,73-78` — ArkTS 调本地 native library 的 `loadmodel()` / `modelinfer()`。

### MNN Chat Android（固定提交）

- [`README_CN.md:21-35`](https://github.com/alibaba/MNN/blob/bef71b9756a2c77549eddbe33eb97290e3b16602/apps/Android/MnnLlmChat/README_CN.md#L21-L35) — 本地运行、安装后下载模型和低配设备风险。
- [`ModelMarketItem.kt:6-19`](https://github.com/alibaba/MNN/blob/bef71b9756a2c77549eddbe33eb97290e3b16602/apps/Android/MnnLlmChat/app/src/main/java/com/alibaba/mnnllm/android/modelmarket/ModelMarketItem.kt#L6-L19) — 模型下载源、repo path 与文件大小。
- [`LlmSession.kt:57-115`](https://github.com/alibaba/MNN/blob/bef71b9756a2c77549eddbe33eb97290e3b16602/apps/Android/MnnLlmChat/app/src/main/java/com/alibaba/mnnllm/android/llm/LlmSession.kt#L57-L115) — 本地配置/权重初始化 native runtime。
- [`LlmSession.kt:168-184`](https://github.com/alibaba/MNN/blob/bef71b9756a2c77549eddbe33eb97290e3b16602/apps/Android/MnnLlmChat/app/src/main/java/com/alibaba/mnnllm/android/llm/LlmSession.kt#L168-L184) — prompt 进入本地 `submitNative()`。

## External References

以下资料访问日期均为 2026-09-05；HarmonyOS 文档页面为动态文档，版本号优先采用正文明确的系统版本。端侧问答指南与 API 页面 UI 本次标注更新于 2026-09-03。

### HarmonyOS 系统能力

1. HarmonyOS, **端侧问答模型**：<https://developer.huawei.com/consumer/cn/doc/harmonyos-guides/dataaugmentation-localchatmodel> — 本地处理、模型管理下载、默认模型、PC/2in1 与企业开发者限制。
2. HarmonyOS API Reference, **localChatModel（端侧问答模型）**：<https://developer.huawei.com/consumer/cn/doc/harmonyos-references/dataaugmentation-localchatmodel-api> — `6.0.0(20)`、system capability、`init/chat`、4500 字节建议与错误码。
3. HarmonyOS, **Data Augmentation Kit简介**：<https://developer.huawei.com/consumer/cn/doc/harmonyos-guides/dataaugmentation-introduction> — RAG/检索/端侧模型的设备矩阵；中国境内范围。
4. HarmonyOS, **知识问答（RAG）**：<https://developer.huawei.com/consumer/cn/doc/harmonyos-guides/data-augmentation-rag-development> — 开发者实现 `ChatLLM.streamChat()`；ModelArts MaaS endpoint 与 Bearer API Key 示例。
5. HarmonyOS, **Agent Framework Kit简介**：<https://developer.huawei.com/consumer/cn/doc/harmonyos-guides/hmaf-introduction> — Function/A2A 的定位、Phone/Tablet 与地区边界。
6. HarmonyOS, **通过Function组件拉起智能体**：<https://developer.huawei.com/consumer/cn/doc/harmonyos-guides/hmaf-function> — 先开发/关联智能体，终端需登录华为账号并联网。
7. HarmonyOS API Reference, **FunctionComponent**：<https://developer.huawei.com/consumer/cn/doc/harmonyos-references/hmaf-function-component> — `agentId`、support check、dialog open/close events；起始版本 `6.0.0(20)`。
8. 小艺开放平台, **基础能力**：<https://developer.huawei.com/consumer/cn/doc/service/base-ability-0000002675010323> — 智能体从平台模型列表选择基础模型并配置参数。
9. 小艺开放平台, **开发界面介绍**：<https://developer.huawei.com/consumer/cn/doc/service/development-guide-0000002670266897> — 发布、隐私、内容合规、白名单与运营配置。
10. HarmonyOS, **Intents Kit简介**：<https://developer.huawei.com/consumer/cn/doc/harmonyos-guides/intents-introduction> — 系统分发、意图共享/调用方向、企业开发者限制。
11. HarmonyOS, **Intents Kit接入流程**：<https://developer.huawei.com/consumer/cn/doc/harmonyos-guides/intents-access-flow> — 能力申请、调试权限、上架与审核。
12. HarmonyOS, **CANN Kit简介**：<https://developer.huawei.com/consumer/cn/doc/harmonyos-guides/cannkit-introduction> — Kirin NPU/CPU 推理 runtime、模型转换与设备边界。
13. HarmonyOS, **CANN LM Engine简介**：<https://developer.huawei.com/consumer/cn/doc/harmonyos-guides/cannkit-llm-summary> — 支持模型结构、Kirin X90 与用户模型 pipeline。
14. HarmonyOS, **CANN Kit模型推理**：<https://developer.huawei.com/consumer/cn/doc/harmonyos-guides/cannkit-model-inference> — 准备离线模型、编译与执行。
15. HarmonyOS, **MindSpore Lite Kit简介**：<https://developer.huawei.com/consumer/cn/doc/harmonyos-guides/mindspore-lite-kit-introduction> — 系统内置 runtime、设备类型、`.ms` 模型转换/加载。
16. HarmonyOS, **使用MindSpore Lite进行模型推理**：<https://developer.huawei.com/consumer/cn/doc/harmonyos-guides/mindspore-lite-guidelines> — 模型文件准备、`BuildFromFile` 与本地推理流程。

### 华为云与固定项目源码

17. 华为云, **ModelArts Studio大模型即服务平台快速入门**：<https://support.huaweicloud.com/qs-maas/qs-maas-0001.html> — 云端 MaaS 使用流程。
18. 华为云, **盘古大模型 API认证**：<https://support.huaweicloud.com/api-pangulm/pangulm_05_0011.html> — 云 API 认证边界。
19. 华为云, **获取ModelArts Studio模型调用API Key**：<https://support.huaweicloud.com/usermanual-maas-modelarts/maas-modelarts-0073.html> — 云侧 API Key 管理。
20. LNGA current fixed source：<https://github.com/apap6628114/nga_harmony/tree/ea1edc91231bd4b1c50c13bcffc8d710b98fbe21>。
21. CANN LM Engine official sample：<https://gitee.com/harmonyos_samples/cannkit_samplecode_lm_engine_cpp/tree/e71ebe9fd808cbe310cef6204f616becf49a5828>。
22. MNN Chat Android fixed source：<https://github.com/alibaba/MNN/tree/bef71b9756a2c77549eddbe33eb97290e3b16602/apps/Android/MnnLlmChat>。

### 已完成的系统模型对照

23. Android Developers, **Gemini Nano / AICore**：<https://developer.android.com/ai/gemini-nano>。
24. ML Kit, **Overview of ML Kit GenAI APIs**：<https://developers.google.com/ml-kit/genai> — 设备端处理、无逐次 server cost、设备/配额边界。
25. Apple Developer, **Foundation Models**：<https://developer.apple.com/documentation/foundationmodels> — Apple 平台同类系统端侧模型；不是 Android/HarmonyOS 可移植 API。

## Related Specs

- `.trellis/tasks/07-25-nga-android-advanced/prd.md:22-24,30-36,44-60` — 两条执行路径、真实费用/数据位置披露、无静默上云和 App 自管模型暂不进 MVP。
- `.trellis/tasks/07-25-nga-android-advanced/design.md:52-85` — provider-neutral capability、`SystemOnDevice | Cloud(providerId)`、AICore 与 BYOK 边界。
- `.trellis/tasks/07-25-nga-android-advanced/implement.md:16-28,59-65,74-75` — 先建统一 domain，再分别实现系统和 cloud backend；失败不跨边界。
- `.trellis/spec/frontend/android-migration-architecture.md:17-67` — route/screen/ViewModel/repository 分层、不可变状态与 lifecycle ownership。
- `.trellis/spec/backend/network-foundation-contract.md:242-259` — secret/content 禁止日志、敏感 header 只发 exact HTTPS host、外部边界默认无 NGA Cookie。
- `.trellis/spec/guides/cross-layer-thinking-guide.md:19-51,93-101` — 明确端到端数据流、边界输入/输出/错误及单一 payload contract。
- `.trellis/tasks/07-25-nga-android-advanced/research/ai-call-mechanism-comparison.md` — NGA/Android/Apple 的调用机制与费用责任总表。
- `.trellis/tasks/07-25-nga-android-advanced/research/android-system-genai-api-audit.md` — 当前 Android 系统 provider 的详细发布门。

## Caveats / Not Found

- 本结论限定为截至 2026-09-05 可检索到的公开官方 SDK/API；未断言华为内部能力、定向合作、灰度白名单或未来手机 API 不存在。
- 未找到 HarmonyOS 手机普通第三方 App 的公开“任意 prompt -> completion”系统 LLM API。最接近的 `localChatModel` 官方明确限定 PC/2in1 + 企业开发者。
- `localChatModel` 指南当前将默认模型拼作 `Qwen25-7B-Instruct`，CANN 文档则写 `Qwen2.5-7B-Instruct`。本报告保留各页面原文，不据此推断二者模型包、量化或版本完全相同。
- `localChatModel` API 没有云 credential，官方称端侧处理并免除云模型运维成本；公开页面没有给出可被概括为“永久免费、无限次、对所有企业账户零费用”的价格合同，因此产品文案仍不应使用笼统“免费”。
- Agent Framework Function API 不返回 completion；本次没有把小艺平台提供模型列表推导为免费推理权益。若以后产品真要接托管智能体/A2A，应重新核验当时的商业条款、调用配额、内容流向和 App 能取得的输出 contract。
- 华为云 support 页面在本次命令行刷新时触发 EdgeOne Security Verification；云认证结论同时由其 canonical 文档标题/既有审计和 HarmonyOS RAG 官方样例中的 ModelArts endpoint + Bearer API Key 交叉支持。未把任何促销额度纳入结论。
- 未使用真实华为开发者企业账号申请 `localChatModel`，未在 PC/2in1 或手机上运行任何 Harmony Kit，未执行 LNGA、MNN、ModelArts、盘古或其他模型推理，也未测试真实计费。
- MNN/CANN/MindSpore 只能证明本地 runtime/模型交付机制；没有完成适合 NGA 中文摘要的具体权重、许可证、RAM、速度、电量、热量和低端机覆盖评估。
- HarmonyOS Kit 不能直接移植到当前 Android 工程。当前 Android 系统端侧候选仍是 ML Kit GenAI/AICore；其限制以 `android-system-genai-api-audit.md` 为准。
- 外部仓库结论固定在列出的 commit；主线后续可能变化。LNGA 本地 reference 仍是旧多 provider 快照，当前机制判断采用固定远端提交而非 README 宣传文字。
