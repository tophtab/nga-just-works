# Research: nga_harmony AI 源码审计与 Android 可行性

- Query: 审计 `apap6628114/nga_harmony` 的 AI 功能真实实现，判断根目录 Justwen Android 工程的可移植范围、风险、前置条件和分阶段落地方案。
- Scope: mixed (固定源码、当前 Android 工程、外部协议/平台文档；未调用真实 NGA 或 AI/Search 服务)
- Date: 2026-07-27
- Audited source: `references/nga-clients/nga_harmony` local HEAD `8558a15e5a04c12bf6207265ac33493691aa605e`
- Upstream delta checked: `origin/main=f28dd6024fa3bf39c5a6b84519187f307acbf148`，固定提交之后 6 个提交；AI 客户端文件 SHA-256 与固定提交相同，后续差异集中在通知、排版、链接和 TTS，不改变本报告的 AI 结论。

> **时效说明（2026-09-05）：** 本文主体是旧快照的深度审计，不能再当作当前 LNGA 主线的 provider 清单。最新核验固定在 `origin/main=ea1edc91231bd4b1c50c13bcffc8d710b98fbe21`：上游已删除 `OpenAiCompatibleClient.ets`、多 provider 与 Tavily adapter，改为固定 `https://api.deepseek.com/responses` 的 DeepSeek Responses API；但仍要求用户填写 API Key，并以 `Authorization: Bearer <用户 Key>` 调用，所以经济机制仍是 BYOK，而不是鸿蒙系统模型或项目免费额度。当前对照与其他项目证据见 `research/ai-call-mechanism-comparison.md`。

## Findings

### 执行结论

**结论：技术可行，产品与安全前置工作量大，不能直接搬 ArkTS 源码。** `nga_harmony` 已经真实实现了 BYOK 配置、OpenAI-compatible Chat Completions、模型列表、付费式连接测试、非流式/流式聊天、自写 Markdown、单楼层摘要、用户公开活动采样和 Tavily 工具调用；它不是仅有 README 或空 UI。但其“兼容”范围较窄，取消只停 UI 回调而未取消网络，搜索提供商多数是占位，密钥明文进入普通 Preferences 和系统备份，日志会暴露提示词/响应片段，任意 endpoint 缺少 HTTPS/redirect policy，并且没有 AI 专项测试。

根 Android 工程已有 Kotlin/Java、Compose、Lifecycle、Retrofit/RxJava、Room 和 Android 10-15 基线，能够承载独立 AI 模块；但现有 NGA `RetrofitHelper` 自动注入 Cookie、GBK 解码并记录响应，**绝对不能复用于第三方 AI**。建议原创实现一个无 NGA Cookie、UTF-8、HTTPS-only、可取消、默认不记正文的专用 OkHttp client，并先落地 Keystore BYOK + provider/category consent + 可取消流式聊天 + 单楼层摘要。Web Search 与用户画像后置；原“政治光谱 + 辛辣锐评”不建议进入首发。

| 能力/声明 | 源码事实 | Android 判断 |
|---|---|---|
| 多服务商 BYOK | 6 个 AI 预设 + custom，统一按 Bearer/OpenAI shape 发送；无厂商专用 adapter | 概念可复用，能力需逐 provider 验证；首版只承诺 custom/OpenAI-compatible |
| 模型列表/连接测试 | `GET /models` 只读 `data[].id`；测试会真实发一次 completion，默认最多 2000 tokens | 可做，但测试必须明确可能计费并严格限制输出；错误/response shape 要 typed |
| 非流式/流式聊天 | 两条路径均有；SSE 支持跨字节 UTF-8 和跨 chunk buffer | 可实现；须补标准 SSE 多行字段、限额、真正取消和 lifecycle tests |
| “支持中断” | 页面离开只丢弃回调；service 不暴露 cancel，UI 无停止按钮 | README 声明高于实现，不可照抄验收结论 |
| 多轮/历史 | 仅页面内存气泡，每轮重发全部已完成消息；离页即丢失，无长度预算 | 可做内存 MVP；持久化、账号隔离和清理策略另定 |
| 帖子总结 | 实际只发送当前一个楼层：标题、楼层、作者、纯正文 | 可行且进入 Android MVP；入口为被点击楼层右下角三点菜单“AI 总结”，展示发送预览/同意 |
| 用户分析 | 抓目标 UID 第一页主题/回帖，构造版面/时段/标题/回复片段后发第三方 | 技术可行且经产品决定进入 Android MVP；入口绑定所浏览资料页的目标 UID，保留隐私/公平护栏并移除政治倾向和攻击性画像 |
| 联网搜索/tool calling | Tool Registry 和 Tavily 完整；SerpAPI、Brave、custom 只有设置/预设，没有 adapter | Tavily 概念可做，其他是 stub；首版不做工具调用 |
| Markdown | 自写 parser/renderer，含标题、列表、引用、代码、基础 inline；AI 页面没传链接回调 | Android 应用成熟库或受测 parser；不要逐行翻译 ArkTS 实现 |
| Secret/privacy | 密钥随用户 settings 明文 JSON 持久化且允许系统备份；无 provider/category consent | 阻断式差距；Android MVP 前必须先做 Keystore vault、backup exclusion、consent |
| 测试 | 只有模板示例测试，没有 AI/SSE/Markdown/tool/provider 测试 | 不足以作为可移植质量证据；必须用 fake server/fixtures 重建测试 |

