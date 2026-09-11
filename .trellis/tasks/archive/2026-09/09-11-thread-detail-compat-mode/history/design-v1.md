# Design — 帖子详情兼容模式

> 已过期：本文是修订前方案，保留作决策历史，不可据此实现。用户最新“尽可能复用上游”要求见 scope-revision.md 与新版 prd.md；下文的 full-thread-only、固定20楼和非空字段整页拒绝规则已撤回。新版详细设计正在补充。

## Boundary and source

复用 Justwen `2becba2acc3f6c85340424cd09bb03fa7d759db0`，基线为包含缓存修复的 `5bb92cf0`。最小行为缺口在普通解析失败与 Presenter 前台回退之间；本任务同时适配下游模型、渲染和缓存，不整体引入 `ThreadInfo/ThreadPostInfo` 数据体系。

本设计是已选方案，优先于 research 中的备选项。字段映射和证据分别见 [parser-render-adaptation.md](../research/parser-render-adaptation.md)、[pagination-cache-adaptation.md](../research/pagination-cache-adaptation.md)、[foreground-network-adaptation.md](../research/foreground-network-adaptation.md)；可复用源文见 [upstream-source.md](../research/upstream-source.md)。

## 1. Structure

```text
foreground request + settings/account/parameter snapshot
  → read.php wire + existing parser
    → accepted page → existing native reader
    → eligible format failure (current + foreground + enabled + full thread)
      → one same-origin, same-account App request
      → bytes/status/charset/error classification
      → upstream-derived DTO + whole-page validation
      → ThreadData/ThreadRowInfo + shared renderer
      → native reader → explicit save → owner-scoped versioned cache
    → terminal error → finish loading; eligible errors may open internal WebView

cache open → current-owner + legacy index → selected file/format dispatch
  → same parser/adapter → current reader (no network)
```

- 新 DTO、纯校验/归一化、request policy、cache codec/store 和 client 优先 Kotlin；现有 Java 模型、MVP、Fragment 通过窄接口继续使用。
- 这是旧 reader 的功能适配，不是 Compose/MVVM 迁移切片。现有 RxLifecycle facade 是 Java MVP 边缘的互操作例外；沿用 IO/主线程及 DETACH 取消，不同时改为 Flow 或新增一套页面状态。
- 使用当前 fastjson1 DOM 显式检查类型/字段存在性，再构建 Kotlin DTO；不升级 JSON 或添加 Kotlin reflect 来反射上游构造器。
- 纯 adapter 接受小的 blacklist/render seams；测试不初始化 Android 资产或真实账号。新 payload 类避免带原文的自动 `toString()`。

## 2. Foreground flow and UI

设置使用 app `settings_lab.xml`、app strings 和 common `donottranslate.xml`；key 为 `pref_show_with_app_api`，默认 false，标题「帖子详情兼容模式」。摘要说明普通方式失败后尝试兼容显示，失败仍受内置浏览器设置控制。

| Compatibility | WebView | 合格普通格式失败 |
| --- | --- | --- |
| off | off | 旧前台重试链，经 count/empty/equal Cookie 保护后结束并提示 |
| off | on | 同上，最终按旧行为打开内置浏览器 |
| on | off | 同账号 App 一次，成功显示，否则结束提示 |
| on | on | 同账号 App 一次，不支持的格式/内容可转 WebView；认证/访问验证/限流停止 |

开关开启且请求符合首版 full-thread 范围时，cloned params、账号身份/Cookie 和 model 已持有的 origin 组成 attempt snapshot；不适用的请求保留旧链，不发 App。无账号才创建明确 guest+空 Cookie；选中账号凭据无效不悄悄变 guest。普通方法通过已有 HeaderMap 接收快照，新 App 沿用同一 model origin。Cookie/cid 不进 ThreadData、异常或持久化。

开始 foreground 时读取开关，准备 fallback 时再次检查开关、前台状态、generation 和账号。每个 attempt 仅 normal → app → complete；每次刷新 normal-first，不保留上游 sticky flag。关闭开关的旧链只补先 count 后 getNextCookie、空/相同 Cookie guard。

已识别业务消息、认证、HTTP 401/403/429、挑战或未知 HTML、空体、网络错误和取消不触发 App。分类局限于 thread operation，不做全局 error framework。App 的格式/内容不支持才适用浏览器兜底；明确停止错误不自动继续。

所有终态更新请求状态并清除 loading/refresh；取消、过期身份和后台失败静默。`onResume`/ON_PAUSE 跟踪前台；暂停后不再发起 App/Toast/WebView，DETACH 取消。已发出请求只可把结果交给其同页同账号，不能覆盖新代次。

保留 prefetch 独立 callback、READY 复用、末页排除和提升/降级。后台仍是原 normal request；已提升预取失败且未暂停才进入前台入口。

浏览器仍为 `ForumWebFragment`。URL builder 保留 tid/pid/page/authorid 及存在的 searchpost 上下文，不附接口 output 字段或 Cookie；不改登录和通用 WebView 内部导航。

