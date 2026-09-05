# 技术设计：历史缓存旧图床兼容

## 1. Problem Boundary

缓存已经保存相对附件路径，问题不在“绝对路径还是相对路径”，而在相对路径重新展开时采用了历史页面中的退役主机：

```text
历史 THREAD.PAGE JSON
  ├─ __GLOBAL._ATTACH_BASE_VIEW = img.nga.178.com/attachments
  └─ content / attachurl = 相对附件路径
                 │
                 ▼
ArticleConvertFactory.resolveAttachmentsPrefix
                 │
                 ▼
NgaImageHost 自动模式（当前只校验语法）
                 │
                 ▼
HtmlData → decoder / attachment builder → 旧完整 URL → 加载失败
```

修复应位于解析边界，而不是导入边界。这样同一规则同时覆盖原地缓存、导入缓存以及仍携带历史字段的响应，并且无需不可逆地改写用户数据。

头像是并列的数据流：`__U.avatar → FunctionUtils.parseAvatarUrl → Glide`。它不经过正文 decoder，因此需要在头像 URL 提取边界显式复用遗留主机归一化。

## 2. Resolver Design

### 2.1 保持职责集中

所有退役图床判断继续归属 `NgaImageHost`。不要在 `ArticleConvertFactory`、导入器或 WebView HTML builder 中各写一份域名列表。

在自动模式分支中采用两步解析：

1. 现有 `sanitizeServerAttachmentBaseView` 负责语法、scheme、host 和 `/attachments` 路径形态；
2. 新的纯辅助判断识别规范化结果中的已知退役图床 host；命中时返回 `DEFAULT_ATTACHMENTS_PREFIX`，否则返回页面前缀。

伪代码：

```java
String serverPrefix = sanitizeServerAttachmentBaseView(serverAttachmentBaseView);
if (serverPrefix == null || isRetiredImageHost(serverPrefix)) {
    return DEFAULT_ATTACHMENTS_PREFIX;
}
return serverPrefix;
```

退役 host 匹配必须锚定完整主机，覆盖可选数字编号且大小写不敏感：

```text
img*.nga.178.com
img*.ngacn.cc
```

实现时可以从已存在的 `LEGACY_HOST` 规则提取/复用精确 host 判断，避免正文正则与页面 resolver 的域名清单漂移。

### 2.2 为什么不直接修改通用 sanitizer

`sanitizeServerAttachmentBaseView` 也被 `normalizeLegacyHosts(content, attachmentsPrefix)` 用来校验调用方提供的最终前缀。若它无条件把旧主机改成默认主机，用户手动配置的自定义主机可能在不同消费者中出现不一致结果。

因此“退役页面值回退”只在 `MODE_AUTO` 消费服务器值时发生；三种手动模式保持当前优先级和原样解析。

### 2.3 路径族

`_ATTACH_BASE_VIEW` 明确属于 `/attachments`，所以旧的带编号 host 也统一落到
`https://img.nga.cn/attachments`，不保留编号。正文内的其他遗留绝对路径继续交给现有 `normalizeLegacyHosts`：

- `/attachments/...` 使用本页最终附件前缀；
- `/ngabbs/post/smile/...` 等非附件路径保留 `img` 编号，只将旧域名后缀迁为 `.nga.cn`；
- 主站、当前地址、本地 asset 保持不变。

## 3. Page Rendering Data Flow

`ArticleConvertFactory` 已经每页读取一次 `_ATTACH_BASE_VIEW` 并把最终前缀传入 `HtmlData`。修复 resolver 后，下游无需增加另一套迁移逻辑：

```text
resolveAttachmentsPrefix(page JSON)
  → one immutable attachmentsPrefix
  → body / comments / signatures / vote / audio / video
  → HtmlAttachmentBuilder
  → collected image URL list
```

保留以下不变量：

- 页面值不写进 static、SharedPreferences 或 `ThreadRowInfo`；
- 自动模式的未知合法新主机仍逐页生效；
- 手动模式忽略页面值；
- 无页面上下文继续使用现有安全默认/手动设置。

## 4. Avatar Compatibility

`FunctionUtils.parseAvatarUrl` 先按既有逻辑从直接 URL 或头像 JSON 字符串中提取最终 URL，然后在所有返回分支统一执行：

```java
NgaImageHost.normalizeLegacyHosts(extractedUrl)
```

对 `/avatars/...`，现有非附件规则会把：

```text
http(s)://img.nga.178.com/avatars/...
→ https://img.nga.cn/avatars/...
```

该设计避免把头像误当 `/attachments`，也避免在 `ArticleListAdapter`、`ProfileActivity`、评论 HTML 等多个调用点重复改写。null 和非匹配输入由现有 helper 原样通过。

若 `FunctionUtils` 的 Android 依赖使纯 JVM 行为测试不可行，优先把“提取后归一化”的小段逻辑拆成可测试的纯函数；不要用真实用户字符串或联网测试替代契约测试。

## 5. Cache and Privacy Boundary

- 不改变 `cachePage`、ZIP 打包或解包逻辑。
- 不改写用户 JSON；同一历史包在新旧版本间仍可移植。
- 不将 `.temp/cache_20260905084551.zip` 加入测试资源。
- 合成 fixture 只保留字段形态，例如 `__GLOBAL._ATTACH_BASE_VIEW` 与虚构 `mon_test/...` 路径。
- 运行时不探测 DNS/HTTP；已知域名映射是确定性兼容规则。

## 6. Tests

### 6.1 `NgaImageHostContractTest`

- 自动模式：裸 host、HTTP/HTTPS、协议相对、带 `/attachments[/]` 的退役主机均落到默认前缀；覆盖无编号和编号 host、两个旧后缀。
- 未知合法服务器 host 继续按页面值返回，连续页面值不串页。
- 手动三模式忽略退役服务器值。
- 遗留 avatar URL 走非附件规则到对应 `.nga.cn` host。
- 当前地址、论坛主机、表情路径族和本地 asset 的既有测试保持通过。

### 6.2 页面转换与附件链

- `ArticleConvertFactoryTest` 增加历史 `_ATTACH_BASE_VIEW` → 默认前缀断言。
- 用最小合成页面或已有 `PageAttachmentPrefixFlowTest` 证明正文相对图、附件 HTML 和 image URL list 使用同一个修正后前缀。

### 6.3 头像入口

- 覆盖直接旧 URL、嵌套头像 JSON、当前 URL、其他 host、null 和不含 HTTP 的输入。
- 测试只用虚构路径。

## 7. Compatibility, Rollback, and Risk

- 这是读取/渲染期兼容，不迁移持久化数据，代码回滚无需反向数据迁移。
- 最大风险是 host 规则过宽；通过完整 host 锚定和论坛主机负例防护。
- 第二个风险是误伤手动自定义语义；通过只在自动分支应用退役页面回退来隔离。
- 第三个风险是只修正文未修附件或头像；已有 `HtmlData` 数据流测试和新增头像入口测试共同守门。
- 未来若 NGA 再迁域名，应在 `NgaImageHost` 和平台访问规范中更新映射，不能在导入器中增加临时字符串替换。
