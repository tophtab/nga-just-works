# Justwen 最新提交与兼容模式吸收情况复核

核查日期：2026-09-30。用户在搁置 R7 后单独要求检查上游最新提交及兼容模式采用情况。
本次仅刷新上游 Git 引用、阅读源码和保存核查结果；R7 的修复与移除功能实施继续搁置，任务状态未变。

## 当前上游版本

已成功执行 `git fetch --no-tags upstream-justwen`，并以
`git ls-remote --symref upstream-justwen HEAD 'refs/heads/*'` 核对公开分支：

| 分支 | 最新提交 | 提交日期（+08:00） | 说明 |
| --- | --- | --- | --- |
| master（默认分支） | cdad1abba80236602dc98999bee53d802412a7e4 | 2026-08-07 | 补充遗漏的版本更新提交 |
| master_release | 22ba3082501bcbb08f52a66d787f970f59c2dda7 | 2026-08-30 | 5.0.2发版，顺便升级下target api |

截至本次刷新，公开分支没有 8 月 30 日之后的新提交。较新的实际开发代码在
master_release，不能只看默认 master 得出兼容模式不存在的结论。

- [2becba2a：尝试解决跳转浏览器的问题](https://github.com/Justwen/NGA-CLIENT-VER-OPEN-SOURCE/commit/2becba2acc3f6c85340424cd09bb03fa7d759db0)，2026-08-30：加入 App 备用读取链。
- [22ba3082：5.0.2 发版](https://github.com/Justwen/NGA-CLIENT-VER-OPEN-SOURCE/commit/22ba3082501bcbb08f52a66d787f970f59c2dda7)，2026-08-30：其相对上一提交的全部差异仅在 build.gradle，涉及版本与 SDK；没有后续 reader 修复。
- [750871b3：解决一些 bug](https://github.com/Justwen/NGA-CLIENT-VER-OPEN-SOURCE/commit/750871b31b533dd74059ade146a084f396020cbe)，作者日期 8 月 26 日、提交日期 8 月 29 日：视频样式/标签、缓存缺参保护等，已经纳入此前采用台账。
- [93acf42a：解决游戏综合讨论区打不开的问题](https://github.com/Justwen/NGA-CLIENT-VER-OPEN-SOURCE/commit/93acf42a5b6601f6ee6e257bb31895a3b50ad458)：在 TopicConvertFactory 删除特定 jdata 字段以兼容板块列表解析；不是帖子普通响应截断的修复。

## 上游究竟如何处理读帖失败

以下 U 行号均固定到 master_release 的 22ba3082：

1. `U:nga_phone_base_3.0/src/main/java/sp/phone/mvp/model/ArticleListModel.java:59` 仍请求
   `read.php?&page=…&__output=8&noprefix&v2`。
2. 普通解析返回空且未识别到业务错误时，抛 ServerException（同文件 134–140）。
3. Presenter 先保留已有的其他账号 Cookie 重试；失败后按开关尝试 App API，或跳内置浏览器
   （`U:nga_phone_base_3.0/src/main/java/sp/phone/mvp/presenter/ArticleListPresenter.java:60`、`:91`、`:130`）。
4. App API 是 POST `app_api.php?__lib=post&__act=list`，发送 page/tid/pid/authorid；
   成功后转换成原生 reader 数据，该页 Presenter 后续继续使用 App API；失败仍按设置跳浏览器（同 Presenter 101–119、146–161）。
5. 实验室仍保留两项开关：兼容模式默认关闭，自动内置浏览器默认开启
   （`U:nga_phone_base_3.0/src/main/res/xml/settings_lab.xml:4`、`:32`）。

上游普通 parser 仍直接 JSON.parseObject；预处理是已有 wrapper/非标准字段修正
（`U:lib_core/src/main/java/com/client/androidnga/core/parse/ThreadInfoParse.kt:161`）。
没有发现恢复缺失响应内容的实现，也没有一套移除兼容和浏览器之后保证普通读取成功的方案。

本次已捕获的 47440211 样例是普通响应在 JSON 字段名中间截断，稍后恢复完整；该样例不含
error-fill 标记。缺失响应内容不能靠换 JSON 库或上述预处理恢复。截断来源仍未确定，另一个
账号相关帖未采集响应，不能把两例归为同一根因。见 [原始取证结论](47440211-truncated-response.md)。

## 本 fork 是否漏掉兼容模式

本 fork 的功能提交为 `7acc4e23c6ddc07ba4d03a1c3a9ac7f9a3a41adf`（2026-09-12），
已在当前 HEAD `1a8413d9` 的祖先链中；并非仅写了调研或尚未合并的分支。
当前 AppArticleParser 的来源注释也明确指向上游 2becba2a。

已吸收核心已实现能力：App 请求与参数、正文/作者/楼层转换、完整帖、PID、只看某人、
UID 楼主标记及 HTML 回复引用头。为接入本 fork，还补齐了以下部分：

| 衔接点 | 最新上游现状 | 本 fork 已有处理 |
| --- | --- | --- |
| 分页/定位 | App parser 不消费 currentPage/perPage/totalPage；ArticleTabFragment 仍固定每页 20 楼 | AppArticleParser 使用服务端分页信息，并区分未知窗口和 PID 查询；定位按实际 PID/楼层 |
| 缓存 | 存 App 原始响应后仍走普通 ThreadInfoParse 读缓存 | ArticleCacheReplay 按数据来源选择 parser，缓存区分账号、来源和页大小 |
| 图片前缀 | attachPrefix 按斜杠拆取首段，完整 https URL 会被错误截取 | NgaImageHost 保留完整前缀和手动图床偏好 |
| 请求身份/生命周期 | 另账号普通重试与 App 回退不统一绑定同一请求身份 | 兼容链绑定账号及阅读代次，仅允许前台切换，拒绝过期结果 |
| 空结果/缺内容 | App parser 直接取 result[0]，部分异常与关闭开关组合处理不完整 | 明确空结果、身份和内容校验，保留可读部分的完整性标记和错误收尾 |

这属于复用并适配上游的兼容核心，不是整个上游仓库逐行合并，也不能保证所有账号、帖子和
时段都可读。`attches/hot_post/comment_to_id/html_head_extra` 等扩展在上游只是声明、没有完整
解析展示实现；本 fork 保留原始响应，不存在漏搬一套上游已完成的此类展示功能。

## 其他尚未全量采用的改动

此前 8 月采用台账中的板块动态图标与刷新、视频 CSS/额外无类型 flash 标签、全局
fastjson2/数据层迁移、SDK 36 和旧工具清理，未因本次核查变为已完成。当前仍可核实
BoardEntity/板块链未接入上游 iconUrl/forum_icon_pre、公共 CSS 无 video 规则、解码器保留
typed video 规则、compile/target SDK 为 35。版本目录中有 fastjson2 条目，不代表全局已迁移。

这些是原先就知道的分批采用差异；没有发现一项后来新增、遗漏同步、能直接解决本次普通
读帖失败的上游补丁。完整旧台账见
[adoption-map.md](../../09-11-upstream-august-2026-review/research/adoption-map.md)。

本次验证限于 Git 引用、提交差异和源码路径核对，未运行上游 APK或新增 NGA 请求。
仅保存这份报告，没有改产品代码、合并上游或恢复 R7 实施。
