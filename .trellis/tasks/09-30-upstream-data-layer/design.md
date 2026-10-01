# U3 设计：数据边界与完整 JSON 迁移

状态：2026-09-30 用户已统一确认 U1–U4 全部方案，指定新会话实施；本轮未开始产品实施。上游七次重构 `1307d324` 至 `a6e4a890`，JSON 来源 `25652de8`，版本固定 `2.0.59.android8`。

## 终态与已确认基线

当前 app 的 JSON 生产消费者仍全部使用 fastjson1，core 虽声明 fastjson2 却没有相应生产 import；core_data 只有现有私信 Java 类型。已有 App 兼容 DTO/mapper、UID 楼主、匿名/client helper、严格普通解析与 renderer seam 已在 app 完成交付。
完整盘点得到 43 个直接生产 JSON 文件、12 个直接测试 JSON 文件，以及 62 个相关模型引用文件；所有路径/行号及隐式消费者见 [完整清单](research/consumer-inventory.md)。这不是“以后再盘点”的首批范围。

本项交付：

1. 所有 app runtime fastjson1 消费者统一到现有固定 fastjson2 版本；网络与本地数据分别保留原来的读取/写入语义。
2. 普通读取的原始字段 DTO 和纯解码进入 core_data/core，app 通过薄 mapper 接回既有 Java 展示/导航/缓存接口。
3. 保留 ThreadData、ThreadRowInfo、ThreadPageInfo 与 core HtmlData/AttachmentData/CommentData 的现有公共接口，不再造同义 ThreadInfo/ThreadPostInfo，不机械转换全部 Java。
4. 不改变持久化 schema、文件名、偏好键、Room 版本或 Bundle 草稿状态；不清缓存，不用数据重写换取迁移通过。

## 上游逐项采用决策

| 上游部分 | 决策与具体落点 |
| --- | --- |
| 原始 DTO 与展示状态分离、parser 下沉 | 采用边界；新增 Read*Wire + core decoder，保留 app legacy mapper 和现有公共 facade |
| 每次解析内复用作者对象 | 局部 UID cache，限单次 parse；不跨请求/账号共享 |
| Kotlin 展示类与所有消费者重命名 | 保留现有 Java 公共模型；当前分页/缓存/导航已依赖这些接口 |
| UID 楼主、匿名/client 工具、评论头像 | 已吸收部分保留；复用 ArticleAuthorSupport/ArticleRowPresentation，不再复制一套 |
| 上游简化客户端详情、删除 `17`/WP 正文 | 不采用；保留详细机型/系统、hotReplies 数据及 normal-only `103 ` 正文预处理 |
| HTML 模型搬家、GlobalConfig、ARouter IHtmlConfigService | 不采用机械搬家/全局服务；保留现有 renderer/blacklist seam 和完整图床 |
| 附件/评论、用户状态构造 | 适配现有完整字段，先映射附件/评论/黑名单再 render，不能引入上游先 HTML 后附件的缺陷 |
| 总行数驱动当前页遍历、App 固定20分页 | 不采用；当前页计数和总数分离，保留现行 variable page size、真实楼层和来源能力 |
| fastjson2 及无引号上传处理 | 全消费者切换，按操作设局部兼容选项，包含上传、AI/profile、本地格式与 R8 |
| jdata 删除 regex | 不采用；真实 DTO + 六种归档夹具已证明局部树解析→bean 保留内容且可兼容 |
| 工具清理 | 只删除迁移后无消费者的 helper 和已确认无调用的 app 私信转换副本；活跃 lib_bu_message parser 保留 |

逐提交对应关系及原始证据见 [迁移依据](research/migration-design-evidence.md)。

## JSON 入口契约

不建立全 App JSON 新框架，不改全局 JSONFactory/AutoType 设置；已有可测试入口直接适配，不机械增加 wrapper。

