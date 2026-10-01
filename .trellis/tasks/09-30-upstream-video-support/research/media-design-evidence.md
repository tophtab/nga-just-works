# Research: U2 帖子视频样式与严格相对 flash 标签

- Query: 核对当前公共渲染链与上游 750871b3，给出能直接落地的新增标签语法、生成方式、样式边界、回归夹具与实施边界。
- Scope: mixed；当前独立工作区源码、复制的上游补丁、既有归档、MDN browser-compat-data；没有 NGA 请求、设备操作或产品修改。
- Date: 2026-09-30
- 工作区：`/home/toph/nga-just-works-upstream-adoption`；基线由父任务隔离记录确定为 `1a8413d9`。本研究不执行 git 操作。
- 决策状态：供 U1–U4 整体规划和统一审核，不是启动实施的授权。

## Findings

### 1. 结论与源码校正

U2 只需在现有 Java `ForumBasicDecoder` 添加严格的无类型相对媒体识别，并在公共 `style.css` 添加上游四个 `video` 属性。保留现有 typed video/audio 和无类型绝对 Flash 链接规则；不做整类 Kotlin 重写，不改图床权威来源，不引入播放器或新网络请求。

上游原始补丁已保存在 [upstream-750871b3.diff](upstream-750871b3.diff)：

- 补丁第 7–23 行：新增 `video { width: auto; max-width: 100%; height: auto; display: block; }`。
- 第 638–657 行：typed video 改用 `http://$imageHost/attachments`，typed audio 仍硬编码旧图床；新增无类型规则是 `\\[flash].(.*?)\\[/flash]`，其中 `.` 是任意字符，**并没有**真的限定 `./`。
- 第 466–470 行：无类型 `http…` 仍先生成带 Flash 图标的链接。
- 当前 fork `ForumBasicDecoder.java:27–29,238,241` 已让 video 和 audio 都使用完整页面前缀；因此仅移植图床部分会退步。
- 当前 fork `style.css:42–47` 仍只有图片宽度约束，没有 video 规则。当前 Java decoder `:138–141,237–241` 仍没有无类型相对视频规则；缺口确实尚未吸收。

**已有行为的准确边界：**现有 typed video/audio 自己也使用未转义的 `.` 和 `(.*?)`。本轮保留它们的既有正常输出，不把旧的宽泛输入全部当成正式支持的新契约，也不承诺在 U2 修复所有旧 malformed typed 标签。新增规则的“不吞字”契约只覆盖其自身匹配和拼接行为。

### 2. Files found / Code patterns

