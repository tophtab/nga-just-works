# 发布 NGA Android 5.5.2

## Goal

先整理 5.5.2 发布说明；待深色模式修复任务提交后，再由本任务完成发布提交、推送和稳定版标签。

## Requirements

- 新增 `release-notes/5.5.2.md`，记录 `5.5.1` 之后已完成及即将合入的深色模式修复。
- 本阶段不修改业务代码，不执行发布契约测试、Android 构建、签名校验或 Action 轮询。
- 等相关深色模式修复提交完成后，再将发布说明与该提交一起推送，并创建精确标签 `5.5.2`。

## Acceptance Criteria

- [ ] `release-notes/5.5.2.md` 包含稳定版要求的“新增 / 删除 / 修复”章节和变更链接。
- [ ] changelog 覆盖 Compose 页面、编辑页面及个人资料页的深色模式可读性修复。
- [ ] 在相关修复提交完成前不执行 push 或创建版本标签。

## Notes

- Keep `prd.md` focused on requirements, constraints, and acceptance criteria.
- Lightweight tasks can remain PRD-only.
- For complex tasks, add `design.md` for technical design and `implement.md` for execution planning before `task.py start`.