### 1. 文件与职责

| 文件 | 一句话说明 |
|---|---|
| `references/nga-clients/nga_harmony/entry/src/main/ets/model/AiConfig.ets` | provider、API key、streaming、temperature、maxTokens 数据模型 |
| `references/nga-clients/nga_harmony/entry/src/main/ets/model/AiScenarioConfig.ets` | 两个场景、默认 system prompt 和“会注入的数据”说明 |
| `references/nga-clients/nga_harmony/entry/src/main/ets/model/WebSearchConfig.ets` | 搜索配置及 Tavily/SerpAPI/Brave/custom UI 预设 |
| `references/nga-clients/nga_harmony/entry/src/main/ets/service/ai/AiModelPresets.ets` | 六家 AI endpoint/default-model metadata |
| `references/nga-clients/nga_harmony/entry/src/main/ets/service/ai/OpenAiCompatibleClient.ets` | JSON/SSE、tool call、模型列表、连接测试的核心 HTTP 实现 |
| `references/nga-clients/nga_harmony/entry/src/main/ets/service/ai/ActiveAiService.ets` | active provider 校验、工具三阶段编排、fallback 和错误展示 |
| `references/nga-clients/nga_harmony/entry/src/main/ets/service/ai/WebSearchService.ets` | 搜索 adapter 选择和结果转模型上下文 |
| `references/nga-clients/nga_harmony/entry/src/main/ets/service/ai/search/TavilySearchAdapter.ets` | 唯一实际可工作的 Search provider adapter |
| `references/nga-clients/nga_harmony/entry/src/main/ets/service/ai/tools/*` | tool definition/registry/dispatch 与 `web_search` 执行 |
| `references/nga-clients/nga_harmony/entry/src/main/ets/pages/ai/AiSettingsPanel.ets` | provider/search/scenario 设置 UI 和异步请求失效保护 |
| `references/nga-clients/nga_harmony/entry/src/main/ets/pages/ai/AiChatPage.ets` | 页面内聊天状态、流式节流、history 和 Markdown 气泡 |
| `references/nga-clients/nga_harmony/entry/src/main/ets/pages/ThreadPanel.ets` | 当前楼层摘要入口和 prompt 构造 |
| `references/nga-clients/nga_harmony/entry/src/main/ets/common/components/ProfileCardPopup.ets` | “查成分”入口、用户采样数据转 prompt |
| `references/nga-clients/nga_harmony/entry/src/main/ets/service/api/UserApi.ets` | 以 NGA 登录态抓目标用户第一页主题/回复并清洗片段 |
| `references/nga-clients/nga_harmony/entry/src/main/ets/parser/md/MdParser.ets` | 自写 Markdown AST parser |
| `references/nga-clients/nga_harmony/entry/src/main/ets/common/components/MdStreamingContentView.ets` | 稳定行增量 AST + 不稳定尾段的流式渲染 |
| `references/nga-clients/nga_harmony/entry/src/main/ets/store/settings/domain/AiSettings.ets` | provider CRUD、active selection、场景 prompt 持久化 |
| `references/nga-clients/nga_harmony/entry/src/main/ets/store/SettingsStore.ets` | 按 NGA UID 将整个 SettingsState 序列化进 Preferences |
| `references/nga-clients/nga_harmony/entry/src/main/resources/base/profile/backup_config.json` | 明确允许系统 backup/restore |
| `settings.gradle` | 根 Android 工程 13 个 app/library 模块清单 |
| `build.gradle` | Android/Kotlin/Compose/Retrofit/RxJava/Room 版本与 API 30/35 基线 |
| `lib_base_network/.../RetrofitHelper.java` | 现有 NGA Cookie 注入和 request logging 网络栈，AI 必须隔离 |
| `lib_base_network/.../JsonStringConvertFactory.java` | 现有全局 GBK + raw response logging converter，AI 不可复用 |
| `lib_base_ui_compose/build.gradle` | 可承载 Compose AI UI，但当前未声明 Markdown/AI/SSE 专用依赖 |

### 2. 配置、预设与持久化

