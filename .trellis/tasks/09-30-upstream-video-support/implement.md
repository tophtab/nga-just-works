# U2 实施计划

状态：2026-09-30 用户已统一确认 U1–U4 全部方案，指定新会话实施；本轮未开始产品实施。

## 实施顺序与所有权

1. 整体审核通过后按父计划 start 现有 U2，并派发 Trellis implement/check。所有操作使用独立工作区。
2. 在 `ForumBasicDecoder.java` 增加明确边界的无类型相对标签 helper，复用现有完整前缀，加入 URI 校验与 literal-safe 属性转义/替换。保留老 typed/absolute 行为。
3. 在公共 `style.css` 增加设计中的四属性 video 规则，不修改主题、WebView、附件列表或正文源文。
4. 加入执行真实 decoder 的媒体测试，扩展现有页面前缀及 normal/compat 注入 renderer 回归；不写只是复述正则/CSS 声明的镜像测试。
5. 独立检查完整 diff、所有新增文件、生成 HTML 与局部样式范围；修复本项问题后运行质量门并记录真实结果。

## 验收矩阵

| ID | 行为组 | 核对点 |
| --- | --- | --- |
| U2-A1 | 基础相对标签、大小写、无扩展名/Unicode | 精确拼接一次完整前缀，controls video，源正文保留 |
| U2-A2 | typed video/audio、HTTP/HTTPS absolute | 原视频、音频 filename 参数、Flash 图标链接输出保持 |
| U2-A3 | 相邻标签、夹杂文字、畸形闭合/嵌套 | 每个有效 span 独立转换，无首字/尾文/邻接标签丢失 |
| U2-A4 | `$1`、`&amp;`、单引号、percent/query/fragment | replacement 字面安全，HTML 属性正确编码一次，DOM URL 不被改写 |
| U2-A5 | 空路径/尾空段、dot segment（含 `%2E%2e`）、换行/空白/非法 URI、未知 typed | 新规则保留可读文本，不抛整页异常、不猜地址 |
| U2-A6 | 多页面前缀、auto/manual/img9、无前缀 fallback | 使用当前权威 resolver 的完整结果、页面互不污染，HTTP img9 不被强升 HTTPS |
| U2-A7 | 普通/兼容、评论/签名、附件列表 | 公共显示链一致；附件仍为链接；编辑源文不被 HTML 替换 |
| U2-A8 | 实际 CSS + 本地横/竖/小样本 | 宽度不超正常容器，比例保持；block/小视频/音频/明暗主题符合设计 |

具体输入与期望输出全部在 [研究夹具表](research/media-design-evidence.md)，作为实施和审核的共同依据。

## 检查命令与布局证据

```bash
./gradlew :lib_core:testDebugUnitTest --tests 'gov.anzong.androidnga.core.decode.ForumBasicDecoderMediaTest' --console=plain
./gradlew :nga_phone_base_3.0:testDebugUnitTest --tests 'sp.phone.mvp.model.convert.PageAttachmentPrefixFlowTest' --tests 'sp.phone.mvp.model.thread.NormalArticleParserTest' --tests 'sp.phone.mvp.model.thread.AppArticleParserTest' --console=plain
./gradlew :nga_phone_base_3.0:assembleDebug testDebugUnitTest --continue --console=plain
./gradlew lintDebug --continue --rerun-tasks --console=plain
```

核对所有 Android 模块的 test/lint XML，不以 lint 退出码代替 0 Error/Fatal。无需新增 JSON 或媒体依赖。
使用本机离线浏览器页面加载本次实际 CSS、本地视频样本；在 320/360/768 CSS px 下测普通正文、窄引用、正常表格与折叠展开，记录元素尺寸、截图及浏览器版本。页面禁止请求外网；不开 Android 设备、不请求 NGA。
精确区分布局验证与视频播放结论。若运行环境无法提供浏览器，记录具体缺项，不能以 JVM 字符串测试冒充布局通过。

## 交付与回滚

- 把新标签语法和公共样式契约写入相关规范，保留原规范中的其他会话内容。
- 在本子任务记录测试/布局/检查结果和未执行设备项；父任务继续 U3/U4，无需因子任务切换重复申请批准。
- U3 迁移后重跑 U2 媒体和前缀测试，U4 变更后纳入最终构建/显示路径检查。
- 回滚只撤 U2 功能补丁，不清用户数据；不覆盖 R5/R6 或其他子任务的同文件变更。

## 规划审核处理

[独立规划审核](research/planning-review.md) 的两处精确化已写入设计：正则明确为 Java 字符串字面量；路径 split 保留尾空段，编码点判断忽略大小写，并补混合 `%2E%2e` 夹具。它是规划审核，不是产品测试通过。
