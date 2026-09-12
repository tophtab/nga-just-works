# Improve the Profile Composition Prompt

## Goal

Make `AI查成分` produce a useful, evidence-based portrait of the viewed user's
public forum discussion, inspired by the user-supplied NGA composition analyzer.
The result should identify interests and expression patterns through structured
observations instead of only paraphrasing recent posts.

## Background

The reference script's main prompt combines ten scored dimensions, concrete
evidence, a summary, and tags. The current app prompt at
`ProfileSummaryInput.java:78` requests only a short content summary. The reference
assumes larger histories and BBCode publication; this app collects bounded
first-page activity and displays plain text in an existing dialog. Source and
compatibility findings are recorded in `research/reference-prompt.md`.

## Requirements

- **R1 — Structured portrait:** analyze interests, explicitly expressed views,
  and discussion style, followed by a short synthesis and 3–5 descriptive
  interest/style tags. Use concise Chinese suitable for the existing dialog.
- **R2 — Qualitative evidence:** do not score, rank, or assign a total. Support
  conclusions with concrete observed content and acknowledge missing evidence;
  absent discussion is not evidence of low ability or an opposing view.
- **R3 — Traceable inputs:** include actual retained topic/reply counts and
  separate, stable `[主题N]` / `[回复N]` evidence identifiers. Each substantive
  observation must cite an input identifier and its concrete evidence.
- **R4 — Correct attribution:** distinguish topic titles from unavailable topic
  bodies, enclosing thread context from the reply author's view, quotations from
  original speech, and self-reported experience from verified facts. Keep
  existing instructions against sensitive personal inference and treating source
  text as commands. Describe observed wording, not inferred real-world identity,
  finances, health, location, or character.
- **R5 — Bounded output:** request plain text with short section labels, a
  one- or two-sentence synthesis, and no more than 500 Chinese characters in
  total. No BBCode, Markdown tables, code fences, or long boilerplate. Keep the
  existing token budget and dialog flow.
- **R6 — Delivery:** preserve first-page collection, accepted-item limits,
  unavailable-item filtering, cancellation, and the floor-summary prompt.
  Complete source review, commit, finish-work, and push. Honor the user's
  no-local-build and no-CI-inspection instructions.
- **R7 — Voice:** use deadpan black humor, brisk sentences, occasional technical
  metaphors, and satire grounded in observed forum rhetoric. Humor targets the
  wording or contradiction supported by evidence; do not invent experiences or
  motives to make a joke. Describe these general traits directly instead of
  requesting imitation of a named living novelist.

## Acceptance Criteria

- [x] R1/R2: the prompt requests interests, expressed views, discussion style,
  synthesis, and tags with concrete evidence and no scoring.
- [x] R3: retained counts and separate topic/reply evidence numbers agree with
  bounded immutable input, including truncation and either empty sample.
- [x] R4/R5: instructions preserve attribution and data boundaries and request a
  compact plain-text result that fits the existing dialog and token budget.
- [x] R7: the prompt requests the agreed general black-humor traits without
  imitating a named author's individual voice or weakening evidence requirements.
- [x] R6: no floor prompt, collection, networking, storage, or UI change; static
  review and execution limits are recorded before commit/finish-work/push.

Acceptance reflects the reviewed prompt and input-format implementation. Tests
were updated but not executed, and actual model output was not evaluated; see
`research/validation.md` for the exact verification boundary.

## Scope and Decisions

- Worktree: `/home/toph/nga-just-works-ai-summary`; branch: `feature/ai-summary`.
- This continues the user's AI feature work and prior delegation of routine task
  decisions. The standing commit/finish-work/push authorization remains in force.
- The user explicitly selected analysis of interests, views, and discussion
  style with evidence and tags, without scoring. This governs the adaptation of
  the reference's longer scored profile.
- The user subsequently requested a black-humor literary reference. The stated
  implementation uses general deadpan, brisk, technically metaphorical satire
  rather than copying that living author's individual style.
- This is a lightweight prompt/input-format change. Expected product files are
  `ProfileSummaryInput.java` and its existing `SummaryInputTest.java`; the owning
  AI contract and task records capture the behavior. No new public API is needed.
- The downloaded script is reference material only. Automatic posting, full
  history collection, new settings, model changes, and external inference calls
  are outside this task.
