# Research: U3 JSON、模型消费者与持久化完整盘点

- Query: 盘点 U3 全部 fastjson1/2 消费者、反射/R8、网络和持久化边界，避免只规划首批。
- Scope: internal；固定独立工作区源码，加存档上游证据和离线双库 probe。
- Date: 2026-09-30

## Findings

### 1. 基线与结论

工作区 `/home/toph/nga-just-works-upstream-adoption`，父任务记录的基线 `1a8413d9`。原工作区其他会话未提交 R1–R6 不在此源码快照中；实施整合时重新按这份消费者清单对新增/改动文件核对，不能覆盖原会话源码。U1 先实施后新增的 `BoardEntity.iconUrl` 委托属性、getter/setter JSON 排除注解也属于 U3。

当前**应用 JSON 消费者全部仍为 fastjson1**；`lib_core` 虽声明 fastjson2，当前 core 生产源码没有 fastjson2 import。`lib_core_data` 当前仅四个私信 Java 数据类，不是上游整套 Kotlin 帖子模型。与此同时兼容接口 DTO/mapper、匿名/客户端 helper、UID 楼主判定、严格普通解析和 renderer seam 已在 app 存在，不应重复移植。

### 2. 依赖、类型绑定与反射边界

| 位置 | 当前事实 | 迁移责任 |
| --- | --- | --- |
| `build.gradle:10` | `fastJson_version = 1.1.71.android` | 所有生产/测试消费者切换完成后删除该旧版本变量 |
| `lib_base_common/build.gradle:58` | 对外 `api com.alibaba:fastjson`，因此多数模块无需显式声明即可使用 | 原子切为 `api(libs.fastjson2)`，不能早于调用者迁移 |
| `gradle/libs.versions.toml:4,21` / `lib_core/build.gradle:45` | `fastjson2:2.0.59.android8` 已固定；core implementation 已声明 | 继续同版本，不引入未使用的 fastjson2-kotlin，不借此升级到未知版本 |
| `lib_base_common/consumer-rules.pro:5` | 所有实现 `JavaBean` 的类及成员 keep | 新增反射 DTO 要实现现有 marker；手动解码 DTO 不需要靠反射 |
| `nga_phone_base_3.0/proguard.cfg:23` 起 | 保留 Signature、Annotation、Parcelable/Serializable；同时 keep fastjson1/2 | 保留元数据；完成切换和依赖检查后才能删 fastjson1 keep |
| `ThreadPageInfo.java:48-55` | cacheEntry/cacheSummary getter 和 setter 的 serialize/deserialize 都 false；成员 transient | 必须换成 fastjson2 注解且双向排除，防止缓存 handle 被网络/本地 JSON 注入 |
| `BoardEntity.kt:14` | parentId serialize=false，防止派生父关系存储；Kotlin getter/field 目标须核对 | 保留现有输出形状；U1 iconUrl getter/setter 的双向排除一起迁移 |
| `FilterKeyword.java:16` | 编译的 Pattern 不参与序列化 | 注解迁移后对比旧输出，不能把 Pattern 写入存储 |
| `ArticleCache.kt:105` | `SerializerFeature.WriteMapNullValue` | 改为 `JSONWriter.Feature.WriteMapNullValue`，显式 pageSize:null 不可省略 |
| `ArticleConvertFactory.java:116,175` | 仅两处**活跃** static `JSONObject.toJavaObject` | 改 instance API；DTO 抽取后按实际剩余调用迁移，不统计 `TopicConvertFactory` 注释里的旧代码为活跃入口 |
| `SafeJsonParser.java:20` / `ProfileWebUserParser.java:69` | 每次局部 ParserConfig + DisableSpecialKeyDetect + UseBigDecimal，无全局 parser 设置 | fastjson2 局部 DisableReferenceDetect；保持 @type 普通字段，绝不启用 SupportAutoType/SupportClassForName |
| `ReportTask.java:16` / `AvatarFileUploadTask.java:249` | `ResultBean`、`NonameUploadResponse` 被反射解析但未实现 JavaBean | 这两类补现有 JavaBean marker 或精确 keep，不能靠 keep 库自身保护 app bean |
| `lib_base_network/.../JsonStringConvertFactory.java:27` | Retrofit converter 只返回 String；reflect.Type 是 Retrofit 签名 | 不存在 Gson/Moshi/TypeToken 转换链需要迁移 |

源码全文检索没有生产 `TypeReference`/`TypeToken`、SerializeConfig 或自定义 ObjectReader/ObjectWriter。动态 Class.forName 都是 UI 路由/加载提示/fragment，非 JSON 自动类型加载。当前 cached fastjson 1.2.69 JAR 的存在不代表 app runtime 依赖；实施依赖核验须区分 app RuntimeClasspath 和 kapt/annotationProcessor，不能为“零旧库”把构建工具的依赖盲目排除。

