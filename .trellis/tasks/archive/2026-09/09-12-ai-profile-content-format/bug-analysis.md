# Bug Analysis: native topic-detail string controls

## 1. Root cause category

- **B — Cross-layer contract:** native NGA `read.php` data is JSON-like rather
  than guaranteed strict JSON; the new topic-body reader passed quoted raw
  controls through to a strict shared preflight.
- **D — Test coverage gap:** helper-generated JSON fixtures automatically
  escaped the failing character class.
- **E — Implicit assumption:** ignored metadata was assumed unable to affect
  original-body extraction, although the complete envelope is decoded first.

## 2. Relationship to earlier failures

Earlier repairs covered unavailable activity rows and reasoning-only model
output. This incident occurs at the newly added topic-detail collection stage,
before model submission. It is not evidence that those earlier fixes regressed.
The prior topic-body validation explicitly used synthetic fixtures and made no
live-compatibility claim. The missing native representation case remained
undetected despite a passing 204-test AI baseline.

The discriminating current evidence is one response that fails the unchanged
production parser and succeeds after only in-memory raw-control escaping.
It separates the fault from credentials, HTTP status, GBK decoding, model
service availability, and target identity. See `research/live-result.md`.

## 3. Prevention mechanisms

| Priority | Mechanism | Specific action |
| --- | --- | --- |
| P0 | Local representation adapter | Normalize supported raw TAB/LF/CR before the shared decoder; retain attribution and malformed-input checks. |
| P0 | Wire-level regression | Inject literal characters after JSON serialization and demonstrate failure on the baseline. |
| P0 | Integration regression | Pass a raw-wire detail through topic-list/detail/reply composition; verify metadata exclusion. |
| P1 | Explicit executable contract | Add native string normalization, escape-state behavior, size expansion, and required assertions to the AI-summary spec. |
| P1 | Bounded diagnostics | Keep live observations separately authorized and report structural evidence without retaining private bodies or credentials. |

## 4. Systematic expansion

The shared model parser intentionally rejects raw string controls and is not
the repair owner. The observed topic list had no such controls and parsed
successfully; do not change its contract based solely on the detail response.
Unknown raw controls, missing original identity, access failures, and arbitrary
malformed syntax still need explicit errors. This task adds no fallback that
silently drops source material or changes accounts.

The useful review question for future native-wire changes is whether test
serialization removes the exact source characteristic under investigation.
An integration test cannot catch that characteristic if its fixture generator
normalizes it away before production code receives it.

## 5. Knowledge capture

- The AI-summary spec now records the owning parser, three supported raw
  characters, strict shared-decoder boundary, failure matrix, and regression
  requirements in its existing seven-section contract.
- This application repository has no `src/templates/markdown/spec/` copy of
  the contract; there is no generated template to synchronize.
- Implementation and quality results are recorded in the task's validation
  artifacts before the spec and code are committed together.
