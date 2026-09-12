# Design: profile topic bodies and simplified prompts

## Authority and boundaries

The task PRD records the maintainer's current decisions: include topic original
posts, use a 1,200-character prefix for each topic/reply body, and remove the
rejected fixed paragraph and overlapping preset requirement. These decisions
supersede the title-only, 800-character, and mandatory-citation clauses in
`.trellis/spec/backend/ai-summary-contract.md`; update that spec after the
implementation is checked. Its other transport, session, configuration,
sanitization, and cancellation contracts continue to apply.

Work in `/home/toph/nga-just-works-ai-summary` on `feature/ai-summary`. There is
no schema migration, dependency addition, or main-worktree merge in this task.

## Data flow and ownership

```text
ProfileActivity -> AiSummarySources.profile(uid, name)
  -> captured NGA domain/Cookie/UA and selected AI configuration
  -> ProfileSummaryLoader
     -> NgaProfilePageSource.loadFirstPage(TOPICS)
        -> TOPIC.LIST page 1 -> at most 20 validated topic candidates
        -> sequential THREAD.PAGE page-1 reads -> verified original bodies
        -> one enriched TOPICS Page
     -> NgaProfilePageSource.loadFirstPage(REPLIES)
        -> TOPIC.LIST page 1, searchpost=1 -> viewed user's __P bodies
     -> ProfileSummaryInput -> selected style + bounded plain-text sample
  -> existing AiSummaryClient and result dialog
```

Keep the public `PageSource.loadFirstPage` and `PageCallback` contract. Encapsulate
topic enrichment inside the NGA page source so `ProfileSummaryLoader` continues
to own the two category results and its existing stale-callback checks.

Within the source, retain validated topic IDs in a private candidate type next
to immutable metadata. Fetch IDs must not become prompt text. Reuse one row
validation path for list parsing; do not parse the same response through a
second permissive implementation to recover missing IDs.

The page source may use a small package-private body parser or request-state
helper if that makes its responsibilities clear. It must not introduce another
HTTP client, global cache, or renderer dependency. `AiSummarySources` still
captures the session once for the whole profile request.

## Topic-body request and parser contract

| Boundary | Required behavior |
| --- | --- |
| Selection | Retain at most 20 available, viewed-author topic rows from the existing first list page. Validate a positive decimal TID before scheduling a detail request. |
| Request | Use the source-observed `THREAD.PAGE` GET: `/read.php?page=1&__output=8&noprefix&v2&tid=<tid>`. No PID or author filter, because an original post must be found in the topic's first page. |
| Origin/session | Reuse the page source's exact HTTPS NGA allowlist, Cookie/UA snapshot, explicit headers, redirect restrictions, timeouts, and disabled application retries. |
| Decode | Retain the 512 KiB response bound and declared-charset/GBK behavior. Parse a bounded object with `SafeJsonParser`, preceded only by source-backed NGA envelope normalization when needed. Normalize outside quoted text; never execute response content. |
| Page identity | Require a matching positive `data.__T.tid` and a `data.__R` object. Validate any additional supplied topic identity instead of coercing absent fields to zero. |
| Original-post identity | Select an explicitly reported `lou == 0`, not array position. Require matching original-row TID and viewed author ID. A supplied topic-level author ID must agree. Reject ambiguous duplicate originals. |
| Extracted text | Extract only that row's body. Normal textual/numeric wire forms need source-backed conversion preserving their text; objects and arrays are not body text. Other floors, user tables, signatures, attachments, and session metadata do not enter the prompt. |
| Availability | Skip existing explicitly denied/error list rows before authorship checks. A known unavailable original body contributes a fixed application-owned unavailable notice next to its already-visible topic metadata; do not include server denial text. |
| Failures | Unknown malformed envelopes, missing mandatory identity, foreign authors/threads, top-level access errors, redirects, HTTP errors, and timeouts terminate collection through the existing safe error path. They must not silently become title-only success. |

