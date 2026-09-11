# Implementation plan — 帖子详情兼容模式（范围修订版）

## Baseline and entry gate

当前为 in_progress，修订方案已于 2026-09-12 获得实施批准。本清单取代 [旧清单](history/implement-v1.md)，包含完整帖、PID、作者查询和按字段降级；已有授权继续有效，不重复询问。

以 `main@5bb92cf033aa32d749d10e1a497bc05173cd2955` 或包含它的更新基线工作，保留 `6203dad5`。本轮开始时分支名为fix/thread-menu-cache，结束核对时由外部工作切到相同提交的main，没有产品树变化。计划分支 `feature/thread-detail-compat-mode`；启动前检查并行WIP，需要时隔离worktree，不动已有AI summary及其他任务。

用户已在修订摘要及术语说明后回复「行」，task start 已完成。auto模式按workflow派发trellis-implement，完成后独立trellis-check；主会话协调、审查和规范更新。每次prompt以`Active task: .trellis/tasks/archive/2026-09/09-11-thread-detail-compat-mode`开头，明确文件责任、共享工作区与不得覆盖他人修改；子agent不递归派发。

manifest中的android-quality-guidelines.md、component-guidelines.md和query-pagination-revision.md超过自动注入上限：implement/check必须用工具分段读取这三份原文件，不能把截断注入当完整上下文。新研究优先于旧研究的范围建议，最终选择以prd/design为准。

## Ordered implementation

1. **纯契约与upstream adapter**：复用pinned DTO与实际字段映射，定义query/source/generation、nullable paging、row presentation、content completeness和明确error/data结果。先写有行为断言的合成夹具；未知扩展不反射成硬失败，保留source/raw/attribution。
2. **原生渲染与行行为**：抽现有app renderer seam；保留图片prefix、附件/评论/屏蔽先后、WP/匿名/client信息。接入HTML引用头、UID楼主、明确comment菜单、未知score/identity及不可用正文占位；不生成上游没有的侧载协议。
3. **统一查询与分页**：ThreadData+activity generation；修改pager/count/initial page/pending anchor和quote helper所有消费者。PID采用scoped view且show-all使用resolvedTid；作者过滤各页不丢参数。App非20和缺metadata可读，旧normal20封装明确。
4. **操作与source切换**：endpoint-specific byte client、normal/App策略、取消/身份/设置重查；App来源绑定当前query generation，后续页/刷新同源。必要时最多一个锚点对齐请求；普通预取保留并加query/source/generation资格检查，旧链账号保护先count。
5. **缓存集成**：复用ArticlePageCache资格和metadata准备，新增owner/layout envelope及统一entry resolver。list/open/read/write/delete成套接入；旧raw保持原parser，不同来源/页大小分开，未知size保存单窗口；关闭开关可回读。缓存缺参guard按生命周期正确位置加。
6. **设置、终态与集成回归**：默认关闭实验室开关、四种开关组合、loading清理、浏览器查询上下文；缓存列表区别可用版本，旧zip导出范围准确。验证通知→回复→显示全部→缓存与现有菜单/页签。
7. **质量和交付**：执行下面的离线gate，保存check-results；独立check审完整diff。主会话按实际代码更新操作、分页/预取/cache/UI规范。只有验证完成才汇报实现完成；按workflow提供具体commit分组并收尾，不执行未授权发布。

这些步骤共享模型/Presenter，按依赖顺序完成；不要派多个worker并行修改同一链。

## Required behavior matrix