| 入口 | 固定迁移方式 | 必须保持 |
| --- | --- | --- |
| 旧 bean（网络/存储） | 显式字段名，必要时仅该 bean 入口使用 SupportSmartMatch | authorid/authorId、from_client/fromClient 等别名；不能静默变成0/null |
| TopicConvertFactory | 保留Topic入口已有wrapper清理（不加入普通读帖token修复）；先 `JSON.parseObject(raw, DisableReferenceDetect)` 获得 tree，再 `toJavaObject(TopicListBean.class, SupportSmartMatch)` | jdata 首/末/空格/嵌套六类输入、板块名、原文；仍校验 envelope/必要字段，错误不假装空成功 |
| NGA/头像上传 | 各自 payload decoder 局部 AllowUnQuotedFieldNames | 各自 data/error/error_code，现有压缩后重试语义不改；不统一猜错误 envelope |
| SafeJsonParser / ProfileWebUserParser | 局部 AllowUnQuotedFieldNames、DisableReferenceDetect、DisableSingleQuote | 现有深度/大小/字符/脚本提取 guard；@type/$ref 仍是普通数据，绝不启用类加载或引用替换 |
| 严格 owned cache | 保留原显式 type/owner/layout/version 检查与 WriteMapNullValue | pageSize:null、Int 字段、未知 owner/layout 拒绝；不能混用网络宽松解析设置 |
| 其他消息/通知/动作/profile | 按各自原 decoder、索引与错误语义迁移 API | GBK/wrapper/身份、列表次序、业务结果与取消语义不变 |

不启用 UseDoubleForDecimals、IgnoreCheckClose、全局 SupportSmartMatch、FieldBased 或 SupportAutoType。当前 BigDecimal 精度、尾随第二对象拒绝、现行 typed failure/fallback 资格继续由具体操作的契约约束。
研究 probe 发现新库对 `+123` 等 token 容忍可能不同；这不授权放宽操作协议或把未知/截断输入标为成功。字符修复与 schema 验证仍由现有入口所有。

## 普通读取数据分层

```text
现有网络/缓存来源与 NormalArticleParser / ArticleErrors
  → ArticleConvertFactory public facade（签名不变）
  → core ReadThreadWireDecoder（只解码和关联 raw DTO）
  → app ReadThreadLegacyMapper（还原现有 Java 展示/导航模型）
  → 既有 ArticleRowRenderer / HtmlConvertFactory
  → 原 ReaderSession / Presenter / UI
```

AppArticleParser 保留当前实现与来源/分页语义，继续共享既有 author/render helper；不为“统一 parser”把两种协议塞进一个猜测器。

具体边界：app保留raw、各入口现有wrapper/token预处理与ArticleErrors；将已经取得的data JSONObject和core自有ReadDecodeMode传入 `ReadThreadWireDecoder.decode(data, mode)`，返回typed wire或core shape问题。core不重新parse raw、不引用app的ArticleSource/ArticleFailure。app mapper根据原有scoped/legacy语义将shape问题映射到原FORMAT/CONTENT/null分支；不把新增decoder变成第二轮raw修复器，也不为此重写现有App parser。

### DTO 字段与有效性

新 DTO 位于 `lib_core_data/.../com/justwen/androidnga/core/data/thread/`：

- `ReadThreadWire`：`__T`、`__U`/`__GROUPS`、`__R`、`__GLOBAL._ATTACH_BASE_VIEW`、`__R__ROWS`、`__ROWS` 与当前已消费的可选页字段。保留 missing/null/wrong-type 状态，不能以空集合/0 默认抹掉严格判断所需证据。
- `ReadPostWire`：tid/fid/authorid/pid/lou、subject/content/alterinfo、vote/postdate/level/from_client/score、attachs/comment/`17`；保留正文 scalar validity 和字段存在性，以及 row 自带 author/isanonymous/avatar/mute/signature 等旧 fallback 字段与覆盖优先级。
- `ReadUserWire`：username/avatar/yz/mute_time/rvrc/signature/postnum/memberid/buffs；按现有匿名与声望换算规则映射，缺用户/组保留 row 资料，不丢整页。
- `ReadAttachmentWire`：aid/url_utf8_org_name/dscp/size/ext/name/thumb/attachurl/type/subid 全部保留；评论、附件及重复条目的原顺序保持。
- `ReadTopicWire`：tid/fid/author/authorid/lastposter/replies/subject/titlefont/type/topic_misc/postdate；映射旧 ThreadPageInfo 命名，topic 缺失或不可解析仍是原有 null 降级，不能生成 tid=0 假描述。

