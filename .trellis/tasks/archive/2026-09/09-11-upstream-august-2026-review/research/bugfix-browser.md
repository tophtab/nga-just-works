# Research: 8 月零散修复与帖子详情兼容模式

- Query: Justwen 的 `750871b3`、`2becba2a` 实际修了什么；除图床外，当前 fork 哪些值得独立移植？
- Scope: mixed；以补丁、最终上游源码、当前工作区和离线 JSON 实验为依据，不访问 NGA。
- Date: 2026-09-11
- 上游修复提交：`750871b31b533dd74059ade146a084f396020cbe`，作者时间 2026-08-26 12:27:32 +08:00，提交时间 2026-08-29 22:27:02 +08:00。
- 上游兼容模式提交：`2becba2acc3f6c85340424cd09bb03fa7d759db0`，作者时间 2026-08-30 09:54:06 +08:00，提交时间 2026-08-30 22:52:31 +08:00。
- 固定比较对象：当前 `main@8284c703`；共同上游基线 `5d807617f8058950f7ea81dda405e38fb0cc37ec`。
- 引用约定：下文 `U:path:line` 指 `22ba3082501bcbb08f52a66d787f970f59c2dda7` 的仓库原路径及行号；`F:path:line` 指当前 `8284c703`。补丁归属由上述两个完整提交核对，避免将后续修复误归到先前数据重构。

## Findings

### 结论与采纳顺序

最值得独立考虑的是视频尺寸约束、特定相对视频标签支持、缓存页缺参保护，以及停止无意义的单账号重试。缓存保护和账号检查都只能借鉴意图，上游判断位置有缺陷。帖子详情备用 API 的潜在用户收益更大，但它是默认关闭的实验链路，尚不能作为直接移植的小补丁。

| 顺序 | 建议 | 当前覆盖 | 成本与依赖 |
| --- | --- | --- | --- |
| 1 | 给帖子内视频加 `max-width: 100%` 等布局约束 | 当前 CSS 无 `video` 规则 | 低；独立 CSS 改动，离线大尺寸视频页面即可验证 |
| 1 | 对缺失 `ArticleListParam` 做正常退出保护 | 当前会先解引用空参数 | 低；需保留 `super.onCreate` 生命周期，不能原样复制上游位置 |
| 1 | 同账号不重复请求，空账号先检查再取 Cookie | 当前没有数量保护；上游也未完整保护零账号 | 低至中；限定既有读取错误链，验证 0/1/2 账号和前后台行为，不扩大自动换账号 |
| 2 | 支持 `[flash]./相对附件路径[/flash]`；保留现有 `NgaImageHost` 前缀 | 当前只有 `[flash=video]` 与绝对链接型 `[flash]` | 低至中；需限制匹配范围，不照搬会吞首字符的通配正则 |
| 2 | 支持已经含 `<b>Reply to …</b>` 的引用头 | 当前 `[pid]` 链接已有，额外引用框转换没有 | 低；主要服务另一种正文表示，独立验证 BBCode 与 HTML 两种形式 |
| 条件优先 | 设计显式、可关闭、保留本地能力的备用帖子读取适配层 | 当前没有 `post/list` App API 和兼容模式开关 | 中至高；需响应夹具、错误分类、账号绑定、分页/作者过滤/缓存/图片/预取适配后再决定 |
| 无需独立移植 | 黑名单渲染时序、旧库上传无引号字段兼容 | 当前实现已覆盖对应行为 | 上游修的是数据/JSON 库迁移后的问题 |
| 不建议单独移植 | 板块刷新时间 key 加 `_v2`、整类 Kotlin 重写、目录里的库版本调整 | 不是新的用户功能 | 仅在确有格式迁移或依赖升级时处理 |

### `750871b3`：逐项拆开“解决一些 bug”

#### 1. 视频宽度和无类型视频标签

`U:lib_core/src/main/assets/html/style.css:49` 新增 `video { width: auto; max-width: 100%; height: auto; display: block; }`，目标是限制原生帖子 WebView 里的视频宽度，避免视频天然尺寸撑开正文。当前 `F:lib_core/src/main/assets/html/style.css:42` 仅限制图片；`F:lib_core/src/main/assets/html/html_template.html:7` 确认该公共 CSS 被帖子 HTML 加载。此规则是明确、独立的移植候选，源码未证明播放兼容性或横竖屏效果。

