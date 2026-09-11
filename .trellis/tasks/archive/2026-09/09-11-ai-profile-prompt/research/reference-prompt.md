# Reference Prompt and App Constraints

## Source

- User-specified source: <https://greasyfork.org/zh-CN/scripts/595323-nga-%E6%88%90%E5%88%86%E5%88%86%E6%9E%90%E5%99%A8-deepseek-%E9%BB%98%E8%AE%A4%E7%89%88/code>.
- Retrieved on 2026-09-11 from Greasy Fork's public code and update endpoints.
  Script metadata identifies `NGA 成分分析器`, author `winop`, version `2.2.31`,
  license `MIT`.
- The downloaded script SHA-256 is
  `85ee0fe89afc8c4049c06a72870d1454c2976c0f65b8ee6ef057c9d9c457ae83`.
  The reference is stored under ignored `.temp`; no downloaded JavaScript was
  executed or added as an application dependency.

## Observed Prompt Patterns

The direct profile-analysis request at reference line 685 asks for ten dimensions
scored 1–10, supporting evidence, an overall score, a 200–300 character summary,
3–5 tags, and a possible region. Its output is NGA BBCode intended for posting.
The longer export prompts at lines 847–1270 and 1317–1752 expand the rubric and
assume much larger histories, including topic bodies and up to 500 replies.

The ten reference dimensions are technical ability, spending ability,
professional depth, social activity, interest breadth, emotional stability,
living quality, influence, learning growth, and authenticity/trustworthiness.
Several require observations the app does not collect, such as other users'
reactions or complete posting history. Some extrapolate real-world personal
traits from forum content. The useful transferable structure is explicit
dimensions, concrete evidence, a compact synthesis, and descriptive tags.

The script also has floor-reply and automatic-posting prompts. Those are separate
features and are not part of this profile-prompt request.

## Existing App Boundary

- `ProfileSummaryInput.java:78` currently asks only for a concise summary of
  recent public discussion. This is the owner of the requested behavior change.
- Inputs contain at most 20 accepted first-page topics and 20 accepted
  first-page replies. Topic entries have title, board, and date, without a topic
  body; reply entries additionally have at most 800 characters of public text.
- The actual reply belongs to the viewed user, but its enclosing topic title
  is context and need not express that user's opinion. Quote attribution must
  remain distinct from the author's own statements.
- `AiSummaryDialog.java:129` uses plain `TextView.setText`. It does not render
  BBCode or Markdown tables. The result remains in the existing source-page
  dialog.
- `AiSummaryClient.java:72` allows 1,024 output tokens for summaries. Keep the
  requested response concise; this task does not change transport or token limits.
- Existing text sanitization, immutable snapshots, accepted-entry limits,
  unavailable-item filtering, and credential isolation remain applicable.

## Validation Boundary

Use source/spec review and existing input-format coverage. If counts or evidence
numbering change, extend the existing bounded-input tests for those observable
properties; do not add tests that merely repeat the new prompt string.

The user declined local builds and subsequently said not to inspect remote build
results. Do not compile or run Gradle/lint/JVM tests, and do not query or wait for
CI after the authorized commit, finish-work, and push. No NGA reads, model calls,
credentials access, or device operations are needed for this task.

## Prompt Adaptation

The user selected a qualitative portrait: interests, views, and discussion style
with evidence and tags, without scoring. Retain the reference's structured
observations, synthesis, and descriptive tags; omit numeric dimension scores,
overall scores, rankings, and unsupported real-world trait extrapolation.

Ask for five plain-text sections: `兴趣关注`, `主要观点`, `发言风格`, `成分总结`,
and `标签`. The first three sections each contain at most two concise observations.
Every substantive observation includes a `[主题N]` or `[回复N]` reference plus a
short quote or concrete paraphrase; an identifier alone is not evidence. Where
the sample is insufficient, say so briefly rather than inventing a view or trait.
Contradiction claims require both comparable statements and their references.

Use one or two sentences for synthesis, then 3–5 supported interest/style tags;
fewer tags are acceptable when evidence is sparse. Keep the whole answer within
500 Chinese characters and avoid a repeated opening disclaimer.

The user's later voice preference is implemented as general traits: deadpan
black humor, brisk short sentences, occasional technical or consumer-culture
metaphors, and satire tied to actual forum wording. Do not name or imitate the
individual style of the living novelist referenced by the user. Humor remains
secondary to clear evidence and attribution; do not manufacture contradictions,
experiences, motives, or personal insults for a punchline.

Prefix the input with the actual retained topic and reply counts and label each
entry using an index starting at 1 within its own kind. The labels are plain-text
evidence references, not clickable links or extra metadata collection. Existing
title, board, date, reply truncation, quote markers, and empty-sample wording stay
available. Explicitly state that topic bodies and whole histories are absent;
do not treat reply thread titles or quoted material as the author's own opinion.
