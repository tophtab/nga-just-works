# Streaming Summary Design

## Ownership and Integration Contract

`AiSummaryClient` owns HTTP; a pure Java parser owns SSE framing and typed
answer/reasoning accumulation. `SummaryController` owns generation and target
checks. The shared dialog renders state and copies its answer projection.
Both input classes own their exact prompt instruction.

The user requested following Cherry Studio. The pinned source analysis in
`research/cherry-studio-streaming.md` is authoritative for this adaptation:
one request-owned message, typed reasoning/text projections, stable folding,
answer-only copy, and coalesced snapshots. No persistent output storage is added.

Add the following default method to both `AiSummaryClient.Callback` and
`SummaryController.Callback`, retaining success/error methods:

```java
default void onProgress(String answer, String reasoning) { }
```

These are cumulative snapshots, not deltas. Transport callbacks run on its
thread; the controller marshals snapshots to its UI executor and rejects stale
generations. Terminal `onSuccess(String)` carries the complete answer only;
retain the latest reasoning on success. Input-source callbacks do not publish
model progress. Coalesce progress if needed to avoid an unbounded UI queue.

Controller state exposes `getAnswer()`, `getReasoning()`, `getErrorMessage()`,
and `getCopyText()`. Retain `getText()` as a compatibility projection (error
message in ERROR, answer otherwise). Copy text is empty while loading or without
an answer; after success/failure it is the received answer in full. On errors,
retain partial answer/reasoning and show the error in a separate view. Retry and
cancel clear both channels and invalidate queued updates.

## Transport and Completion

- Summaries send `stream: true` and accept SSE. The user's final choice is one
  shared `max_tokens: 10000` ceiling for both summaries and the non-streaming
  connection probe. Thinking and body generation share that ceiling. Do not send
  `max_completion_tokens`, `max_output_tokens`, or thinking/reasoning overrides.
  Do not restore the former 1,024/eight-token budgets or add a separate thinking
  budget. The final 10,000 choice supersedes the earlier no-cap instruction.
- Preserve the isolated client, one-shot POST, no redirects, no automatic retry,
  and credential isolation. Model discovery stays bounded ordinary JSON.
- Summary total timeout is 180 seconds, idle read timeout 60 seconds, and
  connect/write 15 seconds. Keep the test timeout seam. Non-summary operations
  may retain the current timeouts.
- Strict incremental UTF-8 and SSE framing support CR/LF/CRLF, blank event
  delimiters, comments, event/id fields, multiline data, usage-only events,
  and DONE. Read only first-choice/index-zero content and reasoning strings.
- Require a valid finish or DONE before success. EOF without completion is an
  interrupted response; `finish_reason: length` is output exhaustion. A completed
  empty answer is an empty-response error. Never substitute reasoning or tool
  arguments for the answer. Preserve partial text through progress on failure.
- Retain ordinary JSON completion compatibility. Narrowly decode or reject
  recognized serialized protocol envelopes inside content; never display them
  as an answer. Ordinary prose/code containing braces or `data:` stays text.
- Normalize leading inline `<think>`/`<thinking>` sections across stream chunks
  before publishing an answer, following Cherry's reasoning extraction pattern.
  Keep native reasoning separate; unclosed matched thinking tags never become
  answer content. Leave literal tags inside already-started prose/code intact.
- Resource guards are independent of the prompt: at most 8 MiB decompressed
  summary transport, a 512 Ki-character event (shared decoder bound), and
  256 Ki characters per answer/reasoning channel. Resource overflow is an
  explicit failure, never silent substring clipping or false success. Keep
  model-list bounds unchanged. No 500/1,000/NGA-derived clipping is added.

## Shared Dialog and Prompts

The existing ScrollView contains a separate reasoning toggle/view above the
answer. Show it only when reasoning exists; start folded, retain user fold state
across chunks, and reset on retry. Use existing theme resources and an accessible
button label. Keep loading/error text separate from received answer text. Copy
uses `getCopyText()` directly, regardless of fold state; close/pause clears all
transient text. Avoid announcing every streaming token through accessibility
live regions. Keep existing navigation and manual Retry behavior.

Both prompts contain exactly `回复正文在1000字以内`. Remove the profile's old
500-character sentence. Preserve evidence IDs, attribution, public input bounds,
and the qualitative five-section profile request. The prompt is not a client
length check. No storage migration or provider-specific setting is introduced.
