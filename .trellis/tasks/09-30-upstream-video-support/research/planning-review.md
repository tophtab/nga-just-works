# Research: U2 规划文档转写审核

- Query: 复核本任务 prd.md、design.md、implement.md 是否准确承接 media-design-evidence.md，检查语法转义、兼容范围和验证边界。
- Scope: internal；规划和既有研究文件，只读产品源码，不运行产品测试。
- Date: 2026-09-30

## Findings

结论：三份文档已完整承接 U2 的功能边界、旧行为保留、公共调用链、离线验收与独立回滚。没有阻碍整体方案评审的架构冲突；建议主会话在交给用户前修正下面两个精度问题。所有任务仍处于 U1–U4 整体审核前的 planning，不单独启动 U2。

### R1 — 明确候选模式是正则文本还是 Java 字符串（中）

- 文件：`../design.md:19`。
- 当前写作“Java 模式 `(?i)\\[flash\\](\\./[^\\[\\]\\r\\n]*)\\[/flash\\]`”。这里展示的其实是 **Java 字符串中的转义内容**，不是正则引擎接收的文本；没有引号与显式标注，直接作为 raw regex 使用会把反斜杠解释错。
- 建议统一选用以下正则原文并明确说明“正则文本，写入 Java 字符串时再转义反斜杠”：

```text
(?i)\[flash\](\./[^\[\]\r\n]*)\[/flash\]
```

- 或者保留双反斜杠，但放入明确的 Java 字符串字面量：

```java
"(?i)\\[flash\\](\\./[^\\[\\]\\r\\n]*)\\[/flash\\]"
```

- `research/media-design-evidence.md` 已使用第二种带引号、明确 Java 字符串的写法，事实本身无误。此项只消除设计转写歧义。

### R2 — URI 分段校验补齐两个实施约束（中）

- 文件：`../design.md:20`，以及 `../implement.md:21` 的 U2-A5。
- 研究明确要求 `/` 分段保留尾空段，并对 `%2e` 大小写不敏感地做 dot-segment 判断；设计缩写后丢失了这两个显式限定。
- 风险：Java `split("/")` 默认丢弃末尾空段，可能把应该保留原文的 `[flash]./dir/[/flash]` 接受为视频；只替换字面 `%2e` 则会遗漏 `%2E` 和大小写混合的 dot-segment。
- 建议将设计该句补成：按 `/` 分段并保留尾空段（Java `split("/", -1)`）；每段非空。对 `%2e` 做大小写不敏感的仅用于判断的替换，拒绝 `.` 和 `..`，不改最终 URL。
- 验收补充明确的 `[flash]./%2E%2e/a.mp4[/flash]` 与 `[flash]./dir/[/flash]` 均保留原文。原研究已有小写 encoded-dot 和尾斜杠夹具，新增混合大小写仅补齐明确边界。

### 通过的主要核对

- PRD 只描述用户行为、验收、范围；设计及执行计划分别承担实现契约和顺序。
- 保留 typed video/audio、HTTP/HTTPS absolute Flash 图标链接的正常输出，明确不顺带修复所有旧畸形输入。
- 新标签 literal `./`、URI 校验、非空路径、query/fragment、Unicode、单引号/美元符号 escaping 与逐 span 拼接完整；非法候选只影响自身。
- source 与展示分离、manual/response-local 前缀、评论/签名公共链、附件列表继续链接均准确。
- CSS 四属性、block、小视频不撑满、竖视频不限高、固有比例、元数据前布局限制、祖先自身溢出限制均准确。
- 产品文件限定 core decoder + CSS；测试针对生产 decoder 和两 parser seam；避开旧 null/decodeBasic 入口。
- JVM/浏览器布局/Android 设备播放/真实服务结果的证据等级清楚；全模块 Debug 质量门与设备/NGA 禁止项一致。
- U3 后续迁移与 U4 最终整合须重跑媒体夹具；R5/R6 未提交改动和 R7 排除范围未误纳入。

## Files Found / Related Specs

- `.trellis/tasks/09-30-upstream-video-support/prd.md`：用户行为和验收映射。
- `.trellis/tasks/09-30-upstream-video-support/design.md`：严格标签、HTML 与 CSS 契约。
- `.trellis/tasks/09-30-upstream-video-support/implement.md`：顺序、测试矩阵和默认质量门。
- `.trellis/tasks/09-30-upstream-video-support/research/media-design-evidence.md`：本次核对依据及完整夹具。
- `.trellis/spec/backend/thread-detail-compat-contract.md`：共用渲染、源正文保留与页面前缀。
- `.trellis/spec/backend/android-quality-guidelines.md`：离线默认质量门、设备 opt-in。

## External References

本次是转写审核，没有新增外部检索。URI、Java regex、MDN CSS 的依据沿用 media-design-evidence.md。

## Caveats / Not Found

- 未写产品代码或修改三份规划；仅给主会话反馈上述修订。
- 未运行产品测试、浏览器、构建或设备操作；“规划一致”不等于功能已验证。
