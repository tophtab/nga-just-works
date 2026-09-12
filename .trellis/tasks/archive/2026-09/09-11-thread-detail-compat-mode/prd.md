# 适配上游帖子详情兼容模式

## Goal

普通详情接口发生可回退的格式错误时，在用户开启「帖子详情兼容模式」后使用上游备用接口，让完整帖子、单条回复/PID 定位及「只看某人」尽可能继续原生阅读；以复用上游实现为起点，适配当前模型、分页、渲染和缓存。

## Background and evidence

- 用户已选择「复用他的代码，再适配到我们现有项目。按你推荐的来」，并明确批准「建立适配任务并继续」。2026-09-12 在修订范围和术语说明后回复「行」，已批准当前兼容模式批次实施。
- 最新方向为「尽可能能使用上游的，就尽可能使用上游」。此前直接排除 PID/作者查询、非 20 楼分页和所有非空特殊字段的方案已被取代；变更理由见 [scope-revision.md](scope-revision.md)，全月采用目录见 [adoption-map.md](../09-11-upstream-august-2026-review/research/adoption-map.md)。
- 本轮以源码中已实现的能力为基础，补必要接入适配；不从零扩建上游只有字段声明、没有实现的独立功能。已有主链路、未消费字段和未做真实可用性验证分别表述。
- 上游来源为 Justwen `2becba2acc3f6c85340424cd09bb03fa7d759db0`；沿用 `.trellis/tasks/archive/2026-09/09-11-upstream-august-2026-review/research/bugfix-browser.md` 与 `sync-strategy.md`。待复用 DTO/parser/helper 源文已保存到 [upstream-source.md](research/upstream-source.md)。
- 实现基线为 `5bb92cf033aa32d749d10e1a497bc05173cd2955`，包含 `6203dad5` 菜单/缓存修复。规划开始时位于 fix/thread-menu-cache，核对结束时工作区已位于相同提交的 main，产品树无差异；不能退回缺少修复的旧 main@8284c703。
- 当前使用 `ArticleConvertFactory → ThreadData/ThreadRowInfo`；共享渲染 seam 位于 `ArticleConvertFactory.java:145`、`:162`，详见 [parser-render-adaptation.md](research/parser-render-adaptation.md)。
- `ArticleTabFragment.java:98`、`:303` 和 `ArticleListAdapter.java:198` 的分页/引用固定 20 楼；缓存消费者不止 page parser，详见 [pagination-cache-adaptation.md](research/pagination-cache-adaptation.md)。
- 上游没有可复现的真实响应夹具。缺少真实验证表示当前可用性未验证，不能据此把上游已经传递的查询参数或所有未知可选字段排除。未消费字段的含义不能靠猜测补成协议。

## Requirements

### R1 — 开关

- 实验室新增「帖子详情兼容模式」，key `pref_show_with_app_api`，默认关闭；现有「使用内置浏览器打开」独立控制。

### R2 — 前台回退和预取

- 启用后普通入口先走现有接口，合格格式失败才同账号尝试 App。需保持同一阅读会话的来源与分页一致，不能混用两种接口中含义不同的页码；任何必要的定位补请求均有明确上限，不形成重试循环。
- 成功切换后，本帖后续翻页和显式刷新沿用同一来源；关闭开关、重新进入、切换账号/查询时重新建立普通来源。刷新仍强制请求，不拿 READY 数据假装刷新；源切换最多补一次必要的楼层对齐请求。
- 已识别业务、认证、访问验证/未知 HTML、限流、网络失败、取消和空响应不触发备用请求。App 格式/内容不支持时按浏览器开关降级；认证/访问验证/限流只提示并停止。
- 所有终态清理 loading/refresh，包括两个开关都关闭；WebView 回退保留 tid/pid/page/authorid 等请求上下文，不传接口 Cookie。

- 保留普通接口两页预取、末页排除、请求复用、READY/显式刷新、提升/暂停降级和 DETACH 取消；来源/查询/分页变更后旧预取不得串页。后台仍只走现有普通方法，不调用 App、换账号、Toast 或浏览器。
- 已提升预取失败且仍在前台时才进入普通前台入口。证据：`ArticleListPresenter.java:97`、`:158`、`:170`、`:190`；见 [foreground-network-adaptation.md](research/foreground-network-adaptation.md)。