| 文件与证据行 | 实际作用 / 本轮用途 |
| --- | --- |
| `lib_core/src/main/java/gov/anzong/androidnga/core/decode/ForumBasicDecoder.java:22–36` | 双参数解码入口、页面附件前缀、旧 `&amp;` → `&` 预处理 |
| 同文件 `:138–141` | 现有绝对 http/https Flash 图标链接；保留 |
| 同文件 `:237–243` | typed 视频与音频；新增无类型相对规则放在它们后、return 前 |
| `lib_base_common/src/main/java/gov/anzong/androidnga/base/util/StringUtils.java:23–32` | `replaceAll` 直接使用 Java Matcher replacement 语义，不能拼入不受控的 `$` 或反斜杠作为 replacement 模板 |
| `lib_core/src/main/java/gov/anzong/androidnga/core/decode/ForumDecoder.java:17–34` | Basic → Vote → Album → Emoticon → Image → Dice 公共链；不增加第二套媒体链 |
| `lib_core/src/main/java/gov/anzong/androidnga/core/HtmlConvertFactory.java:20–42` | 全部正文使用 ForumDecoder，并组装公共模板及明暗样式 |
| `lib_core/src/main/assets/html/html_template.html:7–9` | 公共 `style.css` 后跟主题 CSS；body 与所有子内容共用该样式 |
| `lib_core/src/main/assets/html/style.css:42–47` | 当前只有 img max-width；这里新增 video 规则 |
| `lib_core/src/main/assets/html/style_dark.css:1–11`、`style_light.css:1–7` | 主题只有文字/引用背景/图片滤镜，不覆盖 video 尺寸 |
| `nga_phone_base_3.0/src/main/java/sp/phone/mvp/model/convert/ArticleConvertFactory.java:208–243` | normal-only WP 预处理在 renderer 上游；中立 `renderRow` 传完整前缀并用 `ArticleSourceText.renderBody` |
| `nga_phone_base_3.0/src/main/java/sp/phone/mvp/model/thread/AppArticleParser.kt:16–18,38,66` | compat 默认直接调用同一 `ArticleConvertFactory.renderRow`；响应 `attachPrefix` 经 NgaImageHost |
| `lib_core/src/main/java/gov/anzong/androidnga/core/data/HtmlData.java:158–168` | 有页面前缀就使用，没有则固定解析兜底；不新设静态页面图床 |
| `lib_base_common/src/main/java/gov/anzong/androidnga/common/util/NgaImageHost.java:83–123,173–254` | manual > server；服务端只接受根路径或 `/attachments[/]`，custom 输入只保留 base host；不能宣称任意自定义路径受支持 |
| `lib_core/src/main/java/gov/anzong/androidnga/core/corebuild/HtmlCommentBuilder.java:50–53` | 评论也走公共 decoder；其媒体自然共享行为 |
| `lib_core/src/main/java/gov/anzong/androidnga/core/corebuild/HtmlSignatureBuilder.java:18–22` | 显示签名时共用公共 decoder；没有新增签名开关 |
| `lib_core/src/main/java/gov/anzong/androidnga/core/corebuild/HtmlAttachmentBuilder.java:14–37,82–87` | 附件列表 mp3/mp4 生成链接；不应误写为本轮将其改成播放器 |
| `nga_phone_base_3.0/src/main/java/sp/phone/ui/adapter/ArticleListAdapter.java:567` | HTML 最终进入 LocalWebView；不改 dead native TextView 分支 |
| `nga_phone_base_3.0/src/main/java/sp/phone/view/webview/LocalWebView.java:57–70,107–113` | 现有 JavaScript、图片策略和相同 HTML 复用；没有 U2 所需设置改动 |
| `lib_core/build.gradle:39–46` | JUnit 4 与 `testImplementation project(':lib_base_common')` 已有，新增 JVM decoder 测试无需依赖调整 |
| `lib_core/src/test/java/gov/anzong/androidnga/core/ExampleUnitTest.java:24–35` | 旧 quote 测试只打印，不足以验证媒体；需要有断言的新专门测试 |
| `nga_phone_base_3.0/src/test/java/sp/phone/mvp/model/convert/PageAttachmentPrefixFlowTest.java:25–84` | 已有页面图床消费者、附件列表和退休主机回退测试；补媒体行为案例而非只增加字符串存在性断言 |
| `nga_phone_base_3.0/src/test/java/sp/phone/mvp/model/thread/AppArticleParserTest.kt:12–41`、`NormalArticleParserTest.kt:22–52` | 两个 parser 都已支持注入 renderer，能离线验证响应局部前缀 + 保留源文本 + 实际 core decode |

### 3. 推荐新增识别契约（确定方案）

只新增以下媒体形式，标签名大小写不敏感，与现有无类型绝对标签一致：

```text
[flash]./relative/path[?query][#fragment][/flash]
```

`[flash=video]`、`[flash=audio]` 和已有 `[flash]http…[/flash]` 仍由原规则处理。新的无类型规则不猜测 `http…`、`//host/…`、`/path`、`../path`、省略 `./` 或未知 `flash=xxx` 的含义。

精确的候选边界可用以下 Java 正则文本（代码字符串形式）识别：

```java
"(?i)\\[flash\\](\\./[^\\[\\]\\r\\n]*)\\[/flash\\]"
```

随后对捕获到的完整 `./…` 做小型 URI 校验，防止候选正则本身成为宽泛接受规则：

