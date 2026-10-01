# Research: U3 全量迁移设计依据与实施批次

- Query: 形成完整 U3 可审核范围、采用/适配/保留清单、逐批文件责任、兼容用例与回滚点。
- Scope: mixed；当前源代码、固定上游补丁、存档研究和双库离线实验。
- Date: 2026-09-30

## Findings

### 1. 建议交付终态

1. app 运行时统一 `com.alibaba.fastjson2:fastjson2:2.0.59.android8`，43 个当前直接生产消费者、12 个直接测试消费者和隐式存储消费者全部纳入，不遗留“后续再盘点”的阶段。
2. 普通读帖的原始 DTO 与 JSON 字段解码进入 core_data/core；现有 app Java `ThreadData` / `ThreadRowInfo` / `ThreadPageInfo` 保留作为展示、导航和持久化适配接口。**不再新增同义 ThreadInfo/ThreadPostInfo，也不把整个页面重写 Kotlin/Compose。** 新 raw DTO 不携带 HTML、账号、Reader generation、UI fallback 或缓存路径。
3. `ArticleConvertFactory` 保留现有 public facade 和 renderer seam；内部分为 core 原始解码 → app 原始/展示适配 → 现有渲染。NormalArticleParser/AppArticleParser、ArticleErrors、ArticleReaderSession、ArticlePagingInfo、ArticleCacheStore 继续持有各自现有策略。
4. 没有数据库/偏好/草稿/缓存 schema 迁移，没有全量重写或清缓存。适配后的新数据仍可被旧版本读取；原始响应和既有合法 topicInfo 原文不变。

该终态吸收上游最有价值的 bean/model/parser 边界与 JSON 升级，拒绝只为上游命名一致而搬动已稳定的 Java 类型。

### 2. 完整采用、适配、保留表

| 上游内容 | 处理 | 本 fork 落点 / 理由 |
| --- | --- | --- |
| `1307d324` 原始附件/帖子数据集中 | 适配 | 新 core_data wire DTO；现有 Attachment/ThreadRowInfo 不全局改名，保留 Java 消费者 |
| `0dbd80b4` 展示字段从协议数据分开 | 采用原则 | wire DTO 无 formattedHtml/isBlocked/clientModel；既有 ThreadRowInfo 作为展示适配对象，ArticleRowPresentation 已表达能力 |
| 简化客户端 Toast / 浏览器图标 | 保留 fork | 原 from_client 与 from_client_model、机型/系统细节继续交给 ArticleListAdapter，不接受只剩枚举名称 |
| `0d6e9e50` 匿名/client helper 与 core_data 依赖 | 部分已吸收；其余适配 | ArticleAuthorSupport 已存在。可保持 app 包装；只在确有 core 使用者时移动纯函数，不复制匿名表 |
| `86e3f65d` ThreadBean/用户字段/UID OP | DTO 部分适配，UID已吸收 | nullable/raw-validity 避免缺用户或结构化正文成为虚假有效数据；已有 UID 楼主标记继续使用 |
| 删除热门回复 `17` | 不采用 | 保留 hotReplies 字段/拆分逻辑，虽当前 UI 无消费者，属于已保留数据能力 |
| 删除 Windows Phone `103 ` unescape | 不采用 | 保留普通链 WP 正文预处理，App 原始正文不套普通 WP 预处理 |
| 评论识别/menu 更换 | 不采用产品变化 | 使用 ArticleRowPresentation.kind + 现有 FunctionUtils fallback；不把 fid==0 猜成评论 |
| `00352348` 评论/附件展示映射 | 适配 | 评论用自身用户/头像；完整附件列表和黑名单必须在 HTML 前完成；不搬上游顺序 bug |
| `3596da00` HTML Java→Kotlin/改包、GlobalConfig | 保留当前 | 现有 HtmlData/AttachmentData/CommentData 已在 core，迁移语言/位置本身无必要；保持 U2 的渲染测试边界 |
| `a6e4a890` parser 移入 core | 适配 | core 仅原始 DTO 解码/用户关联；app 保留 source/query/account/paging/errors/渲染连接，不返回简化后的上游 ThreadInfo |
| 上游用户资料缓存 | 适配 | 每次解析内的 UID→已解码用户缓存，仅此解析生命周期，不跨账号/请求共享 |
| 上游总行数驱动当前行遍历 | 不采用 | 使用当前 `__R__ROWS` 和其严格/legacy 校验；总数仅存总数，不扫描 `__ROWS` 次 |
| IHtmlConfigService + ARouter global adapter | 不采用原实现 | 现有 ArticleRowRenderer/ArticleBlacklist 可用，不引入 service locator；必要时配置是显式单次值而非新增全局读取 |
| `25652de8` fastjson2 | 全量采用并适配 | 包含所有本地格式、AI/profile 安全边界、R8、类型别名和调用API；不增加未用 kotlin 扩展库 |
| `93acf42a` jdata regex | 不原样采用 | 实验已证明树解析→bean 能兼容全部存档形状，避免有末字段/空格漏洞的剥离 regex |
| `750871b3` 无引号上传字段选项 | 局部采用 | 上传和已有 AI/profile 兼容点按 fixture 设定，不用全局 JSONFactory 配置 |
| 旧工具/私信副本清理 | 最小范围 | app 无消费者 MessageConvertFactory 可删；只有迁移后最后消费者消失的 helper 可删，不扩大到 4c99051f 全清单 |

