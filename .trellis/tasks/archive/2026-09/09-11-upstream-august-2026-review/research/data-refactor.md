# Research: 上游数据重构、fastjson2 迁移与旧代码清理

- Query: 核对 `1307d324`、`0dbd80b4`、`0d6e9e50`、`86e3f65d`、`00352348`、`3596da00`、`a6e4a890` 七次数据重构，以及 `25652de8`、`4c99051f`，判断对当前 fork 的价值、行为变化、依赖与适合采用的粒度。
- Scope: mixed；以本地提供的上游完整补丁、固定版本源码和当前 fork 源码为主；补充本任务主会话执行的合成 JSON 离线实验。
- Date: 2026-09-11

## Findings

### 1. 判断与证据边界

**值得借鉴的是接口字段与展示模型分离、解析器移出应用模块，以及按 UID 判断楼主；不建议按这七个提交整组搬运。** 它们整理了职责和命名，但最终仍是 Java MVP/RxJava 页面调用可变 Kotlin 模型、同步生成 HTML，不是已经完成的 Kotlin + Compose + MVVM 架构。源码同时显示附件渲染顺序、缺失用户资料处理等新的问题。

**fastjson2 可以作为单独的依赖迁移评估，必须先补兼容用例。** 当前 fork 已在 `lib_core` 使用 fastjson2，却仍通过 `lib_base_common` 给其余模块提供 fastjson1；因此上游的价值是统一依赖及配套调用方式。本任务的离线实验已证明，直接升级会改变两类非标准 JSON 的接受行为。

**`4c99051f` 主要是维护清理，直接用户收益有限。** 可以独立清理确认无调用者的代码，并为后续删除旧模型减少编译依赖；不能把它描述为图片加载、网络或启动性能优化。

固定比较对象：

- `U`：Justwen `master_release@22ba3082501bcbb08f52a66d787f970f59c2dda7`。下文最终行为引用该版本，提交表另标明变化的引入提交。
- `F`：当前 fork `main@8284c703`；共同的固定上游基线为 `5d807617`。
- 提交日期采用 committer date，时区 `+08:00`。例如 `3596da00` 作者日期是 8 月 11 日，实际提交日期是 8 月 23 日；不能按作者日期推断它早于 8 月 15 日的数据重构。
- “源码事实”指实际字段、调用顺序、删除和分支；“静态推断”指由这些代码推导的输入边界风险，未声称已在设备或 NGA 线上复现。除明确引用主会话的 JSON 实验外，本报告不报告运行时验证结果。

### 2. 文件索引

| 文件 | 作用与版本 |
| --- | --- |
| `lib_core_data/src/main/java/com/client/androidnga/core/data/bean/ThreadBean.kt` | U 的 `read.php data` DTO 与 `ThreadUserBean`；记录 `__T/__U/__R/__ROWS` 等接口字段。 |
| `lib_core_data/src/main/java/com/client/androidnga/core/data/bean/ThreadPostBean.kt` | U 的楼层 DTO；最终不再内嵌展示对象。 |
| `lib_core_data/src/main/java/com/client/androidnga/core/data/model/ThreadInfo.kt` | U 的页、楼层展示模型及 `ClientModel`；仍为可变属性。 |
| `lib_core_data/src/main/java/com/client/androidnga/core/data/model/ThreadPageInfo.kt` | U 的主题列表/页元信息；同时被历史、收藏、搜索等旧页面使用。 |
| `lib_core/src/main/java/com/client/androidnga/core/parse/ThreadInfoParse.kt` | U 最终的 `read.php` DTO→展示模型→HTML 主入口。 |
| `lib_core/src/main/java/com/client/androidnga/core/parse/ForumParseUtils.kt` | U 的头像字符串提取、站点错误消息解析；实际对象名是 `ForumParsekUtils`。 |
| `lib_core/src/main/java/gov/anzong/androidnga/core/IHtmlConfigService.kt` | U 解析/渲染侧使用的设置接口。 |
| `nga_phone_base_3.0/src/main/java/gov/anzong/androidnga/service/HtmlConfigService.kt` | U 的设置适配器；仍调用应用全局管理器。 |
| `lib_core/src/main/java/gov/anzong/androidnga/core/HtmlConvertFactory.java` | U 新增从 `ThreadPostInfo` 构造 HTML 输入的重载；F 已有较底层的 HTML 转换器。 |
| `nga_phone_base_3.0/src/main/java/sp/phone/mvp/model/convert/ArticleConvertFactory.java` | F 的现用解析器；接口解码、用户状态、图床和 HTML 拼装混合在一起；U 在 `a6e4a890` 删除。 |
| `nga_phone_base_3.0/src/main/java/sp/phone/mvp/model/ArticleListModel.java` | 两边的网络/本地缓存接入点；上游重构更换 parser，但仍沿用原 Rx 调用方式。 |
| `nga_phone_base_3.0/src/main/java/sp/phone/mvp/presenter/ArticleListPresenter.java` | F 在这里实现预取状态与前后台失败分离，是移植时的主要冲突点。 |
| `nga_phone_base_3.0/src/main/java/sp/phone/ui/fragment/ArticleListFragment.java`、`sp/phone/ui/adapter/ArticleListAdapter.java`（同一源码根目录） | 页面、菜单、客户端信息与 HTML 展示；上游类型迁移涉及这些消费者。 |
| `build.gradle`、`gradle/libs.versions.toml`、`lib_base_common/build.gradle`、`lib_core/build.gradle`、`lib_core_data/build.gradle` | JSON 库版本、对外依赖、Kotlin 插件与模块依赖变化。 |