同提交把 `ForumBasicDecoder.java` 改写为 Kotlin。绝大多数 BBCode 替换只是搬写，实质媒体变化是：

- `[flash=video]./…[/flash]` 的视频地址由硬编码 `img.ngacn.cc` 改成 `HtmlData.attachmentHost`，仍自行拼 `http://<host>/attachments…`。
- 新增 `[flash]./…[/flash]` → `<video controls>`。此前无类型标签仅对 `http…` 绝对链接输出可点击 Flash 图标；这个旧规则仍先执行，所以新增规则并没有把所有绝对链接都改为内嵌视频。
- `[flash=audio]` 仍硬编码 `http://img.ngacn.cc/attachments…`，没有一起修。

证据：`U:lib_core/src/main/java/gov/anzong/androidnga/core/decode/ForumBasicDecoder.kt:199`、`:370`、`:377`、`:384`。这些媒体变化属于 `750871b3`；同文件的 `<b>Reply to…</b>` 新规则属于后面的 `2becba2a`。

当前 `F:lib_core/src/main/java/gov/anzong/androidnga/core/decode/ForumBasicDecoder.java:237` 已让视频和音频都使用完整 `attachmentsPrefix`，因此视频图床部分已被更全面覆盖。缺口是新无类型相对视频标签和 CSS。不能整类覆盖：那会丢掉当前协议、手动图床优先级及音频修复。上游正则中的 `.` 是任意一个字符，并非转义的字面点；对不符合 `./…` 约定的输入可能吞掉首字符，移植时需要明确格式夹具。

#### 2. 黑名单必须在生成 HTML 前计算

`750871b3` 把 `postInfo.isBlocked = …` 从 `HtmlConvertFactory.convert` 后移到前面，修复新数据链已判断屏蔽、但 HTML 仍显示正文的问题。

证据：`U:lib_core/src/main/java/com/client/androidnga/core/parse/ThreadInfoParse.kt:86`；`U:lib_core/src/main/java/gov/anzong/androidnga/core/HtmlConvertFactory.java:38`、`:68` 在转换时决定是否输出 `[屏蔽]`。

当前 fork 无此时序缺口：`F:nga_phone_base_3.0/src/main/java/sp/phone/mvp/model/convert/ArticleConvertFactory.java:134` 至 `:139` 先填用户信息再生成正文，`:274` 设置黑名单，`:167` 传给 `HtmlData`。这是“当前旧实现已覆盖”的迁移回归修补，不应再安排一次功能移植。

#### 3. 缓存帖子 Activity 缺参数保护

`U:nga_phone_base_3.0/src/main/java/gov/anzong/androidnga/activity/ArticleCacheActivity.java:37` 在参数为空时 `finish(); return;`，避免随后的 `mRequestParam.title/tid` 空指针。当前 `F:nga_phone_base_3.0/src/main/java/gov/anzong/androidnga/activity/ArticleCacheActivity.java:36` 取参数后直接解引用，缺少保护。

但上游空参分支位于 `super.onCreate(savedInstanceState)` 之前。Android Activity 的 `onCreate` 必须调用父类实现；因此该分支有 `SuperNotCalledException` 风险，不能把原样补丁称为已经正确解决崩溃。建议移植“校验参数并正常退出”的意图，调整到满足 Activity 生命周期的位置。该风险来自源码与 Android 生命周期契约，未在设备上触发验证。

#### 4. 板块远程刷新时间戳换 key

`U:nga_phone_base_3.0/src/main/java/gov/anzong/androidnga/activity/compose/board/ForumBoardViewModel.kt:25` 将 `board_remote_request_time` 改为 `board_remote_request_time_v2`。调用链是打开板块后调用 `requestRemoteBoardList`，读取时间戳并执行一天节流；新 key 第一次缺省为 0，允许下一次打开板块时重新取增量板块，随后仍按一天节流（同文件 `:82`、`:85`、`:100`）。这不是新的板块服务或长期刷新算法。

