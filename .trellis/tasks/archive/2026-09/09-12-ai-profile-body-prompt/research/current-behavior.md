# Current profile sampling and topic-body evidence

## Scope

Read-only source inspection on 2026-09-12. Application baseline:
`feature/ai-summary` at `dc601c93` in
`/home/toph/nga-just-works-ai-summary`. No live forum/model requests, credential
reads, or device operations were performed.

## Current application

| Concern | Source | Observed behavior |
| --- | --- | --- |
| Sampling | `nga_phone_base_3.0/src/main/java/sp/phone/ai/summary/ProfileSummaryLoader.java` | Topics then replies, each first page; immutable viewed UID and prompt selection; cancellable asynchronous ownership. |
| Available topic fields | `nga_phone_base_3.0/src/main/java/sp/phone/ai/summary/NgaProfilePageSource.java:279-300` | Authorship checked on the outer topic row; only title, board, and date retained. The topic TID is not currently retained in an input entry. |
| Reply fields | Same source | Authorship checked on `__P`; body from `__P.content` and date from `__P.postdate`. |
| Topic output | `nga_phone_base_3.0/src/main/java/sp/phone/ai/summary/ProfileSummaryInput.java:32-38,103-111` | `appendTo(..., false)` prints topic metadata only, even if the entry constructor received body text. |
| Reply cutoff | `ProfileSummaryInput.java:13-25`; `SummaryText.java:23-53` in the summary package | Strip/normalize markup, preserve quote boundaries, then retain the first 800 UTF-16 code units, avoiding a split surrogate pair. Whitespace and punctuation count; this is not 800 words or tokens. No truncation marker is currently added. |
| Earlier sanitation bound | `SummaryText.java:11,24` | Source text is first bounded to 64,000 UTF-16 code units before cleaning. Any new full-body policy must account for this earlier cutoff as well as the entry cutoff. |
| Whole-prompt bound | `nga_phone_base_3.0/src/main/java/sp/phone/ai/AiSummaryClient.java:37,122-123` | At most 65,536 UTF-16 code units, including fixed instructions, custom style text, metadata, and all bodies; longer prompts are rejected. |
| Existing regression | `nga_phone_base_3.0/src/test/java/sp/phone/ai/summary/SummaryInputTest.java:72-100` | Explicitly asserts that topic bodies and long reply suffixes are absent. This behavior coverage must change alongside the feature. |

The entire rejected paragraph lives at `ProfileSummaryInput.java:90-93`.
Deleting it alone would leave the forum-roast preset's mandatory identifier and
short-quotation clause at `AiProfilePrompt.java:35`. Both owners must be updated.

## Reference comparison

### nga_harmony

Sources under the main worktree's ignored reference checkout:

- `/home/toph/nga-just-works/references/nga-clients/nga_harmony/entry/src/main/ets/service/api/UserApi.ets:200-247`
- `/home/toph/nga-just-works/references/nga-clients/nga_harmony/entry/src/main/ets/common/components/ProfileCardPopup.ets:64-74`

Its wired analysis path reads the first topic/reply list pages. It stores topic
subjects and dates, without original-post bodies. Reply text is cleaned and cut
with `rc.slice(0, 200)` before inclusion. This confirms a title-only reference,
not an NGA API requirement.

### NGA composition userscript

Inspected the already downloaded reference
`.temp/greasyfork-595323-current.user.js`; it was not executed.

- `collectUserAuthoredTopicUrls` is defined at line 277 and
  `fetchTopicsContent` at line 289. The latter contains HTML fetching and body
  extraction code for up to 30 topics.
- Export templates at lines 980 and 1450 contain topic-body sections.
- However, searching the complete script finds no call to either helper.
  `allTopics` is initialized/reset to an empty array and only read by exports.
  The wired topic-page feature `extractTopicTitlesFromCurrentPage` at line 692
  extracts titles/URLs, not bodies.

Therefore the inspected userscript contains a dormant body-collection helper
and body-ready export templates. Do not claim that its active analysis path
actually collects and supplies topic bodies. The earlier task's reference
audit described the broader export shape; tracing call sites qualifies that
claim.

## Source-derived implementation direction

- Use the existing `TOPIC.LIST` operation to select up to 20 topics from the
  first page, then retain each validated topic TID for an original-post read.