1. 用 `new java.net.URI(relative)` 校验 URI 字符与 percent triplet；`URISyntaxException` 只表示不识别该候选，原样保留，不抛出到整页。
2. 必须无 scheme、无 authority，`getRawPath()` 以字面 `./` 开始；路径在这两个字符之后必须非空。
3. 对 `rawPath.substring(2)` 按 `/` 分段（保留尾空段）：每段非空；段不得为 `.` 或 `..`。将 `%2e` 大小写不敏感地换成 `.` **仅用于 dot-segment 判断**，同样拒绝编码的单/双点段；不改输出 URL。
4. 整个候选拒绝原始空白、Unicode 空格/控制字符和 `[`/`]`。URI 本身再拒绝未编码的双引号、尖括号、反斜杠、不合法 `%` 等；原始单引号是合法 URI 内容，输出时 HTML 属性转义。不得 trim 输入、修复非法 URI 或猜测内容。
5. 路径后允许 URI 标准 query 和 fragment，允许空 query/fragment，但不能用它们替代非空路径。保留参数顺序、大小写、`$`、`&`、percent escape，不 URL decode/re-encode。无文件扩展名白名单，也不把 `.mp3` 自动推断成音频：这是上游新增的视频方言，真实格式由既有浏览器媒体能力决定。
6. 这里的 `./` 是“拼到当前附件目录”的标识，不能调用 `URI.resolve()` 或重新解析主机；输出完整 URL 为现有 `attachmentsPrefix + relative.substring(1)`。

这样实现最多只需 decoder 中局部 Pattern、校验、HTML 属性转义与替换方法，均可 JVM 执行；不新增通用 URL 库、不扩大 NgaImageHost 的输入语法。

**畸形和嵌套的确定行为：**每个满足边界的独立 span 独立处理，候选不允许跨过 `[`/`]` 或换行；外层无效文本不被吞掉。若畸形外层中含完整有效内层 `[flash]./ok.mp4[/flash]`，内层可以被识别，外层起止标记仍保留。不是 BBCode 嵌套解析器，也不声称所有畸形外层字节完全不受其他现有 BBCode 规则影响。

### 4. HTML 生成和 replacement 边界

推荐新增规则在原 typed 两行之后运行；原三条媒体规则保持原位。新的转换只消费匹配区间，用 `StringBuilder.append(content, cursor, match.start())` + 完整生成值 + 最后 appendTail 的等效 substring 写法。这样 `$1` 和反斜杠根本不会进入 Matcher replacement 模板；如选择 `appendReplacement`，完整生成值必须先 `Matcher.quoteReplacement`，不能直接拼 replacement 字符串。

生成形态固定为：

```html
<video src='ESCAPED_COMPLETE_URL' controls='controls'></video>
```

`ESCAPED_COMPLETE_URL` 对完整 URL 按顺序做 HTML attribute escaping：`&` → `&amp;`，`'` → `&#39;`，`"` → `&quot;`，`<` → `&lt;`，`>` → `&gt;`。这是 HTML 编码，不能用 URL 编码替代。当前 `ForumBasicDecoder.java:36` 先将原文本 `&amp;` 解为 `&`；新规则再编码一次使最终 DOM `src` 保留参数原值。不要重新经过这条解码流程，也不要给新 video 添加 `autoplay`、`muted`、尺寸属性、监听器、下载或转码。

不改变全局 `StringUtils.replaceAll` 或老媒体规则的 escaping 行为。对无法解析的候选只复制原 span，因此不会因为一个标签异常导致整段内容为空或抛异常。

### 5. 夹具表与明确期望

记 `P = https://page.example/attachments`；`V(s)` 表示 `<video src='P+s（按上节 HTML 属性转义）' controls='controls'></video>`。以下“原样”只约束新增规则；无其他 BBCode 的列可直接用 `ForumBasicDecoder.decode(content, HtmlData.create(...))` 断言完整字符串。