- `AiProviderProfile` 保存 `endpoint/apiKey/modelName/streaming/temperature/maxTokens`，API Key 只是普通 string；默认温度 0.7、输出 2000 tokens，范围 0-1.5/400-20000（`references/nga-clients/nga_harmony/entry/src/main/ets/model/AiConfig.ets:8`, `:14`, `:25`, `:30`, `:33`, `:36`）。
- provider CRUD 在内存列表中替换后立刻 `persist()`；删除 active profile 会选择剩余第一项（`references/nga-clients/nga_harmony/entry/src/main/ets/store/settings/domain/AiSettings.ets:44`, `:56`, `:77`, `:86`）。ID 使用时间戳 + `Math.random()`，不是安全标识；虽非凭证，但不应作为安全边界（同文件 `:15`）。
- 六个预设为 DeepSeek、智谱、豆包、MiniMax、Kimi、OpenAI，再加 custom；它们只是 endpoint、默认模型、`supportsModelList` 和占位符 metadata，没有 provider-specific wire adapter（`references/nga-clients/nga_harmony/entry/src/main/ets/service/ai/AiModelPresets.ets:55`）。默认模型字符串属于会变化的运营 metadata，不能作为长期可用性承诺。
- 设置 UI 支持新增/更新/删除/激活、密码型输入、stream toggle、temperature 和 max tokens（`references/nga-clients/nga_harmony/entry/src/main/ets/pages/ai/AiSettingsPanel.ets:361`, `:411`, `:1670`, `:1785`, `:1967`）。密码输入仅遮罩显示，不改变底层明文存储。
- endpoint 规格化只用于匹配预设：trim、去 `/chat/completions`、去尾斜杠、转小写（同文件 `:475`）；请求端的 `buildChatUrl` 只去尾斜杠再追加 `/chat/completions`，没有 URI parser、HTTPS-only、host/port/userinfo、DNS/IP 或 redirect 校验（`references/nga-clients/nga_harmony/entry/src/main/ets/service/ai/OpenAiCompatibleClient.ets:852`）。因此 custom 可指向 HTTP、局域网或任意 host，存在密钥泄露/SSRF-like client risk。
- `SettingsState.aiProfiles` 与 `webSearchProfiles` 都包含明文 API Key（`references/nga-clients/nga_harmony/entry/src/main/ets/store/settings/SettingsState.ets:44`, `:50`）；`SettingsStore` 以 `settings_<nga uid>` 将整个对象 JSON 化写进普通 Preferences（`references/nga-clients/nga_harmony/entry/src/main/ets/store/SettingsStore.ets:94`, `:98`; `references/nga-clients/nga_harmony/entry/src/main/ets/common/infra/PreferencesStore.ets:55`）。
- logout 只换成新内存 `SettingsState`，没有删除旧 `settings_<uid>`；再次登录同 UID 会重新加载它（`references/nga-clients/nga_harmony/entry/src/main/ets/store/SettingsStore.ets:80`, `:106`; `references/nga-clients/nga_harmony/entry/src/main/ets/store/AppStore.ets:102`）。配置删除会从随后写回的 JSON 中消失，但没有 secure deletion contract。
- backup profile 明确 `allowToBackupRestore: true`，Backup ability 没有排除/加密 AI secrets（`references/nga-clients/nga_harmony/entry/src/main/resources/base/profile/backup_config.json:2`; `references/nga-clients/nga_harmony/entry/src/main/ets/entrybackupability/EntryBackupAbility.ets:6`）。这与 Android 任务要求的 KeyVault/backup exclusion 相反。

### 3. 请求 shape、模型列表与连接测试

- 所有 provider 共用 `Content-Type: application/json`、`Accept: text/event-stream` 和 `Authorization: Bearer <key>`，包括非流式和模型列表（`references/nga-clients/nga_harmony/entry/src/main/ets/service/ai/OpenAiCompatibleClient.ets:866`）。它不支持 Azure-style query key、组织/项目 header、厂商签名或 per-provider output-field 差异，所以“OpenAI-compatible”只是一个固定子集。
- request body 固定为 `model/stream/temperature/max_tokens/messages`，可选 `tools`；assistant tool_calls 会写 `content:null`，tool message 写 `tool_call_id/name`（同文件 `:883`, `:890`, `:895`, `:902`, `:918`, `:932`）。没有 context length/token budget、message count/size limit、usage、seed、response_format 或 `max_completion_tokens` capability negotiation。
- non-stream `chatComplete` 发 POST，60s read/15s connect timeout；非 200 会把 response body 前 300 字符写公开 warn log，然后仅抛数字状态码，200 只读取 `choices[0].message.content`（同文件 `:443`, `:452`, `:464`, `:1029`）。结构化 provider error/message/usage 全丢失。
- `listModels` 把 endpoint 尾部 `/chat/completions` 去掉后请求 `/models`，只接受 HTTP 200 和 `data[].id`，错误变空列表（同文件 `:816`, `:835`, `:1186`）。UI 因而无法区分“不支持”“无模型”“认证失败”“解析失败”。
- `testConnection` 不是 HEAD/health check，而是发送“请用一句简短的中文确认连接成功。”的真实非流式 completion；body 沿用默认 temperature/maxTokens，超时 15s/10s（同文件 `:766`, `:768`, `:771`, `:778`）。这可能计费；设置页没有费用/数据发送确认，只显示耗时和回复（`references/nga-clients/nga_harmony/entry/src/main/ets/pages/ai/AiSettingsPanel.ets:655`, `:691`）。
- 设置页用 request ID + profile/endpoint/key/model snapshot 丢弃过期 model/test UI 结果，是合理模式，但并未取消底层请求（同文件 `:592`, `:634`, `:672`, `:721`）。

### 4. 流式解析、取消与错误处理

