# Design — 帖子详情兼容模式（范围修订版）

## Boundary and source

采用 Justwen `2becba2acc3f6c85340424cd09bb03fa7d759db0` 已实现的 App 请求、DTO、楼层转换和引用语法，接入包含 `6203dad5` 的当前基线 `5bb92cf0`。保留现有原生 reader、图床、缓存和预取；上游未实现的独立附件/热评/父评论协议不在本轮从零扩建。

本设计取代 [旧设计](history/design-v1.md) 的 full-thread-only、固定20楼及非空可选字段整页拒绝规则。源码事实仍见 [upstream-source.md](research/upstream-source.md)；新版行为依据为 [查询与分页](research/query-pagination-revision.md)、[按字段复用](research/content-reuse-revision.md) 和 [网络边界](research/foreground-network-adaptation.md)。研究是证据与备选建议，冲突处以本设计为准。

## 1. Structure

```text
request/account/settings snapshot + reader query/source generation
  → ordinary read.php on a new reader
    → usable result → native reader
    → eligible foreground format failure → one same-account App request
      → bytes/error classification → upstream-derived DTO projection
      → query identity + paging normalization + field-wise content mapping
      → adopt coherent source layout; at most one anchor-alignment request
      → existing ThreadData/ThreadRowInfo + shared renderer → native reader
  → explicit refresh / page selection → current generation's source

explicit save → complete full-query response → versioned owner/layout cache
cache open → explicit stored format/layout dispatch → same adapter; no network
```

新增 DTO、分页值、request policy、codec/store 与 endpoint client 使用 Kotlin，现有 Java/RxLifecycle 边缘保留。使用当前 fastjson1 DOM 读取实际消费字段，避免 Kotlin 构造器反射把一个未知扩展字段升级成整页错误。不因此迁移全局 JSON 库、全部数据类或 Compose/MVVM。

## 2. Foreground source, settings and terminal behavior

- 实验室使用上游 key `pref_show_with_app_api`，标题「帖子详情兼容模式」，默认 false；内置浏览器开关独立。
- 新 reader 从普通接口开始。兼容开启时，固定 params、selected identity/Cookie、model origin；无账号可明确使用 guest，选中账号无效不能悄悄变 guest。开关关闭的旧链只补先 count 后 getNextCookie、空/相同 Cookie 保护。
- 合格前台格式失败才进入一次 App 回退。成功后来源绑定到该 reader 的查询 generation，后续翻页和显式刷新沿用当前来源；显式刷新仍强制新请求，不复用 READY。关闭兼容、换账号、换查询或重新打开 reader 时重置普通来源。此决定替代旧计划的逐页/每次刷新 normal-first，避免不同页大小混页。
- 只有普通接口完整帖可自动预取。保留 next-two、末页不预取、READY/in-flight 复用、提升/暂停降级、offscreen limit 2 和 DETACH 取消；PID/作者/缓存/App/未知总页数不发布预取候选。
- 当前 source、query、page layout、account 或 generation 不一致时，不得复用 READY 或投递旧数据。源切换由当前前台结果提议、activity owner 确认；后台完成不能切换整个 reader。
- 认证、已识别业务拒绝、401/403/429、访问挑战/未知 HTML、网络错误、空传输体、取消均不自动尝试 App。App 不递归重试或轮换账号。未知 code 不赋予猜测含义：先识别已知错误，再独立验证 data；只有 code/msg 而无有效 data 不是成功。
- 终态统一结束 loading/refresh。暂停、旧请求、切账号后的回调不产生 App/Toast/WebView 副作用；DETACH 取消。在仍有效的前台请求中，格式/内容不支持可按浏览器开关打开现有 WebView，认证/挑战/限流停止。
- WebView URL 保留原 tid/pid/page/authorid 及已有 searchpost 路由上下文，不传接口 Cookie。新功能不重写通用 WebView 登录或导航。

| 兼容开关 | 浏览器开关 | 普通格式失败的结果 |
| --- | --- | --- |
| 关 | 关 | 旧链经账号保护后结束提示 |
| 关 | 开 | 旧链经账号保护后按既有行为转浏览器 |
| 开 | 关 | 同账号 App；失败结束提示 |
| 开 | 开 | 同账号 App；可降级的格式/内容失败转浏览器 |