### R3 — DTO、渲染和源文复用

- 在 app 层用 Kotlin 复用上游 DTO、字段及已实现映射，接入当前 Java 模型；共享 renderer。可用的解析叶子函数随功能复用，完整数据层搬迁和全局 JSON/SDK 升级另按批次安排。
- `attachPrefix` 进入现有 `NgaImageHost`，保留完整协议/路径、手动优先级、页面隔离和历史图床修复。旧链附件/评论/屏蔽仍先于 HTML 准备。
- 昵称、匿名、头像、客户端、签名和黑名单保持现有语义。缺失作者用中性展示，身份动作不指向 UID 0；App 普通行不被旧启发式误判为评论；未知评分不伪装为 0。
- 复用上游 UID 楼主判断，缺失/匿名身份不能误标；对已确定的评论复用隐藏“贴条/只看此人”的菜单行为，保留本地最近的菜单修复。
- 源文、HTML 和 raw 分开保存；仅支持上游确认的 `<b>Reply to [pid=…]…</b>` 外层归一化，不全面转换 HTML。原作者/代码来源说明保留。

### R4 — 身份、错误和生命周期

- 开关开启时，新链固定开始时参数、账号和 model origin，不自动换账号。关闭时保留旧链，仅补 count-before-getNextCookie 及空/相同 Cookie 保护；零账号不崩溃，单账号不重复请求。证据：`ArticleListPresenter.java:137`、`UserManager.kt:69`、`:146`。
- 过期请求、切账号后的结果、暂停和销毁不得发起新的前台副作用。Cookie/cid 只存在于瞬时请求上下文，不进 DTO、日志和缓存。
- 错误分类和终态遵守 R2，网络操作细节由 R8 规定。

### R5 — 缓存适配和保留现有修复

- 保留 `ArticlePageCache.prepare` 的全帖资格、选中子页克隆、合法 topicInfo 原文和缺失 metadata 补齐；通知 → 回复 →「显示全部」仍可缓存。证据：`ArticlePageCache.java:16`、`:26` 及 `.trellis/spec/backend/thread-page-cache-contract.md`。
- 新链有确定账号来源的数据使用版本/格式封装，页面和标题一同按账号保存，正确 dispatch read.php/App parser。无可靠 owner 的旧流程不补造 owner，旧 raw 不迁移、不重贴标签。
- 缓存列表呈现当前账号新页与旧页，保留真实页码、1–5 页等宽页签；仅分页语义相同的数据才可合并，不将过滤结果或不同接口/页大小的同号页混成全帖。A/B 的新缓存互不列出、覆盖、读取或删除。
- 已有但损坏的新页明确失败，不改用另一份数据掩盖错误；回读绝不发网络。关闭兼容开关仍能读已保存的新页。
- 同帖不同来源/页大小作为独立缓存版本；未知页大小只保存独立窗口。删除选中版本不影响其他版本或账号。缓存页缺启动参数时安全退出，并满足 Activity 生命周期。
- 首版新格式支持本机保存、列表、回读和删除；旧 zip 导入导出继续支持旧缓存，并明确说明新格式暂未包含。

### R6 — 查询、分页及按字段降级

- App 适配包含完整帖子、PID/单条回复与作者筛选；复用上游 page/tid/pid/authorid 参数。保留入口上下文，不能将筛选查询偷换成全帖，缓存回读仍不联网。
- 复用上游 currentPage/perPage/totalPage 等字段，将当前固定 20 楼假设改为明确分页契约。服务器分页、全帖楼层、过滤后结果序号和列表位置分别处理；不补楼、不改号、不因有楼层缺口就丢弃合法筛选结果。
- 未知页大小或缺少定位依据只限制依赖这些信息的操作，不先拒绝所有可读正文；来源变更后重新对齐阅读位置，不能静默跳错楼。
- 特殊内容逐字段处理：复用上游实际存在的正文、引用、用户及评论能力；已有 renderer 能处理的继续显示；未知可选元数据保留 raw 并局部降级，非空本身不是整页拒绝依据。
- 关键正文不可读但身份可信时保留可见占位和未完整显示说明，不静默丢行；该条依赖源文的动作和整页缓存不可用。身份/查询矛盾则整体错误。错误响应不因带 result 就一概认作成功；未知 code 也不能脱离结构与已知错误事实单独判定含义。