| 输入 | 新增规则 / 完整媒体输出期望 |
| --- | --- |
| `[flash]./mon_202609/a.mp4[/flash]` | `V(/mon_202609/a.mp4)` |
| `前[flash]./a.mp4[/flash]后` | `前V(/a.mp4)后`，文字各保留一次 |
| `[FLASH]./A.MP4[/FlAsH]` | `V(/A.MP4)`，保留路径大小写 |
| `[flash]./a.mp4[/flash][flash]./b.mp4[/flash]` | 两个独立 video；无残余标记、无字符丢失 |
| `[flash]./a.mp4[/flash]中[flash=audio]./b.mp3[/flash]` | 新 video + `中` + 原有 `<audio src='P/b.mp3&filename=nga_audio.mp3' controls='controls'></audio>` |
| `[flash=video]./a.mp4[/flash]` | 保留原有 `<video src='P/a.mp4' controls='controls'></video>` |
| `[flash=audio]./a.mp3[/flash]` | 保留原音频 URL 及 `&filename=nga_audio.mp3`，不得变成 video |
| `[flash]https://media.example/a.mp4[/flash]` | 保留 `<a href="https://media.example/a.mp4"><img src='file:///android_asset/flash.png' style= 'max-width:100%;' ></a>`；HTTP 同理 |
| `[flash]./a.mp4?token=$1&x=2#t=1[/flash]` | `src='P/a.mp4?token=$1&amp;x=2#t=1'`；`$1` 字面保留 |
| `[flash]./a.mp4?x=1&amp;y=2[/flash]` | 单次编码 `src='P/a.mp4?x=1&amp;y=2'`，不是 `&amp;amp;` |
| `[flash]./a'b.mp4[/flash]` | `src='P/a&#39;b.mp4'`，无属性终止；DOM 值含字面单引号 |
| `[flash]./a%20b.mp4?x=%24[/flash]` | percent escape 原样保留，无二次编码 |
| `[flash]./视频.mp4[/flash]` | `V(/视频.mp4)`；不引入 ASCII/`mon_`/扩展名白名单 |
| `[flash]./media[/flash]` | `V(/media)`；无扩展名也允许 |
| `[flash]./a.mp4?[/flash]`、`[flash]./a.mp4#[/flash]` | 有真实路径，保留空 query/fragment |
| `[flash][/flash]`、`[flash]./[/flash]` | 原样，不产生空 src |
| `[flash]./?x=1[/flash]`、`[flash]./#t=1[/flash]` | 原样，query/fragment 不能替代路径 |
| `[flash]x/a.mp4[/flash]`、`[flash]/a.mp4[/flash]`、`[flash]../a.mp4[/flash]` | 原样，不吞首字符 |
| `[flash]//media.example/a.mp4[/flash]`、`[flash].//a.mp4[/flash]` | 原样，不推断另一主机或空路径段 |
| `[flash]./dir/../a.mp4[/flash]`、`[flash]./%2e%2e/a.mp4[/flash]` | 原样，不能逃出附件目录 |
| `[flash]./dir/[/flash]`、`[flash]./dir//a.mp4[/flash]` | 原样，拒绝空路径段 |
| `[flash] ./a.mp4[/flash]`、`[flash]./a.mp4 [/flash]` | 原样，不 trim、不修复空白 |
| `[flash]./a\nb.mp4[/flash]`（真实换行） | 原样，不跨行匹配 |
| `[flash]./a%GG.mp4[/flash]`、`[flash]./a%[/flash]` | 原样，非法 URI 不能导致整页异常 |
| 含真实反斜杠、双引号或 `<...>` 的相对路径 | 新规则原样，不插入属性/HTML；原有通用正文规则仍有自己的既定行为 |
| `[flash=unknown]./a.mp4[/flash]` | 原样，无类型识别不能吞 typed 标签 |
| `[flash]./a.mp4`、`[flash]./a.mp4[/flas]`、`[/flash]后` | 原样，无闭合/错误闭合不得吞尾文 |
| `[flash]./bad[flash]./ok.mp4[/flash][/flash]` | `[flash]./badV(/ok.mp4)[/flash]`，只消费内层有效 span |
| `[flash]./broken [flash]./ok.mp4[/flash]尾` | 无效前缀保留，内层 video 正常，尾文保留 |
| 一条错误新格式后跟正常 typed/absolute/new 标签 | 错误新格式保留，之后独立有效形式按各自规则处理；无 new-rule 吞字 |

前缀夹具：

- 两个非空 HtmlData 先后设 `https://first.example/attachments`、`http://second.example:8080/attachments`，每次新 video 各自使用页面值，不互相污染。
- 非空 HtmlData 未设前缀：采用当前 NgaImageHost 无页面值兜底；不把 `decode(content, null)` 作为受支持入口。
- 在现有 NgaImageHost 合约测试矩阵确认 auto 的裸 host、`https://…/attachments/`、`//host/attachments`、退休主机、空/错误 host；manual default/img9/custom 优先已有覆盖。新媒体消费者只验证它完整消费最终前缀，不重复实现/测试另一套 resolver。
- `img9` 的 HTTP 必须保留；不统一强制 HTTPS。
- `HtmlData.setAttachmentsPrefix` 不额外修整/补 `/attachments`；权威 resolver 已返回无尾斜杠的完整前缀。

