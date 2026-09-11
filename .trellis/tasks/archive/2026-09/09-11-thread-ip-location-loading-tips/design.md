# Design: Author Location and Usage Tip Integration

Status: approved for implementation on 2026-09-12 (user: “确认吧。开干吧。”).

## Branch and worktree

Implementation was approved on 2026-09-12. Branch
`feature/thread-ip-location-loading-tips` was created from `main` at `5bb92cf0`
in `/home/toph/nga-just-works-thread-ip-location-loading-tips`; this baseline
already contains cache fix `6203dad5`. Product edits and checks run in that
isolated worktree. The separate AI and thread-compatibility worktrees remain
outside this task. Implementation and checks precede any integration into `main`.

## Deliverable boundaries

The location child owns public author metadata, supplemental USER.PROFILE reads,
shared persistent cache/request reuse, and per-delivered-page enrichment.
The loading-tip child owns a local catalog and shared legacy loading widget.
Detailed contracts live in each child design.

| Decision | Approved contract |
| --- | --- |
| Author detail | Remove level/reputation; retain post count and show known public location |
| Metadata trigger | Follow every online page delivery, normal or prefetched, through the existing mechanism |
| Page selection | Owned exclusively by existing thread loading/prefetch; no location-specific three-page window or batch |
| Fixed interval | None; no normal-request pacing timer |
| Cache/concurrency | 24-hour retained successful/valid-empty cache, shared per-key in-flight work, one supplementary call in flight with immediate next dispatch |
| Failure | Per-key cooldown and rate-limit/authentication/challenge stop; no identity rotation |
| Offline/missing | Saved-page readers never request profile data; omit unavailable location |
| Loading tips | One stable local tip per foreground initial-loading occasion, no added wait |
| AI dependency | Enable verified guidance when its actual settings destination exists; no AI branch merge |

## Shared data-flow boundaries

Existing thread loading decides what is read and when. Once a page's author list
is available, normal and prefetch success both trigger its location enrichment.
No multi-page barrier, extra thread read, or second planner is needed. Existing
valid in-flight page completion remains valid under the original lifecycle.

Location work stays independent of body rendering and cannot retry/rewrite the
thread response. Results update only matching metadata, not body WebViews.
Loading copy consumes foreground visibility/lifecycle only: a prefetched page can
fetch its locations without consuming a usage tip. The two features intentionally
have different foreground requirements.

Both children may edit `ArticleListFragment`; implement sequentially and review
combined delivery, cleanup, and error handling against the final code.

## Request volume and tradeoffs

Count eligible distinct UIDs from actual delivered pages, after cache and
in-flight reuse. Current-plus-two prefetch may initially cover about sixty rows;
this is an estimate, not a batching contract. Later prefetch queries only new
uncached authors. Final-page exclusion and reused pages reduce the count.

A profile observation can be up to 24 hours old and is not a historical posting
location. Cache eviction/loss can require another request. The no-interval user
instruction applies to ordinary dispatch; failure handling and cache freshness
remain separate. No safe NGA threshold has been measured.

AI guidance is truthful only with the real settings destination. Capability-based
eligibility avoids a new user setting or cross-worktree runtime dependency.

## Rollout and rollback

No new server/dependency, raw thread-cache migration, or preference migration is
needed. Both changes are independently reversible; the location cache is
independently versioned/disposable and tips hold no persistent state. Update
project specs only after implementation is checked.
