# Merge IP queries into main

## Goal

Integrate `experiment/auto-ip-query` into `main`, complete checks and commits,
finish the Trellis task, and push main. The maintainer explicitly deferred the
new stable release in a follow-up message: “release先不发布新版本。其他的先干了”.

## Background

- Main and origin/main began at `70ff31df`; the IP branch and its remote are
  `f5bcec31`. Main disabled automatic subscriptions after the branch split.
- Main has newer committed AI collection fixes. Unrelated uncommitted edits
  exist in `AiSummaryClient.java`, `AiModelsClientTest.java`, and
  `.trellis/spec/backend/ai-summary-contract.md`, alongside the separate
  `09-12-ai-profile-format-recurrence` task. A parallel article-page-flicker
  task now also has unstaged changes in the Fragment and two shared specs.
- The existing main-push workflow can publish its normal Debug preview. A new
  stable release requires a version tag, which is excluded from this task.

The maintainer subsequently switched the shared worktree back to main and
explicitly requested continuing edits there. Main advanced to `8a6b3249` with a
compatibility-summary wording change, which must also be preserved.

During final checks the parallel page-flicker session committed its work as
`16cc185b`, then archived its task and recorded its journal. The IP merge must
retain those commits as main ancestry. Its final product validation therefore
includes the now-committed reader fix; the AI WIP remains uncommitted.

## Requirements

1. Preserve both branches' history with a merge commit and restore automatic
   author-location integration on main.
2. Retain the IP branch's Web-profile parser, serialized 500 ms pacing,
   cache/account/lifecycle isolation, metadata ordering, and HTTP 503 stop
   policy, together with main's committed AI improvements. Replaying an already
   delivered page on resume must preserve its consumer and cached metadata
   without creating new network work.
3. Keep unrelated working-tree changes intact and outside this task's commits.
4. Run applicable offline JVM, compilation, and lint checks; synchronize the
   active IP feature contracts.
5. Commit, archive this task, record the journal, and push main. Follow the
   project handoff policy: stop after push without CI polling.

## Acceptance criteria

- Main includes both `f5bcec31` and `70ff31df` in its ancestry.
- Fresh online page delivery subscribes to author locations; same-view and
  recreated-view READY replays do not recreate an online subscription. IP
  regressions and committed main AI tests pass.
- Affected sources compile and all required Android lint XML reports show
  zero Error/Fatal issues.
- This task and its journal are archived/committed before main is pushed.
- The main push succeeds; no new stable tag or release is created.
- The parallel uncommitted AI edits and task remain available.

## Out of scope

Publishing 6.0.0, release notes/version changes, new lookup behavior, AI
redesign, CI policy changes, local APK packaging, device/ADB actions, live NGA
traffic, or modifying existing stable releases.