## 3. App operation contract

按 August 源码登记 `THREAD.PAGE.APP_COMPAT`，不冒充 July 固定契约已有的操作。复用 `POST /app_api.php?__lib=post&__act=list`，form 为 page 及非零 tid/pid/authorid；可同时传 pid 和 authorid。searchPost 在已检查源码中是本地路由标志，两种请求均未发送，不新增猜测的 App 参数；perPage 仅消费返回值。

保留 [网络研究](research/foreground-network-adaptation.md) 中已选 endpoint-specific byte client：同 model origin/账号快照、显式 Cookie/browser UA/`X-User-Agent: Nga_Official`，不经过旧全局 Cookie/raw logger/String converter，不升级依赖。HTTPS 精确 host、默认443、根路径；允许项目现有五个主域，拒绝损坏偏好而不换域；无自动 redirect/SSL redirect/connection retry/Cookie jar。

读取并关闭 body，声明与流式长度均限制为 4 MiB。合法声明的 UTF-8/GBK/GB2312/GB18030 严格解码，缺 charset 使用此操作的上游 GBK 兼容默认；非法 charset 或解码错误不多编码试探。所有这些是本地操作契约，没有真实接口可用性保证。日志仅记录操作和粗粒度失败原因，不含 raw、Cookie/cid 或账号秘密。

## 4. Query and paging adapter

在 ThreadData 附加一个不可变分页/来源值，在 ArticleShareViewModel 持有 reader generation。字段包括 query kind、requested tid/pid/author/search disposition、resolvedTid、source、requested/effective page、可空 perPage/totalPage/vrows、页码依据和导航能力。保留 `rowNum == rowList.size()` 与原始服务字段，不用造出来的 __ROWS 迎合旧 `/20`。

| 查询 | 原生行为 | 分页/定位 |
| --- | --- | --- |
| 完整 tid | 保存全部返回行及顺序，短页/楼层缺口不自动拒绝 | 优先用有效 totalPage、perPage；只有完整帖才可用 vrows/perPage 做明确的备用推导 |
| 只看作者 | 每次请求保留 authorid；原始 lou 不重编号 | totalPage 表示本查询的页；vrows 含义未证实时不充当全帖楼层上限 |
| PID/回复搜索 | 保留 pid 与附带 authorid；必须找到目标 PID 才声称定位成功；PID-only 从匹配行确定 resolvedTid | 用现有 scoped search 页面显示 singleton/window，按实际 PID 找列表位置，不当完整帖分页 |

共同规则：

1. 正数 perPage 可为10/20/30/40等，不能取 result.size 作为页大小，也不截断/补楼/拼成20楼。正数 totalPage 优先；与 vrows 推导不同本身不能证明正文错误。
2. 缺 currentPage 时，完整/作者查询以请求页为明确的本地坐标；PID 仍是 lookup window。合法但不同的 currentPage 在 pager owner 重新归位，不能写在旧页号下。无效/溢出分页信息只降低可用导航能力。
3. perPage 缺失但 totalPage 可用时仍可翻页；两者均无依据时显示已取得的当前窗口，不虚构“全帖只有一页”，不自动扫页找总数。页面内已知 PID/lou 仍可定位；未知映射的任意跳楼不启用。
4. 稀疏楼层、附加评论不能靠 `floor % pageSize` 定位。使用 generation-tagged pending anchor，目标页加载后按实际 PID 优先、lou 次之查找；不存在时明确提示，不假装跳到了。
5. 完整帖且有可靠楼层窗口时，`floor / perPage + 1` 只生成候选页，仍须验证目标行。作者过滤/PID/未知页大小不能靠这个公式定位全帖楼层。
6. 新源/页大小进入同一 reader 时更新 generation，清候选与 READY、重建页身份、忽略旧回调。普通20条第7页转 App10条第7页时，保留楼层120锚点，最多追加一次前台 App第13页对齐。失败或无可靠映射时显示已取得窗口并说明位置未保留，不继续扫描/交替重试。
7. ArticleListActivity 实际应用入口页码；ArticleTabFragment/ArticlePagerAdapter/GotoDialogFragment/ArticleListFragment 共同使用上述模型，不能只改 converter 放宽条件。
8. PID 入口统一采用可显示“显示全部”的 scoped 页面。显示全部用 resolvedTid 新建参数，清 pid/author/search/cache，page=1，保留合法同帖 metadata；作者过滤也可通过同一 helper 退出筛选。不能让原始 tid=0 进入全帖。
9. 现有 `[pid=pid,tid,page]` 引用的页提示保留普通20楼约定的独立 helper；不能填 App/过滤页号。真实 PID 仍是导航身份，当前 decoder 本来不消费第三项。缺少 floor 时使用已有 PID 链接能力，不伪造页码。