- `ChatStreamParser` 维护 pending text，先把 CRLF/CR 归一化，再按空行分 SSE event；每个 `data:` 行独立 JSON.parse，支持 `[DONE]`、`choices[0].delta.content`、部分实现的 `delta.message.content`、finish_reason 及按 index 拼接 tool_calls（`references/nga-clients/nga_harmony/entry/src/main/ets/service/ai/OpenAiCompatibleClient.ets:114`, `:132`, `:222`, `:238`, `:275`, `:390`）。UTF-8 使用 streaming decoder，能处理字节边界（同文件 `:667`, `:699`）。
- 正常 stream 和工具决策 stream 都注册 `dataReceive/dataEnd`，settled guard 后解绑 listener 并 `destroy()`；非 200/request error/parse error 会 reject（同文件 `:492`, `:504`, `:508`, `:532`, `:546`, `:585`; 普通流 `:660`, `:671`, `:675`, `:699`, `:713`, `:738`）。资源清理路径存在，是可借鉴点。
- 不是完整 SSE 语义：标准允许一个 event 的多个 `data:` field 以换行拼接，本实现逐行当独立 JSON；`line.trim()` 也主动改变 payload 空白。JSON parse failure只记 warn 并返回空 delta，可能最终表现为“空/格式不兼容”而不是精确 protocol error（同文件 `:238`, `:245`, `:993`；外部 SSE 规范见下）。
- **没有真正取消。** `AiChatPage.aboutToDisappear()` 仅令 requestId 失效和停止页面更新，没有持有或调用 HTTP cancel；service 函数只返回最终 Promise/string，没有 cancellation handle（`references/nga-clients/nga_harmony/entry/src/main/ets/pages/ai/AiChatPage.ets:130`, `:245`; `references/nga-clients/nga_harmony/entry/src/main/ets/service/ai/OpenAiCompatibleClient.ets:660`）。输入栏只有“发送”，发送中禁用，没有停止按钮（`references/nga-clients/nga_harmony/entry/src/main/ets/pages/ai/AiChatPage.ets:597`）。README 的“支持多轮对话与中断”在 `README.md:78` 中，因此“中断”属于未兑现声明。
- Tool path 存在额外语义缺陷：最终回答总是先走 stream，即使 profile 的 streaming toggle 为 false，失败才退 non-stream（`references/nga-clients/nga_harmony/entry/src/main/ets/service/ai/ActiveAiService.ets:373`）。400/404/422/501 或 error text 含 tool/function 就把 endpoint+model 在当前进程标成不支持 tools，可能把临时认证/路径错误误判为能力缺失（同文件 `:496`, `:516`）。
- 错误翻译覆盖 400/401/403/404/429/5xx/负数（`references/nga-clients/nga_harmony/entry/src/main/ets/service/ai/AiErrorTranslator.ets:52`），但实际网络异常常直接以任意 message 抛出，`formatAiCompletionError` 仅对纯数字 message 使用 translator（`references/nga-clients/nga_harmony/entry/src/main/ets/service/ai/ActiveAiService.ets:545`），所以负数网络分类并未稳定接上。

### 5. UI 状态、聊天历史与 Markdown

- `AiChatPage` 保持 `bubbles/isSending/streamingText/tool phase` 等页面状态；路由带 `initialPrompt` 时页面出现即自动发送，场景 prompt 作为 system message 注入（`references/nga-clients/nga_harmony/entry/src/main/ets/pages/ai/AiChatPage.ets:61`, `:108`, `:120`, `:125`, `:152`, `:375`）。这意味着用户在点击场景入口后没有发送预览/编辑/逐 provider consent 阶段。
- 每轮 history 从内存 bubbles 重建并重发全部 user/assistant 文本；不含未完成气泡，也不保存 tool call transcript；无持久化、分页、token/window pruning 或本地“清空会话”行为（同文件 `:164`, `:375`）。离页后状态销毁。源码没有除帖子/用户场景外的自由聊天路由入口，故 README 的“通用 AI 对话”是组件能力但未完整接入产品导航（入口搜索只命中 `ThreadPanel.ets:666` 和 `ProfileCardPopup.ets:77`）。
- UI 用非响应式 `streamBuffer` 累积 chunk，再自适应定时更新 `streamingText`，避免每 token 重建；request ID 和 `pageAlive` 阻止过期回调更新 UI（同文件 `:90`, `:100`, `:256`, `:265`, `:292`）。这是合理的 Android Compose state/recomposition 参考思路。
- 自写 Markdown AST 支持 heading、paragraph、code block、unordered/ordered list、blockquote、thematic break，以及 bold/italic/strike/inline-code/link（`references/nga-clients/nga_harmony/entry/src/main/ets/model/MdNode.ets:7`; `references/nga-clients/nga_harmony/entry/src/main/ets/parser/md/MdParser.ets:30`; `references/nga-clients/nga_harmony/entry/src/main/ets/common/components/MdNodesView.ets:68`, `:263`）。没有 table、image、raw HTML、安全 URL policy或完整 CommonMark 兼容性。
- 流式视图把已换行前缀增量 parse 成 AST，未完成代码块留在 tail；源码明确承认流式期间多行段落会拆成多个段落，结束后再全量纠正（`references/nga-clients/nga_harmony/entry/src/main/ets/common/components/MdStreamingContentView.ets:24`, `:333`）。
- AI 页面实例化 `MdStreamingContentView`/`MdContentView` 时没传 `onLinkClick`，默认 callback 是 no-op；搜索引用虽然显示为 Markdown link，但不可点击（`references/nga-clients/nga_harmony/entry/src/main/ets/pages/ai/AiChatPage.ets:487`, `:578`; `references/nga-clients/nga_harmony/entry/src/main/ets/common/components/MdContentView.ets:15`）。

### 6. 场景 prompt 与数据构造

#### 单楼层摘要（已实现，但名称易误导）