使用明确的纯 Kotlin DTO/结果表示原始有效性，core 不返回 app 模型或未解释的 JSONObject/ThreadRowInfo，不访问 Context、账号、缓存路径或网络。手动解码优先保留严格类型判断，不依赖智能匹配把 object/array stringify 成正文。
core_data 只新增 Kotlin/JVM17 配置，core 声明其依赖；不改变现有私信 Java 类型。

### 严格与旧缓存模式

scoped 路径保留现有 envelope/error 前置判断、当前页计数、行类型、sourceComplete 和正文可编辑能力；legacy replay 保留原先跳过坏行/返回 null 等语义。
模式只是解码契约，不是网络 fallback 开关。core 异常按具体来源映射回原有 failure/null，不能把所有异常统一成 FORMAT 后改变是否切来源。
具体保留：scoped 的坏 `__R`/count → FORMAT，声明范围内缺失或非对象 row → CONTENT；可选 topic 转换失败 → null；结构化 body → sourceUnavailable，不丢整页或改为 FORMAT。legacy 原有跳过坏 row、外层失败返回 null 的路径保持。这些映射由 app facade/mapper 承担，core 只报告对应 shape 问题。
HTML 顺序是用户/黑名单 → 完整附件/评论与页面图床 → normal-only WP 源文处理 → HtmlData → HTML/imageUrls。formattedHtml、imageUrls、ArticleRowPresentation 不从 wire 当权威字段读取。

## 持久化与 R8

[完整清单第3节](research/consumer-inventory.md) 的每一种格式都需要旧写→新读、新写→旧读、新写→新读的语义往返：搜索、历史、详情描述、旧/raw/owned缓存、ZIP、板块/收藏/主页顺序、屏蔽、属地缓存和隐式表情排序。
JSON 对象键顺序/空白不作为格式；raw response 和已有效的 topicInfo 原文必须保留。数组顺序、缺失/null、Int/Long、Unicode/转义、恢复文件与失败行为是格式契约。
草稿实际是 TopicPostFragment 的 Bundle `body/title/anony`；账号库仍是 Room version1；AI 编辑 draft/加密配置不经这些 JSON bean，不虚构 schema 迁移。

迁移 ThreadPageInfo cache handles、FilterKeyword.Pattern、BoardEntity.parentId 注解，并包含 U1 新 iconUrl getter/setter 的 serialize/deserialize=false。真实 encoder/decoder 与 shrinker fixture 验证注解作用，不只核对拼写。
ReportTask.ResultBean、AvatarFileUploadTask.NonameUploadResponse 补已有 JavaBean marker，复用精确 keep 契约；不加全 app keep。保留 Annotation/Signature 与 route/Parcelable 所需规则。
旧 fastjson keep 和运行时依赖在全消费者切换、反射/兼容门通过后一起删除；旧库可作为仅测试/研究 oracle，不准回到 APK runtime。构建工具 kapt 间接旧库单独列明，不能强行排除它导致编译失败。

## 分批、风险和回滚

完整批次是 B0 样本 → B1 纯解析测试入口 → B2 全 JSON2 切换 → B3 wire DTO/core decoder → B4 facade/mapper 接入 → B5 收尾。先隔离 JSON 默认行为变化，再改变模型边界。
B2 是所有跨模块 JSON 类型的一次完整切换提交，内部不交付半迁移 API；B3 可先编译/测试而暂不接生产，B4 再切现有 facade。
每批验收与具体文件见 [implement.md](implement.md)。保持旧 schema 使各批可代码回滚；B4 回滚不必撤 JSON2，B2 回滚必须整笔依赖/消费者一起撤回。
最大风险为别名静默丢值、null/type 变化、旧缓存字节改写及新 mapper 改变正文/错误语义；以上有针对性夹具与逐批门，不以“编译通过”代替行为验证。
R1–R6 的正式交付若加入基线，只补相应新增字段/消费者及既有回归；不从原脏树复制，不扩展 R7 产品修复。