## 3. New operation contract

按网络研究保存的原始证据登记 `THREAD.PAGE.APP_COMPAT`（July pinned contract 之外的 August source-observed 扩展）。

- POST `/app_api.php?__lib=post&__act=list`；首版合格 full-thread form 为 page/tid。上游 pid/authorid 的存在只作证据，不在本版本发送未支持查询。
- endpoint-specific Retrofit/OkHttp byte client，返回 `Response<ResponseBody>`，绕开旧 String converter、全局 Cookie provider 和 request/raw logger；不升级依赖。显式 browser UA、`X-User-Agent: Nga_Official`、snapshot Cookie。
- origin 仅 HTTPS/default 443/root path/exact host：bbs.nga.cn、bbs.ngacn.cc、nga.178.com、nga.donews.com、ngabbs.com。拒绝 userinfo/query/fragment、非默认端口、损坏偏好；不悄悄换域名。
- redirects、SSL redirects、connection retry 均关闭；无 Cookie jar/自动认证，一次 App attempt 至多发送一次。
- 声明长度和流式读取均受 4 MiB 上限限制，body 必须关闭。合法声明的 UTF-8/GBK/GB2312/GB18030 严格解码；缺 charset 仅对此操作使用上游 GBK 兼容约定；未知 charset/解码失败返回协议错误，不试探多个编码。
- 区分 HTTP/网络、业务/认证、访问验证、限流、协议/内容不支持和取消。展示消息有界，日志和异常没有原始响应或身份值。
- code 缺失/null 可进入结构验证；未知非空 code 不猜 0/200 成功。数据/错误冲突不输出成功。

GBK 缺省和结构接收都是明确的本地兼容规则，尚无真实服务验证；不宣称新 endpoint 当前可用率。

## 4. Page admission and row mapping

先验证整页再渲染，不产生半页成功。关键数值保留存在性/nullable，不能用缺失后的零值凑合法页。

| Field/context | Rule |
| --- | --- |
| Request | tid>0、child page>=1；无 pid/author/search/cache 特殊上下文 |
| perPage | 必须明确为 20 |
| vrows | 正数，直接给 ThreadData.__ROWS，不 +1/缩放 |
| totalPage | 提供时等于 `(vrows + 19L)/20L`；缺失时由验证过的规则推导 |
| currentPage | 提供时等于请求页且合法；缺失时必须由完整楼层序列证明请求页 |
| result | 非空，长度恰为 `min(20, vrows - 20L*(page-1))`；`lou[i] == 20L*(page-1)+i`，不排序/补洞/改号 |
| Identity | 每行 tid 为请求正数 tid；lou=0 可有 pid=0，其余楼层必须有正数且不重复的 pid；缺失字段不补造身份 |
| Metadata | 非空 ThreadPageInfo、同 tid、可用 subject；可选 author/fid/replies 使用已知映射 |
| Arithmetic | long 校验；与旧 float 页数算法不一致的极端值拒绝，不扩大为任意页大小改造 |

首版不适配 PID/作者筛选、非20、特殊插入/楼层缺口。普通 UI 的分页、floor jump 和引用页码公式保留。

详细字段矩阵采用 parser research 中已确认映射。content 空而 subject 非空时沿用上游的 subject-as-content，行 subject 清除；源文保证非 null。作者缺失局部降级，合法 UID 才启用身份操作，unknown/anonymous 引用不生成 UID 0 链接。client-family、匿名名和 avatar 复用当前叶子处理；blacklist 在 HTML 前赋值。

ThreadRowInfo 增加 presentation-only 的可空 comment-kind override（legacy null 走旧 heuristic，App ordinary=false）和 scoreKnown（legacy true，App false）。绑定时重设 score visibility，防止复用视图漏恢复；不发明 good-minus-bad 评分公式。这些 flags 不是可接受的服务端字段。

`attches`、`hot_post`、`comment_to_id`、`html_head_extra` 只接受缺失/null/空白；`isTieTiao` 只接受缺失/null/false。其他值整页 UnsupportedContent，未证实的 `"0"/"[]"/"{}"` 也不猜空。已有 inline BBCode/media 使用当前 renderer，不能因此宣称独立附件完整兼容。

## 5. Renderer reuse

ArticleConvertFactory 提供包内共享 renderer 或抽一个 app helper，保持旧附件/评论/用户数据先于 HTML。legacy WP from_client=103 预处理留旧 adapter；不要仅因 client code 相同就在 App 响应上套用。只共享 client-family/匿名名的叶子转换，不伪造 __U/__GROUPS JSON 调用旧 mapper。

`attachPrefix` 一次交给 `NgaImageHost.attachmentsPrefix`，完整 prefix 沿 HtmlData 到正文/签名/图片集合；手动优先级与历史 avatar 归一化保持。

精确、完整的 `<b>Reply to [pid=…]Reply[/pid]…</b>` 外层在 app 归一化为 [b]，然后复用现有 core decoder。replacement 必须 literal-safe/幂等，不吞相邻头或其他 HTML。row.content、formattedHtmlData、ThreadData.rawData 分别保留源文、HTML、原始响应。