- 场景默认 system prompt 要求核心观点、关键信息、逻辑脉络、一句话总结（`references/nga-clients/nga_harmony/entry/src/main/ets/model/AiScenarioConfig.ets:50`）。设置页可即时编辑并持久化，也展示“标题/楼层/作者/正文”注入字段（`references/nga-clients/nga_harmony/entry/src/main/ets/pages/ai/AiSettingsPanel.ets:1528`, `:1588`）。
- 实际入口位于每个 `PostItem` 的 summarize callback；只把当前 `PostInfo` 的 BBCode AST 转纯文本，再构造标题、楼层、作者和正文，不遍历整帖分页（`references/nga-clients/nga_harmony/entry/src/main/ets/pages/ThreadPanel.ets:648`, `:654`, `:659`）。`bbNodesToPlainText` 会忽略图片/视频/音频/表情，保留 quote 子文本（`references/nga-clients/nga_harmony/entry/src/main/ets/common/utils/Utils.ets:240`）。
- 因而“帖子内容总结”实际是“单楼层文本摘要”。正文无输入字符/token 上限，超长楼层可能超过 provider context；作者名和引用内容也会发送。Android UI 应准确命名，展示 exact payload preview，允许删除作者/引用/选中文本，并在 RequestBuilder 端限长。

#### 用户“成分分析”（已实现，但样本与推断风险高）

- 默认 system prompt 要求从论坛数据推断身份/兴趣/角色、**政治光谱**，并作“辛辣尖锐”的一句话锐评（`references/nga-clients/nga_harmony/entry/src/main/ets/model/AiScenarioConfig.ets:60`）。这会生成敏感属性推断和攻击性评价，不能视为普通摘要。
- 点击任意用户卡片的“查成分”后，先用当前登录用户的 NGA session 请求目标 UID 的 `thread.php` 第一页主题和第一页面回复；主题/回复计数只是这两页的采样，却被 prompt 写成“共 N 个”（`references/nga-clients/nga_harmony/entry/src/main/ets/common/components/ProfileCardPopup.ets:26`, `:35`, `:42`; `references/nga-clients/nga_harmony/entry/src/main/ets/service/api/UserApi.ets:200`, `:221`）。
- 采样数据包含主题标题/时间、版面、24 小时时段和每条最多 200 字的回复片段；回复清洗用 regex 去 quote/部分 BBCode/HTML/URL，仍可能保留昵称、联系方式或引用残片（`references/nga-clients/nga_harmony/entry/src/main/ets/service/api/UserApi.ets:209`, `:237`, `:247`）。随后页面自动把用户名、UID、聚合和全部样本发送给 active provider（`references/nga-clients/nga_harmony/entry/src/main/ets/common/components/ProfileCardPopup.ets:41`, `:64`, `:70`, `:76`）。
- 设置页仅提供静态数据类别预览，不记录 provider/category consent，也不在发送时确认目标用户数据会离开 NGA/设备（`references/nga-clients/nga_harmony/entry/src/main/ets/model/AiScenarioConfig.ets:90`; `references/nga-clients/nga_harmony/entry/src/main/ets/pages/ai/AiSettingsPanel.ets:1591`）。
- Android 推荐改为“公开活动摘要”：只做可验证的样本统计，明确“第一页采样”而非总体；默认移除 UID、作者名、原文片段和政治倾向推断；只允许分析当前用户本人，或要求针对第三方内容的额外显式确认/最小化。保留原 prompt 会形成较高的隐私、公平、骚扰与误导风险。

### 7. Web Search 与 Function Calling

- `ToolRegistry` 真正注册了一个 `WebSearchTool`，只要 active search profile 非空就把 `web_search` JSON Schema 发给模型（`references/nga-clients/nga_harmony/entry/src/main/ets/service/ai/ActiveAiService.ets:54`; `references/nga-clients/nga_harmony/entry/src/main/ets/service/ai/tools/WebSearchTool.ets:21`, `:59`）。
- 工具流程为：带 tools 的模型决策 -> 顺序执行 tool call -> assistant tool_calls + tool result 回传模型 -> final response；工具失败会降级普通 chat 或输出“不继续推断”（`references/nga-clients/nga_harmony/entry/src/main/ets/service/ai/ActiveAiService.ets:189`, `:242`, `:276`, `:345`）。标准 tool_calls 和 DeepSeek DSML 文本格式均有 parser；DSML 是 vendor-specific compatibility hack（`references/nga-clients/nga_harmony/entry/src/main/ets/service/ai/OpenAiCompatibleClient.ets:334`, `:1078`）。
- 搜索 UI 宣称 Tavily、SerpAPI、Brave、custom 四类（`references/nga-clients/nga_harmony/entry/src/main/ets/model/WebSearchConfig.ets:47`），但 `WebSearchService.getAdapter` 的 switch 只有 Tavily，其余返回 unsupported；custom endpoint 字段从未传给 adapter（`references/nga-clients/nga_harmony/entry/src/main/ets/service/ai/WebSearchService.ets:41`）。三者是可配置 stub，不是实现。
- Tavily adapter POST `https://api.tavily.com/search`，Bearer key，basic depth、最多 5 条、包含 answer；15s read/10s connect timeout（`references/nga-clients/nga_harmony/entry/src/main/ets/service/ai/search/TavilySearchAdapter.ets:18`, `:37`, `:49`）。模型生成的 query 会发送给 Tavily，返回摘要/标题/URL/content 又发送给 AI provider（`references/nga-clients/nga_harmony/entry/src/main/ets/service/ai/tools/WebSearchTool.ets:70`; `references/nga-clients/nga_harmony/entry/src/main/ets/service/ai/WebSearchService.ets:88`）。这是双第三方数据流，当前无单独 consent。
- 搜索结果是外部不可信文本，直接放入 tool content；一句“请仅基于以上搜索结果”不能抵御 prompt injection（`references/nga-clients/nga_harmony/entry/src/main/ets/service/ai/WebSearchService.ets:95`）。Android 若后续实现，需 adapter allowlist、query preview/consent、result size/URL policy、untrusted-content delimiters、引用可点击与多轮/多工具上限。

