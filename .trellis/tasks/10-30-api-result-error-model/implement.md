# Implement: 网络层 ApiResult 错误模型

> 前置：`prd.md` + `design.md` 已评审。状态进入 `in_progress` 后才能执行。

## 1. Build 配置

- [ ] 在 `gradle/libs.versions.toml` 增加 `coroutines = "1.10.2"` 与 `kotlinx-coroutines-core` 库条目（仅测试使用）。
- [ ] 在 `lib_base_network/build.gradle` 增加：
  - `testImplementation 'junit:junit:4.13.2'`
  - `testImplementation libs.kotlinx.coroutines.core`
- [ ] `./gradlew :lib_base_network:dependencies --configuration debugCompileClasspath` 确认 fastjson2 与 retrofit 可见，且未引入运行时协程依赖。

## 2. 类型实现

- [ ] 新建 `ApiError.kt`：sealed interface、6 个 subtype、`BusinessException`。
- [ ] 新建 `ApiResult.kt`：sealed interface、`success`/`failure` 工厂、`dataOrNull`/`errorOrNull` 扩展。
- [ ] 新建 `ApiSafeCall.kt`：`Throwable.toApiError(message)` 内部映射 + `apiSafeCall`，`CancellationException` 先于其他 catch 重新抛出。
- [ ] 所有公开类型写 KDoc，说明「这是客户端失败分类，不是服务端协议」。

## 3. 测试先行

- [ ] `ApiErrorMappingTest`：6 类映射 + `cause` 保留。
- [ ] `ApiSafeCallTest`：成功 / 解析抛异常 / 请求抛异常 / 取消传播。
- [ ] 运行 `./gradlew :lib_base_network:testDebugUnitTest`。
- [ ] 若 `HttpException` 构造不便，用 `Response.error(...)` 构造并断言 `code` / `errorBody`。

## 4. 消息 Repository 接入

- [ ] `compose/MessageRepository.kt`：用 `apiSafeCall` 替换 `try/catch`，`LoadResult.Error` 改为携带 `ApiError.cause`。
- [ ] `detail/MessageDetailRepository.kt`：同上，保留 `recipient` / `msgTitle` 的现有写入行为。
- [ ] 不改分页大小、请求参数、空列表判定。

## 5. 验证

- [ ] `./gradlew :lib_base_network:testDebugUnitTest`
- [ ] `./gradlew :lib_bu_message:testDebugUnitTest`
- [ ] `./gradlew :nga_phone_base_3.0:testDebugUnitTest --tests 'sp.phone.json.MessageParserGoldenTest'`
- [ ] `./gradlew :lib_base_network:lintDebug`（或项目现有质量门）
- [ ] `rg -n "CancellationException" lib_base_network/src` 确认只有一处显式重抛。

## 6. 文档与收尾

- [ ] 更新 `.trellis/spec/backend/network-foundation-contract.md`：增加「共享失败分类」小节，说明 `ApiResult` 语义与传输层不变的边界。
- [ ] 如 registry 中的 `MESSAGE.*` 错误处理列发生变化，同步更新 `.trellis/spec/backend/nga-platform-operation-registry.md`。
- [ ] 在 `SOURCE_LEDGER.md` 的 fork changes 中记录借鉴自上游 `0b536408..36823f74` 的错误模型结构（仅结构借鉴，代码按本 fork 包名和语义重写）。

## 7. 回滚点

- 提交粒度：①类型+测试 ②消息 Repository 接入。任一失败可只回退后一步。
- 不涉及数据迁移，回滚无需清理。