当前 `F:nga_phone_base_3.0/src/main/java/gov/anzong/androidnga/activity/compose/board/ForumBoardViewModel.kt:28` 使用旧 key，并保留同样节流（`:197`）；`F:nga_phone_base_3.0/src/main/java/gov/anzong/androidnga/activity/compose/board/ForumBoardRepository.kt:56` 已在本地数据版本变化时重置远程时间。没有明确格式迁移需求时无需照搬 `_v2`：其直接效果只是一次额外刷新，源码也没有说明此处换 key 的具体服务端故障。此次调研没有触发任何刷新请求。

#### 5. 上传响应接受无引号字段名

`U:nga_phone_base_3.0/src/main/java/sp/phone/mvp/model/TopicPostModel.java:225` 在剥去 `window.script_muti_get_var_store=` 后，使用 `JSON.parseObject(s, JSONReader.Feature.AllowUnQuotedFieldNames)`。它影响附件上传响应解码；压缩重传、`error_code=9` 判断和 `attachments`/`attachments_check` 处理保持原有逻辑。

当前 `F:nga_phone_base_3.0/src/main/java/sp/phone/mvp/model/TopicPostModel.java:223` 使用 fastjson 1 的默认 `JSON.parseObject`；版本来源 `F:build.gradle:10` 为 `1.1.71.android`，`F:lib_base_common/build.gradle:58` 导出该依赖。

主会话用本机缓存 JAR 运行纯合成离线实验，结果为：

| 合成输入 | fastjson 1.1.71.android 默认 | fastjson2 2.0.59.android8 默认 | fastjson2 显式 AllowUnQuotedFieldNames |
| --- | --- | --- | --- |
| 标准、有引号 JSON | 通过 | 通过 | 通过 |
| `{data:{url:"https://example.invalid/a"}}` | 通过 | `JSONException` | 通过 |

因此，这项是 fastjson2 迁移后的兼容性修补，当前 fork 对这类输入已兼容。若未来升级 JSON 库，必须把这个行为作为迁移夹具，而非此刻单独改上传库或上传流程。实验只证明特定语法容忍，不能证明真实上传可用或所有异常响应可安全处理。复现证据见同任务 [json-compatibility.md](json-compatibility.md) 和 `research/probes/`，由主会话保存。

### `2becba2a`：增加另一个帖子响应适配器，降低自动转 WebView 的机会

#### 1. 变化范围与默认设置

新增实验室“帖子详情兼容模式”开关 `pref_show_with_app_api`，默认 `false`；既有自动 WebView 开关默认 `true`。XML 与 Presenter 的缺省值一致。证据：`U:nga_phone_base_3.0/src/main/res/xml/settings_lab.xml:4`、`:32`；`U:lib_base_common/src/main/res/values/donottranslate.xml:7`；`U:nga_phone_base_3.0/src/main/java/sp/phone/mvp/presenter/ArticleListPresenter.java:62`。

所以升级后的默认行为仍是原有读取失败时可能转内置浏览器，用户打开新开关才会尝试备用接口。当前 fork 的 `F:nga_phone_base_3.0/src/main/res/xml/settings_lab.xml:4` 只有既有 WebView 设置，没有此开关或备用读取方法。

这两个提交没有改普通 `/read.php` 的 URL 契约。最终上游仍先请求 `GET /read.php?&page=<page>&__output=8&noprefix&v2`，按需追加 `tid`、`pid`、`authorid`（`U:nga_phone_base_3.0/src/main/java/sp/phone/mvp/model/ArticleListModel.java:54`）；当前相同请求见 `F:nga_phone_base_3.0/src/main/java/sp/phone/mvp/model/ArticleListModel.java:49`。先前 `ThreadInfoParse` 数据重构及其他响应修补应另计，不应因为本提交标题就统称为“修复 read.php”。

#### 2. 完整前台调用链