预计不改 core/core_data，避免引入其重构和 owned test baseline。共享提取用旧链回归证明等价；页面 prefix 隔离不被宣称为修复全局 renderer 所有并发问题。

## 6. Versioned cache

ThreadData 携带 source format（默认 read_php）和可选非秘密 owner。开启新链后的 normal/App 成功均可带可信 owner；旧链/prefetch 不伪造来源。immutable cache write record 复用 ArticlePageCache.prepare 的克隆、资格和 topicInfo 原文/补齐规则。

```text
legacy (preserved):
  files/cache/<tid>/<tid>.json
  files/cache/<tid>/<page>.json
owned v1:
  files/thread-cache-v1/<owner>/<tid>/topic.json
  files/thread-cache-v1/<owner>/<tid>/pages/<page>.json
```

owner 为经验证的稳定 `uid-<id>` 或明确 guest，不能来自任意 Intent 路径。topic.json 保留 prepared topicInfo 原文。UTF-8 page envelope：

```json
{"schema":"nga-thread-page","version":1,"format":"app_api","owner":"uid-123","tid":456,"page":2,"raw":"<decoded response text>"}
```

format 仅 read_php/app_api；raw 为原始解码字符串，不是 HTML 或重新序列化 payload object。版本、类型、owner/位置/tid/page、大小与空值在 dispatch 前检查。

一个 app cache store/index 供 ArticleListModel、TopicListModel、ArticleCacheActivity 共用 listTopics/listPages/read/write/delete：

1. 只索引当前 owner 与 legacy。相同 tid 合一条，存在新页则用该 owner 的有效 topic.json，否则 legacy metadata；标题和正文同样隔离。
2. 正整数实际页码并集、数字排序，新 owner 页优先同页 legacy。已有但损坏的新页报错，不因 parser 失败换旧文件掩盖损坏。
3. 可信 owner 写新 root，legacy 不动。旧链保存 legacy 成功后移除当前 owner 同 tid/page 的旧新格式副本，避免历史 App 页遮住刚保存的普通页；失败不删旧数据，其他 owner 不动。
4. cache navigation 携带可验证的 owner key，读取/投递重查。账号变更后刷新列表或结束旧缓存页；不从 Intent 接收任意文件路径。
5. 删除可见 tid 的当前 owner 数据和 legacy 数据，不删除其他 owner。旧 raw 的历史共享语义保留，不悄悄迁移为当前账号。
6. 后台执行有界 I/O、单文件临时写入/替换；描述/页面均成功才提示。身份变化不重贴写入 owner。
7. 新位置必须合法 envelope；旧位置未封装 raw 只进旧 parser；unknown/corrupt 不试两个 parser。App 以相同 adapter 离线回读，开关关闭仍可读；错误绝不发网络。
8. 旧全帖资格、metadata 补齐、稀疏页和 1–5 页等宽 tabs 全保留。ArticleCacheActivity 缺参保护须调用 super.onCreate 后正常结束，不复制上游 lifecycle 漏洞。

首版不改 zip 格式：旧导入导出继续只操作 legacy cache，新格式仅本机保存/回读/删除。导出说明明确「仅旧版缓存；新格式缓存暂不包含」；不得递归打包其他 owner 或把新页降成共享 raw。通用 zip 安全/权限重构另属任务。

## 7. Expected changes and rollback

| Area | Files/responsibility |
| --- | --- |
| App operation | 新 Kotlin DTO、normalizer/validator、request/error policy、endpoint client |
| Existing flow | ArticleListModel/Contract/Presenter：新阶段、身份/取消、缓存 dispatch、WebView context |
| Models/render | ThreadData、ThreadRowInfo、ArticleConvertFactory、最多一个共享 app renderer |
| Row UI | FunctionUtils、ArticleListAdapter、ArticleListFragment：comment override、unknown score/identity |
| Cache | 新 codec/store、必要 cache record seam、TopicListModel、TopicCacheFragment、ArticleCacheActivity、ArticleListParam 的 owner Parcelable |
| Export/settings | TopicListPresenter 的范围说明、settings_lab/app strings/common preference key |
| Validation/spec | app JVM tests/fixtures；新增 operation 和实际改变的 prefetch/cache/UI contracts |

不预计改分页算法、core/core_data、全局 RetrofitHelper、登录或依赖版本。若实际需要扩大，先回报真实原因并更新范围。

验收使用 PRD AC1–AC8，含 legacy 等价、同页新旧更新、两账号、未知格式和过期回调。默认关闭开关可停新请求，新 cache 仍可本机读取；旧 APK 忽略独立 root，旧 raw 不变。回滚仅撤本任务，不清空用户数据或覆盖并行工作。

严格接收规则可能拒绝有效服务变体，实际兼容成功率未知；新 cache zip、特殊查询/内容及 live 验证是显式延期。