- The existing `THREAD.PAGE` operation is documented in
  `.trellis/spec/backend/nga-platform-operation-registry.md:43` and built by
  `nga_phone_base_3.0/src/main/java/sp/phone/mvp/model/ArticleListModel.java:49-72`:
  `GET /read.php?page=1&__output=8&noprefix&v2&tid=<tid>`.
- `ArticleConvertFactory.java:60-100` in the adjacent `convert/` directory
  consumes `data.__T` and `data.__R`. Its renderer converts all floors and logs
  raw responses (`:61,73`), so it is evidence for the wire shape, not a suitable
  collector to invoke directly for this feature.
- Add a narrow body-read path that extracts only the verified original post,
  reuses the profile collector's session/cancellation/host constraints, and
  never sends other floors or renderer metadata to the AI. This can add up to
  20 bounded topic-detail reads to the existing two list reads.
- Preserve the unavailable-item vs malformed/foreign-author distinction from
  the existing profile contract. `ThreadRowInfo.java:14-25` declares the
  `tid`, `authorid`, `pid`, and `lou` fields. The pinned Justwen
  `ArticleListFragment.java:291-297` recognizes the original post by `lou == 0`;
  the corresponding current-fork check is at `:380-384`. The new collector
  must verify explicit floor metadata, matching thread identity, and the
  viewed author rather than taking the first returned row by position.
- `ThreadPageInfo` in `sp/phone/mvp/model/entity/` maps topic identity and author
  from `data.__T`. When both topic-level and original-row identity fields are
  present, reject disagreements; absent or malformed mandatory identity
  fields must not be replaced by Java bean defaults.
- `TopicSearchFragment.java:84-96` uses `content=1` to mean search includes
  body text. This is not evidence for a topic-list option that returns bodies.
  Keep the existing list operation and add the source-observed detail read.
- The inspected branch has no `sp.phone.profile.ProfileEnvelopeParser` or
  compatibility-reader module. Do not assume newer main-worktree helpers are
  available or merge unrelated branch work as part of this feature.

## Wire compatibility boundary

The pinned `ArticleConvertFactory.java:52-58` recognizes the `/*$js$*/` marker
and legacy numeric `content`/`subject` forms. The existing AI `SafeJsonParser`
deliberately rejects arbitrary JavaScript, comments, and control characters.
Keep any necessary source-backed envelope normalization local to the new NGA
body parser, outside quoted text, before the bounded safe decoder. Do not
weaken the shared AI-response decoder or invoke the legacy renderer/logging
path. Unknown malformed input remains an explicit collection error.

The existing redacted research at
`/home/toph/nga-just-works/.trellis/tasks/08-08-read-php-contract-utilization/research/read-php-evidence.md:44-65`
records a past JSON-like response with `content/tid/pid/authorid/lou` fields
and an unresolved parse failure. It is not a complete reusable response fixture
or evidence that a new parser works against the live service. Current work
uses source-derived synthetic fixtures, with this compatibility limitation
reported rather than hidden by permissive parsing.

## Confirmed body-length decision

On 2026-09-12 the maintainer answered: `限制改成取前1200个字。`
The task applies a 1,200-character prefix to each topic and reply body, with
the existing UTF-16 and surrogate-boundary semantics. No product questions
remain. The initial suggestion to replace individual caps with a shared body
budget is not the selected design.

Forty retained bodies contribute at most 48,000 code units. At the existing
title/board/date bounds, either built-in style can still fit under the 65,536
whole-prompt bound. An 8,192-character custom style combined with maximum
metadata can exceed that bound; retain the existing explicit rejection rather
than silently changing the selected body prefix or custom instructions.

## Verification plan boundary

Planning is source-only. Implementation should add meaningful behavior
coverage for topic attribution, body inclusion, prefix limits, and cancel/late
callback handling, then run the applicable device-independent Android checks.
Device tests and live service calls are not part of this task's current scope.

Planning validation passed `task.py validate`, requirement/acceptance mapping,
manifest-path checks, and whitespace checks. At that gate the task remained
`planning` and only task artifacts had changed. Validation warns that the AI summary and
Android quality specs exceed the per-file injection cap; implementation/review
dispatches must read their relevant complete sections directly from disk.