```text
页面 Presenter.loadPage
  ├─ 此 Presenter 尚未成功使用兼容模式
  │    └─ read.php GET → ThreadInfoParse → ThreadInfo
  │         ├─ 正常数据：原生帖子列表
  │         ├─ 识别出的站点错误 / 网络等非 ServerException：显示错误
  │         └─ 解析无结果且识别不出站点错误：ServerException
  │              └─ 既有 next-account read.php 重试（最多追加一次）
  │                   └─ 仍是 ServerException 或未能重试
  │                        ├─ app 开关开：App API POST → ThreadInfoAppParse
  │                        │    ├─ 成功：原生列表，当前 Presenter 进入兼容模式
  │                        │    └─ 任何错误：显示错误；WebView 开关开则转 WebView
  │                        ├─ 仅 WebView 开关开：显示错误并转 WebView
  │                        └─ 两开关都关：当前实现漏掉错误收尾
  └─ 此 Presenter 已成功使用兼容模式：直接走 App API
```

证据：`U:nga_phone_base_3.0/src/main/java/sp/phone/mvp/model/ArticleListModel.java:121` 至 `:158`；`U:nga_phone_base_3.0/src/main/java/sp/phone/mvp/presenter/ArticleListPresenter.java:49` 至 `:153`。重试次序不能省略：它先经过既有另账号重试，然后才有兼容 API。新 API 非 `ServerException` 的网络错误也会经过其专门回调尝试 WebView，而普通 `read.php` 的非 `ServerException` 错误只显示提示。

`mUserCompatMode` 属于每个 `ArticleListPresenter`（`:47`），成功时设置并弹出“使用兼容模式打开”（`:115`）；后续该 Presenter 直接请求 App API，未见自动回切或重新检查关闭开关的逻辑。`U:nga_phone_base_3.0/src/main/java/sp/phone/ui/fragment/ArticleListFragment.java:254` 每个页面创建自身 Presenter，所以这不是整个话题或全局长期共享的兼容状态。

所谓“浏览器”首先是 App 的 `ForumWebFragment`：Presenter 路由到 Fragment 容器后关闭原帖子页面（`U:nga_phone_base_3.0/src/main/java/sp/phone/mvp/presenter/ArticleListPresenter.java:155`）。它不是在这里直接启动系统默认浏览器。`U:nga_phone_base_3.0/src/main/java/gov/anzong/androidnga/activity/fragment/ForumWebFragment.kt:28` 另有用户点击菜单才调用 `ACTION_VIEW` 的外部浏览器入口。

#### 3. 新接口、模型、解析器和伴随改动

| 改动 | 实际作用及证据 |
| --- | --- |
| `RetrofitService` 新 POST 重载 | 接受动态 URL、HeaderMap、FieldMap；`U:lib_base_network/src/main/java/com/justwen/androidnga/base/network/retrofit/RetrofitService.java:76`。原有公共网络栈继续使用 |
| `ArticleListModel.loadPageWithAppApi` | `POST <可选域名>/app_api.php?__lib=post&__act=list`；表单传 `page`，非零时传 `tid`、`pid`、`authorid`；后台解析，主线程投递，仍绑定 `FragmentEvent.DETACH`；`U:nga_phone_base_3.0/src/main/java/sp/phone/mvp/model/ArticleListModel.java:80` |
| `ThreadAppBean` | 新响应是顶层 `result` 数组、`vrows`、`attachPrefix`、`tauthorid` 等，与 `read.php` 的 `data.__R/__U/__T/__ROWS` 是两套表示；`U:lib_core_data/src/main/java/com/client/androidnga/core/data/bean/ThreadAppBean.kt:5` |
| `ThreadInfoAppParse` | 将新表示变为已有 `ThreadInfo/ThreadPostInfo`，调用同一 HTML 渲染器，再交给原生列表；`U:lib_core/src/main/java/com/client/androidnga/core/parse/ThreadInfoAppParse.kt:25`、`:45`、`:60` |
| 旧 parser 的工具抽取 | 匿名用户名解码及客户端类型判断抽到 `ThreadParseUtils`；`ThreadInfoParse` 改调用点，算法本身基本保持。新 App parser只复用客户端判断，未调用匿名名解码；`U:lib_core/src/main/java/com/client/androidnga/core/parse/ThreadParseUtils.kt:7`、`:27`，`U:lib_core/src/main/java/com/client/androidnga/core/parse/ThreadInfoParse.kt:209`、`:225` |
| HTML 引用头 | `ForumBasicDecoder.kt:62` 新增已经是 `<b>Reply to [pid=…]…</b>` 时的引用框转换。原先 `[b]…[/b]` 路径仍在；当前 fork 只有后者和通用 `[pid]` 链接转换 |
| 依赖声明 | `U:lib_core/build.gradle:44` 新增 Kotlin reflect；`U:gradle/libs.versions.toml:5` 把 fastjson2-kotlin 的声明版本从 `2.0.59.android8` 改为 `2.0.21.android`，核心 fastjson2 仍为 `2.0.59.android8`。这是两个不同 artifact 的版本，且导出的构建文件未见消费该 Kotlin 扩展别名，不能描述成整个 JSON 核心库降级或已启用扩展 |
| 测试 | 新增 `ThreadInfoAppParseTest` 从 `tem.json` 读取后打印结果，没有断言；删除旧 `ExampleUnitTest.java` 的示例测试。见 `U:lib_core/src/test/java/com/client/androidnga/core/parse/ThreadInfoAppParseTest.kt:7`。主会话已检查最终上游完整受跟踪源码树，确认没有 `tem.json` 或 `src/test/resources/` 条目；此测试依赖未跟踪夹具，不能作为可复现的通过证据 |

