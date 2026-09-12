# AI 查成分主题正文与提示词简化

## Goal

Give profile AI analysis the viewed user's own topic bodies as well as reply
text, and simplify the fixed prompt according to the maintainer's request.
Use the first 1,200 cleaned characters of each topic or reply body.

## Background and confirmed facts

- The implementation lives in `/home/toph/nga-just-works-ai-summary` on
  `feature/ai-summary`. Task creation and planning were authorized on
  2026-09-12; this request does not target unrelated main-worktree changes.
- The current profile collector requests the first topic page and the first
  reply page, retaining at most 20 available entries from each. Topic entries
  have no body; reply entries use the viewed user's `__P.content`.
  Evidence: `NgaProfilePageSource.java:279-300` and
  `ProfileSummaryInput.java:32-38` under
  `nga_phone_base_3.0/src/main/java/sp/phone/ai/summary/`.
- Each reply is cleaned and then cut to at most 800 Java UTF-16 code units.
  The remainder is absent from the model input; this is not a model-output
  limit or semantic summarization. `SummaryText.limit` avoids splitting a
  surrogate pair. Evidence: `ProfileSummaryInput.java:13-25` and
  `SummaryText.java:23-53` in the same package.
- The whole AI prompt also has a separate 65,536-code-unit limit. Over-limit
  prompts are currently rejected, not automatically shortened.
  Evidence: `nga_phone_base_3.0/src/main/java/sp/phone/ai/AiSummaryClient.java:37,122-123`.
- The fixed paragraph the maintainer rejected is in
  `ProfileSummaryInput.java:90-93`. The forum-roast preset independently
  repeats a requirement for identifiers and short quotations in
  `nga_phone_base_3.0/src/main/java/sp/phone/ai/AiProfilePrompt.java:35`.
- Reference comparison and source-derived topic-body feasibility are recorded
  in `research/current-behavior.md`. Reference implementations are evidence of
  choices, not a requirement to keep title-only topics.

## Requirements

- **R1 — Topic bodies:** Include the original-post body of each available
  retained topic in the profile analysis input, with its existing title,
  board, and date. Content must belong to the viewed profile user and the
  selected topic. Do not substitute other users' replies or a later floor.
- **R2 — Prompt simplification:** Remove the entire fixed paragraph beginning
  `每条实质观察须附输入中的[主题N]或[回复N]编号` and ending
  `不作人格评价。`, as requested. Remove the overlapping mandatory
  identifier/quotation wording from the forum-roast preset. Apply the fixed
  paragraph removal consistently to both presets and the custom style;
  preserve the user's stored custom text verbatim. Input entry labels may
  remain for organization, without requiring them in the model's answer.
- **R3 — Accurate framing:** Replace the statement that topics have no body
  with a description of the actual retained data. Preserve the first-page
  sample description and the distinction between authored text and quotations.
  Any text-length statement must match the policy selected for R4.
- **R4 — Body length:** The maintainer selected the first 1,200 characters
  on 2026-09-12. Apply this limit separately to each topic and reply body after
  the existing text cleaning, using the existing UTF-16 counting and
  surrogate-boundary behavior. Shorter text is retained in full. Describe the
  prefix limit accurately in the prompt. Keep the existing whole-prompt limit
  and its explicit over-limit failure, including an exceptionally large custom
  style plus maximal metadata; do not silently shorten saved custom text or
  introduce another body-allocation strategy.
- **R5 — Collection behavior:** Keep the viewed UID and one session snapshot
  throughout collection, first-page sampling, and the 20-entry allowance for
  each category. Topic-body reads must be bounded and cancellable. Unavailable
  content must not be represented as authored text; cancellation must stop
  later reads and discard late results. No credentials or raw response
  metadata enter the AI prompt.

## Acceptance criteria

- [x] **AC1 / R1:** A visible topic's original-post body appears after its
  metadata in the generated profile prompt. Text from another author or floor
  cannot enter that topic body.
- [x] **AC2 / R2:** Neither built-in style imposes the rejected fixed rules or
  mandatory source identifiers/short quotations. Custom instructions are
  preserved exactly, and the fixed paragraph is absent for custom requests.
- [x] **AC3 / R3:** Sample counts, first-page scope, body availability, and any
  truncation notice accurately describe the text sent to the provider.
- [x] **AC4 / R4:** Bodies below/at/above 1,200 characters retain the expected
  prefix without splitting a surrogate pair. A maximum-sized 20-topic and
  20-reply sample fits the existing client limit with either built-in style.
  If custom instructions and metadata push an input over the global limit,
  the existing explicit error remains and no partial prompt is sent.
- [x] **AC5 / R5:** Collection remains tied to the selected UID and topic;
  unavailable records, cancellation, and stale callbacks retain their defined
  behavior without additional activity pages or account rotation.

All acceptance criteria were verified on 2026-09-12 through source review,
synthetic fixtures, the passing full app suite, and all-module lint. See
[validation.md](validation.md) for evidence and the two documented library
example-test compilation failures in the repository-wide diagnostic.

## Out of scope

- Full-history collection, additional activity-list pages, other users' floors,
  media retrieval, and changes to the standalone floor-summary feature.
- Changing provider settings, saved custom prompt text, result presentation,
  output-token limits, the model's requested response length, or the existing
  whole-prompt size limit.
- Live NGA/model calls, device operations, publication, and unrelated worktree
  changes during planning.

## Planning status

All product decisions are resolved. The PRD, `design.md`, `implement.md`, source
research, and both agent context manifests are complete. On 2026-09-12 the
maintainer approved proceeding from the final summary and instructed delivery
in this order: finish implementation/checks, commit, finish-work, then push.

## Delivery

After verification, commit this task's changes on `feature/ai-summary`, archive
the task and record the session through finish-work, then push that feature
branch. This authorization does not include merging into main, unrelated
changes, live service calls, or device operations.