### 8. Secrets、日志、备份、隐私与同意

| 风险 | 源码证据 | 严重度/处理 |
|---|---|---|
| API Key 明文 Preferences + backup | `SettingsState.ets:44`; `PreferencesStore.ets:55`; `backup_config.json:2` | Critical；Android release blocker，Keystore-wrapped AES-GCM + backup exclusion |
| 登出不删旧 AI 配置 | `SettingsStore.ets:80`, `:94`, `:106` | High；明确保留/清除语义并提供“删除所有 AI 数据” |
| 任意 endpoint/无 HTTPS policy | `OpenAiCompatibleClient.ets:852` | High；URI parse、HTTPS-only、redirect revalidation、no Cookie |
| provider error body 公开日志 | `OpenAiCompatibleClient.ets:464`, `:641` | High；只记 request ID/status/redacted provider ID |
| tool request body可被 verbose 记录 | `OpenAiCompatibleClient.ets:623` | High；即使默认 verbose=false 也不应存在正文日志路径 |
| DSML/搜索 query/结果摘要公开日志 | `OpenAiCompatibleClient.ets:164`, `:320`; `ActiveAiService.ets:323` | High；删 raw/query/content logging |
| release 日志总开关默认 true | `references/nga-clients/nga_harmony/entry/src/main/ets/common/utils/Logger.ets:10` | Medium/High；release redaction + compile-time policy |
| 无发送前 consent | 场景页自动发送 `AiChatPage.ets:125`；只做字段预览 `AiSettingsPanel.ets:1591` | Critical；provider + category versioned consent 必须 gate RequestBuilder |
| 全 history 每轮重发 | `AiChatPage.ets:375` | Medium/High；UI 说明、token budget、清除/不持久化策略 |
| 连接测试可能计费 | `OpenAiCompatibleClient.ets:766` | Medium；显示费用提示并限制 max output |

README/注释中的“API 密钥（不记录到日志）”只是一条注释（`references/nga-clients/nga_harmony/entry/src/main/ets/model/AiConfig.ets:32`），并不能覆盖 body/error/DSML/tool query 日志。审计未发现 Key 被显式写到 logger，但明文内存、普通持久化、backup 和错误上下文仍不合格。

### 9. 测试与实现可信度

- `entry/src/test/LocalUnit.test.ets:3` 只有 `abc` contains 模板；`entry/src/ohosTest/ets/test/Ability.test.ets:4` 也是模板。固定树内没有 AI client、SSE、Markdown、tool、secret、consent、provider shape 或场景数据测试。
- 无 fixture/fake server 能证明 `/models`、Chat JSON、SSE partial frame、tool call、DSML、timeout/cancel/HTTP error 的行为。源码存在实现，但“可在六家 provider 上稳定工作”没有测试或授权 live evidence支持。
- 本审计没有使用真实 API Key，也没有向 NGA、AI provider 或 Tavily 发付费/业务请求；结论均为 `source-observed`，不是当前服务可用性保证。

### 10. GPL 与 ArkTS -> Android 边界

- `nga_harmony` README 声明 GPL-2.0，根 `LICENSE` 为 GNU GPL v2（`references/nga-clients/nga_harmony/README.md:164`; `references/nga-clients/nga_harmony/LICENSE:59`）。当前 Android 根工程同样带 GNU GPL v2（`LICENSE:59`），所以 GPL 版本本身没有明显不兼容；但复制、翻译或修改具体源码仍是衍生使用，需要保留版权/修改 notice、提供对应源码并履行 GPL-2.0 条款（两份 `LICENSE:79`, `:90`）。该结论不是法律意见。
- ArkTS 的 `@kit.NetworkKit`、ArkUI component/state、Preferences、Ability lifecycle、task/runtime API 无法在 Android 编译。即使许可证允许，transport、storage、cancellation、Compose/View state 与 lifecycle 都必须用 Android API 独立实现。
- 可概念复用：provider/profile domain、OpenAI message/tool JSON shape、SSE parser 状态划分、stream UI 节流、active profile、场景 request-builder、三阶段 tool orchestration、capability/error matrix。
- 必须原创/独立实现：Android Keystore vault、backup/data-extraction rules、OkHttp/Flow 或 Rx cancellation、HTTPS/redirect/Cookie policy、Compose/View UI、Markdown/URL handling、Room/DataStore scope、consent/redaction/audit、instrumentation/fake-server tests。
- 不建议复制：默认 prompt 文案（尤其政治光谱/锐评）、ArkUI 页面/图标/视觉资产、自写 Markdown parser、DSML regex、弱随机 ID、明文 settings、raw logging。当前任务 PRD也明确将 `nga_harmony` 仅作为行为线索，不作为 Android UI/资产/不安全实现基线（`.trellis/tasks/07-25-nga-android-advanced/prd.md:5`, `:24`）。

### 11. 根 Android 工程的实际可行性