### 3. JSON 兼容实验改变了具体设计

本次运行 JDK 本地 javac/java，使用 Gradle 缓存真实 `1.1.71.android` 和 `2.0.59.android8` JAR。全部是合成输入，无 Android/NGA。源码和原始结果在 `research/probes/`；编译产物已删除。

| 实验 | fastjson1 | fastjson2 默认 / 指定路径 | 设计结论 |
| --- | --- | --- | --- |
| LegacyBean getters authorId/fromClient，wire authorid/from_client | `42 / 103 synthetic` | 默认 `0 / null`；SupportSmartMatch 后一致 | 必须显式映射/alias，或仅对旧 bean 入口使用局部 SupportSmartMatch；imports 替换不是完成 |
| 真实 TopicListBean + 存档6个 jdata fixture | 存档全通过 | typed parse 4种 hex escape 失败；generic tree→toJavaObject 6/6保留 synthetic 板块名 | TopicConvertFactory 用局部树解析后 bean 转换，涵盖首/末/空格/嵌套；无需删除 jdata 或写容错扫描器 |
| SafeJsonParser 风格 `{unquoted:3}` | 成功 | AllowUnQuotedFieldNames 成功 | 沿用当前 AI 测试要求，不声称无引号只来自上传 |
| @type/$ref 顶层与嵌套 | 普通字段 | DisableReferenceDetect、不启用AutoType时保留普通字段 | 替换 DisableSpecialKeyDetect 的**行为**，不是找名字相同的 flag |
| decimal 0.1 | BigDecimal | 本地 JSON2 generic 默认仍 BigDecimal | 不打开 UseDoubleForDecimals；fixture 约束类型/精度 |
| `{"ok":1}{}` | 失败 | 失败 | 保留完整输入消费检查，不开 IgnoreCheckClose |

注意：单独 generic tree 可以接受的 jdata hex string，不代表 typed reader skip unknown fields 也接受；这是旧报告与本次结果不矛盾的原因。另一个 probe 显示局部低级解析对 `+123` 的容忍不同，因此解析 wrapper/token 修正必须继续由既有具体 operation 负责，不能据库默认把未知输入判为成功。

JSON 入口策略：

- 旧 bean 网络/持久化：优先精确键和受影响 bean 局部 `SupportSmartMatch`，列出 `ThreadPageInfo.authorId`、row `from_client`、TopicPostBean/TopicListBean 嵌套类、User getter 等对照；禁止全局 smart/field-based 开关。
- TopicConvertFactory：wrapper 原样处理后 generic JSONObject，再 `toJavaObject(TopicListBean.class, SupportSmartMatch)`；未知 jdata 可以留在树中，被 DTO 忽略，不改原 raw。仍要求合法 envelope/必要字段；截断失败不改写成功空列表。
- Upload：`JSON.parseObject(payload, AllowUnQuotedFieldNames)`，保留每个入口不同的 data/error/error_code。头像上传非NGA格式也单独 fixture，不共用错误 envelope 猜测。
- SafeJsonParser/ProfileWebUserParser：局部 AllowUnQuotedFieldNames + DisableReferenceDetect + DisableSingleQuote，保留现有字符/深度/HTML脚本提取 guard，默认关闭类加载；错误不附原始 body cause。
- cache/持久化：不借网络宽松配置改变严格 owner/layout/Int 检查；`JSONWriter.Feature.WriteMapNullValue` 保持 null；保存后旧 reader 可用。
- 不将 fastjson2 默认当语法规范。所有改变按现有 parser 的输入集与测试约束，特别保持 CONTENT/FORMAT/EMPTY/AUTH/RATE_LIMIT 区分；R7 不在此任务。

