# U2 设计：视频排版与严格相对标签

状态：2026-09-30 用户已统一确认 U1–U4 全部方案，指定新会话实施；本轮未开始产品实施。来源 `750871b3`，当前基线 `1a8413d9`。

## 采用与保留

采用上游 `video` 宽高约束和无类型相对 `[flash]` 的视频含义；不复制其把任意首字符当作 `.` 的正则，也不复制退化的固定音频图床。
现有 typed video/audio、无类型绝对 Flash 链接、完整页面附件前缀、手动图床与公共正文链继续使用。
保持 Java `ForumBasicDecoder`，新增窄 helper 即可，不把本项变成 Kotlin/数据层重写。

详细证据、33 类夹具和实际调用位置见 [media-design-evidence.md](research/media-design-evidence.md)。

## 新标签的完整识别契约

```text
[flash]./relative/path[?query][#fragment][/flash]
```

- 标签大小写不敏感，必须字面 `./` 开头且路径非空。候选不能跨 `[`、`]`、换行：Java 字符串字面量 `"(?i)\\[flash\\](\\./[^\\[\\]\\r\\n]*)\\[/flash\\]"`。
- 对候选使用 `java.net.URI` 验证字符、percent triplet、无 scheme/authority；raw path 去掉 `./` 后按 `/` 分段并保留尾空段（`split("/", -1)`），每段非空，不能为 `.`/`..`。仅为判断 dot-segment 大小写不敏感地将 `%2e`（包括 `%2E`）视为点，不修改输出 URL。
- 拒绝空白/控制字符、未编码双引号、反斜杠、尖括号和非法百分号，不 trim、不修复。未知或不合格的匹配区间保留文本；一个坏标签不能令整段解码抛异常。
- 可保留 query/fragment、Unicode、合法单引号、`$`、percent escape 与原大小写；不规定 `mon_` 或扩展名白名单。无扩展名也可作为视频，实际播放能力由原浏览器决定。
- 不识别省略 `./`、`../`、`//host`、`/path`、未知 typed 标签。已有 typed/absolute 由原规则处理，其历史畸形输入行为不在本项重写。
- 有效内层标签可独立转换，畸形外层文本保留；这里不是通用 BBCode 嵌套解析器，不扩展外层语义。

## URL 与 HTML 生成

位置在原 typed video/audio 转换之后、方法 return 之前。复用入口已求得的完整 `attachmentsPrefix`，使用 `prefix + relative.substring(1)`；不调用 `URI.resolve`，不重新推导附件主机。

```html
<video src='ESCAPED_COMPLETE_URL' controls='controls'></video>
```

完整 URL 做 HTML 属性转义（依次 `&`、单引号、双引号、`<`、`>`），不能用 URL 编码替代。旧入口会先把 `&amp;` 解为 `&`，新增规则再编码一次；因此 query 的 DOM 值不丢失，也不变成双重 `&amp;amp;`。
用 StringBuilder 复制未匹配区间并插入最终字符串，或对完整 replacement 使用 `Matcher.quoteReplacement`；不让 URL 中 `$1` 成为 regex replacement 指令。
不修改共享 StringUtils，不改变老规则输出，不加 autoplay/muted/事件回调/下载器。

## 共享渲染与样式

实际路径是 NormalArticleParser / AppArticleParser → 现有 ArticleConvertFactory.renderRow → HtmlConvertFactory → ForumDecoder → ForumBasicDecoder。评论与签名已经使用同一公共 decoder。
源正文和可编辑文本保留原标签，只在展示阶段生成 HTML。附件列表 mp4/mp3 仍是链接，不因本项变成内嵌播放器。

公共 `lib_core/src/main/assets/html/style.css` 加入：

```css
video {
    width: auto;
    max-width: 100%;
    height: auto;
    display: block;
}
```

视频受其正文容器宽度约束，按固有比例缩放；小视频不强制拉满，竖视频不额外限高，不固定 16:9、不裁切。block 会使播放器独占一行。
明暗主题没有覆盖这些属性，audio 不受 video selector 影响；不改 WebView 设置。该基础 CSS 不依赖 SDK36。
祖先本身无限宽的旧表格、媒体元数据加载前布局位移、codec/远端可达性不是四条 CSS 可保证的结果。

## 代码与验证边界

产品文件仅 `ForumBasicDecoder.java`、公共 `style.css`。测试新增 core 媒体用例，并扩展 app 的 PageAttachmentPrefixFlowTest、两个 parser 的注入 renderer 案例。
用非空 HtmlData 测试有/无附件前缀。旧 `decodeBasic` 默认空实现、`decode(content,null)` 的旧异常不是当前生产媒体路径，也不在本项顺带修复。

离线 JVM 证明匹配、转义、前缀与双来源保真；本机离线浏览器使用实际 CSS 和本地样本验证布局。两者均不代表 Android 设备播放、全屏或服务端成功率。
不碰 R5 表情 decoder/R6 编辑器在途代码；U3 后续调整 JSON/wire/mapper 边界时保留现有 HtmlData 与渲染公共接口，继续运行本项的行为夹具。

## 回滚

没有设置、数据库、缓存格式或请求变化。独立撤回本项 helper、CSS 和对应测试即可恢复当前基线；按补丁回滚，不以整文件覆盖其他会话或 U3 改动。