### 6. CSS、浏览器与比例限制

采用上游属性原义，用本地缩进风格加入：

```css
video {
    width: auto;
    max-width: 100%;
    height: auto;
    display: block;
}
```

- `width:auto` 保留较小视频原始尺寸，不强制撑满；`max-width:100%` 约束到其 containing block；`height:auto` 在媒体元数据给出固有比例后按缩放宽度推导高度。
- 不固定 16:9，不使用 `aspect-ratio`、固定高度或 `object-fit:cover`；横视频、竖视频、正方形按各自比例展示，不裁切。竖视频可能很高，**本轮不加最大高度**。
- `display:block` 让播放器独占一行，文字仍保留但不继续与 video inline 同排；这是吸收上游样式后的明确行为。
- 加载前无固有媒体信息时由浏览器使用默认 replaced-element 尺寸；CSS 不保证零布局位移，也不证明 codec、远端可用性、播放、全屏或 controls 在每一 WebView 上一致。
- 普通与兼容内容、已显示的评论/签名 video 共用；audio 不匹配 `video` 选择器。主题样式无 video 覆盖；无需改明暗主题、WebView viewport、JavaScript、网络图片策略或读取来源。
- `max-width` 相对 containing block，而不是直接相对物理屏幕。用离线夹具验证普通 body、窄引用、折叠展开及表格容器；既有任意宽表格等祖先自身溢出不是仅靠 video CSS 能全局修复的承诺。

MDN browser-compat-data 于 2026-09-30 实际读取：`width`、`max-width`、`height`、`display` 的 Chrome `version_added` 均为 `1`，Chrome Android 与 Android WebView 均标记 `mirror`。因此这四项不要求 U4 SDK 36，也不需要新 WebView API。这里仅能证明基本 CSS 属性兼容资料，不能推定播放能力。

### 7. 实施文件、验证和回滚建议

产品修改只涉及：

1. `lib_core/src/main/java/gov/anzong/androidnga/core/decode/ForumBasicDecoder.java`：新增局部严格 untyped helper、固定 Pattern、URI 校验和属性转义；旧规则不动。
2. `lib_core/src/main/assets/html/style.css`：新增四属性 video 规则。

行为测试建议：

1. 新增 `lib_core/src/test/java/gov/anzong/androidnga/core/decode/ForumBasicDecoderMediaTest.java`，直接运行生产双参数 decoder，覆盖上表识别、拒绝、拼接、escaping、旧类型兼容和前缀隔离；不写只检查正则字符串的镜像测试。
2. 在 `nga_phone_base_3.0/src/test/java/sp/phone/mvp/model/convert/PageAttachmentPrefixFlowTest.java` 补新/旧媒体使用完整前缀的执行性断言。
3. 在现有 `NormalArticleParserTest.kt` / `AppArticleParserTest.kt` 各加少量媒体桥接案例：注入 renderer，使用真实 `ArticleSourceText.renderBody` + `HtmlData` + core decoder，断言两种响应方言传对前缀、源正文保留而展示结果出现 video。不为测试调用需要 Android asset/context 的静态 `HtmlConvertFactory` 初始化；现有生产默认 seam 已由源码追踪确认。
4. CSS 是可逆的小改动，不添加只重复四条声明的脆弱静态单测。实施后用本机离线浏览器页面加载本次实际 CSS 与本地视频样本，检查宽 320/360/768 CSS px 下横/竖/小尺寸视频、引用/表格/折叠、明暗主题和音频未改；禁用外网请求。保存尺寸/截图与测试浏览器版本作为布局证据。

实施后默认质量门（研究阶段未执行）：

```bash
./gradlew :lib_core:testDebugUnitTest --tests 'gov.anzong.androidnga.core.decode.ForumBasicDecoderMediaTest' --console=plain
./gradlew :nga_phone_base_3.0:testDebugUnitTest --tests 'sp.phone.mvp.model.convert.PageAttachmentPrefixFlowTest' --tests 'sp.phone.mvp.model.thread.NormalArticleParserTest' --tests 'sp.phone.mvp.model.thread.AppArticleParserTest' --console=plain
./gradlew :nga_phone_base_3.0:assembleDebug testDebugUnitTest lintDebug --continue --console=plain
```