### 4. 有序实施批次（完整，不是首批占位）

以下批次全部属于现有 U3 子任务；不用新任务目录。每批独立提交/构建/审查，未通过停止后续批次；单一库切换 B2 内的生产更改必须一起完成，禁止把旧/新 JSONObject 跨 public API 混用。

| 批次 | 精确文件 / 符号责任 | 交付与验证 | 回滚 |
| --- | --- | --- | --- |
| B0 固定整合基线与行为样本 | 本文 inventory 的43生产文件/12测试文件；已有 `NormalArticleParserTest`, `AppArticleParserTest`, `ArticleConvertFactoryTest`, `ArticleCacheStoreTest`, `ArticlePageCacheTest`, `HomeBoardOrderContractTest`, `AuthorLocationStoreTest`, `ProfileLocationParserTest`, AI parser/client/profile tests；新增 legacy-storage/JSON fixture | 接入 U1/U2 与原会话已交付 R1–R6，记录新增消费者；合成旧库 golden 输入/输出和内容/身份/菜单/错误基线，真实敏感数据不得进入 fixture | 仅测试/资料，无产品回滚 |
| B1 为 JSON 差异建立局部测试缝 | `TopicConvertFactory.getTopicListInfo` JSON→TopicListBean；`TopicPostModel.uploadFileInner` payload decoder；`AvatarFileUploadTask.NonameUploadResponse`；`SafeJsonParser.parseObject`；`ProfileWebUserParser.parse`；必要时新增 package-local helper，仍使用旧库 | 不移动传输/回调/重试，仅把不可独立测试的 decode 提为纯函数；固定 jdata6形状/无引号/异常 envelope/alias/@type/$ref；report和avatar bean补marker可在B2 | 回退 helper 提取即可；旧输入输出不变 |
| B2 全部 JSON2 原子切换 | `build.gradle`、`lib_base_common/build.gradle`、`lib_core/build.gradle`、catalog；inventory全部当前源码和测试 imports；3种注解类+U1 iconUrl；2 static toJavaObject；ArticleCache null writer；两ParserConfig入口；2缺keep bean；`proguard.cfg` | 旧版本写→新读、新写→旧读及新→新；网络矩阵全部；`dependencyInsight` 分别看runtime/kapt；保留边界和字段值后才移除旧依赖/keep。无全局 parser设置，无额外 fastjson2-kotlin | 作为一个完整切换提交回退；不拆半个依赖回退。所有新存储仍旧 schema，数据无需恢复或清理 |
| B3 引入普通读取 wire DTO/core decoder | `lib_core_data/build.gradle` 开 Kotlin/JVM17；新增 `com.justwen.androidnga.core.data.thread.ReadThreadWire/ReadPostWire/ReadUserWire/ReadTopicWire/ReadAttachmentWire`；`lib_core/build.gradle` 明确core_data依赖；新增 core `ReadThreadWireDecoder` | DTO仅协议字段、原始有效性和解析结果；不依赖app，也不导入ThreadRowInfo。字符/数字/null/未知结构 fixture；页row计数与总数分离、每次用户缓存、缺用户组行为。此批可先不接生产入口 | 回退新增DTO/decoder/依赖，无用户行为或持久化变化 |
| B4 接普通解码与旧展示适配 | `ArticleConvertFactory` 保留 getArticleInfo/getScopedArticleInfo/renderRow 签名；新增 app `ReadThreadLegacyMapper`，搬现有 buildRow*/buildThreadPageInfo 的字段关联；`NormalArticleParser` 只必要适配。ThreadData/ThreadRowInfo/Attachment/ThreadPageInfo仍Java公共类型 | 原始scalar/DTO→旧展示→附件/评论/黑名单完成→renderer；legacy宽松入口与scoped严格入口均保留。新旧fixture语义对比全部字段、sourceComplete/ArticleRowPresentation；App mapper仍当前实现，仅共用既有helper | 回退 facade接线/mapper，此前JSON2批可保留；无缓存格式更改，旧raw照常重放 |
| B5 收尾与全链验证 | 移除确认无消费者 app `sp.phone.mvp.model.convert.MessageConvertFactory`；清理B4取代的内部parse helper；active lib_bu_message parser保留；更新契约/上下文/清单 | 重新扫描全部旧import/注解/隐式消费者与R8规则；受影响及全模块build/unit/lint；U1图标持久化和U2 HTML的联合回归；只宣称已运行的检查 | 纯删除可独立回退；B4若回退，恢复其关联helper；不把独立B2回退一并强制执行 |