此实现是在 App 内增加备用数据读取和转换，目标是在普通详情格式失败时保住原生阅读体验。它没有修复 `ForumWebFragment` 的导航、浏览器重定向或 NGA 服务本身。

#### 4. 不宜原样移植的已见风险

| 领域 | 可核实事实 | 移植含义 |
| --- | --- | --- |
| 关闭回退后的界面收尾 | `ArticleCallback.onError` 的 ServerException 分支只在两个开关至少开一个时继续处理；都关时不调用 `hideLoadingView/setRefreshing(false)/showToast`（`U:nga_phone_base_3.0/src/main/java/sp/phone/mvp/presenter/ArticleListPresenter.java:60`） | 明确的遗漏分支，静态上可能让加载状态不结束；当前 fork 在检查 WebView 设置前先统一错误收尾（`F:nga_phone_base_3.0/src/main/java/sp/phone/mvp/presenter/ArticleListPresenter.java:64`） |
| 单账号 / 空账号 | 新增 `userCount < 2`，但在判断前已经取了 `getNextCookie()`（`U:nga_phone_base_3.0/src/main/java/sp/phone/mvp/presenter/ArticleListPresenter.java:134`）；`UserManager.kt:69` 零账号返回索引 -1，`:146` 直接索引列表 | 可免除单账号重复请求，仍有零账号越界风险；应该先判断数量，而非复制原顺序 |
| 账号一致性 | 另账号重试把 Cookie 放在局部 HeaderMap（`:139`）；App API 回退使用成员 `mHeaderMap`（`:63`），该 Map 初始为空 | 新链路不继承刚才重试账号；又回到拦截器执行时的活动账号。不能宣称具有请求级账号绑定 |
| 错误响应 | App parser直接反序列化，无 `code/msg` 成功判定、无 challenge/rate limit 分型；`parsePageInfo` 直接取 `result[0]`（`U:lib_core/src/main/java/com/client/androidnga/core/parse/ThreadInfoAppParse.kt:25`、`:90`） | 空数组、缺失数据、错误对象和不匹配结构可能异常；不能以有 JSON/HTTP 200 推断业务成功。至少需要合法空页和业务错误夹具 |
| 分页元数据 | Bean 有 `currentPage/perPage/totalPage`，parser只取 `vrows`；UI 仍按 `ceil(totalRows / 20.0)` 分页，楼层跳转也按 20（`U:lib_core_data/src/main/java/com/client/androidnga/core/data/bean/ThreadAppBean.kt:11`、`:27`、`:37`；`U:nga_phone_base_3.0/src/main/java/sp/phone/ui/fragment/ArticleTabFragment.java:88`、`:308`） | `page` 参数确实发送了，但完整分页正确性取决于新接口是否正好遵守旧 20 行假设；没有 live/fixture 证据支持该假设 |
| 只看某作者 / PID 跳转 | API 表单保留 `authorid` 和 `pid`；parser未恢复 `ThreadPageInfo.page/pid/position/replies`，只填标题、作者、板块及首行 tid（`U:nga_phone_base_3.0/src/main/java/sp/phone/mvp/model/ArticleListModel.java:83`；`U:lib_core/src/main/java/com/client/androidnga/core/parse/ThreadInfoAppParse.kt:90`） | 不能把“参数传了”写成“只看楼主、定位、分页已验证”。需验证筛选后的总数、楼层和页码含义 |
| 热门回复、贴条、附件和评分 | Bean 声明 `hot_post/attches/comment_to_id/isTieTiao/vote_good/vote_bad`，parser没有将它们映射为热评、comments、attachInfo、isComment、score（`U:lib_core_data/src/main/java/com/client/androidnga/core/data/bean/ThreadAppBean.kt:19`、`:104`、`:108`、`:118`、`:138`；`U:lib_core/src/main/java/com/client/androidnga/core/parse/ThreadInfoAppParse.kt:45`） | 可能只保留正文基本信息。部分服务端内容也许已内联进 content，但源码和本次证据不足以确认，不能宣称无损 |
| 离线缓存 | App parser把其 JSON 放进 `rawData`（`:27`）；Presenter照常缓存；`loadCachePage` 只调用 `getThreadInfo → ThreadInfoParse`，无格式标识、无 App parser分支（`U:nga_phone_base_3.0/src/main/java/sp/phone/mvp/model/ArticleListModel.java:162`、`:185`、`:212`） | 缓存 App API 成功页后重开，会遇到错误 parser；这是格式级不匹配，移植要存类型或做明确离线适配 |
| 图片上下文 | App parser将 `attachPrefix` 按 `/` 切分后只取首段（`U:lib_core/src/main/java/com/client/androidnga/core/parse/ThreadInfoAppParse.kt:101`）；HtmlConvertFactory把它当裸 attachmentHost（`U:lib_core/src/main/java/gov/anzong/androidnga/core/HtmlConvertFactory.java:33`） | 如果响应是完整 `https://…`，首段为 `https:`；空值、`//host` 等也无当前规范中的归一化和固定回退。必须接入现有 `NgaImageHost`，保留手动选择优先级和页面隔离 |
| 用户显示 | 直接使用 `author.avatar/username`；匿名判断看 `annoy`，没有使用已抽取的匿名名转换；禁言只查 buff 105（`U:lib_core/src/main/java/com/client/androidnga/core/parse/ThreadInfoAppParse.kt:74`） | 字段含义未实证；需要与当前头像归一化、匿名显示及禁言规则对齐 |
| 预取 | 上游 Presenter没有当前 fork 的 `ArticlePageRequestState/PrefetchCallback` | 整体替换会丢失当前两页预取、去重、前后台提升/降级。不能让预取失败走兼容 API/换账号/浏览器副作用 |
| WebView 兜底上下文 | `getCurrentUrl` 只保留 tid 或 pid，无 page/authorid（`U:nga_phone_base_3.0/src/main/java/sp/phone/mvp/presenter/ArticleListPresenter.java:164`） | 即使最终仍能打开浏览器，也不保证保留当前页和作者过滤。此旧缺陷当前 fork 也存在（`F:nga_phone_base_3.0/src/main/java/sp/phone/mvp/presenter/ArticleListPresenter.java:210`） |

