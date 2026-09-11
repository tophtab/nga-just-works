# Research: 分支小修复与 JSON 实验汇总交叉核对

- Query: 核对 `branch-and-small-fixes.md` 与 `json-compatibility.md` 是否存在具体事实错误或超出证据的表述。
- Scope: internal；读取已有上游补丁/最终源码快照、当前源码、保存的合成探针输入和输出；不运行 Git、联网或构建。
- Date: 2026-09-11

## Findings

在本次核对范围内，未发现需要修改的具体事实错误或过度结论。

- JSON 汇总的 24 个 `jdata` 组合和 6 个上传组合与已保存 TSV 一致；探针会检查保留的合成板块名称或 URL，确实不只是检查“未抛异常”。
- 探针中的 `jdata` 正则与上游 `93acf42a5b6601f6ee6e257bb31895a3b50ad458` 补丁一致；末字段和空格边界失败的表述与输入、输出一致。上游 `JSON.parseObject` 位于 `try` 外，因此报告没有误称扩大的 `catch` 能兜住解析异常。
- 上游最终 `ForumBoardViewModel.kt:96-99` 仅在增量板块非空时合并；`ForumBoardModel.kt:192-193` 先保存图标前缀，`:220-229` 只在合并中重新计算图标。报告关于“只变前缀不会立刻更新当前模型”的已修正文案符合源码。
- `6a78543086cf439aa695d28c56aeca1f8ae8bdd4` 将持久化对象从 `localBoardList` 改为 `boardMap.values`；map 由根节点和递归子节点填充，读回时作为根列表使用。报告对层级和顺序风险的判断有代码依据，并明确未做重启 UI 验证。
- 当前两个板块图标 URL 确实使用 `https://img4.nga.cn` 的不同路径族；当前板块持久化仍保存根列表。
- `86e6e782` 和 `22ba3082` 的版本及 SDK 数值与补丁一致；当前 minSdk 29、compile/target 35、派生版本变量与当前 `build.gradle:120-125` 一致。

### Files found / code patterns

| 文件 | 核对作用 |
| --- | --- |
| `research/branch-and-small-fixes.md` | 被核对的分支、图床、板块和 SDK 汇总 |
| `research/json-compatibility.md` | 被核对的 JSON 兼容实验解释 |
| `research/probes/{JsonProbe,UploadJsonProbe}.java` | 实际输入、解析方式和结果判据 |
| `research/probes/{result,upload-result}.tsv`、`fixtures/*.json` | 保存的 30 个组合结果和六个合成夹具 |
| `22ba3082:nga_phone_base_3.0/src/main/java/sp/phone/mvp/model/convert/TopicConvertFactory.java:35` | 正则、解析与异常捕获边界 |
| `22ba3082:nga_phone_base_3.0/src/main/java/gov/anzong/androidnga/activity/compose/board/ForumBoardModel.kt:30,57,188,220,233` | 根/子节点 map、图标前缀保存、重算和持久化 |
| `22ba3082:nga_phone_base_3.0/src/main/java/gov/anzong/androidnga/activity/compose/board/ForumBoardRepository.kt:29` | 本地 JSON 数组直接读为根列表 |
| `8284c703:nga_phone_base_3.0/src/main/java/gov/anzong/androidnga/activity/compose/board/ForumBoardModel.kt:421` | 当前根列表保存边界 |
| `8284c703:nga_phone_base_3.0/src/main/java/sp/phone/common/ApiConstants.java:12,23` | 当前两个板块图标路径 |
| `8284c703:build.gradle:120` | 当前 SDK 和版本来源 |

### External references / versions

- 上游固定为 `22ba3082501bcbb08f52a66d787f970f59c2dda7`；读取主会话已导出的 `93acf42a`、`6a785430`、`86e6e782`、`22ba3082` 完整补丁。
- 实验版本为 `com.alibaba:fastjson:1.1.71.android` 与 `com.alibaba.fastjson2:fastjson2:2.0.59.android8`；本次核对读取保存结果，没有重跑或下载。

### Related specs

- `.trellis/spec/backend/network-foundation-contract.md`：源码观察不等于线上可用性，保持操作级解析边界。
- `.trellis/spec/backend/nga-platform-access-rules.md`：板块图标与附件路径族区分，验证以离线证据为限。

## Caveats / Not Found

- 本轮没有独立重跑 Git 分支计数、patch-id 或历史提交追溯；这些沿用主会话保存的清单和已有检查，未从源码快照倒推出 Git 结论。
- 没有重跑 JVM 探针、真实服务或 Android UI。此结论确认汇总与已有源码/实验相符，不扩展为运行时兼容保证。
- 未编辑主会话的两份汇总文件。
