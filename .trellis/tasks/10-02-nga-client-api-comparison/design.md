# API comparison research design

## Boundary

This deliverable is source research and adoption advice, not a product migration.
The four repositories are pinned independently. Current source, old Justwen
compatibility contracts, and proposed improvements must remain distinguishable.
Research can proceed during planning; it does not activate product implementation.

## Evidence and report structure

- `research/current-project.md`: current product operation and transport baseline.
- `research/compose-nga.md`: signed API application and reachable consumers.
- `research/lnga-harmony.md`: requested Harmony repository, not an assumed rename.
- `research/nga-client.md`: Java library, distinguishing public methods and constants.
- `research/comparison.md`: symmetric matrix and prioritized adoption candidates.

Each operation records request construction, parser ownership, call-site evidence,
and transport exceptions. Source presence, live reachability, and correctness are
separate claims. Runtime availability is unverified throughout. Missing evidence
is reported explicitly rather than filled using another repository's protocol.

## Focused reader feasibility

The user prioritized read reliability and approved focused LNGA research.
`research/lnga-signed-html-feasibility.md` consolidates the transport, parser and
current-reader studies. Its offline-first prototype boundary supersedes the
broad feature ranking as the next candidate investigation; no product change
is approved by these documents.

## Adoption policy

Rank by concrete user benefit, evidence strength, and compatibility cost. App API
use alone is not a benefit: the current reader already uses an optional App path.
Token signatures and Cookie sessions are distinct request contracts. Prefer
operation-local experiments and offline fixtures over wholesale transport changes.
Retain the current reader's account, origin, error and pagination boundaries.
New product features, authentication changes and live validation require their
own scoped planning; this report authorizes none of them.

## Validation and rollback

Check revisions, source anchors, declaration/use distinctions, four-way coverage,
and every recommendation's current baseline and caveat. No service tests or
application builds are needed for Markdown-only research. All artifacts are local
task files; rollback consists of reverting the relevant document edits.
