# 帖子详情兼容模式：独立审查

日期：2026-09-12。审查者：`/root/check_thread_compat`。

工作目录：`/home/toph/nga-just-works-compat-mode`；分支：
`feature/thread-detail-compat-mode`；基线：
`5bb92cf033aa32d749d10e1a497bc05173cd2955`。本报告覆盖独立审查修复后的最终工作区；
`check-results.md` 保留实现者交接时的历史结果。本次没有提交、推送或合并。

已读取本任务的 PRD、design、implement、scope revision、执行记录及全部 19 项
`check.jsonl` 上下文。审查覆盖完整产品 diff、新增的 12 个 thread 源文件及相关测试，
沿实际调用检查请求/响应、reader generation、来源切换、PID/楼层定位、原生渲染、
缓存写入/回放/列表/删除与设置默认值。最后一批修复完成后再次核对受影响的调用路径。

## Findings (fixed)

### 1. 前台刷新尚未完成时，重复进入页面会提前恢复旧数据

- File：`nga_phone_base_3.0/src/main/java/sp/phone/mvp/presenter/ArticlePageRequestState.java`、
  `ArticleListPresenter.java`。
- Issue：`NONE` 同时表示“已有 READY 数据”和“仍有前台请求”。Presenter 在
  `NONE && mThreadData != null` 时调用 `showData`，会提前结束刷新并把状态改为 READY，
  后续刷新可能另起请求。
- Fix：增加 `SHOW_READY_DATA`，只有真正 READY 的自动加载可展示保留数据；
  `NONE` 和 `WAIT_FOR_PREFETCH` 继续等待当前请求。补充状态序列单测与 Presenter
  调用边界断言，覆盖已有内容时反复 resume/refresh 的情况。

### 2. BOM 前缀使 HTML/空响应被误判为可触发备用读取的 FORMAT

- File：`nga_phone_base_3.0/src/main/java/sp/phone/mvp/model/thread/ArticleFailure.kt`、
  `../convert/ArticleConvertFactory.java`。
- Issue：带 Unicode BOM 的 HTML 或只有 BOM/空白的响应此前落入 FORMAT，
  从而可能消耗 App 回退预算。
- Fix：`ArticleErrors.bodyText` 仅对解析输入移除一个前导 BOM 和外围空白，
  分别保留 ACCESS/EMPTY 的终止分类；普通 scoped parser 在包装修复前也使用该边界。
  原始 decoded raw 保持原样。`ArticleErrorsTest` 对普通/App parser 及
  `ArticleAttemptPolicy` 验证不触发回退；实际 parser → store → replay 测试验证
  带 BOM 的 raw 完整往返。

### 3. scoped 普通响应的结构化正文会被 Fastjson 转成“完整正文”

- File：`nga_phone_base_3.0/src/main/java/sp/phone/mvp/model/convert/ArticleConvertFactory.java`。
- Issue：例如 `content: {}` 配合有效 subject，会把对象字符串当成可读正文，
  并允许缓存成完整页面。
- Fix：scoped 转换采用独立浅拷贝，校验实际消费的 content/subject/alterinfo 类型，
  不把对象、数组或布尔值转成正文。保留可用身份与可读片段，将损坏 source 标记为
  unavailable；页面完整性递归覆盖已知评论。保留普通接口既有的数值、空字符串、
  subject、alter notice、WP 与 blacklist 处理；默认关闭时的 legacy 接受路径保持独立。
  `NormalArticleParserTest` 覆盖投影和兼容行为，`ArticleOwnedPageCacheTest` 通过
  真实 parser → `ArticlePageCache.prepare` 证明损坏正文不能写成完整缓存。

### 4. 已知嵌套评论需要局部提示，旧回复头裁剪会截字或崩溃

- File：`ArticleConvertFactory.java`、
  `nga_phone_base_3.0/src/main/java/sp/phone/mvp/model/thread/ArticleRowPresentation.kt`、
  `lib_core/src/main/java/gov/anzong/androidnga/core/corebuild/HtmlCommentBuilder.java`。
- Issue：已知父子关系的损坏评论不能让可读主回复整页失败，也不能无提示地留下空评论。
  core 原来的 `substring(indexOf("[/b]") + 4)` 对不带回复头的内容会删除前三个字符，
  短正文还可能越界。
- Fix：`ArticleSourceText.renderBody` 为主回复和嵌套评论提供独立展示输入，
  缺失内容显示提示，始终不改写 `row.content`。已知评论及其身份/父关系被保留；
  `hasCompleteSource` 递归将页面标记为不可完整缓存。core 的包内静态 helper
  `HtmlCommentBuilder.stripReplyHeader(String)` 仅裁剪完整、位于开头的已知 Reply
  标记，保留普通正文、不完整或无关粗体标记；null 安全转为空展示文本。
- Tests：`HtmlCommentBuilderTest.onlyACompleteLeadingReplyHeaderIsRemoved`、
  `plainShortMissingAndIncompleteContentRemainSafeAndUnmodified`；另有普通 parser
  的父子保留断言、source/display 分离单测及嵌套渲染接线检查。此项 core 适配已由
  主代理确认属于 design §5，并纳入 core 单测与 lint。

### 5. 有真实 PID/source 的独立 COMMENT/UNKNOWN 行被一并禁止回复