反射 bean 清单：`TopicListBean` + DataBean/CUBean/FBean/TBean/PBean；`TopicPostBean` + DataBean/CUBean/FBean；`ThreadRowInfo`→`Map<String,Attachment>`；`ThreadPageInfo`→ReplyInfo；`ForumsListBean`→Result/Group/Forum；`BoardEntity` 自嵌套 children；旧 Board→BoardKey；FilterWordBean；FilterKeyword；`sp.phone.common.User`；ResultBean；NonameUploadResponse。`Map.class` 原始反射仅 TopicConvertFactory 的 sub_forums，必须保留其字符串/数字值处理。未直接 JSON 导入的数据类仍受 marker/字段命名影响。

### 3. 隐式消费者与所有持久化格式

| 存储/交接 | 读写入口与格式 | U3 保留及往返样例 |
| --- | --- | --- |
| 全局通用 List 偏好 | `PreferenceUtils.java:113,122`；JSONArray + Class | 唯一当前泛型 List 调用者为 `EmoticonOrderStore.java:45,66`；保留文件名顺序、缺失与异常回退；不能只测直接 JSON imports |
| 搜索历史 | `SearchModel.kt:15,28,41,102`，三类 String[] 偏好 | 原键/最多20/去重次序、Unicode/引号/反斜杠保留 |
| 最近主题 | `TopicHistoryManager.java:40,79`，ThreadPageInfo[]，最多40 | authorId、lastPoster、titleFont、topicMisc、postDate、anonymity、ReplyInfo、page/pid/position 等 getter 名字保持；不套上游重复 authorId/authorid 模型 |
| 详情导航描述 | `TopicHistoryFragment.java:131`、`TopicSearchFragment.java:285`、`ArticlePage.kt:132`、`ArticlePageCache.java:53` | JSON 字符串经 Intent/param；已有有效描述按缓存契约原文保存；extra 字段不被重新序列化剥除 |
| 旧主题缓存 | `ArticleCache.kt:166-198`，`files/cache/<tid>/<tid>.json` + `<page>.json` | 描述是旧 bean JSON，页是**原始普通响应**；不能批量重写/造 owner，原 parser 宽松入口保留 |
| 新缓存 v1 | `ArticleCache.kt:94-129`，thread-cache-v1/owner/tid/layout/{topic.json,pages/n.json} | schema/version/format/owner/tid/queryKind/layoutId/page/requestedPage/pageSize/pageBasis/raw；version/page/tid 等须保持 Int，pageSize 显式 null；未知版本/错误 owner/layout 不 fallback，不网络重读 |
| 旧缓存 zip | `LegacyArticleCacheArchive.kt:24,45` | 原始 UTF-8 描述/页字节与路径边界，不借库迁移改归档目录或纳入 owned 缓存 |
| 板块列表/远程原文 | `ForumBoardRepository.kt:18,31,51,248,258,277` | board_list.json、board_list_remote.json；保留 U1 schema/独立前缀偏好与每日节流，不做再次清库 |
| 板块收藏 | `ForumBoardRepository.kt:20,67-155`、`ForumBoardModel.kt:119-127` | board_bookmark.json + .tmp/.bak；旧 BOOKMARK_BOARD 的 Board[]；保留完整 staged write、恢复、有效空列表、根节点、稳定 key 和排序；U1 iconUrl 不落盘 |
| 首页排序 | `HomeBoardOrder.kt:37,69` | 字符串 ID[]，默认顺序写 null 意味恢复默认而非 JSON 字符串 null；保留已有未知ID/重复ID规则 |
| 屏蔽用户/关键词 | `FilterManager.kt:34-96` | global preference 向 filter DataStore 迁移已存在；User[] 与 FilterKeyword[]，enabled/keyword/null 字符串/旧 user 字段保持；不得因一次 parse 错误删除旧源 |
| 地域缓存 | `AuthorLocationStore.java:81-130` | version=1 + entries(origin/account/author/kind/location/observed/expires)；Long 时戳、null location、条数/体积/有效期/来源隔离；原 atomic move 不变 |
| 账号数据库 | `lib_bu_account/.../AppDatabase.java:16` | Room version=1 app_database.db，User columns；JSON 库不参与数据库 schema，过滤 User JSON 不授权改 Room column 或账号索引偏好 |
| 发帖草稿/编辑恢复 | `PostActivity.java:51-53`、`TopicPostFragment.java:82-97` | 实際保存为 Android Bundle 的 body/title/anony；不存在独立 JSON 草稿存储。保留 Bundle 键/附件提交流程，不虚构 schema migration |
| AI 设置草稿/密钥 | AiModelEditorState/AiProfilePromptEditorState、AI 设置存储 | UI 内存 draft + 原设置/加密密钥存储，不经 JSON bean；仅 AI HTTP payload/SSE/models 响应属于本次库迁移 |

