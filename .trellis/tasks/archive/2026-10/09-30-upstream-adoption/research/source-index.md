# 上游采用依据索引

本文件整合已经完成的源码调查供后续规划使用，没有新增 NGA 请求或重新认定服务端根因。
2026-09-30 刷新上游：master_release=22ba3082501bcbb08f52a66d787f970f59c2dda7，master=cdad1abba80236602dc98999bee53d802412a7e4。
四类任务对应 8 月已知改动，未发现其后新增的读帖修复提交。

| 子任务 | 上游来源 | 重点复核 |
| --- | --- | --- |
| U1 | 6a785430、750871b3 | forum_icon_pre → iconUrl → UI；仅前缀变化刷新；收藏排序与根节点持久化 |
| U2 | 750871b3 | video CSS、无类型相对 flash；修正宽泛正则，保留当前完整图床和音频 |
| U3 | 1307d324 至 a6e4a890、25652de8 | DTO/展示模型/解析边界；JSON 网络与本地格式；避免上游迁移回归 |
| U4 | 22ba3082 | compile/target 36；工具链和 Android 16 行为；本 fork minSdk 29 |

## 已有完整资料

- [逐项采用清单](../../archive/2026-09/09-11-upstream-august-2026-review/research/adoption-map.md)
- [图标、刷新与 SDK](../../archive/2026-09/09-11-upstream-august-2026-review/research/branch-and-small-fixes.md)
- [视频与兼容模式差异](../../archive/2026-09/09-11-upstream-august-2026-review/research/bugfix-browser.md)
- [数据重构](../../archive/2026-09/09-11-upstream-august-2026-review/research/data-refactor.md)
- [JSON 兼容案例](../../archive/2026-09/09-11-upstream-august-2026-review/research/json-compatibility.md)
- [最新上游核查](../../archive/2026-09/09-30-reader-native-loading-research/research/upstream-compat-audit-2026-09-30.md)

旧报告部分“本 fork 当前状态”固定在早期基线；后续规划须结合采用清单的 9 月 12 日实施更新和当前工作树。
尤其兼容核心已经合入，不应按最初审计重复实施。当前 R1–R6 有其他会话的工作，未来实施要用独立工作区并纳入最终整合结果。

## 2026-09-30 U1 设计复核

- [当前上游、URL 与刷新边界](../../09-30-upstream-board-icons/research/upstream-refresh-and-url.md)
- [状态、持久化与 UI](../../09-30-upstream-board-icons/research/state-persistence-ui.md)
- [独立工作区与审核顺序](workspace-isolation.md)

当前 U1 远程 UI 是 Coil，不是旧材料所述 Glide；本轮方案使用旧原始缓存迁移前缀，并保留原一天节流。其余三项专项设计已在本轮全部完成，统一审核入口见父review.md。

## 本轮U2–U4与整体证据

- [U2媒体/标签/CSS全部夹具](../../09-30-upstream-video-support/research/media-design-evidence.md)
- [U3完整消费者/存储/R8清单](../../09-30-upstream-data-layer/research/consumer-inventory.md)
- [U3全部批次与模型边界](../../09-30-upstream-data-layer/research/migration-design-evidence.md)
- [U3双库离线probe复现](../../09-30-upstream-data-layer/research/probes/README.md)
- [U4固定工具链/CI/native证据](../../09-30-upstream-sdk-36/research/platform-toolchain-evidence.md)
- [U4官方全平台适用性矩阵](../../09-30-upstream-sdk-36/research/android16-applicability-matrix.md)
- [其他会话整合边界](concurrent-work-boundaries.md)
- [统一审核摘要](../review.md)
- [跨任务规划审核](joint-planning-review.md)
- [审核修订与最终文档校验](../planning-validation.md)

旧报告中“core已经有fastjson2生产消费”“core_data已有整套帖子Kotlin模型”等判断不能沿用：本轮源码已重新盘点，当前app兼容DTO在app，core仅声明JSON2依赖。U3按实际消费面设计；这不是重复实现已交付的兼容读取。
