# Thread IP Location and Loading Usage Tips

## Goal

Replace low-value author detail with useful public location information and
teach existing interactions during loading, preserving responsive reading and
reusing location data across the existing page-loading/prefetch mechanism.

## Background

Task creation/planning was approved on 2026-09-11. Userscript investigation found
one-hour UID caches without global pacing, in-flight reuse, or location-path
challenge backoff; the IP child's research records the exact source evidence.

The user clarified that author-location retrieval must accompany the existing
page prefetch mechanism, not a separately defined three-page batch, and asked
for no fixed inter-request interval. About sixty authors is only an estimate
under today's current-plus-two prefetch policy.

The implementation baseline is `main` at `5bb92cf0`, which includes the existing
cache fix `6203dad5`. Implementation uses the isolated
`feature/thread-ip-location-loading-tips` branch/worktree. The AI configuration example exists
in the separate `feature/ai-summary` worktree; guidance must follow actual feature
availability in the shipped build.

## Requirements and Child Ownership

| ID | Outcome | Child task |
| --- | --- | --- |
| R1 | Replace floor level/reputation with public location, retain post count, and enrich each normal/prefetched page through shared cache/request reuse without fixed pacing | `../09-11-thread-author-ip-location/` |
| R2 | Show accurate local Chinese usage tips during existing initial loading, including AI configuration when available | `../09-11-loading-usage-tips/` |
| R3 | Preserve reading, existing prefetch decisions, refresh gestures, offline caches, and account isolation | Parent integration review |

The children are independently verifiable. Implement sequentially because both
may touch `ArticleListFragment.java`; the parent owns final integration.

## Acceptance Criteria

- [x] AC1/R1: Floors omit level/reputation and display known public location with
  post count, or post count alone when location is unavailable.
- [x] AC2/R1: Each online normal/prefetched page enriches its eligible authors
  without waiting for other pages. Cache/in-flight reuse spans deliveries; there
  is no fixed page-count rule or normal-request interval.
- [x] AC3/R2: Foreground initial loading shows one verified tip, varies on later
  occasions, and removes it through the normal success/error transition.
- [x] AC4/R2: AI guidance is eligible only when the build has its real destination.
- [x] AC5/R3: Location requests do not delay content or change thread prefetch
  selection/final-page behavior, gestures, page-cache format, or account scope.
- [x] AC6/R3: Both children meet acceptance and the offline Android quality gate;
  validation clearly distinguishes source evidence from live/device checks.

## Out of Scope

A new prefetch planner, fixed three-page batches, normal-request pacing, new AI
capabilities or AI-branch merging, raw-IP geolocation/history, profile redesign,
new gestures, remote tips, quotations, operation dialogs, Compose private-message
loading, broad architecture changes, and release publication.

## Planning Artifacts

Both children have research, PRDs, designs, execution plans, and context manifests;
the parent has an integration design/checklist. The corrected mechanism-driven
plan was approved for implementation on 2026-09-12: “确认吧。开干吧。”
Implementation uses the isolated `feature/thread-ip-location-loading-tips` worktree.

## Completion Evidence — 2026-09-12

Both children passed the combined review recorded in
`research/combined-check-report.md`; final commands and diagnostic limits are in
`research/final-validation.md`. Debug assembly and 223 app JVM tests passed,
and all 13 Android lint reports contain zero Error/Fatal issues. The repository
debug-unit diagnostic reproduced two documented, unrelated example-test build
failures. No live NGA or device operation was run. Source/lifecycle reasoning
and offline tests establish the reviewed behavior without claiming device UI or
current server availability verification.