持久化原则：旧库写→新库读、新库写→旧库读、再次新库读的语义往返；不要求 JSON 键顺序/空白一致。例外是 raw response 与已提供 topicInfo 必须原文保留，不能用格式化输出代替；存储路径/版本/偏好键不变，因此源代码批次可回退，无用户数据迁移或清缓存步骤。

### 4. 网络入口、共享边界与例外

- 普通读帖：ArticleConvertFactory 同时服务 legacy 宽松缓存和 NormalArticleParser 严格链。`__R__ROWS` 控制当前页，`__ROWS` 是总量；ArticleErrors 的失败分类、contentComplete、可选用户资料降级与各入口返回语义必须分别保持。
- App 读帖：ThreadAppBean + AppArticleParser 已实现 nullable page size、缺失元数据能力降级、typed shape 检查；禁止导入上游固定20分页/单一ThreadInfo结果替换。
- TopicConvertFactory：wrapper、TopicListBean、parent JSON 字符串/对象及 sub_forums Map；jdata 兼容采用树解析后 bean 映射，详见另文 probe；不会把无效/截断 envelope 伪装为空列表成功。
- TopicPostModel：发帖预读 TopicPostBean、推荐标签 data.0 索引、附件上传 window wrapper/error_code=9/attachments/attachments_check/url；仅迁移解析，保留压缩后最多原有重试分支，不增加写请求。
- AvatarFileUploadTask 与 AvatarPostActivity：第三方上传 ResultBean 和头像设置 data/error；属于两种不同 envelope，分别 fixture。
- LikeTask、ReportTask、PostCommentTask、ProxyBridge：data/error/__MESSAGE 的既有成功/失败语义，不改投票/贴条/举报请求体、账号或重试。
- 活跃私信 parser 是 `lib_bu_message/.../MessageConvertFactory.java`；app 同名副本当前无外部调用（在下面符号清单中保留为删除候选）。Private message list/detail/allmsgs/userInfo 与发送 data/error 分别覆盖。
- ProfileEnvelopeParser/JsonProfileLoadTask 与 Web `__UCPUSER` 提取是两条链；迁移库不能混用或放宽 HTML 脚本提取规则。
- AI SafeJsonParser 是有界通用树解析器。`AiResponseParserTest.java:163` 明确兼容无引号字段；`:172` 明确 @type/$ref 普通数据。`ProfileLocationParserTest.java:170` 同样覆盖特殊键。AllowUnQuotedFieldNames 必须按这两种既有入口和上传证据局部开启，不能武断声明“仅上传”，也不能全局开启。

### 5. 模型现状与上游对照

| 上游最终类型/工具 | 当前对应和消费者 | 完整采用判断 |
| --- | --- | --- |
| ThreadBean/ThreadPostBean/ThreadUserBean/ThreadPageBean/ThreadAttachBean | 普通 row/user 当前仍从 JSONObject 映射 ThreadRowInfo/Attachment/ThreadPageInfo | 采用原始字段与展示分离，新增精确 wire DTO；扩展保留 fork 字段/来源有效性。不得将结构化正文强制 stringify |
| ThreadInfo/ThreadPostInfo | ThreadData/ThreadRowInfo 已连着 app ArticlePagingInfo/ArticleRowPresentation；下附实际消费者 | 保留 Java 公共模型作展示/旧 UI 适配；不新增一套同义 display model 再全局重命名 |
| ThreadPageInfo Kotlin | 历史/搜索/收藏/缓存/导航共享 Java ThreadPageInfo | 保留 Java 类与持久化键，使用单一 wire→legacy mapper，不复制 upstream authorId/authorid 双字段 |
| ThreadInfoParse | ArticleConvertFactory + NormalArticleParser + ArticleErrors；App 单独 mapper | 只抽纯解码/DTO与解析内用户关联；账号/分页/缓存/错误策略仍由现有 app 链所有 |
| ForumParsekUtils/ThreadParseUtils | ArticleAuthorSupport 已复用匿名/client；头像经 FunctionUtils→NgaImageHost | 复用已有 helper，不复制第二份表或弱化图床规则。若移动纯 helper，旧 app wrapper 保留 Java 调用兼容 |
| IHtmlConfigService/HtmlConfigService | ArticleRowRenderer/ArticleBlacklist seam 已存在；renderRow/buildHtmlData 使用全局设置 | 不新增 ARouter service；本次若抽 rendering 使用显式单次 render 配置值，保留现有 seam；无必要不动此层 |
| HtmlData/AttachmentData/CommentData/GlobalConfig | core Java HTML 数据类型已与 app 解耦 | 本批保持位置/API/语言；仅以源文件搬家没有交付价值，不照搬 Kotlin conversion |
| UID OP / 匿名修正 / 评论头像 / 黑名单时序 | `ArticleConvertFactory.java:181-188,257-269,330`、AppArticleParser 已有 | 已吸收，验证保持即可；不宣称新功能 |

