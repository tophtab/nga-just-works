# NGA Android AI 设置与总结设计

## Scope boundary

本设计只有一条执行路径：用户配置的 OpenAI-compatible API。手机系统模型、本地模型、多 provider 管理、通用聊天和其他高级功能不进入本任务。

```text
AI 设置 -> 保存一份 AiConfig
                 |
楼层菜单 --------+-> AiSummaryClient -> AI 总结结果
用户资料页 ------+
```

## Existing UI integration

本任务按已批准的现有 Java/Preference/XML 页面接入方案实施，作为
`android-migration-architecture.md` 中 Kotlin/Compose 迁移目标的局部互操作例外。
当前三个调用入口均由 Java Activity/Fragment 承载；使用同一套 Java 可调用的
配置、请求和总结状态边界，可以保持现有导航与生命周期，不引入第二套页面或
为 AI 功能先执行整项 M1/M2 迁移。此例外不改变其他任务的迁移目标。

- 在 `settings.xml` 根级增加与“实验室”同级的 `PreferenceScreen`：“AI 设置”。
- 该入口通过 `SettingsFragment` 和 `LauncherSubActivity` 的现有子页面导航打开新的 `SettingsAiFragment`；二级页标题为“AI 设置”，系统返回行为回到主设置页。
- 二级页使用独立 `settings_ai.xml`，集中显示 API 服务地址、API Key、模型名称、连接测试和清除配置，不在一级页面直接展开输入项。
- 在 `article_list_context_menu.xml` 和 `article_list_context_menu_with_tid.xml` 同时增加“AI 总结”，由 `ArticleListFragment` 使用菜单已绑定的 `ThreadRowInfo` 构造请求。
- 在 `menu_user_profile.xml` 增加“AI 总结”，由 `ProfileActivity` 使用已加载的 `mProfileData.uid` 作为目标用户。
- 两个入口都在原页面打开同一个可滚动总结弹窗，只改变标题和输入内容；不增加独立 AI 结果主页。

## AI configuration

`AiConfig` 在内存中持有一份校验后的当前配置：

```text
normalizedEndpoint + apiKey + model
```

- endpoint 必须是 HTTPS 地址；model 和 Key 不得为空。
- `AiConfigStore` 将整份配置用 Android Keystore AES-GCM 加密后，通过
  `AtomicFile` 写入 `noBackupFilesDir/ai-config.bin`；普通偏好和备份不保存 Key。
  地址、模型与 Key 作为同一条记录原子更新，避免新地址读到旧 Key 的混合状态。
- Key 丢失或密文损坏时拒绝加载并清理失效记录，不创建替代 Key 尝试解密，也不回退到缓存或明文。
- API Key 在二级页中表现为密码型设置项，但不得使用 `EditTextPreference` 的默认明文持久化；输入结果只交给安全存储，页面仅显示掩码状态。
- “测试连接”发送一条短请求，并把结果归类为成功、认证失败、地址错误、网络错误或服务端错误。
- 删除配置时同时删除密文 Key。界面只显示掩码，不回显完整 Key。

## AI request client

新增独立 `AiSummaryClient`，使用当前工程已有的 OkHttp 运行时能力，但不复用 NGA `RetrofitHelper`，从而避免携带 NGA Cookie、NGA 编解码和正文日志。

客户端只实现本任务所需的非流式 OpenAI-compatible Chat Completions 请求：

```text
POST <endpoint>/chat/completions
Authorization: Bearer <user key>
Content-Type: application/json; charset=utf-8
```

endpoint 规范化为最终 Chat Completions URL：移除末尾斜杠，保留用户填写的
版本或自定义路径；已含 `/chat/completions` 时不重复拼接，否则追加该路径。
不猜测根地址的 `/v1`，设置页示例使用 `https://api.openai.com/v1`。
拒绝 userinfo、query、fragment、控制字符和非 HTTPS 地址。
响应只读取首个文本结果；HTTP、网络和解析错误映射为简单的 UI 错误。
底层请求必须可取消，并关闭自动跳转和自动重试。构建解析出的 OkHttp 为
4.12.0，测试库须与实际运行时版本一致；对 `503` 等可能触发隐式 follow-up
的响应，使用单次请求语义并验证服务端请求计数仍为一次。

## Summary inputs

### Floor summary

`FloorSummaryInput` 在点击菜单时立即冻结：帖子标题、楼层号、作者、当前楼层纯文本正文。它不遍历其他楼层，也不包含 NGA Cookie 或账号凭证。

### Profile summary

`ProfileSummaryInput` 绑定当前 `mProfileData.uid`。App 使用现有 NGA 登录态获取该 UID 的主题第一页和回复第一页，将公开标题、版面、日期及受限长度的回复文本整理成摘要输入，不自动请求后续页面。

## UI state

共用一个简单状态模型：

```text
Idle -> Loading -> Success | Error
```

- 未配置时直接显示“请先配置 AI”，不创建网络请求。
- 新请求、页面退出或目标对象变化时取消旧请求并丢弃迟到结果。
- 总结弹窗提供关闭、重试和复制；不提供多轮对话或流式聊天。关闭弹窗即取消尚未完成的请求。

## Validation

- 单元测试：配置校验、endpoint 规范化、Key 保存/删除、楼层输入只含当前行、资料输入绑定目标 UID。
- MockWebServer：连接测试、成功响应、401/403、429、5xx、超时、畸形 JSON、取消，以及请求中不存在 NGA Cookie。
- UI/集成：一级“AI 设置”入口、二级设置页及返回行为、两套楼层菜单、资料页菜单、未配置引导、共用可滚动总结弹窗、加载/结果/错误/重试/复制、关闭后旧结果不串页。

## Rollback

AI 功能不修改现有 NGA 数据库和论坛请求协议。若实现发生问题，可以隐藏三个 AI 入口并移除独立客户端，不影响原有设置、阅读和用户资料功能。