账号边界证据还包括 `U:lib_base_network/src/main/java/com/justwen/androidnga/base/network/retrofit/RetrofitHelper.java:108`：没有显式 Cookie 才查询全局活动账号；原有浏览器 UA 与 `X-User-Agent: Nga_Official` 继续使用。`U:lib_base_network/src/main/java/com/justwen/androidnga/base/network/retrofit/converter/JsonStringConvertFactory.java:38` 仍统一按 GBK 读取所有字符串并输出原始正文日志。新增 App 请求继承这些旧行为，没有建立新的编码、重定向、脱敏或身份契约。当前规范明确禁止把这些历史缺陷复制到新协议；这里列的是新适配层的实际依赖，不是建议扩展任务范围。

#### 5. 当前 fork 必须保留的能力与最低验证条件

当前仍使用 `ArticleConvertFactory → ThreadData/ThreadRowInfo`，不依赖上游新 `ThreadInfo` 全链。它明确构建热门回复、贴条、附件和图片列表（`F:nga_phone_base_3.0/src/main/java/sp/phone/mvp/model/convert/ArticleConvertFactory.java:134`、`:178`、`:211`、`:226`）；页面图床归一化入口在 `:104`，完整前缀经 `HtmlData` 传递。兼容 API 可以适配到现有模型，并不要求先整体搬入上游数据重构。

