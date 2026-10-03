# Restore the original floor favorite action

## Goal and approval

Restore the original floor-level “收藏” action removed before 6.0.0, so users can save the selected post to their existing server-side favorites.

The user first requested issue #10 research, then clarified that the reference is the original implementation. After the original behavior and minimal restoration were summarized, the user explicitly approved that baseline: “先按照原有的楼层收藏按钮回复。相当于回退这个楼层收藏的功能”. Here “回复” is understood in context as “恢复”; this is implementation authorization for the previously described restoration, not an instruction to post a GitHub reply.

## Evidence

- The original action was added in `b3e78d91`; `6203dad5` removed the two menu entries and handler while retaining `BookmarkTask.execute(String tid, String pid)`.
- Four checked historical/current nodes do not connect ordinary favorite-list clicks to precise PID navigation. Restoring that navigation is not part of restoring the button.
- Detailed issue, screenshot, PC-preview, deletion, and page-coordinate research remains in [findings.md](research/findings.md). Earlier enhancement recommendations in that historical report are not implementation requirements.

## Requirements

- R1: Restore `menu_favorite`, visibly named “收藏”, in both original floor overflow menus and at its previous relative position.
- R2: Dispatch the selected row's own `tid` and `pid` through the unchanged existing `BookmarkTask.execute(tidStr, pidStr)` overload.
- R3: Preserve the existing favorite request/result behavior, whole-thread favorite action, current floor-menu actions and reader behavior.
- R4: Synchronize the active frontend floor-menu spec to describe the restored action.

## Acceptance criteria

- [x] AC1 (R1): The ordinary floor menu contains “收藏” after poll/vote and before author-only viewing; the PID-context menu contains it after blacklist and before poll/vote.
- [x] AC2 (R2): Both menus reach the same handler, using IDs derived from the clicked row, including a PID-only launch whose initial topic ID may be absent.
- [x] AC3 (R3): No favorite-list navigation/page-coordinate, preview, persistence, removal, endpoint, request/response parsing, or unrelated menu behavior changes appear in the diff.
- [x] AC4 (R4): The active spec permits floor favorites, retains the support/oppose/signature menu exclusions, and distinguishes the whole-thread action.
- [x] AC5: Required local compilation, Debug unit tests and lint pass; no live NGA mutation or device operation is used for validation.

## Scope exclusions and evidence limits

Do not implement either suggested navigation enhancement, fix the independently discovered page “7” defect, add favorite previews or flushed-post recovery, migrate to v2/folders, alter history behavior, or revert the whole removal commit. Do not create a new local favorite-state model or change success feedback. No GitHub issue reply or device installation is requested. The user subsequently confirmed the commit/archive plan and explicitly requested a patch release: “确认。完成后发布修复版本”. Stable 6.2.5 notes, local release-identity validation, fast-forward integration into main, and pushing main plus the immutable stable tag are now authorized.

The screenshot's actual request/response and PC snapshot retention remain unverified; these do not block the approved button restoration. No product decision remains open for this implementation.

## Authorized release handoff

Publish stable 6.2.5 through the existing tag-driven GitHub workflow. Preserve signing and CI behavior; no local Release/Preview packaging. Follow the project instruction to stop after successful main/tag push without polling CI. Release notes describe only the restored floor favorite action.
