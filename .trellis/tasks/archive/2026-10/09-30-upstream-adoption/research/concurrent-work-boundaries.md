# 其他会话与四项集成边界

当前交接补充（2026-09-30）：用户已整体批准，指定新会话实施；原工作区随后被清理，文档现已恢复到主仓库。下文保留规划/审核时的历史状态，当前授权与工作区以父任务approval.md、handoff.md和recovery.md为准。

日期：2026-09-30。只读检查来源工作区 `/home/toph/nga-just-works`，独立工作区基线仍为 `1a8413d9`。
原工作区正在进行 R1–R6 的产品改动；本轮没有复制或修改这些内容。以下是规划时的快照，不声称其他会话已经交付。

## 对本任务的影响

| 分组 | 当前在途文件/行为 | 本任务处理 |
| --- | --- | --- |
| R1 网页唤起 | Manifest、ArticleListActivity、ArticleLinkParser/ArticleListParam、ArticleLaunchTarget | U4 的平台/返回检查保留现有链接参数契约；不能以旧 manifest/Activity 整文件覆盖 |
| R2 属地生命周期 | AuthorLocation*/ProfileLocation*、ArticleReaderSession、相关测试与规范 | U3 JSON/模型迁移保留成功/空值/停止/失败区分与查看账号维度；不得顺带改请求策略 |
| R3 错误消息 | ArticleFailure、ArticleReaderSession、ArticleListModel/Presenter、错误测试 | U3 只改变数据表示/解析归属，错误语义与取消处理保持 |
| R4 回复楼层 | ArticlePage、ArticleShareViewModel、ArticleListFragment、启动目标测试 | U3 adapter 必须保持真实楼层、定位参数与完整列表关系 |
| R5 表情 | EmoticonUtils、ForumEmoticonDecoder、StringUtils、decoder 测试 | U2 只改 BasicDecoder/CSS；U3 调整 JSON/wire/mapper 边界时保留 R5 表情逻辑和现有 HtmlData/渲染接口，不覆盖文件 |
| R6 编辑媒体 | TopicPostPresenter/Fragment、编辑器类与布局、editor-inline-media-contract | U3 调整 JSON/wire/mapper 边界时保留 Bundle 草稿、源文与预览区分；U4 仅适配返回分发，保留现有编辑草稿行为，不新增确认语义 |

## 确定的整合规则

1. 本轮 U1–U4 全部分析和规划；统一审核前不实施任何产品改动。
2. 实施使用当前独立分支，U1→U2→U3（内部依赖批次）→U4 是可审查的默认提交顺序。未完成的外部会话改动不成为本任务的产品修复分支。
3. 外部 R1–R6 有正式交付后，在 U3 大范围迁移前或其后最近的稳定检查点整合对应已提交版本；使用逐文件/逐段冲突处理，不复制当前源脏树。若它们仍未交付，可以完成本分支的隔离验证，但父集成记录必须准确标记未包含的行为版本。
4. 两边共享的规范采用局部增补：动态图标、视频、JSON 或平台适配内容不能替换整份新规范，不能把原工作区的无关未提交改动纳入本分支提交。
5. 合并后按实际变化重跑受影响验收组，并在父任务最终集成检查上运行全工程 Debug test/lint、构建及需要的 Release R8 检查；不把旧基线测试结果当成整合结果。
6. 如果正式交付改变了已审核方案的产品范围/兼容保证，再报告具体差异并重新审核；常规文本冲突处理与不改变方案的适配不需要重新申请逐项实施许可。

## 2026-10-01 实施基线核对

本次独立工作区基于主仓库 `557f7bea398ed7a7702a876ec99d92eff5d7672e`，已包含 R1–R6 的正式交付与最新编辑器表情尺寸/基线修复。上述表格保留为历史责任边界；本次按已合入源码实施，不需复制在途产品文件。

恢复文件原先在本节之后混入了无关 shell / probe transcript。2026-10-01 仅在实施工作区删除此损坏尾部；源仓库恢复副本保留，未执行该尾部内容。对应 U3/U4 的独立 probe / native 研究文件仍保留为历史证据。