按 Android quality 合约查看所有 Android 模块 lint XML 的 Error/Fatal 为 0、测试报告确实执行且无失败；若旧报告不可信才重跑 lint。完成源码路径核对后，设备测试按当前未授权状态记“not run per project policy”，不设为用户必须补做的交付阻塞项，不运行 NGA 请求。离线浏览器布局与 JVM HTML 生成均不能写成“Android 实机视频播放已验证”。

U2 不依赖 U1 模型或 U3 JSON/DTO 改造，也不依赖 U4；执行顺序按父计划统一安排即可。回滚是撤回本子任务的 decoder helper + CSS + 专属测试改动，无缓存/数据库/设置迁移；既有 typed/absolute 能力自然回到本次基线。不要用整文件覆盖恢复其他会话的 shared 文件。

### 8. 原工作区与共享文件的只读比较

2026-09-30 对以下当前文件做字节只读比较，未执行 git 或写原工作区：

- `ForumBasicDecoder.java`、`style.css`、`ArticleConvertFactory.java`、`AppArticleParser.kt`、`NgaImageHost.java` 在源工作区和本独立工作区一致。
- `nga_phone_base_3.0/src/main/java/sp/phone/util/StringUtils.java` 不一致；父会话确认它属于 R5 未提交工作。U2 不修改此旧工具类，也不修改 R5 `ForumEmoticonDecoder` 或 R6 发帖代码。
- 这里只是研究时快照，不保证未来合并无冲突；最终整合继续保留其他会话交付并重跑媒体/前缀/正文回归。

### 9. Related specs

- `.trellis/spec/frontend/index.md` 与 `component-guidelines.md:625–642`：真正正文是 LocalWebView，不能给 dead TextView 加行为。
- `.trellis/spec/backend/thread-detail-compat-contract.md:131–145,250–257`：普通与兼容共用渲染、响应局部完整附件前缀、手动优先、源正文保留；不能把此项扩成 R7 读取修复。
- `.trellis/spec/backend/android-quality-guidelines.md:7–36,149–169`：设备 opt-in，默认构建/JVM/lint，所有模块 Error/Fatal 检查。
- `.trellis/spec/guides/code-reuse-thinking-guide.md`：新能力进入已有公共链，不引入平行 resolver/renderer。
- 前端/后端普通 quality 文件仍有模板部分；执行性质量门以 android-quality-guidelines 为准。

### 10. External references

- 上游 commit `750871b31b533dd74059ade146a084f396020cbe`：本任务 `upstream-750871b3.diff`，由父会话导出，已分段核对。
- [CSS width](https://developer.mozilla.org/docs/Web/CSS/Reference/Properties/width)、[max-width](https://developer.mozilla.org/docs/Web/CSS/Reference/Properties/max-width)、[height](https://developer.mozilla.org/docs/Web/CSS/Reference/Properties/height)、[display](https://developer.mozilla.org/docs/Web/CSS/Reference/Properties/display)；对应 MDN browser-compat-data `main/css/properties/{property}.json` 于研究日期直接读取。远程 main 可变化，研究记录保存了本次返回的兼容结论。
- Java 标准库 `java.net.URI` 与 `java.util.regex.Matcher`；工程 Java target 为 17，Android minSdk 29，不引入新库。

## Caveats / Not Found

- 未运行产品构建、单测、浏览器布局或媒体播放；本文件记录的是已核对源码与将要验证的确定方案。
- `ForumDecoder.decodeBasic` (`:41–42`) 调用的是 `IForumDecoder.decode(String)` 默认空实现 (`IForumDecoder.java:18–20`)；不要将它当媒体入口或顺手修复。
- 尽管 `ForumBasicDecoder.java:27–29` 有空 htmlData 前缀分支，随后 `:55` 等替换表达式会立即解引用 htmlData；因此非空内容 + null HtmlData 并非可用入口。兜底测试使用非空 HtmlData 且缺前缀；此旧问题不阻碍实际双参数生产链。
- 新规则不保证修复所有既有 typed/absolute 的畸形匹配或原始 HTML 行为；新增区间边界和 escaping 有独立完整测试。若将来要收紧旧规则，须作为明确的兼容行为变更另行安排。
- 没有新增 codec/播放器、音频样式、下载、自动播放、发帖编辑器能力、任意 host/path 支持；R7 产品修复仍排除。