### R7 — 验证

- 用合成离线夹具和 fake transport 验证行为，不把它们标为真实响应或可用性证明。
- 相关 JVM、debug 构建及全模块 lint 通过；每份 lint XML 必须 0 Error/Fatal。无关旧 debug 示例失败按现有规范记录，不掩盖。
- 默认不执行真实 NGA、账号数据读取、ADB/设备、签名发布、push/merge。

### R8 — 新操作边界（规划补充）

- 新增来源明确的 `POST /app_api.php?__lib=post&__act=list` 操作扩展；保留已确认请求/身份头。
- 使用 endpoint-specific byte client、同一 origin/账号快照、HTTPS 精确目标、显式 charset/大小限制、取消与错误分类；不复制旧 String converter 的原文日志、全局 Cookie 或自动 redirect/retry。完整契约见网络研究及 design。
- 不扩展为全局网络、登录或账号管理重写。

## Acceptance criteria

| ID | Requirement | 可观察验收结果 |
| --- | --- | --- |
| AC1 | R1–R2 | 四种开关组合均结束加载；默认零 App 请求；回退和定位补请求有界；明确停止错误不回退；同一 pager 不混用不同分页来源。 |
| AC2 | R3 | 原生正文/引用、UID楼主、评论菜单、用户/匿名/缺失身份、图片/头像/屏蔽正确；旧 renderer 抽取前后等价，源文与 raw 保留。 |
| AC3 | R2、R4 | 0/1/2 账号、切换身份、过期/暂停/销毁、预取提升和失败均有离线断言；后台无可见副作用。 |
| AC4 | R5 | 旧 raw、两种新 envelope、关闭开关和混合稀疏页可正确回读；未知版本/损坏/错owner不误读。 |
| AC5 | R6 | 合格完整帖、PID、作者过滤及非20分页能显示；按实际 PID/楼层定位；未知可选字段不导致整页失败；关键结构矛盾不缓存，过滤结果不变成全帖。 |
| AC6 | R7 | 相关 JVM/debug/lint 检查记录完整；全模块 lint 0 Error/Fatal；不声称已验证真实成功率。 |
| AC7 | R8 | fake transport 验证 method/URL/form/headers、origin、redirect/retry、charset/大小和取消；无敏感日志或真实流量。 |
| AC8 | R5 | metadata补齐/原文、账号/来源/页大小隔离、单窗口、选中版本更新/删除、页签、缺参保护及导出范围均有验证。 |

## Out of scope and deferred items

- 整批上游分支合并。七笔数据重构的可用代码随功能适配；其他媒体/UI、板块图标及 JSON/SDK 等改动保留在完整采用台账，按依赖安排独立批次，不视为放弃。
- 源码没有实现或没有足够字段依据的独立附件协议、评论父级关系、热评交互与评分公式；保留可读主体和 raw，不捏造新协议或把未知元数据当作已解码。
- 新缓存 zip 导入导出、旧缓存全量账号迁移、全局网络/认证重写。
- 真实服务成功率、设备操作、签名发布及远端分支操作。

## Technical notes and artifact status

- 本功能的请求、渲染、分页、UI 和缓存需共同验收，仍使用已有任务；不重复征询任务创建。
- 新版 [design.md](design.md) 与 [implement.md](implement.md) 已覆盖查询、分页/定位、逐字段降级与缓存布局；旧方案移到 history/，不再作为实施依据。详细源证据见 [query-pagination-revision.md](research/query-pagination-revision.md) 和 [content-reuse-revision.md](research/content-reuse-revision.md)。
- 功能、独立审查、本地检查与工作提交已完成，本记录随任务归档；实现位于 `/home/toph/nga-just-works-compat-mode` 的 `feature/thread-detail-compat-mode` 分支。最终范围与 278 项测试、13 模块 lint 结果见 [delivery.md](delivery.md) 和 [独立审查](independent-check.md)。离线验收只能证明本地行为，真实接口覆盖率尚未验证。
