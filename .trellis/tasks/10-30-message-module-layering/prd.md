# 消息模块按 Compose 分层重构

## Goal

把 `lib_bu_message` 的私信列表、详情、发送三页拆成五层：Activity 只做路由/菜单/FAB 壳，Screen 只做渲染，ViewModel 持有页面状态与分页流，Repository 只发请求，Parser 只做 JSON→模型并返回 `ApiResult`。删除 285 行的 `MessageConvertFactory`，同时保持默认 Compose 与可选 Miuix 两套外观的现有观感和交互。

## Background

- 前置任务：`10-30-api-result-error-model` 必须先完成，本任务直接使用其 `ApiResult` / `apiSafeCall`。
- 现状文件与职责：
  - `compose/MessageListActivity.kt`：路由、菜单、两套 `ContentView` / `MiuixContentView`、行渲染、点击跳转混在一个文件。
  - `compose/MessageRepository.kt`、`detail/MessageDetailRepository.kt`：拼参数、发请求、调解析、判错混在一起。
  - `compose/detail/MessageDetailRepository.kt`：用 object 级可变字段 `recipient`、`msgTitle` 传递页面状态。
  - `compose/post/MessagePostRepository.kt`、`MessagePostModel.kt`：发送页状态与请求耦合。
  - `MessageConvertFactory.java`：285 行，承担列表、详情、发送三类解析，错误通过可变 `errorMsg` 暴露。
  - `compose/MiuixMessagePages.kt`、`compose/post/MiuixMessageComposer.kt`：现有 Miuix 渲染，必须在重构后继续可用。
- 既有 golden 测试 `nga_phone_base_3.0/src/test/java/sp/phone/json/MessageParserGoldenTest.java` 直接依赖 `MessageConvertFactory`，包含列表坐标/顺序、详情楼层与用户拼接、业务拒绝三条断言。
- 上游对应实现可参考 `ddb31bcb`、`c83bde29`、`44ea2d65`，但本 fork 的包名、数据模型、Miuix 组件与请求参数均不同，只能借鉴结构，不能直接搬运。

## Requirements

- R1：三页各自拆分为 Activity、Screen、ViewModel、Repository、Parser 五层，每层只承担单一职责；Activity 不直接调用网络或解析。
- R2：Parser 返回 `ApiResult<T>`；列表、详情、发送三类解析分离到各自 Parser，不再共用一个工厂类。
- R3：删除 `MessageConvertFactory.java`，并移除 `MessageDetailRepository` 的 object 级可变状态；页面标题、收件人等状态归 ViewModel 持有。
- R4：保留两套外观：默认 Compose 与可选 Miuix；视觉、手势、下拉刷新、错误/重试表现与重构前一致。
- R5：请求参数、分页大小（当前 20）、ARouter 路径与跳转参数（如 `mid`、`action`）保持不变。
- R6：账号切换时列表按现有方式刷新；若改为 `StateFlow` 驱动，用户可见行为必须与现在等价。
- R7：把 golden 测试迁移到新 Parser 并保持断言等价；测试可仍在现有测试模块执行。
- R8：失败时保留可读错误（来自前置任务的 `ApiError`），页面仍显示现有错误/重试状态。
- R9：在重构后的 Repository 边界补充失败透传测试，断言 `ApiResult.Failure` 变成携带原因的 `LoadResult.Error`（承接前置任务 `10-30-api-result-error-model` R8 的未覆盖面）。

## Acceptance Criteria

- [ ] `lib_bu_message` 下不再存在 `MessageConvertFactory`，全仓无残留引用。
- [ ] 列表、详情、发送三页的 Activity 文件只包含路由、菜单、FAB、ViewModel 获取与 Screen 调用。
- [ ] 三页的 Parser 均有单元测试；原 golden 测试的三条断言在新实现下等价通过。
- [ ] 默认外观与 Miuix 外观下：列表可翻页与下拉刷新、详情可翻页、发送页可校验并发送，行为与重构前一致。
- [ ] `MessageDetailRepository` 不再有可变全局字段；标题与收件人在 ViewModel 中可追踪。
- [ ] 请求参数与分页大小与重构前逐项一致（以现有源码为基准核对）。
- [ ] 项目现有质量门（单测、lint/编译）通过。

## Technical Notes

- ViewModel 使用 `androidx.lifecycle.ViewModel` + `SavedStateHandle` 承载 `mid` 等参数；分页流用 `cachedIn(viewModelScope)`。
- 默认外观与 Miuix 外观共用同一个 ViewModel 和同一份页面状态，只替换 Screen 实现。
- 不引入上游的 `ui/compose3`、material3 组件。

## Key Decisions

- 失败提示与上游一致：三页统一「加载失败，请重试！」并可点击重试；`ApiError` 分类只用于内部状态与日志（用户确认 2026-10-30）。
- 数据模型继续使用 `lib_core_data` 现有类，不随上游迁移数据类，避免牵连其他模块与 golden 断言。
- 详情回复收件人跟上游：取列表行的 `from_username`，不再使用解析出的 `_Alluser` 参与者列表。单人会话无差异；多人会话回复对象变为该会话的发起人（用户确认 2026-10-30）。

## Out of Scope

- 消息页视觉改版、新增功能（批量操作、草稿持久化等）。
- 其他模块的分层重构。
- 传输层与工具链改动。