当前预取证据是 `F:nga_phone_base_3.0/src/main/java/sp/phone/mvp/presenter/ArticleListPresenter.java:100`、`:157`、`:176`、`:190`：后台失败静默，页面进入时复用同一请求，只有仍处前台时才恢复既有错误链。未来如果允许备用读取，需要作为明确前台策略接入，并保留这些约束。

建议把以下作为未来移植的验收输入，而非本轮已完成测试：

- 本地响应夹具覆盖正常页、空页、最后一页、`pid` 定位、`authorid` 筛选、非 20 的 `perPage`、错误 code/msg、HTML challenge、无效 JSON。
- 明确 API 响应的正文是原始 BBCode、已部分 HTML 化还是包含内嵌热评/附件；对热评、贴条、评分、引用、匿名用户、头像逐项核对。
- 新旧缓存格式分别识别；两种 parser只接受自己的结构，解析失败不得伪装空成功。
- 四种开关组合、0/1/2 账号、请求中切账号、页面销毁、前台/后台预取失败都得到可预期的收尾；不新增自动轮换身份。
- 所有媒体使用当前页面的完整前缀；自动/手动图床、缺失字段、非法字段、两页并发均保留当前行为。
- 先离线验证；真实服务可用性只有在另行授权的有限验证后才能下结论。本轮没有实施该验证。

### Files found

| 文件组（仓库原路径） | 作用 |
| --- | --- |
| `lib_core/src/main/assets/html/style.css`、`html_template.html` | 帖子 WebView 公共样式与加载入口 |
| `lib_core/src/main/java/gov/anzong/androidnga/core/decode/ForumBasicDecoder.{java,kt}` | BBCode、引用、视频与音频转换；Java为当前版本，Kotlin为最终上游 |
| `lib_core/src/main/java/com/client/androidnga/core/parse/{ThreadInfoParse,ThreadInfoAppParse,ThreadParseUtils}.kt` | 上游普通接口、备用接口与共用字段转换 |
| `lib_core_data/src/main/java/com/client/androidnga/core/data/bean/ThreadAppBean.kt` | 新 API 响应模型 |
| `lib_core_data/src/main/java/com/client/androidnga/core/data/model/{ThreadInfo,ThreadPageInfo}.kt` | 上游页面、楼层、渲染中间模型；`ThreadBasicInfo/ThreadPostInfo` 在 `ThreadInfo.kt` 中声明 |
| `lib_core/src/main/java/gov/anzong/androidnga/core/HtmlConvertFactory.java` | 中间模型转 HTML，读取屏蔽、附件、评论、图床上下文 |
| `nga_phone_base_3.0/src/main/java/sp/phone/mvp/{model/ArticleListModel,presenter/ArticleListPresenter}.java` | 普通请求、备用请求、账号重试、浏览器兜底、缓存、当前 fork 预取 |
| `nga_phone_base_3.0/src/main/java/sp/phone/mvp/model/convert/ArticleConvertFactory.java` | 当前 fork 详情解析与渲染上下文 |
| `nga_phone_base_3.0/src/main/java/sp/phone/ui/fragment/{ArticleListFragment,ArticleTabFragment}.java` | 每页 Presenter、数据投递、页数与楼层跳转 |
| `nga_phone_base_3.0/src/main/java/gov/anzong/androidnga/activity/{ArticleCacheActivity.java,fragment/ForumWebFragment.kt}` | 缓存入口生命周期、内置/外部浏览器边界 |
| `nga_phone_base_3.0/src/main/java/gov/anzong/androidnga/activity/compose/board/{ForumBoardViewModel,ForumBoardRepository}.kt` | 板块刷新节流与本地版本迁移 |
| `nga_phone_base_3.0/src/main/java/sp/phone/mvp/model/TopicPostModel.java` | 上传响应语法兼容 |
| `lib_base_network/src/main/java/com/justwen/androidnga/base/network/retrofit/{RetrofitService,RetrofitHelper}.java`、`converter/JsonStringConvertFactory.java` | POST 重载、请求身份、GBK 转换 |
| `lib_bu_account/src/main/java/com/justwent/androidnga/bu/UserManager.kt`、`nga_phone_base_3.0/src/main/java/sp/phone/common/UserManagerImpl.java` | 活动账号和 next-account Cookie |
| `nga_phone_base_3.0/src/main/res/xml/settings_lab.xml`、`lib_base_common/src/main/res/values/donottranslate.xml` | 实验室开关与默认值 |
| `gradle/libs.versions.toml`、`lib_core/build.gradle` | 备用 Kotlin Bean 解析相关依赖声明 |
| `lib_core/src/test/java/com/client/androidnga/core/parse/ThreadInfoAppParseTest.kt` | 新增读取/打印示例测试；未提供行为断言 |