### 6. Related specs / external references

必须遵守 `.trellis/spec/backend/{thread-detail-compat-contract,thread-page-cache-contract,thread-page-prefetch-contract,author-profile-location-contract,ai-summary-contract,network-foundation-contract,nga-platform-access-rules,nga-platform-operation-registry,android-quality-guidelines}.md`，以及 `frontend/android-migration-architecture.md`。通用 database/error-handling/quality 文档仍是模板，不冒充可执行契约。

上游证据：父任务 source-index，存档 data-refactor.md **181行全文**和 json-compatibility.md **76行全文**已读；当前任务 upstream-data-refactor.diff / upstream-json.diff。固定版本为 Justwen `22ba3082501bcbb08f52a66d787f970f59c2dda7`；七提交 1307d324/0dbd80b4/0d6e9e50/86e3f65d/00352348/3596da00/a6e4a890，JSON 25652de8，后续兼容 93acf42a/750871b3。

## Caveats / Not Found

- 没有使用用户数据、NGA 请求、凭据、设备或签名构建；仅源码扫描和脱敏本机 JAR probe，不声称整个 app 已迁移或已通过测试。
- 没有 TypeReference、自定义反射 JSON factory 或 JSON 草稿存储；缓存 JSON2 jar 不等于 app 已切换。
- 原工作区 R1–R6 最终交付、U1 新属性必须在实施前按差异补入；任何改变 source/account/page 身份的方案回主会话审查，不纳入 R7 修复。

## Appendix: 自动枚举的完整直接消费者与展示模型引用

以下附录由当前工作区全量生产/测试源码扫描生成；行号属于本次基线，不包含 build 产物。

### 生产直接消费者（43 个源码文件）

