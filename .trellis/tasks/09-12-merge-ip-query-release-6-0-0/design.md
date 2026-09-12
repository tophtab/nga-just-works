# Integration design

## Boundary and ownership

Perform a real merge of `experiment/auto-ip-query` into main, restore the
existing `ArticleListFragment` service calls, and retain main's newer AI code.
The merge imports the Web parser/transport, queue behavior, metadata strings,
and existing regressions. The implement worker owns the remaining replay fix
in `AuthorLocationService.Page`, its integration contract test, and validation
report. The coordinator owns specs, task artifacts, index/merge handling,
commits, finish-work, and main push. The checker reviews the complete scope.

## Main worktree and parallel changes

Initial preparation and validation used an isolated worktree. The maintainer
then explicitly requested edits in `/home/toph/nga-just-works` on main, then at
`8a6b3249`. The pending merge was transferred there, retaining the latest main
commit and real source-branch merge parent. Three overlapping parallel files
were backed up and merged in memory first. The Git index contains only the IP
integration; unstaged AI and article-page-flicker edits remain in the working
files and must not be swept into this task's commit.

Before the IP commit, the parallel page-flicker session separately committed
its code as `16cc185b` and completed its archive/journal commits. Retain this
new main ancestry. The final product snapshot includes the already reviewed
reader changes; only independent AI work is still unstaged. Main's later
Trellis bookkeeping does not require another product test run.

All further product edits occur on main. Verify the committed scope separately
from unrelated working changes when staging shared files. No PR is part of this
direct-main task; archive may use `--skip-branch-validation` for its deliberately
identical branch/base metadata. The old isolated worktree is only a temporary
validation/backup location and must not supply older main content wholesale.

## Replay correction

The presenter sends both real responses and READY replay through `setData`.
Make repeated nonnull response delivery idempotent at the actual subscription
owner, `AuthorLocationService.Page`. Preserve its existing consumer and online
intent and re-emit the latest current-generation LiveData snapshot without
repository calls. Null clears identity/consumer; close releases the response;
session invalidation remains authoritative. This preserves cached metadata
even if the old renderer or the parallel owner-metadata update rebinds a body.

Do not add an early Fragment return that bypasses the parallel task's topic-owner
updates, or refactor its body/title/menu/anchor behavior. A retained view creates
an offline Page subscription; READY replay cannot promote it to online. Fresh
response instances still replace consumers; view-destroyed responses remain
retained for later cache-only rendering.

## Compatibility and handoff

Preserve the Web-profile route, data-only parser, immutable sessions, cache and
error policies, one physical request in flight, 500 ms completion spacing, and
body-independent HTTP 503 stop. Keep main's committed version/string/AI changes.
Use offline compilation, JVM tests, and lint. No local APK, ADB, or live NGA.

Stable release publication remains deferred: no new notes, versions, or tags.
Commit the checked integration on main, archive this task and record the journal,
then push main only. Preserve all unrelated unstaged edits and other tasks.
Never force push; stop after successful push without polling CI.