- 工程是 13 模块 Justwen Java/Kotlin/Groovy app，已有 `lib_base_network`、`lib_core_data`、`lib_base_ui_compose` 等合理边界（`settings.gradle:1`）。Kotlin 2.0.21、Compose 1.6.8/UI 1.7.0、Lifecycle 2.6.2、Retrofit 2.6.0、RxJava 2.2.6、Room 2.4.1；当前根工程为 `minSdk 29/compileSdk 35/targetSdk 35`（`build.gradle:121-123`）。
- App 与 UI library 已启用 Compose，可承载 AI config/chat screen（`nga_phone_base_3.0/build.gradle:153`; `lib_base_ui_compose/build.gradle:32`）。现有业务代码已经使用 coroutine/Flow，`debugRuntimeClasspath` 解析到 coroutines 1.7.3 和 OkHttp 4.12.0（后者由 Coil 间接引入），但尚未为 AI 直接声明并锁定独立 client/SSE 依赖；也未发现 DataStore、Android Security Crypto、CommonMark/Markwon 或 Moshi/Gson。实现时应显式选择并锁定 AI 所需依赖，不能把传递依赖当作稳定契约。
- `lib_base_network` 的 Retrofit/OkHttp builder 会给缺少 Cookie header 的任何请求注入 active NGA Cookie，并打印 request；converter 将所有 String body 当 GBK 且记录完整 body（`lib_base_network/src/main/java/com/justwen/androidnga/base/network/retrofit/RetrofitHelper.java:103`, `:108`, `:134`; `lib_base_network/src/main/java/com/justwen/androidnga/base/network/retrofit/converter/JsonStringConvertFactory.java:38`）。AI 请求复用它会违反第三方隔离、UTF-8 与日志要求。
- Android manifest 已有 INTERNET，但 `network_security_config.xml` 的 base config 明确允许 cleartext；custom AI endpoint 必须在业务层强制 HTTPS，不能依赖平台清单兜底。当前也未见 AI secret 的 backup exclusion（`nga_phone_base_3.0/src/main/AndroidManifest.xml:9`, `:19`; `nga_phone_base_3.0/src/main/res/xml/network_security_config.xml:9`）。
- 最合适结构是新/最近的 AI business module：纯 Kotlin domain + provider adapter + KeyVault/Consent repositories；网络 client 独立构造，不装 NGA Cookie interceptor/converter；UI 使用现有 Compose/theme/navigation。可复用 Lifecycle/ViewModel，但不把 API Key 放 Room/DataStore。

### 12. 分阶段 MVP 与相对工作量

以下为包含自动化测试/审查的相对量级，不含 foundation/reading/interactions 前置任务和外部合规审批：`S` 为局部改动，`M` 为单一子系统，`L` 为跨网络/存储/UI 的完整能力，`XL` 为多个高风险子系统及其集成验证。它们不代表日历时间承诺。

| 阶段 | 范围 | 相对工作量 | 退出条件 |
|---|---|---:|---|
| P0 安全/契约底座 | 独立无 Cookie UTF-8 client、URI/redirect policy、KeyVault、backup exclusion、provider/category consent、typed error、MockWebServer | L | secret/log/APK/backup scan 通过；未同意时零网络 |
| P1 可用底座 | 1 个 custom OpenAI-compatible provider、metadata CRUD、受限 model list/连接测试、可取消 SSE chat、内存 history、共享 preview/consent/result 基础设施 | L | JSON/SSE/error/cancel/process recreation/API 35 tests 通过 |
| P2 对象上下文 MVP | 当前楼层三点菜单“AI 总结” + 当前资料页“用户行为分析”；冻结 row/目标 UID，数据最小化、事实型输出、准确采样标识、额外 consent | M-L | 两个入口、preview/wire equality、privacy/sample-bias UI、redaction 和 stale-result tests 通过 |
| P3 体验与兼容 | provider capability matrix、成熟 Markdown/安全链接、history 清除/账号 scope、token/context budget、validated presets | M-L | 每个宣称 provider 有 fixture + 授权低频验证 |
| P4 Web Search/tools | 先仅 Tavily；query preview/consent、prompt-injection containment、引用 UI、调用/费用上限；其他 adapter 单独验收 | L/adapter | 双 provider 数据流明确、tool loop/timeout/cancel tested |

**产品决定（2026-07-27）覆盖了本审计原先将用户分析后置/仅本人优先的建议：Android MVP 同时包含两个对象上下文场景。** 当前楼层总结绑定被点击 row；用户行为分析绑定所浏览资料页的目标 UID；两者都从对象菜单进入，不以独立 AI 首页作为主入口。用户活动固定采样主题第 1 页和回复第 1 页，不自动翻页，并在各状态明确标注为近期公开活动样本。首发仍不包含六家预设、DSML、Web Search、全量历史抓取、政治光谱或攻击性画像。

### 13. 前置条件、主要风险与验证要求

前置条件：

1. 完成 task PRD 指定的 root-fork foundation/account/host/raw-response/codec 基线；AI 端点与 NGA session/network 必须是两个不可混用的 client ownership boundary（`.trellis/tasks/07-25-nga-android-advanced/prd.md:9`）。
2. 决定允许的 endpoint policy：建议默认仅预设 exact HTTPS origin；custom 允许用户 HTTPS host，但禁止 userinfo、非默认端口、IP/localhost/private range 和跨 host 携 Authorization redirect，除非开发者模式有独立风险确认。
3. 决定 Key 生命周期：Keystore invalidation、设备迁移不恢复 secret、删除/登出/换账号行为、内存清理、crash/analytics redaction。
4. 固定 provider + data category consent 版本、费用/保留提示和撤销；产品已允许从所浏览资料页发起事实型公开活动分析，但必须明确采样范围，禁止政治/敏感属性推断和攻击性“成分分析”。
5. 选定 Android SSE/JSON/Markdown 方案与 license/NOTICE；不要手写大量 parser 后再补测试。