| 文件 | 类型/行号 |
| --- | --- |
| `lib_base_common/src/main/java/gov/anzong/androidnga/base/util/PreferenceUtils.java` | `9: com.alibaba.fastjson.JSON;` |
| `lib_bu_message/src/main/java/com/justwen/androidnga/module/message/MessageConvertFactory.java` | `5: com.alibaba.fastjson.JSON;`<br>`6: com.alibaba.fastjson.JSONObject;` |
| `lib_bu_message/src/main/java/com/justwen/androidnga/module/message/compose/post/MessagePostRepository.kt` | `3: com.alibaba.fastjson.JSON`<br>`4: com.alibaba.fastjson.JSONObject` |
| `nga_phone_base_3.0/src/main/java/gov/anzong/androidnga/activity/AvatarPostActivity.java` | `20: com.alibaba.fastjson.JSON;`<br>`21: com.alibaba.fastjson.JSONObject;` |
| `nga_phone_base_3.0/src/main/java/gov/anzong/androidnga/activity/compose/SearchModel.kt` | `3: com.alibaba.fastjson.JSON` |
| `nga_phone_base_3.0/src/main/java/gov/anzong/androidnga/activity/compose/board/ForumBoardModel.kt` | `3: com.alibaba.fastjson.JSON` |
| `nga_phone_base_3.0/src/main/java/gov/anzong/androidnga/activity/compose/board/ForumBoardRepository.kt` | `4: com.alibaba.fastjson.JSON` |
| `nga_phone_base_3.0/src/main/java/gov/anzong/androidnga/activity/compose/board/HomeBoardOrder.kt` | `3: com.alibaba.fastjson.JSON` |
| `nga_phone_base_3.0/src/main/java/gov/anzong/androidnga/activity/compose/board/data/BoardEntity.kt` | `4: com.alibaba.fastjson.annotation.JSONField` |
| `nga_phone_base_3.0/src/main/java/gov/anzong/androidnga/activity/compose/filter/FilterKeyword.java` | `6: com.alibaba.fastjson.annotation.JSONField;` |
| `nga_phone_base_3.0/src/main/java/gov/anzong/androidnga/activity/compose/filter/FilterManager.kt` | `3: com.alibaba.fastjson.JSON` |
| `nga_phone_base_3.0/src/main/java/gov/anzong/androidnga/activity/compose/filter/FilterWordModel.kt` | `3: com.alibaba.fastjson.JSON` |
| `nga_phone_base_3.0/src/main/java/sp/phone/ai/AiMessageAccumulator.java` | `3: com.alibaba.fastjson.JSONArray;`<br>`4: com.alibaba.fastjson.JSONObject;` |
| `nga_phone_base_3.0/src/main/java/sp/phone/ai/AiResponseParser.java` | `3: com.alibaba.fastjson.JSONArray;`<br>`4: com.alibaba.fastjson.JSONObject;` |
| `nga_phone_base_3.0/src/main/java/sp/phone/ai/AiSummaryClient.java` | `3: com.alibaba.fastjson.JSON;` |
| `nga_phone_base_3.0/src/main/java/sp/phone/ai/SafeJsonParser.java` | `3: com.alibaba.fastjson.JSON;`<br>`4: com.alibaba.fastjson.JSONObject;`<br>`5: com.alibaba.fastjson.parser.Feature;`<br>`6: com.alibaba.fastjson.parser.ParserConfig;` |
| `nga_phone_base_3.0/src/main/java/sp/phone/ai/summary/NgaProfilePageSource.java` | `3: com.alibaba.fastjson.JSONObject;` |
| `nga_phone_base_3.0/src/main/java/sp/phone/common/TopicHistoryManager.java` | `6: com.alibaba.fastjson.JSON;` |
| `nga_phone_base_3.0/src/main/java/sp/phone/mvp/model/TopicPostModel.java` | `9: com.alibaba.fastjson.JSON;`<br>`10: com.alibaba.fastjson.JSONObject;` |
| `nga_phone_base_3.0/src/main/java/sp/phone/mvp/model/convert/ArticleConvertFactory.java` | `5: com.alibaba.fastjson.JSON;`<br>`6: com.alibaba.fastjson.JSONObject;` |
| `nga_phone_base_3.0/src/main/java/sp/phone/mvp/model/convert/ErrorConvertFactory.java` | `3: com.alibaba.fastjson.JSON;`<br>`4: com.alibaba.fastjson.JSONObject;` |
| `nga_phone_base_3.0/src/main/java/sp/phone/mvp/model/convert/ForumNotificationFactory.java` | `4: com.alibaba.fastjson.JSONArray;`<br>`5: com.alibaba.fastjson.JSONObject;` |
| `nga_phone_base_3.0/src/main/java/sp/phone/mvp/model/convert/MessageConvertFactory.java` | `3: com.alibaba.fastjson.JSON;`<br>`4: com.alibaba.fastjson.JSONObject;` |
| `nga_phone_base_3.0/src/main/java/sp/phone/mvp/model/convert/TopicConvertFactory.java` | `5: com.alibaba.fastjson.JSON;`<br>`6: com.alibaba.fastjson.JSONObject;` |
| `nga_phone_base_3.0/src/main/java/sp/phone/mvp/model/entity/ThreadPageInfo.java` | `4: com.alibaba.fastjson.annotation.JSONField;` |
| `nga_phone_base_3.0/src/main/java/sp/phone/mvp/model/thread/ArticleCache.kt` | `3: com.alibaba.fastjson.JSON`<br>`4: com.alibaba.fastjson.JSONObject`<br>`5: com.alibaba.fastjson.serializer.SerializerFeature`<br>`181: } catch (_: IOException) { null } catch (_: IllegalArgumentException) { null } catch (_: com.alibaba.fastjson.JSONException) { null }` |
| `nga_phone_base_3.0/src/main/java/sp/phone/mvp/model/thread/ArticleFailure.kt` | `3: com.alibaba.fastjson.JSON`<br>`4: com.alibaba.fastjson.JSONObject` |
| `nga_phone_base_3.0/src/main/java/sp/phone/mvp/model/thread/ArticlePage.kt` | `132: try { com.alibaba.fastjson.JSON.parseObject(it).getIntValue("tid") == tid }` |
| `nga_phone_base_3.0/src/main/java/sp/phone/mvp/model/thread/ThreadAppBean.kt` | `3: com.alibaba.fastjson.JSONArray`<br>`4: com.alibaba.fastjson.JSONObject` |
| `nga_phone_base_3.0/src/main/java/sp/phone/mvp/presenter/ArticlePageCache.java` | `3: com.alibaba.fastjson.JSON;`<br>`4: com.alibaba.fastjson.JSONException;` |
| `nga_phone_base_3.0/src/main/java/sp/phone/profile/AuthorLocationStore.java` | `3: com.alibaba.fastjson.JSON;`<br>`4: com.alibaba.fastjson.JSONArray;`<br>`5: com.alibaba.fastjson.JSONObject;` |
| `nga_phone_base_3.0/src/main/java/sp/phone/profile/ProfileEnvelopeParser.java` | `3: com.alibaba.fastjson.JSON;`<br>`4: com.alibaba.fastjson.JSONObject;` |
| `nga_phone_base_3.0/src/main/java/sp/phone/profile/ProfileLocationParser.java` | `3: com.alibaba.fastjson.JSONObject;` |
| `nga_phone_base_3.0/src/main/java/sp/phone/profile/ProfileWebUserParser.java` | `3: com.alibaba.fastjson.JSON;`<br>`4: com.alibaba.fastjson.JSONObject;`<br>`5: com.alibaba.fastjson.parser.Feature;`<br>`6: com.alibaba.fastjson.parser.ParserConfig;` |
| `nga_phone_base_3.0/src/main/java/sp/phone/proxy/ProxyBridge.java` | `8: com.alibaba.fastjson.JSON;`<br>`9: com.alibaba.fastjson.JSONObject;` |
| `nga_phone_base_3.0/src/main/java/sp/phone/task/AvatarFileUploadTask.java` | `11: com.alibaba.fastjson.JSON;` |
| `nga_phone_base_3.0/src/main/java/sp/phone/task/JsonProfileLoadTask.java` | `3: com.alibaba.fastjson.JSONObject;` |
| `nga_phone_base_3.0/src/main/java/sp/phone/task/LikeTask.java` | `3: com.alibaba.fastjson.JSON;`<br>`4: com.alibaba.fastjson.JSONObject;` |
| `nga_phone_base_3.0/src/main/java/sp/phone/task/PostCommentTask.java` | `7: com.alibaba.fastjson.JSON;`<br>`8: com.alibaba.fastjson.JSONObject;` |
| `nga_phone_base_3.0/src/main/java/sp/phone/task/ReportTask.java` | `3: com.alibaba.fastjson.JSON;` |
| `nga_phone_base_3.0/src/main/java/sp/phone/task/SearchBoardTask.java` | `3: com.alibaba.fastjson.JSON;`<br>`4: com.alibaba.fastjson.JSONObject;` |
| `nga_phone_base_3.0/src/main/java/sp/phone/ui/fragment/TopicHistoryFragment.java` | `18: com.alibaba.fastjson.JSON;` |
| `nga_phone_base_3.0/src/main/java/sp/phone/ui/fragment/TopicSearchFragment.java` | `18: com.alibaba.fastjson.JSON;` |

