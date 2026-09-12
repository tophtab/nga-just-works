# Stream AI Summaries with Collapsed Reasoning

## Goal

Stream usable answers for both profile composition and floor summaries while
preserving provider-default thinking. Keep reasoning separate and collapsed;
copy the complete reply body only.

## Background

The supplied profile yielded 13 available topics and 19 replies, all matching
its UID. GLM consumed the old 1,024-token budget during reasoning and returned
empty content with finish reason `length`. With normal thinking and 4,096 tokens,
the same sample produced a 454-character answer. A DeepSeek comparison returned
an upstream HTTP 403. Redacted evidence is in `research/model-response-evidence.md`.

## Requirements

- R1: Use real streaming and display answer chunks while generation runs.
- R2: Preserve model thinking; show it separately, initially collapsed, with an
  accessible expand/collapse control. Never treat reasoning as answer text.
- R3: Copy only the received reply body, independent of the fold state. Exclude
  reasoning, protocol metadata, status messages, and errors.
- R4: Both prompt builders must contain exactly `回复正文在1000字以内`. Replace
  the profile's old 500-character instruction. This is prompt guidance only:
  receive, display, and copy replies over 1,000 characters without truncation.
- R5: Use the user's final choice of `max_tokens: 10000` for summaries and
  connection tests. This is the combined generation ceiling for thinking and
  body text; preserve default thinking without a separate reasoning budget.
  Distinguish exhaustion, empty output, malformed protocol, and interrupted
  streams. Preserve partial text when a stream fails.
- R6: Preserve existing profile source-input bounds, isolated credentials,
  manual retry, cancellation, and stale-generation protection. Keep operational
  resource bounds separate from prompt length and separate for both channels.
- R7: Keep the service address and Key exclusively in ignored `.temp`. No real
  forum excerpts, model reasoning, or credentials enter Trellis/tests/commits.
  Honor the retained no-local-build instruction and report verification limits.
- R8: Follow the inspected Cherry Studio pattern for typed reasoning/text
  content, stream updates, collapsed display, and answer-only copy, adapting it
  to the existing transient Android dialog.

## Acceptance Criteria

Implementation and regression coverage were checked statically under the user's
no-local-build restriction. Checkmarks do not claim JVM or Android execution;
the executed checks and live-probe limitations are recorded in `validation.md`.

- [x] R1: Fake-server progress arrives before completion; ordinary JSON remains
  a compatible fallback.
- [x] R2/R3: Mixed reasoning/answer chunks, fold toggles, retry, and copy keep
  the two channels separate.
- [x] R4: Both exact prompt instructions are present; output over 1,000
  characters is received and copied in full.
- [x] R5: Usage-only, reasoning-only, exhausted, malformed, and incomplete
  streams cannot become false successful replies or display raw envelopes.
- [x] R6: Late callbacks after close, same-target retry, or target replacement
  cannot overwrite a new state; no automatic retry or credential mixing.
- [x] R7: Record live protocol verification, static review, and unexecuted tests
  honestly; scan tracked work for supplied secret values.

## Scope and Authorization

The user delegated task decisions and explicitly requested the above behavior,
including the exact prompt sentence for both features. Work in the existing
`feature/ai-summary` worktree, retaining its shared legacy Java/XML dialog.
The latest prompt-only instruction supersedes an NGA-derived clipping rule;
no unverified forum maximum is claimed. Existing NGA reads are complete.
The user additionally requested one final GLM streaming request using the same
bounded sample and configured service, without redirects or retries, and a
report of the actual input/output/reasoning token usage and completion status.
That no-cap request completed normally. The user then chose a 10,000-token
generation ceiling, superseding the earlier no-cap preference and the briefly
suggested 12,000-token setting; retain this final choice in code and tests.
The user explicitly requested committing the completed work, running
`finish-work`, and pushing `feature/ai-summary`. This authorizes the normal
work commit, task archive, journal record, and branch push without another
confirmation. It does not authorize local compilation or workflow monitoring.
No pagination, new chat/settings architecture, thinking override, server
administration, local compilation/build, device operation, or remote workflow
inspection is part of this task.
