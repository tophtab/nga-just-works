# Design: 网络层 ApiResult 错误模型

## 1. Boundaries

- 只改 `lib_base_network` 的「失败表示」，不改传输、Cookie、UA、GBK 转换、重试。
- 依赖方向不变：`lib_base_network` → `lib_base_common`（fastjson2 经 api 暴露）、`lib_base_logger`、Retrofit。
- 不新增运行时依赖；Kotlin 协程只用于测试。

## 2. File Layout

```
lib_base_network/src/main/java/com/justwen/androidnga/base/network/
  ApiResult.kt      // sealed 结果类型 + 便捷查询
  ApiError.kt       // sealed 错误类型 + BusinessException
  ApiSafeCall.kt    // suspend 调用包装 + Throwable 分类
lib_base_network/src/test/java/com/justwen/androidnga/base/network/
  ApiErrorMappingTest.kt
  ApiSafeCallTest.kt
```

包名沿用本 fork 的 `com.justwen.androidnga`，不复制上游 `com.client.*`。

## 3. Contracts

### 3.1 Result

```kotlin
sealed interface ApiResult<out T> {
    data class Success<T>(val data: T) : ApiResult<T>
    data class Failure(val error: ApiError) : ApiResult<Nothing>

    companion object {
        fun <T> success(data: T): ApiResult<T> = Success(data)
        fun failure(error: ApiError): ApiResult<Nothing> = Failure(error)
        fun failure(cause: Throwable, message: String? = null): ApiResult<Nothing> =
            Failure(cause.toApiError(message))
    }
}

val <T> ApiResult<T>.dataOrNull: T? get() = (this as? ApiResult.Success)?.data
val ApiResult<*>.errorOrNull: ApiError? get() = (this as? ApiResult.Failure)?.error
```

与上游的差异：上游用 `data class ApiResult(data: T? = null, error: ApiError? = null)`，允许「两者都为 null」和「两者都非空」的非法状态。这里改为 sealed，`when` 可穷尽，调用方不会拿到两个都空的结果。代价是从上游示例迁移到本实现时需要把 `result.data?.let` 改成 `when`，属一次性成本。

### 3.2 Errors

```kotlin
sealed interface ApiError {
    val cause: Throwable

    data class NetworkUnavailable(override val cause: Throwable) : ApiError
    data class Timeout(override val cause: Throwable) : ApiError
    data class HttpStatus(
        val code: Int,
        val message: String?,
        val errorBody: String?,
        override val cause: Throwable,
    ) : ApiError
    data class JsonParse(val message: String?, override val cause: Throwable) : ApiError
    data class Business(val code: String?, val message: String, override val cause: Throwable) : ApiError
    data class Unexpected(override val cause: Throwable) : ApiError
}

class BusinessException(val code: String? = null, override val message: String) : Exception(message)
```

`cause` 用 `val` 而非上游的 `var`，避免错误对象被外部改写。

### 3.3 Mapping Order

分类必须按此顺序，先具体后宽泛（`SocketTimeoutException` 是 `IOException` 子类）：

| 顺序 | 条件 | 结果 |
| --- | --- | --- |
| 1 | `BusinessException` | `Business(code, message)` |
| 2 | `SocketTimeoutException` | `Timeout` |
| 3 | `retrofit2.HttpException` | `HttpStatus(code, message, errorBody)` |
| 4 | `com.alibaba.fastjson2.JSONException` | `JsonParse(message)` |
| 5 | `java.io.IOException` | `NetworkUnavailable` |
| 6 | `kotlin.coroutines.cancellation.CancellationException` | **重新抛出，不转换** |
| 7 | 其他 | `Unexpected` |

`CancellationException` 的检查放在 `apiSafeCall` 的 catch 中优先执行，而不是放在 `toApiError` 内部作为普通映射，避免任何调用路径把它吞掉。

### 3.4 Safe Call

```kotlin
suspend fun <T : Any> apiSafeCall(
    request: suspend () -> String,
    parse: (String) -> ApiResult<T>,
): ApiResult<T> = try {
    parse(request())
} catch (e: CancellationException) {
    throw e
} catch (e: Throwable) {
    ApiResult.failure(e)
}
```

`parse` 抛出的异常同样被分类，因此 Parser 可以选择抛 `BusinessException` 或返回 `ApiResult.Failure`，两种都成立。

## 4. Integration With Existing Message Repositories

本任务只做最小接入，不移动解析代码（分页/分层在子任务 `10-30-message-module-layering`）：

```kotlin
val result = apiSafeCall(
    request = { service.getString(paramMap) },
    parse = { js ->
        val factory = MessageConvertFactory()
        factory.getMessageListInfo(js)?.let { ApiResult.success(it) }
            ?: ApiResult.failure(BusinessException(message = factory.errorMsg.ifEmpty { "请重新登录" }))
    },
)
return when (result) {
    is ApiResult.Success -> LoadResult.Page(result.data.messageEntryList.orEmpty(), preKey, nextKey)
    is ApiResult.Failure -> LoadResult.Error(result.error.cause)
}
```

约束：

- 空列表仍按现有行为返回 `LoadResult.Error`（registry 中 `MESSAGE.LIST` 记录为 “Paging error on empty”），本任务不改。
- `MessageDetailRepository` 的 `recipient` / `msgTitle` 全局字段本任务不动，由子任务移除。
- `MessageConvertFactory.errorMsg` 的可变错误状态本任务不拆，只在接入点读取。

## 5. Testing

- `ApiErrorMappingTest`：逐一构造 `BusinessException`、`SocketTimeoutException`、`HttpException`、`JSONException`、`IOException`、普通异常，断言映射类型与 `cause`。
- `ApiSafeCallTest`：`runBlocking` 调用，覆盖成功、解析抛异常、请求抛异常、`CancellationException` 原样抛出。
- 测试依赖：`junit:junit:4.13.2` 与 `kotlinx-coroutines-core:1.10.2`（仅 `testImplementation`）。项目 Kotlin 2.2.21 可读取 1.10.2 的 metadata。
- 现有 `MessageParserGoldenTest` 不改，作为回归门。

## 6. Compatibility And Rollback

- 无数据传输、存储、路由、界面变化。
- 回滚 = 删除 3 个新文件与 2 个测试文件、还原两个 Repository；无迁移步骤。
- 后续子任务会在同一模型上继续，因此错误类型命名一次定好，避免二次改名。

## 7. Risks

- `ApiError.HttpStatus` 读取 `errorBody` 需要一次性字符串读取，含隐私内容；只保留在内存并交给日志脱敏策略，不落盘、不上报。
- 分类依赖 Retrofit 与 fastjson2 的具体异常类型；升级这两个库时要复核映射测试。