- File：`ArticleRowPresentation.kt`、
  `nga_phone_base_3.0/src/main/java/sp/phone/ui/adapter/ArticleListAdapter.java`、
  `nga_phone_base_3.0/src/main/java/sp/phone/mvp/presenter/ArticleListPresenter.java`。
- Issue：只以普通 POST kind 为条件，会隐藏有完整回复目标和正文的独立评论/未知类型行的
  回复、引用能力。
- Fix：`ArticleRowPresentation.canReply` 按可用 source、有效 tid 与实际 own PID
  （或可确认的主题首楼）判断。按钮展示、点击与 Presenter 引用采用同一条件。
  缺 UID 仍使用中性署名，不推断未知 `comment_to_id`；贴条、作者过滤、编辑继续使用
  各自必要条件。`ArticleRowPresentationTest` 的 3 项单测及 UI 接线断言覆盖该行为。
  主代理已确认此产品判断并同步规范。

另清理了引用署名抽取后留下的未用局部变量及 converter 的未用 TAG，未改公共接口。

## Findings (not fixed)

没有遗留的本任务产品问题或待定设计判断。全仓库 debug 单测诊断仍有两个未改动模块的
既有 fixture 编译失败；它们不属于本次适配，未加依赖或禁用测试来掩盖失败：

1. `lib_bu_statistics/src/test/java/com/justwen/androidnga/cloud/ExampleUnitTest.java`：
   `:lib_bu_statistics:compileDebugUnitTestJavaWithJavac` 报
   `package org.junit does not exist`。证据：`required-broad-debug-tests.log:297`。
2. `lib_module_debug` 的 ExampleUnitTest KAPT stub：
   `:lib_module_debug:kaptDebugUnitTestKotlin` 报
   `NonExistentClass cannot be converted to Annotation`。证据：同日志 `:349`。

独立审查确认 `git diff 5bb92cf033aa32d749d10e1a497bc05173cd2955 --
lib_bu_statistics lib_module_debug` 为空。这两个失败与项目质量规范中的诊断基线一致。
原历史说明中的 core/base_ui 失败未在本次发生；core 已因本次改动重新执行并通过测试。
全仓库诊断命令仍应标记为失败，不能用可用 XML 的全通过替代该结论。

## Verification

- Lint：**PASS**。最终 app/core lint 通过；逐一解析 settings.gradle 中全部 13 个
  Android 模块的 lint XML，13/13 存在，Error/Fatal 总计 0。
- TypeCheck：**PASS**。最终 Debug Java/Kotlin 编译与 `assembleDebug` 通过；
  core Java 在最新 focused 检查实际编译，最终 invocation 合法复用该结果。
- Tests：**PASS（本任务要求的 app/common/core）**，39 suites / 278 tests，
  0 failures、0 errors、0 skipped。全仓库诊断的两个例外见上。
- `git diff --check`：**PASS**。

### 最终命令与证据

```sh
./gradlew :nga_phone_base_3.0:testDebugUnitTest \
  :lib_base_common:testDebugUnitTest :lib_core:testDebugUnitTest \
  :nga_phone_base_3.0:assembleDebug :nga_phone_base_3.0:lintDebug \
  :lib_core:lintDebug --console=plain
```

`independent-final-check.log`：27 秒通过，563 actionable tasks（36 executed / 527 up-to-date）。
app 单测实际重跑（日志 `:735`）；common 未变，合法复用（`:434`）；core 5 项在最后
focused 中实际执行，最终命令复用（`:481`）。app/core lint analysis 和 report 本轮实际运行。

| 模块 | Suites | Tests | Failure / Error / Skip |
| --- | ---: | ---: | --- |
| nga_phone_base_3.0 | 33 | 211 | 0 / 0 / 0 |
| lib_base_common | 4 | 62 | 0 / 0 / 0 |
| lib_core | 2 | 5 | 0 / 0 / 0 |
| 本次 gate 合计 | 39 | 278 | 0 / 0 / 0 |

`independent-comment-focused-check.log`：最新评论/引用修复后，app 62 + core 5 项实际通过。
`independent-reproduction.log`：修复前 6 tests / 3 个预期失败，确认 BOM 与结构化正文问题。
以上日志路径相对于本任务目录，仅保留在本地。

全模块 lint 的未变部分复用实现者的 `required-all-lint.log`（45 秒，536 tasks 全部执行），
并独立核验最终 13/13 XML 的 Error/Fatal 均为 0。Warnings 与实现者快照一致
（app 726、core 8），未 suppress。其他模块单测也复用未变结果；全部可用 XML 是
11 模块 / 49 suites / 294 tests，全零 failure/error/skip。两个编译失败模块没有 XML，
不能视为“0 测试通过”，也不能将全仓库诊断写为通过。

### 规范、验证边界与交接

主代理已同步 compat/cache/prefetch/operation registry/frontend 规范；审查复核
SHOW_READY_DATA、renderBody、canReply、stripReplyHeader 及递归 completeness
契约与代码一致。没有平台配置或生成模板变化。

测试使用合成数据、fake calls、JVM 逻辑和 Android 源码接线断言，未实际验证设备上的
生命周期/WebView。本次没有 NGA 访问、真实账号存储读取、设备/ADB/instrumentation、
release/preview 签名或发布操作；它们不是本次默认门禁。

没有未解决的 in-scope 问题。产品/测试保持冻结；Gradle 与产品代码所有权交回主代理，
后续文档和提交计划由主代理收尾。