选择 JSON 库先迁移、随后 DTO 抽取，便于把“库默认变化”与“字段映射改变”分开定位。B1 是短期测试缝，不建立全 App JSON facade/框架；已经纯函数可测的入口不用机械包一层。B3/B4 是完整采用的结构性增量，非“以后再决定迁移哪些模型”。

### 5. DTO/core 边界必须覆盖的字段与行为

`ReadThreadWire`：原始 raw 单独由 app ThreadData 保存；数据中 `__T`、`__U`（含`__GROUPS`）、`__R`、`__GLOBAL._ATTACH_BASE_VIEW`、`__R__ROWS`、`__ROWS`；必须区分 missing/null/wrong-type，不以 emptyMap/0 默认抹去严格校验需要的信息。仅保留当前使用的 `__PAGE/__R__ROWS_PAGE` 信息为可选原字段，不用它们替换现有分页契约。DTO 不主动支持未建模新协议。

`ReadPostWire`：tid/fid/authorid/pid/lou、subject/content/alterinfo、vote、postdate、level、from_client、score、attachs、comment、`17`；正文/标题值保留 scalar validity（String/Number/null vs object/array）以及字段存在性；row 自带 author/isanonymous/yz/js_escap_avatar/muteTime/aurvrc/signature 等已有 fallback 值必须在旧 mapper 相同优先级保留，用户表覆盖顺序不变。formattedHtml/imageUrls/blacklist/ArticleRowPresentation 为本地输出，不能从 wire 当权威读取。

`ReadAttachmentWire`：aid/url_utf8_org_name/dscp/size/ext/name/thumb/attachurl/type/subid，字段完整映射回 Attachment（当前不是只存 url）。Map 顺序和 comments 顺序不因 DTO 改用无序集合而变化；热门回复按当前逗号拆分规则保留。

`ReadUserWire`：username/avatar/yz/mute_time/rvrc/signature/postnum/memberid/buffs；当前用户名匿名判断、aurvrc int 与 reputation/10 分别保持；每次解析内按 UID 缓存；缺少用户返回 row原有资料，缺组不丢整页。账号/blacklist 从已有 app 参数接入，绝不装进 global core 用户缓存。

`ReadTopicWire` 映射 `ThreadPageInfo`：tid、fid、author、authorid→authorId、lastposter→lastPoster、replies、subject、titlefont→titleFont、type、topic_misc→topicMisc、postdate→postDate；已有 launch/cache 的 page/pid/position/anonymity/replyInfo/board 通过旧 Java bean 存储契约保留。topic 缺失/不可解析仍现有 null 降级；UI/cache 不能被默认 tid=0 的假topic骗成有效。

解码模式必须明确：**scoped 模式**沿 ArticleErrors 前置 envelope/error 检查，严格 count/row shape、结构化正文产生 sourceUnavailable、缺完整body不伪成功；**legacy replay模式**保持现有跳过坏row和失败null等外层语义。mode不是网络fallback开关，core不触发重试/切账号/开WebView。新decoder的内部异常在app映射回原有typed failure/null，不能把所有异常合并成FORMAT改变fallback资格。

HTML顺序保持：用户和黑名单 → nested comments和附件映射/当前页面完整图床前缀 → normal-only WP source修正 → HtmlData → HTML/imageUrls。继续使用 `NgaImageHost.attachmentsPrefix`/历史域名规则。不得先render再补attachments，不从尚未初始化的parser字段取host。

### 6. 完整验收矩阵

