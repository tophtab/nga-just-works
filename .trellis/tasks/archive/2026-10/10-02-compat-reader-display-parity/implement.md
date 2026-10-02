# Implementation Plan

## Entry Gate

- [x] Task-creation and planning consent received.
- [x] Requirements, design, evidence, and spec manifests prepared.
- [x] Final planning summary approved by the user: “好，开干吧”.
- [x] Task started after approval.

## Ordered Work

1. Main session updates the compatibility contract's intentionally opaque-field
   boundary and incorrect comment-marker semantics to match the approved design.
2. Dispatch `trellis-implement` with active-task context. Ownership: App DTO/
   parser, its focused tests, and only necessary existing integration seams.
   The worker is not alone in the workspace; preserve other edits. Read current
   specs and the evidence note before coding.
3. Implement validated score, attachment, and nested-comment projection. Preserve
   default layout, identity/actions, original source, query/paging semantics,
   malformed-content visibility, and recursive cache completeness.
4. Apply the resolved score contract: default score is support count. Map valid
   nonnegative vote_good directly; vote_bad never changes/suppresses it. Cover
   valid zero, missing/invalid good, and independent bad values in the owner test.
5. Audit shared author/media/poll/signature/blacklist/IP paths and fix only proven
   gaps needed for this task. IP transport/repository behavior is already shared.
6. Replace the test that assumes every `isTieTiao=true` root row is a comment.
   Add focused regressions for parent-plus-child rendering, child author differing
   from a filtered parent, score validity, attachments/prefix/image list, damaged
   supplemental content, and populated hot-post exclusion. Prefer production
   rendering seams over source-string snapshots or tests mirroring assignments.
7. Run the affected owner/consumer tests as needed, then dispatch `trellis-check`
   for spec and integration review. Include the parser-to-renderer seam and
   compatibility page author IDs; do not duplicate repository scheduling tests.
8. Run the repository Debug gate once after the final changes and inspect all
   Android module lint XML reports for zero Error/Fatal issues.
9. Main session reviews final diff, records verified results and remaining data
   limitations, and follows finish-work requirements. Keep unrelated comparison
   task artifacts untouched.

## Validation Commands

Focused owner checks (select the actual changed suites):

```bash
./gradlew :nga_phone_base_3.0:testDebugUnitTest --tests 'sp.phone.mvp.model.thread.AppArticleParserTest' --console=plain
```

If the shared rendering seam changes, include its core and app tests; retain
ordinary-read parity fixtures and relevant page-cache/source-validity coverage.
The final required gate is:

```bash
./gradlew testDebugUnitTest lintDebug --continue --console=plain
```

Inspect every generated `*/build/reports/lint-results-debug.xml`; Gradle success
alone is insufficient because lint uses `abortOnError=false`. No automatic
`--rerun-tasks`, repeated broad gates, local APK packaging, device tests, real
voting/posting, or additional live probing are part of this plan.

## Review and Rollback Points

- DTO: field presence and type validity must remain distinguishable.
- Parser: parent identity survives comments; child authors do not violate a
  top-level author filter; optional corruption does not lose readable content.
- Renderer: default builders receive mapped content before execution, with one
  response-local prefix; preserve poll/source/inline media and image identities.
- Cache: incomplete nested/supplemental content cannot be saved as complete.
- IP: same delivery and metadata-only update path, no new lookup mechanism.
- Rollback: revert this task's adapter/spec/tests without changing stored raw
  pages, account ownership, or unrelated edits.

## Completion Status

- [x] Approved mappings and focused production-renderer regressions implemented.
- [x] Independent full-scope review and its two fixes completed.
- [x] Final repository gate: 562 passing tests, 13 lint reports, zero Error/Fatal.
- [x] Protocol, ephemeral serialization state, and renderer limits captured in spec.
- [x] User explicitly authorized commits, push, finish-work and stable publication.
- [x] Work and 6.2.4 notes committed as `2e2a0a70`; finish-work owns archive/journal.
- Release handoff after finish-work: fast-forward main, check remote refs, then
  push main plus the new 6.2.4 tag and stop without polling CI.
