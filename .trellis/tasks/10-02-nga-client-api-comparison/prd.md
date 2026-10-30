# Four-Way NGA API Comparison and Upstream Learning

## Goal

Compare `Night-stars-1/compose-nga`, `apap6628114/lnga_harmony`,
`ForeverZero/NgaClient`, and the current `nga-just-works` project at equal
API-detail depth. The purpose is to learn the three upstream projects' API
strengths and identify concrete improvements for this project. A catalog is
supporting evidence; the main result is a justified learning/adoption plan.
The user approved planning and explicitly clarified both the four-way scope
and the learning objective on 2026-10-02. The subsequent priority is thread-read
reliability: investigate reported HTTP403/read failures, whether upstream fixes
were confirmed, and whether alternative request mechanisms could reduce reliance
on compatibility/browser settings. The user clarified that the symptom is intermittent default-reader failure
with the generic backend-error toast, not specifically HTTP403. The user then
approved focused research into LNGA signed HTML-to-native reading, including
request/session compatibility, parser fidelity and current-reader integration.

## Background

- The current fork's baseline is Justwen commit
  `5d807617f8058950f7ea81dda405e38fb0cc37ec` (`SOURCE_LEDGER.md`).
- Existing operation and transport contracts live in
  `.trellis/spec/backend/nga-platform-operation-registry.md` and
  `.trellis/spec/backend/network-foundation-contract.md`.
- Earlier research covers `apap6628114/nga_harmony`; its identity and source
  version must not be silently substituted for the requested `lnga_harmony`.
- There was no current task before this task was created. Existing related
  tasks remain separate.

## Requirements

- R1: Pin each of the four projects to a commit and record the repository
  identity, default branch, source availability, and relevant source paths.
- R2: Inventory NGA-facing operations by feature, including method, host/path,
  selectors, parameters, session requirements, response shape, and encoding.
- R3: Trace shared request construction, authentication, decoding, error
  handling, and retry behavior; distinguish declarations from reachable use.
- R4: Compare all four projects symmetrically and identify the three upstream
  projects' strengths relative to current product source. For each candidate
  lesson, document evidence, current behavior, expected benefit, adoption cost,
  compatibility implications, and priority; label missing evidence.
- R5: Keep findings in reviewable Markdown with source anchors. Source code
  observations do not establish current server availability.
- R6: Inspect public issue discussions and linked changes for read failures;
  distinguish HTTP403 from response corruption, closed issues from confirmed
  fixes, upstream claims from source observations, and improvement from immunity.

- R7: Trace LNGA signed HTML from request through envelope, extracted data,
  native model and display. Assess current credential reuse, preserved/lost
  fields, integration seams, parser failure modes and offline experiment gates.

## Acceptance Criteria

- AC1 (R1): All four repositories have a reproducible source revision and a source
  map, or an explicit access limitation if its source is unavailable.
- AC2 (R2, R3): Readers can trace each catalogued operation to request and
  parser evidence, and can identify shared transport behavior and exceptions.
- AC3 (R4): A four-column comparison matrix identifies equivalent operations,
  differences, unsupported/uncertain areas, and an evidence-backed prioritized
  list of lessons for the current fork. An upstream pattern is not called an
  advantage merely because it is newer or uses a different endpoint.
- AC4 (R5): Findings distinguish observed source behavior, locally tested
  behavior, and unverified runtime assumptions; no live availability claim is
  inferred from code.

- AC5 (R6): `research/read-reliability.md` compares evidence across all three
  upstreams, explains plausible benefits and limits for the current reader, and
  provides dated links plus search-coverage limitations.

- AC6 (R7): A focused feasibility report identifies source-backed transport and
  parser contracts, names concrete parity gaps, and separates offline correctness
  checks from any later live reliability validation.

## Out of Scope

- Implementing or migrating application features, changing API contracts in
  product code, or importing upstream implementation code.
- Account login, posting, uploads, and server-side changes. The user separately
  authorized a bounded three-request live read of thread47660355/page1 using
  .temp/cookies.txt; results and limits are in research/lnga-live-test-summary.md.
  No further live traffic is implied.
- General UI, build-system, or architecture comparisons unrelated to APIs.

## Deliverables and Evidence Boundary

The source research is recorded in `research/comparison.md` and its four linked
repository ledgers. `design.md` records the comparison method and compatibility
boundaries; `implement.md` records research and review steps. Source revisions
and operation call sites have been inspected. No NGA requests or application
changes are part of this task. Adoption priorities are recommendations, not
product requirements or implementation approval. The task remains in planning
for user review; no blocking research-scope question remains.
