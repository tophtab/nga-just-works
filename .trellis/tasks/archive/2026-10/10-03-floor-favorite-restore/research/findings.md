# Issue #10：楼层收藏、跳转与单独页码调研

本文保留实施前调研事实和候选建议。用户后续已批准仅恢复原有楼层收藏菜单及调用；最新执行范围以任务 prd.md/design.md 为准，本文中的导航增强不在此次实现范围内。

调研日期：2026-10-03。当前源码及发布版 `6.2.4`：`0e5ef39d5cc16d6bf2ff4fd8192fad9c52583a77`。原项目基线：`5d807617f8058950f7ea81dda405e38fb0cc37ec`；同时通过 GitHub 读取 Justwen 当前 master `cdad1abba80236602dc98999bee53d802412a7e4` 的收藏点击代码，结论一致。

## 结论与证据强度

| 问题 | 结论 | 证据强度 |
| --- | --- | --- |
| 收藏后能否精确返回楼层 | 普通收藏夹点击没有传递 PID，只打开主题；原项目和当前版本均如此 | 源码确认 |
| 为什么截图似乎成功 | 截图只能证明看到 20、21 楼，不能证明按收藏 PID 定位。进入某页后恰好出现收藏内容是可行解释 | 无操作过程，无法确定当事人路径 |
| 单独的“7” | 当前代码会把收藏列表页码传入帖子页码；越界或缺少总页数时保留该数字并只生成一个页签 | 源码确认；截图具体成因是高匹配推断 |
| 收藏楼层按钮是否需要重建接口 | 原 `tid + pid` 写入重载仍在，6.0.0 只撤掉菜单和处理分支 | 源码及提交确认 |
| PC 收藏预览与冲水文字 | issue 作者明确报告了这两个场景；客户端列表模型确有 `__P.content`，但未核验 PC 响应或冲水前后样本 | 用户报告 + 支持性源码；存储机制未确认 |

本轮仅 GitHub 公共资料及本地源码调研，没有进行 NGA 登录、收藏写入、实时读取、设备操作或 UI 实测。以下代码路径推演不是截图现场复现，也不代表服务端可用性验证。未修改产品代码，未运行 Gradle 测试。

## 1. issue 实际提供了什么

