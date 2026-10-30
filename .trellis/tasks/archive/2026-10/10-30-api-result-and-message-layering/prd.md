# 引入 ApiResult 错误模型并重构消息模块分层

## Goal

借鉴上游 Justwen 5.1.x 的网络错误建模和消息模块分层，在本 fork 内引入 `ApiResult` / `ApiError` / `apiSafeCall`，并以此重写私信列表、详情、发送三页。目标是让网络失败可分类、可阅读、可测试，并消除消息模块中 285 行的单体解析类；界面（默认 Compose 与可选 Miuix）保持现有观感。

## Background

- 本 fork 基线为 Justwen `5d807617f8058950f7ea81dda405e38fb0cc37ec`（`SOURCE_LEDGER.md`），分叉后已有 363 个提交，不整体 merge 上游。
- 上游在 `0b536408..36823f74`（5.1.0 / 5.1.1）期间新增 `ApiResult`/`ApiError`/`apiSafeCall`，并把消息列表/详情/发送三页重写为 Activity + Screen + ViewModel + Repository + Parser 五层，删除 `MessageConvertFactory.java`。
- 本 fork 消息模块现状：
  - `lib_bu_message/.../compose/MessageRepository.kt`、`detail/MessageDetailRepository.kt` 自行拼接请求、调用 `MessageConvertFactory`，失败返回 `LoadResult.Error(Exception())`（无错误信息）。
  - `MessageDetailRepository` 用可变的 object 级字段 `recipient` / `msgTitle` 传递页面状态。
  - `MessageConvertFactory.java` 共 285 行，同时承担列表、详情、发送三类解析，并通过可变 `errorMsg` 暴露错误。
  - 现有 golden 测试 `nga_phone_base_3.0/src/test/java/sp/phone/json/MessageParserGoldenTest.java` 直接依赖 `MessageConvertFactory`。
- 现有约束：`.trellis/spec/backend/network-foundation-contract.md` 明确规定 GBK 转换器「I/O 失败返回空串」等是既有遗留缺陷，本任务不得改变传输层行为，也不得复制该缺陷。

## Task Map

| 子任务 | 目录 | 顺序 | 说明 |
| --- | --- | --- | --- |
| 网络层引入 ApiResult 错误模型 | `10-30-api-result-error-model` | 先做 | 基础设施 + 现有消息 Repository 的最小接入 |
| 消息模块按 Compose 分层重构 | `10-30-message-module-layering` | 依赖前者 | 三页分层、删除单体解析类、迁移 golden 测试 |

## Cross-child Acceptance Criteria

- [ ] 两个子任务的单元测试与现有项目质量门全部通过。
- [ ] 私信列表、详情、发送三页在默认外观与 Miuix 外观下均可正常打开、翻页、发送、失败重试。
- [ ] 请求参数、分页大小、ARouter 跳转协议与现有版本一致，无服务端可见行为变化。
- [ ] `MessageConvertFactory.java` 被删除，且没有调用方残留。
- [ ] `SOURCE_LEDGER.md` 记录本次借鉴的上游提交范围与改动性质。

## Out of Scope

- 其他模块（帖子阅读、通知、收藏等）迁移到 `ApiResult`。
- 上游 `ui/compose3/` 的 material3 主题层。
- 消息页面的视觉重设计。
- Kotlin / minSdk / compileSdk 等工具链升级。
- 传输层遗留缺陷修复（GBK 空串、全量日志等）。

## Key Decisions

- 失败提示与上游一致：界面统一「加载失败，请重试！」并可点击重试，分类结果只进入内部错误对象与日志，不做分类文案（用户确认 2026-10-30）。
- 只借鉴上游结构，不整体 merge；包名、数据模型、Miuix 组件沿用本 fork。