| Area | Meaningful offline assertions |
| --- | --- |
| Queries | tid全文、pid-only、pid+author、作者分页/刷新均保留字段；searchPost本地路由；PID实际命中；show-all清字段且resolvedTid正确 |
| Paging | 10/20/30/40及短页、楼层缺口；缺perPage/total/current、reported page不同、overflow；保留正文、降低导航，不制造行数 |
| Anchors | 目标child未创建时保留pending PID/lou；按实际行查找；普通20第7页→App10第13页最多一次对齐；找不到不扫页、不跳错 |
| Source/reuse | App源后续页和刷新一致；开关关闭/查询或账号变化重置；旧generation不能更新READY/title/count/body；无不同page-layout串页 |
| Content | 已消费source/author正确；未知attches/hot/comment/head的空/null/0/[]/{}/任意类型不整页拒绝；不注入head、不造parent/score |
| Incomplete | 明确不可读source有占位或错误；不丢条目、不缓存不完整页；已知空/subject/alter/屏蔽按原语义；已知错误赢过残留result |
| Row UI | UID楼主在后页/PID/过滤正常，0/匿名不误判；真实评论隐藏相关动作，稀疏普通行不误判；未知身份/score与回收view复位 |
| Rendering | literal-safe/幂等HTML引用头；编辑引用仍用source；完整手动/自动prefix，两页隔离，旧附件/评论头像/WP/client行为不退化 |
| Foreground | 四开关组合、0/1/2账号、空/相同nextCookie、同账号App、pause/detach/stale；背景无App/Toast/WebView/换账号 |
| Transport | URL/method/form/headers、exact origin、redirect/retry限制、UTF8/GBK与无/非法charset、4MiB/截断、HTTP与业务错误、取消；无真实流量 |
| Cache | legacy/read_php/App显式dispatch；相同tid/page不同owner/source/size独立；unknown-size窗口独立；关闭开关回读；corrupt/version/owner/layout错误不联网 |
| Cache UX | 稀疏2/7和2/10数值排序、1–5等宽tab、selected child与metadata原文/补齐、删除选中entry不影响其他、旧zip范围 |

保留并按实际改动扩展ArticlePageCacheTest、ArticlePageRequestStateTest、ArticlePagePrefetchPlannerTest、TopicPagePrefetchContractTest、ArticlePageRefreshContractTest、ArticleConvertFactoryTest、PageAttachmentPrefixFlowTest、FunctionUtilsAvatarTest及common NgaImageHost tests。新增测试验证行为与副作用，避免只检查实现名称或重复CSS。

## Validation commands

先跑新增pure/fake tests和受影响回归，再执行必需gate；结果通过后只有新改动/失败/未解决问题才扩大或重复。

```bash
./gradlew :nga_phone_base_3.0:testDebugUnitTest :lib_base_common:testDebugUnitTest --console=plain
./gradlew :nga_phone_base_3.0:assembleDebug :nga_phone_base_3.0:lintDebug --console=plain
./gradlew lintDebug --continue --rerun-tasks --console=plain
./gradlew testDebugUnitTest --continue --console=plain
```

独立审查补充：为保证已知父级评论的异常正文仅局部占位，实际改动包含
`lib_core` 的 `HtmlCommentBuilder` 完整回复头裁剪；这是 design §5 已允许的必要
renderer 适配。最终 gate 额外执行 `:lib_core:testDebugUnitTest` 和
`:lib_core:lintDebug`，不顺带搬迁 core 模块或调整其产品依赖。

逐一核对settings.gradle中Android模块的lint XML全部存在且0 Error/Fatal。末条是debug诊断，lib_base_ui/lib_bu_statistics缺JUnit、lib_core Android-dependent示例、lib_module_debug KAPT的已知基线需按规范记录；若实现实际触及该模块，处理owned baseline或给出具体影响分析，不能用旧基线掩盖新失败。

不运行aggregate test/signing变体、NGA真实流量、账号存储读取、ADB/install/connected/device、push/merge/release。若无需变更core则不为共享renderer顺带迁移它；若必要变更，其检查范围相应扩大。

## Gate and rollback

- [x] 最新PRD/design/implement与用户最新采用边界一致，最终摘要得到后续实施批准。
- [x] implement/check manifests有真实上下文，validate通过；原超长spec已安排实际读取。
- [x] 实现基线包含6203dad5，并行WIP已保护；旧20-only摘要未被当作批准。
- [x] 完成实际代码、全部相关离线检查及独立完整diff审查；检查报告区分离线验证与真实接口未验证。最终 app/common/core 278 项通过，13 模块 lint 0 Error/Fatal；见 [delivery.md](delivery.md) 与 [independent-check.md](independent-check.md)。

默认off可停止App请求，已存新页仍可读；旧raw不迁移、不重贴owner。撤回代码不清空用户数据、不覆盖其他任务。新cache zip、动态图标/独立媒体、全局JSON/SDK/纯模块迁移按完整采用台账另批处理，并不代表放弃这些上游变化。

## Execution workspace — 2026-09-12

Implementation uses `/home/toph/nga-just-works-compat-mode`, branch `feature/thread-detail-compat-mode`, based on `5bb92cf0`. All product edits and checks must run there. Root `main` and the existing AI-summary worktree remain untouched. Main session owns task metadata/specs; product implementer must not commit or change task status.