[issue #10](https://github.com/tophtab/nga-just-works/issues/10) 的作者反馈：6.0.0 后无法再收藏楼层；APP 收藏夹只显示主题但点击能跳到楼层；PC 收藏夹可直接显示收藏回复，某些冲水主题的文字仍可看到。

[截图所在回复](https://github.com/tophtab/nga-just-works/issues/10#issuecomment-5966119692) 有以下可观察事实：

- 截图文件名标识包 `com_github_tophtab_ngajustworks` 和 `ArticleListActivity`，支持来自本 fork，而非仅凭界面猜测原版。文件名不证明版本号。
- 顶部是一个占整条宽度的“7”页签；不是一条“已定位收藏”提示。
- 可见内容依次为 20 楼、21 楼；主题标题以“纠结中央空调还是挂…”开头，正文时间为 2024-08-22。
- 没有收藏列表、被点项目、点击过程、目标 PID、完整主题链接、版本号或兼容模式设置。
- 日期为 2024 的内容今天仍可读，并不证明这个主题已经冲水。

## 2. 收藏 PID 在哪里丢失

使用下文简称 `A = nga_phone_base_3.0/src/main/java`。

1. `A/gov/anzong/androidnga/activity/compose/drawer/NavigationDrawerViewModel.kt:128`：收藏入口设置 `favor=1`，没有设置 `searchPost`。
2. `A/sp/phone/mvp/model/TopicListModel.java:193`：请求 `thread.php?...favor=1&page=N&lite=js&noprefix`。
3. `A/sp/phone/mvp/model/convert/TopicConvertFactory.java:186`：`tpcurl` 只被用于提取 `tid`；`pageInfo.page` 保存的是上述列表页 N。`__P` 存在时，另外保存 `pid`、作者、正文、时间等信息。
4. `A/sp/phone/ui/fragment/TopicSearchFragment.java:259`：收藏页继承公共点击逻辑，普通主题项调用 `ArticleNavigation.fromSearchResult`。
5. **`A/sp/phone/mvp/model/thread/ArticlePage.kt:122`：始终复制 `tid`、`page`；仅 `request.searchPost != 0` 时复制 PID。没有收藏分支。**
6. `A/gov/anzong/androidnga/activity/ArticleListActivity.java:35`：`pid=0 && searchPost=0` 打开完整主题 Pager；有 PID 才打开回复阅读页。

因此即使服务端完整返回了收藏楼层 ID，普通收藏夹点击也没有使用它。增加菜单本身不会修复这条读回路径。

源码锚点：[当前导航](https://github.com/tophtab/nga-just-works/blob/0e5ef39d5cc16d6bf2ff4fd8192fad9c52583a77/nga_phone_base_3.0/src/main/java/sp/phone/mvp/model/thread/ArticlePage.kt#L122)、[列表转换](https://github.com/tophtab/nga-just-works/blob/0e5ef39d5cc16d6bf2ff4fd8192fad9c52583a77/nga_phone_base_3.0/src/main/java/sp/phone/mvp/model/convert/TopicConvertFactory.java#L186)、[Justwen 当前点击实现](https://github.com/Justwen/NGA-CLIENT-VER-OPEN-SOURCE/blob/cdad1abba80236602dc98999bee53d802412a7e4/nga_phone_base_3.0/src/main/java/sp/phone/ui/fragment/TopicSearchFragment.java#L263)。

现有 `ReplySearchNavigationTest.ownTopicAndNonReplyNavigationPreserveTheirQueryKinds` 也明确检查“有 PID 的条目在非回复搜索时成为 FULL 查询”，并保留列表页 3。它没有单独覆盖 `favor=1`，后续修复需要新增收藏语义用例，不能通过把收藏伪装成回复搜索来绕过。

**版本差异：**旧基线 `ArticlePagerAdapter` 每次把子页请求改成 `position + 1`，初始只有第一页；因此上游虽然也错误携带列表页码，旧 Pager 通常将其覆盖成 1。当前阅读器为了保留真实起始页会接纳 launch page，于是旧入口的页码混淆变成可见缺陷。不能把当前“单独 7”机制无差别归因到所有旧版。

## 3. 为什么可能只出现一个“7”

当前链路：

```text
收藏列表第 7 页的一项
  → pageInfo.page = 7（列表坐标）
  → ArticleListParam(page=7, pid=0)（错误用于帖子坐标）
  → 请求整帖 page=7
  → 响应若报告总页数小于 7：badBounds=true，totalPages=null
  → Pager(count=1, singlePage=7)
  → 唯一页签标题为“7”
```

- `ArticleShareViewModel.java:36` / `ArticleReaderSession.kt:20` 接纳请求页。
- `ArticlePage.kt:70` 的普通响应分页计算在 `page > total` 时清除 `totalPages`，保留 `effectivePage=page`；没有有效总行数也得到未知总页数。
- `ArticlePagerAdapter.java:64` 在总页数未知时设置 `count=1`、`singlePage=state.currentPage`；`:124` 显示实际 page 值。
- `ArticleTabFragment.java:149` 将唯一页签撑满宽度，与截图形态相符。

**条件推演例子，不是真实响应：**收藏项来自列表第 7 页；主题总计 22 层；服务端若对越界页返回末页的 20、21 楼，则本地计算 `ceil(22/20)=2`、`7>2`，最终正文显示这两层，页签却显示唯一的“7”。若被收藏内容恰好在末页，看起来就像定位成功。

其中“从收藏夹第 7 页进入”及“服务端越界时返回末页”尚无当事人操作/响应证明；其他未知分页元数据也能生成单页签。20/21 楼在普通每页 20 楼模型中属于第 2 页；但不能仅凭这个算式排除兼容接口使用不同分页尺寸。

**最有区分力的补充材料：**APP 版本及兼容模式；被收藏项目在收藏列表的页码；目标楼层号/PID；从该项目点入的连续过程。可请用户比较收藏列表不同页上的普通主题项是否也带入相同页码。不需要让用户重新收藏或删除原有收藏。

## 4. PC 预览和冲水文字：已知与未知

`TopicListBean.PBean` 定义 `tid/pid/authorid/postdate/subject/content`，`TopicConvertFactory` 把正文映射到 `ThreadPageInfo.ReplyInfo.content`。这说明列表模型能携带回复正文，不能反过来证明当前所有收藏响应都提供这些字段。

APP 收藏页在 `TopicSearchFragment.java:130` 选择 `TopicListAdapter`，只有回复搜索使用 `ReplyListAdapter`。`TopicListAdapter.java:63` 只画主题、作者、最后回复者与回复数，所以“收藏列表只有标题”不等于“服务端没有保存目标楼层”。

从作者描述推测，PC 可能读取与实时 `read.php` 不同的收藏副本/历史文本。**未确定**它是收藏时快照还是其他服务端保留内容、更新是否覆盖、字符/附件保存上限、期限、权限变化后可见性，以及旧收藏与新接口的兼容性。本仓库没有 NGA 服务端源码；本轮也没有 PC 收藏页面响应或冲水前后对照，不能把推测写成 API 保证。

还有一个值得后续验证的字段：已有 DTO 样例 `tpcurl` 带 `fav=...`，当前转换器只取 `tid`，不保留其他参数。这个字段的用途尚未核验，不能直接声称它是冲水恢复凭证或盲目加回所有请求。

恢复既有楼层写入后，即使 APP 先不做摘要，仍能重新支持作者在 PC 管理楼层收藏的使用方式；但它不会自动让 APP 的实时 PID 阅读具有冲水恢复能力。

## 5. 其他影响恢复的细节

| 细节 | 当前证据与建议 |
| --- | --- |
| 移除范围 | `6203dad5` 移除两个楼层菜单的收藏项及 `ArticleListFragment` handler；`BookmarkTask.execute(String tid,String pid)` 保留。不要回滚整笔提交，它还包含缓存修复及其他菜单清理 |
| 主题收藏与楼层收藏 | 顶部收藏调用 `execute(int tid)`；楼层收藏需使用被点击行自身的 `tid/pid`，特别是 PID-only 阅读入口，不能依赖 launch tid |
| 不同阅读入口 | 两套菜单分别覆盖普通阅读及 PID 阅读，恢复应同时考虑默认/兼容数据；不能把嵌套贴条的父 PID 冒充独立收藏身份 |
| 同帖多收藏 | `ThreadPageInfo.equals/hashCode` 使用 tid+pid；列表去重使用该身份。不得退化成只按 tid 去重，否则会合并同帖不同收藏 |
| 删除 | `TopicListModel.java:89` 使用列表页和 `tidarray=tid_pid`；`c9ead9eb` 是专门修复收藏回复删除的历史提交。修导航时必须保留列表页用于删除，不能把 `pageInfo.page` 全局改为 1 |
| 回复作者 | 收藏回复作者可以与主题作者不同。PID 收藏导航不应附带主题作者过滤；已有回复搜索修复 `c768a3cb` 说明这个陷阱真实存在 |
| 缺少 PID | 不可从标题、列表位置、正文引用链接猜目标 PID；当无目标身份时，只能明确按主题打开 |
| 普通主题列表也受影响 | `fromSearchResult` 由普通列表/搜索/收藏共用，列表第 N 页与帖子第 N 页的混淆并非收藏专属。修复需覆盖普通列表导航，同时保留独立的外部链接、历史和缓存坐标语义 |
| 历史记录 | `TopicHistoryFragment.java:125` 另有打开入口，也复制 page、忽略 PID；本轮最小恢复不自动等同于“历史也直达收藏楼层”，需另定范围 |
| 合集/镜像 | 公共点击函数在普通文章分支前处理合集/镜像；不可简单把所有收藏条目强制送进 PID 阅读页 |
| 反馈 | 现有添加收藏仅从响应切字符串显示 toast，没有 typed success/read-back；不能因为请求结束就新增持久化“已收藏”状态，失败/未知结果不得自动重试写入 |
| 新收藏接口 | 已有四方 API 调研指出其他客户端 v2 常省略 PID 或写死 0；直接换成新收藏夹接口可能丢掉本次要恢复的语义。不是此任务前置条件 |
| 正文预览 | 不可直接把收藏页切成 `ReplyListAdapter`：它会过滤没有 `replyInfo` 的项，导致普通主题收藏消失。混合列表需要专门处理 |

历史来源：[最初楼层收藏](https://github.com/tophtab/nga-just-works/commit/b3e78d91b67cb883e19133c32bc5213dbefed8b4)、[楼层收藏删除修复](https://github.com/tophtab/nga-just-works/commit/c9ead9ebe1266b09305c510959f715a7a61b0fe4)、[6.0.0 前菜单清理](https://github.com/tophtab/nga-just-works/commit/6203dad5a9d02891cc7a554f11a18fc4ed9251d8)。

## 6. 建议恢复方案与尚待选择的交互

**用户后续澄清（2026-10-03）：当前优先确定原项目行为，以理解 issue 的“恢复原状”诉求；不应先要求用户选择新的点击交互。下文 PID 导航和完整主题定位均属于增强方案，不是已经证实的原功能。**

推荐最小范围：恢复两处楼层菜单的收藏操作；沿用现有服务端 tid+pid 收藏；纠正列表页码泄漏；收藏楼层项进入已有 PID 回复阅读页，保留“显示全部”打开完整主题。普通主题收藏从第一页打开。

该方案能先让“收藏的就是这一层，打开的也是这一层”成立；不会承诺首次点击已进入完整主题的原位置。现有 `ArticleListFragment.java:536` 会按实际 PID 设置定位 anchor；回复不存在时应保留错误/缺失反馈，不能把打开主题当成成功定位。

另一方案是首次点击直接打开完整主题并滚动到对应楼层。它需要先解析可靠的全局位置，再请求对应整帖页并匹配 PID；查询窗口内的 floor 不能直接等同全局 floor。`fae59cbc` 最近已撤回“显示全部自动定位”，因为 lookup 可能显示 floor 0 而不具备全局位置证据。此方案应单独设计和验证，不能仅将 PID 填入现有 FULL 查询或恢复旧 floor/20 算式。

建议暂缓：APP 收藏正文预览、冲水快照 UI、收藏夹 v2/分组、历史定位扩展。保留它们作为后续候选，不与按钮恢复捆绑。

## 7. 补充核验：原项目的“原状”是什么

本次追加对照四个节点：2020-07-12 首次增加收藏楼层的 `b3e78d91`、本 fork 的 Justwen 固定基线 `5d807617`、本 fork 移除前发布版 `5.6.1`，以及 GitHub 上 Justwen 当前 master `cdad1abb`。

- 首次增加功能的提交标题原文就是 **“增加收藏楼层功能，但暂不支持显示收藏的楼层”**。实际补丁仅增加 tid+pid 写入重载、两套菜单和 handler，没有修改收藏列表导航。这条提交说明只能辅助理解，不能单凭“显示”一词推断点击行为。
- 四个节点的收藏点击均没有独立的 PID 导航：只有 `searchPost != 0` 才把目标 PID 放入文章参数。收藏页继承公共点击处理，自己只补充长按删除，不覆盖点击。
- 原项目 `ArticleListActivity` 以 `searchPost` 区分完整主题页和回复搜索页；普通收藏点击进入 `ArticleTabFragment`。旧 Pager 初始子页将 page 写成 `position+1`，所以走普通新开页面路径时从第 1 页开始，而非按收藏 PID 定位。
- 原项目确有搜索回复的独立阅读路径及“显示全部”，但普通收藏夹点击没有接入；不能以另一个入口的能力替代收藏功能本身的证据。原项目的外部 PID 链接路由也不能直接套用当前 fork 的 `pid != 0 → ArticleSearchFragment` 规则。

原状可准确表述为：**楼层菜单能把指定楼层保存进服务端收藏；APP 收藏列表仍显示主题；点击普通收藏项打开完整主题，不附带该收藏楼层的精准定位。** 原始 issue 首先要求恢复按钮，这一诉求可通过恢复菜单和写入 handler 满足；是否同时修页码或增强导航应另列，不将其说成恢复旧跳转。

因此，前一轮把“打开回复”与“完整主题定位”作为二选一提前了：这两者都是候选改善。作者声称旧行为能跳转，与已核验的收藏点击代码仍存在差异；应继续保留未知，不能据此断言他记错，也不能把截图当成原项目实现证明。
