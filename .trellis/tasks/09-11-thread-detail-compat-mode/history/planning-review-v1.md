# Planning review — 2026-09-11

> 已过期：用户已扩大复用方向，旧最终摘要及本次检查结论不能授权实施。当前状态为 revision_required；完成新版设计、清单和范围核对后重写本记录。

## Result

Ready for the final planning review. Task creation and selective reuse/adaptation were approved earlier; implementation approval for the final scope is still pending. Status remains `planning`.

The PRD convergence pass is complete: R1–R7 and AC1–AC6 retain the seeded requirement/acceptance meanings, R8/AC7–AC8 add transport and expanded cache checks, temporary planning questions/placeholders have been removed, and evidence anchors remain in the owning sections/research.

## Concrete final summary prepared for review

- Reuse upstream App API fields/parser logic in the current native reader; add the experimental preference default off.
- On a supported full-thread foreground format failure, try App once with the same account. Preserve ordinary prefetch and the repaired image/cache/menu behavior.
- Validate 20-floor page/identity consistency; unsupported PID/author queries and unproven content structures retain explicit error/browser outcomes rather than partial success.
- Keep old caches readable; use explicit source formats and owner scopes for attributable new data. New-format cache zip import/export is deferred; the UI must state the legacy export scope.
- Acceptance uses synthetic/fake-transport JVM tests, debug build and zero Error/Fatal in every Android lint report. Real endpoint coverage is unknown because no reproducible real upstream fixture exists.
- This is selective feature adaptation, not a whole upstream merge or model/library migration.

## Checks performed

- `python3 .trellis/scripts/task.py validate .trellis/tasks/09-11-thread-detail-compat-mode`: passed; 16 real entries in each manifest.
- Two source specs exceed the automatic per-file injection cap: Android quality and UI component guidelines. Both manifest reasons and implement.md explicitly require implement/check agents to read the original files with tools before working. These warnings are not treated as full context having been injected.
- Artifact checks: prd.md/design.md/implement.md exist and contain no TBD/planning placeholders; code fences are balanced; local links resolve; all R1–R8/AC1–AC8 entries exist; manifests reference unique existing files.
- `git diff --check`: passed for tracked changes. All work in this planning continuation is under this task directory; no product diff was created.
- HEAD remained `5bb92cf033aa32d749d10e1a497bc05173cd2955` on `fix/thread-menu-cache`. Metadata records this implementation baseline separately from the task's default PR target field.

No build, product test, NGA request, account read, device operation, branch change, task activation, commit, push or merge was performed in this planning continuation.

## Concurrent workspace note

The latest status also showed untracked task directories `09-11-loading-usage-tips`, `09-11-thread-author-ip-location` and `09-11-thread-ip-location-loading-tips`, besides this task and the earlier August research. They are outside this task and were not edited. Recheck the workspace before starting implementation; potential reader-file overlap must not lead to overwriting another task's edits.

## Required next step

Present the final scope/limits and ask for one approval of that concrete plan, as required by `.agents/skills/trellis-brainstorm/SKILL.md:60–61`. After a subsequent explicit approval, activate this task, establish the recorded implementation baseline, and dispatch implementation. Do not repeat task-creation consent or redo the August audit.