### External references / versions

- [Justwen `750871b3` 完整提交](https://github.com/Justwen/NGA-CLIENT-VER-OPEN-SOURCE/commit/750871b31b533dd74059ade146a084f396020cbe)。本轮读取主会话已导出的完整补丁。
- [Justwen `2becba2a` 完整提交](https://github.com/Justwen/NGA-CLIENT-VER-OPEN-SOURCE/commit/2becba2acc3f6c85340424cd09bb03fa7d759db0)。本轮读取主会话已导出的完整补丁。
- [最终上游源码树](https://github.com/Justwen/NGA-CLIENT-VER-OPEN-SOURCE/tree/22ba3082501bcbb08f52a66d787f970f59c2dda7)。上文 `U:` 引用均固定在此 SHA；当前文件引用固定为 `8284c703`，不是移动中的远端分支。
- [Android Activity.onCreate 契约](https://developer.android.com/reference/android/app/Activity#onCreate(android.os.Bundle))：子类必须调用父类实现；本次未进行设备验证。
- JSON 行为比较使用缓存的 `com.alibaba:fastjson:1.1.71.android` 与 `com.alibaba.fastjson2:fastjson2:2.0.59.android8`；合成探针和结果见 [json-compatibility.md](json-compatibility.md) 与 `research/probes/`。未将 NGA 真实响应、账号或上传数据用于夹具。

### Related specs

- `.trellis/workflow.md`：本轮为研究，成果仅保存在当前任务；不进入产品实现。
- `.trellis/spec/backend/network-foundation-contract.md`：固定基线、请求身份、Cookie、编码、浏览器边界、错误/缓存/脱敏；不将源码观察等同于官方或当前可用 API。
- `.trellis/spec/backend/nga-platform-access-rules.md`：`THREAD.PAGE` 页面级图片上下文、手动图床优先级、禁止隐式换账号和 live 流量边界。
- `.trellis/spec/backend/thread-page-prefetch-contract.md`：当前普通帖子预取必须保持既有接口、后台静默、去重、前台提升/降级及 DETACH 取消。

## Caveats / Not Found

- `post/list` 是从源码发现的备用读取路径，不代表官方、稳定、当前仍可用或对所有账号可用。没有请求真实 NGA、读取账号、构建 APK 或设备测试。
- 错误分支遗漏、缓存 parser不匹配、字段未映射和参数传递是源码事实；实际触发频率、服务端字段语义、分页规格及内联内容是否补足缺字段尚未验证。
- Kotlin Bean 反射、缺字段/null 的具体抛错方式、各 JSON 错误对象如何实例化，本轮未运行 App parser完整 harness，不把潜在异常写成已复现的线上故障。
- 新测试缺少断言且依赖未跟踪的 `tem.json`，资源缺失由主会话的完整受跟踪源码树检查确认，不是从部分导出目录推测。它还以默认 `null` config 调用 parser，而正常楼层会进入 `HtmlConvertFactory.java:37` 的 `config.isDarkMode()`；即便另补资源，也需要提供有效配置才能测试完整渲染。
- 前置数据重构的旧接口循环边界、附件赋值时机和热评模型缺失由同任务的数据重构报告负责；本文只在说明备用链路移植成本时引用，不重复归因给 `2becba2a`。
- `750871b3` 的 video host 部分当前已被更完整实现覆盖，但无类型视频标签和 CSS 尚未覆盖；“图床已处理”不能自动推导“该提交已全部处理”。
