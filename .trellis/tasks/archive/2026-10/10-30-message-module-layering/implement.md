# Implement: 消息模块按 Compose 分层重构

> 前置：`10-30-api-result-error-model` 已完成并合入；`prd.md` + `design.md` 已评审；状态进入 `in_progress` 后执行。

## 0. 准备与证据核对

- [ ] `rg -n "ACTIVITY_MESSAGE_DETAIL|MessageConvertFactory|MessageDetailRepository\\.(recipient|msgTitle)" --glob '!**/build/**'` 列出全部入口与引用。
- [ ] 记录当前请求参数、分页大小、发送请求体到任务 `research/`，作为重构后的对照基线。
- [ ] 确认 `lib_bu_message` 的 `testOptions` 可运行 Kotlin 单测（已有 `testImplementation junit:4.13.2`）。

## 1. 共享解析修复

- [ ] 新建 `LegacyMessageJson.kt`：`stripWrapper`、`truncateErrorFill`、`repairNumericTokens`，输入输出均为 `String`，附 KDoc 说明来源是旧工厂行为。
- [ ] 单测覆盖：JS 前缀、注释截断、`+123` / `0123` token。

## 2. 列表页

- [ ] `list/MessageListParser.kt`：迁移 `getMessageListInfo` 逻辑，返回 `ApiResult<MessageListInfo>`；空响应 → `Unexpected`；`error.0` → `Business`；无数据 → `Business("请重新登录")`。
- [ ] `list/MessageListRepository.kt`：实例化 PagingSource，`apiSafeCall` 调用；空列表仍返回 `LoadResult.Error`（保持现状）。
- [ ] `list/MessageListViewModel.kt`：`cachedIn(viewModelScope)`；提供 `refresh()` 入口给切号回调。
- [ ] `list/MessageListScreen.kt`（默认外观）与 `list/MiuixMessageListScreen.kt`（迁自 `MiuixMessagePages.kt` 的列表部分）。
- [ ] `list/MessageListActivity.kt`：只保留路由、菜单、FAB、ViewModel、Screen；`startMessageDetail` 增加 `subject`/`recipient` 参数。
- [ ] 测试：迁移列表 golden 断言。
- [ ] 验证：`./gradlew :lib_bu_message:testDebugUnitTest :nga_phone_base_3.0:compileDebugJavaWithJavac`

## 3. 详情页

- [ ] `detail/MessageDetailParser.kt`：迁移 `getMessageDetailInfo` + `parseJsonThreadPage` + `convertJSobjToList` + `fillUserInfo` + `formatContent`；保留 `__P` 片段剔除与图片域名改写。
- [ ] `detail/MessageDetailRepository.kt`：删除 `recipient`/`msgTitle` 全局字段；只返回分页流。
- [ ] `detail/MessageDetailViewModel.kt`：`SavedStateHandle` 读取 `mid`、`subject`、`recipient`；暴露 paging 流与回复所需参数。
- [ ] `detail/MessageDetailScreen.kt` 与 `detail/MiuixMessageDetailScreen.kt`（迁自 `MiuixMessagePages.kt` 的详情部分）。
- [ ] `detail/MessageDetailActivity.kt`：FAB 使用 ViewModel 参数；`startWebView` 保持在 Activity。
- [ ] 测试：迁移详情 golden 断言 + 楼层计算 + 用户拼接。
- [ ] 验证：`./gradlew :lib_bu_message:testDebugUnitTest`

## 4. 发送页

- [ ] `post/MessagePostParser.kt`：迁移 `checkResult`，返回 `ApiResult<String>`。
- [ ] `post/MessagePostRepository.kt`：去掉共享可变 `queryParamMap`，每次调用构造新 map；GBK 编码与全角逗号处理保持逐字一致。
- [ ] `post/MessagePostViewModel.kt`：`SavedStateHandle` 读取 `mid`/`action`/`to`/`title`；草稿与 `isRunning` 归位；校验文案不变；通过 `Channel` 发一次性结果事件。
- [ ] `post/MessagePostScreen.kt`（默认外观）与迁移后的 `post/MiuixMessageComposer.kt`。
- [ ] `post/MessagePostActivity.kt`：只保留路由、VM、Screen、结果收集与提示。
- [ ] 测试：`MessagePostParserTest` 覆盖三种成功标记、`error.0`、通用失败。
- [ ] 验证：`./gradlew :lib_bu_message:testDebugUnitTest`

## 5. 清理与清单

- [ ] 删除 `MessageConvertFactory.java`、旧 `compose/` 文件（`MessageListActivity.kt`、`MessageRepository.kt`、`MessageViewModel.kt`、`MiuixMessagePages.kt`、`detail/*`、`post/*` 中已被替代的旧文件）。
- [ ] 更新 `lib_bu_message/src/main/AndroidManifest.xml` 的 activity 全限定名。
- [ ] 更新 `nga_phone_base_3.0` 中所有旧包名引用；删除旧 `MessageParserGoldenTest.java`。
- [ ] `rg -n "MessageConvertFactory|module\\.message\\.compose" --glob '!**/build/**'` 必须为空。

## 6. 全量验证

- [ ] `./gradlew :lib_bu_message:testDebugUnitTest`
- [ ] `./gradlew :nga_phone_base_3.0:testDebugUnitTest`
- [ ] `./gradlew assembleDebug`
- [ ] `./gradlew lintDebug`（或项目现有质量门定义的 lint 任务）
- [ ] 真机走查默认外观与 Miuix 外观的列表/详情/发送；未获当次授权时记录 “not run per project policy”，不得自行执行 adb。

## 7. 文档与收尾

- [ ] 更新 `.trellis/spec/backend/nga-platform-operation-registry.md` 的 `MESSAGE.LIST` / `MESSAGE.READ` / `MESSAGE.SEND` 行：新的代码位置、错误处理列、`status` 由 `unchanged` 改为实际状态。
- [ ] 更新 `.trellis/spec/frontend/component-guidelines.md`（若消息页的组件约定发生变化）。
- [ ] 更新 `SOURCE_LEDGER.md`：记录结构借鉴自上游 `ddb31bcb`、`c83bde29`、`44ea2d65`，代码按本 fork 模型重写。
- [ ] 若解析器拆分暴露出新的协议事实，补充到 `network-foundation-contract.md`。

## 8. 回滚点

- 三个页面各自一个提交，删除工厂类单独一个提交：回退任一步不影响其他页。
- 风险最高的文件：`MessageDetailParser.kt`（文件最大、修复规则最多）、`MessagePostRepository.kt`（请求体逐字兼容）、`AndroidManifest.xml`（入口名）。
