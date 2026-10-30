# 网络层引入 ApiResult 错误模型

## Goal

在 `lib_base_network` 增加统一的失败建模：`ApiResult<T>`、`ApiError` sealed 类型、`BusinessException` 与 `apiSafeCall`，把超时、无网络、HTTP 状态码、JSON 解析失败、业务拒绝和未知异常区分开，并保证协程取消不被吞掉。消息模块的现有 Repository 首先接入该模型。

## Background

- 当前消息模块失败路径：`MessageRepository.kt` 与 `MessageDetailRepository.kt` 用 `catch (e: Exception)` 捕获后返回 `LoadResult.Error(Exception())`，既丢失原因，也会把 `CancellationException` 当成普通错误。
- 现有代码中没有可复用的 `Result` / `ApiResult` 抽象。
- `lib_base_network` 目前是 Java 为主的 Java/Kotlin 混合模块，已启用 `kotlin-android` 插件；`RetrofitServiceKt` 提供 `suspend` 接口。
- `RetrofitHelper` 的 Cookie 注入、UA、GBK 转换器行为由 `network-foundation-contract.md` 约束，本任务不改。

## Requirements

- R1：新增 `ApiResult<out T>`，成功时携带 `data`，失败时携带 `error`，二者互斥；提供 `success` / `failure` 工厂与 `isSuccess` / `isFailure` 查询。
- R2：新增 `ApiError` sealed 类型，至少覆盖：无网络/IO 失败、超时、HTTP 状态码异常、JSON 解析失败、业务拒绝、未预期异常；错误需保留原始 `cause`。
- R3：新增 `BusinessException`（携带可选业务码与消息），用于服务端返回「HTTP 成功但业务失败」的场景。
- R4：新增 `apiSafeCall`：执行 `suspend` 请求并交给解析函数，异常统一转换为 `ApiResult.failure`；`CancellationException` 必须原样重新抛出。
- R5：分类规则明确：`SocketTimeoutException` → 超时，`IOException` → 无网络，`retrofit2.HttpException` → HTTP 错误，`com.alibaba.fastjson2.JSONException` → JSON 解析，`BusinessException` → 业务错误，其余 → 未预期异常。
- R6：消息模块两个现有 Repository 改用 `apiSafeCall`/`ApiResult`，`LoadResult.Error` 必须携带可读原因，不再构造空 `Exception()`；`MessageDetailRepository` 不再把解析错误藏在可变 `errorMsg` 里。
- R7：不修改 `RetrofitService` / `RetrofitServiceKt` 签名、不修改 `RetrofitHelper` 拦截器、不修改 `JsonStringConvertFactory` 的 GBK 与空串行为。
- R8：新增单元测试覆盖成功、各类失败分类与取消传播。消息 Repository 的「错误透传」单测因现有 PagingSource 依赖 Android Context、且这两个 Repository 会在子任务 `10-30-message-module-layering` 中整体重写，改由该子任务在仓储边界覆盖。

## Acceptance Criteria

- [ ] `ApiResult`、`ApiError`、`BusinessException`、`apiSafeCall` 在 `lib_base_network` 中可用，并有公开的 KDoc 说明各错误语义。
- [ ] 单元测试验证：超时、无网络、HTTP 错误码、JSON 解析失败、业务拒绝、未知异常各自映射到对应 `ApiError`。
- [ ] 单元测试验证：`CancellationException` 从 `apiSafeCall` 原样抛出，而不是变成 `ApiResult.failure`。
- [ ] 消息列表与详情在请求失败时，`LoadResult.Error` 携带的错误对象能说明失败类别，不再是无消息的空异常。
- [ ] 现有 `MessageParserGoldenTest` 与其余测试保持通过。
- [ ] 无任何用户可见的界面或文案变化。

## Technical Notes

- 该模型只做「客户端失败分类」，不重试、不缓存、不改请求构造。
- 解析函数签名建议为 `(String) -> ApiResult<T>`；空响应体的判定留给各 Parser，传输层保持现状。
- 包位置与现有模块命名一致，放在 `com.justwen.androidnga.base.network` 下，不复制上游的 `com.client.*` 包名。

## Out of Scope

- 其他业务模块接入 `ApiResult`。
- 统一重试、退避、错误埋点。
- 传输层遗留缺陷（静默空串、全量日志、GBK 假设）。