If no original remains because explicitly unavailable records were filtered,
retain the topic with an unavailable body status. A well-formed original with
empty textual content can likewise display that no text was available. Absence
of an original in an otherwise unmarked response is a format error, not proof
of an unavailable post. A later floor never substitutes for the original.

The full HTTP detail response can contain other floors, but only the validated
original's text survives projection. Avoid the legacy `ArticleConvertFactory`
path because it renders all rows and logs raw response data.

## Sequential collection and cancellation

- The source owns one cancellable load state covering its list request and
  all topic-detail requests. At most one transport call is active in that
  state. Retain list order when producing the enriched page.
- Use an explicit stage/request identity so a completed old callback cannot
  advance a new stage. Account for fake calls that complete synchronously
  before returning their handles.
- Cancellation stops the active call, prevents later detail reads, clears
  transient candidates, and suppresses success/error callbacks arriving late.
  Cancellation between enrichment completion and the reply read remains
  covered by `ProfileSummaryLoader` and `SummaryController`.
- A call-factory/enqueue exception or parser error must produce at most one
  terminal safe error; do not leave the UI waiting or leak raw exception text.
- An all-unavailable topic list schedules no detail reads. Replies can still
  provide a useful sample. If both categories have no visible entries, keep
  the existing no-content result.
- There are at most 22 application-scheduled reads: two category-list reads
  plus one detail read per retained topic. This does not claim a bound on
  underlying HTTP follow-up transmissions.

## Input model and prompt

Give topic and reply entries a shared body representation with a single
1,200-character limit. A body-oriented name is preferable to treating a topic
body as a reply. Preserve source compatibility for existing entry callers and
tests where practical; do not add duplicate configuration values.

Use the existing `SummaryText` sanitation and surrogate-safe limiting behavior.
Keep the earlier 64,000-character source processing bound and existing
title/board/date/name bounds. The prompt framing states that every body is a
prefix of at most 1,200 characters; this is the truncation disclosure and does
not claim a complete post. A separate per-entry truncation state is unnecessary.

Serialize topics as metadata followed by `主题正文：...` and replies as metadata
followed by `回复正文：...`. Keep actual counts and independent input entry labels.
Empty/unavailable body notices must be application-owned text distinguishable
from authored content.

Remove exactly the rejected fixed paragraph and the mandatory numbered
short-quotation clause in the preset's punchline instruction. Retain the forum voice and
the two preset output structures, custom text verbatim, and the existing
sample/quotation framing. Replace the inaccurate title-only statement.
Do not reintroduce the removed rules elsewhere or silently rewrite a saved
custom prompt to enforce them.

The 1,200-character input cap does not change the existing model-output length
instructions or output-token configuration.

## Prompt-size compatibility

Forty bodies consume at most 48,000 UTF-16 code units. Test maximum retained
metadata plus each built-in style against the existing 65,536 whole-prompt
limit. Keep that client limit as the final boundary for custom instructions.
An exceptionally large custom style plus maximum metadata can still fail with
the existing over-limit error; no partial request, extra body truncation, or
custom-text mutation is allowed. No automatic token budget is introduced.

## Verification and delivery considerations

Behavior coverage must prove original-post attribution, 1,200-character
prefixes, accurate counts, actual source/body isolation, sequential request
ownership, cancellation, and typed failures. Existing style-selection and
custom-text snapshot tests remain applicable. Prose deletions need source
review, not a new test that repeats every prompt sentence.

Use synthetic fixtures and MockWebServer. Live NGA/model compatibility and
device behavior are not claimed by these tests. Additional detail reads can
increase loading latency; the existing cancellation UI remains usable. Unknown
NGA syntax produces a safe error rather than guessed text.

This change has no persisted state. Rollback restores the profile collector,
input/preset changes, and their tests/spec delta together. Avoid resetting the
worktree or changing unrelated main-worktree files.