空数组不能访问 result[0]：PID 给出未找到目标；其他查询可以显示明确的未返回内容状态，不能缓存、制造楼层或声称空响应证明了服务端业务成功。混 tid、请求/结果明确矛盾或错误结构不进入正常阅读；可选作者缺失不会自动丢弃正文。

## 5. Content, row UI and actual upstream reuse

采用 [字段级研究](research/content-reuse-revision.md) 的实际消费矩阵：

| 内容 | 采用方式 |
| --- | --- |
| source/subject/alterinfo/vote、author、UID/时间/client | 复用上游有效映射；缺 author 局部降级；保留本地匿名、头像、屏蔽、详细客户端信息 |
| 楼主标记 | 复用 UID 判断，普通/App 都接入；已知正当身份才比较，0/占位符/匿名哨兵不相等成楼主；缺失时只保留可靠旧名降级 |
| `<b>Reply to [pid=…]…</b>` | 仅归一化已确认外层为 BBCode，literal-safe、幂等；共享 decoder，编辑/引用仍用源文，raw 单独保留 |
| inline 图片/音频/视频/投票 | 继续使用现有 renderer；完整 attachPrefix 经 NgaImageHost，保留手动图床、页面隔离、旧地址和音频 |
| attches/hot_post/comment_to_id/html_head_extra | 上游 App parser 未消费；原始值保留、可选投影忽略；非空或类型变化不独立导致整页拒绝，不执行额外 HTML，不猜附件/热评/父级协议 |
| isTieTiao | 合法 Boolean 可辅助明确 row kind；true 的可读行完整显示为评论，不制造父级/楼层；错误类型保留为未知，不按缺头像等稀疏字段猜评论 |
| 已知 read.php 嵌套评论 | 保留已有确定父级/作者/附件链；复用上游“评论不显示贴条、只看此人”的菜单语义，保留本地最近菜单修复 |
| vote_good/vote_bad | 原文保留，不做没有依据的分数公式；App未知分数不显示为伪造的0，绑定时恢复普通行分数可见性 |

正文可读而未知可选字段存在时正常显示。已知 source 缺失/不可消费时不得悄悄删除整条；可用带身份的“此条内容暂无法完整显示”占位保留其位置，并标记本页不完整、关闭该条依赖源文的动作和整页缓存。无法建立可信查询/行身份的结果整体错误。明确空字符串、subject-as-content 和已知 alter/屏蔽提示按现有语义处理，不把它们统称为缺正文。占位不能作为“完整兼容”的成功宣称。

shared renderer 抽取必须保持旧链附件、评论、屏蔽先于 HTML；保留普通接口 WP 预处理，不因 App client=103 就套用未证实的相同转义。App独立评论使用普通行 renderer；不得喂给当前无条件截掉 `[/b]` 前内容的 nested comment builder。若确需复用该 builder，先把裁剪限定于完整已识别引用头。

普通行/评论/未知行、已知/未知UID和score通过少量附加 presentation metadata 与现有 Java UI 对接，不整体替换 ThreadRowInfo。身份动作需实际有效 UID/PID，未知 parent reference 不能代替本行 PID。视频CSS和额外无类型flash规则仍在整体采用台账，按独立媒体批次接入，不混入这次必要 renderer 抽取。