必须验证：

- Unit/property tests：endpoint normalization、redirect/auth stripping、request JSON、all supported response/error unions、SSE 分片在任意 UTF-8/CRLF/event/data 边界、oversize/malformed frames、tool call fragments、context budget。
- MockWebServer integration：model list、connection cost limit、200/4xx/429/5xx、slow/no end、disconnect、cancel 后 socket/call 终止、rotation/back/account switch/provider deletion、绝不附带 NGA Cookie。
- Secret/privacy：Key 不出现在 Room/DataStore/SharedPreferences、Auto Backup/device transfer、exports、logs、crash/analytics、screenshots/recents、APK/resources；删除/rekey/Keystore invalidation tested。
- Consent/redaction：每 provider × `chat/post_summary/user_analysis/search_query` 分开记录；未同意/撤销/切 provider 时 RequestBuilder 前 fail closed；payload preview 与实际 wire fixture 一致。
- UI：API 35 主门覆盖 streaming recomposition、stop button、retry、process death、Markdown large output、safe link handoff、accessibility；API 30 floor smoke 和 API 36 target-35 forward run 按项目规范有设备再做（`.trellis/spec/backend/android-quality-guidelines.md:33`, `:68`）。
- Live 验证：只在用户明确授权后，使用测试账户/低成本模型/最小非敏感 prompt，一次一 provider；保留脱敏状态/shape/时间证据，不保存 Key、论坛正文或 raw provider response。

## External References

- OpenAI API reference, Chat Completions: <https://platform.openai.com/docs/api-reference/chat>（接口概念参考；2026-07-27 页面从当前环境返回 403，未据此证明具体 provider 兼容性）。
- OpenAI API reference, Models list: <https://platform.openai.com/docs/api-reference/models/list>（同上；provider-specific shape 必须 fixture/live 验证）。
- WHATWG Server-Sent Events: <https://html.spec.whatwg.org/multipage/server-sent-events.html>（2026-07-27 可访问；用于多 `data:` field、event boundary 和 CR/LF 语义）。
- Android Keystore: <https://developer.android.com/privacy-and-security/keystore>（2026-07-27 可访问；KeyVault 根密钥与硬件/系统保护参考）。
- Android backup: <https://developer.android.com/identity/data/autobackup>（2026-07-27 可访问；secret exclusion/device transfer policy 参考）。
- Android log disclosure risk: <https://developer.android.com/privacy-and-security/risks/log-info-disclosure>（2026-07-27 可访问；prompt/key/content logging policy 参考）。
- Tavily Search API: <https://docs.tavily.com/documentation/api-reference/endpoint/search>（2026-07-27 可访问；只能说明官方 documented endpoint，不证明固定源码当前 Key/套餐可用）。
- GNU GPL v2: <https://www.gnu.org/licenses/old-licenses/gpl-2.0.en.html>（2026-07-27 可访问；复制/翻译前仍应做项目法律/notice 审查）。

## Related Specs

- `.trellis/tasks/07-25-nga-android-advanced/prd.md:22-35`：BYOK、Keystore、consent、可取消 streaming、secret exclusion 与 tests 的验收边界。
- `.trellis/tasks/07-25-nga-android-advanced/design.md:52-68`：`AiConfigRepository -> ConsentStore -> Redaction -> ProviderAdapter -> Flow` 的既定 Android 架构。
- `.trellis/tasks/07-25-nga-android-advanced/implement.md:16-19`：AI provider/key/consent 先于 chat/scenario，随后做 API 35 与 license gate。
- `.trellis/spec/backend/network-foundation-contract.md:243-250`：不得伪装官方、不得记录 secret/content、敏感 header 只发 exact HTTPS host。
- `.trellis/spec/backend/nga-platform-access-rules.md:58-71`：HTTPS origin/redirect revalidation 和 external host no-Cookie policy。
- `.trellis/spec/backend/nga-platform-access-rules.md:224-258`：禁止 raw/secret logging，自动化只用 fixture/fake server。
- `.trellis/spec/backend/android-quality-guidelines.md:30-72`：min/compile/target 与 API 35 主门、API 30 floor、API 36 forward matrix。

## Caveats / Not Found

- 未发现 `nga_harmony` AI 专项测试、fixture、benchmark、privacy policy、provider retention/cost 文案、secret migration、backup exclusion、真正的 HTTP cancel API或可达的自由聊天入口。
- 未发现 SerpAPI、Brave 或 custom Search adapter；它们仅存在类型、预设和设置 UI。
- 未发现 provider-specific OpenAI compatibility layer；“六服务商支持”是统一 endpoint metadata + 通用 wire shape，不是六套经验证 adapter。
- 未验证任何预设默认模型在 2026-07-27 仍存在/可用，也未验证 NGA 的用户第一页搜索行为仍被服务端支持；固定源码只能证明 client 行为。
- 上游六个后续提交虽修改 `ThreadPanel`/`ProfilePanel` 等交叉文件，但 AI core hash 未变，场景入口的数据构造也保持一致；若未来继续审计，应重新固定 commit，而不是假定 `main` 不变。
- GPL 兼容结论只说明两边仓库都展示 GPL-2.0 文本，不替代版权来源台账、作者 notice、资产权利或正式法律意见。