### 测试直接消费者（12 个源码文件）

| 文件 | 类型/行号 |
| --- | --- |
| `nga_phone_base_3.0/src/test/java/gov/anzong/androidnga/activity/compose/board/HomeBoardOrderContractTest.kt` | `3: com.alibaba.fastjson.JSON` |
| `nga_phone_base_3.0/src/test/java/sp/phone/ai/AiResponseParserTest.java` | `8: com.alibaba.fastjson.JSON;`<br>`9: com.alibaba.fastjson.JSONObject;` |
| `nga_phone_base_3.0/src/test/java/sp/phone/ai/AiStreamParserTest.java` | `8: com.alibaba.fastjson.JSON;`<br>`9: com.alibaba.fastjson.JSONObject;` |
| `nga_phone_base_3.0/src/test/java/sp/phone/ai/AiSummaryClientTest.java` | `11: com.alibaba.fastjson.JSONObject;`<br>`176: + com.alibaba.fastjson.JSON.toJSONString(answer)` |
| `nga_phone_base_3.0/src/test/java/sp/phone/ai/summary/NgaProfilePageSourceTest.java` | `10: com.alibaba.fastjson.JSON;`<br>`11: com.alibaba.fastjson.JSONObject;` |
| `nga_phone_base_3.0/src/test/java/sp/phone/mvp/model/convert/ArticleConvertFactoryTest.java` | `5: com.alibaba.fastjson.JSONObject;` |
| `nga_phone_base_3.0/src/test/java/sp/phone/mvp/model/thread/AppArticleParserTest.kt` | `3: com.alibaba.fastjson.JSON`<br>`4: com.alibaba.fastjson.JSONArray`<br>`5: com.alibaba.fastjson.JSONObject` |
| `nga_phone_base_3.0/src/test/java/sp/phone/mvp/model/thread/ArticleCacheStoreTest.kt` | `3: com.alibaba.fastjson.JSON`<br>`36: catch (_: com.alibaba.fastjson.JSONException) { }` |
| `nga_phone_base_3.0/src/test/java/sp/phone/mvp/model/thread/NormalArticleParserTest.kt` | `3: com.alibaba.fastjson.JSON` |
| `nga_phone_base_3.0/src/test/java/sp/phone/mvp/presenter/ArticlePageCacheTest.java` | `11: com.alibaba.fastjson.JSON;` |
| `nga_phone_base_3.0/src/test/java/sp/phone/profile/AuthorLocationStoreTest.java` | `9: com.alibaba.fastjson.JSON;`<br>`10: com.alibaba.fastjson.JSONObject;` |
| `nga_phone_base_3.0/src/test/java/sp/phone/profile/ProfileLocationParserTest.java` | `6: com.alibaba.fastjson.JSONObject;` |

### 模型直接引用（62 个生产文件）

