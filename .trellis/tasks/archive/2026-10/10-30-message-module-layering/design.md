# Design: 消息模块按 Compose 分层重构

## 1. Current vs Target

### Current

| 文件 | 现有职责 |
| --- | --- |
| `compose/MessageListActivity.kt` | 路由、菜单、`ContentView`、`MiuixContentView`、行渲染、跳转 |
| `compose/MessageRepository.kt` | 参数拼装、请求、解析、判错（`catch (e: Exception)`） |
| `compose/MessageViewModel.kt` | 15 行转发 |
| `compose/detail/MessageDetailActivity.kt` | 路由、FAB、两套 UI、行渲染、打开浏览器 |
| `compose/detail/MessageDetailModel.kt` | 转发 + 读取全局 `recipient`/`msgTitle` |
| `compose/detail/MessageDetailRepository.kt` | 请求、解析、写全局 `recipient`/`msgTitle` |
| `compose/post/MessagePostActivity.kt` | 表单、校验、发送、结果处理 |
| `compose/post/MessagePostModel.kt` | 草稿状态、校验、发送 |
| `compose/post/MessagePostRepository.kt` | 共享可变 `queryParamMap`、GBK 编码、结果判定 |
| `compose/MiuixMessagePages.kt` | Miuix 列表/详情渲染 |
| `compose/post/MiuixMessageComposer.kt` | Miuix 发送表单 |
| `MessageConvertFactory.java` | 285 行，列表/详情/发送三类解析 + 可变 `errorMsg` |

### Target

```
module/message/
  LegacyMessageJson.kt          // 共享的 JS 包装剥离、注释截断、非标准数字修复
  list/
    MessageListActivity.kt      // ARouter 路由 + 菜单 + FAB + VM + Screen 调用
    MessageListScreen.kt        // 默认 Compose 列表
    MiuixMessageListScreen.kt   // Miuix 列表（迁自 MiuixMessagePages.kt）
    MessageListViewModel.kt     // Paging 流 + 刷新入口
    MessageListRepository.kt    // 请求 + apiSafeCall
    MessageListParser.kt        // String → ApiResult<MessageListInfo>
  detail/
    MessageDetailActivity.kt
    MessageDetailScreen.kt
    MiuixMessageDetailScreen.kt
    MessageDetailViewModel.kt   // SavedStateHandle: mid/subject/recipient
    MessageDetailRepository.kt
    MessageDetailParser.kt      // String,page → ApiResult<MessageDetailInfo>
  post/
    MessagePostActivity.kt
    MessagePostScreen.kt
    MiuixMessageComposer.kt
    MessagePostViewModel.kt     // SavedStateHandle + 草稿 + 发送状态
    MessagePostRepository.kt
    MessagePostParser.kt        // String → ApiResult<String>
```

`MessageConvertFactory.java`、`compose/` 目录在迁移完成后删除；`AndroidManifest.xml` 中的 activity 全限定名同步更新，ARouter `@Route` 路径不变。

## 2. Layer Contracts

- **Activity**：只做 `@Route`、`getOptionMenuData()`、`getFabClickAction()`、获取 ViewModel、调用 Screen、处理导航与 `startActivity`。不直接调用 Repository。
- **Screen**：`@Composable`，输入 ViewModel/不可变参数 + 回调，不持有网络逻辑，不读全局单例。
- **ViewModel**：`ViewModel` + `SavedStateHandle`；暴露 `Flow<PagingData<...>>`、只读状态与一次性事件（`Channel.receiveAsFlow()`）；不调用 ARouter、不弹 Toast、不持有 Context。
- **Repository**：构造请求参数、调用 `RetrofitServiceKt`、通过 `apiSafeCall` 交给 Parser；不持有可变全局字段。
- **Parser**：纯函数，输入响应字符串，输出 `ApiResult<现有模型>`；不依赖 Android `Context`，错误用常量文案（`"网络错误"`、`"请重新登录"`）。

默认外观与 Miuix 外观共用同一个 ViewModel 和同一份状态，只切换 Screen 实现。

## 3. Behavior That Must Stay Byte-Identical

