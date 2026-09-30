# Inline media implementation plan — new-session execution

- [x] Complete product scope review and obtain final planning approval before task activation.
- [x] Consume the R5 shared mapping and inspect existing attachment-prefix helpers.
- [x] Implement pure source-token discovery and lossless token ranges.
- [x] Implement view-owned decoration and bounded image targets; preserve selections and reject stale callbacks.
- [x] Reuse decoration for edit initialization and draft/view restoration; prevent original-body duplicate insertion.
- [x] Implement the reviewed whole-token delete/selection behavior and preserve unknown/failing content.
- [x] Verify no existing attachments are re-uploaded and outgoing source changes only through user edits.
- [x] Run meaningful token/round-trip/lifecycle tests plus the parent's required Android offline gate.
- [x] Update relevant editor contracts and review source preservation before handoff.

Exclusive files: composer presenter/view and new editor helpers/tests; coordinate R5 mapping API without rewriting that worker's changes.
Rollback is independent of network submit/upload protocols, which are unchanged.
No device operations or live posting tests are implied.
