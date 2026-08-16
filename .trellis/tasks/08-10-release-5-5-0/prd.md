# 发布 5.5.0 版本

## Goal

在待完成的 bug 修复合入后，将届时的 `main` 作为发布边界，编写准确的中文
changelog，并发布签名的稳定版 `5.5.0`。

## Background

- 用户已明确要求暂停发布，等待另一项 bug 修复完成。
- 只有用户后续明确通知该修复已经完成，才重新检查发布范围并继续规划。
- 暂停期间不得创建或推送 `5.5.0` 标签，不得创建 GitHub Release。

## Requirements

- 发布说明必须覆盖最新稳定版之后、截至最终发布提交的全部用户可见净变化，
  包括尚未完成的 bug 修复。
- bug 修复完成前，不锁定最终 changelog 内容或发布提交 SHA。
- 保留当前工作区中与发布无关的未提交修改，不将其混入发布提交或标签。

## Acceptance Criteria

- [ ] 用户已明确通知待完成的 bug 修复可以纳入发布。
- [ ] 重新核对最终 `main`、远端标签、GitHub Release 和提交范围。
- [ ] `5.5.0` changelog 准确覆盖最终发布范围并通过仓库校验。
- [ ] 仅在最终计划再次获得用户批准后启动实现与发布。

## Open Questions

- 等待用户通知 bug 修复完成；届时重新收敛最终发布范围。

## Notes

- Keep `prd.md` focused on requirements, constraints, and acceptance criteria.
- Lightweight tasks can remain PRD-only.
- For complex tasks, add `design.md` for technical design and `implement.md` for execution planning before `task.py start`.