| 文件 | 类型/行号 |
| --- | --- |
| `lib_bu_message/src/main/java/com/justwen/androidnga/module/message/MessageConvertFactory.java` | `ThreadPageInfo` |
| `lib_core/src/main/java/gov/anzong/androidnga/core/HtmlConvertFactory.java` | `HtmlData` |
| `lib_core/src/main/java/gov/anzong/androidnga/core/corebuild/HtmlAttachmentBuilder.java` | `HtmlData`<br>`AttachmentData` |
| `lib_core/src/main/java/gov/anzong/androidnga/core/corebuild/HtmlBuilder.java` | `HtmlData` |
| `lib_core/src/main/java/gov/anzong/androidnga/core/corebuild/HtmlCommentBuilder.java` | `HtmlData`<br>`CommentData` |
| `lib_core/src/main/java/gov/anzong/androidnga/core/corebuild/HtmlSignatureBuilder.java` | `HtmlData` |
| `lib_core/src/main/java/gov/anzong/androidnga/core/corebuild/HtmlVoteBuilder.java` | `HtmlData` |
| `lib_core/src/main/java/gov/anzong/androidnga/core/corebuild/IHtmlBuild.java` | `HtmlData` |
| `lib_core/src/main/java/gov/anzong/androidnga/core/data/AttachmentData.java` | `AttachmentData` |
| `lib_core/src/main/java/gov/anzong/androidnga/core/data/CommentData.java` | `CommentData` |
| `lib_core/src/main/java/gov/anzong/androidnga/core/data/HtmlData.java` | `HtmlData`<br>`AttachmentData`<br>`CommentData` |
| `lib_core/src/main/java/gov/anzong/androidnga/core/decode/ForumAlbumDecoder.java` | `HtmlData` |
| `lib_core/src/main/java/gov/anzong/androidnga/core/decode/ForumBasicDecoder.java` | `HtmlData` |
| `lib_core/src/main/java/gov/anzong/androidnga/core/decode/ForumDecoder.java` | `HtmlData` |
| `lib_core/src/main/java/gov/anzong/androidnga/core/decode/ForumDiceDecoder.kt` | `HtmlData` |
| `lib_core/src/main/java/gov/anzong/androidnga/core/decode/ForumImageDecoder.java` | `HtmlData` |
| `lib_core/src/main/java/gov/anzong/androidnga/core/decode/ForumVoteDecoder.java` | `HtmlData` |
| `lib_core/src/main/java/gov/anzong/androidnga/core/decode/IForumDecoder.java` | `HtmlData` |
| `nga_phone_base_3.0/src/main/java/gov/anzong/androidnga/activity/ProfileActivity.java` | `HtmlData` |
| `nga_phone_base_3.0/src/main/java/gov/anzong/androidnga/activity/compose/filter/FilterManager.kt` | `ThreadPageInfo` |
| `nga_phone_base_3.0/src/main/java/gov/anzong/androidnga/ui/fragment/TopicListBaseFragment.kt` | `ThreadPageInfo` |
| `nga_phone_base_3.0/src/main/java/gov/anzong/androidnga/ui/fragment/TopicListSimpleFragment.kt` | `ThreadPageInfo` |
| `nga_phone_base_3.0/src/main/java/sp/phone/ai/summary/FloorSummaryInput.java` | `ThreadData`<br>`ThreadRowInfo` |
| `nga_phone_base_3.0/src/main/java/sp/phone/common/TopicHistoryManager.java` | `ThreadPageInfo` |
| `nga_phone_base_3.0/src/main/java/sp/phone/http/bean/Attachment.java` | `Attachment` |
| `nga_phone_base_3.0/src/main/java/sp/phone/http/bean/ThreadData.java` | `ThreadData`<br>`ThreadRowInfo`<br>`ThreadPageInfo` |
| `nga_phone_base_3.0/src/main/java/sp/phone/http/bean/ThreadRowInfo.java` | `ThreadRowInfo`<br>`Attachment` |
| `nga_phone_base_3.0/src/main/java/sp/phone/mvp/contract/ArticleListContract.java` | `ThreadData`<br>`ThreadRowInfo` |
| `nga_phone_base_3.0/src/main/java/sp/phone/mvp/contract/TopicListContract.java` | `ThreadPageInfo` |
| `nga_phone_base_3.0/src/main/java/sp/phone/mvp/model/ArticleListModel.java` | `ThreadData` |
| `nga_phone_base_3.0/src/main/java/sp/phone/mvp/model/TopicListModel.java` | `ThreadPageInfo` |
| `nga_phone_base_3.0/src/main/java/sp/phone/mvp/model/convert/ArticleConvertFactory.java` | `ThreadData`<br>`ThreadRowInfo`<br>`ThreadPageInfo`<br>`Attachment`<br>`HtmlData`<br>`AttachmentData`<br>`CommentData` |
| `nga_phone_base_3.0/src/main/java/sp/phone/mvp/model/convert/MessageConvertFactory.java` | `ThreadPageInfo` |
| `nga_phone_base_3.0/src/main/java/sp/phone/mvp/model/convert/TopicConvertFactory.java` | `ThreadPageInfo` |
| `nga_phone_base_3.0/src/main/java/sp/phone/mvp/model/entity/ThreadPageInfo.java` | `ThreadPageInfo` |
| `nga_phone_base_3.0/src/main/java/sp/phone/mvp/model/entity/TopicListInfo.java` | `ThreadPageInfo` |
| `nga_phone_base_3.0/src/main/java/sp/phone/mvp/model/thread/AppArticleParser.kt` | `ThreadData`<br>`ThreadRowInfo`<br>`ThreadPageInfo` |
| `nga_phone_base_3.0/src/main/java/sp/phone/mvp/model/thread/ArticleCache.kt` | `ThreadData`<br>`ThreadPageInfo` |
| `nga_phone_base_3.0/src/main/java/sp/phone/mvp/model/thread/ArticlePage.kt` | `ThreadData`<br>`ThreadRowInfo` |
| `nga_phone_base_3.0/src/main/java/sp/phone/mvp/model/thread/ArticleReaderSession.kt` | `ThreadData` |
| `nga_phone_base_3.0/src/main/java/sp/phone/mvp/model/thread/ArticleRowPresentation.kt` | `ThreadRowInfo` |
| `nga_phone_base_3.0/src/main/java/sp/phone/mvp/model/thread/NormalArticleParser.kt` | `ThreadData` |
| `nga_phone_base_3.0/src/main/java/sp/phone/mvp/model/thread/ThreadAppBean.kt` | `ThreadData` |
| `nga_phone_base_3.0/src/main/java/sp/phone/mvp/presenter/ArticleListPresenter.java` | `ThreadData`<br>`ThreadRowInfo` |
| `nga_phone_base_3.0/src/main/java/sp/phone/mvp/presenter/ArticlePageCache.java` | `ThreadData`<br>`ThreadPageInfo` |
| `nga_phone_base_3.0/src/main/java/sp/phone/mvp/presenter/TopicListPresenter.java` | `ThreadPageInfo` |
| `nga_phone_base_3.0/src/main/java/sp/phone/mvp/viewmodel/ArticleShareViewModel.java` | `ThreadData` |
| `nga_phone_base_3.0/src/main/java/sp/phone/param/TopicTitleHelper.java` | `ThreadPageInfo` |
| `nga_phone_base_3.0/src/main/java/sp/phone/profile/ArticleAuthorIds.java` | `ThreadData`<br>`ThreadRowInfo` |
| `nga_phone_base_3.0/src/main/java/sp/phone/profile/AuthorLocationPage.java` | `ThreadData` |
| `nga_phone_base_3.0/src/main/java/sp/phone/profile/AuthorLocationService.java` | `ThreadData` |
| `nga_phone_base_3.0/src/main/java/sp/phone/ui/adapter/ArticleBodyViews.java` | `ThreadData`<br>`ThreadRowInfo` |
| `nga_phone_base_3.0/src/main/java/sp/phone/ui/adapter/ArticleListAdapter.java` | `ThreadData`<br>`ThreadRowInfo` |
| `nga_phone_base_3.0/src/main/java/sp/phone/ui/adapter/ReplyListAdapter.java` | `ThreadPageInfo` |
| `nga_phone_base_3.0/src/main/java/sp/phone/ui/adapter/TopicListAdapter.java` | `ThreadPageInfo` |
| `nga_phone_base_3.0/src/main/java/sp/phone/ui/fragment/ArticleListFragment.java` | `ThreadData`<br>`ThreadRowInfo` |
| `nga_phone_base_3.0/src/main/java/sp/phone/ui/fragment/TopicCacheFragment.java` | `ThreadPageInfo` |
| `nga_phone_base_3.0/src/main/java/sp/phone/ui/fragment/TopicFavoriteFragment.java` | `ThreadPageInfo` |
| `nga_phone_base_3.0/src/main/java/sp/phone/ui/fragment/TopicHistoryFragment.java` | `ThreadPageInfo` |
| `nga_phone_base_3.0/src/main/java/sp/phone/ui/fragment/TopicSearchFragment.java` | `ThreadPageInfo` |
| `nga_phone_base_3.0/src/main/java/sp/phone/util/FunctionUtils.java` | `ThreadRowInfo`<br>`HtmlData` |
| `nga_phone_base_3.0/src/main/java/sp/phone/util/HtmlUtils.java` | `ThreadRowInfo`<br>`Attachment`<br>`HtmlData` |