- 列表请求：`__lib=message`、`__act=message`、`act=list`、`lite=js`、`page`；分页 20。
- 详情请求：`act=read`、`lite=js`、`mid`、`page`；分页 20；`lou = 20 * (page - 1) + i`。
- 发送请求：query 含 `__lib`、`__act`、`lite=js`、`charset=gbk`、`act`；form 含 `mid`、GBK 编码的 `to`（全角逗号转半角）、可选 GBK `subject`、GBK `content`。
- 响应修复：剥离 `window.script_muti_get_var_store=`、截断 `/*error fill content`、修复 `"content":+123` 与 `"content":0123` 类 token、剔除详情中 `"__P"` 到 `"this_visit_rows"` 的无效片段、详情图片域名 `[img]./mon_` 改写。
- 详情 `_Alluser` 取 `allUsers` 空格分隔后的奇数位拼接；`_Title` 取第一条消息 subject，空则空串。
- 发送成功标记仍为 `"发送完毕 ..."`、`" @提醒每24小时不能超过50个"`、`"操作成功"`。
- 校验文案不变：`"请输入收件人"`、`"请输入内容或者内容字数少于6"`、`"发送中，请勿重复点击"`。
- 失败 UI 不变：统一 `error_load_failed`（「加载失败，请重试！」），点击重试。

## 4. State Ownership Changes

- 详情页标题与收件人：由 `MessageDetailRepository` 的可变 object 字段改为**导航参数**。列表点击详情时通过 ARouter 传入 `mid`、`subject`、`recipient`，详情 ViewModel 从 `SavedStateHandle` 读取。收件人取列表行的 `from_username`，与上游一致；不再使用解析出的 `_Alluser` 参与者列表（用户确认 2026-10-30）。
  - 兼容：缺少 `subject`/`recipient`（深链或其他入口）时为空串，回复 FAB 仍然可用，与当前首屏数据到达前的状态一致。
  - 行为差异：单人会话回复对象不变；多人会话由「全部参与者」变为「会话发起人」。这是有意接受的差异。
  - 收益：删除全局可变状态；回复 FAB 不必等第一页解析完成。
- 发送页草稿与 `isRunning` 归 ViewModel；Repository 每次调用新建请求 map，不再共享可变 `queryParamMap`（registry `MESSAGE.SEND` 已要求 “do not share mutable request map”）。

## 5. Tests

- 新增 `lib_bu_message/src/test/.../MessageParserGoldenTest.kt`，迁移现有三条断言（列表坐标/顺序、详情楼层/用户/标题、业务拒绝），并补充空串、`null`、非法 JSON、`error.0` 为空四类用例。
- 新增 `MessagePostParserTest`：三种成功标记、`error.0`、通用失败。
- `LegacyMessageJson` 的修复逻辑用列表/详情共用的输入样本覆盖一次。
- 删除 `nga_phone_base_3.0/src/test/java/sp/phone/json/MessageParserGoldenTest.java`（其覆盖已迁入模块测试）。

## 6. Rollout Order

1. 列表页（`MessageListActivity` + Repository + Parser + Screen），删除列表相关工厂方法。
2. 详情页（含导航参数化），删除详情相关工厂方法。
3. 发送页（Parser 化 + 请求 map 修复），删除 `MessageConvertFactory` 与旧 `compose` 文件。
4. 真机走查两套外观（需维护者当次授权，未授权则记录为 “not run per project policy”）。

每一步都可独立编译、跑测试、回退。

## 7. Rollback

- 按步骤提交：列表 / 详情 / 发送各一个提交；回退单步不影响其他两页。
- 删除 `MessageConvertFactory` 放在最后一步，回退该步即可恢复旧解析。
- 无数据、路由、清单持久化迁移；ARouter 路径保持不变，因此外部入口不会失效。

## 8. Risks

- 详情 FAB 的 `to`/`title` 来源改为导航参数后，若存在未传参的入口，回复时会缺少收件人。计划用 grep 全仓 `ACTIVITY_MESSAGE_DETAIL` 确认入口，并在 ViewModel 中保留空值容错。
- Miuix 与默认外观共用 ViewModel，`cachedIn(viewModelScope)` 的生命周期需确认不会在切换外观时重复请求。
- 发送页请求 map 修复可能改变旧有的 `act` 泄漏行为；验证时记录实际请求参数，确认只多不少。
- 解析器不再使用 `ContextUtils`，错误文案若与资源字符串不一致会导致用户可见变化；以 `strings.xml` 当前值为准。
