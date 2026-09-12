# Repair AI profile content-format failure and merge into main

## Goal

Repair the reproduced AI profile-analysis failure, retain its regression
coverage, and merge the verified correction into `main`.
The visible error is `NGA内容格式异常，请稍后重试` (the source string includes a
space after `NGA`).

## Confirmed context

- The maintainer requested testing and diagnosis, then explicitly approved
  creating this Trellis task on 2026-09-12.
- After reviewing the reported root cause and linked repair plan, the maintainer
  explicitly requested: `好，修复，并且merge到main分支里`. This approves the
  proposed product correction and its integration into `main`.
- The feature lives in `/home/toph/nga-just-works-ai-summary`, branch
  `feature/ai-summary`. Diagnosis used baseline `9a8113a9`; `main` subsequently
  merged that branch at `7cb9e50b`. The feature worktree was fast-forwarded to
  that current main baseline before repair, retaining main's existing changes.
- Commit `0482795c` added sequential original-topic-body collection to profile
  analysis. Existing activity-list and model-output fixes predate that change.
- The reported message belongs to `NgaProfilePageSource.RESPONSE_ERROR`, before
  model submission; it does not establish an API-key or model-service failure.
- The current focused AI JVM suite executed successfully: 204 tests across
  13 classes, zero failures, errors, or skips. Those tests use synthetic data.
- The subsequently authorized live probe reproduced the exact error on its
  second and final request. The first topic list parsed successfully; the
  first topic detail contained 53 literal U+0009 characters inside strings.
  Escaping those characters in memory made the same production parser accept
  that same response and verify the original body. See `research/live-result.md`.

## Requirements

- R1: Locate the exact failing response shape or decoding rule using current
  production code, and distinguish observed facts from hypotheses.
- R2: Compare the new topic-body reader with source-backed NGA formats and
  existing reader behavior; preserve viewed-user and requested-topic identity.
- R3: Produce a deterministic, sanitized reproduction where possible and
  identify why the existing tests did not catch it.
- R4: Record the investigation, commands/results, limitations, and smallest
  proposed correction. Do not claim the application is repaired without an
  implemented and verified correction.
- R5: Implement the reviewed local topic-detail normalization for raw TAB/LF/CR
  with regression coverage; retain the shared model decoder, identity checks,
  escape semantics, cancellation, and operational limits.
- R6: Commit the verified repair and merge it into `main`, preserving existing
  main changes and recording the resulting commit and validation evidence.

## Acceptance criteria

- AC1: The final report identifies whether failure occurs during the topic
  list, an original-topic detail, or the reply list, with evidence for its
  precise cause or a clearly stated unresolved boundary.
- AC2: A confirmed parser defect has an offline reproduction against the
  current production classes and a named missing regression case.
- AC3: Existing regression results and any live observations are reported
  separately; no device/UI or live-service result is inferred from JVM tests.
- AC4: No credentials, raw personal content, or private configuration enter
  tracked artifacts, diagnostics output, or model requests.
- AC5: Raw TAB/LF/CR within native topic-detail strings no longer abort a valid
  profile sample; escaped controls and literal backslash text stay distinct,
  malformed escapes and unsupported raw controls still fail, and only the
  verified original reaches profile input.
- AC6: Regression, build, and required Android quality checks pass on the
  integrated main baseline; task-scoped failures are resolved before handoff.
- AC7: `main` contains the repair commit; the final report identifies the commit,
  merge result, and any material verification limits.

## Scope and constraints

The reviewed scope now covers diagnosis, product repair, required quality checks,
commits, and integration into `main`. No device operations, separate release
publication, prompt redesign, model changes, or unrelated cleanup.
Use existing local evidence and offline tests first. Any required live NGA
observation follows the platform access rules with an explicit, bounded
read-only request plan; there is no retry loop or account rotation.

## Diagnosis outcome and execution approval

The investigation acceptance criteria are met. The current defect is a native
NGA string-encoding compatibility gap in topic-detail normalization. Generated
JSON fixtures escaped the characters and concealed the gap. It is not a
failure of the configured model service.

The repair in `design.md` and `implement.md` was approved by the maintainer's
subsequent message. The added main-integration scope is that same explicit
request, not an inferred feature expansion. Proceed with implementation and
validation without a second task or repeated approval request.