### 3. 九个提交分别做了什么

| 提交 / 提交日期 | 实际变化 | 对 fork 的意义 |
| --- | --- | --- |
| [`4c99051f`](https://github.com/Justwen/NGA-CLIENT-VER-OPEN-SOURCE/commit/4c99051fe1e52be4b2756374c4a912bddf876ff3) / 08-08 | 删除 `HtmlUtils`、`MD5Util`、`PluginUtils`；删除 `HttpUtil` 的旧 `getHtml/getCharset/downImage3`、若干旧常量；删除 `ImageUtils` 的 Drawable 缩放、旧头像流/缓存辅助函数和两个 `StringUtils` 函数；同步移除 Adapter 对 `HtmlUtils` 的初始化。 | 大部分是无活跃调用者的旧实现。适合单独的小范围清理；它还消除了随后被删除的 `ThreadRowInfo` 的一组旧源码引用。 |
| [`1307d324`](https://github.com/Justwen/NGA-CLIENT-VER-OPEN-SOURCE/commit/1307d324a48c06a5d48e7fcc5e9ccc331e3caa69) / 08-08 | 将 app 内的 `Attachment`、`ThreadRowInfo`、`ThreadData`、`ThreadPageInfo` 迁为 `lib_core_data` 的 Kotlin 类，改名为 `ThreadAttachBean`、`ThreadPostBean`、`ThreadInfo`、`ThreadPageInfo`；大量 getter/setter 改为 `@JvmField` 访问；为已有的 `lib_core_data` 模块开启 Kotlin/JVM 17。主题列表、历史、收藏、搜索、过滤、详情页的类型引用一起改。 | 主要是搬迁/命名和 Java↔Kotlin 调用调整；并未增加网络或缓存功能。首次提交仍把接口字段与展示状态混在 `ThreadPostBean`，不能把它单独当作完成后的架构。 |
| [`0dbd80b4`](https://github.com/Justwen/NGA-CLIENT-VER-OPEN-SOURCE/commit/0dbd80b446ed38ebdfd928790570209a8e77a094) / 08-08 | 引入 `ThreadPostInfo`，先迁移 `formatHtml/isBlocked/clientModel`；`ThreadPostBean` 暂时持有它；引入 `ClientModel` 枚举并区分浏览器/客户端。客户端按钮的 Toast 从详细来源、机型、系统信息缩为“发送自”加枚举名称。 | 拆分思路可取，Toast 信息减少是明确的产品变化，不能在结构整理时顺手带入。 |
| [`25652de8`](https://github.com/Justwen/NGA-CLIENT-VER-OPEN-SOURCE/commit/25652de8dd6fc78646d074080239d3b194c7ddb8) / 08-09 | 移除 `fastJson_version=1.1.71.android`，把 common 的 `api` 改为 fastjson2；迁移 `JSON/JSONObject/JSONArray/JSONField` 导入以及两处 `JSONObject.toJavaObject` 静态调用。影响网络响应、上传、私信、过滤器、历史/搜索/版面偏好序列化。另增加 `fastjson2-kotlin` catalog 别名。 | 可以和数据类重命名分开设计；但不是只改 Gradle 版本号。需要保留旧数据格式并覆盖非标准响应。 |
| [`0d6e9e50`](https://github.com/Justwen/NGA-CLIENT-VER-OPEN-SOURCE/commit/0d6e9e5058d47dbf56bd41fd6a9815abb2fdc398) / 08-09 | 增加 `lib_core → lib_core_data` 依赖；最初的 `ThreadInfoParse` 只抽出客户端类型、匿名名转换；引入 `ThreadUserBean`，将匿名、禁言、威望、发帖数、用户组逐步移入 `ThreadPostInfo`。 | 开始把 app 的协议处理移到 core；此时主 parser 仍在 app。现有 fork 已处理这些用户信息，不能算新功能。 |
| [`86e3f65d`](https://github.com/Justwen/NGA-CLIENT-VER-OPEN-SOURCE/commit/86e3f65dcbb01c627c6fbdd23d4bc8fb46c82506) / 08-15 | `ThreadBean` 开始描述 `data`；wrapper/数字 token 修复和用户状态逻辑进入 Kotlin parser；评论改用 DTO map；通过主题作者 UID 标记楼主，移除共享 ViewModel 中保存楼主名字的机制。删除旧 app `MessageConvertFactory` 和旧 BBCode 辅助代码，同时删除 `"17"` 热门回复解析和 `from_client=103` 的旧正文解码。 | UID 判断值得独立借鉴。DTO 的引入不代表已完整保留响应字段。删除热门回复信息和旧正文兼容需单独决定；此阶段还出现匿名判断误用 `nickname` 的中间状态。 |
| [`00352348`](https://github.com/Justwen/NGA-CLIENT-VER-OPEN-SOURCE/commit/0035234899853f65ac90432329206778eeaa8341) / 08-16 | `ThreadPostInfo` 接管楼层身份、正文、主题、投票、图片列表等；新增 `ThreadBasicInfo.attachHost`；详情 Presenter、Adapter、Fragment 改为消费展示对象；评论判断移至转换阶段。修正上一阶段评论头像错误使用父楼层头像的问题。 | 完成了一段有价值的消费者隔离，但对本 fork 仍需要改动回复/引用/菜单/图片等多个入口。评论头像修正是上游迁移过程中的回归修正，F 本来取评论自身头像。 |
| [`3596da00`](https://github.com/Justwen/NGA-CLIENT-VER-OPEN-SOURCE/commit/3596da00f315a0aa224f07c06905fdf02714d970) / 08-23 | `HtmlData/AttachmentData/CommentData` 从 `lib_core` Java 类迁到 `lib_core_data` Kotlin 类；所有 builder/decoder/import 随迁；新增简短 `GlobalConfig` 类。骰子 decoder 为 Kotlin 可空字段补 `?.`。 | 主要是所有权和语言整理。F 骰子 decoder 已在转换前检查三个 ID 是否为空，不能把这几处 `?.` 宣称为本 fork 新补齐的空值功能。 |
| [`a6e4a890`](https://github.com/Justwen/NGA-CLIENT-VER-OPEN-SOURCE/commit/a6e4a89019f9fa7962479c2d6d82a891164a27cc) / 08-23 | 完成 `ThreadInfoParse.parse`，每次新建 parser 并使用本次解析内的用户缓存；删除 app `ArticleConvertFactory`；增加 `IHtmlConfigService` 和 app 适配器；网络与缓存改用新入口；`rowNum` 改为列表长度，`__ROWS` 改名为 `totalRows`。匿名检查改回 `username`；调整评论识别及菜单。 | 最接近可复用的最终形态，也引入下文所列附件顺序、用户缺失与总行数遍历问题；不能作为可直接替换 F parser 的成品。 |

可复核的提交内代码锚点：`1307d324:lib_core_data/src/main/java/com/client/androidnga/core/data/bean/ThreadPostBean.kt:7`、`0dbd80b4:lib_core_data/src/main/java/com/client/androidnga/core/data/model/ThreadInfo.kt:37`、`0d6e9e50:lib_core/src/main/java/com/client/androidnga/core/parse/ThreadInfoParse.kt:6`、`86e3f65d:lib_core/src/main/java/com/client/androidnga/core/parse/ThreadInfoParse.kt:13`、`00352348:lib_core/src/main/java/com/client/androidnga/core/parse/ThreadInfoParse.kt:172`、`3596da00:lib_core_data/src/main/java/com/client/androidnga/core/data/html/HtmlData.kt:3`、`a6e4a890:lib_core/src/main/java/com/client/androidnga/core/parse/ThreadInfoParse.kt:21`；清理项由 `4c99051f` 的删除 diff 核对。

### 4. 最终架构的收益与尚未达到的边界

最终旧读接口的流程是：

```text
ArticleListModel（app；RxJava；网络 / 文件缓存）
  → ThreadInfoParse（lib_core；清理 JSON、关联用户、构造展示状态）
      → ThreadBean / ThreadPageBean / ThreadPostBean / ThreadUserBean（lib_core_data）
      → ThreadInfo / ThreadPostInfo（lib_core_data）
      → HtmlConvertFactory → HTML 与 imageUrlList
  → 旧 ArticleListPresenter / Fragment / Adapter
```

- **职责更清楚。** 接口的下划线字段留在 bean，UI 的 `isBlocked/isMuted/isThreadAuthor/formatHtml` 留在 model。最终 `ThreadPostBean` 不再持有 `threadPostInfo`；见 [U: ThreadPostBean.kt:8](https://github.com/Justwen/NGA-CLIENT-VER-OPEN-SOURCE/blob/22ba3082501bcbb08f52a66d787f970f59c2dda7/lib_core_data/src/main/java/com/client/androidnga/core/data/bean/ThreadPostBean.kt#L8)、[U: ThreadInfo.kt:33](https://github.com/Justwen/NGA-CLIENT-VER-OPEN-SOURCE/blob/22ba3082501bcbb08f52a66d787f970f59c2dda7/lib_core_data/src/main/java/com/client/androidnga/core/data/model/ThreadInfo.kt#L33)。这是可复用设计，不等于不可变 domain 模型。
- **降低 core 对 app 类名的直接依赖。** `IHtmlConfigService` 提供黑名单、签名、表情尺寸、图片开关、host、夜间模式；但 app 的 [HtmlConfigService.kt:9](https://github.com/Justwen/NGA-CLIENT-VER-OPEN-SOURCE/blob/22ba3082501bcbb08f52a66d787f970f59c2dda7/nga_phone_base_3.0/src/main/java/gov/anzong/androidnga/service/HtmlConfigService.kt#L9) 仍即时访问 `PhoneConfiguration/UserManagerImpl/ThemeManager`。没有不可变设置/账号快照，解析结果仍混入渲染状态。
- **本次解析内缓存用户 bean。** [U: ThreadInfoParse.kt:20](https://github.com/Justwen/NGA-CLIENT-VER-OPEN-SOURCE/blob/22ba3082501bcbb08f52a66d787f970f59c2dda7/lib_core/src/main/java/com/client/androidnga/core/parse/ThreadInfoParse.kt#L20) 每次 `parse` 新建对象，`:60` 按 authorId 缓存反序列化对象。这证明重复作者复用对象，不能据此报告整页解析更快；同一文件还有总行数遍历问题。
- **仍不是完整响应契约。** [ThreadBean.kt:5](https://github.com/Justwen/NGA-CLIENT-VER-OPEN-SOURCE/blob/22ba3082501bcbb08f52a66d787f970f59c2dda7/lib_core_data/src/main/java/com/client/androidnga/core/data/bean/ThreadBean.kt#L5) 只建模 `data` 的若干字段；`__GLOBAL` 仍是字符串，`__U` 仍是字符串 map。外层 `encode/time`、`__CU`、请求身份、account scope、typed failure 没有对应模型；[parser:133](https://github.com/Justwen/NGA-CLIENT-VER-OPEN-SOURCE/blob/22ba3082501bcbb08f52a66d787f970f59c2dda7/lib_core/src/main/java/com/client/androidnga/core/parse/ThreadInfoParse.kt#L133) 仅把 `__T` 的 tid/fid/authorid/subject 传给展示模型，不能说“完整利用 read.php”。
- **网络/缓存治理没有完成。** U 的 [ArticleListModel.java:54](https://github.com/Justwen/NGA-CLIENT-VER-OPEN-SOURCE/blob/22ba3082501bcbb08f52a66d787f970f59c2dda7/nga_phone_base_3.0/src/main/java/sp/phone/mvp/model/ArticleListModel.java#L54) 仍未发送 `searchpost`；`:171` 的 cache key 仍只有 tid/page；`:212` 解析失败仍输出完整 body。成功路径原 parser 的整段 body 日志在重构中删除，但不能称日志隐私已解决。

### 5. 明确的行为变化：哪些能借鉴，哪些不能算新增收益

| 变化 | U 证据 / F 对照 | 判断 |
| --- | --- | --- |
| 按 UID 判断楼主 | U `ThreadInfoParse.kt:199` 用 `__T.authorid == userBean.uid`；F `ArticleListFragment.java:322` 只在首行 `lou==0` 时保存作者名，`FunctionUtils.java:317` 比较名字。 | **值得抽取。** 在主题 metadata 存在时可摆脱先加载楼主页的依赖，也避免仅靠名称识别。应优先比较主题作者 UID 与楼层 authorId，并给缺失 `__T/__U/uid` 明确降级，不能照抄 nullable 相等的全部细节。 |
| 客户端来源 Toast 简化、浏览器改通用图标 | U `ArticleListAdapter.java:73` 只显示枚举名称，`:399` 的浏览器枚举走通用图标；F 同文件 `:80` 起仍显示 Life Style/官方/开源版、机型与系统，`:502` 按平台显示图标。 | **明确的信息减少。** 可以采用枚举，又保留原始 client 字符串及详情展示；两者不必绑定。 |
| 热门回复字段停止解析 | `86e3f65d` 删除 `buildRowHotReplay` 与 `ThreadPostBean.hotReplies`。F `ArticleConvertFactory.java:211` 仍解析旧字段 `"17"`，`ThreadRowInfo.java:34` 保存列表。源码检索没有找到 UI 消费它。 | 是**数据保留能力减少**，不是已证实的热门回复界面被删除。和 fork 的后续热门回复规划有关，不应随模型整理丢弃。 |
| 旧 Windows Phone 正文解码停止执行 | `86e3f65d` 删除 `from_client` 以 `103 ` 开头时调用 `StringUtils.unescape` 的分支和函数。F `ArticleConvertFactory.java:150` 仍调用；`StringUtils.java:66` 起是旧兼容转换。 | 源码事实是兼容分支被删除。旧帖子是否还需要它，未用 fixture/线上确认；保留或明确退休都应单独决策。 |
| 评论菜单与识别条件变化 | U `ThreadInfoParse.kt:115` 改为检查空附件/空评论、fid=0、level/签名等；`a6e4a890` 在 `ArticleListFragment.java:176` 起对 `isComment` 隐藏“贴条”和“只看此人”。F `FunctionUtils.java:393` 仍按 null 字段和 avatar 判断。 | 是实际交互变化；可以独立评估修正，但 `fid==0` 等条件需要脱敏评论/普通回复 fixture，不能只按字段缺失猜测。 |
| 匿名、评论头像、黑名单时序修正 | `a6e4a890` 把匿名判断从中间版本 `nickname` 改回 `username`；`00352348` 修正中间版本评论头像；后续 `750871b3` 把 `isBlocked` 赋值提前至 HTML 转换前。F parser `:277` 本来使用 username、`:191` 使用评论自身头像、`:134`—`:139` 先用户状态后 HTML。 | 这些主要是**上游重构引入问题后的修正**；F 已有正确顺序/来源，不宜当作迁移带来的新功能。 |

以上 F 引用均位于 `nga_phone_base_3.0/src/main/java/` 下：`sp/phone/mvp/model/convert/ArticleConvertFactory.java`、`sp/phone/ui/fragment/ArticleListFragment.java`、`sp/phone/ui/adapter/ArticleListAdapter.java`、`sp/phone/util/FunctionUtils.java`、`sp/phone/util/StringUtils.java`、`sp/phone/http/bean/ThreadRowInfo.java`。

### 6. 最终 parser 的具体风险

这些问题用于判断移植质量，不代表本报告已经在 Android 运行了上游。

1. **附件列表生成晚于 HTML 渲染。** [U: ThreadInfoParse.kt:87](https://github.com/Justwen/NGA-CLIENT-VER-OPEN-SOURCE/blob/22ba3082501bcbb08f52a66d787f970f59c2dda7/lib_core/src/main/java/com/client/androidnga/core/parse/ThreadInfoParse.kt#L87) 先调用 HTML 转换，`:92` 才设置 `attachInfo`；新对象该字段默认 `emptyList()`（`ThreadInfo.kt:162`）。而 [HtmlConvertFactory.java:49](https://github.com/Justwen/NGA-CLIENT-VER-OPEN-SOURCE/blob/22ba3082501bcbb08f52a66d787f970f59c2dda7/lib_core/src/main/java/gov/anzong/androidnga/core/HtmlConvertFactory.java#L49) 正是从这个字段读取附件，`HtmlAttachmentBuilder.java:60` 对空集合直接返回。**源码可以确认：首次渲染没有拿到 attachs 派生的附件列表。** 静态推断是独立附件区和由它追加的图片列表可能缺失；正文 `[img]` 走另一条 decoder 路径，不应泛化成“所有图片不显示”。F [ArticleConvertFactory.java:156](/home/toph/nga-just-works/nga_phone_base_3.0/src/main/java/sp/phone/mvp/model/convert/ArticleConvertFactory.java:156) 在转换前通过 `buildHtmlData` 填好附件（`:178`）。此顺序问题由 `a6e4a890` 引入，`750871b3` 只修复了邻近的黑名单赋值顺序。
2. **附件 host 读取未完成赋值的 parser 字段。** 同一 parser `:45` 执行 `threadInfo = ThreadInfo().apply { ... parsePostInfoList(...) }`；`:106` 在 apply 内读取外部 `threadInfo?.basicInfo?.attachHost`。`parse()` 的新对象此时 `threadInfo` 仍为 null。因此即使只把附件赋值移到 HTML 前，还需改成使用本次局部 `basicInfo`。这是对象初始化顺序事实；最终 UI 的具体表现仍未运行验证。
3. **用主题总行数遍历当前页行 map。** 同文件 `:54` 把 `__ROWS` 传给循环，`:78` 按该 count 探测 `"0".."count-1"`；原逻辑与 F `ArticleConvertFactory.java:94` 使用 `__R__ROWS`。在“总行数很大、页内只有少量行”的合成形状下，新代码会做许多无结果 map 查询；两个字段不一致时也可能改变选取范围。这里没有耗时测量，不能声称上游更快或慢了某个比例；迁移应以当前页实际有效行和明确顺序为边界。
4. **可选用户资料更容易升级为整页解析失败。** U `:60` 的 `getUserBean` 承诺非空，却直接反序列化可能不存在的 `__U[authorId]`；`:204`—`:205` 直接解引用 `__GROUPS/memberid`。F parser `:263` 跳过 authorId=0，`:270` 对用户不存在早返回，`:308`—`:313` 将用户组/威望读取放在局部 try/catch。静态推断是缺失用户或分组 metadata 会让上游丢弃整个解析结果；缺失情况下的具体库异常类型未做 Kotlin harness。F 自身也没有全面防护，尤其 `__U` 整体缺失，不能称 F parser 已健壮。
5. **缺失 `__T` 的 UI 风险仍在。** U parser `:133` 明确可以返回 null pageInfo，但 U [ArticleListFragment.java:302](https://github.com/Justwen/NGA-CLIENT-VER-OPEN-SOURCE/blob/22ba3082501bcbb08f52a66d787f970f59c2dda7/nga_phone_base_3.0/src/main/java/sp/phone/ui/fragment/ArticleListFragment.java#L302) 仍直接调用 `data.pageInfo.getSubject()`。F `ArticleListFragment.java:318` 有同类问题；这是**未修复的共同缺口**，不是重构新引入。

另一个接口边界不一致：`parse(rawData, config)` 声明 config 可空，但 `HtmlConvertFactory.java:37` 直接调用 config。app 当前调用传入 `new HtmlConfigService()`，所以不能说现有调用必然失败；若把它当通用 parser 暴露，需要收紧或实现空配置契约。

### 7. fastjson2 的实际增量与兼容要求

版本证据：F [build.gradle:10](/home/toph/nga-just-works/build.gradle:10) 为 `1.1.71.android`，通过 [lib_base_common/build.gradle:58](/home/toph/nga-just-works/lib_base_common/build.gradle:58) 对外提供；F [lib_core/build.gradle:45](/home/toph/nga-just-works/lib_core/build.gradle:45) 已依赖 catalog 的 `2.0.59.android8`。`25652de8` 将 common 的依赖切到同一 fastjson2 artifact（[U: lib_base_common/build.gradle:58](https://github.com/Justwen/NGA-CLIENT-VER-OPEN-SOURCE/blob/22ba3082501bcbb08f52a66d787f970f59c2dda7/lib_base_common/build.gradle#L58)），并迁移使用者。

不能把 `fastjson2-kotlin` 的 catalog 声明当作已采用 Kotlin 扩展库：`25652de8` 增加的是 `2.0.59.android8` 别名，后续 `2becba2a` 将该声明改为 `2.0.21.android`；导出的变更模块 Gradle 文件未见该别名的消费。这里实际评估的是 core `fastjson2:2.0.59.android8`，该核心库没有随这个别名降级。

主会话使用本机缓存的两个实际版本 JAR，运行脱敏合成数据，结果如下；研究者已读取结果表：

| 合成输入 | fastjson1 默认 | fastjson2 默认 | 上游应对与边界 |
| --- | --- | --- | --- |
| 标准 JSON | 通过 | 通过 | 对照组，不证明所有原有 DTO/偏好兼容。 |
| 未建模 `jdata` 字符串内的单反斜杠 `\x5C` | 通过 | `JSONException` | `93acf42a` 预先删除 jdata 的 regex 可修“键冒号无空格、字段后有逗号”的测试形状；字段位于末尾或键冒号有空格的变体仍失败。该问题应列为升级回归要求，不能称当前 F 已复现的 parser 缺陷。 |
| `{data:{url:"https://example.invalid/a"}}` | 通过 | `JSONException` | `750871b3` 使用 `JSONReader.Feature.AllowUnQuotedFieldNames` 后通过；同样是升级时需要补回的容忍行为。 |

上述分别是 24 个 jdata/regex 对照组合和 6 个上传字段对照组合，没有真实 NGA 响应。具体合成源码、结果及更完整限制由主会话的兼容性报告归档；这些实验不是安全性审计、端到端上传成功验证，也不是性能 benchmark。

迁移还会触碰本地格式，不能只跑一个帖子解析样例：F `TopicHistoryManager.java:38` 读取历史 JSON，`:79` 写回；`FilterManager.kt:39`、`:54`、`:67`、`:88` 读写屏蔽用户/关键字；版面、搜索和通用偏好也经 common JSON 库。Kotlin Bean 的字段命名、Java getter、`JSONField` 注解、空值、数字字符串、旧保存格式都需要离线往返用例。尤其上游 `ThreadPageInfo` 最终同时保留 `authorId` 和 `authorid`（U `ThreadPageInfo.kt:12`—`:15`），不应把重复字段作为新的长期契约。

推荐把依赖迁移做成一个单独、可回滚的改动：先固定旧库输入与持久化行为，再迁移 imports/注解/API，按 operation 设置必要容忍特性；不要通过一个全局宽松开关掩盖未知响应，也不顺便重命名所有数据类。`93acf42a` 和 `750871b3` 的相关片段是迁移证据与用例来源，而非两个必须原样 cherry-pick 的完整提交。

### 8. 清理提交的边界

`4c99051f` 的直接调用检查与当前 F 一致：

- F `ArticleListAdapter.java:371` 仍只是为旧 `HtmlUtils` 初始化静态字符串；实际帖子渲染走 `ArticleConvertFactory → HtmlConvertFactory`。删除 `HtmlUtils` 时必须同步移除此引用。
- `MD5Util`、`PluginUtils` 在本次相关生产源码检索中没有外部调用；没有证据表明清理意味着用户失去正在工作的插件能力。
- `HttpUtil.getHtml` 没有活跃调用；项目 operation registry 已将其记为 dormant（`.trellis/spec/backend/nga-platform-operation-registry.md:114`）。删除它不能等同于升级当前 Retrofit 传输层。
- 删除的是 `ImageUtils.loadDefaultAvatar()` 的无参旧 Bitmap 版本；F `ProfileActivity.java:516` 仍使用 `loadDefaultAvatar(ImageView, String)`，后者保留。Bitmap 缩放与实际上传处理也不能按同名函数一并删掉。
- `86e3f65d` 进一步删除的 app `sp.phone.mvp.model.convert.MessageConvertFactory` 是旧副本；F 活跃私信 Repository 引用的是 `lib_bu_message` 中 `com.justwen.androidnga.module.message.MessageConvertFactory`（`MessageRepository.kt:12,43` 与 `MessageDetailRepository.kt:11,55`），该清理不等于删除私信功能。
- `86e3f65d` 的 `StringUtils` 删除项中，`unescape` 当前仍有活跃帖子调用；因此不能把这一整块归为同等安全的死代码。

这类清理适合以“确认最后调用者消失后删除”为粒度。未测 APK/R8 输出，不报告包体或加载速度收益；也未以全量反射分析证明任何同名方法绝对不存在动态调用。

### 9. 与 fork 已有工作对齐

| 当前 fork 的实际状态 / 规划 | 与上游关系 | 应保留或复用的边界 |
| --- | --- | --- |
| **已交付：翻页预取。** F `ArticleListPresenter.java:100`、`:158`—`:195` 区分背景预取、前台接管、失败回退；`ArticleShareViewModel.java:58`—`:66` 发布不可变候选列表；`ArticleTabFragment.java:122` 保留两页。 | 上游这些类型重构没有等价的预取状态机。 | 只替换数据边界，保留单页请求去重、末页不预取、离开页面取消前台接管、DETACH 取消、背景失败不 Toast/开 WebView/切账号。 |
| **已交付：本 fork 的图床路径与历史域名归一化。** F parser `:103` 通过 `NgaImageHost.attachmentsPrefix` 传递页面前缀；F `FunctionUtils.java:389` 归一化旧头像域名。 | 上游改用简单 `attachHost` 与 `ForumParsekUtils`，不是这些 fork 规则的超集。 | 移动 parser/HtmlData/头像 helper 时继续调用当前 `NgaImageHost`；不能把 fork 的安全回退、路径前缀或旧域名修复替换掉。 |
| **尚在 planning：read.php 契约任务。** `.trellis/tasks/08-08-read-php-contract-utilization/task.json:6` 为 planning。 | 上游没有修 `searchpost`、account/request cache key、缺失 `__T` 安全降级或完整字段保留。 | 该任务 `design.md:26` 已允许先抽纯 JVM helper，`:95` 定义作者/空值降级，`:113` 明确先完成请求、容错、隐私和契约测试，再接正式 DTO。可以吸收上游建模经验，不能标记为“已被上游覆盖”。 |
| **已有批准的迁移方向、尚在 planning：Kotlin + Compose + MVVM。** `.trellis/tasks/07-27-android-kotlin-compose-migration-research/design.md:20` 记录方向批准；task.json 仍为 planning。 | 上游做的是一部分模型/core 抽取，没有 Repository、账号快照、Flow/不可变 UiState 或 Compose 页面迁移。 | 与设计的 M1 共享数据边界、M6 帖子详情/富文本切片衔接；`:164` 和 `:230` 要求旧 UI 经共享边界逐步迁移。不要另开一套与已有目标并行的长期架构。 |

### 10. 建议采用粒度、顺序与验证

以下是后续候选范围和相对优先级，不是本轮实施授权。

1. **较高：在既有 read.php 任务内借鉴 UID 楼主判断和 parser 分层。** 保留当前公开入口，先补请求 `searchpost`、可选字段、局部 metadata 失败、脱敏诊断与 fixture。最小 UID 改动不需要七次模型重命名，也不需要先统一 JSON 库；成本小至中，收益能直接对应深链/非首页显示正确性。
2. **中：单独统一 fastjson2。** 成本中、横向影响广。先覆盖上表非标准 JSON、错误 envelope、数字/空值、历史/搜索/过滤/版面偏好与 `JSONField` 保留，再切 common 依赖。后续用受影响模块 JVM 检查、编译及项目要求的 lint/发布检查确认；本轮未执行这些构建。
3. **中低：将 DTO/model/renderer 边界纳入现有迁移切片。** 借鉴 `bean/model` 和配置接口，但采用明确的输入快照、typed result 与唯一 Repository/adapter；避免同时替换 parser、全部 UI 类型、缓存格式和网络重试。成本高，应以一个完整 operation/页面数据边界为检查点，修正附件顺序和 count/metadata 问题后才接旧 UI。
4. **低：独立删除确认无消费者的旧工具和重复私信 parser。** 不需要先升级 fastjson2；清理与结构迁移分开，便于审阅。保留仍在使用的 WP 解码，除非已有行为退休决定及对应证据。

如果确实要移植原代码，依赖链大致为：清除旧模型的死引用 → `1307d324` 的类型搬迁 → `0dbd80b4` 的展示子对象 → fastjson2 及其兼容修正 → `0d6e9e50/86e3f65d/00352348` 的 DTO、parser 与消费者切换 → `3596da00` 的 HTML 类型搬迁 → `a6e4a890` 的完整入口，再补后续已知回归。**这解释了为什么“只挑最后一个数据重构提交”不可行；不意味着应按这条链直接移植。**

重点冲突文件是 `ArticleConvertFactory`、`ArticleListModel/Presenter/Contract`、`ArticleShareViewModel`、`ArticleListFragment/Adapter`、`HtmlData/HtmlConvertFactory`、`FunctionUtils`、`TopicHistoryManager`、版面/搜索/过滤的 JSON 使用点，以及 common/core Gradle 依赖。原样套用上游文件会丢失 F 的预取、图床和交互改动；实际文本合并冲突数量未模拟。

后续最必要的行为用例：普通/过滤/定位/末页请求；缺失 `__T/__U/__GROUPS`；页内行数与总行数不一致；重复作者；正文图片与独立附件、评论附件分别验证；匿名/楼主/禁言/屏蔽标签；WP 历史正文；客户端详情；后台预取失败与前台接管；旧本地 JSON 往返。只应使用脱敏 fixture/本地测试。

### 11. Related specs

- `.trellis/spec/frontend/android-migration-architecture.md`：保留存储、路由与行为；新数据接口采用 Kotlin/Repository/结构化异步；旧 Java/parser 可作为有契约的边界保留。
- `.trellis/spec/backend/thread-page-prefetch-contract.md`：现有单一请求路径、末页新鲜度、前后台失败分离及取消契约。
- `.trellis/spec/backend/network-foundation-contract.md`：固定源行为与迁移规则分开；不复制全局账号轮换、完整响应日志或不受控缓存。
- `.trellis/spec/backend/nga-platform-access-rules.md`：页面附件前缀、wrapper、账号快照、typed failure 与离线证据要求。
- `.trellis/spec/backend/nga-platform-operation-registry.md`：`THREAD.PAGE`、`TOPIC.LIST`、`ATTACHMENT.UPLOAD` 及 dormant 工具的归属。
- `.trellis/spec/backend/android-quality-guidelines.md`：后续实现时遵守受影响检查与设备操作范围。本报告未运行 Android 构建或设备操作。

### 12. External references / versions

- 原仓库：[Justwen/NGA-CLIENT-VER-OPEN-SOURCE](https://github.com/Justwen/NGA-CLIENT-VER-OPEN-SOURCE)；固定 source 和九个完整提交链接见上文，分支/时间清单由本任务 `commit-inventory.json` 统一记录。
- JSON artifact：`com.alibaba:fastjson:1.1.71.android` → `com.alibaba.fastjson2:fastjson2:2.0.59.android8`。版本依据项目源码与缓存 JAR 离线实验，不依据“最新版”或通用 benchmark 宣称升级收益。
- fastjson2 项目参考入口：[alibaba/fastjson2](https://github.com/alibaba/fastjson2)。本报告没有把该项目宣传性能、通用默认行为或未来版本行为当作当前 Android 应用的实测结果。

## Caveats / Not Found

- 没有访问真实 NGA 接口，没有读取账号、Cookie 或凭据文件，没有运行 APK、设备测试或广泛 Android 构建。
- 未找到这九个补丁新增覆盖上述 parser/依赖兼容性的有效回归测试；`3596da00` 对现有示例测试仅调整 import。后续补丁是否有线下/私有测试，无法从这批源码判断。
- 未验证 Kotlin/fastjson2 对所有缺字段、null、数字类型和旧存储的完整组合；报告给出的缺失用户/分组风险是源码推断。主会话 JSON 实验仅证明列出的合成形状。
- 未测性能、内存或 APK 大小；按用户缓存减少反序列化、类数量变少或 Kotlin 代码更短，都不能推出端到端性能提高。
- `750871b3` 与 `2becba2a` 的完整 bugfix、备用 API、错误/缓存语义由同任务另一份报告覆盖。本文只引用和数据重构/fastjson2 直接关联的部分，避免把后续修正误算成七次重构的原始收益。