## 6. Versioned cache and retained repairs

继续复用 ArticlePageCache.prepare 的完整帖资格、选中子页克隆、原始 topicInfo 保留和 metadata 补齐。PID/作者/回复搜索即使可读或退出后有 resolvedTid，也不能保存成全帖；“显示全部”新请求成功后照常可缓存。内容不完整/查询失败不保存。

```text
legacy: files/cache/<tid>/<tid>.json and <page>.json
owned:  files/thread-cache-v1/<owner>/<tid>/<layout-id>/topic.json
        files/thread-cache-v1/<owner>/<tid>/<layout-id>/pages/<page>.json
known layout: source + positive perPage (read_php-20 / app_api-10 / ...)
unknown size: an independent one-window snapshot ID; never union snapshots
```

新 chain normal/App 可带可信非秘密 owner；旧链无可信来源时仍保存旧 raw，不补造 owner。owner 为验证过的 uid 或明确 guest，不含Cookie/cid。envelope含 schema/version=1、format、owner、tid、queryKind=FULL、layoutId、page、pageSize(nullable)、pageBasis、raw；raw 是原始解码字符串，非HTML。topic.json保留 prepared metadata 原文。

统一 store/index 供 model、缓存列表、open/read/delete 使用：

- 当前 owner的新布局和 legacy分别列出，标题/正文一同隔离。每条记录有可验证 entry handle；同tid不同source/size不合成一套页签，不因为都是“第2页”互相覆盖。用户副标题说明普通/兼容显示与已存页数，避免展示内部schema字段。
- 同一个已知布局按正整数真实页码合并/排序；未知页大小按独立窗口保存，不后来悄悄重贴标签。旧raw继续只走旧parser；新envelope根据format显式dispatch。损坏/未知版本/错owner或layout本地失败，不换parser/源掩盖，不联网。
- 当前选择的child、effective page、layout与保存记录一致后写入；不拿 ThreadPageInfo.page 当分页 authority。后台有界IO、临时文件替换，标题/页面完成才反馈成功。重复写同一entry/page更新本版本，其他布局/账号/legacy不动。
- 缓存导航只传受限entry/owner/layout值，不接收任意路径；读取与交付重查账号。删除仅删除选中entry，其他布局与账号不受影响。
- 保留稀疏实际页码、1–5页等宽页签、metadata补齐/原文和通知回复→显示全部缓存修复。ArticleCacheActivity缺参保护必须在正确super.onCreate生命周期后结束。
- 兼容开关关闭仍能读已存App页。旧zip导入导出继续支持legacy；新存储根不被旧导出误打包，界面明确这次只导出旧格式缓存。新格式zip是后续独立工作。

## 7. Change ownership and checks

| Area | Expected consumers |
| --- | --- |
| 操作/转换 | App Kotlin DTO/policy/client/paging/codec；ArticleListModel/Contract/ArticleConvertFactory |
| reader状态 | ArticleListPresenter、ArticleShareViewModel、ArticleListParam、ThreadData、ThreadRowInfo |
| 页码/定位/UI | ArticleListActivity、ArticleTabFragment、ArticlePagerAdapter、GotoDialogFragment、ArticleListFragment、ArticleSearchFragment、ArticleListAdapter、FunctionUtils |
| 缓存/设置 | ArticlePageCache、ArticleCacheActivity、TopicListModel、TopicCacheFragment、TopicListPresenter、实验室资源 |
| 规范与验证 | 操作登记、prefetch/cache/UI契约的本功能delta、合成JVM/fake transport和debug/lint |

具体执行与回归矩阵见 implement.md。上游只有字段、没有实现的独立能力不扩建；整套数据层/JSON/SDK迁移、板块动态图标和独立媒体补丁按总采用台账另批推进。关闭开关停止新App请求；已存页面仍可读，旧数据不迁移/删除。回滚只撤本任务变更，不覆盖并行工作。

实际接口可用性、缺省charset与可选字段协议均未用真实响应验证；离线验收证明本地适配行为，不证明服务端覆盖率。