| 场景族 | 必须验证的输入和观察项 |
| --- | --- |
| ordinary/App 与错误 | ordinary wrapper、BOM、既有数字token、标准/非标准json、空响应、HTML、error/__MESSAGE/msg、截断、尾随第二对象；保持FORMAT/CONTENT/EMPTY/AUTH/ACCESS/RATE_LIMIT/BUSINESS和fallback资格 |
| row与source | 有/缺/错型content/subject/alterinfo、数字正文、空字符串、缺__T/__U/__GROUPS、缺author、重复author、anonymous malformed名字；body不可编辑时仍用现有placeholder/菜单能力，不发明正文 |
| identity与paging | full/author/PID/search、PID-only resolvedTid、页面缺口/末页/unknown size、实际楼层不是adapter index、重复PID/错TID、account/generation/source变动；不固定App每页20 |
| 富文本与作者 | inline image/独立attachment/comment image、有效/异常图床前缀、评论自身avatar、黑名单render前生效、匿名UID不能可导航、OP nullable、客户端机型详情、WP `103 `、hotReplies `17`、骰子字段保持 |
| jdata/upload | 存档6个fixture+末字段/空格/嵌套/引号/反斜杠；未知malformed不能伪成功；NGA上传无引号data/url/attachments/check + error_code9原有重试；Noname头像data/error |
| 通知/消息/动作 | notification arrays、recent reply indexes、私信list/detail/allmsgs/userInfo+send error、profile JSON/web、Like/Report/PostComment/ProxyBridge 成功和业务失败各一；offline fake server，无写线上 |
| AI/profile安全边界 | 本已有unquoted fixture、@type/$ref顶层嵌套、不执行引用和类名、depth48/512KiB、非法单引号/注释/control chars、BigDecimal精度、无原文异常cause；__UCPUSER脚本提取规则不变 |
| 持久化 | consumer-inventory第3节**每一行**的旧→新/新→旧/新→新；数字别名、nullable/default、排序、重复ID、缓存raw/topicInfo原文、pageSize null、cache handle双向排除、错误owner/layout拒绝、atomic/tmp/bak恢复 |
| 草稿/Room | TopicPostFragment Bundle body/title/anony恢复、PostParam附件字段、Room User columns/version1与账号选择不变；无额外持久化迁移 |
| R8 | 完整注解/Signature保留、JavaBean marker包含嵌套bean、ResultBean/NonameUploadResponse精确保留；最小脱敏bean压缩后反射fixture或已获授权CI minified构建证据，不能仅debug结果当release证明 |

### 7. 验证命令与门槛

实施阶段按批先跑受影响单测，例如 core/common/message/app 的 `testDebugUnitTest`，然后项目强制门槛：

```bash
./gradlew :nga_phone_base_3.0:assembleDebug
./gradlew :nga_phone_base_3.0:testDebugUnitTest
./gradlew :nga_phone_base_3.0:lintDebug
./gradlew lintDebug --continue --rerun-tasks --console=plain
./gradlew testDebugUnitTest --continue
./gradlew :nga_phone_base_3.0:dependencyInsight --dependency fastjson --configuration debugRuntimeClasspath
./gradlew :nga_phone_base_3.0:dependencyInsight --dependency fastjson --configuration releaseRuntimeClasspath
```

全模块lint要检查生成报告零Error/Fatal而非只看进程exit。B2后重搜 `com.alibaba.fastjson.` imports、旧annotation、SerializerFeature、ParserConfig；历史研究/probe旧库对照和annotationProcessor依赖单独说明，不粗暴字符串归零。R8验证可运行不带签名/设备的独立缩减fixture；没有授权不得运行本地assembleRelease/Preview或设备测试。上述命令本研究未运行，规划与执行证据不能混写。

复现两个研究probe：将研究源码、真实TopicListBean和JavaBean用 `javac -d <临时目录>` 编译；classpath为本机两库JAR；运行 JsonBoundaryProbe，运行 JdataTreeProbe 并传存档 `research/probes/fixtures`。具体版本和实际结果在 `probes/README.md`。

### 8. Related specs / external references

相关spec清单与固定上游文件见 consumer-inventory.md。尤其 thread-detail-compat/thread-page-cache/thread-page-prefetch 决定旧 Java 适配保留边界，frontend/android-migration-architecture 要求渐进共享数据边界而非第三套架构。

外部固定参考：[Justwen 25652de8](https://github.com/Justwen/NGA-CLIENT-VER-OPEN-SOURCE/commit/25652de8dd6fc78646d074080239d3b194c7ddb8)、[a6e4a890](https://github.com/Justwen/NGA-CLIENT-VER-OPEN-SOURCE/commit/a6e4a89019f9fa7962479c2d6d82a891164a27cc)、[fastjson2](https://github.com/alibaba/fastjson2)。行为结论优先来自本次固定版本JAR probe，不从README宣传推性能收益。

## Caveats / Not Found

- 本研究只证明表内输入和源码关系，未执行全app单测/构建/R8/设备；B0/B2/B4门槛是后续实现验证，不是现已通过。
- 未发现必须改变持久化schema或必须全替换Java模型的依据；因此不留“稍后选格式”的开放项，也不建议双写新格式。
- U1新图标注解与原工作区R1–R6新增字段以真实整合结果增补；若最终并入源更改了既定业务行为，主会话评估范围，不借本任务修R7。
- 最终 design.md/implement.md 由主会话所有；本文件提供完整可审核建议和依据，不代替用户对 U1–U4 的一次整体确认。
