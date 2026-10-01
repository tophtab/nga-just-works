# 当前实施接续（2026-10-01）

实施工作区 `/home/toph/nga-just-works-upstream-adoption`，分支 `feat/upstream-adoption`，基于主仓库 `557f7bea`。
规划资料已保存为 `345db9cb`，U1 产品/规范提交为 `4ccd7564`：35项聚焦测试、740项全模块测试、13模块lint 0 Error/Fatal、Debug构建与独立check均通过。
U2 已完成，提交 `88ce8be3`（749测试/13模块lint及72布局场景通过）。U3 已start，B0已提交 `05c2602e`，B1已提交 `1b4f8ade`，B2已提交 `4a4296c2`，B3已提交 `758aa6b9`，当前B4实施中（实际app R8因签名guard待U4）。U1任务目录暂保留以供U3联合验收引用，父任务收尾时统一归档。
当前实施详情/工具链预取位置见 [implementation-session.md](implementation-session.md)。下文为原始批准交接历史，旧“尚未实施/工作区不存在”描述不再代表当前状态。用户整体授权不变。

---

# 接续：U1–U4 已整体批准，留待新会话实施

2026-09-30，用户在阅读四项具体方案及SDK36对Android15的影响后回复：“好吧，那确认吧。我以后会开个新的会话窗口去实施。”批准记录见 [approval.md](approval.md)。既定四项及U3全部B0–B5已经获批，不重复请求整体或逐项批准。

## 先读这些文件

当前完整资料在主仓库 `/home/toph/nga-just-works/.trellis/tasks/` 的原父任务及四个子任务内。先读approval.md、review.md、父prd/design/implement和research/source-index.md，再读当前子任务的全部设计/实施计划。没有新建任务。

原 `/home/toph/nga-just-works-upstream-adoption` 工作区与备份被用户清理。完整规划已从本次会话工具记录恢复；恢复说明见 [recovery.md](recovery.md)。旧材料中的该绝对路径、feat/upstream-adoption分支与1a8413d9是规划时环境，不能当作仍存在的实施工作区或最新代码基线。

## 新会话开始实施

1. 检查主仓库当前状态和Git提交，保留其他会话改动。交接时主仓库为main@c44b2f4591dc4b232d86e56aa85fb022199d3206；新会话仍须读取实际HEAD。原R1–R6已存在正式交付，应核对当前合入结果，按现行源码/规范补齐消费者与回归，不照搬规划时行号或覆盖文件。
2. 从确认后的最新已提交基线建立独立工作区，可复用原路径和分支名，但先检查是否被占用。五个任务目录目前仍未提交，创建工作区后必须完整复制父任务、四个子任务以及未提交的R7引用归档，逐文件校验；不能只依赖git worktree复制已提交文件。
3. 在新工作区更新五任务的branch/worktree_path和实际实施基线；保留现有父子关系、implementation_approved=true及本批准记录。
4. 从现有U1开始task.py start，按Phase 2与Trellis implement/check执行；顺序U1→U2→U3（B0–B5）→U4→父集成验收。既定方案内无需重复审批；实质范围/产品行为变化才带具体差异返回。

## 必须保持

- 用户主力手机为Android15/API35，以其现有体验不退化为主要兼容性目标；同时完成Android16必要适配，minSdk29保持。共享返回、发帖面板、草稿、抽屉/WebView、正文媒体、布局与排序纳入回归。
- 本轮只保存批准和恢复交接，没有start、产品实施、构建、产品单测/lint/R8、设备、真实NGA、签名打包、提交或发布。设备等边界沿用既有计划；离线证据不冒充实机验证。
- R7仅保留归档调研，产品修复、兼容/内置浏览器移除继续搁置。
- 大于32KiB的规范分段读全；各context有真实规范、研究和approval，不能把截断注入当全文。
- U3完整消费者/存储和双库probe、U4全部平台/工具链/native证据均已恢复。旧工具链/源码/原生产物证据带原基线，实施时检查本次实际结果。
- 新SDK/toolchain在实施准备时预取；不关闭签名guard。实际app R8验收未完成时，U3/U4和父任务不能提前关闭。

五任务暂保留planning，metadata为approved_waiting_for_implementation_session；这表示方案已批、实施未开始。

## 2026-10-01 范围确认

用户曾提出“回帖成功后按页面缓存刷新且保持浏览位置”，随后明确回复“算了，去掉这个需求吧”。该补充需求已撤回，不进入U1–U4，不增加任务或实施批次；原整体批准及新会话实施安排保持有效。仅做了初步只读检索，没有产品实现。
