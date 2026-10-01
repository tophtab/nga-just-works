> 历史方案：原 R7 已拆为独立调研成果归档；本文不是执行清单。普通读取修复及兼容/浏览器移除仍搁置。当前范围以 prd.md 和 delivery.md 为准。

# R7 plan — ordinary repair first, then remove both fallback features

> 2026-09-30 用户最新决定：R7 全部搁置，停止研究、诊断及实施，待用户另行恢复。保留“先修复正常读取，再移除兼容模式和帖子内置浏览器”的目标与已有取证；未修改 R7 产品代码。

Status: planning. No product implementation activated. The latest user
instruction rejects hidden compatibility and internal-browser recovery.

## Completed evidence

- [x] Trace ordinary, compatibility and internal-browser paths, including settings.
- [x] Record earlier four-sample comparisons, host replay and user switch evidence.
- [x] Reproduce 47440211 incomplete ordinary JSON, locate initial JSON failure,
      verify exact-prefix relationship to later complete ordinary response,
      and preserve synthetic truncation/complete controls.
- [x] Keep the different-post A-B-A account observation separate; both posts
      recovered, and existing local Cookie mapping to account A is confirmed.
- [x] Record the user's ordinary-only repair and complete feature-removal goal;
      archive superseded automatic-App/phone-diagnostic proposals.

## Research and design before implementation

- [ ] Validate ordinary transport framing/decompression behavior offline and
      narrow the original incomplete-body cause without inventing remote facts.
- [ ] Define and demonstrate a normal-path correction that addresses observed
      failures; do not substitute App success, immediate retries or synthetic
      missing-content reconstruction for actual ordinary reading.
- [ ] Trace all live App/browser entry points and old saved-page formats;
      finish the ordinary-path unification and non-destructive cache migration design.
- [ ] Reconcile current R3/R4 edits and update the final design/acceptance plan.
- [ ] Present the concrete final implementation summary for review, then
      activate the actual R7 task only after that reviewed scope is authorized.

## Implementation order after design review

1. Dispatch Trellis implement with the current active task context and exclusive
   ownership of reader request/parser/state changes and required tests. State
   that other work exists and must be preserved. Do not execute the superseded
   R7-A implementation checklist.
2. Implement and test the evidenced ordinary-path correction, error states,
   selected-account request ownership and any explicitly justified bounded
   recovery. Verify complete ordinary data reaches the existing native reader.
3. Remove online App requests, source adoption, compatibility settings/notices
   and obsolete branches. Migrate historical saved App pages through the
   reviewed offline-only mechanism without data deletion.
4. Remove both automatic and menu-based internal forum-browser entry points,
   settings and unused dedicated code. Preserve login and local body rendering.
5. Recheck pagination, refresh, reply anchors, lifecycle/account changes,
   background work, saved pages and retained error/retry behavior. Verify
   stale preference values cannot reenable either removed feature.
6. Run focused tests and all required offline Android build/unit/lint checks;
   inspect lint reports for zero Error/Fatal. Dispatch Trellis check for the
   complete final diff, including unintended leftover online fallback paths.
7. Update affected specs and report ordinary-reading evidence separately from
   unreproduced account-specific cases and unverified device success rates.

Completion requires ordinary-reader improvement plus both removals, not only
settings removal or fewer browser navigations. Do not release a destructive
intermediate state as the completed repair. Preserve rollback points for the
normal-path refactor and saved-data migration; do not overwrite others' edits.
